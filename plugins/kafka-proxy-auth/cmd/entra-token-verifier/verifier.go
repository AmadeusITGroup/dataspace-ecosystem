package main

import (
"context"
"crypto/rsa"
"encoding/base64"
"encoding/json"
"fmt"
"io"
"math/big"
"net/http"
"os"
"strings"
"sync"
"time"

"github.com/golang-jwt/jwt/v5"
)

// TokenClaims represents the validated token claims
type TokenClaims struct {
Subject   string
Issuer    string
Audience  []string
ExpiresAt time.Time
IssuedAt  time.Time
Email     string
Name      string
Roles     []string
Scopes    []string
AppID     string
TenantID  string
}

// TokenVerifier handles token validation
type TokenVerifier struct {
config    *Config
jwksCache map[string]*rsa.PublicKey
cacheMux  sync.RWMutex
lastFetch time.Time
}

// JWKS response structure
type jwksResponse struct {
Keys []jwk `json:"keys"`
}

type jwk struct {
Kid string `json:"kid"`
Kty string `json:"kty"`
Use string `json:"use"`
N   string `json:"n"`
E   string `json:"e"`
}

// NewTokenVerifier creates a new token verifier instance
func NewTokenVerifier(config *Config) (*TokenVerifier, error) {
verifier := &TokenVerifier{
config:    config,
jwksCache: make(map[string]*rsa.PublicKey),
}

// Pre-fetch JWKS
if err := verifier.refreshJWKS(context.Background()); err != nil {
return nil, fmt.Errorf("failed to fetch JWKS: %w", err)
}

return verifier, nil
}

// VerifyToken validates a JWT token
func (v *TokenVerifier) VerifyToken(ctx context.Context, tokenString string) (*TokenClaims, error) {
// Open log file
logFile, _ := os.OpenFile("/tmp/verifier-debug.log", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
if logFile != nil {
defer logFile.Close()
fmt.Fprintf(logFile, "About to verify token...\n")
}

// Refresh JWKS if needed
if time.Since(v.lastFetch) > time.Duration(v.config.CacheExpiration)*time.Second {
if err := v.refreshJWKS(ctx); err != nil {
// Log error but continue with cached JWKS
fmt.Fprintf(os.Stderr, "[ERROR] entra-token-verifier: Failed to refresh JWKS: %v\n", err)
}
}

// Parse token with validation
token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
// Verify signing method
if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
}

// Get key ID from token header
kid, ok := token.Header["kid"].(string)
if !ok {
return nil, fmt.Errorf("kid not found in token header")
}

if logFile != nil {
fmt.Fprintf(logFile, "Looking for key ID: %s\n", kid)
}

// Get public key from cache
v.cacheMux.RLock()
publicKey, exists := v.jwksCache[kid]
v.cacheMux.RUnlock()

if !exists {
if logFile != nil {
fmt.Fprintf(logFile, "Key %s not in cache, available keys: ", kid)
v.cacheMux.RLock()
for k := range v.jwksCache {
fmt.Fprintf(logFile, "%s ", k)
}
v.cacheMux.RUnlock()
fmt.Fprintf(logFile, "\n")
}
return nil, fmt.Errorf("key %s not found in JWKS", kid)
}

if logFile != nil {
fmt.Fprintf(logFile, "Found public key for kid: %s\n", kid)
}

return publicKey, nil
})

if err != nil {
if logFile != nil {
fmt.Fprintf(logFile, "[ERROR] Token parse/validation failed: %v\n", err)
}
return nil, fmt.Errorf("failed to parse/validate token: %w", err)
}

if !token.Valid {
if logFile != nil {
fmt.Fprintf(logFile, "[ERROR] Token is invalid\n")
}
return nil, fmt.Errorf("token is invalid")
}

// Extract claims
claims, ok := token.Claims.(jwt.MapClaims)
if !ok {
return nil, fmt.Errorf("failed to extract claims")
}

// Validate issuer
issuer, _ := claims["iss"].(string)
if logFile != nil {
fmt.Fprintf(logFile, "Token issuer: %s\n", issuer)
fmt.Fprintf(logFile, "Allowed issuers: %v\n", v.config.AllowedIssuers)
}
if !v.isAllowedIssuer(issuer) {
if logFile != nil {
fmt.Fprintf(logFile, "[ERROR] Invalid issuer: %s\n", issuer)
}
return nil, fmt.Errorf("invalid issuer: %s", issuer)
}

// Validate audience
var audiences []string
switch aud := claims["aud"].(type) {
case string:
audiences = []string{aud}
case []interface{}:
for _, a := range aud {
if audStr, ok := a.(string); ok {
audiences = append(audiences, audStr)
}
}
}

if logFile != nil {
fmt.Fprintf(logFile, "Token audience: %v\n", audiences)
fmt.Fprintf(logFile, "Expected client ID: %s\n", v.config.ClientID)
}

if !v.hasValidAudience(audiences) {
if logFile != nil {
fmt.Fprintf(logFile, "[ERROR] Invalid audience: %v\n", audiences)
}
return nil, fmt.Errorf("invalid audience: %v", audiences)
}

// Build TokenClaims
tokenClaims := &TokenClaims{
Issuer:   issuer,
Audience: audiences,
}

if sub, ok := claims["sub"].(string); ok {
tokenClaims.Subject = sub
}

if exp, ok := claims["exp"].(float64); ok {
tokenClaims.ExpiresAt = time.Unix(int64(exp), 0)
}

if iat, ok := claims["iat"].(float64); ok {
tokenClaims.IssuedAt = time.Unix(int64(iat), 0)
}

// Extract optional claims
if email, ok := claims["email"].(string); ok {
tokenClaims.Email = email
} else if upn, ok := claims["upn"].(string); ok {
tokenClaims.Email = upn
} else if preferredUsername, ok := claims["preferred_username"].(string); ok {
tokenClaims.Email = preferredUsername
}

if name, ok := claims["name"].(string); ok {
tokenClaims.Name = name
}

if appID, ok := claims["appid"].(string); ok {
tokenClaims.AppID = appID
}

if tid, ok := claims["tid"].(string); ok {
tokenClaims.TenantID = tid
}

// Extract roles
if roles, ok := claims["roles"].([]interface{}); ok {
for _, role := range roles {
if r, ok := role.(string); ok {
tokenClaims.Roles = append(tokenClaims.Roles, r)
}
}
}

// Extract scopes
if scopeStr, ok := claims["scp"].(string); ok {
tokenClaims.Scopes = strings.Fields(scopeStr)
}

// Validate required scopes
if len(v.config.RequiredScopes) > 0 && !v.hasRequiredScopes(tokenClaims.Scopes) {
return nil, fmt.Errorf("missing required scopes: required=%v, found=%v",
v.config.RequiredScopes, tokenClaims.Scopes)
}

if logFile != nil {
fmt.Fprintf(logFile, "✓ Token validated successfully!\n")
fmt.Fprintf(logFile, "Subject: %s\n", tokenClaims.Subject)
fmt.Fprintf(logFile, "Email: %s\n", tokenClaims.Email)
fmt.Fprintf(logFile, "Roles: %v\n", tokenClaims.Roles)
}

return tokenClaims, nil
}

// refreshJWKS fetches and caches the JWKS
func (v *TokenVerifier) refreshJWKS(ctx context.Context) error {
resp, err := http.Get(v.config.JWKSUrl)
if err != nil {
return fmt.Errorf("failed to fetch JWKS from %s: %w", v.config.JWKSUrl, err)
}
defer resp.Body.Close()

if resp.StatusCode != http.StatusOK {
body, _ := io.ReadAll(resp.Body)
return fmt.Errorf("JWKS endpoint returned status %d: %s", resp.StatusCode, string(body))
}

var jwksResp jwksResponse
if err := json.NewDecoder(resp.Body).Decode(&jwksResp); err != nil {
return fmt.Errorf("failed to decode JWKS response: %w", err)
}

// Convert JWKs to RSA public keys
newCache := make(map[string]*rsa.PublicKey)
for _, key := range jwksResp.Keys {
if key.Kty != "RSA" {
continue // Skip non-RSA keys
}

publicKey, err := v.jwkToRSAPublicKey(key)
if err != nil {
fmt.Fprintf(os.Stderr, "[WARN] Failed to convert JWK %s to RSA public key: %v\n", key.Kid, err)
continue
}

newCache[key.Kid] = publicKey
}

v.cacheMux.Lock()
v.jwksCache = newCache
v.lastFetch = time.Now()
v.cacheMux.Unlock()

fmt.Fprintf(os.Stderr, "[DEBUG] Cached %d public keys from JWKS\n", len(newCache))

return nil
}

// jwkToRSAPublicKey converts a JWK to an RSA public key
func (v *TokenVerifier) jwkToRSAPublicKey(key jwk) (*rsa.PublicKey, error) {
// Decode modulus (n)
nBytes, err := base64.RawURLEncoding.DecodeString(key.N)
if err != nil {
return nil, fmt.Errorf("failed to decode modulus: %w", err)
}

// Decode exponent (e)
eBytes, err := base64.RawURLEncoding.DecodeString(key.E)
if err != nil {
return nil, fmt.Errorf("failed to decode exponent: %w", err)
}

// Convert exponent bytes to int
var e int
if len(eBytes) == 3 {
e = int(eBytes[0])<<16 | int(eBytes[1])<<8 | int(eBytes[2])
} else if len(eBytes) == 4 {
e = int(eBytes[0])<<24 | int(eBytes[1])<<16 | int(eBytes[2])<<8 | int(eBytes[3])
} else {
// For other lengths, use big.Int
eInt := new(big.Int).SetBytes(eBytes)
e = int(eInt.Int64())
}

// Create RSA public key
publicKey := &rsa.PublicKey{
N: new(big.Int).SetBytes(nBytes),
E: e,
}

return publicKey, nil
}

// isAllowedIssuer checks if the issuer is in the allowed list
func (v *TokenVerifier) isAllowedIssuer(issuer string) bool {
for _, allowed := range v.config.AllowedIssuers {
if strings.TrimSuffix(issuer, "/") == strings.TrimSuffix(allowed, "/") {
return true
}
}
return false
}

// hasValidAudience checks if the token has a valid audience
func (v *TokenVerifier) hasValidAudience(audiences []string) bool {
for _, aud := range audiences {
if aud == v.config.ClientID || aud == "api://"+v.config.ClientID {
return true
}
}
return false
}

// hasRequiredScopes checks if the token has all required scopes
func (v *TokenVerifier) hasRequiredScopes(tokenScopes []string) bool {
scopeMap := make(map[string]bool)
for _, scope := range tokenScopes {
scopeMap[scope] = true
}

for _, required := range v.config.RequiredScopes {
if !scopeMap[required] {
return false
}
}
return true
}

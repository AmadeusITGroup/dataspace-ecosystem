package main

import (
	"context"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/grepplabs/kafka-proxy/pkg/apis"
	"github.com/grepplabs/kafka-proxy/plugin/token-info/shared"
	"github.com/hashicorp/go-plugin"
)

const (
	StatusOK                 = 0
	StatusEmptyToken         = 1
	StatusInvalidToken       = 2
	StatusTokenExpired       = 3
	StatusUnauthorized       = 4
	StatusInternalError      = 5
)

// Configuration for the token verifier
type Config struct {
	TenantID        string
	ClientID        string
	JWKSUrl         string
	AllowedIssuers  []string
	RequiredScopes  []string
	CacheExpiration time.Duration
	Debug           bool
}

// EntraTokenInfo implements the TokenInfo interface for OAUTHBEARER mechanism
type EntraTokenInfo struct {
	config   *Config
	verifier *TokenVerifier
}

// VerifyToken implements apis.TokenInfo interface
// This is called by kafka-proxy when using --auth-local-mechanism=OAUTHBEARER
func (e *EntraTokenInfo) VerifyToken(ctx context.Context, request apis.VerifyRequest) (apis.VerifyResponse, error) {
	if e.config.Debug {
		debugLog("VerifyToken called with token length: %d", len(request.Token))
	}

	if request.Token == "" {
		if e.config.Debug {
			debugLog("Empty token received")
		}
		return apis.VerifyResponse{Success: false, Status: StatusEmptyToken}, nil
	}

	// Verify the JWT token
	claims, err := e.verifier.VerifyToken(ctx, request.Token)
	if err != nil {
		if e.config.Debug {
			debugLog("Token verification failed: %v", err)
		}
		
		// Map error to appropriate status
		if strings.Contains(err.Error(), "expired") {
			return apis.VerifyResponse{Success: false, Status: StatusTokenExpired}, nil
		} else if strings.Contains(err.Error(), "invalid") {
			return apis.VerifyResponse{Success: false, Status: StatusInvalidToken}, nil
		} else if strings.Contains(err.Error(), "unauthorized") {
			return apis.VerifyResponse{Success: false, Status: StatusUnauthorized}, nil
		}
		
		return apis.VerifyResponse{Success: false, Status: StatusInternalError}, nil
	}

	if e.config.Debug {
		debugLog("Token verified successfully for subject: %s, email: %s", claims.Subject, claims.Email)
	}

	return apis.VerifyResponse{Success: true, Status: StatusOK}, nil
}

// TokenVerifier handles JWT token verification
type TokenVerifier struct {
	config     *Config
	jwksCache  map[string]*rsa.PublicKey
	cacheTime  time.Time
	cacheMutex sync.RWMutex
}

// TokenClaims represents the JWT claims we extract
type TokenClaims struct {
	Subject  string
	Issuer   string
	Audience string
	Email    string
	Name     string
	Roles    []string
	Scopes   []string
	AppID    string
	TenantID string
	jwt.RegisteredClaims
}

// NewTokenVerifier creates a new token verifier
func NewTokenVerifier(config *Config) *TokenVerifier {
	return &TokenVerifier{
		config:    config,
		jwksCache: make(map[string]*rsa.PublicKey),
	}
}

// VerifyToken verifies a JWT token from Azure Entra ID
func (tv *TokenVerifier) VerifyToken(ctx context.Context, tokenString string) (*TokenClaims, error) {
	// Parse the token without verification first to get the key ID
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		// Verify the signing method is RSA
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}

		// Get the key ID from the token header
		kid, ok := token.Header["kid"].(string)
		if !ok {
			return nil, fmt.Errorf("kid header not found in token")
		}

		// Get the public key from JWKS
		publicKey, err := tv.getPublicKey(ctx, kid)
		if err != nil {
			return nil, fmt.Errorf("failed to get public key: %w", err)
		}

		return publicKey, nil
	})

	if err != nil {
		return nil, fmt.Errorf("token verification failed: %w", err)
	}

	if !token.Valid {
		return nil, fmt.Errorf("invalid token")
	}

	// Extract claims
	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return nil, fmt.Errorf("failed to extract claims")
	}

	// Validate issuer
	issuer, _ := claims["iss"].(string)
	if !tv.isAllowedIssuer(issuer) {
		return nil, fmt.Errorf("unauthorized issuer: %s", issuer)
	}

	// Validate audience
	audience := tv.extractAudience(claims)
	if audience != tv.config.ClientID {
		return nil, fmt.Errorf("unauthorized audience: %s (expected: %s)", audience, tv.config.ClientID)
	}

	// Extract token claims
	tokenClaims := &TokenClaims{
		Subject:  tv.extractString(claims, "sub"),
		Issuer:   issuer,
		Audience: audience,
		Email:    tv.extractString(claims, "email", "preferred_username", "upn"),
		Name:     tv.extractString(claims, "name"),
		AppID:    tv.extractString(claims, "appid", "azp"),
		TenantID: tv.extractString(claims, "tid"),
	}

	// Extract roles
	tokenClaims.Roles = tv.extractStringArray(claims, "roles")

	// Extract scopes
	if scp, ok := claims["scp"].(string); ok {
		tokenClaims.Scopes = strings.Fields(scp)
	}

	return tokenClaims, nil
}

// getPublicKey retrieves the public key for the given key ID
func (tv *TokenVerifier) getPublicKey(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	// Check cache first
	tv.cacheMutex.RLock()
	if key, ok := tv.jwksCache[kid]; ok {
		// Check if cache is still valid
		if time.Since(tv.cacheTime) < tv.config.CacheExpiration {
			tv.cacheMutex.RUnlock()
			return key, nil
		}
	}
	tv.cacheMutex.RUnlock()

	// Cache miss or expired, refresh JWKS
	if err := tv.refreshJWKS(ctx); err != nil {
		return nil, err
	}

	// Try again from cache
	tv.cacheMutex.RLock()
	defer tv.cacheMutex.RUnlock()

	key, ok := tv.jwksCache[kid]
	if !ok {
		return nil, fmt.Errorf("key ID %s not found in JWKS", kid)
	}

	return key, nil
}

// refreshJWKS fetches and caches the JSON Web Key Set
func (tv *TokenVerifier) refreshJWKS(ctx context.Context) error {
	tv.cacheMutex.Lock()
	defer tv.cacheMutex.Unlock()

	// Create HTTP client with timeout
	client := &http.Client{
		Timeout: 10 * time.Second,
	}

	req, err := http.NewRequestWithContext(ctx, "GET", tv.config.JWKSUrl, nil)
	if err != nil {
		return fmt.Errorf("failed to create JWKS request: %w", err)
	}

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("failed to fetch JWKS: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("JWKS endpoint returned status %d: %s", resp.StatusCode, string(body))
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("failed to read JWKS response: %w", err)
	}

	var jwks struct {
		Keys []struct {
			Kid string   `json:"kid"`
			Kty string   `json:"kty"`
			Use string   `json:"use"`
			N   string   `json:"n"`
			E   string   `json:"e"`
			X5c []string `json:"x5c"`
		} `json:"keys"`
	}

	if err := json.Unmarshal(body, &jwks); err != nil {
		return fmt.Errorf("failed to parse JWKS: %w", err)
	}

	// Clear old cache
	tv.jwksCache = make(map[string]*rsa.PublicKey)

	// Convert JWKs to RSA public keys
	for _, key := range jwks.Keys {
		if key.Kty != "RSA" {
			continue
		}

		publicKey, err := tv.jwkToRSAPublicKey(key.N, key.E)
		if err != nil {
			debugLog("Failed to convert JWK to RSA public key for kid %s: %v", key.Kid, err)
			continue
		}

		tv.jwksCache[key.Kid] = publicKey
	}

	tv.cacheTime = time.Now()

	if tv.config.Debug {
		debugLog("JWKS refreshed, cached %d keys", len(tv.jwksCache))
	}

	return nil
}

// jwkToRSAPublicKey converts a JWK to an RSA public key
func (tv *TokenVerifier) jwkToRSAPublicKey(nStr, eStr string) (*rsa.PublicKey, error) {
	// Decode modulus
	nBytes, err := base64.RawURLEncoding.DecodeString(nStr)
	if err != nil {
		return nil, fmt.Errorf("failed to decode modulus: %w", err)
	}

	// Decode exponent
	eBytes, err := base64.RawURLEncoding.DecodeString(eStr)
	if err != nil {
		return nil, fmt.Errorf("failed to decode exponent: %w", err)
	}

	// Convert to big.Int
	n := new(big.Int).SetBytes(nBytes)
	
	// Convert exponent bytes to int
	var e int
	for _, b := range eBytes {
		e = e*256 + int(b)
	}

	return &rsa.PublicKey{
		N: n,
		E: e,
	}, nil
}

// isAllowedIssuer checks if the issuer is in the allowed list
func (tv *TokenVerifier) isAllowedIssuer(issuer string) bool {
	if len(tv.config.AllowedIssuers) == 0 {
		return true
	}

	for _, allowed := range tv.config.AllowedIssuers {
		if issuer == allowed {
			return true
		}
	}

	return false
}

// extractAudience extracts audience from claims (can be string or array)
func (tv *TokenVerifier) extractAudience(claims jwt.MapClaims) string {
	if aud, ok := claims["aud"].(string); ok {
		return aud
	}
	if aud, ok := claims["aud"].([]interface{}); ok && len(aud) > 0 {
		if audStr, ok := aud[0].(string); ok {
			return audStr
		}
	}
	return ""
}

// extractString extracts a string value from claims, trying multiple keys
func (tv *TokenVerifier) extractString(claims jwt.MapClaims, keys ...string) string {
	for _, key := range keys {
		if val, ok := claims[key].(string); ok {
			return val
		}
	}
	return ""
}

// extractStringArray extracts a string array from claims
func (tv *TokenVerifier) extractStringArray(claims jwt.MapClaims, key string) []string {
	if val, ok := claims[key].([]interface{}); ok {
		result := make([]string, 0, len(val))
		for _, v := range val {
			if str, ok := v.(string); ok {
				result = append(result, str)
			}
		}
		return result
	}
	return nil
}

// debugLog writes debug messages to a file
func debugLog(format string, args ...interface{}) {
	f, err := os.OpenFile("/tmp/token-info-debug.log", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return
	}
	defer f.Close()

	timestamp := time.Now().Format("2006-01-02 15:04:05")
	message := fmt.Sprintf(format, args...)
	fmt.Fprintf(f, "[%s] %s\n", timestamp, message)
}

func main() {
	// Parse command-line flags
	var (
		tenantID       = flag.String("tenant-id", "", "Azure Entra ID Tenant ID")
		clientID       = flag.String("client-id", "", "Application (client) ID")
		jwksURL        = flag.String("jwks-url", "", "JWKS URL (optional, auto-constructed from tenant-id)")
		allowedIssuers = flag.String("allowed-issuers", "", "Comma-separated list of allowed issuers")
		debug          = flag.Bool("debug", false, "Enable debug logging")
	)
	flag.Parse()

	if *tenantID == "" {
		fmt.Fprintf(os.Stderr, "Error: --tenant-id is required\n")
		os.Exit(1)
	}

	if *clientID == "" {
		fmt.Fprintf(os.Stderr, "Error: --client-id is required\n")
		os.Exit(1)
	}

	// Construct JWKS URL if not provided
	jwksURLFinal := *jwksURL
	if jwksURLFinal == "" {
		jwksURLFinal = "https://login.microsoftonline.com/common/discovery/v2.0/keys"
	}

	// Parse allowed issuers
	var issuers []string
	if *allowedIssuers != "" {
		issuers = strings.Split(*allowedIssuers, ",")
		for i, issuer := range issuers {
			issuers[i] = strings.TrimSpace(issuer)
		}
	} else {
		// Default: allow both v1 and v2 endpoints
		issuers = []string{
			fmt.Sprintf("https://login.microsoftonline.com/%s/v2.0", *tenantID),
			fmt.Sprintf("https://sts.windows.net/%s/", *tenantID),
		}
	}

	config := &Config{
		TenantID:        *tenantID,
		ClientID:        *clientID,
		JWKSUrl:         jwksURLFinal,
		AllowedIssuers:  issuers,
		CacheExpiration: 1 * time.Hour,
		Debug:           *debug,
	}

	if *debug {
		debugLog("Starting Entra Token Info plugin")
		debugLog("Tenant ID: %s", *tenantID)
		debugLog("Client ID: %s", *clientID)
		debugLog("JWKS URL: %s", jwksURLFinal)
		debugLog("Allowed Issuers: %v", issuers)
	}

	verifier := NewTokenVerifier(config)
	tokenInfo := &EntraTokenInfo{
		config:   config,
		verifier: verifier,
	}

	// Serve the plugin using HashiCorp go-plugin
	plugin.Serve(&plugin.ServeConfig{
		HandshakeConfig: shared.Handshake,
		Plugins: map[string]plugin.Plugin{
			"tokenInfo": &shared.TokenInfoPlugin{Impl: tokenInfo},
		},
		GRPCServer: plugin.DefaultGRPCServer,
	})
}

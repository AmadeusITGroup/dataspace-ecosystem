package main

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

func TestTokenVerifier(t *testing.T) {
	// This is a unit test with mock configuration
	config := &Config{
		TenantID:        "test-tenant-id",
		ClientID:        "test-client-id",
		JWKSUrl:         "https://example.com/jwks",
		AllowedIssuers:  []string{"https://example.com"},
		RequiredScopes:  []string{},
		CacheExpiration: 3600,
	}

	// Note: This test requires a mock JWKS endpoint for full testing
	// In production, you would set up a test server with valid JWKS
	t.Log("TokenVerifier configuration created successfully")
}

func TestIsAllowedIssuer(t *testing.T) {
	config := &Config{
		AllowedIssuers: []string{
			"https://login.microsoftonline.com/tenant-id/v2.0",
			"https://sts.windows.net/tenant-id/",
		},
	}

	verifier := &TokenVerifier{
		config: config,
	}

	tests := []struct {
		name     string
		issuer   string
		expected bool
	}{
		{
			name:     "Valid issuer v2.0",
			issuer:   "https://login.microsoftonline.com/tenant-id/v2.0",
			expected: true,
		},
		{
			name:     "Valid issuer v2.0 with trailing slash",
			issuer:   "https://login.microsoftonline.com/tenant-id/v2.0/",
			expected: true,
		},
		{
			name:     "Valid issuer STS",
			issuer:   "https://sts.windows.net/tenant-id/",
			expected: true,
		},
		{
			name:     "Invalid issuer",
			issuer:   "https://evil.com/",
			expected: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := verifier.isAllowedIssuer(tt.issuer)
			if result != tt.expected {
				t.Errorf("Expected %v, got %v for issuer %s", tt.expected, result, tt.issuer)
			}
		})
	}
}

func TestHasValidAudience(t *testing.T) {
	config := &Config{
		ClientID: "test-client-id",
	}

	verifier := &TokenVerifier{
		config: config,
	}

	tests := []struct {
		name      string
		audiences []string
		expected  bool
	}{
		{
			name:      "Valid audience - exact match",
			audiences: []string{"test-client-id"},
			expected:  true,
		},
		{
			name:      "Valid audience - api:// prefix",
			audiences: []string{"api://test-client-id"},
			expected:  true,
		},
		{
			name:      "Valid audience - multiple audiences",
			audiences: []string{"other-audience", "test-client-id"},
			expected:  true,
		},
		{
			name:      "Invalid audience",
			audiences: []string{"wrong-client-id"},
			expected:  false,
		},
		{
			name:      "Empty audiences",
			audiences: []string{},
			expected:  false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := verifier.hasValidAudience(tt.audiences)
			if result != tt.expected {
				t.Errorf("Expected %v, got %v for audiences %v", tt.expected, result, tt.audiences)
			}
		})
	}
}

func TestHasRequiredScopes(t *testing.T) {
	config := &Config{
		RequiredScopes: []string{"read", "write"},
	}

	verifier := &TokenVerifier{
		config: config,
	}

	tests := []struct {
		name        string
		tokenScopes []string
		expected    bool
	}{
		{
			name:        "Has all required scopes",
			tokenScopes: []string{"read", "write", "admin"},
			expected:    true,
		},
		{
			name:        "Has exact required scopes",
			tokenScopes: []string{"read", "write"},
			expected:    true,
		},
		{
			name:        "Missing one required scope",
			tokenScopes: []string{"read"},
			expected:    false,
		},
		{
			name:        "Missing all required scopes",
			tokenScopes: []string{"admin"},
			expected:    false,
		},
		{
			name:        "Empty token scopes",
			tokenScopes: []string{},
			expected:    false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := verifier.hasRequiredScopes(tt.tokenScopes)
			if result != tt.expected {
				t.Errorf("Expected %v, got %v for scopes %v", tt.expected, result, tt.tokenScopes)
			}
		})
	}
}

func TestTokenClaimsExtraction(t *testing.T) {
	// Create a test token with custom claims
	privateKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("Failed to generate key: %v", err)
	}

	claims := jwt.MapClaims{
		"sub":                "test-subject",
		"iss":                "https://login.microsoftonline.com/tenant-id/v2.0",
		"aud":                "test-client-id",
		"exp":                time.Now().Add(time.Hour).Unix(),
		"iat":                time.Now().Unix(),
		"email":              "test@example.com",
		"name":               "Test User",
		"roles":              []string{"admin", "user"},
		"scp":                "read write",
		"appid":              "app-id",
		"tid":                "tenant-id",
		"preferred_username": "testuser",
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	token.Header["kid"] = "test-key-id"

	tokenString, err := token.SignedString(privateKey)
	if err != nil {
		t.Fatalf("Failed to sign token: %v", err)
	}

	t.Logf("Generated test token: %s...", tokenString[:50])
	
	// Note: Full validation would require setting up JWKS
	// This test demonstrates the token structure
}
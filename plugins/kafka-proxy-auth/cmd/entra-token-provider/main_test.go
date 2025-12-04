package main

import (
	"context"
	"os"
	"testing"
)

func TestGetToken(t *testing.T) {
	// Skip if credentials not provided
	clientID := os.Getenv("TEST_AZURE_CLIENT_ID")
	clientSecret := os.Getenv("TEST_AZURE_CLIENT_SECRET")
	tenantID := os.Getenv("TEST_AZURE_TENANT_ID")
	scope := os.Getenv("TEST_OAUTH2_SCOPE")

	if clientID == "" || clientSecret == "" || tenantID == "" || scope == "" {
		t.Skip("Skipping integration test: Azure credentials not provided")
	}

	config := &Config{
		ClientID:     clientID,
		ClientSecret: clientSecret,
		TenantID:     tenantID,
		Scope:        scope,
	}
	config.TokenURL = "https://login.microsoftonline.com/" + tenantID + "/oauth2/v2.0/token"

	ctx := context.Background()
	token, err := getToken(ctx, config)

	if err != nil {
		t.Fatalf("Failed to get token: %v", err)
	}

	if token.AccessToken == "" {
		t.Error("Access token is empty")
	}

	if token.TokenType != "Bearer" {
		t.Errorf("Expected token type 'Bearer', got '%s'", token.TokenType)
	}

	if token.ExpiresIn <= 0 {
		t.Errorf("Invalid expiration: %d", token.ExpiresIn)
	}

	t.Logf("Successfully obtained token (expires in %d seconds)", token.ExpiresIn)
}

func TestValidateConfig(t *testing.T) {
	tests := []struct {
		name        string
		clientID    string
		clientSecret string
		tenantID    string
		scope       string
		shouldError bool
	}{
		{
			name:         "Valid config",
			clientID:     "test-client",
			clientSecret: "test-secret",
			tenantID:     "test-tenant",
			scope:        "test-scope",
			shouldError:  false,
		},
		{
			name:         "Missing client ID",
			clientID:     "",
			clientSecret: "test-secret",
			tenantID:     "test-tenant",
			scope:        "test-scope",
			shouldError:  true,
		},
		{
			name:         "Missing client secret",
			clientID:     "test-client",
			clientSecret: "",
			tenantID:     "test-tenant",
			scope:        "test-scope",
			shouldError:  true,
		},
		{
			name:         "Missing tenant ID",
			clientID:     "test-client",
			clientSecret: "test-secret",
			tenantID:     "",
			scope:        "test-scope",
			shouldError:  true,
		},
		{
			name:         "Missing scope",
			clientID:     "test-client",
			clientSecret: "test-secret",
			tenantID:     "test-tenant",
			scope:        "",
			shouldError:  true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			// Set flags for testing
			*clientID = tt.clientID
			*clientSecret = tt.clientSecret
			*tenantID = tt.tenantID
			*scope = tt.scope

			err := validateConfig()
			if tt.shouldError && err == nil {
				t.Error("Expected error but got none")
			}
			if !tt.shouldError && err != nil {
				t.Errorf("Unexpected error: %v", err)
			}
		})
	}
}
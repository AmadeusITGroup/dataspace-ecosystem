package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// TokenResponse represents the OAuth2 token response from Azure
type TokenResponse struct {
	AccessToken  string `json:"access_token"`
	TokenType    string `json:"token_type"`
	ExpiresIn    int    `json:"expires_in"`
	ExtExpiresIn int    `json:"ext_expires_in,omitempty"`
}

// getToken retrieves an OAuth2 access token from Azure Entra ID
func getToken(ctx context.Context, tokenURL, clientID, clientSecret, scope string) (string, int, error) {
	// Prepare request body
	data := url.Values{}
	data.Set("client_id", clientID)
	data.Set("client_secret", clientSecret)
	data.Set("scope", scope)
	data.Set("grant_type", "client_credentials")

	// Create HTTP request
	req, err := http.NewRequestWithContext(ctx, "POST", tokenURL, strings.NewReader(data.Encode()))
	if err != nil {
		return "", 0, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	// Send request
	client := &http.Client{
		Timeout: 30 * time.Second,
	}

	resp, err := client.Do(req)
	if err != nil {
		return "", 0, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// Read response body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", 0, fmt.Errorf("failed to read response: %w", err)
	}

	// Check status code
	if resp.StatusCode != http.StatusOK {
		// Log first 500 chars of error response for debugging
		errBody := string(body)
		if len(errBody) > 500 {
			errBody = errBody[:500] + "..."
		}
		return "", 0, fmt.Errorf("token request failed with status %d, URL: %s, Response: %s", resp.StatusCode, tokenURL, errBody)
	}

	// Parse response
	var tokenResp TokenResponse
	if err := json.Unmarshal(body, &tokenResp); err != nil {
		return "", 0, fmt.Errorf("failed to parse token response: %w", err)
	}

	if tokenResp.AccessToken == "" {
		return "", 0, fmt.Errorf("received empty access token")
	}

	return tokenResp.AccessToken, tokenResp.ExpiresIn, nil
}
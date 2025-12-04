package main

import (
	"context"
	"fmt"
	"os"

	"github.com/grepplabs/kafka-proxy/pkg/apis"
	"github.com/grepplabs/kafka-proxy/plugin/token-provider/shared"
	"github.com/hashicorp/go-plugin"
	"github.com/spf13/pflag"
)

const (
	StatusOK         = 0
	StatusTokenError = 1
)

// EntraTokenProvider implements the apis.TokenProvider interface
type EntraTokenProvider struct {
	clientID     string
	clientSecret string
	tenantID     string
	scope        string
	tokenURL     string
	debug        bool
}

// GetToken implements the TokenProvider interface
// This is called by kafka-proxy when it needs to authenticate to Kafka brokers
func (p *EntraTokenProvider) GetToken(ctx context.Context, request apis.TokenRequest) (apis.TokenResponse, error) {
	if p.debug {
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-provider: GetToken called\n")
	}

	// Get OAuth2 token from Azure Entra ID
	token, expiresIn, err := getToken(ctx, p.tokenURL, p.clientID, p.clientSecret, p.scope)
	if err != nil {
		// Log to both stderr and a file for debugging
		errMsg := fmt.Sprintf("[ERROR] entra-token-provider: Failed to get token: %v\n", err)
		fmt.Fprintf(os.Stderr, errMsg)
		
		// Also write to a debug file
		if logFile, ferr := os.OpenFile("/tmp/token-provider-error.log", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644); ferr == nil {
			logFile.WriteString(errMsg)
			logFile.Close()
		}
		
		return apis.TokenResponse{
			Success: false,
			Status:  StatusTokenError,
			Token:   "",
		}, nil
	}

	if p.debug {
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-provider: Successfully obtained token (expires in %d seconds)\n", expiresIn)
	}

	return apis.TokenResponse{
		Success: true,
		Status:  StatusOK,
		Token:   token,
	}, nil
}

func main() {
	var clientID, clientSecret, tenantID, scope, tokenURL string
	var debug bool

	pflag.StringVar(&clientID, "client-id", "", "Azure AD application client ID")
	pflag.StringVar(&clientSecret, "client-secret", "", "Azure AD application client secret")
	pflag.StringVar(&tenantID, "tenant-id", "", "Azure AD tenant ID")
	pflag.StringVar(&scope, "scope", "", "OAuth2 scope (e.g., https://kafka.example.com/.default)")
	pflag.StringVar(&tokenURL, "token-url", "", "OAuth2 token endpoint URL (optional, auto-constructed if not provided)")
	pflag.BoolVar(&debug, "debug", false, "Enable debug logging")
	pflag.Parse()

	// Validate required parameters
	if clientID == "" || clientSecret == "" || tenantID == "" || scope == "" {
		fmt.Fprintf(os.Stderr, "[ERROR] entra-token-provider: Missing required parameters (client-id, client-secret, tenant-id, scope)\n")
		os.Exit(1)
	}

	// Construct token URL if not provided
	if tokenURL == "" {
		tokenURL = fmt.Sprintf("https://login.microsoftonline.com/%s/oauth2/v2.0/token", tenantID)
	}

	if debug {
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-provider: Starting plugin\n")
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-provider: Token URL: %s\n", tokenURL)
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-provider: Scope: %s\n", scope)
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-provider: Client ID: %s\n", clientID)
	}

	provider := &EntraTokenProvider{
		clientID:     clientID,
		clientSecret: clientSecret,
		tenantID:     tenantID,
		scope:        scope,
		tokenURL:     tokenURL,
		debug:        debug,
	}

	// Serve the plugin using kafka-proxy's plugin framework
	plugin.Serve(&plugin.ServeConfig{
		HandshakeConfig: shared.Handshake,
		Plugins: map[string]plugin.Plugin{
			"tokenProvider": &shared.TokenProviderPlugin{Impl: provider},
		},
		// Enable gRPC serving for this plugin
		GRPCServer: plugin.DefaultGRPCServer,
	})
}

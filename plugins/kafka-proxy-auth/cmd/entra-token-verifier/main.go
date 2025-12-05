package main

import (
	"context"
	"fmt"
	"os"
	"strings"

	"github.com/grepplabs/kafka-proxy/plugin/local-auth/shared"
	"github.com/hashicorp/go-plugin"
	"github.com/spf13/pflag"
)

const (
	StatusOK           = 0
	StatusInvalidToken = 1
)

// Config holds the configuration for the token verifier
type Config struct {
	TenantID        string
	ClientID        string
	JWKSUrl         string
	AllowedIssuers  []string
	RequiredScopes  []string
	CacheExpiration int
	StaticUsers     map[string]string // username -> password mapping
}

// EntraTokenVerifier implements the apis.PasswordAuthenticator interface
type EntraTokenVerifier struct {
	verifier *TokenVerifier
	config   *Config
	debug    bool
}

// Authenticate implements the PasswordAuthenticator interface (username, password string) (bool, int32, error)
// This is called by kafka-proxy when clients connect with SASL/PLAIN
// It supports TWO authentication modes:
// 1. JWT token authentication: password starts with "eyJ" (JWT header)
// 2. Simple username/password: for testing/legacy support
func (v *EntraTokenVerifier) Authenticate(username, password string) (bool, int32, error) {
	// Log to file for debugging
	logFile, _ := os.OpenFile("/tmp/verifier-debug.log", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if logFile != nil {
		defer logFile.Close()
		fmt.Fprintf(logFile, "\n=== Authenticate called ===\n")
		fmt.Fprintf(logFile, "Username: %s\n", username)
		fmt.Fprintf(logFile, "Credential length: %d\n", len(password))
		// NOTE: Never log actual password/token content for security reasons
	}
	
	// Check if password looks like a JWT token (starts with eyJ and has 2 dots)
	isJWT := strings.HasPrefix(password, "eyJ") && strings.Count(password, ".") == 2
	
	if logFile != nil {
		fmt.Fprintf(logFile, "Authentication type: ")
		if isJWT {
			fmt.Fprintf(logFile, "JWT token\n")
		} else {
			fmt.Fprintf(logFile, "Username/Password\n")
		}
	}
	
	// MODE 1: JWT Token Authentication
	if isJWT {
		// Lazy initialization of verifier on first use
		if v.verifier == nil {
			if v.debug {
				fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Initializing JWT verifier\n")
			}
			if logFile != nil {
				fmt.Fprintf(logFile, "Initializing JWT verifier...\n")
			}
			verifier, err := NewTokenVerifier(v.config)
			if err != nil {
				fmt.Fprintf(os.Stderr, "[ERROR] entra-token-verifier: Failed to initialize verifier: %v\n", err)
				if logFile != nil {
					fmt.Fprintf(logFile, "ERROR: Failed to initialize verifier: %v\n", err)
				}
				return false, StatusInvalidToken, nil
			}
			v.verifier = verifier
			if logFile != nil {
				fmt.Fprintf(logFile, "JWT verifier initialized successfully\n")
			}
		}

		if v.debug {
			fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Validating JWT for user: %s\n", username)
		}

		if logFile != nil {
			fmt.Fprintf(logFile, "About to verify JWT token...\n")
		}

		// Validate the JWT token
		ctx := context.Background()
		claims, err := v.verifier.VerifyToken(ctx, password)
		if err != nil {
			if v.debug {
				fmt.Fprintf(os.Stderr, "[ERROR] entra-token-verifier: JWT validation failed: %v\n", err)
			}
			if logFile != nil {
				fmt.Fprintf(logFile, "ERROR: JWT validation failed: %v\n", err)
			}
			return false, StatusInvalidToken, nil
		}

		if v.debug {
			fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: JWT validated successfully for: %s\n", claims.Subject)
			fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Email: %s, Scopes: %v\n", claims.Email, claims.Scopes)
		}

		if logFile != nil {
			fmt.Fprintf(logFile, "✓ JWT validated successfully!\n")
			fmt.Fprintf(logFile, "Subject: %s\n", claims.Subject)
			fmt.Fprintf(logFile, "Email: %s\n", claims.Email)
			fmt.Fprintf(logFile, "Scopes: %v\n", claims.Scopes)
		}

		return true, StatusOK, nil
	}
	
	// MODE 2: Simple Username/Password Authentication
	// Use configured static users from command-line args
	validUsers := v.config.StaticUsers
	
	if logFile != nil {
		fmt.Fprintf(logFile, "Checking username/password against static user list\n")
		fmt.Fprintf(logFile, "Valid users: %v\n", getKeys(validUsers))
	}
	
	if len(validUsers) == 0 {
		if logFile != nil {
			fmt.Fprintf(logFile, "ERROR: No static users configured. Use --static-user flag.\n")
		}
		return false, StatusInvalidToken, nil
	}
	
	if expectedPassword, exists := validUsers[username]; exists {
		if password == expectedPassword {
			if v.debug {
				fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Username/password authentication succeeded for: %s\n", username)
			}
			if logFile != nil {
				fmt.Fprintf(logFile, "✓ Username/password authentication successful!\n")
				fmt.Fprintf(logFile, "User: %s\n", username)
			}
			return true, StatusOK, nil
		}
		
		if v.debug {
			fmt.Fprintf(os.Stderr, "[ERROR] entra-token-verifier: Wrong password for user: %s\n", username)
		}
		if logFile != nil {
			fmt.Fprintf(logFile, "ERROR: Wrong password for user: %s\n", username)
		}
		return false, StatusInvalidToken, nil
	}
	
	if v.debug {
		fmt.Fprintf(os.Stderr, "[ERROR] entra-token-verifier: Unknown user: %s\n", username)
	}
	if logFile != nil {
		fmt.Fprintf(logFile, "ERROR: Unknown user: %s\n", username)
	}
	return false, StatusInvalidToken, nil
}

func getKeys(m map[string]string) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	return keys
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}

func main() {
	fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: main() called, starting plugin serve\n")
	
	// Parse flags at startup
	var tenantID, clientID, jwksURL string
	var allowedIssuers, requiredScopes, staticUserArgs []string
	var cacheExpiration int
	var debug bool

	pflag.StringVar(&tenantID, "tenant-id", "", "Azure AD tenant ID")
	pflag.StringVar(&clientID, "client-id", "", "Expected audience (client ID)")
	pflag.StringVar(&jwksURL, "jwks-url", "", "JWKS URL for token validation")
	pflag.StringSliceVar(&allowedIssuers, "allowed-issuer", []string{}, "Allowed token issuers")
	pflag.StringSliceVar(&requiredScopes, "required-scope", []string{}, "Required scopes in token")
	pflag.IntVar(&cacheExpiration, "cache-expiration", 3600, "JWKS cache expiration in seconds")
	pflag.BoolVar(&debug, "debug", false, "Enable debug logging")
	pflag.StringSliceVar(&staticUserArgs, "static-user", []string{}, "Static users in format 'username:password' or environment variable name")
	pflag.Parse()

	// Auto-construct JWKS URL if not provided
	if jwksURL == "" && tenantID != "" {
		// Use /common/ endpoint which has all keys for all tenants
		// This is more reliable than tenant-specific endpoints
		jwksURL = "https://login.microsoftonline.com/common/discovery/v2.0/keys"
	}

	// Set default allowed issuers if not provided
	if len(allowedIssuers) == 0 && tenantID != "" {
		allowedIssuers = []string{
			fmt.Sprintf("https://login.microsoftonline.com/%s/v2.0", tenantID),
			fmt.Sprintf("https://sts.windows.net/%s/", tenantID),
		}
	}

	// Parse static users from format "username:password"
	staticUsers := make(map[string]string)
	
	for _, userArg := range staticUserArgs {
		// Check if this looks like an environment variable name (no colon, all caps/underscores)
		if !strings.Contains(userArg, ":") && strings.ToUpper(userArg) == userArg {
			// This is likely an environment variable name - read from it
			envValue := os.Getenv(userArg)
			if envValue != "" {
				// Parse comma-separated users from environment variable
				userPairs := strings.Split(envValue, ",")
				for _, userPair := range userPairs {
					userPair = strings.TrimSpace(userPair)
					if userPair == "" {
						continue
					}
					parts := strings.SplitN(userPair, ":", 2)
					if len(parts) == 2 {
						staticUsers[parts[0]] = parts[1]
					} else {
						fmt.Fprintf(os.Stderr, "[WARN] entra-token-verifier: Invalid user format in environment variable, skipping entry\n")
					}
				}
				if debug {
					fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Loaded %d static users from environment variable %s\n", len(staticUsers), userArg)
				}
			} else {
				fmt.Fprintf(os.Stderr, "[WARN] entra-token-verifier: Environment variable %s is empty or not set\n", userArg)
			}
		} else {
			// Direct username:password format
			parts := strings.SplitN(userArg, ":", 2)
			if len(parts) == 2 {
				staticUsers[parts[0]] = parts[1]
			} else {
				// Don't log the actual userArg as it might contain sensitive data
				fmt.Fprintf(os.Stderr, "[WARN] entra-token-verifier: Invalid static-user format (expected username:password or ENV_VAR_NAME), skipping entry\n")
			}
		}
	}

	if debug {
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Initializing plugin\n")
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: JWKS URL: %s\n", jwksURL)
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Client ID: %s\n", clientID)
		fmt.Fprintf(os.Stderr, "[DEBUG] entra-token-verifier: Static users configured: %d\n", len(staticUsers))
	}

	// Initialize the config
	config := &Config{
		TenantID:        tenantID,
		ClientID:        clientID,
		JWKSUrl:         jwksURL,
		AllowedIssuers:  allowedIssuers,
		RequiredScopes:  requiredScopes,
		CacheExpiration: cacheExpiration,
		StaticUsers:     staticUsers,
	}

	// Create a factory that will be called to initialize the plugin
	factory := &EntraTokenVerifierFactory{
		config: config,
		debug:  debug,
	}
	
	// Serve the plugin using kafka-proxy's plugin framework
	plugin.Serve(&plugin.ServeConfig{
		HandshakeConfig: shared.Handshake,
		Plugins: map[string]plugin.Plugin{
			"passwordAuthenticator": &shared.PasswordAuthenticatorPlugin{Impl: factory},
		},
		// Enable gRPC serving for this plugin
		GRPCServer: plugin.DefaultGRPCServer,
	})
}

// EntraTokenVerifierFactory creates the authenticator lazily
type EntraTokenVerifierFactory struct {
	authenticator *EntraTokenVerifier
	config        *Config
	debug         bool
}

func (f *EntraTokenVerifierFactory) Authenticate(username, password string) (bool, int32, error) {
	// Lazy initialization on first authentication request
	if f.authenticator == nil {
		// Create the authenticator with the pre-parsed config
		f.authenticator = &EntraTokenVerifier{
			verifier: nil, // Will be initialized on first use
			debug:    f.debug,
			config:   f.config,
		}
	}

	// Delegate to the actual authenticator
	return f.authenticator.Authenticate(username, password)
}

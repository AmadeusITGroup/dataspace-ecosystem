package org.eclipse.edc.test.system;

public class LocalProvider extends AbstractParticipant {

    public static final String ASSET_ID_REST_API = "rest-api";
    public static final String ASSET_ID_REST_API_DOMAIN = "rest-api-domain";
    public static final String ASSET_ID_FAILURE_REST_API = "failure-rest-api";
    public static final String ASSET_ID_REST_API_EMBEDDED_QUERY_PARAMS = "rest-api-embedded-query-params";
    public static final String EMBEDDED_QUERY_PARAM = "someEmbeddedQueryParam";
    public static final String ASSET_ID_REST_API_OAUTH2 = "rest-api-oauth2";
    public static final String POLICY_RESTRICTED_API = "restricted-api";
    public static final String ASSET_ID_REST_20_SEC_API = "rest-api-20s";
    public static final String ASSET_ID_KAFKA_STREAM = "kafka-stream";
    public static final String ASSET_ID_KAFKA_PROXY_TEST = "kafka-proxy-test";
    public static final String OAUTH2_CLIENT_SECRET_KEY = "oauth-secret";
    public static final String OAUTH2_CLIENT_SECRET = "supersecret";
    public static final String ASSET_ID_REST_API_ROUTE_DOMAIN_RESTRICTED = "rest-api-route-domain-restricted";
    public static final String ASSET_ID_REST_API_TRAVEL_DOMAIN_RESTRICTED = "rest-api-travel-domain-restricted";

    // Azure Blob Storage constants
    public static final String ASSET_ID_AZURE_BLOB = "azure-blob-storage";
    public static final String AZURE_STORAGE_ACCOUNT_NAME = "devstoreaccount1";
    // Default Azurite account key - safe to use in test code
    public static final String AZURE_STORAGE_ACCOUNT_KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="; // gitleaks:allow
    public static final String AZURE_STORAGE_CONTAINER_SRC = "provider-src";
    public static final String AZURE_STORAGE_CONTAINER_DEST = "consumer-dest";
    public static final String AZURE_STORAGE_BLOB_NAME = "test-data.json";
    // Must match the content uploaded by the Terraform init job in modules/azurite-blobstorage/main.tf
    public static final String AZURE_STORAGE_BLOB_CONTENT = "{\"message\": \"Hello from Azure Blob Storage!\"}";

    @Override
    protected String name() {
        return "provider";
    }

}

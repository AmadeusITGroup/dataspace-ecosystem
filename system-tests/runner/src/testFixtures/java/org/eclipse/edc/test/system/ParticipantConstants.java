package org.eclipse.edc.test.system;

public interface ParticipantConstants {

    int IDENTITY_HUB_DID_PORT = 8383;
    int CONTROL_PLANE_DSP_PORT = 8282;

    // Whether TLS is enabled for the cluster (affects both ingress and pod-to-pod communication)
    boolean USE_TLS = Boolean.parseBoolean(System.getProperty("tls.enabled", System.getenv().getOrDefault("TLS_ENABLED", "true")));

    // HTTP scheme based on TLS configuration
    String SCHEME = USE_TLS ? "https" : "http";

    // Default port based on TLS configuration
    String DEFAULT_PORT = USE_TLS ? "443" : "80";

    // Configurable hostname for the cluster ingress
    String CLUSTER_HOSTNAME = System.getProperty("cluster.hostname", System.getenv().getOrDefault("CLUSTER_HOSTNAME", "localhost:" + DEFAULT_PORT));

    // Configurable kubectl context for accessing the Kubernetes cluster
    String KUBECTL_CONTEXT = System.getProperty("kubectl.context", System.getenv().getOrDefault("KUBECTL_CONTEXT", "kind-dse-cluster"));
    
    // Print the configuration for debugging
    static void printConfiguration() {
        System.out.println("=================================");
        System.out.println("SYSTEM TEST CONFIGURATION");
        System.out.println("=================================");
        System.out.println("TLS Enabled: " + USE_TLS);
        System.out.println("Scheme: " + SCHEME);
        System.out.println("Cluster Hostname: " + CLUSTER_HOSTNAME);
        System.out.println("Kubectl Context: " + KUBECTL_CONTEXT);
        System.out.println("Identity Hub Port: " + IDENTITY_HUB_DID_PORT);
        System.out.println("Control Plane DSP Port: " + CONTROL_PLANE_DSP_PORT);
        System.out.println("=================================");
    }
}

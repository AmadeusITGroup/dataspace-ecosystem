package org.eclipse.edc.test.system;

public interface ParticipantConstants {

    int IDENTITY_HUB_DID_PORT = 8383;
    int CONTROL_PLANE_DSP_PORT = 8282;
    
    // Configurable hostname for the cluster HTTPS ingress (port 443)
    String CLUSTER_HOSTNAME = System.getProperty("cluster.hostname", System.getenv().getOrDefault("CLUSTER_HOSTNAME", "localhost:443"));

    // Configurable kubectl context for accessing the Kubernetes cluster
    String KUBECTL_CONTEXT = System.getProperty("kubectl.context", System.getenv().getOrDefault("KUBECTL_CONTEXT", "kind-dse-cluster"));
    
    // Print the configuration for debugging
    static void printConfiguration() {
        System.out.println("🚀 =================================");
        System.out.println("🚀 SYSTEM TEST CONFIGURATION");
        System.out.println("🚀 =================================");
        System.out.println("🚀 Cluster Hostname: " + CLUSTER_HOSTNAME);
        System.out.println("🚀 Kubectl Context: " + KUBECTL_CONTEXT);
        System.out.println("🚀 Identity Hub Port: " + IDENTITY_HUB_DID_PORT);
        System.out.println("🚀 Control Plane DSP Port: " + CONTROL_PLANE_DSP_PORT);
        System.out.println("🚀 =================================");
    }
}

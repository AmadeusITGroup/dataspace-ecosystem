package org.eclipse.edc.test.system;

public interface ParticipantConstants {

    int IDENTITY_HUB_DID_PORT = 8383;
    int CONTROL_PLANE_DSP_PORT = 8282;
    
    // Configurable hostname for the cluster ingress
    String CLUSTER_HOSTNAME = System.getProperty("cluster.hostname", System.getenv().getOrDefault("CLUSTER_HOSTNAME", "localhost:80"));
    
    // Print the configuration for debugging
    static void printConfiguration() {
        System.out.println("🚀 =================================");
        System.out.println("🚀 SYSTEM TEST CONFIGURATION");
        System.out.println("🚀 =================================");
        System.out.println("🚀 Cluster Hostname: " + CLUSTER_HOSTNAME);
        System.out.println("🚀 Identity Hub Port: " + IDENTITY_HUB_DID_PORT);
        System.out.println("🚀 Control Plane DSP Port: " + CONTROL_PLANE_DSP_PORT);
        System.out.println("🚀 =================================");
    }
}

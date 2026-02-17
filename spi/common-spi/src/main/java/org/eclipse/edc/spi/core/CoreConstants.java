package org.eclipse.edc.spi.core;

/**
 * Core constants for DSE (Dataspace Ecosystem).
 * 
 * NOTE: The namespace and policy constants (DSE_NS, DSE_POLICY_PREFIX, DSE_POLICY_NS) 
 * are now configurable via the DseNamespaceExtension.
 * 
 * Default values (for local development):
 * - DSE_NS = "https://w3id.org/dse/v0.0.1/ns/"
 * - DSE_POLICY_PREFIX = "dse-policy"
 * - DSE_POLICY_NS = "https://w3id.org/dse/policy/"
 * 
 * 
 * These constants remain for backward compatibility but should be replaced
 * with injection of DseNamespaceConfig where possible.
 */
public interface CoreConstants {
    /**
     * DSE namespace URL.
     *
     * @deprecated Use DseNamespaceConfig injection instead.
     */
    @Deprecated(since = "0.6.4", forRemoval = true)
    String DSE_NS = "https://w3id.org/dse/v0.0.1/ns/";
    
    /**
     * DSE policy prefix identifier.
     *
     * @deprecated Use DseNamespaceConfig injection instead.
     */
    @Deprecated(since = "0.6.4", forRemoval = true)
    String DSE_POLICY_PREFIX = "dse-policy";
    
    /**
     * DSE policy namespace URL.
     *
     * @deprecated Use DseNamespaceConfig injection instead.
     */
    @Deprecated(since = "0.6.4", forRemoval = true)
    String DSE_POLICY_NS = "https://w3id.org/dse/policy/";
    
    String DSE_VC_TYPE_SCOPE_ALIAS = "org.eclipse.dse.vc.type";
}

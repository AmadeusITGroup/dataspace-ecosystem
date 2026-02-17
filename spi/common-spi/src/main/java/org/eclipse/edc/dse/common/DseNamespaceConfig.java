package org.eclipse.edc.dse.common;

/**
 * Configuration record containing the DSE namespace constants.
 * This configuration is provided by the DseNamespaceExtension and can be
 * overridden via runtime settings.
 *
 * @param dseNamespace The DSE namespace URL (e.g., "https://w3id.org/dse/v0.0.1/ns/")
 * @param dsePolicyPrefix The DSE policy prefix (e.g., "dse-policy")
 * @param dsePolicyNamespace The DSE policy namespace URL (e.g., "https://w3id.org/dse/policy/")
 */
public record DseNamespaceConfig(
        String dseNamespace,
        String dsePolicyPrefix,
        String dsePolicyNamespace
) {
}

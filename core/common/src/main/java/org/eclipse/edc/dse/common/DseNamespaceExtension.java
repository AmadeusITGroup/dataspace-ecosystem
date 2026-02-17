package org.eclipse.edc.dse.common;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

/**
 * Extension that provides configurable DSE namespace constants.
 * This allows different values for different environments (local vs platform).
 */
@Extension(value = "DSE Namespace Configuration")
public class DseNamespaceExtension implements ServiceExtension {

    @Setting(description = "DSE namespace prefix (e.g. 'dse')", 
             key = "dse.namespace.prefix", 
             defaultValue = "dse")
    private String namespacePrefix;

    @Setting(description = "DSE namespace version", 
             key = "dse.namespace.version", 
             defaultValue = "v0.0.1")
    private String namespaceVersion;

    @Setting(description = "DSE policy prefix (e.g. 'dse-policy')", 
             key = "dse.policy.prefix", 
             defaultValue = "dse-policy")
    private String policyPrefix;

    private DseNamespaceConfig config;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var nsUrl = String.format("https://w3id.org/%s/%s/ns/", namespacePrefix, namespaceVersion);
        var policyUrl = String.format("https://w3id.org/%s/policy/", namespacePrefix);
        
        config = new DseNamespaceConfig(nsUrl, policyPrefix, policyUrl);
        
        context.getMonitor().info(String.format(
                "DSE Namespace configured - Prefix: %s, Policy Prefix: %s, NS URL: %s, Policy URL: %s",
                namespacePrefix, policyPrefix, config.dseNamespace(), config.dsePolicyNamespace()
        ));
    }

    @Provider(isDefault = true)
    public DseNamespaceConfig dseNamespaceConfig() {
        if (config == null) {
            throw new IllegalStateException("DseNamespaceConfig has not been initialized yet. Ensure initialize() has been called before requesting the configuration.");
        }
        return config;
    }
}

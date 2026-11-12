package org.eclipse.edc.dse.controlplane.asset.validation;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset.EDC_ASSET_TYPE;

/**
 * Registers DXP ontology validation for assets that opt in through DCAT metadata.
 */
@Extension(value = AssetOntologyValidationExtension.NAME)
public class AssetOntologyValidationExtension implements ServiceExtension {

    public static final String NAME = "Asset DXP Ontology Validation";
    public static final String DEFAULT_PROFILE_IRI = "https://w3id.org/dse/profile/inria-iata/24.4.0";

    @Setting(description = "DXP ontology profile IRI that enables asset validation",
            key = "dse.asset.ontology.validation.profile",
            defaultValue = DEFAULT_PROFILE_IRI)
    private String profileIri;

    @Setting(description = "Comma-separated HTTPS hosts allowed to serve OpenAPI documents",
            key = "dse.asset.ontology.validation.allowed.hosts",
            required = false)
    private String allowedHosts;

    @Setting(description = "Maximum OpenAPI document size in bytes",
            key = "dse.asset.ontology.validation.max.document.bytes",
            defaultValue = "2097152",
            min = 1024)
    private int maxDocumentBytes;

    @Setting(description = "OpenAPI download timeout in seconds",
            key = "dse.asset.ontology.validation.request.timeout.seconds",
            defaultValue = "10",
            min = 1)
    private int requestTimeoutSeconds;

    @Setting(description = "Allow HTTP OpenAPI URLs for local demonstrations only",
            key = "dse.asset.ontology.validation.allow.http",
            defaultValue = "false")
    private boolean allowHttp;

    @Setting(description = "Allow OpenAPI hosts resolving to private addresses for local demonstrations only",
            key = "dse.asset.ontology.validation.allow.private.addresses",
            defaultValue = "false")
    private boolean allowPrivateAddresses;

    private OpenApiDocumentLoader documentLoader;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private TypeManager typeManager;

        @Inject
        private WebService webService;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var hosts = Arrays.stream((allowedHosts == null ? "" : allowedHosts).split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
        if (hosts.isEmpty()) {
            context.getMonitor().warning("DXP ontology OpenAPI host allowlist is empty; opted-in assets will be rejected");
        }
        var profile = OntologyProfile.load(typeManager.getMapper());
        documentLoader = new OpenApiDocumentLoader(hosts, maxDocumentBytes, Duration.ofSeconds(requestTimeoutSeconds),
                allowHttp, allowPrivateAddresses);
        var openApiValidator = new OpenApiOntologyValidator(profile, typeManager.getMapper());

        validatorRegistry.register(EDC_ASSET_TYPE,
                new AssetOntologyValidator(profileIri, documentLoader, openApiValidator));
        webService.registerResource(ApiContext.MANAGEMENT,
                new OntologyApiController(profileIri, profile, typeManager.getMapper()));
        context.getMonitor().info("DXP ontology asset validator registered");
    }

    @Override
    public void shutdown() {
        if (documentLoader != null) {
            documentLoader.close();
        }
    }
}
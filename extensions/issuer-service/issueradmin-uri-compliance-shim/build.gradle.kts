/*
 * Temporary shim (W3, Q5b), IssuerService only: remove this module, its settings.gradle.kts entry and its
 * issuer-service-base dependency once the portful holders are deleted in every environment.
 */
plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.runtime.metamodel)              // @Extension, @Inject
    implementation(libs.edc.spi.core)                       // ServiceExtension, Monitor, EdcException
    implementation(libs.edc.ext.jetty.core)                 // JettyService.addConnectorConfigurationCallback
    implementation(libs.jetty.server)                       // ServerConnector, HttpConnectionFactory, Server; jetty-http UriCompliance
    implementation(libs.jetty.util)                         // LifeCycle.Listener
    implementation(libs.jetty.ee10.servlet)                 // ServletContextHandler, ServletHandler.setDecodeAmbiguousURIs

    testImplementation(libs.edc.core.junit)                 // DependencyInjectionExtension, ObjectFactory
    testImplementation(libs.edc.core.jersey)                // JerseyRestService, EDC exception mappers
    testImplementation(libs.edc.lib.json)                   // JacksonTypeManager
    testImplementation(libs.edc.lib.util)                   // Ports
    testImplementation(libs.edc.issuerservice.api.admin.config)  // upstream IssuerAdminApiConfigurationExtension (web.http.issueradmin.*)
    testImplementation(libs.edc.issuerservice.api.holder)   // upstream IssuerHolderAdminApiController, HolderService
    testImplementation(project(":extensions:issuer-service:membership-attestation-api"))
    testImplementation(project(":extensions:issuer-service:domain-attestation-api"))
    testImplementation(project(":extensions:issuer-service:visibility-attestation-api"))
    testImplementation(project(":spi:issuer-service-spi"))  // attestation stores
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}

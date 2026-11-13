plugins {
    `java-library`
}

group = "org.eclipse.edc"
version = "unspecified"

dependencies {
    implementation(project(":core:common"))                 // DseReflectionUtil
    implementation(libs.edc.spi.core)                       // CriterionOperatorRegistry, QueryResolver, QuerySpec, PropertyLookup, SortOrder
    implementation(libs.edc.federatedcatalog.spi.core)      // FederatedCatalogCache, CatalogConstants
    implementation(libs.edc.spi.catalog)                    // Catalog, Dataset
    implementation(libs.edc.lib.util)                       // LockManager, ReflectionException
    implementation(libs.edc.lib.query)                      // CriterionOperatorRegistryImpl.ofDefaults()
    implementation(libs.edc.runtime.metamodel)              // @Extension, @Inject, @Provider
    implementation(libs.edc.spi.validator)                  // JsonObjectValidatorRegistry, Validator (v4 QuerySpec sort field)
    implementation(libs.edc.spi.jsonld)                     // JsonLd (expanding the v4 QuerySpec before its sort field is checked)
    implementation(libs.edc.api.management.lib)             // ManagementApiJsonSchema.V4.version()

    testImplementation(libs.edc.core.junit)
    // HTTP tests of the v4 query validation: upstream /v4/catalogs/request, DSE /v4/catalogs/query/count
    testImplementation(libs.edc.federatedcatalog.api)       // upstream CatalogsApiV4Controller
    testImplementation(project(":extensions:federated-catalog:asset-count-api"))   // CatalogAssetCountApiController
    testImplementation(libs.edc.core.runtime)               // JsonObjectValidatorRegistryImpl
    testImplementation(libs.edc.core.jersey)                // JerseyRestService, EDC exception mappers
    testImplementation(libs.edc.ext.jetty.core)             // JettyService
    testImplementation(libs.edc.lib.jerseyproviders)        // JerseyJsonLdInterceptor, ObjectMapperProvider
    testImplementation(libs.restAssured)
    // dcat:themeTaxonomy passthrough tests (ingestion and query side)
    testImplementation(project(":spi:dcat-distribution-spi"))   // ThemeTaxonomyConstants
    testImplementation(libs.edc.spi.transform)              // TypeTransformerRegistry
    testImplementation(libs.edc.lib.transform)              // TypeTransformerRegistryImpl
    testImplementation(libs.edc.lib.jsonld)                 // TitaniumJsonLd, JacksonJsonLd
    testImplementation(libs.edc.lib.json)                   // JacksonTypeManager
    testImplementation(libs.edc.protocol.dsp.catalog.v2025) // upstream JsonObjectToCatalogTransformer (passthrough test)
    testImplementation(libs.edc.protocol.dsp.catalog)       // upstream JsonObjectFromCatalogV2025Transformer (passthrough test)
    testImplementation(libs.edc.spi.participant)            // ParticipantIdMapper
    testImplementation(libs.edc.dsp.spi.v2025)              // Dsp2025Constants
    testImplementation(libs.jackson.databind)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

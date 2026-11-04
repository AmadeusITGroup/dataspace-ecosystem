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

    testImplementation(libs.edc.core.junit)
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

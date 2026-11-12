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
}

tasks.test {
    useJUnitPlatform()
}

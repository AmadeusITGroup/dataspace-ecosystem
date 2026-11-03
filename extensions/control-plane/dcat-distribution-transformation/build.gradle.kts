plugins {
    `java-library`
}

dependencies {
    // Extension dependencies
    api(project(":extensions:common:dcat-distribution-transformers"))
    implementation(project(":extensions:control-plane:category-whitelist-config"))
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.spi.participant)
    implementation(libs.edc.dsp.spi.v2025)
    implementation(libs.edc.protocol.dsp.catalog)             // JsonObjectFromCatalogV2025Transformer (the DSP 2025-1 transformer upstream registers)
    implementation(libs.edc.runtime.metamodel)

    // Test dependencies - boot a full runtime
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.controlplane.bom)
    testImplementation(libs.edc.dsp)
    testImplementation(libs.edc.lib.json)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

plugins {
    `java-library`
}

dependencies {
    // Extension dependencies
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.dsp.spi.v2025)
    implementation(libs.edc.runtime.metamodel)

    // Test dependencies - boot a full runtime
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.controlplane.bom)
    testImplementation(libs.edc.dsp)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

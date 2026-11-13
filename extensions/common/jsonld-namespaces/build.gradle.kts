plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.dsp.spi.v2025)  // Dsp2025Constants.DSP_SCOPE_V_2025_1
    implementation(libs.edc.api.management.lib)  // ManagementApi.MANAGEMENT_SCOPE / MANAGEMENT_SCOPE_V4

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.lib.jsonld)
    testImplementation(libs.assertj)
}

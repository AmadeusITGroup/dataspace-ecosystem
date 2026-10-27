plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.jsonld)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.lib.jsonld)
    testImplementation(libs.assertj)
}

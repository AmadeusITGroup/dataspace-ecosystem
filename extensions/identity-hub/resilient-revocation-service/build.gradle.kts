plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.http)
    implementation(libs.edc.spi.vc)
    implementation(libs.edc.spi.token)
    implementation(libs.edc.spi.identity.did)
    implementation(libs.edc.spi.identitytrust)
    implementation(libs.edc.spi.transaction)
    implementation(libs.edc.lib.vc)
    implementation(libs.edc.identityhub.spi.core)
    implementation(libs.jackson.databind)

    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.core.boot)
    testImplementation(libs.assertj)
    testImplementation(libs.junit.jupiter.api)
}

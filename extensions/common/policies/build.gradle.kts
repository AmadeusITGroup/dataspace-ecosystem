plugins {
    `java-library`
}

dependencies {
    api(project(":spi:common-spi"))
    api(libs.edc.spi.jsonld)
    implementation(libs.edc.spi.participant)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.contract)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.jwt)
    implementation(libs.edc.identitytrust.core)
    implementation(libs.edc.spi.policy.engine)
    implementation(libs.edc.spi.identitytrust)
    implementation(libs.edc.lib.util)

    testImplementation(libs.edc.core.junit)
}

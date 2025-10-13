plugins {
    `java-library`
}

dependencies {
    implementation(project(":spi:telemetry-service-spi"))

    implementation(libs.edc.issuerservice.spi.holder)
    implementation(libs.edc.lib.token)
    implementation(libs.edc.lib.http)
    implementation(libs.edc.spi.identity.did)
    implementation(libs.edc.spi.transaction)
    testImplementation(testFixtures(project(":spi:telemetry-agent-spi")))
}

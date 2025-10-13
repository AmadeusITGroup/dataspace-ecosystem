plugins {
    `java-library`
}

dependencies {
    implementation(project(":spi:telemetry-agent-spi"))
    implementation(project(":spi:telemetry-service-spi"))
    implementation(libs.edc.lib.store)
    implementation(libs.edc.lib.query)
    testImplementation(libs.edc.lib.query)
    testImplementation(libs.edc.lib.json)
    testImplementation(testFixtures(project(":spi:telemetry-agent-spi")))
}

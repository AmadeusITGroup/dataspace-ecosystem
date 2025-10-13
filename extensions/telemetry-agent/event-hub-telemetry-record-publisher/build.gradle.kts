plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.azure.messaging.eventhub)
    implementation(project(":spi:common-spi"))
    implementation(project(":spi:telemetry-agent-spi"))
    implementation(project(":spi:telemetry-service-spi"))

    testImplementation(libs.edc.core.junit)
}

plugins {
    `java-library`
}
dependencies {
    implementation(project(":spi:telemetry-service-spi"))
    implementation(libs.edc.spi.core)
}

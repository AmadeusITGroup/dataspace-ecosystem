plugins {
    `java-library`
}

dependencies {
    api(libs.edc.spi.core)
    api(libs.edc.spi.controlplane)

    testImplementation(libs.edc.core.junit)
}
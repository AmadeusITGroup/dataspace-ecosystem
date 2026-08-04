plugins {
    `java-library`
}

dependencies {
    api(project(":core:common"))
    api(libs.edc.spi.asset)
    api(libs.edc.spi.core)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.edc.lib.query)
    testImplementation(libs.jackson.databind)
}

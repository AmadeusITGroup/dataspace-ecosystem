plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.asset)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.http)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.lib.validator)
    implementation(libs.jakarta.rsApi)
    implementation(libs.jakartaJson)
    implementation(libs.jackson.databind)
    implementation(libs.swagger.parser)
    implementation(libs.jena.core)

    testImplementation(libs.edc.junit)
    testRuntimeOnly(libs.edc.core.jersey)
    testImplementation(libs.assertj)
    testImplementation(libs.junit.jupiter.api)
}

edcBuild {
    publish.set(false)
}
plugins {
    `java-library`
    id("io.swagger.core.v3.swagger-gradle-plugin")
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.identityhub.spi.core)
    implementation(libs.edc.lib.store)
    implementation(libs.edc.spi.web)
    implementation(libs.jakarta.rsApi)
    implementation(libs.edc.core.api)
    implementation(libs.edc.core.jersey)
    implementation(project(":spi:telemetry-storage-spi"))

    testImplementation(testFixtures(project(":spi:telemetry-storage-spi")))
    testImplementation(libs.edc.lib.query)
    testImplementation(libs.edc.tests.junit.base)
    testImplementation(libs.restAssured)
    testImplementation(libs.awaitility)
    testImplementation(testFixtures(libs.edc.core.jersey))
}

edcBuild {
    swagger {
        apiGroup.set("telemetry-storage-api")
    }
}
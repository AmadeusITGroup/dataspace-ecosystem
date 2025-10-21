plugins {
    `java-library`
    id("io.swagger.core.v3.swagger-gradle-plugin")
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.lib.store)
    implementation(libs.edc.issuerservice.api.admin.config)
    implementation(project(":spi:issuer-service-spi"))

    testImplementation(testFixtures(project(":spi:issuer-service-spi")))
    testImplementation(libs.edc.lib.query)
    testImplementation(libs.edc.tests.junit.base)
    testImplementation(libs.restAssured)
    testImplementation(libs.awaitility)
    testImplementation(testFixtures(libs.edc.core.jersey))
}

edcBuild {
    swagger {
        apiGroup.set("issuer-admin-api")
    }
}
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
    implementation(libs.edc.issuerservice.api.admin.config)
    implementation(project(":spi:issuer-service-spi"))
    implementation("org.eclipse.edc:identity-hub-credentials-store-sql:0.13.0")
    implementation(libs.edc.spi.core)
    implementation("org.eclipse.edc:sql-lib:0.13.0")
    implementation("org.eclipse.edc:issuerservice-issuance-spi:0.13.0")
    implementation("org.eclipse.edc:issuance-process-store-sql:0.13.0")
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

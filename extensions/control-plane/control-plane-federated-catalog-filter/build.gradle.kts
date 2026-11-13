plugins {
    `java-library`
    id(libs.plugins.swagger.get().pluginId)
}

dependencies {
    api(libs.edc.spi.asset)
    api(libs.edc.spi.core)
    api(libs.edc.spi.controlplane)
    implementation(libs.edc.lib.token)
    implementation(libs.edc.spi.identity.did)
    implementation(libs.edc.lib.http)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.lib.transform)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.lib.jerseyproviders)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.api.management.lib)
    implementation(libs.jakarta.rsApi)
    api(project(":spi:common-spi"))
    api(project(":spi:federated-catalog-filter-spi"))

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.ext.jsonld)
    testImplementation(testFixtures(libs.edc.core.jersey))
    testImplementation(libs.restAssured)
}

edcBuild {
    swagger {
        apiGroup.set("control-plane")
    }
}

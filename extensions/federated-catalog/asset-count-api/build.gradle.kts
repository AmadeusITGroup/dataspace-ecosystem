plugins {
    `java-library`
    id(libs.plugins.swagger.get().pluginId)
}

dependencies {
    implementation(project(":spi:common-spi"))
    implementation(libs.edc.federatedcatalog.spi.core)
    implementation(libs.edc.spi.catalog)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.spi.participant)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.dsp.spi.v2025)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.jakarta.rsApi)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.ext.jsonld)
}

tasks.test {
    useJUnitPlatform()
}

edcBuild {
    swagger {
        apiGroup.set("federated-catalog")
    }
}
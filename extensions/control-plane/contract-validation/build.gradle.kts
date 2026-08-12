plugins {
    `java-library`
}

dependencies {
    implementation(project(":core:controlplane"))

    api(libs.edc.spi.core)
    api(libs.edc.spi.asset)
    api(libs.edc.spi.contract)
    api(libs.edc.spi.catalog)
    api(libs.edc.spi.policy.engine)

    implementation(libs.edc.lib.controlplane.policies)
    implementation(libs.edc.spi.jsonld)
    implementation(libs.edc.lib.query)
    implementation(project(":core:common"))
    implementation(libs.edc.core.controlplane.contract)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}

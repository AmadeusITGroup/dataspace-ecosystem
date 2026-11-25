plugins {
    `java-library`
}

dependencies {
    api(libs.edc.spi.core)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.asset)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.lib.validator)
    implementation(project(":extensions:control-plane:category-whitelist-config"))

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.lib.json)
    testImplementation(libs.assertj)
    testImplementation(libs.jakartaJson)
    // Runtime-backed API tests: boot a real control plane and call the management API
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.controlplane.bom)
    testImplementation(libs.restAssured)
}

edcBuild {
    publish.set(false)
}

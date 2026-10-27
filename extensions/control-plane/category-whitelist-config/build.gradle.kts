plugins {
    `java-library`
}

dependencies {
    api(libs.edc.spi.core)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.jackson.databind)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.lib.json)
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}

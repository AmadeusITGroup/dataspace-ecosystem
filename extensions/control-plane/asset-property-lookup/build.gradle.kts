plugins {
    `java-library`
}

dependencies {
    api(project(":core:controlplane"))
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.core)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}

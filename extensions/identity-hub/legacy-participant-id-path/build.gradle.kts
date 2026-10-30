plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.jakarta.rsApi)

    testImplementation(libs.edc.core.junit)
}

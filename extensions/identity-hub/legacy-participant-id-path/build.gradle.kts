plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.jakarta.rsApi)

    testImplementation(libs.edc.core.junit)
    // JAX-RS implementation, so that the test can use a real UriBuilder like the runtime does
    testImplementation(libs.edc.core.jersey)
}

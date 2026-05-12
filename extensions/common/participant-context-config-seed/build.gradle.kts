plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.participantcontext.config)
    implementation(libs.edc.core.participantcontext.config)

    testImplementation(libs.edc.core.junit)
}

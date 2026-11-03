plugins {
    `java-library`
}

dependencies {
    api(libs.edc.spi.catalog)
    implementation(libs.edc.spi.jsonld)   // Namespaces, used by ThemeTaxonomyConstants

    testImplementation(libs.edc.junit)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

plugins {
    `java-library`
}

dependencies {
    api(project(":spi:dcat-distribution-spi"))
    api(libs.edc.spi.core)
    api(libs.edc.spi.catalog)
    api(libs.edc.spi.transform)
    api(libs.edc.spi.jsonld)
    api(libs.edc.protocol.dsp.catalog)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jakarta-jsonp")

    testImplementation(libs.edc.junit)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

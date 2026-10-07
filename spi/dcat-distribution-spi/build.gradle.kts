plugins {
    `java-library`
}

dependencies {
    api(libs.edc.spi.catalog)

    testImplementation(libs.edc.junit)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

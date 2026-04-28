plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.transaction)
    implementation(libs.edc.spi.transaction.datasource)
    implementation(libs.edc.core.sql)
    implementation(libs.edc.lib.sql)
    implementation(libs.edc.issuerservice.issuance.spi)
    implementation(libs.jackson.databind)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.postgres)
    testImplementation(testFixtures(libs.edc.core.sql))
    testImplementation(testFixtures(libs.edc.sql.test.fixtures))
}

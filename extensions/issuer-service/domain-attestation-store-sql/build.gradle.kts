plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    implementation(project(":spi:issuer-service-spi"))

    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.transaction)
    implementation(libs.edc.spi.transaction.datasource)
    implementation(libs.jackson.databind)
    implementation(libs.edc.core.sql)
    implementation(libs.edc.lib.sql)

    implementation(libs.edc.sql.bootstrapper)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.assertj)
    testImplementation(libs.postgres)
    testImplementation(testFixtures(project(":spi:issuer-service-spi")))
    testImplementation(testFixtures(libs.edc.core.sql))
    testImplementation(testFixtures(libs.edc.sql.lease))
    testImplementation(testFixtures(libs.edc.sql.test.fixtures))
}



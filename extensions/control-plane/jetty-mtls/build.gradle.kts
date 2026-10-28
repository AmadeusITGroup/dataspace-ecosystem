plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.web)
    implementation(libs.edc.ext.jetty.core)
    implementation(libs.jakarta.rsApi)
    implementation(libs.jetty.server)
    implementation(libs.jetty.util)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.core.boot)
    testImplementation(libs.edc.core.jersey)
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}

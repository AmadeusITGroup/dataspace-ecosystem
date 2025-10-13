plugins {
    `java-library`
}

dependencies {
    implementation(project(":extensions:common:store:sql:telemetry-store-sql"))
    implementation(project(":spi:telemetry-agent-spi"))
    implementation(libs.edc.spi.web)
    implementation(libs.edc.core.jersey)
    implementation(libs.jetty.jakarta.servletApi)
}

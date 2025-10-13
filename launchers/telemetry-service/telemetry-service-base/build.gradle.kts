plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":core:telemetry-service-core"))
    runtimeOnly(project(":extensions:telemetry-service:telemetry-service-credential-api"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.bundles.identity)
}

edcBuild {
    publish.set(false)
}
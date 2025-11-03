plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:telemetry-csv-manager:telemetry-csv-manager-api"))

    runtimeOnly(libs.bundles.connector)
}

edcBuild {
    publish.set(false)
}
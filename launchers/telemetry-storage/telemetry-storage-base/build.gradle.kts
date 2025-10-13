plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:telemetry-storage:telemetry-storage-api"))

    runtimeOnly(libs.bundles.connector)
}

edcBuild {
    publish.set(false)
}
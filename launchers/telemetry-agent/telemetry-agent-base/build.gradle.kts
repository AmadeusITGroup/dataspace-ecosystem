plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":core:telemetry-agent-core"))
    runtimeOnly(project(":extensions:telemetry-agent:event-hub-telemetry-record-publisher"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.bundles.identity)
}

edcBuild {
    publish.set(false)
}
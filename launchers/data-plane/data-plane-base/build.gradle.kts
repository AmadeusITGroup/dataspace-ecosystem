plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:data-plane:data-plane-public-api-v2"))
    runtimeOnly(project(":extensions:data-plane:data-plane-data-consumption-metrics"))
    runtimeOnly(project(":core:common:telemetry-record-store"))

    // Azure Blob Storage support
    runtimeOnly(libs.edc.ext.azure.blob.core)
    runtimeOnly(libs.edc.ext.azure.data.plane.storage)
    runtimeOnly(project(":extensions:data-plane:data-plane-provision-blob"))

    runtimeOnly(libs.edc.ext.dpf.kafka)
    runtimeOnly(libs.edc.core.edr.store)
    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.dataplane.bom)
}

edcBuild {
    publish.set(false)
}
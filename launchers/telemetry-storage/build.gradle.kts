plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:telemetry-storage:telemetry-storage-base"))
    implementation(project(":launchers:telemetry-storage:telemetry-storage-postgresql-hashicorpvault"))
    implementation(project(":launchers:telemetry-storage:telemetry-storage-postgresql-azurevault"))
}
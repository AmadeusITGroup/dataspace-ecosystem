plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:telemetry-csv-manager:telemetry-csv-manager-base"))
    implementation(project(":launchers:telemetry-csv-manager:telemetry-csv-manager-postgresql-hashicorpvault"))
    implementation(project(":launchers:telemetry-csv-manager:telemetry-csv-manager-postgresql-azurevault"))
}
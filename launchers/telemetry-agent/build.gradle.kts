plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:telemetry-agent:telemetry-agent-base"))
    implementation(project(":launchers:telemetry-agent:telemetry-agent-postgresql-hashicorpvault"))
}
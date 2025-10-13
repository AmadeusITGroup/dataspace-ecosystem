plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:identity-hub:identity-hub-base"))
    implementation(project(":launchers:identity-hub:identity-hub-postgresql-hashicorpvault"))
    implementation(project(":launchers:identity-hub:identity-hub-postgresql-azurevault"))
}
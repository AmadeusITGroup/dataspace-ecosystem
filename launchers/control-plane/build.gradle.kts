plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:control-plane:control-plane-base"))
    implementation(project(":launchers:control-plane:control-plane-postgresql-hashicorpvault"))
    implementation(project(":launchers:control-plane:control-plane-postgresql-azurevault"))
}
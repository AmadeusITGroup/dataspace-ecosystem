plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:federated-catalog:federated-catalog-base"))
    implementation(project(":launchers:federated-catalog:federated-catalog-postgresql-hashicorpvault"))
    implementation(project(":launchers:federated-catalog:federated-catalog-postgresql-azurevault"))
}
plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:issuer-service:issuer-service-base"))
    implementation(project(":launchers:issuer-service:issuer-service-postgresql-hashicorpvault"))
    implementation(project(":launchers:issuer-service:issuer-service-postgresql-azurevault"))
}
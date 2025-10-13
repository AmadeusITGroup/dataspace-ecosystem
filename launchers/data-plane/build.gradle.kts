plugins {
    `java-library`
}

dependencies {
    implementation(project(":launchers:data-plane:data-plane-base"))
    implementation(project(":launchers:data-plane:data-plane-postgresql-hashicorpvault"))
    implementation(project(":launchers:data-plane:data-plane-postgresql-azurevault"))
}
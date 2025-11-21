plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":core:kafka-proxy-k8s-manager-core"))
    
    runtimeOnly(libs.edc.core.boot)
    runtimeOnly(libs.edc.core.runtime)
    runtimeOnly(libs.edc.core.connector)
    runtimeOnly(libs.edc.core.micrometer)
    
    // Add bundle for complete connector functionality
    runtimeOnly(libs.bundles.connector)
    
    // Add HashiCorp Vault extension to replace InMemoryVault
    //runtimeOnly(libs.edc.ext.vault.hashicorp)
}

edcBuild {
    publish.set(false)
}
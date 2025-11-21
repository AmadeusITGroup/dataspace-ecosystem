plugins {
    `java-library`
}

dependencies {
    // EDC dependencies
    implementation(libs.edc.spi.core)
    implementation(libs.edc.core.runtime)
    
    // Kubernetes client
    implementation("io.fabric8:kubernetes-client:6.9.2")
    
    // Vault client
    implementation("com.bettercloud:vault-java-driver:5.1.0")
    
    // JSON processing
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    
    // Utilities
    implementation("org.apache.commons:commons-lang3:3.13.0")
    implementation("commons-io:commons-io:2.15.1")
    
    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj)
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")
    testImplementation("io.fabric8:kubernetes-server-mock:6.9.2")
    testRuntimeOnly(libs.junit.jupiter.engine)
}
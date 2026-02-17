plugins {
    `java-library`
}

dependencies {
    api(project(":spi:common-spi"))
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.core)
    
    testImplementation(libs.edc.core.junit)
}

plugins {
    `java-library`
}

dependencies {
    api(project(":extensions:common:dcat-distribution-transformers"))
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.dsp.spi.v2025)
    implementation(libs.edc.runtime.metamodel)
}

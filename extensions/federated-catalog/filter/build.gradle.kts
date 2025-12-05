plugins {
    id("java")
    id(libs.plugins.swagger.get().pluginId)
}

group = "org.eclipse.edc"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":spi:common-spi"))
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.edc.spi.core)
    implementation(libs.edc.spi.identitytrust)
    implementation(libs.edc.spi.policy.engine)
    implementation(libs.edc.spi.policy.engine.lib)
    implementation(libs.edc.runtime.metamodel)
    implementation(libs.edc.spi.boot)
    implementation(libs.edc.core.jersey)
    implementation(libs.jakarta.rsApi)
    implementation(libs.edc.spi.contract)
    implementation(project(":extensions:common:policies"))
    implementation(libs.edc.spi.catalog)
    api(libs.edc.spi.controlplane)
    implementation(libs.edc.spi.identity.did)
}

tasks.test {
    useJUnitPlatform()
}

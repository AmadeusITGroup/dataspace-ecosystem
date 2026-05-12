plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.lib.transform)
    implementation(libs.edc.spi.core)
    runtimeOnly(project(":extensions:common:participant-context-config-seed"))
    runtimeOnly(project(":extensions:common:vc-revocation-patch"))
    runtimeOnly(project(":extensions:common:policies"))
    runtimeOnly(project(":extensions:common:odrl-policy-did-validation"))
    runtimeOnly(project(":extensions:agreements"))
    runtimeOnly(project(":extensions:common:metrics:custom-micrometer"))
    runtimeOnly(project(":extensions:control-plane:asset-custom-property-subscriber"))
    runtimeOnly(project(":extensions:control-plane:transfer-data-plane-signal-kafka"))
    runtimeOnly(project(":extensions:control-plane:control-plane-federated-catalog-filter"))
    runtimeOnly(libs.edc.controlplane.api.secrets)
    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.controlplane.bom) {
        exclude(group = "org.eclipse.edc", module = "dsp")
    }
    runtimeOnly(libs.edc.dsp)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.lib.json)
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}
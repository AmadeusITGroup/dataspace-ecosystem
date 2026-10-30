plugins {
    `java-library`
}

dependencies {
    implementation(libs.edc.spi.transform)
    implementation(libs.edc.lib.transform)
    implementation(libs.edc.spi.core)
    runtimeOnly(project(":extensions:common:participant-context-config-seed"))
    runtimeOnly(project(":extensions:common:policies"))
    runtimeOnly(project(":extensions:common:jsonld-namespaces"))
    runtimeOnly(project(":extensions:common:odrl-policy-did-validation"))
    runtimeOnly(project(":extensions:agreements"))
    runtimeOnly(project(":extensions:common:metrics:custom-micrometer"))
    runtimeOnly(project(":extensions:control-plane:asset-custom-property-subscriber"))
    runtimeOnly(project(":extensions:control-plane:asset-property-lookup"))
    runtimeOnly(project(":extensions:control-plane:category-whitelist-config"))
    runtimeOnly(project(":extensions:control-plane:control-plane-federated-catalog-filter"))
    runtimeOnly(project(":extensions:control-plane:contract-validation"))
    runtimeOnly(project(":extensions:control-plane:dcat-distribution-transformation"))
    runtimeOnly(project(":extensions:control-plane:jetty-mtls"))
    runtimeOnly(libs.edc.controlplane.api.secrets)
    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.controlplane.bom) {
        exclude(group = "org.eclipse.edc", module = "dsp")
        // EDC 0.18 switched the control plane to the new Data Plane Signaling (DPS) protocol, while the
        // DSE data plane (dataplane-base-bom) still speaks the legacy signaling API: keep the legacy flow
        // controller (added below) and drop the DPS one, so there is a single DataFlowController.
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling")
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling-core")
        exclude(group = "org.eclipse.edc", module = "data-plane-signaling-oauth2")
        // EDC 0.17 added the federated catalog query API and crawler to the control plane BOM. DSE runs a
        // dedicated federated catalog runtime; in a connector they start an empty crawler and register the
        // DSP 2025 JSON-LD contexts on the management scope, which changes management API v3 output keys.
        exclude(group = "org.eclipse.edc", module = "federated-catalog-api")
        exclude(group = "org.eclipse.edc", module = "catalog-crawler-core")
    }
    runtimeOnly(libs.edc.transfer.data.plane.signaling)
    runtimeOnly(libs.edc.dsp)

    testImplementation(libs.edc.core.junit)
    testImplementation(libs.edc.lib.json)
    testImplementation(libs.assertj)
}

edcBuild {
    publish.set(false)
}
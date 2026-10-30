plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":core:telemetry-service-core"))
    runtimeOnly(project(":extensions:common:participant-context-config-seed"))
    runtimeOnly(project(":extensions:telemetry-service:telemetry-service-credential-api"))
    runtimeOnly(project(":extensions:common:policies"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.bundles.identity)
    runtimeOnly(libs.edc.identitytrust.core)
    runtimeOnly(libs.edc.identitytrust.issuersconfiguration)
    runtimeOnly(libs.edc.sql.jti.store)
    runtimeOnly(libs.edc.oauth2.oauth2client)
    runtimeOnly(libs.edc.dsp)
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
}

edcBuild {
    publish.set(false)
}

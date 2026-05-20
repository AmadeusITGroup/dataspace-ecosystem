plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":core:telemetry-agent-core"))
    runtimeOnly(project(":extensions:common:participant-context-config-seed"))
    runtimeOnly(project(":extensions:telemetry-agent:event-hub-telemetry-record-publisher"))
    runtimeOnly(project(":extensions:common:policies"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.bundles.identity)
    runtimeOnly(libs.edc.identitytrust.core)
    runtimeOnly(libs.edc.identitytrust.issuersconfiguration)
    runtimeOnly(libs.edc.oauth2.oauth2client)
    runtimeOnly(libs.edc.identityhub.bom)
    runtimeOnly(libs.edc.sql.jti.store)
}

edcBuild {
    publish.set(false)
}
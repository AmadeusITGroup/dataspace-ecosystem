plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:identity-hub:did-web-parser"))
    runtimeOnly(project(":extensions:identity-hub:identity-hub-iatp"))
    runtimeOnly(project(":extensions:identity-hub:superuser-seed"))
    runtimeOnly(project(":extensions:common:metrics:custom-micrometer"))
    // INTERIM FIX — remove when DSE migrates to EDC v1.0.0.
    // Replaces the stock credential-watchdog + revocation service to stop transient statuslist
    // failures from flipping VCs to ERROR(-100) and to let -100 self-heal.
    // Upstream fixes: eclipse-edc/IdentityHub#1102 and eclipse-edc/Connector#6049 (both land in EDC 1.0.0).
    runtimeOnly(project(":extensions:identity-hub:resilient-revocation-service"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.identityhub.local.statuslist.publisher)
    runtimeOnly(libs.edc.identityhub.bom) {
        exclude(group = "org.eclipse.edc", module = "identityhub-api-authentication")
        exclude(group = "org.eclipse.edc", module = "identityhub-api-authorization")
        // INTERIM FIX — remove this exclude when migrating to EDC v1.0.0 (IdentityHub#1102) so the
        // native watchdog runs again. Until then our RecoveringCredentialWatchdog replaces it.
        exclude(group = "org.eclipse.edc", module = "credential-watchdog")
    }
}

edcBuild {
    publish.set(false)
}
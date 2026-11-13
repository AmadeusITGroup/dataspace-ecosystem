plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:common:participant-context-config-seed"))
    runtimeOnly(project(":extensions:issuer-service:membership-attestation-api"))
    runtimeOnly(project(":extensions:issuer-service:domain-attestation-api"))
    runtimeOnly(project(":extensions:issuer-service:visibility-attestation-api"))
    // Temporary shim (W3, Q5b): issueradmin accepts %253A, so the port-ful holders/attestations can be deleted.
    // Remove once the portful holders are deleted in every environment.
    runtimeOnly(project(":extensions:issuer-service:issueradmin-uri-compliance-shim"))

    runtimeOnly(libs.edc.issuerservice.db.attestations)
    runtimeOnly(project(":extensions:issuer-service:json-database-attestation"))
    runtimeOnly(libs.edc.issuerservice.bom) {
        exclude(group = "org.eclipse.edc", module = "issuer-admin-api-authentication")
        exclude(group = "org.eclipse.edc", module = "identityhub-api-authorization")
    }
    runtimeOnly(libs.bundles.connector)
}

edcBuild {
    publish.set(false)
}

plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:common:vc-revocation-patch"))
    runtimeOnly(project(":extensions:identity-hub:did-web-parser"))
    runtimeOnly(project(":extensions:identity-hub:identity-hub-iatp"))
    runtimeOnly(project(":extensions:identity-hub:superuser-seed"))
    runtimeOnly(project(":extensions:common:metrics:custom-micrometer"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.identityhub.local.statuslist.publisher)
    runtimeOnly(libs.edc.identityhub.bom) {
        exclude(group = "org.eclipse.edc", module = "identityhub-api-authentication")
        exclude(group = "org.eclipse.edc", module = "identityhub-api-authorization")
    }
}

edcBuild {
    publish.set(false)
}
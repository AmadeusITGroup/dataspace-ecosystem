plugins {
    `java-library`
}

dependencies {
    runtimeOnly(project(":extensions:federated-catalog:federated-catalog-dcat-distribution-transformation"))
    runtimeOnly(project(":extensions:common:policies"))
    runtimeOnly(project(":extensions:common:metrics:custom-micrometer"))
    runtimeOnly(project(":extensions:federated-catalog:participant-registry-node-directory"))
    runtimeOnly(project(":extensions:federated-catalog:federated-catalog-cache"))
    runtimeOnly(project(":extensions:federated-catalog:asset-count-api"))
    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.federatedcatalog.bom) {
        exclude(group = "org.eclipse.edc", module = "dsp")
    }
    runtimeOnly(libs.edc.dsp)
}

edcBuild {
    publish.set(false)
}
plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

var distTar = tasks.getByName("distTar")
var distZip = tasks.getByName("distZip")

dependencies {
    runtimeOnly(project(":launchers:issuer-service:issuer-service-base"))
    runtimeOnly(project(":extensions:issuer-service:membership-attestation-store-sql"))
    runtimeOnly(project(":extensions:issuer-service:domain-attestation-store-sql"))

    runtimeOnly(libs.edc.issuerservice.bom.sql)
    runtimeOnly(libs.edc.ext.vault.hashicorp)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
    dependsOn(distTar, distZip)
    mustRunAfter(distTar, distZip)
}

edcBuild {
    publish.set(false)
}
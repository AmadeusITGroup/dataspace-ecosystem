plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

var distTar = tasks.getByName("distTar")
var distZip = tasks.getByName("distZip")

dependencies {
    runtimeOnly(project(":launchers:data-plane:data-plane-base"))

    runtimeOnly(project(":extensions:common:store:sql:telemetry-store-sql"))
    runtimeOnly(libs.edc.ext.sql.edrindex)
    runtimeOnly(libs.edc.dataplane.sql.bom)
    runtimeOnly(libs.edc.ext.azure.vault)
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
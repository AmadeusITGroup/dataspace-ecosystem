plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

var distTar = tasks.getByName("distTar")
var distZip = tasks.getByName("distZip")

dependencies {
    runtimeOnly(project(":launchers:telemetry-storage:telemetry-storage-base"))
    runtimeOnly(project(":extensions:telemetry-storage:telemetry-storage-store-sql"))

    runtimeOnly(libs.edc.core.sql)
    runtimeOnly(libs.edc.sql.bootstrapper)
    runtimeOnly(libs.edc.ext.sql.pool.apache.commons)
    runtimeOnly(libs.postgres)

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
plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

var distTar = tasks.getByName("distTar")
var distZip = tasks.getByName("distZip")

dependencies {
    runtimeOnly(project(":extensions:telemetry-csv-manager:telemetry-csv-manager-api"))

    runtimeOnly(libs.bundles.connector)
    runtimeOnly(libs.edc.core.sql)
    runtimeOnly(libs.edc.sql.bootstrapper)
    runtimeOnly(libs.edc.ext.sql.pool.apache.commons)
    runtimeOnly(libs.postgres)
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
    destinationDirectory.set(layout.buildDirectory.dir("shadow"))
    dependsOn(distTar, distZip)
    mustRunAfter(distTar, distZip)
}

edcBuild {
    publish.set(false)
}
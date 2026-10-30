plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

val distTar = tasks.named("distTar")
val distZip = tasks.named("distZip")

dependencies {
    runtimeOnly(project(":launchers:federated-catalog:federated-catalog-base"))

    runtimeOnly(libs.edc.issuerservice.holder.store.sql)
    runtimeOnly(libs.bundles.coresql)
    runtimeOnly(libs.edc.ext.azure.vault)
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
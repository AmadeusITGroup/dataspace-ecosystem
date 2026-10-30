plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

val distTar = tasks.named("distTar")
val distZip = tasks.named("distZip")

dependencies {
    runtimeOnly(project(":launchers:data-plane:data-plane-base"))

    runtimeOnly(project(":extensions:common:store:sql:telemetry-store-sql"))
    runtimeOnly(libs.edc.ext.sql.edrindex)
    runtimeOnly(libs.edc.core.participantcontext.connector.classic)
    // EDC 0.18 classic participant core no longer bundles the (in-memory) ParticipantContextStore
    runtimeOnly(libs.edc.core.participantcontext)
    runtimeOnly(libs.edc.dataplane.sql.bom)
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

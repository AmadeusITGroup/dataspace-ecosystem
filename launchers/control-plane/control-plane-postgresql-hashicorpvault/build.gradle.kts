import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

val distTar = tasks.named("distTar")
val distZip = tasks.named("distZip")

dependencies {
    runtimeOnly(project(":launchers:control-plane:control-plane-base"))
    runtimeOnly(libs.edc.controlplane.sql.bom) {
        // federated catalog stores (added to this BOM in EDC 0.17) are not used by a DSE connector
        exclude(group = "org.eclipse.edc", module = "federated-catalog-cache-sql")
        exclude(group = "org.eclipse.edc", module = "target-node-directory-sql")
    }
    runtimeOnly(libs.edc.ext.vault.hashicorp)
    runtimeOnly(project(":extensions:agreements:retirement-evaluation-store-sql"))
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("${project.name}.jar")
    destinationDirectory.set(layout.buildDirectory.dir("shadow"))
    dependsOn(distTar, distZip)
    mustRunAfter(distTar, distZip)
}

edcBuild {
    publish.set(false)
}

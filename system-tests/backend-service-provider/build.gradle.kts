plugins {
    `java-library`
    id("application")
    alias(libs.plugins.shadow)
}

val distTar = tasks.named("distTar")
val distZip = tasks.named("distZip")

dependencies {
    implementation(libs.bundles.connector)

    implementation(libs.nimbus.jwt)
    implementation(libs.jakarta.rsApi)
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

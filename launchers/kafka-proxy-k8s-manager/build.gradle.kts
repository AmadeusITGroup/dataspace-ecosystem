plugins {
    `java-library`
    id("com.github.johnrengelman.shadow")
    id("application")
}

dependencies {
    implementation(project(":launchers:kafka-proxy-k8s-manager:kafka-proxy-k8s-manager-base"))
}

application {
    mainClass.set("org.eclipse.edc.boot.system.runtime.BaseRuntime")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    mergeServiceFiles()
    archiveFileName.set("kafka-proxy-k8s-manager.jar")
}
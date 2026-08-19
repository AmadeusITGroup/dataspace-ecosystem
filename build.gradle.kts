import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin
import java.io.ByteArrayOutputStream

plugins {
    `java-library`
    id("com.bmuschko.docker-remote-api") version "9.3.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    alias(libs.plugins.edc.build)
}

val annotationProcessorVersion: String by project
val dseWebsiteUrl: String by project
val dseScmConnection: String by project
val dseScmUrl: String by project

val loadToKind = project.hasProperty("loadToKind")

// Versions captured at root scope for use in allprojects constraints
val jettyVersion: String = libs.versions.jetty.get()
val bouncycastleVersion: String = libs.versions.bouncycastle.get()
val jacksonVersion: String = libs.versions.jackson.get()
val jacksonAnnotationsVersion: String = libs.versions.jacksonAnnotations.get()

// Kind cluster name configuration (can be overridden via environment variable)
val kindClusterName = System.getenv("KIND_CLUSTER_NAME") ?: "dse-cluster"

// Container Registry Configuration - can be provided via environment variables or gradle properties
val registryName = System.getenv("CDS_REGISTRY_NAME") ?: project.findProperty("registryName")?.toString()
val registryPort = System.getenv("CDS_REGISTRY_PORT") ?: project.findProperty("registryPort")?.toString()
val registryUsername = System.getenv("CDS_REGISTRY_USR") ?: project.findProperty("registryUsername")?.toString()
val registryPassword = System.getenv("CDS_REGISTRY_PWD") ?: project.findProperty("registryPassword")?.toString()
val registryPathToCa = System.getenv("CDS_REGISTRY_PATH_TO_CA") ?: project.findProperty("registryPathToCa")?.toString()
val registryVersionTag = project.findProperty("registryVersionTag")?.toString() ?: "latest"

// Check if registry configuration is provided (for push to registry workflow)
val useRegistry = !registryName.isNullOrEmpty() && !registryPort.isNullOrEmpty()

// Local image content fingerprint (not comparable to a registry manifest digest, podman computes those differently).
fun Project.podmanImageIdOrNull(imageName: String): String? {
    val output = ByteArrayOutputStream()
    val result = exec {
        commandLine("podman", "image", "inspect", imageName, "--format", "{{.Id}}")
        standardOutput = output
        isIgnoreExitValue = true
    }
    if (result.exitValue != 0) return null
    return output.toString().trim().ifEmpty { null }
}

// Queries the registry's v2 HTTP API directly so we check the remote state, not a local record of it.
fun Project.registryManifestDigestOrNull(repository: String, tag: String): String? {
    if (registryName.isNullOrEmpty() || registryPort.isNullOrEmpty()) return null
    val headers = ByteArrayOutputStream()
    val args = mutableListOf(
        "curl", "-s", "-D", "-", "-o", "/dev/null",
        "-H", "Accept: application/vnd.docker.distribution.manifest.v2+json,application/vnd.oci.image.manifest.v1+json"
    )
    if (!registryPathToCa.isNullOrEmpty()) {
        args += listOf("--cacert", registryPathToCa)
    }
    if (!registryUsername.isNullOrEmpty() && !registryPassword.isNullOrEmpty()) {
        args += listOf("-u", "$registryUsername:$registryPassword")
    }
    args += "https://$registryName:$registryPort/v2/$repository/manifests/$tag"

    val result = exec {
        commandLine(args)
        standardOutput = headers
        isIgnoreExitValue = true
    }
    if (result.exitValue != 0) return null
    return headers.toString()
        .lineSequence()
        .firstOrNull { it.startsWith("Docker-Content-Digest:", ignoreCase = true) }
        ?.substringAfter(":")
        ?.trim()
}

allprojects {

    apply(plugin = "${group}.edc-build")
    apply(plugin = "org.eclipse.edc.autodoc")
    apply(plugin = "jacoco")

    // configure which version of the annotation processor to use. defaults to the same version as the plugin
    configure<org.eclipse.edc.plugins.autodoc.AutodocExtension> {
        processorVersion.set(annotationProcessorVersion)
        outputDirectory.set(project.layout.buildDirectory.asFile.get())
    }

    configure<org.eclipse.edc.plugins.edcbuild.extensions.BuildExtension> {
        pom {
            // this is actually important, so we can publish under the correct GID
            groupId = project.group.toString()
            projectName.set(project.name)
            description.set("edc :: ${project.name}")
            projectUrl.set(dseWebsiteUrl)
            scmConnection.set(dseScmConnection)
            scmUrl.set(dseScmUrl)
        }
    }

    configure<CheckstyleExtension> {
        isIgnoreFailures = true
        configFile = rootProject.file("resources/checkstyle-config.xml")
        configDirectory.set(rootProject.file("resources"))
    }

    tasks.withType<Test> {
        finalizedBy(tasks.withType<JacocoReport>())
    }

    tasks.withType<JacocoReport> {
        reports {
            xml.required.set(true)
        }
    }

    // Force secure Netty version to fix vulnerable component: netty-codec-http2
    configurations.all {
        resolutionStrategy {
            force("io.netty:netty-codec-http2:4.2.4.Final")
        }
    }

    dependencies {
        constraints {
            // Jetty: enforce consistent version across all transitive artifacts (security mitigation)
            // org.eclipse.jetty.toolchain is intentionally excluded (uses its own versioning)
            add("implementation", "org.eclipse.jetty:jetty-http") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty:jetty-io") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty:jetty-security") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty:jetty-server") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty:jetty-session") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty:jetty-util") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty.ee10:jetty-ee10-servlet") { version { strictly(jettyVersion) } }
            add("implementation", "org.eclipse.jetty.websocket:jetty-websocket") { version { strictly(jettyVersion) } }

            // Bouncycastle: enforce consistent version across all transitive artifacts (security mitigation)
            add("implementation", "org.bouncycastle:bcprov-jdk18on") { version { strictly(bouncycastleVersion) } }
            add("implementation", "org.bouncycastle:bcpkix-jdk18on") { version { strictly(bouncycastleVersion) } }
            add("implementation", "org.bouncycastle:bcutil-jdk18on") { version { strictly(bouncycastleVersion) } }

            // Jackson: enforce consistent version across all transitive artifacts (security mitigation)
            add("implementation", "com.fasterxml.jackson.core:jackson-core") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.core:jackson-databind") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.core:jackson-annotations") { version { strictly(jacksonAnnotationsVersion) } }
            add("implementation", "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.datatype:jackson-datatype-jsr310") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.datatype:jackson-datatype-jakarta-jsonp") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.jakarta.rs:jackson-jakarta-rs-base") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.jakarta.rs:jackson-jakarta-rs-json-provider") { version { strictly(jacksonVersion) } }
            add("implementation", "com.fasterxml.jackson.module:jackson-module-jakarta-xmlbind-annotations") { version { strictly(jacksonVersion) } }
        }
    }
}

subprojects {
    afterEvaluate {
        val vaultType: String = project.findProperty("vaultType") as? String ?: "hashicorp"
        val baseImage: String = project.findProperty("baseImage") as? String ?: "centos:latest"
        val registryUrl: String = project.findProperty("registryUrl") as? String ?: "quay.io/centos"
        val installJava: String = project.findProperty("installJava") as? String ?: "true"
        
        // Skip building Docker images for non-selected vault variants
        val shouldSkipVaultVariant = when {
            vaultType == "both" -> false  // Build all variants when vaultType=both
            project.name.contains("-postgresql-azurevault") && vaultType != "azure" -> true
            project.name.contains("-postgresql-hashicorpvault") && vaultType != "hashicorp" -> true
            else -> false
        }

        if (project.plugins.hasPlugin("com.github.johnrengelman.shadow") && file("${project.projectDir}/Dockerfile").exists() && !shouldSkipVaultVariant) {
            val buildDir = project.layout.buildDirectory.get().asFile
            val otelFileName = "opentelemetry-javaagent.jar"
            val agentFileOnBuildDirectory = buildDir.resolve(otelFileName)

            val openTelemetryAgentUrl =
                "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.9.0/opentelemetry-javaagent.jar"

            val dockerContextDir = project.projectDir
            val dockerFile = file("$dockerContextDir/Dockerfile")
            val imageName = "${project.name}:latest"
            val imageTar = layout.buildDirectory.file("docker/${project.name}-image.tar")

            val copyOtel = tasks.register("copyOtel") {
                val agentFile = rootDir.resolve("externalLibs").resolve(otelFileName)
                // Only execute if the opentelemetry agent exists in the root dir but not in build directory
                onlyIf {
                    agentFile.exists() && !agentFileOnBuildDirectory.exists()
                }
                // Ensure the build directory exists before copying
                doFirst {
                    buildDir.mkdirs()
                }
                // Copy the jar file
                doLast {
                    copy {
                        from(agentFile)
                        into(buildDir)
                    }
                }
            }

            val downloadOtel = tasks.create("downloadOtel") {
                // Run after copyOtel to check if we still need to download
                dependsOn(copyOtel)
                // only execute task if the opentelemetry agent does not exist after copyOtel. invoke the "clean" task to force
                onlyIf {
                    !agentFileOnBuildDirectory.exists()
                }
                // this task could be the first in the graph, so "build/" may not yet exist. Let's be defensive
                doFirst {
                    project.layout.buildDirectory.asFile.get().mkdirs()
                }
                // download the jar file
                doLast {
                    val download = { url: String, destFile: File ->
                        ant.invokeMethod(
                            "get",
                            mapOf("src" to url, "dest" to destFile)
                        )
                    }
                    logger.lifecycle("Downloading OpenTelemetry Agent")
                    download(openTelemetryAgentUrl, agentFileOnBuildDirectory)
                }
            }

            val shadowJarTask = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>(
                ShadowJavaPlugin.SHADOW_JAR_TASK_NAME
            )

            val podmanTask = tasks.register("podmanize", Exec::class) {
                group = "build"
                description = "Build container image with Podman"
                dependsOn(shadowJarTask, copyOtel)
                inputs.file(shadowJarTask.get().archiveFile)
                inputs.file(dockerFile)
                val jarFile = "./build/shadow/${project.name}.jar"
                val otelFile = "./build/$otelFileName"
                val platform = System.getProperty("platform")
                outputs.file(imageTar)
                val commandLineArgs  = mutableListOf(
                    "podman", "build",
                    "--no-cache",
                    "--build-arg", "JAR=$jarFile",
                    "--build-arg", "OTEL_JAR=$otelFile",
                    "--build-arg", "BASE_IMAGE=$baseImage",
                    "--build-arg", "REGISTRY_URL=$registryUrl",
                    "--build-arg", "INSTALL_JAVA=$installJava",
                    "-t", imageName,
                    "-f", dockerFile,
                    dockerContextDir
                )

                if (platform != null) {
                    commandLineArgs.add("--platform")
                    commandLineArgs.add(platform)
                }
                commandLine(commandLineArgs )
                doFirst {
                    exec {
                        commandLine("podman", "rmi", "-f", imageName)
                        isIgnoreExitValue = true
                    }
                    val result = ByteArrayOutputStream()
                    exec {
                        commandLine("podman", "images", "-q", imageName)
                        standardOutput = result
                        isIgnoreExitValue = true
                    }

                    val output = result.toString().trim()
                    if (output.isEmpty()) {
                        println("Image $imageName successfully deleted.")
                    } else {
                        println("Image $imageName still exists with ID: $output")
                    }
                }
                doLast {
                    exec {
                        commandLine("podman", "save", "-o", imageTar.get().asFile.absolutePath, imageName)
                    }
                    println("New image saved $imageName")
                }
            }

            val loadToKindTask = tasks.register("loadToKind") {
                dependsOn(podmanTask)
                inputs.file(imageTar)
                inputs.property("useRegistry", useRegistry)
                val marker = layout.buildDirectory.file("docker/${project.name}-kind-loaded.txt")
                outputs.file(marker)
                if (useRegistry) {
                    val targetTag = "$registryName:$registryPort/${project.name}:$registryVersionTag"
                    inputs.property("registryTargetTag", targetTag)
                    // Skip the push only if: the local image hasn't changed since our last successful push (recorded
                    // image ID matches), AND the registry still reports the exact digest we pushed back then.
                    outputs.upToDateWhen {
                        val markerFile = marker.get().asFile
                        val recorded = markerFile.takeIf { it.exists() }?.readLines()
                            ?.mapNotNull { line -> line.split(": ", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                            ?.toMap()
                        val currentLocalId = podmanImageIdOrNull(imageName)
                        val remoteDigest = registryManifestDigestOrNull(project.name, registryVersionTag)
                        recorded != null && currentLocalId != null && remoteDigest != null &&
                            recorded["Tag"] == targetTag &&
                            recorded["LocalImageId"] == currentLocalId &&
                            recorded["PushedDigest"] == remoteDigest
                    }
                }
                
                doLast {
                    if (useRegistry) {
                        // Push to registry workflow
                        val targetTag = "$registryName:$registryPort/${project.name}:$registryVersionTag"
                        
                        logger.lifecycle("Registry configuration detected - pushing image to registry")
                        logger.lifecycle("Target: $targetTag")
                        
                        // Login to registry if credentials are provided
                        if (!registryUsername.isNullOrEmpty() && !registryPassword.isNullOrEmpty()) {
                            logger.lifecycle("Logging into registry: $registryName:$registryPort")
                            exec {
                                commandLine("podman", "login", "$registryName:$registryPort", "-u", registryUsername, "-p", registryPassword)
                            }
                            logger.lifecycle("Successfully logged into registry")
                        } else {
                            logger.lifecycle("No credentials provided - attempting push without login")
                        }
                        
                        // Tag the image for registry
                        logger.lifecycle("Tagging image: $imageName -> $targetTag")
                        exec {
                            commandLine("podman", "tag", imageName, targetTag)
                        }
                        
                        // Push to registry, capturing the actual digest podman wrote so we can verify it later.
                        logger.lifecycle("Pushing image to registry...")
                        val digestFile = layout.buildDirectory.file("docker/${project.name}-pushed.digest").get().asFile
                        exec {
                            commandLine("podman", "push", "--digestfile", digestFile.absolutePath, targetTag)
                        }
                        logger.lifecycle("Successfully pushed image: $targetTag")
                        
                        val localImageId = podmanImageIdOrNull(imageName) ?: "unknown"
                        val pushedDigest = digestFile.readText().trim()
                        marker.get().asFile.writeText("Pushed to registry at ${System.currentTimeMillis()}\nTag: $targetTag\nLocalImageId: $localImageId\nPushedDigest: $pushedDigest")
                    } else {
                        // Default behavior: load to Kind cluster
                        logger.lifecycle("No registry configuration detected - loading image to Kind cluster: $kindClusterName")
                        exec {
                            commandLine("kind", "load", "image-archive", imageTar.get().asFile.absolutePath, "-n", kindClusterName)
                        }
                        logger.lifecycle("Image loaded to kind: $imageName")
                        marker.get().asFile.writeText("Loaded to Kind at ${System.currentTimeMillis()}")
                    }
                }
            }

            val dockerTask: DockerBuildImage = tasks.create("dockerize", DockerBuildImage::class) {
                project.plugins.apply("com.bmuschko.docker-remote-api")
                images.add("${project.name}:latest")
                // specify platform with the -Dplatform flag:
                if (System.getProperty("platform") != null)
                    platform.set(System.getProperty("platform"))
                buildArgs.put("JAR", "build/shadow/${project.name}.jar")
                buildArgs.put("OTEL_JAR", "build/${agentFileOnBuildDirectory.name}")
                buildArgs.put("REGISTRY_URL", registryUrl)
                buildArgs.put("BASE_IMAGE", baseImage)
                buildArgs.put("INSTALL_JAVA", installJava)
                inputDir.set(file(dockerContextDir))
            }
            // make sure  always runs after "dockerize" and after "copyOtel"
            dockerTask.dependsOn(tasks.named(ShadowJavaPlugin.SHADOW_JAR_TASK_NAME))
                .dependsOn(downloadOtel)
        }
    }
}

// Task to clean all cache files across all projects to force complete rebuild
tasks.register("cleanCache") {
    group = "build"
    description = "Delete all cached files (binaries, images, build outputs) across all projects to force complete rebuild"
    
    doLast {
        val rootBuildDir = layout.buildDirectory.asFile.get()
        if (rootBuildDir.exists()) {
            rootBuildDir.deleteRecursively()
            println("✓ Deleted root build directory: ${rootBuildDir.absolutePath}")
        }
        
        // Clean Gradle cache
        val gradleCacheDir = file(".gradle")
        if (gradleCacheDir.exists()) {
            gradleCacheDir.deleteRecursively()
            println("✓ Deleted Gradle cache directory: ${gradleCacheDir.absolutePath}")
        }
        
        // Clean all subproject build directories
        var cleanedCount = 0
        subprojects.forEach { subproject ->
            val subprojectBuildDir = subproject.layout.buildDirectory.asFile.get()
            if (subprojectBuildDir.exists()) {
                subprojectBuildDir.deleteRecursively()
                cleanedCount++
            }
        }
        println("✓ Cleaned $cleanedCount subproject build directories")
        
        // Clean image tar files
        fileTree(rootDir) {
            include("**/image.tar")
            include("**/*-image.tar")
            include("**/*-kind-loaded.txt")
        }.forEach { tarFile ->
            tarFile.delete()
            println("✓ Deleted: ${tarFile.relativeTo(rootDir)}")
        }
        
        // Remove all podman images for this project
        val imagesToClean = mutableListOf<String>()
        subprojects.forEach { subproject ->
            if (subproject.file("Dockerfile").exists() && !subproject.name.contains("system-tests")) {
                imagesToClean.add("${subproject.name}:latest")
            }
        }
        
        if (imagesToClean.isNotEmpty()) {
            println("\nRemoving podman images...")
            imagesToClean.forEach { imageName ->
                try {
                    exec {
                        commandLine("podman", "rmi", "-f", imageName)
                        isIgnoreExitValue = true
                    }
                    println("✓ Removed: $imageName")
                } catch (e: Exception) {
                    // Image might not exist, that's okay
                }
            }
        }
        
        println("\n✓ All caches cleared. Next build will regenerate everything from scratch.")
        println("  Run './gradlew podmanize' to rebuild all images")
    }
}

buildscript {
    dependencies {
        val edcGradlePluginsVersion: String by project
        val version: String by project
        classpath("org.eclipse.edc.edc-build:org.eclipse.edc.edc-build.gradle.plugin:${edcGradlePluginsVersion}")
        classpath("org.eclipse.edc.autodoc:org.eclipse.edc.autodoc.gradle.plugin:$version")
    }
}

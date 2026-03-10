/*
 *  Copyright (c) 2024 Eclipse Dataspace Connector Project
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Eclipse Dataspace Connector Project - initial implementation
 */

plugins {
    id("com.bmuschko.docker-remote-api") version "9.3.4"
}

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import java.io.ByteArrayOutputStream

val dockerImageName = project.findProperty("docker.image.name")?.toString() ?: "kafka-proxy-entra-auth"
val dockerImageTag = project.findProperty("docker.image.tag")?.toString() ?: "latest"
val dockerRegistry = project.findProperty("docker.registry")?.toString() ?: ""
val loadToKind = project.hasProperty("loadToKind")

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

val pluginDir = file("kafka-proxy-auth")
val dockerContextDir = pluginDir
val dockerFile = file("$dockerContextDir/Dockerfile")
val imageName = "$dockerImageName:$dockerImageTag"
val imageTar = file("build/docker/kafka-proxy-entra-auth-image.tar")
val goInputs = fileTree(pluginDir) {
    include("**/*.go")
    include("go.mod")
    include("go.sum")
    include("Makefile")
    exclude("bin/**")
}
val goLinuxBinaries = files(
    pluginDir.resolve("bin/entra-token-provider"),
    pluginDir.resolve("bin/entra-token-verifier"),
    pluginDir.resolve("bin/entra-token-info")
)

// Task to build Go binaries
val buildGoPlugins = tasks.register<Exec>("buildGoPlugins") {
    group = "build"
    description = "Build Go authentication plugins"
    
    workingDir = pluginDir
    
    // Use environment variable or system property to override the build target
    val makeTarget = System.getProperty("go.build.target") 
        ?: System.getenv("GO_BUILD_TARGET") 
        ?: "build"  // Default to regular build for CI/CD compatibility

    inputs.files(goInputs)
    inputs.property("makeTarget", makeTarget)
    outputs.dir(pluginDir.resolve("bin"))
    
    commandLine = listOf("make", makeTarget)
    
    doFirst {
        println("Building Go authentication plugins with target: $makeTarget")
    }
    
    doLast {
        println("Go plugins built successfully")
    }
}

// Task to build Go binaries specifically for Linux (Docker)
val buildGoPluginsLinux = tasks.register<Exec>("buildGoPluginsLinux") {
    group = "build"
    description = "Build Go authentication plugins for Linux (Docker)"

    inputs.files(goInputs)
    outputs.files(goLinuxBinaries)
    
    workingDir = pluginDir
    commandLine = listOf("make", "build-linux")
    
    doFirst {
        println("Building Go authentication plugins for Linux/Docker...")
    }
    
    doLast {
        println("Go plugins built successfully for Linux/Docker")
    }
}

// Task to run Go tests
tasks.register<Exec>("testGoPlugins") {
    group = "verification"
    description = "Run Go plugin tests"
    
    workingDir = pluginDir
    commandLine = listOf("make", "test")
    
    doFirst {
        println("Running Go plugin tests...")
    }
}

// Task to clean Go build artifacts
tasks.register<Exec>("cleanGoPlugins") {
    group = "build"
    description = "Clean Go plugin build artifacts"
    
    workingDir = pluginDir
    commandLine = listOf("make", "clean")
    
    doFirst {
        println("Cleaning Go plugin build artifacts...")
    }
}

// Podman build task (following the same pattern as other modules)
val podmanTask = tasks.register("podmanize", Exec::class) {
    group = "build"
    description = "Build container image with Podman for Kafka Proxy authentication plugins"
    dependsOn(buildGoPluginsLinux)
    
    // Track Go binaries as inputs
    inputs.files(
        fileTree(pluginDir.resolve("bin")) {
            include("entra-token-provider")
            include("entra-token-verifier")
            include("entra-token-info")
        }
    )
    inputs.file(dockerFile)
    outputs.file(imageTar)

    val platform = System.getProperty("platform")

    val commandLineArgs = mutableListOf(
        "podman", "build",
        "--no-cache",
        "-t", imageName,
        "-f", dockerFile.absolutePath,
        dockerContextDir.absolutePath
    )

    if (platform != null) {
        commandLineArgs.add("--platform")
        commandLineArgs.add(platform)
    }

    commandLine(commandLineArgs)

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
        // Ensure the build/docker directory exists
        imageTar.parentFile.mkdirs()
        exec {
            commandLine("podman", "save", "-o", imageTar.absolutePath, imageName)
        }
        println("Podman image built and saved: $imageName")
    }
}

// Load to Kind task (following the same pattern as other modules)
val loadToKindTask = tasks.register("loadToKind") {
    dependsOn(podmanTask)
    inputs.file(imageTar)
    val marker = layout.buildDirectory.file("docker/kafka-proxy-entra-auth-kind-loaded.txt")
    outputs.file(marker)
    
    doLast {
        if (useRegistry) {
            // Push to registry workflow
            val targetTag = "$registryName:$registryPort/$dockerImageName:$registryVersionTag"
            
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
            
            // Push to registry
            logger.lifecycle("Pushing image to registry...")
            exec {
                commandLine("podman", "push", targetTag)
            }
            logger.lifecycle("Successfully pushed image: $targetTag")
            
            marker.get().asFile.writeText("Pushed to registry at ${System.currentTimeMillis()}\nTag: $targetTag")
        } else {
            // Default behavior: load to Kind cluster
            logger.lifecycle("No registry configuration detected - loading image to Kind cluster: $kindClusterName")
            exec {
                commandLine("kind", "load", "image-archive", imageTar.absolutePath, "-n", kindClusterName)
            }
            logger.lifecycle("Image loaded to kind: $imageName")
            marker.get().asFile.writeText("Loaded to Kind at ${System.currentTimeMillis()}")
        }
    }
}

// Docker build task (for Docker users)
val dockerTask = tasks.register("dockerize", DockerBuildImage::class) {
    group = "docker"
    description = "Build Docker image with Entra ID authentication plugins"
    
    dependsOn(buildGoPlugins)
    
    images.add(imageName)
    
    if (dockerRegistry.isNotEmpty()) {
        images.add("$dockerRegistry/$imageName")
    }
    
    // specify platform with the -Dplatform flag:
    if (System.getProperty("platform") != null) {
        platform.set(System.getProperty("platform"))
    }
    
    inputDir.set(dockerContextDir)
    dockerFile.set(file("$dockerContextDir/Dockerfile"))
    
    doFirst {
        println("Building Docker image: $imageName")
    }
    
    doLast {
        println("Docker image built successfully")
    }
}

// Aggregate task for complete build
tasks.register("buildAll") {
    group = "build"
    description = "Build, test, and create container image for authentication plugins"
    
    dependsOn("testGoPlugins", "buildGoPlugins")
    
    if (loadToKind) {
        dependsOn("loadToKind")
    } else {
        dependsOn("podmanize")
    }
}

// Clean task removes image tar and marker files
tasks.register("cleanImage") {
    group = "build"
    description = "Clean generated image tar and marker files"
    
    doLast {
        // Clean old image.tar in plugin directory
        val oldImageTar = file("${dockerContextDir.path}/image.tar")
        if (oldImageTar.exists()) {
            oldImageTar.delete()
            println("Deleted old image tar: ${oldImageTar.absolutePath}")
        }
        
        // Clean new image tar in build directory
        if (imageTar.exists()) {
            imageTar.delete()
            println("Deleted image tar: ${imageTar.absolutePath}")
        }
        
        // Clean marker file
        val marker = file("build/docker/kafka-proxy-entra-auth-kind-loaded.txt")
        if (marker.exists()) {
            marker.delete()
            println("Deleted marker file: ${marker.absolutePath}")
        }
    }
}

// Task to clean all cache files and force complete rebuild from scratch
tasks.register("cleanCache") {
    group = "build"
    description = "Delete all cached files (Go binaries, images, build outputs) to force complete rebuild"
    
    // Note: We don't depend on cleanGoPlugins because it tries to use podman/docker
    // which can fail if directories are already deleted. We clean manually instead.
    dependsOn("cleanImage")
    
    doLast {
        // Clean Go binary directory directly (don't use Make clean to avoid podman issues)
        val goBinDir = pluginDir.resolve("bin")
        if (goBinDir.exists()) {
            goBinDir.deleteRecursively()
            println("✓ Deleted Go binary directory: ${goBinDir.absolutePath}")
        }
        
        // Clean build directory completely
        val buildDir = file("build")
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
            println("✓ Deleted build directory: ${buildDir.absolutePath}")
        }
        
        // Clean old image.tar in plugin directory if it exists
        val oldImageTar = file("${pluginDir.path}/image.tar")
        if (oldImageTar.exists()) {
            oldImageTar.delete()
            println("✓ Deleted old image tar: ${oldImageTar.absolutePath}")
        }
        
        // Remove any cached podman images
        try {
            exec {
                commandLine("podman", "rmi", "-f", imageName)
                isIgnoreExitValue = true
            }
            println("✓ Removed podman image: $imageName")
        } catch (e: Exception) {
            // Image might not exist, that's okay
        }
        
        println("\n✓ Plugin caches cleared. Next build will regenerate everything from scratch.")
    }
}

// Hook into standard Gradle lifecycle
tasks.named("clean") {
    dependsOn("cleanGoPlugins", "cleanImage")
}

/*
 *  Copyright (c) 2024 Amadeus IT Group
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus IT Group - Vault type selection logic
 */

/**
 * Shared configuration for launcher modules to manage vault-specific dependencies.
 * This script dynamically includes HashiCorp Vault, Azure Vault, or both variants
 * based on the 'vaultType' project property.
 *
 * Usage: apply(from = "$rootDir/gradle/launcher-vault-dependencies.gradle.kts")
 *
 * Supported vaultType values:
 * - "hashicorp" (default): Include only HashiCorp Vault variant
 * - "azure": Include only Azure Vault variant
 * - "both": Include both HashiCorp and Azure Vault variants
 */

val vaultType: String = project.findProperty("vaultType") as? String ?: "hashicorp"

// Validate vaultType to catch typos and invalid values early
val supportedVaultTypes = listOf("hashicorp", "azure", "both")
require(vaultType in supportedVaultTypes) {
    "Invalid vaultType: '$vaultType'. Supported values are: ${supportedVaultTypes.joinToString(", ")}"
}

val launcherPrefix = ":launchers:${project.name}:${project.name}"

// Helper function to check if a project module exists
fun projectExists(path: String): Boolean {
    return rootProject.findProject(path) != null
}

// Helper function to validate and add vault variant dependency
fun addVaultVariant(dependencies: DependencyHandlerScope, variant: String) {
    val modulePath = "$launcherPrefix-postgresql-${variant}vault"
    if (!projectExists(modulePath)) {
        throw GradleException(
            "Module '$modulePath' not found. " +
            "The launcher '${project.name}' may not support vault type '$variant'. " +
            "Please check if this module exists in settings.gradle.kts or use a different vaultType."
        )
    }
    dependencies.add("implementation", project(modulePath))
}

dependencies {
    // Always include base module
    add("implementation", project("$launcherPrefix-base"))
    
    // Add vault-specific modules with existence validation
    when (vaultType) {
        "azure" -> addVaultVariant(this, "azure")
        "both" -> {
            addVaultVariant(this, "hashicorp")
            addVaultVariant(this, "azure")
        }
        else -> addVaultVariant(this, "hashicorp")
    }
}

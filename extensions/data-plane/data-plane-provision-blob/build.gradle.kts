/*
 *  Copyright (c) 2024 Dataspace Ecosystem
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Dataspace Ecosystem - initial API and implementation
 *       Based on Eclipse EDC Technology-Azure
 *
 */

// TODO: When upgrading to EDC v0.15, this local module can be replaced by the upstream
//  Maven dependency: org.eclipse.edc.azure:data-plane-provision-blob

plugins {
    `java-library`
}

dependencies {
    api(libs.edc.ext.azure.blob.core)
    api(libs.edc.spi.core)
    api(libs.edc.spi.dpf)

    implementation(libs.edc.lib.util)

    testImplementation(libs.edc.junit)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj)
}

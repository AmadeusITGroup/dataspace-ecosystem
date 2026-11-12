plugins {
    `java-library`
}

dependencies {
    // Our extension under test
    testImplementation(project(":extensions:control-plane:dcat-distribution-transformation"))

    // Runtime dependencies to boot a minimal control-plane with DSP catalog support
    testImplementation(libs.edc.controlplane.base.bom)
    testImplementation(libs.edc.iam.mock)

    // Test utilities
    testImplementation(libs.edc.junit)
    testImplementation(libs.edc.dsp.spi.v2025)
    testImplementation(libs.edc.spi.jsonld)
    testImplementation(libs.edc.spi.catalog)
    testImplementation(libs.edc.spi.asset)
    testImplementation(libs.restAssured)
    testImplementation(libs.assertj)
}

// NOTE: Tests in this module are tagged with @EndToEndTest, which the EDC build plugin
// excludes by default. To run these tests, pass -DincludeTags=EndToEndTest:
//   ./gradlew :system-tests:dcat-distribution-transformation-test:test -DincludeTags=EndToEndTest
tasks.test {
    useJUnitPlatform {
        includeTags("EndToEndTest")
    }
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
}

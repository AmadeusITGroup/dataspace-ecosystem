plugins {
    `java-library`
}

dependencies {
    api(project(":spi:common-spi"))
    api(libs.edc.identityhub.spi.core)
    api(libs.edc.spi.core)
}

plugins {
    `java-library`
    id("io.swagger.core.v3.swagger-gradle-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.edc.core.jersey)
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("org.hibernate.orm:hibernate-core:7.1.2.Final")
    implementation("io.minio:minio:8.6.0")
    implementation(libs.jjwt.api)
    implementation("com.azure:azure-storage-blob:12.31.3")
    implementation("com.azure:azure-identity:1.18.1")
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation("org.hibernate.orm:hibernate-core:7.1.2.Final")
    testRuntimeOnly("org.hsqldb:hsqldb:2.7.4")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.10.0")
}
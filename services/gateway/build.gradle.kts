plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.forge"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Cloud Gateway (使用 4.1.x 以兼容 Spring Boot 3.3.x)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway:4.1.3")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.6")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Config
    implementation("org.yaml:snakeyaml:2.2")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("ch.qos.logback:logback-classic")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("gateway-service.jar")
}
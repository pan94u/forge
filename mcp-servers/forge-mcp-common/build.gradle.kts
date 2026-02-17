plugins {
    kotlin("plugin.serialization")
    `java-library`
}

group = "com.forge"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.7"
val logbackVersion = "1.4.14"
val slf4jVersion = "2.0.11"

dependencies {
    // Ktor Server
    api("io.ktor:ktor-server-core:$ktorVersion")
    api("io.ktor:ktor-server-netty:$ktorVersion")
    api("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    api("io.ktor:ktor-server-auth:$ktorVersion")
    api("io.ktor:ktor-server-status-pages:$ktorVersion")
    api("io.ktor:ktor-server-call-logging:$ktorVersion")

    // Ktor Serialization
    api("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Ktor Client (for OAuth introspection)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Kotlin Serialization
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Logging
    api("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

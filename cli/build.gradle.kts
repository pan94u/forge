plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native") version "0.10.4"
    application
}

application {
    mainClass.set("com.forge.cli.ForgeCliMainKt")
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.yaml:snakeyaml:2.3")

    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.mockk:mockk:1.13.13")
}

graalvmNative {
    binaries {
        named("main") {
            mainClass.set("com.forge.cli.ForgeCliMainKt")
            imageName.set("forge")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
    toolchainDetection.set(false)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}

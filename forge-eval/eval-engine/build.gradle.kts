plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":forge-eval:eval-protocol"))
    implementation(project(":adapters:model-adapter"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

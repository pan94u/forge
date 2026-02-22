plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Anthropic official Java SDK
    implementation("com.anthropic:anthropic-java:2.12.0")

    // Google GenAI official SDK (Gemini)
    implementation("com.google.genai:google-genai:1.2.0")

    // Alibaba DashScope official SDK (Qwen)
    implementation("com.alibaba:dashscope-sdk-java:2.20.6") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // AWS Bedrock SDK (for BedrockAdapter)
    implementation(platform("software.amazon.awssdk:bom:2.28.3"))
    implementation("software.amazon.awssdk:bedrockruntime")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

# CLI 模块 Build 配置

## 文件位置
`cli/build.gradle.kts`

## 配置内容

```kotlin
plugins {
    kotlin("jvm")
    id("org.graalvm.buildtools.native") version "0.10.4")
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
```

## 技术栈分析

| 类别 | 技术选型 |
|------|----------|
| 语言 | Kotlin (JVM) |
| CLI 框架 | Picocli 4.7.6 |
| 序列化 | Gson 2.11.0 |
| 配置解析 | SnakeYAML 2.3 |
| 测试 | JUnit Jupiter 5.11.3 + MockK 1.13.13 |
| Native 编译 | GraalVM Native Image |

## 关键特性

1. **GraalVM Native Image**: 支持将 Kotlin 应用编译为原生可执行文件
2. **Picocli**: 提供命令行交互能力，支持命令自动生成帮助文档
3. **多模块构建**: 作为 Gradle 多模块项目的子模块

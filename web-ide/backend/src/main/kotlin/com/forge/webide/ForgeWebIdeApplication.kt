package com.forge.webide

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.forge.webide", "com.forge.eval.api"])
@EnableScheduling
class ForgeWebIdeApplication

/**
 * 全局 JPA 配置。
 * 独立于主类，@WebMvcTest 的 TypeExcludeFilter 会跳过此 @Configuration，
 * 避免在 web 层测试中触发 JPA 初始化。
 *
 * 注意：@EnableJpaRepositories 会禁用 Spring Boot 自动配置，
 * 因此必须显式包含所有需要扫描的包（包括 webide 自身）。
 */
@Configuration
@EntityScan(basePackages = ["com.forge.webide.entity", "com.forge.eval.api.entity"])
@EnableJpaRepositories(basePackages = ["com.forge.webide.repository", "com.forge.eval.api.repository"])
class EvalApiJpaConfig

fun main(args: Array<String>) {
    runApplication<ForgeWebIdeApplication>(*args)
}

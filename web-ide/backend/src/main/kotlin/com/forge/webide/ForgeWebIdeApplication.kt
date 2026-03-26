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
 * forge-eval 模块的 JPA 配置。
 * 独立于主类，@WebMvcTest 的 TypeExcludeFilter 会跳过此 @Configuration，
 * 避免在 web 层测试中触发 JPA 初始化。
 *
 * webide 自身的 entity/repository 由 Spring Boot auto-config 自动发现（在主类包下）。
 * eval-api 的 entity/repository 在不同包，需要显式声明。
 */
@Configuration
@EntityScan(basePackages = ["com.forge.eval.api.entity"])
@EnableJpaRepositories(basePackages = ["com.forge.eval.api.repository"])
class EvalApiJpaConfig

fun main(args: Array<String>) {
    runApplication<ForgeWebIdeApplication>(*args)
}

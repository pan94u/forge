package com.forge.webide.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring 基础配置。
 *
 * 模型 Adapter 不再在启动时统一初始化，而是通过 [com.forge.webide.service.DynamicAdapterFactory]
 * 按请求（per-request）从用户数据库配置动态创建。
 */
@Configuration
class ClaudeConfig {

    @Bean
    fun webClient(): WebClient = WebClient.builder().build()
}

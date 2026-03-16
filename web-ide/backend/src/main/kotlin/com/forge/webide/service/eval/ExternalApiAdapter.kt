package com.forge.webide.service.eval

import org.springframework.stereotype.Component

/**
 * 外部 API Agent 适配器（Sprint 21.6 实现）。
 * 当前为 stub，调用时抛出 UnsupportedOperationException。
 */
@Component
class ExternalApiAdapter : AgentAdapter {

    override fun execute(input: String, config: AgentExecutionConfig): AgentResponse {
        throw UnsupportedOperationException("External API adapter not yet implemented — planned for Sprint 21.6")
    }
}

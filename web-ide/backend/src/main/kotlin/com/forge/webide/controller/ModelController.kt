package com.forge.webide.controller

import com.forge.adapter.model.ModelInfo
import com.forge.webide.config.ProviderDefaultModels
import com.forge.webide.service.UserModelConfigService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

/**
 * REST controller for model discovery and management.
 *
 * - /api/models         — 所有 Provider 的静态默认模型列表（无需登录）
 * - /api/models/available — 当前用户已配置 Provider 的可用模型（需登录）
 */
@RestController
@RequestMapping("/api/models")
class ModelController(
    private val userModelConfigService: UserModelConfigService
) {

    /**
     * 返回所有 Provider 的静态默认模型列表。
     * 不依赖用户配置，用于展示 Forge 支持哪些模型。
     */
    @GetMapping
    fun listAllModels(): ResponseEntity<List<ModelInfo>> {
        return ResponseEntity.ok(ProviderDefaultModels.ALL)
    }

    /**
     * 返回当前用户已配置且启用的 Provider 的可用模型列表。
     * 包含各 Provider 默认模型 + 用户自定义 model ID。
     * ModelSelector 使用此端点获取可选模型。
     */
    @GetMapping("/available")
    fun listAvailableModels(principal: Principal?): ResponseEntity<List<ModelInfo>> {
        val userId = principal?.name ?: "anonymous"
        val models = userModelConfigService.getModelsForUser(userId)
        return ResponseEntity.ok(models)
    }
}

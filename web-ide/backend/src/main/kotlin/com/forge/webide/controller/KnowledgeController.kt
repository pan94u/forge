package com.forge.webide.controller

import com.forge.webide.model.*
import com.forge.webide.service.KnowledgeIndexService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient

@RestController
@RequestMapping("/api/knowledge")
class KnowledgeController(
    private val knowledgeIndexService: KnowledgeIndexService,
    private val webClient: WebClient
) {

    @GetMapping("/search")
    fun searchKnowledge(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<List<KnowledgeDocument>> {
        val results = knowledgeIndexService.search(
            query = q ?: "",
            type = type?.let {
                try {
                    DocumentType.valueOf(it.uppercase().replace("-", "_"))
                } catch (e: IllegalArgumentException) {
                    null
                }
            },
            limit = limit
        )
        return ResponseEntity.ok(results)
    }

    @GetMapping("/docs/{id}")
    fun getDocument(
        @PathVariable id: String
    ): ResponseEntity<KnowledgeDocument> {
        val doc = knowledgeIndexService.getDocument(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(doc)
    }

    @GetMapping("/services")
    fun getServiceGraph(): ResponseEntity<ServiceGraph> {
        val graph = knowledgeIndexService.getServiceGraph()
        return ResponseEntity.ok(graph)
    }

    @GetMapping("/apis")
    fun getApiCatalog(): ResponseEntity<List<ApiService>> {
        val apis = knowledgeIndexService.getApiCatalog()
        return ResponseEntity.ok(apis)
    }

    @PostMapping("/apis/try")
    fun tryApiCall(
        @RequestBody request: ApiTryRequest
    ): ResponseEntity<Any> {
        return try {
            val response = webClient
                .method(org.springframework.http.HttpMethod.valueOf(request.method.uppercase()))
                .uri(request.url)
                .headers { headers ->
                    request.headers.forEach { (key, value) ->
                        headers.set(key, value)
                    }
                }
                .let { builder ->
                    if (request.body != null) {
                        builder.bodyValue(request.body)
                    } else {
                        builder
                    }
                }
                .retrieve()
                .bodyToMono(Any::class.java)
                .block()

            ResponseEntity.ok(response ?: emptyMap<String, Any>())
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf(
                "error" to true,
                "message" to (e.message ?: "Request failed"),
                "type" to e.javaClass.simpleName
            ))
        }
    }

    @GetMapping("/diagrams")
    fun getDiagrams(): ResponseEntity<List<ArchDiagram>> {
        val diagrams = knowledgeIndexService.getDiagrams()
        return ResponseEntity.ok(diagrams)
    }
}

package com.forge.mcp.common

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Custom serializer for [Instant] that converts to/from ISO-8601 strings.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

/**
 * An audit trail entry capturing who called what tool, when, and the outcome.
 */
@Serializable
data class AuditEntry(
    val userId: String,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    val tool: String,
    val params: JsonObject,
    val result: String,
    val durationMs: Long
) {
    companion object {
        /**
         * Convenience factory that converts a raw Map<String, Any?> to a JsonObject
         * for the params field.
         */
        fun create(
            userId: String,
            timestamp: Instant,
            tool: String,
            params: Map<String, Any?>,
            result: String,
            durationMs: Long
        ): AuditEntry = AuditEntry(
            userId = userId,
            timestamp = timestamp,
            tool = tool,
            params = mapToJsonObject(params),
            result = result,
            durationMs = durationMs
        )

        private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
            val elements = map.mapValues { (_, value) -> anyToJsonElement(value) }
            return JsonObject(elements)
        }

        private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as Map<String, Any?>)
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}

/**
 * Interface for audit trail loggers.
 */
interface AuditLogger {
    /**
     * Logs the given audit entry. Implementations should be non-blocking
     * when possible and must not throw exceptions.
     */
    fun log(entry: AuditEntry)
}

/**
 * Audit logger implementation that writes structured JSON to SLF4J.
 *
 * Each audit entry is serialized as a single-line JSON object and logged
 * at INFO level under the "com.forge.mcp.audit" logger name, making it
 * easy to filter and parse in log aggregation systems.
 */
class Slf4jAuditLogger(
    private val json: Json = Json { encodeDefaults = true }
) : AuditLogger {

    private val logger = LoggerFactory.getLogger("com.forge.mcp.audit")

    override fun log(entry: AuditEntry) {
        try {
            val serialized = json.encodeToString(AuditEntry.serializer(), entry)
            logger.info("AUDIT {}", serialized)
        } catch (e: Exception) {
            // Audit logging must never break the request flow.
            logger.error(
                "Failed to serialize audit entry for tool={} user={}: {}",
                entry.tool, entry.userId, e.message
            )
        }
    }
}

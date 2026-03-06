# mcp-servers-forge-mcp-common

Generated: 2026-03-06T21:03:44Z

## Module Structure

```
  mcp-servers/forge-mcp-common/src/main/kotlin/com/forge/mcp/common/AuditLog.kt
  mcp-servers/forge-mcp-common/src/main/kotlin/com/forge/mcp/common/AuthProvider.kt
  mcp-servers/forge-mcp-common/src/main/kotlin/com/forge/mcp/common/HealthCheck.kt
  mcp-servers/forge-mcp-common/src/main/kotlin/com/forge/mcp/common/McpProtocol.kt
  mcp-servers/forge-mcp-common/src/main/kotlin/com/forge/mcp/common/McpServerBase.kt
  mcp-servers/forge-mcp-common/src/main/kotlin/com/forge/mcp/common/Metrics.kt
```

## Public Interfaces and Classes

### main/kotlin/com/forge/mcp/common/AuditLog.kt

- `21: object InstantSerializer : KSerializer<Instant>`
- `38: data class AuditEntry(`
- `90: interface AuditLogger`
- `105: class Slf4jAuditLogger(`

### main/kotlin/com/forge/mcp/common/AuthProvider.kt

- `19: data class AuthResult(`
- `36: interface AuthProvider`
- `70: data class OAuthConfig(`
- `111: class OAuthAuthProvider(`

### main/kotlin/com/forge/mcp/common/HealthCheck.kt

- `13: data class HealthStatus(`
- `34: interface HealthCheckProvider`

### main/kotlin/com/forge/mcp/common/McpProtocol.kt

- `13: data class ToolDefinition(`
- `20: data class ToolCallRequest(`
- `26: data class ToolCallResponse(`
- `33: sealed class ToolContent`
- `48: data class ToolListResponse(`
- `53: sealed class McpError(`
- `84: data class McpErrorResponse(`

### main/kotlin/com/forge/mcp/common/McpServerBase.kt

- `40: abstract class McpServerBase(`
- `266: data class McpPrincipal(`

### main/kotlin/com/forge/mcp/common/Metrics.kt

- `15: data class MetricsSnapshot(`
- `26: data class ToolMetrics(`
- `45: object McpMetrics`


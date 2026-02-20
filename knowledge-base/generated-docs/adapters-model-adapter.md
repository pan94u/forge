# adapters-model-adapter

Generated: 2026-02-20T13:33:20Z

## Module Structure

```
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/BedrockAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ClaudeAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/LocalModelAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ModelAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/Models.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/StreamEvent.kt
  adapters/model-adapter/src/test/kotlin/com/forge/adapter/model/ClaudeAdapterToolCallingTest.kt
```

## Public Interfaces and Classes

### main/kotlin/com/forge/adapter/model/BedrockAdapter.kt

- `21: class BedrockAdapter(`

### main/kotlin/com/forge/adapter/model/ClaudeAdapter.kt

- `28: class ClaudeAdapter(`

### main/kotlin/com/forge/adapter/model/LocalModelAdapter.kt

- `28: class LocalModelAdapter(`

### main/kotlin/com/forge/adapter/model/ModelAdapter.kt

- `20: interface ModelAdapter`
- `98: class AuthenticationException(message: String, cause: Throwable? = null) :`
- `104: class RateLimitException(`
- `113: class ModelNotAvailableException(message: String, cause: Throwable? = null) :`

### main/kotlin/com/forge/adapter/model/Models.kt

- `6: data class CompletionOptions(`
- `42: data class Message(`
- `54: data class CompletionResult(`
- `77: data class TokenUsage(`
- `87: enum class StopReason`
- `103: data class ModelInfo(`
- `129: enum class CostTier`
- `138: data class ToolDefinition(`
- `147: data class ToolUse(`
- `156: data class ToolResult(`

### main/kotlin/com/forge/adapter/model/StreamEvent.kt

- `16: sealed class StreamEvent`

### test/kotlin/com/forge/adapter/model/ClaudeAdapterToolCallingTest.kt

- `19: class ClaudeAdapterToolCallingTest`


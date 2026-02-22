# adapters-model-adapter

Generated: 2026-02-22T01:35:24Z

## Module Structure

```
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/BedrockAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ClaudeAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/GeminiAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/LocalModelAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ModelAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/ModelRegistry.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/Models.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/QwenAdapter.kt
  adapters/model-adapter/src/main/kotlin/com/forge/adapter/model/StreamEvent.kt
  adapters/model-adapter/src/test/kotlin/com/forge/adapter/model/ClaudeAdapterToolCallingTest.kt
  adapters/model-adapter/src/test/kotlin/com/forge/adapter/model/GeminiAdapterTest.kt
  adapters/model-adapter/src/test/kotlin/com/forge/adapter/model/ModelRegistryTest.kt
  adapters/model-adapter/src/test/kotlin/com/forge/adapter/model/QwenAdapterTest.kt
```

## Public Interfaces and Classes

### main/kotlin/com/forge/adapter/model/BedrockAdapter.kt

- `42: class BedrockAdapter(`

### main/kotlin/com/forge/adapter/model/ClaudeAdapter.kt

- `28: class ClaudeAdapter(`

### main/kotlin/com/forge/adapter/model/GeminiAdapter.kt

- `28: class GeminiAdapter(`

### main/kotlin/com/forge/adapter/model/LocalModelAdapter.kt

- `28: class LocalModelAdapter(`

### main/kotlin/com/forge/adapter/model/ModelAdapter.kt

- `20: interface ModelAdapter`
- `98: class AuthenticationException(message: String, cause: Throwable? = null) :`
- `104: class RateLimitException(`
- `113: class ModelNotAvailableException(message: String, cause: Throwable? = null) :`

### main/kotlin/com/forge/adapter/model/ModelRegistry.kt

- `30: class ModelRegistry(`
- `124: data class RegistrySummary(`

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

### main/kotlin/com/forge/adapter/model/QwenAdapter.kt

- `28: class QwenAdapter(`

### main/kotlin/com/forge/adapter/model/StreamEvent.kt

- `16: sealed class StreamEvent`

### test/kotlin/com/forge/adapter/model/ClaudeAdapterToolCallingTest.kt

- `19: class ClaudeAdapterToolCallingTest`

### test/kotlin/com/forge/adapter/model/GeminiAdapterTest.kt

- `13: class GeminiAdapterTest`

### test/kotlin/com/forge/adapter/model/ModelRegistryTest.kt

- `12: class ModelRegistryTest`

### test/kotlin/com/forge/adapter/model/QwenAdapterTest.kt

- `13: class QwenAdapterTest`


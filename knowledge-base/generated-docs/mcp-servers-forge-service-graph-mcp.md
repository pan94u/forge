# mcp-servers-forge-service-graph-mcp

Generated: 2026-02-25T11:41:59Z

## Module Structure

```
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/ServiceGraphMcpServer.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/indexer/ApmTraceIndexer.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/indexer/GradleIndexer.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/indexer/K8sIndexer.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/indexer/OpenApiIndexer.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/tools/CallChainTool.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/tools/DependenciesTool.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/tools/ImpactAnalysisTool.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/tools/OwnerTool.kt
  mcp-servers/forge-service-graph-mcp/src/main/kotlin/com/forge/mcp/servicegraph/tools/ServiceListTool.kt
```

## Public Interfaces and Classes

### main/kotlin/com/forge/mcp/servicegraph/ServiceGraphMcpServer.kt

- `26: interface McpTool`
- `35: object ServiceGraphStore`
- `153: class ServiceGraphMcpServer`

### main/kotlin/com/forge/mcp/servicegraph/indexer/ApmTraceIndexer.kt

- `26: class ApmTraceIndexer(`

### main/kotlin/com/forge/mcp/servicegraph/indexer/GradleIndexer.kt

- `16: class GradleIndexer(`

### main/kotlin/com/forge/mcp/servicegraph/indexer/K8sIndexer.kt

- `25: class K8sIndexer(`

### main/kotlin/com/forge/mcp/servicegraph/indexer/OpenApiIndexer.kt

- `24: class OpenApiIndexer(`

### main/kotlin/com/forge/mcp/servicegraph/tools/CallChainTool.kt

- `19: class CallChainTool : McpTool`

### main/kotlin/com/forge/mcp/servicegraph/tools/DependenciesTool.kt

- `18: class DependenciesTool : McpTool`

### main/kotlin/com/forge/mcp/servicegraph/tools/ImpactAnalysisTool.kt

- `19: class ImpactAnalysisTool : McpTool`

### main/kotlin/com/forge/mcp/servicegraph/tools/OwnerTool.kt

- `18: class OwnerTool : McpTool`

### main/kotlin/com/forge/mcp/servicegraph/tools/ServiceListTool.kt

- `19: class ServiceListTool : McpTool`


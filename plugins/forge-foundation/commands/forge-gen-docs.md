---
name: forge-gen-docs
description: Generate documentation for code
trigger: /forge-gen-docs
---

# /forge-gen-docs — Generate Documentation

Analyze code and generate missing documentation:

1. Scan for undocumented public APIs
2. Generate API documentation from code + OpenAPI specs
3. Create/update README for each module
4. Generate architecture diagrams (Mermaid)
5. Save to `knowledge-base/generated-docs/`
6. Create PR for human review

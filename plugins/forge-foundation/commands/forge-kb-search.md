---
name: forge-kb-search
description: Search the knowledge base
trigger: /forge-kb-search
---

# /forge-kb-search — Knowledge Base Search

Search across all knowledge sources via MCP tools:

1. Use `forge-knowledge.wiki_search` for wiki/documentation
2. Use `forge-knowledge.adr_search` for Architecture Decision Records
3. Use `forge-knowledge.runbook_search` for operational runbooks
4. Use `forge-knowledge.api_doc_search` for API documentation

Display results in a formatted list with titles, excerpts, and links.
If no results found, log the knowledge gap via `forge-knowledge.gap_log`.

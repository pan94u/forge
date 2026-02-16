# /search-adr

## Purpose

Search Architecture Decision Records (ADRs) to understand past architectural decisions, their context, rationale, and consequences.

## Usage

```
/search-adr <query> [--status <status>] [--since <date>]
```

## Steps

1. Parse the user's search query and filters
2. Call the `forge-knowledge.search_adr` MCP tool with parameters
3. For each matching ADR, display number, title, status, decision date, and decision summary
4. Note if any ADR has been superseded
5. If no ADR exists, suggest creating one if the topic warrants it

## ADR Format Reference

- **Title**: Short descriptive title with ADR number
- **Status**: Proposed, Accepted, Deprecated, Superseded
- **Context**: The issue motivating the decision
- **Decision**: The change being proposed or enacted
- **Consequences**: What becomes easier or harder as a result

## Error Handling

- If the ADR directory is not configured, suggest setting `adr_directory` in plugin config
- If a superseding ADR reference is broken, warn about the broken reference

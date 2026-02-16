# /search-runbooks

## Purpose

Search operational runbooks for procedures related to incidents, deployments, scaling, and troubleshooting. Runbooks contain step-by-step guides for common and emergency scenarios.

## Usage

```
/search-runbooks <query> [--severity <level>] [--category <category>]
```

## Steps

1. Parse the user's search query and filters
2. Call the `forge-knowledge.search_runbooks` MCP tool with parameters
3. For each matching runbook, display title, severity, summary, and first 3 steps
4. Ask the user if they want to see the full runbook

## Error Handling

- If no runbooks are found, suggest checking the runbooks directory
- If the MCP server is unreachable, suggest using `forge doctor`
- Warn if viewing a runbook last updated more than 90 days ago

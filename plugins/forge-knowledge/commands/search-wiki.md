# /search-wiki

## Purpose

Search the internal wiki (Confluence, Notion, or local knowledge base) for documentation relevant to the current task. Returns summarized results with source links.

## Usage

```
/search-wiki <query> [--space <space-key>] [--limit <n>]
```

**Arguments:**
- `query` (required): The search query describing what you need to find
- `--space`: Limit search to a specific wiki space (e.g., "ARCH", "OPS", "DEV")
- `--limit`: Maximum number of results to return (default: 5)

## Steps

1. Parse the user's search query and any filters
2. Call the `forge-knowledge.search_wiki` MCP tool with the query
3. For each result returned:
   - Display the page title and space
   - Show a brief excerpt (first 200 characters of matching content)
   - Provide the source URL
4. If no results are found:
   - Suggest alternative search terms
   - Recommend checking the knowledge base profiles directory
5. Summarize the most relevant finding and how it relates to the current task

## Error Handling

- If the wiki endpoint is not configured, inform the user and suggest checking `.forge.json` configuration
- If the MCP server is not running, suggest starting it with `forge mcp status`
- If the search returns no results, suggest broadening the query

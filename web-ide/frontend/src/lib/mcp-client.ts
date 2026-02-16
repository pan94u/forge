export interface McpTool {
  name: string;
  description: string;
  inputSchema: {
    type: string;
    properties: Record<
      string,
      {
        type: string;
        description?: string;
        enum?: string[];
        default?: unknown;
      }
    >;
    required?: string[];
  };
}

export interface McpToolCallResult {
  content: Array<{
    type: "text" | "image" | "resource";
    text?: string;
    data?: string;
    mimeType?: string;
    uri?: string;
  }>;
  isError: boolean;
}

export interface McpResource {
  uri: string;
  name: string;
  description?: string;
  mimeType?: string;
}

class McpClient {
  private baseUrl: string;

  constructor(baseUrl: string = "") {
    this.baseUrl = baseUrl;
  }

  /**
   * Discover available MCP tools from all connected servers.
   */
  async listTools(): Promise<McpTool[]> {
    const response = await fetch(`${this.baseUrl}/api/mcp/tools`);
    if (!response.ok) {
      throw new Error(`Failed to list MCP tools: ${response.status}`);
    }
    return response.json();
  }

  /**
   * Call an MCP tool by name with the given arguments.
   */
  async callTool(
    toolName: string,
    args: Record<string, unknown>
  ): Promise<McpToolCallResult> {
    const response = await fetch(`${this.baseUrl}/api/mcp/tools/call`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: toolName,
        arguments: args,
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(
        `MCP tool call failed (${response.status}): ${errorText}`
      );
    }

    return response.json();
  }

  /**
   * List available MCP resources.
   */
  async listResources(): Promise<McpResource[]> {
    const response = await fetch(`${this.baseUrl}/api/mcp/resources`);
    if (!response.ok) {
      throw new Error(`Failed to list MCP resources: ${response.status}`);
    }
    return response.json();
  }

  /**
   * Read an MCP resource by URI.
   */
  async readResource(
    uri: string
  ): Promise<{
    contents: Array<{
      uri: string;
      mimeType?: string;
      text?: string;
      blob?: string;
    }>;
  }> {
    const response = await fetch(
      `${this.baseUrl}/api/mcp/resources/read?uri=${encodeURIComponent(uri)}`
    );
    if (!response.ok) {
      throw new Error(`Failed to read MCP resource: ${response.status}`);
    }
    return response.json();
  }

  /**
   * Get tool schema details for a specific tool.
   */
  async getToolSchema(toolName: string): Promise<McpTool | null> {
    const tools = await this.listTools();
    return tools.find((t) => t.name === toolName) ?? null;
  }

  /**
   * Parse a tool call result into a readable string.
   */
  static formatResult(result: McpToolCallResult): string {
    if (result.isError) {
      const errorText = result.content
        .filter((c) => c.type === "text")
        .map((c) => c.text)
        .join("\n");
      return `Error: ${errorText || "Unknown error"}`;
    }

    return result.content
      .map((c) => {
        switch (c.type) {
          case "text":
            return c.text ?? "";
          case "image":
            return `[Image: ${c.mimeType ?? "image"}]`;
          case "resource":
            return `[Resource: ${c.uri ?? "unknown"}]`;
          default:
            return "";
        }
      })
      .join("\n");
  }
}

export const mcpClient = new McpClient();

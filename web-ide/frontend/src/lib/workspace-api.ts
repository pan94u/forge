export interface Workspace {
  id: string;
  name: string;
  description: string;
  status: "active" | "suspended" | "creating" | "error";
  createdAt: string;
  updatedAt: string;
  owner: string;
  repository?: string;
  branch?: string;
  errorMessage?: string;
}

export interface FileNode {
  name: string;
  path: string;
  type: "file" | "directory";
  size?: number;
  children?: FileNode[];
}

export interface CreateWorkspaceRequest {
  name: string;
  description?: string;
  repository?: string;
  branch?: string;
  template?: string;
}

function getAuthHeader(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("forge_access_token");
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

function handleAuthError(response: Response): void {
  if (response.status === 401 && typeof window !== "undefined") {
    // Redirect to login on auth failure
    window.location.href = "/login";
  }
}

class WorkspaceApi {
  private baseUrl: string;

  constructor(baseUrl: string = "") {
    this.baseUrl = baseUrl;
  }

  private headers(extra: Record<string, string> = {}): Record<string, string> {
    return { ...getAuthHeader(), ...extra };
  }

  /**
   * Create a new workspace.
   */
  async createWorkspace(request: CreateWorkspaceRequest): Promise<Workspace> {
    const response = await fetch(`${this.baseUrl}/api/workspaces`, {
      method: "POST",
      headers: this.headers({ "Content-Type": "application/json" }),
      body: JSON.stringify(request),
    });

    handleAuthError(response);
    if (!response.ok) {
      const error = await response.text();
      throw new Error(`Failed to create workspace: ${error}`);
    }

    return response.json();
  }

  /**
   * List all workspaces for the current user.
   */
  async listWorkspaces(): Promise<Workspace[]> {
    const response = await fetch(`${this.baseUrl}/api/workspaces`, {
      headers: this.headers(),
    });

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to list workspaces: ${response.status}`);
    }

    return response.json();
  }

  /**
   * Get workspace details by ID.
   */
  async getWorkspace(id: string): Promise<Workspace> {
    const response = await fetch(`${this.baseUrl}/api/workspaces/${id}`, {
      headers: this.headers(),
    });

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to get workspace: ${response.status}`);
    }

    return response.json();
  }

  /**
   * Delete a workspace.
   */
  async deleteWorkspace(id: string): Promise<void> {
    const response = await fetch(`${this.baseUrl}/api/workspaces/${id}`, {
      method: "DELETE",
      headers: this.headers(),
    });

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to delete workspace: ${response.status}`);
    }
  }

  /**
   * Activate a workspace (start code-server pod).
   */
  async activateWorkspace(id: string): Promise<Workspace> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${id}/activate`,
      { method: "POST", headers: this.headers() }
    );

    if (!response.ok) {
      throw new Error(`Failed to activate workspace: ${response.status}`);
    }

    return response.json();
  }

  /**
   * Suspend a workspace.
   */
  async suspendWorkspace(id: string): Promise<Workspace> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${id}/suspend`,
      { method: "POST", headers: this.headers() }
    );

    if (!response.ok) {
      throw new Error(`Failed to suspend workspace: ${response.status}`);
    }

    return response.json();
  }

  /**
   * Get file tree for a workspace.
   */
  async getFileTree(workspaceId: string): Promise<FileNode[]> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${workspaceId}/files`,
      { headers: this.headers() }
    );

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to get file tree: ${response.status}`);
    }

    return response.json();
  }

  /**
   * Get file content.
   */
  async getFileContent(
    workspaceId: string,
    filePath: string
  ): Promise<string> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${workspaceId}/files/content?path=${encodeURIComponent(filePath)}`,
      { headers: this.headers() }
    );

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to get file content: ${response.status}`);
    }

    return response.text();
  }

  /**
   * Save file content.
   */
  async saveFile(
    workspaceId: string,
    filePath: string,
    content: string
  ): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${workspaceId}/files/content`,
      {
        method: "PUT",
        headers: this.headers({ "Content-Type": "application/json" }),
        body: JSON.stringify({ path: filePath, content }),
      }
    );

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to save file: ${response.status}`);
    }
  }

  /**
   * Create a new file.
   */
  async createFile(
    workspaceId: string,
    filePath: string,
    content: string = ""
  ): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${workspaceId}/files`,
      {
        method: "POST",
        headers: this.headers({ "Content-Type": "application/json" }),
        body: JSON.stringify({ path: filePath, content }),
      }
    );

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to create file: ${response.status}`);
    }
  }

  /**
   * Delete a file.
   */
  async deleteFile(
    workspaceId: string,
    filePath: string
  ): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/workspaces/${workspaceId}/files?path=${encodeURIComponent(filePath)}`,
      { method: "DELETE", headers: this.headers() }
    );

    handleAuthError(response);
    if (!response.ok) {
      throw new Error(`Failed to delete file: ${response.status}`);
    }
  }

  /**
   * Get terminal WebSocket URL for a workspace.
   */
  getTerminalWsUrl(workspaceId: string): string {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = this.baseUrl || window.location.host;
    return `${protocol}//${host}/ws/terminal/${workspaceId}`;
  }
}

export const workspaceApi = new WorkspaceApi();

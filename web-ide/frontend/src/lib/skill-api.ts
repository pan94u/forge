export interface SkillView {
  name: string;
  description: string;
  tags: string[];
  scope: string;
  category: string;
  version: string;
  author: string;
  enabled: boolean;
  subFileCount: number;
  scriptCount: number;
  subFiles: SubFileView[];
  scripts: ScriptView[];
}

export interface SkillDetailView {
  name: string;
  description: string;
  tags: string[];
  scope: string;
  category: string;
  version: string;
  author: string;
  enabled: boolean;
  content: string;
  subFiles: SubFileView[];
  scripts: ScriptView[];
}

export interface SubFileView {
  path: string;
  description: string;
  type: string;
}

export interface ScriptView {
  path: string;
  description: string;
  language: string;
  scriptType: string;
}

export interface ScriptResult {
  exitCode: number;
  stdout: string;
  stderr: string;
}

export interface CreateSkillRequest {
  name: string;
  description: string;
  tags: string[];
  content: string;
}

export interface UpdateSkillRequest {
  description?: string;
  tags?: string[];
  content?: string;
}

function getAuthHeader(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("forge_access_token");
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

function handleAuthError(response: Response): void {
  if (response.status === 401 && typeof window !== "undefined") {
    window.location.href = "/login";
  }
}

class SkillApi {
  private baseUrl: string;

  constructor(baseUrl: string = "") {
    this.baseUrl = baseUrl;
  }

  private headers(extra: Record<string, string> = {}): Record<string, string> {
    return { ...getAuthHeader(), ...extra };
  }

  async listSkills(params?: {
    workspaceId?: string;
    scope?: string;
    category?: string;
  }): Promise<SkillView[]> {
    const query = new URLSearchParams();
    if (params?.workspaceId) query.set("workspaceId", params.workspaceId);
    if (params?.scope) query.set("scope", params.scope);
    if (params?.category) query.set("category", params.category);
    const qs = query.toString();

    const response = await fetch(
      `${this.baseUrl}/api/skills${qs ? `?${qs}` : ""}`,
      { headers: this.headers() }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to list skills: ${response.status}`);
    return response.json();
  }

  async getSkill(name: string, workspaceId?: string): Promise<SkillDetailView> {
    const query = workspaceId ? `?workspaceId=${encodeURIComponent(workspaceId)}` : "";
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}${query}`,
      { headers: this.headers() }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to get skill: ${response.status}`);
    return response.json();
  }

  async readSkillContent(name: string, subPath: string): Promise<string> {
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}/content/${subPath}`,
      { headers: this.headers() }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to read skill content: ${response.status}`);
    const data = await response.json();
    return data.content;
  }

  async createSkill(workspaceId: string, request: CreateSkillRequest): Promise<SkillView> {
    const response = await fetch(
      `${this.baseUrl}/api/skills?workspaceId=${encodeURIComponent(workspaceId)}`,
      {
        method: "POST",
        headers: this.headers({ "Content-Type": "application/json" }),
        body: JSON.stringify(request),
      }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to create skill: ${response.status}`);
    return response.json();
  }

  async updateSkill(workspaceId: string, name: string, request: UpdateSkillRequest): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}?workspaceId=${encodeURIComponent(workspaceId)}`,
      {
        method: "PUT",
        headers: this.headers({ "Content-Type": "application/json" }),
        body: JSON.stringify(request),
      }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to update skill: ${response.status}`);
  }

  async deleteSkill(workspaceId: string, name: string): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}?workspaceId=${encodeURIComponent(workspaceId)}`,
      { method: "DELETE", headers: this.headers() }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to delete skill: ${response.status}`);
  }

  async enableSkill(workspaceId: string, name: string): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}/enable?workspaceId=${encodeURIComponent(workspaceId)}`,
      { method: "POST", headers: this.headers() }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to enable skill: ${response.status}`);
  }

  async disableSkill(workspaceId: string, name: string): Promise<void> {
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}/disable?workspaceId=${encodeURIComponent(workspaceId)}`,
      { method: "POST", headers: this.headers() }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to disable skill: ${response.status}`);
  }

  async runScript(name: string, scriptPath: string, args: string[] = []): Promise<ScriptResult> {
    const response = await fetch(
      `${this.baseUrl}/api/skills/${encodeURIComponent(name)}/scripts/${scriptPath}`,
      {
        method: "POST",
        headers: this.headers({ "Content-Type": "application/json" }),
        body: JSON.stringify({ args }),
      }
    );
    handleAuthError(response);
    if (!response.ok) throw new Error(`Failed to run script: ${response.status}`);
    return response.json();
  }
}

export const skillApi = new SkillApi();

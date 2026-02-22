export interface ModelInfo {
  id: string;
  displayName: string;
  provider: string;
  contextWindow: number;
  maxOutputTokens: number;
  supportsStreaming: boolean;
  supportsVision: boolean;
  costTier: "LOW" | "MEDIUM" | "HIGH";
}

export interface ProviderSummary {
  providers: string[];
  totalModels: number;
  defaultProvider: string;
  modelsByProvider: Record<string, number>;
}

export async function fetchModels(): Promise<ModelInfo[]> {
  const res = await fetch("/api/models");
  if (!res.ok) throw new Error(`Failed to fetch models: ${res.status}`);
  return res.json();
}

export async function fetchProviders(): Promise<ProviderSummary> {
  const res = await fetch("/api/models/providers");
  if (!res.ok) throw new Error(`Failed to fetch providers: ${res.status}`);
  return res.json();
}

export async function fetchHealthCheck(): Promise<Record<string, boolean>> {
  const res = await fetch("/api/models/health");
  if (!res.ok) throw new Error(`Failed to fetch health: ${res.status}`);
  return res.json();
}

// --- User Model Config API ---

export interface UserModelConfigView {
  provider: string;
  hasApiKey: boolean;
  apiKeyMasked: string;
  baseUrl: string;
  region: string;
  enabled: boolean;
  customModels: string[];
  updatedAt: string;
}

export interface UserModelConfigRequest {
  provider: string;
  apiKey?: string;
  baseUrl?: string;
  region?: string;
  enabled?: boolean;
  customModels?: string[];
}

/**
 * 获取当前用户已配置且启用的 Provider 的可用模型列表。
 * ModelSelector 使用此端点，只显示用户可实际使用的模型。
 */
export async function fetchAvailableModels(): Promise<ModelInfo[]> {
  const res = await fetch("/api/models/available");
  if (!res.ok)
    throw new Error(`Failed to fetch available models: ${res.status}`);
  return res.json();
}

export async function fetchUserModelConfigs(): Promise<UserModelConfigView[]> {
  const res = await fetch("/api/user/model-configs");
  if (!res.ok) throw new Error(`Failed to fetch user configs: ${res.status}`);
  return res.json();
}

export async function saveUserModelConfig(
  provider: string,
  config: UserModelConfigRequest,
): Promise<UserModelConfigView> {
  const res = await fetch(`/api/user/model-configs/${provider}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(config),
  });
  if (!res.ok) throw new Error(`Failed to save config: ${res.status}`);
  return res.json();
}

export async function deleteUserModelConfig(provider: string): Promise<void> {
  const res = await fetch(`/api/user/model-configs/${provider}`, {
    method: "DELETE",
  });
  if (!res.ok) throw new Error(`Failed to delete config: ${res.status}`);
}

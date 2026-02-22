"use client";

import React, { useState, useEffect, useCallback } from "react";
import { X, Eye, EyeOff, Check, Loader2, Trash2, Plus } from "lucide-react";
import {
  fetchUserModelConfigs,
  saveUserModelConfig,
  deleteUserModelConfig,
  type UserModelConfigView,
} from "@/lib/model-api";

// Provider 定义及各自的默认模型
const PROVIDERS = [
  {
    id: "anthropic",
    name: "Anthropic (Claude)",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-ant-...",
    defaultModels: [
      "claude-opus-4-6",
      "claude-sonnet-4-6",
      "claude-haiku-4-5",
    ],
  },
  {
    id: "google",
    name: "Google (Gemini)",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "AIza...",
    defaultModels: [
      "gemini-2.5-pro",
      "gemini-2.5-flash",
      "gemini-2.5-flash-lite",
    ],
  },
  {
    id: "dashscope",
    name: "Alibaba (Qwen)",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-...",
    defaultModels: ["qwen3.5-plus", "qwen-plus", "qwen-turbo", "qwen-long"],
  },
  {
    id: "aws-bedrock",
    name: "AWS Bedrock",
    fields: ["region"] as const,
    keyPlaceholder: "",
    defaultModels: [
      "anthropic.claude-opus-4-6-v1",
      "anthropic.claude-sonnet-4-6",
    ],
  },
  {
    id: "openai",
    name: "OpenAI Compatible",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-...",
    defaultModels: ["gpt-4o", "gpt-4o-mini"],
  },
];

interface ModelSettingsDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSaved?: () => void;
}

export function ModelSettingsDialog({
  isOpen,
  onClose,
  onSaved,
}: ModelSettingsDialogProps) {
  const [configs, setConfigs] = useState<UserModelConfigView[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [formData, setFormData] = useState<
    Record<
      string,
      {
        apiKey: string;
        baseUrl: string;
        region: string;
        enabled: boolean;
        customModels: string[];
        newModelInput: string;
      }
    >
  >({});
  const [showKeys, setShowKeys] = useState<Record<string, boolean>>({});
  const [message, setMessage] = useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);

  const loadConfigs = useCallback(async () => {
    try {
      const data = await fetchUserModelConfigs();
      setConfigs(data);

      const form: typeof formData = {};
      for (const p of PROVIDERS) {
        const existing = data.find((c) => c.provider === p.id);
        form[p.id] = {
          apiKey: "",
          baseUrl: existing?.baseUrl ?? "",
          region: existing?.region ?? "",
          enabled: existing?.enabled ?? false,
          customModels: existing?.customModels ?? [],
          newModelInput: "",
        };
      }
      setFormData(form);
    } catch {
      // Ignore - may not be configured
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (isOpen) {
      setLoading(true);
      loadConfigs();
    }
  }, [isOpen, loadConfigs]);

  if (!isOpen) return null;

  const handleSave = async (providerId: string) => {
    setSaving(providerId);
    setMessage(null);
    try {
      const data = formData[providerId];
      await saveUserModelConfig(providerId, {
        provider: providerId,
        apiKey: data.apiKey,
        baseUrl: data.baseUrl,
        region: data.region,
        enabled: data.enabled,
        customModels: data.customModels,
      });
      setMessage({ type: "success", text: `${providerId} 配置已保存` });
      setFormData((prev) => ({
        ...prev,
        [providerId]: { ...prev[providerId], apiKey: "" },
      }));
      await loadConfigs();
      onSaved?.();
    } catch (err) {
      setMessage({
        type: "error",
        text: `保存失败: ${err instanceof Error ? err.message : "Unknown error"}`,
      });
    } finally {
      setSaving(null);
    }
  };

  const handleDelete = async (providerId: string) => {
    setSaving(providerId);
    setMessage(null);
    try {
      await deleteUserModelConfig(providerId);
      setMessage({ type: "success", text: `${providerId} 配置已删除` });
      await loadConfigs();
      onSaved?.();
    } catch (err) {
      setMessage({
        type: "error",
        text: `删除失败: ${err instanceof Error ? err.message : "Unknown error"}`,
      });
    } finally {
      setSaving(null);
    }
  };

  const updateField = (
    providerId: string,
    field: string,
    value: string | boolean | string[],
  ) => {
    setFormData((prev) => ({
      ...prev,
      [providerId]: { ...prev[providerId], [field]: value },
    }));
  };

  const addCustomModel = (providerId: string) => {
    const data = formData[providerId];
    const modelId = data.newModelInput.trim();
    if (!modelId) return;
    const existing = [
      ...(PROVIDERS.find((p) => p.id === providerId)?.defaultModels ?? []),
      ...data.customModels,
    ];
    if (existing.includes(modelId)) return;
    updateField(providerId, "customModels", [...data.customModels, modelId]);
    updateField(providerId, "newModelInput", "");
  };

  const removeCustomModel = (providerId: string, modelId: string) => {
    const data = formData[providerId];
    updateField(
      providerId,
      "customModels",
      data.customModels.filter((m) => m !== modelId),
    );
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50">
      <div className="w-full max-w-lg rounded-lg border border-border bg-background shadow-xl max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h2 className="text-sm font-semibold">Model Provider Settings</h2>
          <button onClick={onClose} className="rounded p-1 hover:bg-accent">
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto p-4 space-y-4">
          {message && (
            <div
              className={`rounded-md px-3 py-2 text-xs ${
                message.type === "success"
                  ? "bg-green-500/10 text-green-600"
                  : "bg-red-500/10 text-red-600"
              }`}
            >
              {message.text}
            </div>
          )}

          {loading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : (
            PROVIDERS.map((provider) => {
              const config = configs.find((c) => c.provider === provider.id);
              const data = formData[provider.id];
              if (!data) return null;

              return (
                <div
                  key={provider.id}
                  className="rounded-lg border border-border p-3 space-y-2"
                >
                  {/* Provider header */}
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <label className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={data.enabled}
                          onChange={(e) =>
                            updateField(
                              provider.id,
                              "enabled",
                              e.target.checked,
                            )
                          }
                          className="h-3.5 w-3.5 rounded border-border"
                        />
                        <span className="text-xs font-medium">
                          {provider.name}
                        </span>
                      </label>
                      {config?.hasApiKey && (
                        <span className="text-[10px] text-green-600 bg-green-500/10 px-1.5 py-0.5 rounded">
                          configured
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-1">
                      {config && (
                        <button
                          onClick={() => handleDelete(provider.id)}
                          disabled={saving === provider.id}
                          className="rounded p-1 text-muted-foreground hover:text-red-500 hover:bg-red-500/10"
                          title="Delete config"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      )}
                      <button
                        onClick={() => handleSave(provider.id)}
                        disabled={saving === provider.id}
                        className="flex items-center gap-1 rounded px-2 py-1 text-[10px] bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                      >
                        {saving === provider.id ? (
                          <Loader2 className="h-3 w-3 animate-spin" />
                        ) : (
                          <Check className="h-3 w-3" />
                        )}
                        Save
                      </button>
                    </div>
                  </div>

                  {/* Fields */}
                  {data.enabled && (
                    <div className="space-y-2 pt-1">
                      {provider.fields.includes("apiKey" as never) && (
                        <div>
                          <label className="text-[10px] text-muted-foreground block mb-0.5">
                            API Key
                            {config?.hasApiKey && (
                              <span className="ml-1 text-muted-foreground">
                                (current: {config.apiKeyMasked})
                              </span>
                            )}
                          </label>
                          <div className="relative">
                            <input
                              type={showKeys[provider.id] ? "text" : "password"}
                              value={data.apiKey}
                              onChange={(e) =>
                                updateField(provider.id, "apiKey", e.target.value)
                              }
                              placeholder={
                                config?.hasApiKey
                                  ? "Leave empty to keep current key"
                                  : provider.keyPlaceholder
                              }
                              className="w-full rounded border border-border bg-background px-2 py-1 text-xs pr-8"
                            />
                            <button
                              type="button"
                              onClick={() =>
                                setShowKeys((prev) => ({
                                  ...prev,
                                  [provider.id]: !prev[provider.id],
                                }))
                              }
                              className="absolute right-1 top-1/2 -translate-y-1/2 p-0.5 text-muted-foreground hover:text-foreground"
                            >
                              {showKeys[provider.id] ? (
                                <EyeOff className="h-3 w-3" />
                              ) : (
                                <Eye className="h-3 w-3" />
                              )}
                            </button>
                          </div>
                        </div>
                      )}

                      {provider.fields.includes("baseUrl" as never) && (
                        <div>
                          <label className="text-[10px] text-muted-foreground block mb-0.5">
                            Base URL (optional)
                          </label>
                          <input
                            type="text"
                            value={data.baseUrl}
                            onChange={(e) =>
                              updateField(provider.id, "baseUrl", e.target.value)
                            }
                            placeholder="Leave empty for default"
                            className="w-full rounded border border-border bg-background px-2 py-1 text-xs"
                          />
                        </div>
                      )}

                      {provider.fields.includes("region" as never) && (
                        <div>
                          <label className="text-[10px] text-muted-foreground block mb-0.5">
                            AWS Region
                          </label>
                          <input
                            type="text"
                            value={data.region}
                            onChange={(e) =>
                              updateField(provider.id, "region", e.target.value)
                            }
                            placeholder="us-east-1"
                            className="w-full rounded border border-border bg-background px-2 py-1 text-xs"
                          />
                        </div>
                      )}

                      {/* 默认模型展示 */}
                      <div>
                        <label className="text-[10px] text-muted-foreground block mb-1">
                          Available Models
                        </label>
                        <div className="flex flex-wrap gap-1">
                          {provider.defaultModels.map((modelId) => (
                            <span
                              key={modelId}
                              className="text-[10px] bg-accent text-accent-foreground px-1.5 py-0.5 rounded"
                            >
                              {modelId}
                            </span>
                          ))}
                          {data.customModels.map((modelId) => (
                            <span
                              key={modelId}
                              className="flex items-center gap-0.5 text-[10px] bg-primary/10 text-primary px-1.5 py-0.5 rounded"
                            >
                              {modelId}
                              <button
                                type="button"
                                onClick={() =>
                                  removeCustomModel(provider.id, modelId)
                                }
                                className="hover:text-red-500"
                              >
                                <X className="h-2.5 w-2.5" />
                              </button>
                            </span>
                          ))}
                        </div>
                        {/* 添加自定义 model ID */}
                        <div className="flex gap-1 mt-1.5">
                          <input
                            type="text"
                            value={data.newModelInput}
                            onChange={(e) =>
                              updateField(
                                provider.id,
                                "newModelInput",
                                e.target.value,
                              )
                            }
                            onKeyDown={(e) => {
                              if (e.key === "Enter")
                                addCustomModel(provider.id);
                            }}
                            placeholder="Add custom model ID..."
                            className="flex-1 rounded border border-border bg-background px-2 py-1 text-[10px]"
                          />
                          <button
                            type="button"
                            onClick={() => addCustomModel(provider.id)}
                            className="flex items-center gap-0.5 rounded px-1.5 py-1 text-[10px] border border-border hover:bg-accent"
                          >
                            <Plus className="h-2.5 w-2.5" />
                            Add
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-border px-4 py-2">
          <p className="text-[10px] text-muted-foreground">
            API Keys are encrypted and stored securely. Configure a provider to
            use its models in chat.
          </p>
        </div>
      </div>
    </div>
  );
}

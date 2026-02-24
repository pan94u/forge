"use client";

import React, { useState, useEffect, useCallback } from "react";
import { X, Eye, EyeOff, Check, Loader2, Trash2 } from "lucide-react";
import {
  fetchUserModelConfigs,
  saveUserModelConfig,
  deleteUserModelConfig,
  type UserModelConfigView,
} from "@/lib/model-api";

const PROVIDERS = [
  {
    id: "anthropic",
    name: "Anthropic (Claude)",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-ant-...",
  },
  {
    id: "google",
    name: "Google (Gemini)",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "AIza...",
  },
  {
    id: "dashscope",
    name: "Alibaba (Qwen)",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-...",
  },
  {
    id: "aws-bedrock",
    name: "AWS Bedrock",
    fields: ["region"] as const,
    keyPlaceholder: "",
  },
  {
    id: "minimax",
    name: "MiniMax",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-...",
  },
  {
    id: "openai",
    name: "OpenAI Compatible",
    fields: ["apiKey", "baseUrl"] as const,
    keyPlaceholder: "sk-...",
  },
];

interface ModelSettingsDialogProps {
  isOpen: boolean;
  onClose: () => void;
}

export function ModelSettingsDialog({
  isOpen,
  onClose,
}: ModelSettingsDialogProps) {
  const [configs, setConfigs] = useState<UserModelConfigView[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [formData, setFormData] = useState<
    Record<string, { apiKey: string; baseUrl: string; region: string; enabled: boolean }>
  >({});
  const [showKeys, setShowKeys] = useState<Record<string, boolean>>({});
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

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
      });
      setMessage({ type: "success", text: `${providerId} 配置已保存` });
      // Reset apiKey field (won't be shown again)
      setFormData((prev) => ({
        ...prev,
        [providerId]: { ...prev[providerId], apiKey: "" },
      }));
      await loadConfigs();
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
    value: string | boolean
  ) => {
    setFormData((prev) => ({
      ...prev,
      [providerId]: { ...prev[providerId], [field]: value },
    }));
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/50">
      <div className="w-full max-w-lg rounded-lg border border-border bg-background shadow-xl max-h-[80vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <h2 className="text-sm font-semibold">Model Provider Settings</h2>
          <button
            onClick={onClose}
            className="rounded p-1 hover:bg-accent"
          >
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
                            updateField(provider.id, "enabled", e.target.checked)
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
            API Keys are encrypted and stored securely. User configs override system defaults.
          </p>
        </div>
      </div>
    </div>
  );
}

"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Key, Save, Eye, EyeOff } from "lucide-react";
import Link from "next/link";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";
import type { OrgModelConfig } from "@/lib/types";

const PROVIDERS = [
  { id: "anthropic", label: "Anthropic (Claude)", color: "from-orange-900/30 to-orange-800/10" },
  { id: "minimax", label: "MiniMax", color: "from-blue-900/30 to-blue-800/10" },
  { id: "gemini", label: "Google Gemini", color: "from-green-900/30 to-green-800/10" },
  { id: "dashscope", label: "Alibaba DashScope", color: "from-purple-900/30 to-purple-800/10" },
  { id: "bedrock", label: "AWS Bedrock", color: "from-yellow-900/30 to-yellow-800/10" },
  { id: "openai", label: "OpenAI", color: "from-teal-900/30 to-teal-800/10" },
];

interface ProviderCardProps {
  orgId: string;
  provider: { id: string; label: string; color: string };
  existing: OrgModelConfig | undefined;
}

function ProviderCard({ orgId, provider, existing }: ProviderCardProps) {
  const qc = useQueryClient();
  const [enabled, setEnabled] = useState(existing?.enabled ?? true);
  const [apiKey, setApiKey] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [baseUrl, setBaseUrl] = useState(existing?.baseUrl ?? "");
  const [saved, setSaved] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      api.modelConfigs.upsert(orgId, provider.id, {
        enabled,
        apiKey: apiKey || undefined,
        baseUrl: baseUrl || undefined,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs", orgId, "model-configs"] });
      setApiKey("");
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    },
  });

  return (
    <Card className={`bg-gradient-to-br ${provider.color}`}>
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Key size={16} className="text-gray-400" />
          <h3 className="text-sm font-semibold text-gray-200">{provider.label}</h3>
        </div>
        <label className="flex cursor-pointer items-center gap-2">
          <span className="text-xs text-gray-400">Enabled</span>
          <div
            onClick={() => setEnabled(!enabled)}
            className={`relative h-5 w-9 rounded-full transition-colors ${
              enabled ? "bg-indigo-600" : "bg-gray-700"
            }`}
          >
            <div
              className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-transform ${
                enabled ? "translate-x-4" : "translate-x-0.5"
              }`}
            />
          </div>
        </label>
      </div>

      <div className="space-y-3">
        {existing?.apiKeyMasked && (
          <p className="text-xs text-gray-400">
            Current key:{" "}
            <span className="font-mono text-gray-300">
              {existing.apiKeyMasked}
            </span>
          </p>
        )}

        <div className="relative">
          <Input
            label="API Key"
            type={showKey ? "text" : "password"}
            placeholder={existing?.apiKeyMasked ? "Update key (leave blank to keep current)" : "Enter API key"}
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
          <button
            type="button"
            onClick={() => setShowKey(!showKey)}
            className="absolute bottom-2 right-3 text-gray-500 hover:text-gray-300"
          >
            {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
          </button>
        </div>

        <Input
          label="Base URL (optional)"
          placeholder="https://api.provider.com/v1"
          value={baseUrl}
          onChange={(e) => setBaseUrl(e.target.value)}
        />

        <Button
          size="sm"
          onClick={() => mutation.mutate()}
          loading={mutation.isPending}
          className="w-full"
        >
          {saved ? (
            "Saved!"
          ) : (
            <>
              <Save size={13} />
              Save Config
            </>
          )}
        </Button>
      </div>
    </Card>
  );
}

export default function ModelConfigPage() {
  const { id } = useParams<{ id: string }>();
  const { data: configs = [], isLoading } = useQuery({
    queryKey: ["orgs", id, "model-configs"],
    queryFn: () => api.modelConfigs.list(id),
  });

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <div>
          <h1 className="text-xl font-bold text-white">Model Configuration</h1>
          <p className="text-sm text-gray-400">
            Configure AI provider API keys for this organization
          </p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          {PROVIDERS.map((provider) => (
            <ProviderCard
              key={provider.id}
              orgId={id}
              provider={provider}
              existing={configs.find((c) => c.provider === provider.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

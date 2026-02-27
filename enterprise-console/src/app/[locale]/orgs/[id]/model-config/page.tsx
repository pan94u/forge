"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Key, Save, Eye, EyeOff } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
import { api } from "@/lib/api";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";
import type { OrgModelConfig } from "@/lib/types";

const PROVIDERS = [
  { id: "anthropic", label: "Anthropic (Claude)" },
  { id: "minimax", label: "MiniMax" },
  { id: "gemini", label: "Google Gemini" },
  { id: "dashscope", label: "Alibaba DashScope" },
  { id: "bedrock", label: "AWS Bedrock" },
  { id: "openai", label: "OpenAI Compatible" },
];

interface ProviderCardProps {
  orgId: string;
  provider: { id: string; label: string };
  existing: OrgModelConfig | undefined;
}

function ProviderCard({ orgId, provider, existing }: ProviderCardProps) {
  const t = useTranslations("modelConfig");
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
    <Card>
      <div className="mb-3 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Key size={16} className="text-muted-foreground" />
          <h3 className="text-sm font-semibold text-foreground">{provider.label}</h3>
        </div>
        <label className="flex cursor-pointer items-center gap-2">
          <span className="text-xs text-muted-foreground">{t("enabledLabel")}</span>
          <div
            onClick={() => setEnabled(!enabled)}
            className={`relative h-5 w-9 rounded-full transition-colors cursor-pointer ${
              enabled ? "bg-primary" : "bg-muted"
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
          <p className="text-xs text-muted-foreground">
            {t("currentKey")}
            <span className="font-mono text-foreground">
              {existing.apiKeyMasked}
            </span>
          </p>
        )}

        <div className="relative">
          <Input
            label={t("apiKeyLabel")}
            type={showKey ? "text" : "password"}
            placeholder={existing?.apiKeyMasked ? t("apiKeyPlaceholderUpdate") : t("apiKeyPlaceholder")}
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
          <button
            type="button"
            onClick={() => setShowKey(!showKey)}
            className="absolute bottom-2 right-3 text-muted-foreground hover:text-foreground transition-colors"
          >
            {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
          </button>
        </div>

        <Input
          label={t("baseUrlLabel")}
          placeholder={t("baseUrlPlaceholder")}
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
            t("saved")
          ) : (
            <>
              <Save size={13} />
              {t("saveBtn")}
            </>
          )}
        </Button>
      </div>
    </Card>
  );
}

export default function ModelConfigPage() {
  const { id } = useParams<{ id: string }>();
  const t = useTranslations("modelConfig");
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
          <h1 className="text-xl font-bold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
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

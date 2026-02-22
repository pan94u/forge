"use client";

import React, { useState, useEffect, useRef } from "react";
import { ChevronDown, Cpu, Zap, Crown } from "lucide-react";
import { fetchAvailableModels, type ModelInfo } from "@/lib/model-api";
import { Settings } from "lucide-react";

const COST_ICON: Record<string, React.ElementType> = {
  LOW: Zap,
  MEDIUM: Cpu,
  HIGH: Crown,
};

const PROVIDER_LABEL: Record<string, string> = {
  anthropic: "Anthropic",
  "aws-bedrock": "AWS Bedrock",
  google: "Google",
  dashscope: "Alibaba",
  local: "Local",
  openai: "OpenAI",
};

interface ModelSelectorProps {
  selectedModel: string;
  onModelChange: (modelId: string) => void;
  onOpenSettings?: () => void;
  refreshKey?: number;
}

export function ModelSelector({
  selectedModel,
  onModelChange,
  onOpenSettings,
  refreshKey,
}: ModelSelectorProps) {
  const [models, setModels] = useState<ModelInfo[]>([]);
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setLoading(true);
    fetchAvailableModels()
      .then(setModels)
      .catch(() => setModels([]))
      .finally(() => setLoading(false));
  }, [refreshKey]);

  // Close dropdown on outside click
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const currentModel = models.find((m) => m.id === selectedModel);
  const displayName =
    currentModel?.displayName ??
    selectedModel.split("/").pop() ??
    "Select model";

  // Group models by provider
  const grouped = models.reduce<Record<string, ModelInfo[]>>((acc, model) => {
    const key = model.provider;
    if (!acc[key]) acc[key] = [];
    acc[key].push(model);
    return acc;
  }, {});

  if (loading) {
    return (
      <div className="flex items-center gap-1 text-xs text-muted-foreground px-2 py-1">
        <Cpu className="h-3 w-3 animate-spin" />
        <span>Loading...</span>
      </div>
    );
  }

  if (models.length === 0) {
    return (
      <button
        onClick={onOpenSettings}
        className="flex items-center gap-1 rounded-md border border-dashed border-border px-2 py-1 text-xs text-muted-foreground hover:bg-accent transition-colors"
        title="Configure model providers"
      >
        <Settings className="h-3 w-3" />
        <span>Configure providers</span>
      </button>
    );
  }

  return (
    <div ref={dropdownRef} className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-accent transition-colors"
        title={`Current model: ${displayName}`}
      >
        {currentModel && <CostIcon costTier={currentModel.costTier} />}
        <span className="max-w-[120px] truncate">{displayName}</span>
        <ChevronDown
          className={`h-3 w-3 transition-transform ${isOpen ? "rotate-180" : ""}`}
        />
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full z-50 mt-1 w-64 rounded-md border border-border bg-popover shadow-lg">
          <div className="max-h-80 overflow-auto p-1">
            {Object.entries(grouped).map(([provider, providerModels]) => (
              <div key={provider}>
                <div className="px-2 py-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {PROVIDER_LABEL[provider] ?? provider}
                </div>
                {providerModels.map((model) => (
                  <button
                    key={model.id}
                    onClick={() => {
                      onModelChange(model.id);
                      setIsOpen(false);
                    }}
                    className={`flex w-full items-center gap-2 rounded px-2 py-1.5 text-xs transition-colors ${
                      model.id === selectedModel
                        ? "bg-primary/10 text-primary"
                        : "hover:bg-accent"
                    }`}
                  >
                    <CostIcon costTier={model.costTier} />
                    <div className="flex-1 text-left">
                      <div className="font-medium">{model.displayName}</div>
                      <div className="text-[10px] text-muted-foreground">
                        {formatContextWindow(model.contextWindow)} ctx
                        {model.supportsVision && " · vision"}
                      </div>
                    </div>
                    {model.id === selectedModel && (
                      <span className="text-primary text-[10px]">active</span>
                    )}
                  </button>
                ))}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function CostIcon({ costTier }: { costTier: string }) {
  const Icon = COST_ICON[costTier] ?? Cpu;
  const color =
    costTier === "HIGH"
      ? "text-amber-500"
      : costTier === "MEDIUM"
        ? "text-blue-500"
        : "text-green-500";
  return <Icon className={`h-3 w-3 flex-shrink-0 ${color}`} />;
}

function formatContextWindow(tokens: number): string {
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(0)}M`;
  if (tokens >= 1_000) return `${(tokens / 1_000).toFixed(0)}K`;
  return `${tokens}`;
}

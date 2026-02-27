"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { skillApi, SkillView } from "@/lib/skill-api";
import { SkillList } from "@/components/skills/SkillList";
import { SkillDetailPanel } from "@/components/skills/SkillDetailPanel";
import { SkillCreateForm } from "@/components/skills/SkillCreateForm";
import {
  Sparkles,
  RefreshCw,
  Plus,
  Loader2,
  AlertCircle,
} from "lucide-react";

type ScopeTab = "all" | "platform" | "workspace" | "custom";

export default function SkillsPage() {
  const t = useTranslations("skills");
  const [activeScope, setActiveScope] = useState<ScopeTab>("all");
  const [skills, setSkills] = useState<SkillView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedSkill, setSelectedSkill] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const scopeTabs: { id: ScopeTab; label: string }[] = [
    { id: "all", label: t("scopeAll") },
    { id: "platform", label: t("scopePlatform") },
    { id: "workspace", label: t("scopeWorkspace") },
    { id: "custom", label: t("scopeCustom") },
  ];

  const loadSkills = useCallback(async () => {
    try {
      const scope =
        activeScope === "all" ? undefined : activeScope.toUpperCase();
      const data = await skillApi.listSkills({ scope });
      setSkills(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : t("failedToLoad"));
    }
  }, [activeScope, t]);

  useEffect(() => {
    setLoading(true);
    loadSkills().finally(() => setLoading(false));
  }, [loadSkills]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadSkills();
    setRefreshing(false);
  };

  const handleTagToggle = (tag: string) => {
    setSelectedTags((prev) =>
      prev.includes(tag) ? prev.filter((tt) => tt !== tag) : [...prev, tag]
    );
  };

  const handleEnableToggle = () => {
    loadSkills();
  };

  const handleCreated = () => {
    setShowCreateForm(false);
    loadSkills();
  };

  // Scope counts
  const counts = {
    all: skills.length,
    platform: skills.filter((s) => s.scope === "PLATFORM").length,
    workspace: skills.filter((s) => s.scope === "WORKSPACE").length,
    custom: skills.filter((s) => s.scope === "CUSTOM").length,
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border bg-card px-4 py-3">
        <div className="flex items-center gap-2">
          <Sparkles className="h-5 w-5 text-primary" />
          <h1 className="text-lg font-semibold text-foreground">
            {t("title")}
          </h1>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            <RefreshCw
              className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
            />
            {t("refresh")}
          </button>
          <button
            onClick={() => {
              setShowCreateForm(true);
              setSelectedSkill(null);
            }}
            className="flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            <Plus className="h-4 w-4" />
            {t("newSkill")}
          </button>
        </div>
      </div>

      {/* Scope Tabs */}
      <div className="flex items-center gap-1 border-b border-border bg-card px-4">
        {scopeTabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveScope(tab.id);
              setSelectedSkill(null);
            }}
            className={`border-b-2 px-4 py-2.5 text-sm font-medium transition-colors ${
              activeScope === tab.id
                ? "border-primary text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            {tab.label} ({counts[tab.id]})
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex flex-1 overflow-hidden">
        {loading ? (
          <div className="flex flex-1 items-center justify-center">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        ) : error ? (
          <div className="flex flex-1 items-center justify-center text-muted-foreground">
            <div className="text-center">
              <AlertCircle className="mx-auto h-8 w-8 opacity-50" />
              <p className="mt-2">{error}</p>
            </div>
          </div>
        ) : (
          <>
            {/* Left Panel: Skill List */}
            <div className="w-80 flex-shrink-0 border-r border-border">
              <SkillList
                skills={skills}
                selectedSkill={selectedSkill}
                onSelect={(name) => {
                  setSelectedSkill(name);
                  setShowCreateForm(false);
                }}
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                selectedTags={selectedTags}
                onTagToggle={handleTagToggle}
              />
            </div>

            {/* Right Panel: Detail or Create */}
            <div className="flex-1 overflow-hidden">
              {showCreateForm ? (
                <SkillCreateForm
                  workspaceId="default"
                  onCreated={handleCreated}
                  onCancel={() => setShowCreateForm(false)}
                />
              ) : selectedSkill ? (
                <SkillDetailPanel
                  skillName={selectedSkill}
                  onEnableToggle={handleEnableToggle}
                />
              ) : (
                <div className="flex h-full items-center justify-center text-muted-foreground">
                  <div className="text-center">
                    <Sparkles className="mx-auto h-12 w-12 opacity-50" />
                    <p className="mt-4 text-lg font-medium">
                      {t("selectSkill")}
                    </p>
                    <p className="mt-1 text-sm">
                      {t("selectSkillDesc")}
                    </p>
                  </div>
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

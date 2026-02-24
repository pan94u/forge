"use client";

import React, { useState, useEffect } from "react";
import { type SkillView, skillApi } from "@/lib/skill-api";

interface WorkspaceSkillPanelProps {
  workspaceId: string;
}

export function WorkspaceSkillPanel({ workspaceId }: WorkspaceSkillPanelProps) {
  const [skills, setSkills] = useState<SkillView[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedSkill, setSelectedSkill] = useState<string | null>(null);
  const [skillContent, setSkillContent] = useState<string>("");
  const [scopeFilter, setScopeFilter] = useState<string>("all");

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch("/api/skills");
        if (res.ok) {
          const data = (await res.json()) as SkillView[];
          setSkills(data);
        }
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    })();
  }, [workspaceId]);

  const loadSkillContent = async (name: string) => {
    try {
      const res = await fetch(`/api/skills/${name}`);
      if (res.ok) {
        const data = (await res.json()) as { content: string };
        setSkillContent(data.content);
      }
    } catch {
      setSkillContent("Failed to load skill content");
    }
  };

  const handleSelect = (name: string) => {
    if (selectedSkill === name) {
      setSelectedSkill(null);
      setSkillContent("");
    } else {
      setSelectedSkill(name);
      loadSkillContent(name);
    }
  };

  const handleToggle = async (skill: SkillView, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      if (skill.enabled) {
        await skillApi.disableSkill(workspaceId, skill.name);
      } else {
        await skillApi.enableSkill(workspaceId, skill.name);
      }
      setSkills((prev) =>
        prev.map((s) =>
          s.name === skill.name ? { ...s, enabled: !s.enabled } : s
        )
      );
    } catch (err) {
      console.error("Failed to toggle skill:", err);
    }
  };

  const filteredSkills = skills.filter((s) => {
    if (scopeFilter === "all") return true;
    return s.scope.toLowerCase() === scopeFilter;
  });

  if (loading) {
    return (
      <div className="flex items-center justify-center p-4">
        <p className="text-xs text-muted-foreground">加载 Skills...</p>
      </div>
    );
  }

  // Detail view when a skill is selected
  if (selectedSkill) {
    const skill = skills.find((s) => s.name === selectedSkill);
    return (
      <div className="flex h-full flex-col">
        <div className="flex items-center gap-2 border-b border-border px-3 py-2">
          <button
            onClick={() => {
              setSelectedSkill(null);
              setSkillContent("");
            }}
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            &larr; 返回
          </button>
          <span className="text-xs font-medium">{selectedSkill}</span>
        </div>
        <div className="flex-1 overflow-auto p-3">
          {skill && (
            <div className="space-y-2">
              <div className="flex flex-wrap gap-1">
                <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] text-primary">
                  {skill.scope}
                </span>
                <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                  {skill.category}
                </span>
                {skill.tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground"
                  >
                    {tag}
                  </span>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">
                {skill.description}
              </p>
              {skill.scripts.length > 0 && (
                <div>
                  <p className="text-xs font-medium">Scripts</p>
                  <ul className="mt-0.5 space-y-0.5">
                    {skill.scripts.map((s) => (
                      <li
                        key={s.path}
                        className="text-xs font-mono text-muted-foreground"
                      >
                        {s.path} ({s.language})
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
          {skillContent && (
            <pre className="mt-3 whitespace-pre-wrap rounded-md border border-border bg-muted/30 p-2 text-xs font-mono">
              {skillContent}
            </pre>
          )}
        </div>
      </div>
    );
  }

  // List view
  return (
    <div className="flex h-full flex-col">
      {/* Scope filter tabs */}
      <div className="flex items-center gap-1 border-b border-border px-3 py-1.5">
        {["all", "platform", "workspace", "custom"].map((scope) => (
          <button
            key={scope}
            onClick={() => setScopeFilter(scope)}
            className={`rounded px-2 py-0.5 text-[10px] font-medium transition-colors ${
              scopeFilter === scope
                ? "bg-primary/10 text-primary"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {scope === "all"
              ? `All (${skills.length})`
              : `${scope} (${skills.filter((s) => s.scope.toLowerCase() === scope).length})`}
          </button>
        ))}
      </div>

      {/* Skill list */}
      <div className="flex-1 overflow-auto">
        {filteredSkills.length === 0 ? (
          <div className="flex items-center justify-center py-8">
            <p className="text-xs text-muted-foreground">暂无 Skills</p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {filteredSkills.map((skill) => (
              <button
                key={skill.name}
                onClick={() => handleSelect(skill.name)}
                className="flex w-full items-center justify-between px-3 py-2 text-left hover:bg-accent/50"
              >
                <div className="flex flex-col gap-0.5">
                  <div className="flex items-center gap-1.5">
                    <span className="text-xs font-medium text-foreground">
                      {skill.name}
                    </span>
                    <span
                      className={`h-1.5 w-1.5 rounded-full ${skill.enabled ? "bg-green-400" : "bg-muted-foreground/40"}`}
                    />
                  </div>
                  <p className="text-[10px] text-muted-foreground line-clamp-1">
                    {skill.description}
                  </p>
                </div>
                <div className="flex flex-shrink-0 items-center gap-2 text-[10px] text-muted-foreground">
                  {skill.scriptCount > 0 && (
                    <span>{skill.scriptCount} scripts</span>
                  )}
                  <button
                    onClick={(e) => handleToggle(skill, e)}
                    className={`relative inline-flex h-4 w-7 flex-shrink-0 items-center rounded-full transition-colors ${
                      skill.enabled ? "bg-green-500" : "bg-muted-foreground/30"
                    }`}
                    title={skill.enabled ? "Disable skill" : "Enable skill"}
                  >
                    <span
                      className={`inline-block h-3 w-3 rounded-full bg-white transition-transform ${
                        skill.enabled ? "translate-x-3.5" : "translate-x-0.5"
                      }`}
                    />
                  </button>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

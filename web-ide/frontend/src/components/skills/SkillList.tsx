"use client";

import React from "react";
import { SkillView } from "@/lib/skill-api";
import {
  Search,
  FileText,
  Terminal,
  ToggleLeft,
  ToggleRight,
} from "lucide-react";

interface SkillListProps {
  skills: SkillView[];
  selectedSkill: string | null;
  onSelect: (name: string) => void;
  searchQuery: string;
  onSearchChange: (query: string) => void;
  selectedTags: string[];
  onTagToggle: (tag: string) => void;
}

export function SkillList({
  skills,
  selectedSkill,
  onSelect,
  searchQuery,
  onSearchChange,
  selectedTags,
  onTagToggle,
}: SkillListProps) {
  // Collect all unique tags
  const allTags = Array.from(
    new Set(skills.flatMap((s) => s.tags))
  ).sort();

  // Filter skills
  const filtered = skills.filter((skill) => {
    const matchesSearch =
      !searchQuery ||
      skill.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      skill.description.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesTags =
      selectedTags.length === 0 ||
      selectedTags.some((tag) => skill.tags.includes(tag));
    return matchesSearch && matchesTags;
  });

  return (
    <div className="flex h-full flex-col">
      {/* Search */}
      <div className="border-b border-border p-3">
        <div className="relative">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search skills..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="w-full rounded-md border border-border bg-background py-2 pl-9 pr-3 text-sm placeholder:text-muted-foreground focus:border-primary focus:outline-none"
          />
        </div>
      </div>

      {/* Tag filters */}
      {allTags.length > 0 && (
        <div className="flex flex-wrap gap-1 border-b border-border px-3 py-2">
          {allTags.slice(0, 12).map((tag) => (
            <button
              key={tag}
              onClick={() => onTagToggle(tag)}
              className={`rounded-full px-2 py-0.5 text-xs transition-colors ${
                selectedTags.includes(tag)
                  ? "bg-primary text-primary-foreground"
                  : "bg-accent text-muted-foreground hover:text-foreground"
              }`}
            >
              {tag}
            </button>
          ))}
        </div>
      )}

      {/* Skill list */}
      <div className="flex-1 overflow-auto">
        {filtered.length === 0 ? (
          <div className="px-3 py-8 text-center text-sm text-muted-foreground">
            No skills found
          </div>
        ) : (
          filtered.map((skill) => (
            <button
              key={skill.name}
              onClick={() => onSelect(skill.name)}
              className={`w-full border-b border-border px-3 py-3 text-left transition-colors ${
                selectedSkill === skill.name
                  ? "bg-accent"
                  : "hover:bg-accent/50"
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="truncate text-sm font-medium text-foreground">
                      {skill.name}
                    </span>
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-medium uppercase ${
                        skill.scope === "PLATFORM"
                          ? "bg-blue-500/10 text-blue-500"
                          : skill.scope === "WORKSPACE"
                            ? "bg-green-500/10 text-green-500"
                            : "bg-orange-500/10 text-orange-500"
                      }`}
                    >
                      {skill.scope.toLowerCase()}
                    </span>
                  </div>
                  <p className="mt-0.5 truncate text-xs text-muted-foreground">
                    {skill.description}
                  </p>
                  <div className="mt-1.5 flex items-center gap-3 text-xs text-muted-foreground">
                    {skill.subFileCount > 0 && (
                      <span className="flex items-center gap-1">
                        <FileText className="h-3 w-3" />
                        {skill.subFileCount}
                      </span>
                    )}
                    {skill.scriptCount > 0 && (
                      <span className="flex items-center gap-1">
                        <Terminal className="h-3 w-3" />
                        {skill.scriptCount}
                      </span>
                    )}
                    {skill.enabled ? (
                      <ToggleRight className="h-3 w-3 text-green-500" />
                    ) : (
                      <ToggleLeft className="h-3 w-3 text-muted-foreground" />
                    )}
                  </div>
                </div>
              </div>
            </button>
          ))
        )}
      </div>

      {/* Count */}
      <div className="border-t border-border px-3 py-2 text-xs text-muted-foreground">
        {filtered.length} of {skills.length} skills
      </div>
    </div>
  );
}

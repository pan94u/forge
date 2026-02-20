"use client";

import React, { useState, useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  File,
  BookOpen,
  Database,
  Server,
  Search,
  X,
  UserCircle,
} from "lucide-react";

export interface ContextItem {
  id: string;
  type: "file" | "knowledge" | "schema" | "service" | "profile";
  label: string;
  description?: string;
  preview?: string;
}

interface ContextPickerProps {
  workspaceId: string;
  onSelect: (item: ContextItem) => void;
  onClose: () => void;
}

type Category = "profiles" | "files" | "knowledge" | "schema" | "services";

const categories: {
  id: Category;
  label: string;
  icon: React.ElementType;
  type: ContextItem["type"];
}[] = [
  { id: "profiles", label: "Profiles", icon: UserCircle, type: "profile" },
  { id: "files", label: "Files", icon: File, type: "file" },
  { id: "knowledge", label: "Knowledge", icon: BookOpen, type: "knowledge" },
  { id: "schema", label: "Schema", icon: Database, type: "schema" },
  { id: "services", label: "Services", icon: Server, type: "service" },
];

const PROFILE_ITEMS: ContextItem[] = [
  { id: "profile-planning", type: "profile", label: "规划", description: "需求分析、产品规划" },
  { id: "profile-design", type: "profile", label: "设计", description: "架构设计、API 设计、ADR" },
  { id: "profile-development", type: "profile", label: "开发", description: "编码实现、Bug 修复、重构" },
  { id: "profile-testing", type: "profile", label: "测试", description: "测试策略、用例设计、覆盖率" },
  { id: "profile-ops", type: "profile", label: "运维", description: "部署、发布、监控、回滚" },
];

export function ContextPicker({
  workspaceId,
  onSelect,
  onClose,
}: ContextPickerProps) {
  const [activeCategory, setActiveCategory] = useState<Category>("profiles");
  const [searchQuery, setSearchQuery] = useState("");
  const searchRef = useRef<HTMLInputElement>(null);

  // Forward keyboard input to search when ContextPicker is open
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      // If search input doesn't have focus and user types a printable character,
      // focus the search input so the keystroke goes there
      if (
        searchRef.current &&
        document.activeElement !== searchRef.current &&
        e.key.length === 1 &&
        !e.ctrlKey &&
        !e.metaKey
      ) {
        searchRef.current.focus();
        // Don't prevent default — the character will be typed into the now-focused input
      }
      // Backspace support: focus search input on backspace too
      if (
        e.key === "Backspace" &&
        searchRef.current &&
        document.activeElement !== searchRef.current &&
        searchQuery.length > 0
      ) {
        searchRef.current.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, searchQuery]);

  // Profiles are static (no API call needed)
  const isProfileCategory = activeCategory === "profiles";

  const { data: apiItems, isLoading } = useQuery<ContextItem[]>({
    queryKey: ["context-items", activeCategory, searchQuery, workspaceId],
    queryFn: async () => {
      const params = new URLSearchParams({
        category: activeCategory,
        q: searchQuery,
        workspaceId,
      });
      const res = await fetch(`/api/context/search?${params}`);
      if (!res.ok) return [];
      return res.json();
    },
    placeholderData: (prev) => prev,
    enabled: !isProfileCategory,
  });

  const items = isProfileCategory
    ? PROFILE_ITEMS.filter(
        (p) =>
          !searchQuery ||
          p.label.includes(searchQuery) ||
          (p.description?.includes(searchQuery) ?? false)
      )
    : apiItems;

  return (
    <div className="max-h-72 overflow-hidden">
      {/* Search */}
      <div className="flex items-center gap-2 border-b border-border px-3 py-2">
        <Search className="h-4 w-4 text-muted-foreground" />
        <input
          ref={searchRef}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          onMouseDown={(e) => e.stopPropagation()}
          className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
          placeholder="Search context..."
        />
        <button onClick={onClose} className="rounded p-0.5 hover:bg-accent">
          <X className="h-3.5 w-3.5 text-muted-foreground" />
        </button>
      </div>

      {/* Category Tabs */}
      <div className="flex border-b border-border">
        {categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => {
              setActiveCategory(cat.id);
              setSearchQuery("");
              setTimeout(() => searchRef.current?.focus(), 0);
            }}
            className={`flex items-center gap-1 px-3 py-1.5 text-xs font-medium transition-colors ${
              activeCategory === cat.id
                ? "border-b-2 border-primary text-foreground"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            <cat.icon className="h-3 w-3" />
            {cat.label}
          </button>
        ))}
      </div>

      {/* Results */}
      <div className="max-h-48 overflow-auto p-1">
        {isLoading ? (
          <div className="space-y-1 p-2">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-8 animate-pulse rounded bg-muted" />
            ))}
          </div>
        ) : items && items.length > 0 ? (
          items.map((item) => (
            <button
              key={item.id}
              onClick={() => onSelect(item)}
              className="flex w-full items-center gap-2 rounded-md px-3 py-1.5 text-left text-sm hover:bg-accent"
            >
              {activeCategory === "profiles" && (
                <UserCircle className="h-3.5 w-3.5 text-orange-400 flex-shrink-0" />
              )}
              {activeCategory === "files" && (
                <File className="h-3.5 w-3.5 text-blue-400 flex-shrink-0" />
              )}
              {activeCategory === "knowledge" && (
                <BookOpen className="h-3.5 w-3.5 text-green-400 flex-shrink-0" />
              )}
              {activeCategory === "schema" && (
                <Database className="h-3.5 w-3.5 text-yellow-400 flex-shrink-0" />
              )}
              {activeCategory === "services" && (
                <Server className="h-3.5 w-3.5 text-purple-400 flex-shrink-0" />
              )}
              <div className="flex-1 min-w-0">
                <p className="truncate font-medium">{item.label}</p>
                {item.description && (
                  <p className="truncate text-xs text-muted-foreground">
                    {item.description}
                  </p>
                )}
              </div>
            </button>
          ))
        ) : (
          <p className="p-4 text-center text-xs text-muted-foreground">
            {searchQuery
              ? `No results for "${searchQuery}"`
              : `No ${activeCategory} found`}
          </p>
        )}
      </div>
    </div>
  );
}

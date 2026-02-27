"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import {
  Search,
  FileText,
  BookOpen,
  AlertTriangle,
  Terminal,
  Filter,
  Globe,
  FolderOpen,
  User,
  Layers,
} from "lucide-react";

interface KnowledgeDocument {
  id: string;
  title: string;
  type: "wiki" | "adr" | "runbook" | "api-doc";
  snippet: string;
  updatedAt: string;
  author: string;
  tags: string[];
  scope?: "global" | "workspace" | "personal";
  scopeId?: string;
}

interface KnowledgeSearchProps {
  onSelectDocument: (docId: string) => void;
  selectedDocId: string | null;
  workspaceId?: string;
}

const scopeFilters = [
  { value: "all", label: "All", icon: Layers },
  { value: "global", label: "Global", icon: Globe },
  { value: "workspace", label: "Workspace", icon: FolderOpen },
  { value: "personal", label: "Personal", icon: User },
] as const;

const docTypeFilters = [
  { value: "all", label: "All", icon: BookOpen },
  { value: "wiki", label: "Wiki", icon: FileText },
  { value: "adr", label: "ADR", icon: AlertTriangle },
  { value: "runbook", label: "Runbook", icon: Terminal },
  { value: "api-doc", label: "API Doc", icon: FileText },
] as const;

function getDocTypeIcon(type: KnowledgeDocument["type"]) {
  switch (type) {
    case "wiki":
      return <FileText className="h-4 w-4 text-blue-400" />;
    case "adr":
      return <AlertTriangle className="h-4 w-4 text-yellow-400" />;
    case "runbook":
      return <Terminal className="h-4 w-4 text-green-400" />;
    case "api-doc":
      return <FileText className="h-4 w-4 text-purple-400" />;
  }
}

function getScopeBadge(scope?: string) {
  switch (scope) {
    case "workspace":
      return (
        <span className="inline-flex items-center gap-0.5 rounded bg-blue-500/10 px-1.5 py-0.5 text-[10px] font-medium text-blue-400">
          <FolderOpen className="h-2.5 w-2.5" />
          Workspace
        </span>
      );
    case "personal":
      return (
        <span className="inline-flex items-center gap-0.5 rounded bg-purple-500/10 px-1.5 py-0.5 text-[10px] font-medium text-purple-400">
          <User className="h-2.5 w-2.5" />
          Personal
        </span>
      );
    case "global":
      return (
        <span className="inline-flex items-center gap-0.5 rounded bg-green-500/10 px-1.5 py-0.5 text-[10px] font-medium text-green-400">
          <Globe className="h-2.5 w-2.5" />
          Global
        </span>
      );
    default:
      return null;
  }
}

export function KnowledgeSearch({
  onSelectDocument,
  selectedDocId,
  workspaceId,
}: KnowledgeSearchProps) {
  const t = useTranslations("knowledge");
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const [scopeFilter, setScopeFilter] = useState<string>("all");
  const [showFilters, setShowFilters] = useState(false);

  const { data: results, isLoading } = useQuery<KnowledgeDocument[]>({
    queryKey: ["knowledge-search", query, typeFilter, scopeFilter, workspaceId],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (query) params.set("q", query);
      if (typeFilter !== "all") params.set("type", typeFilter);
      if (scopeFilter !== "all") params.set("scope", scopeFilter);
      if (workspaceId) params.set("workspaceId", workspaceId);
      const res = await fetch(`/api/knowledge/search?${params}`);
      if (!res.ok) throw new Error("Search failed");
      return res.json();
    },
    placeholderData: (prev) => prev,
  });

  // Hide workspace filter if not in workspace context
  const availableScopeFilters = workspaceId
    ? scopeFilters
    : scopeFilters.filter((f) => f.value !== "workspace");

  return (
    <div className="flex h-full flex-col">
      {/* Search Header */}
      <div className="border-b border-border p-3 space-y-2">
        <div className="flex items-center gap-2">
          <div className="flex flex-1 items-center gap-2 rounded-md border border-input bg-background px-3 py-1.5">
            <Search className="h-4 w-4 text-muted-foreground" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              placeholder={t("searchPlaceholder")}
            />
          </div>
          <button
            onClick={() => setShowFilters(!showFilters)}
            className={`rounded-md border p-1.5 ${
              showFilters || typeFilter !== "all" || scopeFilter !== "all"
                ? "border-primary bg-primary/10 text-primary"
                : "border-input text-muted-foreground hover:bg-accent"
            }`}
          >
            <Filter className="h-4 w-4" />
          </button>
        </div>

        {/* Scope filter chips */}
        {showFilters && (
          <>
            <div className="flex flex-wrap gap-1">
              {availableScopeFilters.map((f) => (
                <button
                  key={f.value}
                  onClick={() => setScopeFilter(f.value)}
                  className={`flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors ${
                    scopeFilter === f.value
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:text-foreground"
                  }`}
                >
                  <f.icon className="h-3 w-3" />
                  {f.label}
                </button>
              ))}
            </div>
            {/* Type filter chips */}
            <div className="flex flex-wrap gap-1">
              {docTypeFilters.map((f) => (
                <button
                  key={f.value}
                  onClick={() => setTypeFilter(f.value)}
                  className={`flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors ${
                    typeFilter === f.value
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:text-foreground"
                  }`}
                >
                  <f.icon className="h-3 w-3" />
                  {f.label}
                </button>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Results */}
      <div className="flex-1 overflow-auto">
        {isLoading ? (
          <div className="space-y-2 p-3">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="space-y-2 rounded-md border border-border p-3">
                <div className="h-4 w-3/4 animate-pulse rounded bg-muted" />
                <div className="h-3 w-full animate-pulse rounded bg-muted" />
                <div className="h-3 w-2/3 animate-pulse rounded bg-muted" />
              </div>
            ))}
          </div>
        ) : results && results.length > 0 ? (
          <div className="space-y-1 p-2">
            {results.map((doc) => (
              <button
                key={doc.id}
                onClick={() => onSelectDocument(doc.id)}
                className={`w-full rounded-md border p-3 text-left transition-colors ${
                  selectedDocId === doc.id
                    ? "border-primary bg-primary/5"
                    : "border-border hover:border-primary/50 hover:bg-accent"
                }`}
              >
                <div className="flex items-start gap-2">
                  {getDocTypeIcon(doc.type)}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <h4 className="text-sm font-medium truncate">
                        {doc.title}
                      </h4>
                      {getScopeBadge(doc.scope)}
                    </div>
                    <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
                      {doc.snippet}
                    </p>
                    <div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
                      <span>{doc.author}</span>
                      <span>-</span>
                      <span>
                        {new Date(doc.updatedAt).toLocaleDateString()}
                      </span>
                    </div>
                    {doc.tags.length > 0 && (
                      <div className="mt-1.5 flex flex-wrap gap-1">
                        {doc.tags.map((tag) => (
                          <span
                            key={tag}
                            className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
                          >
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <div className="flex h-full items-center justify-center p-8 text-center">
            <div>
              <Search className="mx-auto h-8 w-8 text-muted-foreground/50" />
              <p className="mt-3 text-sm text-muted-foreground">
                {query
                  ? `No results for "${query}"`
                  : "Search the knowledge base"}
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

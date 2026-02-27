"use client";

import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
import {
  FolderOpen,
  Search,
  Plus,
  Clock,
  Trash2,
  Play,
  Pause,
  ExternalLink,
  Filter,
} from "lucide-react";
import { workspaceApi, type Workspace } from "@/lib/workspace-api";

function formatTimeAgo(timestamp: string, t: ReturnType<typeof useTranslations>): string {
  const now = new Date();
  const then = new Date(timestamp);
  const diffMs = now.getTime() - then.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return t("justNow");
  if (diffMins < 60) return t("minutesAgo", { n: diffMins });
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return t("hoursAgo", { n: diffHours });
  const diffDays = Math.floor(diffHours / 24);
  return t("daysAgo", { n: diffDays });
}

export default function WorkspacesPage() {
  const t = useTranslations("workspaces");
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const STATUS_OPTIONS = [
    { value: "all", label: t("statusAll") },
    { value: "active", label: t("statusActive") },
    { value: "suspended", label: t("statusSuspended") },
    { value: "creating", label: t("statusCreating") },
    { value: "error", label: t("statusError") },
  ] as const;

  const { data: workspaces, isLoading, error } = useQuery<Workspace[]>({
    queryKey: ["workspaces"],
    queryFn: () => workspaceApi.listWorkspaces(),
  });

  const filtered = (workspaces ?? []).filter((ws) => {
    const matchesSearch =
      !search ||
      ws.name.toLowerCase().includes(search.toLowerCase()) ||
      ws.description?.toLowerCase().includes(search.toLowerCase());
    const matchesStatus = statusFilter === "all" || ws.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const handleDelete = async (ws: Workspace) => {
    if (!confirm(t("confirmDelete", { name: ws.name }))) return;
    setActionInProgress(ws.id);
    try {
      await workspaceApi.deleteWorkspace(ws.id);
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    } catch (err) {
      alert(t("deleteFailed", { error: err instanceof Error ? err.message : String(err) }));
    } finally {
      setActionInProgress(null);
    }
  };

  const handleSuspend = async (ws: Workspace) => {
    setActionInProgress(ws.id);
    try {
      await workspaceApi.suspendWorkspace(ws.id);
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    } catch (err) {
      alert(t("suspendFailed", { error: err instanceof Error ? err.message : String(err) }));
    } finally {
      setActionInProgress(null);
    }
  };

  const handleActivate = async (ws: Workspace) => {
    setActionInProgress(ws.id);
    try {
      await workspaceApi.activateWorkspace(ws.id);
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    } catch (err) {
      alert(t("activateFailed", { error: err instanceof Error ? err.message : String(err) }));
    } finally {
      setActionInProgress(null);
    }
  };

  const statusCounts = (workspaces ?? []).reduce(
    (acc, ws) => {
      acc[ws.status] = (acc[ws.status] || 0) + 1;
      return acc;
    },
    {} as Record<string, number>,
  );

  return (
    <div className="h-full overflow-auto p-8">
      <div className="mx-auto max-w-6xl space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">{t("title")}</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              {t("subtitle", { count: workspaces?.length ?? 0 })}
            </p>
          </div>
          <Link
            href="/workspace/new"
            className="flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            <Plus className="h-4 w-4" />
            {t("newWorkspace")}
          </Link>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {STATUS_OPTIONS.filter((s) => s.value !== "all").map((s) => (
            <button
              key={s.value}
              onClick={() =>
                setStatusFilter(statusFilter === s.value ? "all" : s.value)
              }
              className={`rounded-lg border p-3 text-left transition-colors ${
                statusFilter === s.value
                  ? "border-primary bg-primary/5"
                  : "border-border hover:border-primary/30"
              }`}
            >
              <div className="text-2xl font-bold">
                {statusCounts[s.value] ?? 0}
              </div>
              <div className="text-xs text-muted-foreground">{s.label}</div>
            </button>
          ))}
        </div>

        {/* Search + Filter Bar */}
        <div className="flex items-center gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t("searchPlaceholder")}
              className="w-full rounded-md border border-input bg-background py-2 pl-10 pr-4 text-sm outline-none focus:ring-1 focus:ring-ring"
            />
          </div>
          <div className="flex items-center gap-1">
            <Filter className="h-4 w-4 text-muted-foreground" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-md border border-input bg-background px-2 py-2 text-sm"
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Workspace List */}
        {isLoading ? (
          <div className="space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="h-20 animate-pulse rounded-lg bg-muted" />
            ))}
          </div>
        ) : error ? (
          <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive">
            {t("error")}
          </div>
        ) : filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border p-12 text-center">
            <FolderOpen className="h-12 w-12 text-muted-foreground/50" />
            <h3 className="mt-4 font-medium">
              {search || statusFilter !== "all"
                ? t("noMatching")
                : t("noWorkspaces")}
            </h3>
            <p className="mt-1 text-sm text-muted-foreground">
              {search || statusFilter !== "all"
                ? t("noMatchingHint")
                : t("noWorkspacesHint")}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {filtered.map((ws) => (
              <div
                key={ws.id}
                className="group flex items-center gap-4 rounded-lg border border-border bg-card p-4 transition-colors hover:border-primary/30"
              >
                <FolderOpen className="h-8 w-8 flex-shrink-0 text-forge-400" />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <h3 className="font-medium truncate">{ws.name}</h3>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        ws.status === "active"
                          ? "bg-green-500/10 text-green-400"
                          : ws.status === "suspended"
                            ? "bg-yellow-500/10 text-yellow-400"
                            : ws.status === "error"
                              ? "bg-red-500/10 text-red-400"
                              : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {ws.status}
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground truncate">
                    {ws.description || t("noDescription")}
                  </p>
                  {ws.repository && (
                    <p className="text-xs text-muted-foreground/70 truncate mt-0.5">
                      {ws.repository}
                      {ws.branch ? ` (${ws.branch})` : ""}
                    </p>
                  )}
                  {ws.errorMessage && (
                    <p className="text-xs text-destructive mt-0.5 truncate">
                      {ws.errorMessage}
                    </p>
                  )}
                </div>
                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Clock className="h-3 w-3" />
                  {ws.updatedAt ? formatTimeAgo(ws.updatedAt, t) : "unknown"}
                </div>
                {/* Actions */}
                <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                  <Link
                    href={`/workspace/${ws.id}`}
                    className="rounded p-1.5 text-muted-foreground hover:bg-accent hover:text-foreground"
                    title={t("enterWorkspace")}
                  >
                    <ExternalLink className="h-4 w-4" />
                  </Link>
                  {ws.status === "active" && (
                    <button
                      onClick={() => handleSuspend(ws)}
                      disabled={actionInProgress === ws.id}
                      className="rounded p-1.5 text-muted-foreground hover:bg-yellow-500/10 hover:text-yellow-500 disabled:opacity-50"
                      title={t("suspend")}
                    >
                      <Pause className="h-4 w-4" />
                    </button>
                  )}
                  {ws.status === "suspended" && (
                    <button
                      onClick={() => handleActivate(ws)}
                      disabled={actionInProgress === ws.id}
                      className="rounded p-1.5 text-muted-foreground hover:bg-green-500/10 hover:text-green-500 disabled:opacity-50"
                      title={t("activate")}
                    >
                      <Play className="h-4 w-4" />
                    </button>
                  )}
                  <button
                    onClick={() => handleDelete(ws)}
                    disabled={actionInProgress === ws.id}
                    className="rounded p-1.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive disabled:opacity-50"
                    title={t("delete")}
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

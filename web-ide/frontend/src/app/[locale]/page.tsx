"use client";

import React from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
import {
  FolderOpen,
  Search,
  MessageSquare,
  Plus,
  Clock,
  ArrowRight,
  Activity,
  Trash2,
} from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { workspaceApi, type Workspace } from "@/lib/workspace-api";

interface ActivityItem {
  id: string;
  type: "commit" | "chat" | "deploy" | "review";
  message: string;
  timestamp: string;
  workspace?: string;
}

function getActivityIcon(type: ActivityItem["type"]) {
  switch (type) {
    case "commit":
      return <div className="h-2 w-2 rounded-full bg-green-400" />;
    case "chat":
      return <div className="h-2 w-2 rounded-full bg-purple-400" />;
    case "deploy":
      return <div className="h-2 w-2 rounded-full bg-blue-400" />;
    case "review":
      return <div className="h-2 w-2 rounded-full bg-yellow-400" />;
  }
}

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

export default function DashboardPage() {
  const t = useTranslations("home");
  const tw = useTranslations("workspaces");
  const queryClient = useQueryClient();
  const [deletingId, setDeletingId] = React.useState<string | null>(null);

  const quickActions = [
    {
      icon: Plus,
      label: t("newWorkspace"),
      description: t("newWorkspaceDesc"),
      href: "/workspace/new",
      color: "text-blue-400",
    },
    {
      icon: Search,
      label: t("searchKnowledge"),
      description: t("searchKnowledgeDesc"),
      href: "/knowledge",
      color: "text-green-400",
    },
    {
      icon: MessageSquare,
      label: t("startChat"),
      description: t("startChatDesc"),
      href: "/workspace/new?panel=chat",
      color: "text-purple-400",
    },
  ];

  const {
    data: workspaces,
    isLoading: workspacesLoading,
    error: workspacesError,
  } = useQuery<Workspace[]>({
    queryKey: ["workspaces"],
    queryFn: () => workspaceApi.listWorkspaces(),
  });

  const handleDelete = async (e: React.MouseEvent, wsId: string, wsName: string) => {
    e.preventDefault();
    e.stopPropagation();
    if (!confirm(tw("confirmDelete", { name: wsName }))) return;
    setDeletingId(wsId);
    try {
      await workspaceApi.deleteWorkspace(wsId);
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    } catch (err) {
      alert(tw("deleteFailed", { error: err instanceof Error ? err.message : String(err) }));
    } finally {
      setDeletingId(null);
    }
  };

  const {
    data: activities,
    isLoading: activitiesLoading,
  } = useQuery<ActivityItem[]>({
    queryKey: ["activities"],
    queryFn: async () => {
      const res = await fetch("/api/activities?limit=10");
      if (!res.ok) return [];
      return res.json();
    },
  });

  return (
    <div className="h-full overflow-auto p-8">
      <div className="mx-auto max-w-6xl space-y-8">
        {/* Welcome Section */}
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {t("welcome")}
          </h1>
          <p className="mt-2 text-muted-foreground">
            {t("subtitle")}
          </p>
        </div>

        {/* Quick Actions */}
        <section>
          <h2 className="mb-4 text-lg font-semibold">{t("quickActions")}</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            {quickActions.map((action) => (
              <Link
                key={action.label}
                href={action.href}
                className="group flex items-start gap-4 rounded-lg border border-border bg-card p-5 transition-colors hover:border-primary/50 hover:bg-accent"
              >
                <div
                  className={`mt-0.5 rounded-md bg-muted p-2 ${action.color}`}
                >
                  <action.icon className="h-5 w-5" />
                </div>
                <div className="flex-1">
                  <h3 className="font-medium">{action.label}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {action.description}
                  </p>
                </div>
                <ArrowRight className="mt-1 h-4 w-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
              </Link>
            ))}
          </div>
        </section>

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
          {/* Recent Projects */}
          <section className="lg:col-span-2">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">{t("recentProjects")}</h2>
              <Link
                href="/workspaces"
                className="text-sm text-primary hover:underline"
              >
                {t("viewAll")}
              </Link>
            </div>
            <div className="mt-4 space-y-3">
              {workspacesLoading ? (
                <div className="space-y-3">
                  {[1, 2, 3].map((i) => (
                    <div
                      key={i}
                      className="h-20 animate-pulse rounded-lg bg-muted"
                    />
                  ))}
                </div>
              ) : workspacesError ? (
                <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive">
                  {t("failedLoad")}
                </div>
              ) : workspaces && workspaces.length > 0 ? (
                workspaces.slice(0, 5).map((ws) => (
                  <Link
                    key={ws.id}
                    href={`/workspace/${ws.id}`}
                    className="group flex items-center gap-4 rounded-lg border border-border bg-card p-4 transition-colors hover:border-primary/50 hover:bg-accent"
                  >
                    <FolderOpen className="h-8 w-8 text-forge-400" />
                    <div className="flex-1 min-w-0">
                      <h3 className="font-medium truncate">{ws.name}</h3>
                      <p className="text-sm text-muted-foreground truncate">
                        {ws.description || tw("noDescription")}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <Clock className="h-3 w-3" />
                      {ws.updatedAt
                        ? formatTimeAgo(ws.updatedAt, tw)
                        : "unknown"}
                    </div>
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
                    <button
                      onClick={(e) => handleDelete(e, ws.id, ws.name)}
                      disabled={deletingId === ws.id}
                      className="rounded p-1 text-muted-foreground opacity-0 transition-all hover:bg-destructive/10 hover:text-destructive group-hover:opacity-100 disabled:opacity-50"
                      title={tw("delete")}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </Link>
                ))
              ) : (
                <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border p-12 text-center">
                  <FolderOpen className="h-12 w-12 text-muted-foreground/50" />
                  <h3 className="mt-4 font-medium">{t("noWorkspaces")}</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {t("noWorkspacesHint")}
                  </p>
                  <Link
                    href="/workspace/new"
                    className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
                  >
                    {t("createWorkspace")}
                  </Link>
                </div>
              )}
            </div>
          </section>

          {/* Activity Feed */}
          <section>
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-lg font-semibold">{t("activity")}</h2>
            </div>
            <div className="mt-4 space-y-1">
              {activitiesLoading ? (
                <div className="space-y-2">
                  {[1, 2, 3, 4, 5].map((i) => (
                    <div
                      key={i}
                      className="h-10 animate-pulse rounded bg-muted"
                    />
                  ))}
                </div>
              ) : activities && activities.length > 0 ? (
                activities.map((activity) => (
                  <div
                    key={activity.id}
                    className="flex items-start gap-3 rounded-md p-2 text-sm hover:bg-accent"
                  >
                    <div className="mt-1.5">{getActivityIcon(activity.type)}</div>
                    <div className="flex-1 min-w-0">
                      <p className="truncate">{activity.message}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatTimeAgo(activity.timestamp, tw)}
                        {activity.workspace && (
                          <span> in {activity.workspace}</span>
                        )}
                      </p>
                    </div>
                  </div>
                ))
              ) : (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  {t("noActivity")}
                </p>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

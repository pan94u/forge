"use client";

import React from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import {
  FolderOpen,
  Search,
  MessageSquare,
  Plus,
  Clock,
  ArrowRight,
  Activity,
} from "lucide-react";
import { workspaceApi, type Workspace } from "@/lib/workspace-api";

interface ActivityItem {
  id: string;
  type: "commit" | "chat" | "deploy" | "review";
  message: string;
  timestamp: string;
  workspace?: string;
}

const quickActions = [
  {
    icon: Plus,
    label: "New Workspace",
    description: "Create a new development workspace",
    href: "/workspace/new",
    color: "text-blue-400",
  },
  {
    icon: Search,
    label: "Search Knowledge",
    description: "Browse documentation and architecture",
    href: "/knowledge",
    color: "text-green-400",
  },
  {
    icon: MessageSquare,
    label: "Start Chat",
    description: "Ask the AI assistant anything",
    href: "/workspace/new?panel=chat",
    color: "text-purple-400",
  },
];

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

function formatTimeAgo(timestamp: string): string {
  const now = new Date();
  const then = new Date(timestamp);
  const diffMs = now.getTime() - then.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.floor(diffHours / 24);
  return `${diffDays}d ago`;
}

export default function DashboardPage() {
  const {
    data: workspaces,
    isLoading: workspacesLoading,
    error: workspacesError,
  } = useQuery<Workspace[]>({
    queryKey: ["workspaces"],
    queryFn: () => workspaceApi.listWorkspaces(),
  });

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
            Welcome to Forge
          </h1>
          <p className="mt-2 text-muted-foreground">
            Your AI-powered development environment. Build, explore, and
            collaborate.
          </p>
        </div>

        {/* Quick Actions */}
        <section>
          <h2 className="mb-4 text-lg font-semibold">Quick Actions</h2>
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
              <h2 className="text-lg font-semibold">Recent Projects</h2>
              <Link
                href="/workspace/new"
                className="text-sm text-primary hover:underline"
              >
                View all
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
                  Failed to load workspaces. Please try again.
                </div>
              ) : workspaces && workspaces.length > 0 ? (
                workspaces.slice(0, 5).map((ws) => (
                  <Link
                    key={ws.id}
                    href={`/workspace/${ws.id}`}
                    className="flex items-center gap-4 rounded-lg border border-border bg-card p-4 transition-colors hover:border-primary/50 hover:bg-accent"
                  >
                    <FolderOpen className="h-8 w-8 text-forge-400" />
                    <div className="flex-1 min-w-0">
                      <h3 className="font-medium truncate">{ws.name}</h3>
                      <p className="text-sm text-muted-foreground truncate">
                        {ws.description || "No description"}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <Clock className="h-3 w-3" />
                      {ws.updatedAt
                        ? formatTimeAgo(ws.updatedAt)
                        : "unknown"}
                    </div>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        ws.status === "active"
                          ? "bg-green-500/10 text-green-400"
                          : ws.status === "suspended"
                            ? "bg-yellow-500/10 text-yellow-400"
                            : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {ws.status}
                    </span>
                  </Link>
                ))
              ) : (
                <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border p-12 text-center">
                  <FolderOpen className="h-12 w-12 text-muted-foreground/50" />
                  <h3 className="mt-4 font-medium">No workspaces yet</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Create your first workspace to get started.
                  </p>
                  <Link
                    href="/workspace/new"
                    className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
                  >
                    Create Workspace
                  </Link>
                </div>
              )}
            </div>
          </section>

          {/* Activity Feed */}
          <section>
            <div className="flex items-center gap-2">
              <Activity className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-lg font-semibold">Activity</h2>
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
                        {formatTimeAgo(activity.timestamp)}
                        {activity.workspace && (
                          <span> in {activity.workspace}</span>
                        )}
                      </p>
                    </div>
                  </div>
                ))
              ) : (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  No recent activity
                </p>
              )}
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

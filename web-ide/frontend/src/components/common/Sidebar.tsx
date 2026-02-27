"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  FolderOpen,
  BookOpen,
  GitBranch,
  MessageSquare,
  Blocks,
  PanelLeftClose,
  PanelLeftOpen,
  Server,
  Sparkles,
  BarChart3,
  ChevronRight,
  Building2,
} from "lucide-react";
import { Workspace, workspaceApi } from "@/lib/workspace-api";

interface SidebarProps {
  collapsed: boolean;
  onToggleCollapse: () => void;
  role: "developer" | "product";
}

interface NavItem {
  href: string;
  icon: React.ElementType;
  label: string;
  roles: Array<"developer" | "product">;
  expandable?: boolean;
}

const navItems: NavItem[] = [
  {
    href: "/",
    icon: LayoutDashboard,
    label: "Dashboard",
    roles: ["developer", "product"],
  },
  {
    href: "/workspaces",
    icon: FolderOpen,
    label: "Workspaces",
    roles: ["developer", "product"],
  },
  {
    href: "/knowledge",
    icon: BookOpen,
    label: "Knowledge",
    roles: ["developer", "product"],
    expandable: true,
  },
  {
    href: "/workflows",
    icon: GitBranch,
    label: "Workflows",
    roles: ["developer"],
  },
  {
    href: "/chat",
    icon: MessageSquare,
    label: "AI Chat",
    roles: ["developer", "product"],
  },
  {
    href: "/skills",
    icon: Sparkles,
    label: "Skills",
    roles: ["developer"],
  },
  {
    href: "/evaluations",
    icon: BarChart3,
    label: "Evaluations",
    roles: ["developer"],
  },
  {
    href: "/integrations",
    icon: Blocks,
    label: "Integrations",
    roles: ["developer"],
  },
  {
    href: "/infrastructure",
    icon: Server,
    label: "Infrastructure",
    roles: ["developer"],
  },
];

export function Sidebar({ collapsed, onToggleCollapse, role }: SidebarProps) {
  const pathname = usePathname();
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [knowledgeExpanded, setKnowledgeExpanded] = useState(false);

  useEffect(() => {
    workspaceApi
      .listWorkspaces()
      .then((ws) => setWorkspaces(ws.filter((w) => w.status === "active")))
      .catch(() => {});
  }, []);

  // Auto-expand knowledge section when on knowledge page
  useEffect(() => {
    if (pathname.startsWith("/knowledge")) {
      setKnowledgeExpanded(true);
    }
  }, [pathname]);

  const filteredItems = navItems.filter((item) => item.roles.includes(role));

  const isActive = (href: string) => {
    if (href === "/") return pathname === "/";
    return pathname.startsWith(href);
  };

  return (
    <aside
      className={`flex flex-col border-r border-border bg-card transition-all duration-200 ${
        collapsed ? "w-14" : "w-52"
      }`}
    >
      {/* Navigation */}
      <nav className="flex-1 space-y-0.5 p-2 overflow-y-auto">
        {filteredItems.map((item) => (
          <div key={item.href}>
            {item.expandable && !collapsed ? (
              <>
                <div className="flex items-center">
                  <Link
                    href={item.href}
                    className={`flex flex-1 items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium transition-colors ${
                      isActive(item.href)
                        ? "bg-accent text-foreground"
                        : "text-muted-foreground hover:bg-accent hover:text-foreground"
                    }`}
                  >
                    <item.icon className="h-4 w-4 flex-shrink-0" />
                    <span className="flex-1">{item.label}</span>
                  </Link>
                  <button
                    onClick={() => setKnowledgeExpanded(!knowledgeExpanded)}
                    className="rounded p-1 text-muted-foreground hover:bg-accent hover:text-foreground"
                  >
                    <ChevronRight
                      className={`h-3 w-3 transition-transform ${
                        knowledgeExpanded ? "rotate-90" : ""
                      }`}
                    />
                  </button>
                </div>
                {knowledgeExpanded && workspaces.length > 0 && (
                  <div className="ml-4 mt-0.5 space-y-0.5 border-l border-border pl-2">
                    {workspaces.slice(0, 5).map((ws) => (
                      <Link
                        key={ws.id}
                        href={`/knowledge?workspaceId=${ws.id}`}
                        className={`block truncate rounded-md px-2 py-1 text-xs transition-colors ${
                          pathname === "/knowledge" &&
                          new URLSearchParams(
                            typeof window !== "undefined"
                              ? window.location.search
                              : ""
                          ).get("workspaceId") === ws.id
                            ? "bg-accent text-foreground"
                            : "text-muted-foreground hover:bg-accent hover:text-foreground"
                        }`}
                        title={ws.name}
                      >
                        {ws.name}
                      </Link>
                    ))}
                  </div>
                )}
              </>
            ) : (
              <Link
                href={item.href}
                className={`flex items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium transition-colors ${
                  isActive(item.href)
                    ? "bg-accent text-foreground"
                    : "text-muted-foreground hover:bg-accent hover:text-foreground"
                }`}
                title={collapsed ? item.label : undefined}
              >
                <item.icon className="h-4 w-4 flex-shrink-0" />
                {!collapsed && <span>{item.label}</span>}
              </Link>
            )}
          </div>
        ))}
      </nav>

      {/* Admin Link */}
      <div className="border-t border-border px-2 pt-2">
        <a
          href="http://localhost:9001"
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-3 rounded-md px-2.5 py-2 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
          title="Enterprise Console"
        >
          <Building2 className="h-4 w-4 flex-shrink-0" />
          {!collapsed && <span>Admin</span>}
        </a>
      </div>

      {/* Collapse Toggle */}
      <div className="border-t border-border p-2">
        <button
          onClick={onToggleCollapse}
          className="flex w-full items-center justify-center gap-2 rounded-md px-2.5 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground"
          title={collapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {collapsed ? (
            <PanelLeftOpen className="h-4 w-4" />
          ) : (
            <>
              <PanelLeftClose className="h-4 w-4" />
              <span>Collapse</span>
            </>
          )}
        </button>
      </div>
    </aside>
  );
}

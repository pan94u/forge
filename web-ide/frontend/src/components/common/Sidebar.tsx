"use client";

import React, { useState, useEffect } from "react";
import { Link, usePathname } from "@/navigation";
import { useTranslations } from "next-intl";
import {
  LayoutDashboard,
  FolderOpen,
  BookOpen,
  GitBranch,
  PanelLeftClose,
  PanelLeftOpen,
  Sparkles,
  BarChart3,
  FlaskConical,
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
  labelKey: string;
  roles: Array<"developer" | "product">;
  expandable?: boolean;
}

const navItems: NavItem[] = [
  { href: "/", icon: LayoutDashboard, labelKey: "dashboard", roles: ["developer", "product"] },
  { href: "/workspaces", icon: FolderOpen, labelKey: "workspaces", roles: ["developer", "product"] },
  { href: "/knowledge", icon: BookOpen, labelKey: "knowledge", roles: ["developer", "product"], expandable: true },
  { href: "/workflows", icon: GitBranch, labelKey: "workflows", roles: ["developer"] },
  { href: "/skills", icon: Sparkles, labelKey: "skills", roles: ["developer"] },
  { href: "/evaluations", icon: BarChart3, labelKey: "evaluations", roles: ["developer"] },
  { href: "/eval-dashboard", icon: FlaskConical, labelKey: "evalDashboard", roles: ["developer"] },
];

export function Sidebar({ collapsed, onToggleCollapse, role }: SidebarProps) {
  const pathname = usePathname();
  const t = useTranslations("sidebar");
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
    const stripped = pathname.replace(/^\/(zh|en)/, '') || '/';
    if (stripped.startsWith("/knowledge")) {
      setKnowledgeExpanded(true);
    }
  }, [pathname]);

  const filteredItems = navItems.filter((item) => item.roles.includes(role));

  // Strip locale prefix for comparison
  const strippedPathname = pathname.replace(/^\/(zh|en)/, '') || '/';
  const isActive = (href: string) => {
    if (href === "/") return strippedPathname === "/";
    return strippedPathname.startsWith(href);
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
                    <span className="flex-1">{t(item.labelKey as "dashboard")}</span>
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
                title={collapsed ? t(item.labelKey as "dashboard") : undefined}
              >
                <item.icon className="h-4 w-4 flex-shrink-0" />
                {!collapsed && <span>{t(item.labelKey as "dashboard")}</span>}
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
          {!collapsed && <span>{t("admin")}</span>}
        </a>
      </div>

      {/* Collapse Toggle */}
      <div className="border-t border-border p-2">
        <button
          onClick={onToggleCollapse}
          className="flex w-full items-center justify-center gap-2 rounded-md px-2.5 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground"
          title={collapsed ? t("expand") : t("collapse")}
        >
          {collapsed ? (
            <PanelLeftOpen className="h-4 w-4" />
          ) : (
            <>
              <PanelLeftClose className="h-4 w-4" />
              <span>{t("collapse")}</span>
            </>
          )}
        </button>
      </div>
    </aside>
  );
}

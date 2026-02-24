"use client";

import React from "react";
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
} from "lucide-react";

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
      <nav className="flex-1 space-y-0.5 p-2">
        {filteredItems.map((item) => (
          <Link
            key={item.href}
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
        ))}
      </nav>

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

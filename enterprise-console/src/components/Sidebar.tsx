"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Building2, LayoutDashboard, Settings } from "lucide-react";

const navItems = [
  {
    href: "/",
    label: "Dashboard",
    icon: LayoutDashboard,
    exact: true,
  },
  {
    href: "/orgs",
    label: "Organizations",
    icon: Building2,
    exact: false,
  },
];

export function Sidebar() {
  const pathname = usePathname();

  function isActive(href: string, exact: boolean) {
    if (exact) return pathname === href;
    return pathname === href || pathname.startsWith(href + "/");
  }

  return (
    <aside className="flex h-screen w-52 flex-col border-r border-gray-700 bg-gray-900">
      {/* Logo */}
      <div className="flex items-center gap-2 border-b border-gray-700 px-4 py-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-md bg-indigo-600">
          <Settings size={16} className="text-white" />
        </div>
        <div>
          <p className="text-sm font-semibold text-white">Enterprise</p>
          <p className="text-xs text-gray-400">Console</p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto px-2 py-3">
        <ul className="space-y-0.5">
          {navItems.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.href, item.exact);
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className={`flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                    active
                      ? "bg-indigo-600/20 text-indigo-300"
                      : "text-gray-400 hover:bg-gray-800 hover:text-gray-200"
                  }`}
                >
                  <Icon size={16} />
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      {/* Footer */}
      <div className="border-t border-gray-700 px-4 py-3">
        <p className="text-xs text-gray-500">Phase 13 — Enterprise Console</p>
      </div>
    </aside>
  );
}

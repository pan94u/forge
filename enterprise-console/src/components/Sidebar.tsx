"use client";

import { Building2, LayoutDashboard, Zap, Languages } from "lucide-react";
import { useTranslations, useLocale } from "next-intl";
import { Link, usePathname, useRouter } from "@/navigation";

export function Sidebar() {
  const t = useTranslations();
  const locale = useLocale();
  const pathname = usePathname();
  const router = useRouter();

  const navItems = [
    { href: "/", label: t("nav.dashboard"), icon: LayoutDashboard, exact: true },
    { href: "/orgs", label: t("nav.organizations"), icon: Building2, exact: false },
  ];

  function isActive(href: string, exact: boolean) {
    if (exact) return pathname === href;
    return pathname === href || pathname.startsWith(href + "/");
  }

  function toggleLocale() {
    router.replace(pathname, { locale: locale === "zh" ? "en" : "zh" });
  }

  return (
    <aside className="flex h-screen w-52 flex-col border-r border-border bg-card">
      {/* Logo */}
      <div className="flex items-center gap-2 border-b border-border px-4 py-4">
        <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary">
          <Zap size={16} className="text-primary-foreground" />
        </div>
        <div>
          <p className="text-sm font-semibold text-foreground">{t("sidebar.brand")}</p>
          <p className="text-xs text-muted-foreground">{t("sidebar.subtitle")}</p>
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
                      ? "bg-primary/15 text-primary"
                      : "text-muted-foreground hover:bg-accent hover:text-foreground"
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
      <div className="border-t border-border px-4 py-3 flex items-center justify-between">
        <p className="text-xs text-muted-foreground">{t("sidebar.footer")}</p>
        <button
          onClick={toggleLocale}
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
          title={locale === "zh" ? t("common.langEn") : t("common.langZh")}
        >
          <Languages size={12} />
          {locale === "zh" ? "EN" : "中"}
        </button>
      </div>
    </aside>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import { Building2, Users, Database, Settings2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
import { api } from "@/lib/api";
import { Card } from "@/components/ui/Card";

export default function DashboardPage() {
  const t = useTranslations("dashboard");
  const { data: orgs = [] } = useQuery({
    queryKey: ["orgs"],
    queryFn: api.orgs.list,
  });

  const activeOrgs = orgs.filter((o) => o.status === "ACTIVE").length;
  const stats = [
    {
      label: t("totalOrgs"),
      value: orgs.length,
      sub: `${activeOrgs} ${t("active")}`,
      icon: Building2,
      color: "text-primary",
      href: "/orgs",
    },
    {
      label: t("members"),
      value: "—",
      sub: t("membersDesc"),
      icon: Users,
      color: "text-forge-400",
      href: "/orgs",
    },
    {
      label: t("dbConnections"),
      value: "—",
      sub: t("dbDesc"),
      icon: Database,
      color: "text-green-400",
      href: "/orgs",
    },
    {
      label: t("configItems"),
      value: "—",
      sub: t("configDesc"),
      icon: Settings2,
      color: "text-yellow-400",
      href: "/orgs",
    },
  ];

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-foreground">{t("title")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("subtitle")}</p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <Link key={stat.label} href={stat.href}>
              <Card className="cursor-pointer hover:border-border/80 transition-colors">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-xs text-muted-foreground">{stat.label}</p>
                    <p className="mt-1 text-3xl font-bold text-foreground">
                      {stat.value}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">{stat.sub}</p>
                  </div>
                  <div className={`rounded-lg bg-muted p-2 ${stat.color}`}>
                    <Icon size={20} />
                  </div>
                </div>
              </Card>
            </Link>
          );
        })}
      </div>

      {orgs.length > 0 && (
        <div className="mt-8">
          <h2 className="mb-3 text-lg font-semibold text-foreground">
            {t("recentOrgs")}
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {orgs.slice(0, 6).map((org) => (
              <Link key={org.id} href={`/orgs/${org.id}`}>
                <Card className="cursor-pointer hover:border-border/80 transition-colors">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-md bg-primary/15 text-primary">
                      <Building2 size={18} />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-foreground">
                        {org.name}
                      </p>
                      <p className="text-xs text-muted-foreground">/{org.slug}</p>
                    </div>
                    <span
                      className={`ml-auto rounded-full px-2 py-0.5 text-xs ${
                        org.status === "ACTIVE"
                          ? "bg-green-900/50 text-green-300"
                          : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {org.status.toLowerCase()}
                    </span>
                  </div>
                </Card>
              </Link>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import { Building2, Users, Database, Settings2 } from "lucide-react";
import Link from "next/link";
import { api } from "@/lib/api";
import { Card } from "@/components/ui/Card";

export default function DashboardPage() {
  const { data: orgs = [] } = useQuery({
    queryKey: ["orgs"],
    queryFn: api.orgs.list,
  });

  const activeOrgs = orgs.filter((o) => o.status === "ACTIVE").length;
  const stats = [
    {
      label: "Total Organizations",
      value: orgs.length,
      sub: `${activeOrgs} active`,
      icon: Building2,
      color: "text-indigo-400",
      href: "/orgs",
    },
    {
      label: "Members",
      value: "—",
      sub: "Across all orgs",
      icon: Users,
      color: "text-blue-400",
      href: "/orgs",
    },
    {
      label: "DB Connections",
      value: "—",
      sub: "Configured",
      icon: Database,
      color: "text-green-400",
      href: "/orgs",
    },
    {
      label: "Config Items",
      value: "—",
      sub: "Env configs",
      icon: Settings2,
      color: "text-yellow-400",
      href: "/orgs",
    },
  ];

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <p className="mt-1 text-sm text-gray-400">
          Enterprise Console — Forge Platform management overview
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <Link key={stat.label} href={stat.href}>
              <Card className="cursor-pointer hover:border-gray-600 transition-colors">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-xs text-gray-400">{stat.label}</p>
                    <p className="mt-1 text-3xl font-bold text-white">
                      {stat.value}
                    </p>
                    <p className="mt-0.5 text-xs text-gray-500">{stat.sub}</p>
                  </div>
                  <div
                    className={`rounded-lg bg-gray-700/50 p-2 ${stat.color}`}
                  >
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
          <h2 className="mb-3 text-lg font-semibold text-gray-200">
            Recent Organizations
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {orgs.slice(0, 6).map((org) => (
              <Link key={org.id} href={`/orgs/${org.id}`}>
                <Card className="cursor-pointer hover:border-gray-600 transition-colors">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 items-center justify-center rounded-md bg-indigo-600/20 text-indigo-400">
                      <Building2 size={18} />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-white">
                        {org.name}
                      </p>
                      <p className="text-xs text-gray-500">/{org.slug}</p>
                    </div>
                    <span
                      className={`ml-auto rounded-full px-2 py-0.5 text-xs ${
                        org.status === "ACTIVE"
                          ? "bg-green-900/50 text-green-300"
                          : "bg-gray-700 text-gray-400"
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

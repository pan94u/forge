"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Building2, BookOpen, BarChart2, Heart } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function ArchitecturePage() {
  const { id } = useParams<{ id: string }>();

  const { data: architecture, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "architecture"],
    queryFn: () => api.governance.getArchitecture(id),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!architecture) return null;

  const healthColor =
    architecture.healthScore > 80
      ? "text-green-400"
      : architecture.healthScore >= 50
      ? "text-yellow-400"
      : "text-red-400";

  const statCards = [
    { label: "工作区总数", value: architecture.totalWorkspaces.toLocaleString(), icon: Building2 },
    { label: "有知识沉淀", value: architecture.workspacesWithKnowledge.toLocaleString(), icon: BookOpen },
    { label: "知识覆盖率", value: `${architecture.knowledgeCoverageRate.toFixed(1)}%`, icon: BarChart2 },
    { label: "架构健康度", value: architecture.healthScore.toFixed(0), icon: Heart, colorClass: healthColor },
  ];

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">架构治理</h1>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-8 lg:grid-cols-4">
        {statCards.map(({ label, value, icon: Icon, colorClass }) => (
          <div key={label} className="rounded-lg border border-border bg-card p-4">
            <div className="flex items-center gap-2 mb-2">
              <Icon size={16} className="text-muted-foreground" />
              <span className="text-xs text-muted-foreground">{label}</span>
            </div>
            <div className={`text-2xl font-bold ${colorClass ?? "text-foreground"}`}>{value}</div>
          </div>
        ))}
      </div>

      <Card title="最近工作区">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">名称</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">创建时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {architecture.recentWorkspaces.length === 0 ? (
                <tr>
                  <td colSpan={2} className="px-4 py-8 text-center text-muted-foreground">暂无数据</td>
                </tr>
              ) : (
                architecture.recentWorkspaces.map((ws) => (
                  <tr key={ws.id} className="hover:bg-accent/30 transition-colors">
                    <td className="px-4 py-3 text-foreground">{ws.name}</td>
                    <td className="px-4 py-3 text-xs font-mono text-muted-foreground">
                      {new Date(ws.createdAt).toLocaleString()}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}

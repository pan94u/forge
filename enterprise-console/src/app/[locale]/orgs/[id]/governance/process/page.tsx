"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, GitBranch, Layers, Share2, Activity } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function ProcessPage() {
  const { id } = useParams<{ id: string }>();

  const { data: process, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "process-summary"],
    queryFn: () => api.governance.getProcessSummary(id),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!process) return null;

  const statCards = [
    { label: "流程图总数", value: process.totalFlows.toLocaleString(), icon: GitBranch },
    { label: "节点总数", value: process.totalNodes.toLocaleString(), icon: Layers },
    { label: "连线总数", value: process.totalEdges.toLocaleString(), icon: Share2 },
    { label: "平均节点数", value: process.avgNodesPerFlow.toFixed(1), icon: Activity },
  ];

  const flowTypeEntries = Object.entries(process.flowsByType);

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">流程治理</h1>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-8 lg:grid-cols-4">
        {statCards.map(({ label, value, icon: Icon }) => (
          <div key={label} className="rounded-lg border border-border bg-card p-4">
            <div className="flex items-center gap-2 mb-2">
              <Icon size={16} className="text-muted-foreground" />
              <span className="text-xs text-muted-foreground">{label}</span>
            </div>
            <div className="text-2xl font-bold text-foreground">{value}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 mb-4">
        <Card title="按类型分布">
          <div className="space-y-2">
            {flowTypeEntries.length === 0 ? (
              <p className="text-sm text-muted-foreground">暂无数据</p>
            ) : (
              flowTypeEntries.map(([type, count]) => (
                <div key={type} className="flex items-center justify-between py-1">
                  <span className="text-sm font-mono text-foreground">{type}</span>
                  <span className="text-sm text-muted-foreground">{count}</span>
                </div>
              ))
            )}
          </div>
        </Card>

        <Card title="最近流程图">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium uppercase text-muted-foreground">名称</th>
                  <th className="px-3 py-2 text-left text-xs font-medium uppercase text-muted-foreground">类型</th>
                  <th className="px-3 py-2 text-right text-xs font-medium uppercase text-muted-foreground">节点</th>
                  <th className="px-3 py-2 text-right text-xs font-medium uppercase text-muted-foreground">连线</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {process.recentFlows.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-3 py-6 text-center text-muted-foreground">暂无数据</td>
                  </tr>
                ) : (
                  process.recentFlows.map((flow) => (
                    <tr key={flow.id} className="hover:bg-accent/30 transition-colors">
                      <td className="px-3 py-2 text-xs text-foreground">{flow.flowName}</td>
                      <td className="px-3 py-2 text-xs font-mono text-muted-foreground">{flow.flowType}</td>
                      <td className="px-3 py-2 text-xs text-right text-foreground">{flow.nodeCount}</td>
                      <td className="px-3 py-2 text-xs text-right text-foreground">{flow.edgeCount}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </div>
  );
}

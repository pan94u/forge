"use client";

import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { api } from "@/lib/api";

export default function DataPage() {
  const { id } = useParams<{ id: string }>();
  const qc = useQueryClient();

  const { data: health, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "data"],
    queryFn: () => api.governance.getKnowledge(id),
  });

  const { data: flows = [] } = useQuery({
    queryKey: ["orgs", id, "governance", "process"],
    queryFn: () => api.governance.getProcessFlows(id),
  });

  const snapshotMutation = useMutation({
    mutationFn: () => api.governance.createSnapshot(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["orgs", id, "governance"] });
    },
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!health) return null;

  const healthScore = Math.round(health.overallHealth);
  const coverageScore = Math.round(health.coverageScore);

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link href={`/orgs/${id}/governance`}>
            <Button variant="ghost" size="sm">
              <ArrowLeft size={14} />
            </Button>
          </Link>
          <h1 className="text-xl font-bold text-foreground">数据资产健康度</h1>
        </div>
        <Button
          variant="secondary"
          size="sm"
          loading={snapshotMutation.isPending}
          onClick={() => snapshotMutation.mutate()}
        >
          <RefreshCw size={13} />
          生成快照
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-8 lg:grid-cols-4">
        {[
          { label: "知识标签总数", value: health.totalTags.toLocaleString() },
          { label: "活跃标签", value: health.activeTags.toLocaleString() },
          { label: "覆盖率", value: `${coverageScore}%` },
          { label: "整体健康度", value: `${healthScore}%` },
        ].map(({ label, value }) => (
          <div key={label} className="rounded-lg border border-border bg-card p-4">
            <div className="text-xs text-muted-foreground mb-1">{label}</div>
            <div className="text-2xl font-bold text-foreground">{value}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card title="标签状态分布">
          <div className="space-y-2">
            {health.tagsByStatus.map((item) => (
              <div key={item.status} className="flex items-center justify-between py-1">
                <Badge color={item.status === "active" ? "green" : item.status === "draft" ? "yellow" : "gray"}>
                  {item.status}
                </Badge>
                <span className="text-sm text-foreground">{item.count}</span>
              </div>
            ))}
          </div>
        </Card>

        <Card title="流程图列表">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium uppercase text-muted-foreground">流程名称</th>
                  <th className="px-3 py-2 text-right text-xs font-medium uppercase text-muted-foreground">节点</th>
                  <th className="px-3 py-2 text-right text-xs font-medium uppercase text-muted-foreground">边</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {flows.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="px-3 py-6 text-center text-muted-foreground">暂无流程图数据</td>
                  </tr>
                ) : (
                  flows.map((flow) => (
                    <tr key={flow.id} className="hover:bg-accent/30 transition-colors">
                      <td className="px-3 py-2 text-sm text-foreground">{flow.flowName}</td>
                      <td className="px-3 py-2 text-right text-xs text-muted-foreground">{flow.nodeCount ?? 0}</td>
                      <td className="px-3 py-2 text-right text-xs text-muted-foreground">{flow.edgeCount ?? 0}</td>
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

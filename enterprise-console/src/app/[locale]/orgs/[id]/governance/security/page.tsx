"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, AlertTriangle, Shield } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { api } from "@/lib/api";

export default function SecurityPage() {
  const { id } = useParams<{ id: string }>();

  const { data: security, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "security"],
    queryFn: () => api.governance.getSecurity(id, 30),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!security) return null;

  const riskColor = security.riskLevel === "high" ? "red" : security.riskLevel === "medium" ? "yellow" : "green";

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">安全态势评估</h1>
        <span className="text-sm text-muted-foreground">近 {security.days} 天</span>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Shield size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">风险等级</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold text-foreground capitalize">{security.riskLevel}</span>
            <Badge color={riskColor}>{security.riskLevel === "low" ? "低" : security.riskLevel === "medium" ? "中" : "高"}</Badge>
          </div>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <AlertTriangle size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">异常事件数</span>
          </div>
          <div className="text-2xl font-bold text-foreground">{security.anomalyCount}</div>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Shield size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">安全事件总数</span>
          </div>
          <div className="text-2xl font-bold text-foreground">{security.totalEvents.toLocaleString()}</div>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card title="操作类型分布">
          <div className="space-y-2">
            {security.actionBreakdown.length === 0 ? (
              <p className="text-sm text-muted-foreground">暂无数据</p>
            ) : (
              security.actionBreakdown.map((item) => (
                <div key={item.action} className="flex items-center justify-between py-1">
                  <span className="text-sm font-mono text-foreground">{item.action}</span>
                  <span className="text-sm text-muted-foreground">{item.count}</span>
                </div>
              ))
            )}
          </div>
        </Card>

        <Card title="近期异常事件">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium uppercase text-muted-foreground">时间</th>
                  <th className="px-3 py-2 text-left text-xs font-medium uppercase text-muted-foreground">操作者</th>
                  <th className="px-3 py-2 text-left text-xs font-medium uppercase text-muted-foreground">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/50">
                {security.recentAnomalies.length === 0 ? (
                  <tr>
                    <td colSpan={3} className="px-3 py-6 text-center text-muted-foreground">暂无异常事件</td>
                  </tr>
                ) : (
                  security.recentAnomalies.map((anomaly, i) => (
                    <tr key={i} className="hover:bg-accent/30 transition-colors">
                      <td className="px-3 py-2 text-xs font-mono text-muted-foreground">
                        {new Date(anomaly.timestamp).toLocaleString()}
                      </td>
                      <td className="px-3 py-2 text-xs text-foreground">{anomaly.actorId}</td>
                      <td className="px-3 py-2 text-xs text-foreground">{anomaly.action}</td>
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

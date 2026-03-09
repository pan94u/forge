"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, ClipboardCheck, FileText, CheckCircle, AlertTriangle } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Badge } from "@/components/ui/Badge";
import { api } from "@/lib/api";

export default function CompliancePage() {
  const { id } = useParams<{ id: string }>();

  const { data: compliance, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "compliance"],
    queryFn: () => api.governance.getCompliance(id, 30),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!compliance) return null;

  const riskColor =
    compliance.riskLevel === "high" ? "red" : compliance.riskLevel === "medium" ? "yellow" : "green";
  const riskLabel =
    compliance.riskLevel === "high" ? "高" : compliance.riskLevel === "medium" ? "中" : "低";

  const statCards = [
    { label: "审计事件", value: compliance.totalAuditEvents.toLocaleString(), icon: FileText },
    { label: "HITL 事件", value: compliance.hitlEvents.toLocaleString(), icon: ClipboardCheck },
    { label: "审批通过率", value: `${compliance.hitlApprovalRate.toFixed(1)}%`, icon: CheckCircle },
    { label: "异常事件", value: compliance.anomalyCount.toLocaleString(), icon: AlertTriangle },
  ];

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">合规风险</h1>
        <span className="text-sm text-muted-foreground">近 {compliance.days} 天</span>
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

      <div className="mb-6 rounded-lg border border-border bg-card p-4 flex items-center gap-3">
        <span className="text-sm text-muted-foreground">风险等级：</span>
        <Badge color={riskColor}>{riskLabel}</Badge>
        <span className="text-sm capitalize text-foreground font-medium">{compliance.riskLevel}</span>
      </div>

      <Card title="最近事件">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">时间</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">操作</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">执行者</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">域</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {compliance.recentEvents.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-4 py-8 text-center text-muted-foreground">暂无数据</td>
                </tr>
              ) : (
                compliance.recentEvents.map((event, i) => (
                  <tr key={i} className="hover:bg-accent/30 transition-colors">
                    <td className="px-4 py-3 text-xs font-mono text-muted-foreground">
                      {new Date(event.timestamp).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-xs text-foreground">{event.action}</td>
                    <td className="px-4 py-3 text-xs text-foreground">{event.actor}</td>
                    <td className="px-4 py-3 text-xs font-mono text-muted-foreground">{event.domain}</td>
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

"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, MessageSquare, Play, TrendingUp } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function BudgetPage() {
  const { id } = useParams<{ id: string }>();

  const { data: budget, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "budget"],
    queryFn: () => api.governance.getBudget(id, 30),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!budget) return null;

  const statCards = [
    { label: "会话总数（30 天）", value: budget.totalSessions.toLocaleString(), icon: MessageSquare },
    { label: "执行总数（30 天）", value: budget.totalExecutions.toLocaleString(), icon: Play },
    { label: "ROI 得分", value: budget.roiScore.toFixed(2), icon: TrendingUp },
  ];

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">预算与成本分析</h1>
        <span className="text-sm text-muted-foreground">近 30 天</span>
      </div>

      <div className="grid grid-cols-3 gap-4 mb-8">
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

      <Card title="每日活动趋势">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">日期</th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">会话数</th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">执行数</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {budget.dailyActivity.length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-4 py-8 text-center text-muted-foreground">暂无数据</td>
                </tr>
              ) : (
                budget.dailyActivity.map((day) => (
                  <tr key={day.date} className="hover:bg-accent/30 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-foreground">{day.date}</td>
                    <td className="px-4 py-3 text-right text-foreground">{day.sessions.toLocaleString()}</td>
                    <td className="px-4 py-3 text-right text-foreground">{day.executions.toLocaleString()}</td>
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

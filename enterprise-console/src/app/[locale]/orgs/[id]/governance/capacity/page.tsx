"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, TrendingUp, Layout, PlusCircle, MessageSquare, Telescope } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { api } from "@/lib/api";

export default function CapacityPage() {
  const { id } = useParams<{ id: string }>();

  const { data: capacity, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "capacity"],
    queryFn: () => api.governance.getCapacity(id),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!capacity) return null;

  const statusColor =
    capacity.capacityStatus === "rapid_growth"
      ? "red"
      : capacity.capacityStatus === "growing"
      ? "yellow"
      : "green";

  const statusLabel =
    capacity.capacityStatus === "rapid_growth"
      ? "快速增长"
      : capacity.capacityStatus === "growing"
      ? "增长中"
      : "正常";

  const statCards = [
    { label: "当前工作区", value: capacity.currentWorkspaces.toLocaleString(), icon: Layout },
    { label: "近 30 天新增", value: capacity.workspacesLast30Days.toLocaleString(), icon: PlusCircle },
    { label: "工作区增长率", value: `${capacity.workspaceGrowthRate.toFixed(1)}%`, icon: TrendingUp },
    { label: "当前会话量", value: capacity.currentSessions.toLocaleString(), icon: MessageSquare },
    { label: "30 天预测工作区", value: capacity.forecastWorkspaces30Days.toLocaleString(), icon: Telescope },
  ];

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">容量规划</h1>
      </div>

      <div className="grid grid-cols-2 gap-4 mb-8 lg:grid-cols-3 xl:grid-cols-5">
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

      <div className="rounded-lg border border-border bg-card p-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-sm text-muted-foreground">容量状态：</span>
          <Badge color={statusColor}>{statusLabel}</Badge>
        </div>
        <p className="text-xs text-muted-foreground">预测基于近 30 天线性增长率</p>
      </div>
    </div>
  );
}

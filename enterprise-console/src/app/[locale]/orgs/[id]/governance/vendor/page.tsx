"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Package, Star, BarChart2 } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function VendorPage() {
  const { id } = useParams<{ id: string }>();

  const { data: vendor, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "vendor"],
    queryFn: () => api.governance.getVendor(id, 30),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!vendor) return null;

  const diversificationColor =
    vendor.diversificationScore > 66
      ? "text-green-400"
      : vendor.diversificationScore >= 33
      ? "text-yellow-400"
      : "text-red-400";

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">供应商管理</h1>
        <span className="text-sm text-muted-foreground">近 {vendor.days} 天</span>
      </div>

      <div className="grid grid-cols-1 gap-4 mb-8 sm:grid-cols-3">
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Package size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">Provider 数量</span>
          </div>
          <div className="text-2xl font-bold text-foreground">{vendor.totalProviders}</div>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Star size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">主要 Provider</span>
          </div>
          <div className="text-xl font-bold text-foreground truncate">{vendor.primaryProvider || "—"}</div>
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <BarChart2 size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">多样化评分</span>
          </div>
          <div className={`text-2xl font-bold ${diversificationColor}`}>
            {vendor.diversificationScore.toFixed(0)}
          </div>
        </div>
      </div>

      <Card title="Provider 用量分布">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">Provider</th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">会话数</th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">占比</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {vendor.providerStats.length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-4 py-8 text-center text-muted-foreground">暂无数据</td>
                </tr>
              ) : (
                vendor.providerStats.map((stat) => (
                  <tr key={stat.provider} className="hover:bg-accent/30 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-foreground">{stat.provider}</td>
                    <td className="px-4 py-3 text-right text-foreground">{stat.sessionCount.toLocaleString()}</td>
                    <td className="px-4 py-3 text-right text-muted-foreground">{stat.sharePercent.toFixed(1)}%</td>
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

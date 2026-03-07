"use client";

import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Users } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function TeamPage() {
  const { id } = useParams<{ id: string }>();

  const { data: team, isLoading } = useQuery({
    queryKey: ["orgs", id, "governance", "team"],
    queryFn: () => api.governance.getTeam(id, 30),
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!team) return null;

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/governance`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">团队能力分析</h1>
        <span className="text-sm text-muted-foreground">近 {team.days} 天</span>
      </div>

      <div className="grid grid-cols-1 gap-4 mb-8 sm:grid-cols-2">
        <div className="rounded-lg border border-border bg-card p-4">
          <div className="flex items-center gap-2 mb-2">
            <Users size={16} className="text-muted-foreground" />
            <span className="text-xs text-muted-foreground">成员总数</span>
          </div>
          <div className="text-2xl font-bold text-foreground">{team.totalMembers.toLocaleString()}</div>
        </div>
      </div>

      <Card title="技能使用排行 Top 10">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-border">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">排名</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">技能名称</th>
                <th className="px-4 py-3 text-right text-xs font-medium uppercase text-muted-foreground">使用次数</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {team.topSkills.length === 0 ? (
                <tr>
                  <td colSpan={3} className="px-4 py-8 text-center text-muted-foreground">暂无技能使用数据</td>
                </tr>
              ) : (
                team.topSkills.map((skill, index) => (
                  <tr key={skill.skillName} className="hover:bg-accent/30 transition-colors">
                    <td className="px-4 py-3 text-xs text-muted-foreground">{index + 1}</td>
                    <td className="px-4 py-3 font-medium text-foreground">{skill.skillName}</td>
                    <td className="px-4 py-3 text-right text-foreground">{skill.usageCount.toLocaleString()}</td>
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

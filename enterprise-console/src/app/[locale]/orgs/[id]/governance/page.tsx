"use client";

import { useParams } from "next/navigation";
import { ArrowLeft, DollarSign, Users, Shield, Database, Bot } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";

export default function GovernancePage() {
  const { id } = useParams<{ id: string }>();

  const domains = [
    {
      key: "budget",
      title: "预算管理",
      desc: "成本分析、执行统计、ROI 指标",
      icon: DollarSign,
      href: `/orgs/${id}/governance/budget`,
      color: "text-green-500",
    },
    {
      key: "team",
      title: "团队管理",
      desc: "成员活跃度、技能使用排行",
      icon: Users,
      href: `/orgs/${id}/governance/team`,
      color: "text-blue-500",
    },
    {
      key: "security",
      title: "安全管理",
      desc: "安全态势评分、异常事件检测",
      icon: Shield,
      href: `/orgs/${id}/governance/security`,
      color: "text-red-500",
    },
    {
      key: "data",
      title: "数据治理",
      desc: "知识资产健康度、流程图提取",
      icon: Database,
      href: `/orgs/${id}/governance/data`,
      color: "text-purple-500",
    },
    {
      key: "agent",
      title: "治理 Agent",
      desc: "AI 驱动的治理分析与决策支持",
      icon: Bot,
      href: `/orgs/${id}/governance/agent`,
      color: "text-orange-500",
    },
  ];

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <div>
          <h1 className="text-xl font-bold text-foreground">IT 治理</h1>
          <p className="text-sm text-muted-foreground">AI 驱动的 IT 治理决策支持</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {domains.map(({ key, title, desc, icon: Icon, href, color }) => (
          <Link key={key} href={href}>
            <div className="group rounded-lg border border-border bg-card p-5 hover:border-primary/50 hover:shadow-sm transition-all cursor-pointer">
              <div className="flex items-start gap-3">
                <div className={`mt-0.5 ${color}`}>
                  <Icon size={20} />
                </div>
                <div>
                  <h3 className="font-semibold text-foreground group-hover:text-primary transition-colors">
                    {title}
                  </h3>
                  <p className="text-sm text-muted-foreground mt-1">{desc}</p>
                </div>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

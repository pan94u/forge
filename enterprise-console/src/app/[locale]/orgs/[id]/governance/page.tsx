"use client";

import { useParams } from "next/navigation";
import { ArrowLeft, DollarSign, Users, Shield, Database, Bot, Building2, GitBranch, ClipboardCheck, TrendingUp, Package } from "lucide-react";
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
      key: "architecture",
      title: "架构治理",
      desc: "工作区架构健康度、知识覆盖率分析",
      icon: Building2,
      href: `/orgs/${id}/governance/architecture`,
      color: "text-indigo-500",
    },
    {
      key: "process",
      title: "流程治理",
      desc: "流程图分布、节点统计、类型分析",
      icon: GitBranch,
      href: `/orgs/${id}/governance/process`,
      color: "text-cyan-500",
    },
    {
      key: "compliance",
      title: "合规风险",
      desc: "审计事件、HITL 审批、风险等级评估",
      icon: ClipboardCheck,
      href: `/orgs/${id}/governance/compliance`,
      color: "text-yellow-500",
    },
    {
      key: "capacity",
      title: "容量规划",
      desc: "工作区增长趋势、30 天容量预测",
      icon: TrendingUp,
      href: `/orgs/${id}/governance/capacity`,
      color: "text-orange-500",
    },
    {
      key: "vendor",
      title: "供应商管理",
      desc: "Provider 用量分布、多样化评分",
      icon: Package,
      href: `/orgs/${id}/governance/vendor`,
      color: "text-pink-500",
    },
    {
      key: "agent",
      title: "治理 Agent",
      desc: "AI 驱动的治理分析与决策支持",
      icon: Bot,
      href: `/orgs/${id}/governance/agent`,
      color: "text-teal-500",
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

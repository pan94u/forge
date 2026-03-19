"use client";

import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { ArrowLeft, ClipboardList, Play } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";

export default function EvalPage() {
  const { id } = useParams<{ id: string }>();
  const t = useTranslations("eval");

  const sections = [
    {
      key: "tasks",
      title: t("taskLibrary"),
      desc: t("taskLibraryDesc"),
      icon: ClipboardList,
      href: `/orgs/${id}/eval/tasks`,
      color: "text-blue-500",
    },
    {
      key: "runs",
      title: t("runs"),
      desc: t("runsDesc"),
      icon: Play,
      href: `/orgs/${id}/eval/tasks`,
      color: "text-green-500",
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
          <h1 className="text-xl font-bold text-foreground">{t("title")}</h1>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {sections.map(({ key, title, desc, icon: Icon, href, color }) => (
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

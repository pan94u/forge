"use client";

import React from "react";
import { Code, Briefcase } from "lucide-react";
import { useTranslations } from "next-intl";

interface RoleSwitchProps {
  role: "developer" | "product";
  onRoleChange: (role: "developer" | "product") => void;
}

export function RoleSwitch({ role, onRoleChange }: RoleSwitchProps) {
  const t = useTranslations("roleSwitch");
  return (
    <div className="flex items-center rounded-full border border-border bg-muted p-0.5">
      <button
        onClick={() => onRoleChange("developer")}
        className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
          role === "developer"
            ? "bg-primary text-primary-foreground shadow-sm"
            : "text-muted-foreground hover:text-foreground"
        }`}
        title={t("developerTitle")}
      >
        <Code className="h-3 w-3" />
        <span className="hidden sm:inline">{t("developer")}</span>
      </button>
      <button
        onClick={() => onRoleChange("product")}
        className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
          role === "product"
            ? "bg-primary text-primary-foreground shadow-sm"
            : "text-muted-foreground hover:text-foreground"
        }`}
        title={t("productTitle")}
      >
        <Briefcase className="h-3 w-3" />
        <span className="hidden sm:inline">{t("product")}</span>
      </button>
    </div>
  );
}

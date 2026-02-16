"use client";

import React from "react";
import { Code, Briefcase } from "lucide-react";

interface RoleSwitchProps {
  role: "developer" | "product";
  onRoleChange: (role: "developer" | "product") => void;
}

export function RoleSwitch({ role, onRoleChange }: RoleSwitchProps) {
  return (
    <div className="flex items-center rounded-full border border-border bg-muted p-0.5">
      <button
        onClick={() => onRoleChange("developer")}
        className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
          role === "developer"
            ? "bg-primary text-primary-foreground shadow-sm"
            : "text-muted-foreground hover:text-foreground"
        }`}
        title="Developer mode: Full IDE with terminal and workflow editor"
      >
        <Code className="h-3 w-3" />
        <span className="hidden sm:inline">Developer</span>
      </button>
      <button
        onClick={() => onRoleChange("product")}
        className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors ${
          role === "product"
            ? "bg-primary text-primary-foreground shadow-sm"
            : "text-muted-foreground hover:text-foreground"
        }`}
        title="Product mode: Simplified view focused on knowledge and AI chat"
      >
        <Briefcase className="h-3 w-3" />
        <span className="hidden sm:inline">Product</span>
      </button>
    </div>
  );
}

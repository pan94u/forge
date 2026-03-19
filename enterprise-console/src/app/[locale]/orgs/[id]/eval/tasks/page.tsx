"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Plus, Upload, Trash2, ClipboardList } from "lucide-react";
import { Link } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function EvalTasksPage() {
  const { id } = useParams<{ id: string }>();
  const t = useTranslations("eval");
  const qc = useQueryClient();

  const [typeFilter, setTypeFilter] = useState("");
  const [diffFilter, setDiffFilter] = useState("");
  const [showImport, setShowImport] = useState(false);
  const [yamlText, setYamlText] = useState("");
  const [importMsg, setImportMsg] = useState("");

  const { data: tasks = [], isLoading } = useQuery({
    queryKey: ["eval", "tasks", id, typeFilter, diffFilter],
    queryFn: () => api.eval.listTasks(id, typeFilter || undefined, diffFilter || undefined),
  });

  const deleteMutation = useMutation({
    mutationFn: (taskId: string) => api.eval.deleteTask(taskId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["eval", "tasks"] }),
  });

  const importMutation = useMutation({
    mutationFn: (yaml: string) => api.eval.importYaml([yaml], id),
    onSuccess: (result) => {
      setImportMsg(t("importResult", { imported: result.imported, skipped: result.skipped }));
      setYamlText("");
      qc.invalidateQueries({ queryKey: ["eval", "tasks"] });
    },
  });

  const difficultyColor = (d: string) => {
    switch (d) {
      case "EASY": return "green";
      case "MEDIUM": return "yellow";
      case "HARD": return "red";
      default: return "gray";
    }
  };

  const difficultyLabel = (d: string) => {
    switch (d) {
      case "EASY": return t("diffEasy");
      case "MEDIUM": return t("diffMedium");
      case "HARD": return t("diffHard");
      default: return d;
    }
  };

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/eval`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <div className="flex-1">
          <h1 className="text-xl font-bold text-foreground">{t("tasksTitle")}</h1>
          <p className="text-sm text-muted-foreground">{t("tasksSubtitle")}</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => setShowImport(!showImport)}>
          <Upload size={14} className="mr-1" />
          {t("importYaml")}
        </Button>
        <Link href={`/orgs/${id}/eval/tasks/new`}>
          <Button size="sm">
            <Plus size={14} className="mr-1" />
            {t("newTask")}
          </Button>
        </Link>
      </div>

      {/* YAML Import Panel */}
      {showImport && (
        <Card title={t("importTitle")}>
          <p className="text-sm text-muted-foreground mb-2">{t("importDesc")}</p>
          <textarea
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
            rows={6}
            value={yamlText}
            onChange={(e) => setYamlText(e.target.value)}
            placeholder={t("importPlaceholder")}
          />
          <div className="flex items-center gap-3 mt-2">
            <Button
              size="sm"
              loading={importMutation.isPending}
              onClick={() => yamlText.trim() && importMutation.mutate(yamlText)}
            >
              {t("importBtn")}
            </Button>
            {importMsg && <span className="text-sm text-green-500">{importMsg}</span>}
          </div>
        </Card>
      )}

      {/* Filters */}
      <div className="flex gap-2 mb-4 mt-2">
        <select
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground"
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
        >
          <option value="">{t("filterAll")} — {t("filterType")}</option>
          <option value="SDLC">SDLC</option>
          <option value="GOVERNANCE">GOVERNANCE</option>
          <option value="CUSTOM">CUSTOM</option>
        </select>
        <select
          className="rounded-md border border-border bg-background px-3 py-1.5 text-sm text-foreground"
          value={diffFilter}
          onChange={(e) => setDiffFilter(e.target.value)}
        >
          <option value="">{t("filterAll")} — {t("filterDifficulty")}</option>
          <option value="EASY">{t("diffEasy")}</option>
          <option value="MEDIUM">{t("diffMedium")}</option>
          <option value="HARD">{t("diffHard")}</option>
        </select>
      </div>

      {/* Loading */}
      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      ) : tasks.length === 0 ? (
        <div className="flex flex-col items-center rounded-lg border border-dashed py-20 text-center">
          <ClipboardList size={40} className="mb-3 text-muted-foreground" />
          <p className="text-muted-foreground">{t("noTasks")}</p>
          <p className="text-sm text-muted-foreground">{t("noTasksDesc")}</p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead className="border-b bg-muted/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colName")}</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colType")}</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colDifficulty")}</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colSource")}</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colStatus")}</th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase text-muted-foreground">{t("colActions")}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {tasks.map((task) => (
                <tr key={task.id} className="hover:bg-accent/30 transition-colors">
                  <td className="px-4 py-3">
                    <Link
                      href={`/orgs/${id}/eval/tasks/${task.id}`}
                      className="font-medium text-foreground hover:text-primary transition-colors"
                    >
                      {task.name}
                    </Link>
                    {task.description && (
                      <p className="text-xs text-muted-foreground mt-0.5 truncate max-w-xs">
                        {task.description}
                      </p>
                    )}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{task.taskType || "—"}</td>
                  <td className="px-4 py-3">
                    <Badge color={difficultyColor(task.difficulty)}>
                      {difficultyLabel(task.difficulty)}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground text-xs">
                    {task.source === "YAML_IMPORT" ? t("sourceYaml") : t("sourceManual")}
                  </td>
                  <td className="px-4 py-3">
                    <Badge color={task.isActive ? "green" : "gray"}>
                      {task.isActive ? t("active") : t("inactive")}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => {
                        if (confirm(t("deleteConfirm", { name: task.name }))) {
                          deleteMutation.mutate(task.id);
                        }
                      }}
                      className="text-destructive hover:text-destructive/80 transition-colors"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

"use client";

import { useState, useEffect } from "react";
import { useParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import { Link, useRouter } from "@/navigation";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Card } from "@/components/ui/Card";
import { api } from "@/lib/api";

export default function EvalTaskDetailPage() {
  const { id, taskId } = useParams<{ id: string; taskId: string }>();
  const t = useTranslations("eval");
  const router = useRouter();
  const qc = useQueryClient();
  const isNew = taskId === "new";

  const { data: task, isLoading } = useQuery({
    queryKey: ["eval", "task", taskId],
    queryFn: () => api.eval.getTask(taskId),
    enabled: !isNew,
  });

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [input, setInput] = useState("");
  const [successCriteria, setSuccessCriteria] = useState("");
  const [taskType, setTaskType] = useState("");
  const [difficulty, setDifficulty] = useState("MEDIUM");
  const [graderConfig, setGraderConfig] = useState("");

  useEffect(() => {
    if (task) {
      setName(task.name);
      setDescription(task.description ?? "");
      setInput(task.input);
      setSuccessCriteria(task.successCriteria);
      setTaskType(task.taskType ?? "");
      setDifficulty(task.difficulty);
      setGraderConfig(task.graderConfig ?? "");
    }
  }, [task]);

  const saveMutation = useMutation({
    mutationFn: () => {
      const req = {
        name,
        description: description || undefined,
        input,
        successCriteria,
        taskType: taskType || undefined,
        difficulty,
        graderConfig: graderConfig || undefined,
        orgId: id,
      };
      return isNew
        ? api.eval.createTask(req)
        : api.eval.updateTask(taskId, req);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["eval", "tasks"] });
      router.push(`/orgs/${id}/eval/tasks`);
    },
  });

  if (!isNew && isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6 flex items-center gap-3">
        <Link href={`/orgs/${id}/eval/tasks`}>
          <Button variant="ghost" size="sm">
            <ArrowLeft size={14} />
          </Button>
        </Link>
        <h1 className="text-xl font-bold text-foreground">
          {isNew ? t("createBtn") : t("editTask")}
        </h1>
      </div>

      <Card>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            saveMutation.mutate();
          }}
        >
          <Input
            label={t("nameLabel")}
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder={t("namePlaceholder")}
            required
          />

          <div>
            <label className="mb-1 block text-sm font-medium text-foreground">{t("descLabel")}</label>
            <textarea
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t("descPlaceholder")}
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-foreground">{t("inputLabel")}</label>
            <textarea
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              rows={5}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={t("inputPlaceholder")}
              required
            />
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-foreground">{t("criteriaLabel")}</label>
            <textarea
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              rows={3}
              value={successCriteria}
              onChange={(e) => setSuccessCriteria(e.target.value)}
              placeholder={t("criteriaPlaceholder")}
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-foreground">{t("typeLabel")}</label>
              <select
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                value={taskType}
                onChange={(e) => setTaskType(e.target.value)}
              >
                <option value="">—</option>
                <option value="SDLC">SDLC</option>
                <option value="GOVERNANCE">GOVERNANCE</option>
                <option value="CUSTOM">CUSTOM</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-foreground">{t("difficultyLabel")}</label>
              <select
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value)}
              >
                <option value="EASY">{t("diffEasy")}</option>
                <option value="MEDIUM">{t("diffMedium")}</option>
                <option value="HARD">{t("diffHard")}</option>
              </select>
            </div>
          </div>

          <div>
            <label className="mb-1 block text-sm font-medium text-foreground">{t("graderConfigLabel")}</label>
            <textarea
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm font-mono text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
              rows={4}
              value={graderConfig}
              onChange={(e) => setGraderConfig(e.target.value)}
              placeholder={t("graderConfigPlaceholder")}
            />
          </div>

          {saveMutation.isError && (
            <div className="rounded-md border border-destructive/40 bg-destructive/20 px-3 py-2 text-sm text-destructive-foreground">
              {(saveMutation.error as Error).message}
            </div>
          )}

          <div className="flex gap-2 pt-2">
            <Button type="submit" loading={saveMutation.isPending}>
              {isNew ? t("createBtn") : t("saveBtn")}
            </Button>
            <Link href={`/orgs/${id}/eval/tasks`}>
              <Button variant="secondary">{t("cancel")}</Button>
            </Link>
          </div>
        </form>
      </Card>
    </div>
  );
}

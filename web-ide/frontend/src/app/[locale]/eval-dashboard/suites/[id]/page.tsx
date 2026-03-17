"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { useParams, useRouter } from "next/navigation";
import {
  evalApi,
  type SuiteResponse,
  type EvalTask,
  type RunResponse,
  type Difficulty,
  type GraderConfig,
  type TranscriptTurn,
} from "@/lib/eval-api";

const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD", "EXPERT"];

function LifecycleBadge({ lifecycle }: { lifecycle: string }) {
  const colors: Record<string, string> = {
    CAPABILITY: "bg-blue-500/15 text-blue-400",
    REGRESSION: "bg-yellow-500/15 text-yellow-400",
    SATURATED: "bg-green-500/15 text-green-400",
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${colors[lifecycle] ?? "bg-muted text-muted-foreground"}`}>
      {lifecycle}
    </span>
  );
}

function PlatformBadge({ platform }: { platform: string }) {
  const colors: Record<string, string> = {
    FORGE: "bg-purple-500/15 text-purple-400",
    SYNAPSE: "bg-cyan-500/15 text-cyan-400",
    APPLICATION: "bg-orange-500/15 text-orange-400",
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${colors[platform] ?? "bg-muted text-muted-foreground"}`}>
      {platform}
    </span>
  );
}

function DifficultyBadge({ difficulty }: { difficulty: string }) {
  const colors: Record<string, string> = {
    EASY: "bg-green-500/15 text-green-400",
    MEDIUM: "bg-yellow-500/15 text-yellow-400",
    HARD: "bg-orange-500/15 text-orange-400",
    EXPERT: "bg-red-500/15 text-red-400",
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${colors[difficulty] ?? "bg-muted text-muted-foreground"}`}>
      {difficulty}
    </span>
  );
}

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PENDING: "bg-muted text-muted-foreground",
    RUNNING: "bg-blue-500/15 text-blue-400",
    COMPLETED: "bg-green-500/15 text-green-400",
    FAILED: "bg-red-500/15 text-red-400",
    CANCELLED: "bg-muted text-muted-foreground",
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${colors[status] ?? "bg-muted text-muted-foreground"}`}>
      {status}
    </span>
  );
}

function fmt(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${(ms / 60000).toFixed(1)}m`;
}

function pct(n: number | undefined): string {
  if (n == null) return "-";
  return `${(n * 100).toFixed(1)}%`;
}

export default function SuiteDetailPage() {
  const t = useTranslations("evalDashboard");
  const params = useParams();
  const router = useRouter();
  const suiteId = Array.isArray(params.id) ? params.id[0] : (params.id as string);

  const [suite, setSuite] = useState<SuiteResponse | null>(null);
  const [tasks, setTasks] = useState<EvalTask[]>([]);
  const [runs, setRuns] = useState<RunResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showCreateTask, setShowCreateTask] = useState(false);
  const [showCreateRun, setShowCreateRun] = useState(false);
  const [showSubmitTranscript, setShowSubmitTranscript] = useState(false);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [suiteData, tasksData, runsData] = await Promise.all([
        evalApi.getSuite(suiteId),
        evalApi.listTasks(suiteId),
        evalApi.listRuns(suiteId),
      ]);
      setSuite(suiteData);
      setTasks(tasksData);
      setRuns(runsData);
    } catch {
      // API may not be available
    } finally {
      setLoading(false);
    }
  }, [suiteId]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  if (loading) {
    return (
      <div className="min-h-screen bg-background p-6 max-w-6xl mx-auto">
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background p-6 space-y-8 max-w-6xl mx-auto">
      {/* Header */}
      <div>
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="text-xl font-semibold">{suite?.name ?? "Suite"}</h1>
            {suite?.description && (
              <p className="mt-1 text-sm text-muted-foreground">{suite.description}</p>
            )}
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            {suite && <PlatformBadge platform={suite.platform} />}
            {suite && <span className="text-xs text-muted-foreground">{suite.agentType}</span>}
            {suite && <LifecycleBadge lifecycle={suite.lifecycle} />}
          </div>
        </div>
        <p className="mt-1 text-xs text-muted-foreground font-mono">ID: {suiteId}</p>
      </div>

      {/* Tasks section */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">{t("tasks")} ({tasks.length})</h2>
          <button
            onClick={() => setShowCreateTask(true)}
            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90"
          >
            + Add Task
          </button>
        </div>

        {tasks.length === 0 ? (
          <div className="rounded-lg border border-border py-8 text-center">
            <p className="text-sm text-muted-foreground">No tasks yet. Add the first task to get started.</p>
          </div>
        ) : (
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">{t("name")}</th>
                  <th className="px-4 py-2 text-left font-medium">Difficulty</th>
                  <th className="px-4 py-2 text-left font-medium">{t("lifecycle")}</th>
                  <th className="px-4 py-2 text-center font-medium">Assertions</th>
                  <th className="px-4 py-2 text-left font-medium">Tags</th>
                </tr>
              </thead>
              <tbody>
                {tasks.map(task => {
                  const assertionCount = task.graderConfigs.reduce(
                    (sum, g) => sum + (g.assertions?.length ?? 0),
                    0
                  );
                  return (
                    <tr key={task.id} className="border-t border-border hover:bg-muted/30">
                      <td className="px-4 py-2">
                        <div className="font-medium">{task.name}</div>
                        {task.description && (
                          <p className="mt-0.5 text-xs text-muted-foreground truncate max-w-xs">{task.description}</p>
                        )}
                      </td>
                      <td className="px-4 py-2">
                        <DifficultyBadge difficulty={task.difficulty} />
                      </td>
                      <td className="px-4 py-2">
                        <LifecycleBadge lifecycle={task.tags.find(t => ["CAPABILITY","REGRESSION","SATURATED"].includes(t)) ?? "CAPABILITY"} />
                      </td>
                      <td className="px-4 py-2 text-center font-mono text-xs">{assertionCount}</td>
                      <td className="px-4 py-2">
                        <div className="flex gap-1 flex-wrap">
                          {task.tags.map(tag => (
                            <span key={tag} className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{tag}</span>
                          ))}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Runs section */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">{t("runs")} ({runs.length})</h2>
          <div className="flex gap-2">
            <button
              onClick={() => setShowCreateRun(true)}
              className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90"
              disabled={tasks.length === 0}
            >
              + Run Eval
            </button>
            <button
              onClick={() => setShowSubmitTranscript(true)}
              className="rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:bg-muted"
              disabled={tasks.length === 0}
            >
              + Manual Trial
            </button>
          </div>
        </div>

        {runs.length === 0 ? (
          <div className="rounded-lg border border-border py-8 text-center">
            <p className="text-sm text-muted-foreground">No runs yet. Execute a new run to evaluate this suite.</p>
          </div>
        ) : (
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-center font-medium">#</th>
                  <th className="px-4 py-2 text-left font-medium">Status</th>
                  <th className="px-4 py-2 text-left font-medium">Model</th>
                  <th className="px-4 py-2 text-center font-medium">k</th>
                  <th className="px-4 py-2 text-center font-medium">Pass Rate</th>
                  <th className="px-4 py-2 text-center font-medium">Pass@k</th>
                  <th className="px-4 py-2 text-center font-medium">Pass^k</th>
                  <th className="px-4 py-2 text-center font-medium">Duration</th>
                </tr>
              </thead>
              <tbody>
                {runs.map((run, idx) => (
                  <tr
                    key={run.id}
                    className="border-t border-border hover:bg-muted/30 cursor-pointer"
                    onClick={() => router.push(`/eval-dashboard/runs/${run.id}`)}
                  >
                    <td className="px-4 py-2 text-center font-mono text-xs text-muted-foreground">{idx + 1}</td>
                    <td className="px-4 py-2"><StatusBadge status={run.status} /></td>
                    <td className="px-4 py-2 text-xs font-mono">{run.model ?? "-"}</td>
                    <td className="px-4 py-2 text-center font-mono text-xs">{run.trialsPerTask}</td>
                    <td className="px-4 py-2 text-center font-mono text-xs">{pct(run.summary?.overallPassRate)}</td>
                    <td className="px-4 py-2 text-center font-mono text-xs">{pct(run.summary?.passAtK)}</td>
                    <td className="px-4 py-2 text-center font-mono text-xs">{pct(run.summary?.passPowerK)}</td>
                    <td className="px-4 py-2 text-center font-mono text-xs">{run.summary ? fmt(run.summary.totalDurationMs) : "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Modals */}
      {showSubmitTranscript && (
        <SubmitTranscriptModal
          suiteId={suiteId}
          tasks={tasks}
          onClose={() => setShowSubmitTranscript(false)}
          onCreated={() => { setShowSubmitTranscript(false); fetchAll(); }}
        />
      )}

      {showCreateTask && (
        <CreateTaskModal
          suiteId={suiteId}
          onClose={() => setShowCreateTask(false)}
          onCreated={() => { setShowCreateTask(false); fetchAll(); }}
        />
      )}

      {showCreateRun && (
        <CreateRunModal
          suiteId={suiteId}
          onClose={() => setShowCreateRun(false)}
          onCreated={(run) => {
            setShowCreateRun(false);
            setRuns(prev => [run, ...prev]);
            router.push(`/eval-dashboard/runs/${run.id}`);
          }}
        />
      )}
    </div>
  );
}

// ── Create Task Modal ────────────────────────────────────────────

function CreateTaskModal({
  suiteId,
  onClose,
  onCreated,
}: {
  suiteId: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const t = useTranslations("evalDashboard");
  const [name, setName] = useState("");
  const [prompt, setPrompt] = useState("");
  const [difficulty, setDifficulty] = useState<Difficulty>("MEDIUM");
  const [graderJson, setGraderJson] = useState(
    JSON.stringify(
      [
        {
          type: "CODE_BASED",
          assertions: [
            {
              type: "CONTAINS",
              expected: "",
              description: "Output contains expected value",
            },
          ],
        },
      ] satisfies GraderConfig[],
      null,
      2
    )
  );
  const [jsonError, setJsonError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleJsonChange = (val: string) => {
    setGraderJson(val);
    try {
      JSON.parse(val);
      setJsonError("");
    } catch {
      setJsonError("Invalid JSON");
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !prompt.trim() || jsonError) return;
    let graderConfigs: GraderConfig[];
    try {
      graderConfigs = JSON.parse(graderJson) as GraderConfig[];
    } catch {
      setJsonError("Invalid JSON");
      return;
    }
    setSubmitting(true);
    try {
      await evalApi.createTask(suiteId, { name: name.trim(), prompt: prompt.trim(), difficulty, graderConfigs });
      onCreated();
    } catch {
      // TODO: show error
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <form
        onClick={e => e.stopPropagation()}
        onSubmit={handleSubmit}
        className="w-full max-w-lg rounded-lg border border-border bg-card p-6 space-y-4 shadow-lg max-h-[90vh] overflow-y-auto"
      >
        <h2 className="text-lg font-semibold">Add Task</h2>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">{t("name")} *</label>
          <input
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm"
            required
          />
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">Prompt *</label>
          <textarea
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            rows={4}
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm font-mono resize-y"
            required
          />
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">Difficulty</label>
          <select
            value={difficulty}
            onChange={e => setDifficulty(e.target.value as Difficulty)}
            className="w-full rounded border border-input bg-background px-2 py-1.5 text-sm"
          >
            {DIFFICULTIES.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">
            Grader Configs (JSON)
            {jsonError && <span className="ml-2 text-red-400">{jsonError}</span>}
          </label>
          <textarea
            value={graderJson}
            onChange={e => handleJsonChange(e.target.value)}
            rows={8}
            className={`w-full rounded border bg-background px-3 py-1.5 text-xs font-mono resize-y ${jsonError ? "border-red-500" : "border-input"}`}
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted">
            {t("cancel")}
          </button>
          <button
            type="submit"
            disabled={submitting || !name.trim() || !prompt.trim() || !!jsonError}
            className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {submitting ? t("creating") : t("create")}
          </button>
        </div>
      </form>
    </div>
  );
}

// ── Create Run Modal ─────────────────────────────────────────────

function CreateRunModal({
  suiteId,
  onClose,
  onCreated,
}: {
  suiteId: string;
  onClose: () => void;
  onCreated: (run: RunResponse) => void;
}) {
  const t = useTranslations("evalDashboard");
  const [trialsPerTask, setTrialsPerTask] = useState(3);
  const [model, setModel] = useState("claude-opus-4-5");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const run = await evalApi.createRun({
        suiteId,
        trialsPerTask,
        model: model.trim() || undefined,
      });
      onCreated(run);
    } catch {
      // TODO: show error
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <form
        onClick={e => e.stopPropagation()}
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-lg border border-border bg-card p-6 space-y-4 shadow-lg"
      >
        <h2 className="text-lg font-semibold">Execute Run</h2>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">Trials per Task</label>
          <input
            type="number"
            min={1}
            max={20}
            value={trialsPerTask}
            onChange={e => setTrialsPerTask(Number(e.target.value))}
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm"
          />
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">Model</label>
          <input
            value={model}
            onChange={e => setModel(e.target.value)}
            placeholder="claude-opus-4-5"
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm"
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted">
            {t("cancel")}
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {submitting ? "Submitting..." : "Run Eval"}
          </button>
        </div>
      </form>
    </div>
  );
}

// ── Submit Transcript Modal ─────────────────────────────────────

interface GradeResult {
  score: number;
  passed: boolean;
  explanation: string;
  assertionResults?: { description: string; passed: boolean; expected: string; actual: string }[];
}

function SubmitTranscriptModal({
  suiteId,
  tasks,
  onClose,
  onCreated,
}: {
  suiteId: string;
  tasks: EvalTask[];
  onClose: () => void;
  onCreated?: () => void;
}) {
  const [selectedTaskId, setSelectedTaskId] = useState(tasks[0]?.id ?? "");
  const [transcriptJson, setTranscriptJson] = useState(
    JSON.stringify(
      [
        { role: "user", content: "（用户请求）" },
        {
          role: "assistant",
          content: "（Agent 回复）",
          toolCalls: [
            { toolName: "tool_name", arguments: {} },
          ],
        },
      ] satisfies TranscriptTurn[],
      null,
      2
    )
  );
  const [jsonError, setJsonError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<GradeResult[] | null>(null);

  const selectedTask = tasks.find(t => t.id === selectedTaskId);

  const handleJsonChange = (val: string) => {
    setTranscriptJson(val);
    try {
      JSON.parse(val);
      setJsonError("");
    } catch {
      setJsonError("Invalid JSON");
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTaskId || jsonError) return;
    let turns: TranscriptTurn[];
    try {
      turns = JSON.parse(transcriptJson) as TranscriptTurn[];
    } catch {
      setJsonError("Invalid JSON");
      return;
    }
    setSubmitting(true);
    setResult(null);
    try {
      const res = await evalApi.submitTranscript({
        suiteId,
        taskId: selectedTaskId,
        source: "EXTERNAL",
        turns,
        metadata: { evaluatedVia: "dashboard" },
      });
      const grades = (res as Record<string, unknown>).grades as GradeResult[];
      setResult(grades);
      onCreated?.();
    } catch {
      setResult([{ score: -1, passed: false, explanation: "Submission failed", assertionResults: [] }]);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <div
        onClick={e => e.stopPropagation()}
        className="w-full max-w-2xl rounded-lg border border-border bg-card p-6 space-y-4 shadow-lg max-h-[90vh] overflow-y-auto"
      >
        <h2 className="text-lg font-semibold">Manual Trial — Submit Agent Transcript</h2>
        <p className="text-xs text-muted-foreground">Paste an Agent&apos;s conversation to create a Trial with grading. The result will appear in the Runs list.</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Task selector */}
          <div>
            <label className="block text-xs text-muted-foreground mb-1">Evaluate against task</label>
            <select
              value={selectedTaskId}
              onChange={e => { setSelectedTaskId(e.target.value); setResult(null); }}
              className="w-full rounded border border-input bg-background px-2 py-1.5 text-sm"
            >
              {tasks.map(t => (
                <option key={t.id} value={t.id}>{t.name} ({t.difficulty})</option>
              ))}
            </select>
          </div>

          {/* Show task criteria */}
          {selectedTask && (
            <div className="rounded border border-border bg-muted/20 p-3 space-y-1">
              <div className="text-xs font-medium text-muted-foreground">Grading criteria ({selectedTask.graderConfigs.reduce((s, g) => s + (g.assertions?.length ?? 0), 0)} assertions):</div>
              {selectedTask.graderConfigs.map((g, gi) =>
                g.assertions?.map((a, ai) => (
                  <div key={`${gi}-${ai}`} className="text-xs text-muted-foreground flex gap-2">
                    <span className="text-muted-foreground/50 font-mono">{a.type}</span>
                    <span>{a.description}</span>
                  </div>
                ))
              )}
            </div>
          )}

          {/* Transcript JSON */}
          <div>
            <label className="block text-xs text-muted-foreground mb-1">
              Agent transcript (JSON array of turns)
              {jsonError && <span className="ml-2 text-red-400">{jsonError}</span>}
            </label>
            <textarea
              value={transcriptJson}
              onChange={e => handleJsonChange(e.target.value)}
              rows={12}
              className={`w-full rounded border bg-background px-3 py-1.5 text-xs font-mono resize-y ${jsonError ? "border-red-500" : "border-input"}`}
            />
          </div>

          <div className="flex justify-end gap-2">
            <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted">
              Close
            </button>
            <button
              type="submit"
              disabled={submitting || !!jsonError || !selectedTaskId}
              className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {submitting ? "Evaluating..." : "Evaluate"}
            </button>
          </div>
        </form>

        {/* Results */}
        {result && (
          <div className="border-t border-border pt-4 space-y-4">
            <h3 className="text-sm font-semibold">Evaluation Result</h3>
            {result.map((grade, i) => {
              const isModelBased = !grade.assertionResults || grade.assertionResults.length === 0;
              const graderLabel = isModelBased ? "MODEL-BASED" : "CODE-BASED";
              const graderColor = isModelBased ? "bg-purple-500/15 text-purple-400" : "bg-cyan-500/15 text-cyan-400";
              const graderIcon = isModelBased ? "🧠" : "⚙️";

              return (
                <div key={i} className="rounded-lg border border-border p-4 space-y-2">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${graderColor}`}>
                      {graderIcon} {graderLabel}
                    </span>
                    <span className="text-[10px] text-muted-foreground">
                      {isModelBased ? "LLM judge evaluates quality, clarity, and reasoning" : "Deterministic rule checks on output and tool usage"}
                    </span>
                  </div>

                  <div className="flex items-center gap-3">
                    <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-bold ${grade.passed ? "bg-green-500/15 text-green-400" : "bg-red-500/15 text-red-400"}`}>
                      {grade.passed ? "PASS" : "FAIL"}
                    </span>
                    <span className="text-2xl font-bold font-mono">{grade.score >= 0 ? `${(grade.score * 100).toFixed(0)}%` : "Error"}</span>
                  </div>

                  {/* Model-Based: show explanation as main content */}
                  {isModelBased && grade.explanation && (
                    <div className="rounded bg-muted/20 p-3 text-xs text-muted-foreground leading-relaxed">
                      <div className="text-[10px] text-purple-400 font-medium mb-1">Judge Assessment:</div>
                      {grade.explanation}
                    </div>
                  )}

                  {/* Code-Based: show assertion list */}
                  {!isModelBased && grade.assertionResults && grade.assertionResults.length > 0 && (
                    <div className="space-y-1.5">
                      <div className="text-[10px] text-muted-foreground">{grade.explanation}</div>
                      {grade.assertionResults.map((a, j) => (
                        <div key={j} className="flex items-start gap-2 text-xs">
                          <span className={`mt-0.5 font-mono flex-shrink-0 ${a.passed ? "text-green-400" : "text-red-400"}`}>
                            {a.passed ? "[x]" : "[ ]"}
                          </span>
                          <div>
                            <span className={a.passed ? "text-green-400" : "text-red-400"}>{a.description}</span>
                            {!a.passed && a.expected && (
                              <div className="text-muted-foreground mt-0.5">
                                expected: <span className="font-mono">{a.expected}</span>
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

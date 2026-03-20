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
  type GraderType,
  type AssertionConfig,
  type RubricCriterion,
  type TranscriptTurn,
  type TrendResponse,
  type RegressionReport,
  type LifecycleEvalResponse,
  type Lifecycle,
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
  const [trends, setTrends] = useState<TrendResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [showCreateTask, setShowCreateTask] = useState(false);
  const [showCreateRun, setShowCreateRun] = useState(false);
  const [showSubmitTranscript, setShowSubmitTranscript] = useState(false);

  // Regression state
  const [baselineRunId, setBaselineRunId] = useState("");
  const [currentRunId, setCurrentRunId] = useState("");
  const [regressionResult, setRegressionResult] = useState<RegressionReport | null>(null);
  const [regressionLoading, setRegressionLoading] = useState(false);

  // Task lifecycle expand state
  const [expandedTaskId, setExpandedTaskId] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [suiteData, tasksData, runsData, trendsData] = await Promise.all([
        evalApi.getSuite(suiteId),
        evalApi.listTasks(suiteId),
        evalApi.listRuns(suiteId),
        evalApi.getTrends(suiteId).catch(() => null),
      ]);
      setSuite(suiteData);
      setTasks(tasksData);
      setRuns(runsData);
      setTrends(trendsData);
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

      {/* Trends section — suite-level overview */}
      <section className="space-y-3">
        <h2 className="text-base font-semibold">{t("passRateTrends")}</h2>
        <TrendChart trends={trends} />
      </section>

      {/* Regression Detection — suite-level comparison */}
      <section className="space-y-3">
        <h2 className="text-base font-semibold">{t("regressionDetection")}</h2>
        <div className="rounded-lg border border-border p-4 space-y-4">
          <div className="flex flex-wrap gap-3 items-end">
            <div>
              <label className="block text-xs text-muted-foreground mb-1">{t("baselineRun")}</label>
              <select
                value={baselineRunId}
                onChange={e => setBaselineRunId(e.target.value)}
                className="rounded border border-input bg-background px-2 py-1.5 text-xs min-w-[200px]"
              >
                <option value="">{t("selectBaselineRun")}</option>
                {runs.map((run, idx) => (
                  <option key={run.id} value={run.id}>
                    Run #{idx + 1} ({run.model ?? "unknown"})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs text-muted-foreground mb-1">{t("currentRun")}</label>
              <select
                value={currentRunId}
                onChange={e => setCurrentRunId(e.target.value)}
                className="rounded border border-input bg-background px-2 py-1.5 text-xs min-w-[200px]"
              >
                <option value="">{t("selectCurrentRun")}</option>
                {runs.map((run, idx) => (
                  <option key={run.id} value={run.id}>
                    Run #{idx + 1} ({run.model ?? "unknown"})
                  </option>
                ))}
              </select>
            </div>
            <button
              onClick={async () => {
                if (!baselineRunId || !currentRunId) return;
                setRegressionLoading(true);
                setRegressionResult(null);
                try {
                  const result = await evalApi.detectRegressions(suiteId, currentRunId, baselineRunId);
                  setRegressionResult(result);
                } catch {
                  // ignore
                } finally {
                  setRegressionLoading(false);
                }
              }}
              disabled={!baselineRunId || !currentRunId || regressionLoading}
              className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {regressionLoading ? t("comparing") : t("compare")}
            </button>
          </div>

          {regressionResult && (
            <div className="mt-2">
              {!regressionResult.hasRegressions ? (
                <div className="rounded border border-green-500/30 bg-green-500/10 px-4 py-3 text-sm text-green-400">
                  {t("noRegressions")}
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="rounded border border-red-500/30 bg-red-500/10 px-4 py-2 text-xs text-red-400 font-medium">
                    {t("regressionsDetected", { count: regressionResult.regressions.length })}
                  </div>
                  <div className="rounded-lg border border-border overflow-hidden">
                    <table className="w-full text-xs">
                      <thead className="bg-muted/50">
                        <tr>
                          <th className="px-3 py-2 text-left font-medium">{t("task")}</th>
                          <th className="px-3 py-2 text-center font-medium">{t("baseline")}</th>
                          <th className="px-3 py-2 text-center font-medium">{t("current")}</th>
                          <th className="px-3 py-2 text-center font-medium">{t("significant")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {regressionResult.regressions.map((reg, i) => (
                          <tr key={i} className="border-t border-border">
                            <td className="px-3 py-2 font-medium">{reg.taskName}</td>
                            <td className="px-3 py-2 text-center font-mono">{pct(reg.baselinePassRate)}</td>
                            <td className="px-3 py-2 text-center font-mono text-red-400">{pct(reg.currentPassRate)}</td>
                            <td className="px-3 py-2 text-center">
                              {reg.isStatisticallySignificant ? (
                                <span className="text-red-400 font-medium">{t("significantYes")}</span>
                              ) : (
                                <span className="text-muted-foreground">{t("significantNo")}</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </section>

      {/* Tasks section */}
      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold">{t("tasks")} ({tasks.length})</h2>
          <button
            onClick={() => setShowCreateTask(true)}
            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90"
          >
            + {t("addTask")}
          </button>
        </div>

        {tasks.length === 0 ? (
          <div className="rounded-lg border border-border py-8 text-center">
            <p className="text-sm text-muted-foreground">{t("noTasksYet")}</p>
          </div>
        ) : (
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">{t("name")}</th>
                  <th className="px-4 py-2 text-left font-medium">{t("difficulty")}</th>
                  <th className="px-4 py-2 text-left font-medium">{t("lifecycle")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("graders")}</th>
                  <th className="px-4 py-2 text-left font-medium">{t("tags")}</th>
                </tr>
              </thead>
              <tbody>
                {tasks.map(task => {
                  const hasCode = task.graderConfigs.some(g => g.type === "CODE_BASED");
                  const hasModel = task.graderConfigs.some(g => g.type === "MODEL_BASED");
                  const codeCount = task.graderConfigs.reduce((s, g) => s + (g.type === "CODE_BASED" ? (g.assertions?.length ?? 0) : 0), 0);
                  const modelCount = task.graderConfigs.reduce((s, g) => s + (g.type === "MODEL_BASED" ? (g.rubric?.length ?? 0) : 0), 0);
                  const isExpanded = expandedTaskId === task.id;
                  return (
                    <React.Fragment key={task.id}>
                      <tr
                        className="border-t border-border hover:bg-muted/30 cursor-pointer"
                        onClick={() => setExpandedTaskId(isExpanded ? null : task.id)}
                      >
                        <td className="px-4 py-2">
                          <div className="flex items-center gap-1.5">
                            <span className="text-muted-foreground text-[10px]">{isExpanded ? "▼" : "▶"}</span>
                            <div>
                              <div className="font-medium">{task.name}</div>
                              {task.description && (
                                <p className="mt-0.5 text-xs text-muted-foreground truncate max-w-xs">{task.description}</p>
                              )}
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-2">
                          <DifficultyBadge difficulty={task.difficulty} />
                        </td>
                        <td className="px-4 py-2">
                          <LifecycleBadge lifecycle={task.tags.find(tg => ["CAPABILITY","REGRESSION","SATURATED"].includes(tg)) ?? "CAPABILITY"} />
                        </td>
                        <td className="px-4 py-2 text-center">
                          <div className="flex items-center justify-center gap-1">
                            {hasCode && <span className="rounded bg-cyan-500/15 text-cyan-400 px-1.5 py-0.5 text-[10px]">⚙️ {codeCount}</span>}
                            {hasModel && <span className="rounded bg-purple-500/15 text-purple-400 px-1.5 py-0.5 text-[10px]">🧠 {modelCount}</span>}
                          </div>
                        </td>
                        <td className="px-4 py-2">
                          <div className="flex gap-1 flex-wrap">
                            {task.tags.map(tag => (
                              <span key={tag} className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{tag}</span>
                            ))}
                          </div>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr className="border-t border-border bg-muted/10">
                          <td colSpan={5} className="px-6 py-4">
                            <TaskLifecyclePanel
                              suiteId={suiteId}
                              task={task}
                              onUpdated={fetchAll}
                            />
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
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
              + {t("runEval")}
            </button>
            <button
              onClick={() => setShowSubmitTranscript(true)}
              className="rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:bg-muted"
              disabled={tasks.length === 0}
            >
              + {t("manualTrial")}
            </button>
          </div>
        </div>

        {runs.length === 0 ? (
          <div className="rounded-lg border border-border py-8 text-center">
            <p className="text-sm text-muted-foreground">{t("noRunsYet")}</p>
          </div>
        ) : (
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-center font-medium">#</th>
                  <th className="px-4 py-2 text-left font-medium">{t("status")}</th>
                  <th className="px-4 py-2 text-left font-medium">{t("model")}</th>
                  <th className="px-4 py-2 text-center font-medium">k</th>
                  <th className="px-4 py-2 text-center font-medium">{t("passRate")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("passAtK")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("passPowerK")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("duration")}</th>
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

const ASSERTION_TYPES = [
  "contains",
  "not_contains",
  "matches_pattern",
  "json_schema",
  "json_path",
  "tool_used",
  "tool_not_used",
  "tool_call_count",
  "tool_call_order",
  "turn_count_max",
] as const;

interface GraderDraft {
  id: number;
  type: GraderType;
  // CODE_BASED
  assertions: (AssertionConfig & { _id: number })[];
  // MODEL_BASED
  model: string;
  rubric: (RubricCriterion & { _id: number })[];
}

let _nextId = 1;
function nextId() { return _nextId++; }

function makeDefaultAssertion(): AssertionConfig & { _id: number } {
  return { _id: nextId(), type: "contains", expected: "", description: "" };
}

function makeDefaultCriterion(): RubricCriterion & { _id: number } {
  return { _id: nextId(), criterion: "", weight: 1, description: "" };
}

function makeGrader(type: GraderType): GraderDraft {
  return {
    id: nextId(),
    type,
    assertions: type === "CODE_BASED" ? [makeDefaultAssertion()] : [],
    model: "MiniMax-M2.5",
    rubric: type === "MODEL_BASED" ? [makeDefaultCriterion()] : [],
  };
}

function GraderEditor({
  grader,
  onChange,
  onRemove,
}: {
  grader: GraderDraft;
  onChange: (g: GraderDraft) => void;
  onRemove: () => void;
}) {
  const t = useTranslations("evalDashboard");
  const inputCls = "rounded border border-input bg-background px-2 py-1 text-xs";

  if (grader.type === "CODE_BASED") {
    return (
      <div className="rounded-lg border border-border bg-muted/10 p-3 space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-cyan-400">CODE-BASED</span>
          <button type="button" onClick={onRemove} className="text-xs text-muted-foreground hover:text-destructive">{t("remove")}</button>
        </div>
        <div className="space-y-1.5">
          {grader.assertions.map((a, idx) => (
            <div key={a._id} className="flex gap-1.5 items-start">
              <select
                value={a.type}
                onChange={e => {
                  const updated = [...grader.assertions];
                  updated[idx] = { ...a, type: e.target.value };
                  onChange({ ...grader, assertions: updated });
                }}
                className={`${inputCls} w-40 flex-shrink-0`}
              >
                {ASSERTION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
              <input
                placeholder="expected"
                value={a.expected}
                onChange={e => {
                  const updated = [...grader.assertions];
                  updated[idx] = { ...a, expected: e.target.value };
                  onChange({ ...grader, assertions: updated });
                }}
                className={`${inputCls} flex-1 min-w-0`}
              />
              <input
                placeholder="description"
                value={a.description}
                onChange={e => {
                  const updated = [...grader.assertions];
                  updated[idx] = { ...a, description: e.target.value };
                  onChange({ ...grader, assertions: updated });
                }}
                className={`${inputCls} flex-1 min-w-0`}
              />
              <button
                type="button"
                onClick={() => onChange({ ...grader, assertions: grader.assertions.filter((_, i) => i !== idx) })}
                className="text-xs text-muted-foreground hover:text-destructive flex-shrink-0 mt-0.5"
              >✕</button>
            </div>
          ))}
        </div>
        <button
          type="button"
          onClick={() => onChange({ ...grader, assertions: [...grader.assertions, makeDefaultAssertion()] })}
          className="text-xs text-muted-foreground hover:text-foreground"
        >
          + {t("addAssertion")}
        </button>
      </div>
    );
  }

  // MODEL_BASED
  return (
    <div className="rounded-lg border border-border bg-muted/10 p-3 space-y-2">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-purple-400">MODEL-BASED</span>
        <button type="button" onClick={onRemove} className="text-xs text-muted-foreground hover:text-destructive">{t("remove")}</button>
      </div>
      <div>
        <label className="block text-[10px] text-muted-foreground mb-0.5">{t("judgeModel")}</label>
        <input
          value={grader.model}
          onChange={e => onChange({ ...grader, model: e.target.value })}
          className={`${inputCls} w-full`}
          placeholder="MiniMax-M2.5"
        />
      </div>
      <div className="space-y-1.5">
        {grader.rubric.map((r, idx) => (
          <div key={r._id} className="flex gap-1.5 items-start">
            <input
              placeholder="criterion"
              value={r.criterion}
              onChange={e => {
                const updated = [...grader.rubric];
                updated[idx] = { ...r, criterion: e.target.value };
                onChange({ ...grader, rubric: updated });
              }}
              className={`${inputCls} flex-1 min-w-0`}
            />
            <input
              type="number"
              min={0}
              max={10}
              step={0.1}
              placeholder="weight"
              value={r.weight}
              onChange={e => {
                const updated = [...grader.rubric];
                updated[idx] = { ...r, weight: Number(e.target.value) };
                onChange({ ...grader, rubric: updated });
              }}
              className={`${inputCls} w-16 flex-shrink-0`}
            />
            <input
              placeholder="description"
              value={r.description}
              onChange={e => {
                const updated = [...grader.rubric];
                updated[idx] = { ...r, description: e.target.value };
                onChange({ ...grader, rubric: updated });
              }}
              className={`${inputCls} flex-1 min-w-0`}
            />
            <button
              type="button"
              onClick={() => onChange({ ...grader, rubric: grader.rubric.filter((_, i) => i !== idx) })}
              className="text-xs text-muted-foreground hover:text-destructive flex-shrink-0 mt-0.5"
            >✕</button>
          </div>
        ))}
      </div>
      <button
        type="button"
        onClick={() => onChange({ ...grader, rubric: [...grader.rubric, makeDefaultCriterion()] })}
        className="text-xs text-muted-foreground hover:text-foreground"
      >
        + {t("addCriterion")}
      </button>
    </div>
  );
}

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
  const [graders, setGraders] = useState<GraderDraft[]>([makeGrader("CODE_BASED")]);
  const [submitting, setSubmitting] = useState(false);
  const [showAddMenu, setShowAddMenu] = useState(false);

  const updateGrader = (id: number, updated: GraderDraft) => {
    setGraders(prev => prev.map(g => (g.id === id ? updated : g)));
  };

  const removeGrader = (id: number) => {
    setGraders(prev => prev.filter(g => g.id !== id));
  };

  const addGrader = (type: GraderType) => {
    setGraders(prev => [...prev, makeGrader(type)]);
    setShowAddMenu(false);
  };

  const buildGraderConfigs = (): GraderConfig[] =>
    graders.map(g => {
      if (g.type === "CODE_BASED") {
        return {
          type: "CODE_BASED" as GraderType,
          assertions: g.assertions.map(({ _id: _a, ...rest }) => rest),
        };
      }
      return {
        type: "MODEL_BASED" as GraderType,
        model: g.model || "MiniMax-M2.5",
        rubric: g.rubric.map(({ _id: _r, ...rest }) => rest),
      };
    });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !prompt.trim()) return;
    setSubmitting(true);
    try {
      await evalApi.createTask(suiteId, {
        name: name.trim(),
        prompt: prompt.trim(),
        difficulty,
        graderConfigs: buildGraderConfigs(),
      });
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
        className="w-full max-w-2xl rounded-lg border border-border bg-card p-6 space-y-4 shadow-lg max-h-[90vh] overflow-y-auto"
      >
        <h2 className="text-lg font-semibold">{t("addTask")}</h2>

        {/* Basic info */}
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
          <label className="block text-xs text-muted-foreground mb-1">{t("prompt")} *</label>
          <textarea
            value={prompt}
            onChange={e => setPrompt(e.target.value)}
            rows={4}
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm font-mono resize-y"
            required
          />
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">{t("difficulty")}</label>
          <select
            value={difficulty}
            onChange={e => setDifficulty(e.target.value as Difficulty)}
            className="w-full rounded border border-input bg-background px-2 py-1.5 text-sm"
          >
            {DIFFICULTIES.map(d => <option key={d} value={d}>{d}</option>)}
          </select>
        </div>

        {/* Graders */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-xs text-muted-foreground">{t("graders")} ({graders.length})</label>
            <div className="relative">
              <button
                type="button"
                onClick={() => setShowAddMenu(prev => !prev)}
                className="rounded border border-dashed border-border px-2 py-1 text-xs text-muted-foreground hover:text-foreground hover:border-foreground/30"
              >
                + {t("addGrader")}
              </button>
              {showAddMenu && (
                <div className="absolute right-0 top-full mt-1 z-10 rounded border border-border bg-card shadow-lg min-w-[160px]">
                  {(["CODE_BASED", "MODEL_BASED"] as GraderType[]).map(type => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => addGrader(type)}
                      className="w-full px-3 py-2 text-left text-xs hover:bg-muted"
                    >
                      {type === "CODE_BASED" ? t("graderCodeBased") : t("graderModelBased")}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {graders.map(g => (
            <GraderEditor
              key={g.id}
              grader={g}
              onChange={updated => updateGrader(g.id, updated)}
              onRemove={() => removeGrader(g.id)}
            />
          ))}

          {graders.length === 0 && (
            <div className="rounded border border-dashed border-border py-4 text-center text-xs text-muted-foreground">
              {t("noGradersHint")}
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted">
            {t("cancel")}
          </button>
          <button
            type="submit"
            disabled={submitting || !name.trim() || !prompt.trim()}
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
        <h2 className="text-lg font-semibold">{t("executeRun")}</h2>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">{t("trialsPerTask")}</label>
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
          <label className="block text-xs text-muted-foreground mb-1">{t("model")}</label>
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
            {submitting ? t("submitting") : t("runEval")}
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
  const t = useTranslations("evalDashboard");
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
        <h2 className="text-lg font-semibold">{t("manualTrialTitle")}</h2>
        <p className="text-xs text-muted-foreground">{t("manualTrialDesc")}</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Task selector */}
          <div>
            <label className="block text-xs text-muted-foreground mb-1">{t("evaluateAgainstTask")}</label>
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
              <div className="text-xs font-medium text-muted-foreground">{t("gradingCriteria", { count: selectedTask.graderConfigs.reduce((s, g) => s + (g.assertions?.length ?? 0), 0) })}</div>
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
              {t("transcriptJson")}
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
              {t("close")}
            </button>
            <button
              type="submit"
              disabled={submitting || !!jsonError || !selectedTaskId}
              className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {submitting ? t("evaluating") : t("evaluate")}
            </button>
          </div>
        </form>

        {/* Results */}
        {result && (
          <div className="border-t border-border pt-4 space-y-4">
            <h3 className="text-sm font-semibold">{t("evaluationResult")}</h3>
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
                      {isModelBased ? t("llmJudgeHint") : t("codeRuleHint")}
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
                      <div className="text-[10px] text-purple-400 font-medium mb-1">{t("judgeAssessment")}</div>
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

// ── Trend Chart ───────────────────────────────────────────────────

function TrendChart({ trends }: { trends: TrendResponse | null }) {
  const t = useTranslations("evalDashboard");
  if (!trends || trends.dataPoints.length < 2) {
    return (
      <div className="rounded-lg border border-border py-8 text-center">
        <p className="text-sm text-muted-foreground">{t("notEnoughData")}</p>
      </div>
    );
  }

  const points = trends.dataPoints;
  const W = 800;
  const H = 200;
  const padL = 40;
  const padR = 16;
  const padT = 16;
  const padB = 28;
  const chartW = W - padL - padR;
  const chartH = H - padT - padB;
  const n = points.length;

  function xOf(i: number) {
    return padL + (n === 1 ? chartW / 2 : (i / (n - 1)) * chartW);
  }
  function yOf(v: number) {
    return padT + chartH - v * chartH;
  }

  const passRatePath = points
    .map((p, i) => `${i === 0 ? "M" : "L"}${xOf(i).toFixed(1)},${yOf(p.passRate).toFixed(1)}`)
    .join(" ");
  const avgScorePath = points
    .map((p, i) => `${i === 0 ? "M" : "L"}${xOf(i).toFixed(1)},${yOf(p.averageScore).toFixed(1)}`)
    .join(" ");

  const yTicks = [0, 0.25, 0.5, 0.75, 1];

  return (
    <div className="rounded-lg border border-border p-4">
      {/* Legend */}
      <div className="flex gap-4 mb-2 text-xs text-muted-foreground">
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-6 h-0.5 bg-green-400 rounded" />
          {t("passRateLegend")}
        </div>
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-6 h-0.5 bg-blue-400 rounded" />
          {t("avgScoreLegend")}
        </div>
      </div>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full"
        style={{ height: 200 }}
        preserveAspectRatio="xMidYMid meet"
      >
        {/* Y grid lines and labels */}
        {yTicks.map(v => (
          <g key={v}>
            <line
              x1={padL}
              y1={yOf(v)}
              x2={W - padR}
              y2={yOf(v)}
              stroke="currentColor"
              strokeOpacity={0.1}
              strokeWidth={1}
            />
            <text
              x={padL - 4}
              y={yOf(v) + 3}
              textAnchor="end"
              fontSize={9}
              fill="currentColor"
              fillOpacity={0.5}
            >
              {Math.round(v * 100)}%
            </text>
          </g>
        ))}
        {/* X axis labels */}
        {points.map((_, i) => (
          <text
            key={i}
            x={xOf(i)}
            y={H - 8}
            textAnchor="middle"
            fontSize={9}
            fill="currentColor"
            fillOpacity={0.5}
          >
            {i + 1}
          </text>
        ))}
        {/* Pass Rate line */}
        <path d={passRatePath} fill="none" stroke="#4ade80" strokeWidth={2} strokeLinejoin="round" />
        {points.map((p, i) => (
          <circle key={i} cx={xOf(i)} cy={yOf(p.passRate)} r={3} fill="#4ade80" />
        ))}
        {/* Avg Score line */}
        <path d={avgScorePath} fill="none" stroke="#60a5fa" strokeWidth={2} strokeLinejoin="round" />
        {points.map((p, i) => (
          <circle key={i} cx={xOf(i)} cy={yOf(p.averageScore)} r={3} fill="#60a5fa" />
        ))}
      </svg>
    </div>
  );
}

// ── Task Lifecycle Panel ─────────────────────────────────────────

function TaskLifecyclePanel({
  suiteId,
  task,
  onUpdated,
}: {
  suiteId: string;
  task: EvalTask;
  onUpdated: () => void;
}) {
  const t = useTranslations("evalDashboard");
  const [data, setData] = useState<LifecycleEvalResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [showGraduate, setShowGraduate] = useState(false);
  const [targetLifecycle, setTargetLifecycle] = useState<Lifecycle>("SATURATED");
  const [graduateReason, setGraduateReason] = useState("");
  const [graduating, setGraduating] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    evalApi.getTaskLifecycle(suiteId, task.id)
      .then(res => { if (!cancelled) { setData(res); setLoading(false); } })
      .catch(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [suiteId, task.id]);

  const currentLifecycle = data?.currentLifecycle ?? task.tags.find(tg => ["CAPABILITY","REGRESSION","SATURATED"].includes(tg)) ?? "CAPABILITY";
  const isSaturated = currentLifecycle === "SATURATED";

  const handleGraduate = async () => {
    if (!graduateReason.trim()) return;
    setGraduating(true);
    try {
      await evalApi.updateTaskLifecycle(suiteId, task.id, { lifecycle: targetLifecycle, reason: graduateReason });
      setShowGraduate(false);
      setGraduateReason("");
      onUpdated();
    } catch {
      // ignore
    } finally {
      setGraduating(false);
    }
  };

  if (loading) {
    return <p className="text-xs text-muted-foreground">{t("loadingLifecycle")}</p>;
  }

  if (isSaturated) {
    return (
      <div className="rounded border border-green-500/30 bg-green-500/10 px-4 py-3 text-xs text-green-400">
        {t("taskSaturated")}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* Status row */}
      <div className="flex flex-wrap gap-6 text-xs">
        <div>
          <span className="text-muted-foreground">{t("currentLifecycle")}</span>
          <LifecycleBadge lifecycle={currentLifecycle} />
        </div>
        {data && (
          <div>
            <span className="text-muted-foreground">{t("recommended")}</span>
            <LifecycleBadge lifecycle={data.recommendedLifecycle} />
          </div>
        )}
      </div>

      {/* Transition hint */}
      {data?.shouldTransition && (
        <div className="rounded border border-green-500/30 bg-green-500/10 px-3 py-2 text-xs text-green-400">
          {t("readyToTransition")}{data.reason}
        </div>
      )}
      {data && !data.shouldTransition && (
        <p className="text-xs text-muted-foreground">{data.reason}</p>
      )}

      {/* Metrics */}
      {data && (
        <div className="flex gap-6 text-xs">
          <div>
            <span className="text-muted-foreground">{t("consecutivePassingRuns")}</span>
            <span className="font-mono font-medium">{data.consecutivePassingRuns}</span>
          </div>
          <div>
            <span className="text-muted-foreground">{t("recentPassRate")}</span>
            <span className="font-mono font-medium">{pct(data.recentPassRate)}</span>
          </div>
          {data.recentPassPowerK != null && (
            <div>
              <span className="text-muted-foreground">{t("passPowerK")}: </span>
              <span className="font-mono font-medium">{pct(data.recentPassPowerK)}</span>
            </div>
          )}
        </div>
      )}

      {/* Graduate button */}
      {!showGraduate && (
        <button
          onClick={() => setShowGraduate(true)}
          className="rounded border border-border px-3 py-1.5 text-xs hover:bg-muted"
        >
          {t("graduateTask")}
        </button>
      )}

      {/* Graduate confirmation */}
      {showGraduate && (
        <div className="rounded-lg border border-border p-4 space-y-3 bg-muted/10">
          <p className="text-xs font-medium">{t("confirmGraduation")}</p>
          <div className="flex gap-3 flex-wrap">
            <div>
              <label className="block text-[10px] text-muted-foreground mb-1">{t("targetLifecycle")}</label>
              <select
                value={targetLifecycle}
                onChange={e => setTargetLifecycle(e.target.value as Lifecycle)}
                className="rounded border border-input bg-background px-2 py-1 text-xs"
              >
                <option value="REGRESSION">REGRESSION</option>
                <option value="SATURATED">SATURATED</option>
              </select>
            </div>
            <div className="flex-1 min-w-[200px]">
              <label className="block text-[10px] text-muted-foreground mb-1">{t("reason")} *</label>
              <input
                value={graduateReason}
                onChange={e => setGraduateReason(e.target.value)}
                placeholder={t("reasonPlaceholder")}
                className="w-full rounded border border-input bg-background px-2 py-1 text-xs"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => { setShowGraduate(false); setGraduateReason(""); }}
              className="rounded px-3 py-1 text-xs text-muted-foreground hover:bg-muted"
            >
              {t("cancel")}
            </button>
            <button
              onClick={handleGraduate}
              disabled={!graduateReason.trim() || graduating}
              className="rounded bg-primary px-3 py-1 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {graduating ? t("updating") : t("confirmGraduate")}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

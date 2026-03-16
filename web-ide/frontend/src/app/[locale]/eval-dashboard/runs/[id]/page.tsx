"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { useParams } from "next/navigation";
import { Link } from "@/navigation";
import {
  evalApi,
  type RunResponse,
  type TrialResponse,
  type AssertionResult,
} from "@/lib/eval-api";

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

function OutcomeBadge({ outcome }: { outcome: string }) {
  const colors: Record<string, string> = {
    PASS: "bg-green-500/15 text-green-400",
    FAIL: "bg-red-500/15 text-red-400",
    PARTIAL: "bg-yellow-500/15 text-yellow-400",
    ERROR: "bg-muted text-muted-foreground",
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${colors[outcome] ?? "bg-muted text-muted-foreground"}`}>
      {outcome}
    </span>
  );
}

function StatCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-xl font-semibold font-mono">{value}</div>
    </div>
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

function shortId(id: string): string {
  return id.slice(0, 8);
}

// ── Assertion Results List ───────────────────────────────────────

function AssertionList({ assertions }: { assertions: AssertionResult[] }) {
  if (assertions.length === 0) {
    return <p className="text-xs text-muted-foreground px-4 py-2">No assertions recorded.</p>;
  }
  return (
    <div className="px-4 py-3 space-y-2">
      {assertions.map((a, i) => (
        <div key={i} className="flex items-start gap-2 text-xs">
          <span className={`mt-0.5 flex-shrink-0 font-mono ${a.passed ? "text-green-400" : "text-red-400"}`}>
            {a.passed ? "[x]" : "[ ]"}
          </span>
          <div className="space-y-0.5">
            <div className={a.passed ? "text-green-400" : "text-red-400"}>
              {a.description || a.assertionType}
            </div>
            {!a.passed && (
              <div className="text-muted-foreground space-y-0.5">
                {a.expected && (
                  <div>
                    <span className="text-muted-foreground/60">expected: </span>
                    <span className="font-mono">{a.expected}</span>
                  </div>
                )}
                {a.actual && (
                  <div>
                    <span className="text-muted-foreground/60">actual: </span>
                    <span className="font-mono">{a.actual}</span>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

// ── Trial Row ────────────────────────────────────────────────────

function TrialRow({ trial }: { trial: TrialResponse }) {
  const [expanded, setExpanded] = useState(false);

  const allAssertions: AssertionResult[] = trial.grades.flatMap(g => g.assertionResults ?? []);

  return (
    <>
      <tr
        className="border-t border-border hover:bg-muted/30 cursor-pointer"
        onClick={() => setExpanded(e => !e)}
      >
        <td className="px-4 py-2 text-sm">{trial.taskName}</td>
        <td className="px-4 py-2 text-center font-mono text-xs">{trial.trialNumber}</td>
        <td className="px-4 py-2"><OutcomeBadge outcome={trial.outcome} /></td>
        <td className="px-4 py-2 text-center font-mono text-xs">{trial.score.toFixed(2)}</td>
        <td className="px-4 py-2 text-center font-mono text-xs">{allAssertions.length}</td>
        <td className="px-4 py-2 text-center text-xs text-muted-foreground">
          <span className="select-none">{expanded ? "▲" : "▼"}</span>
        </td>
      </tr>
      {expanded && (
        <tr className="border-t border-border bg-muted/10">
          <td colSpan={6} className="py-0">
            <AssertionList assertions={allAssertions} />
          </td>
        </tr>
      )}
    </>
  );
}

// ── Report Section ───────────────────────────────────────────────

function ReportSection({ runId }: { runId: string }) {
  const [showMarkdown, setShowMarkdown] = useState(false);
  const [showJson, setShowJson] = useState(false);
  const [markdown, setMarkdown] = useState<string | null>(null);
  const [json, setJson] = useState<Record<string, unknown> | null>(null);
  const [loadingMd, setLoadingMd] = useState(false);
  const [loadingJson, setLoadingJson] = useState(false);

  const toggleMarkdown = async () => {
    if (showMarkdown) {
      setShowMarkdown(false);
      return;
    }
    if (!markdown) {
      setLoadingMd(true);
      try {
        const md = await evalApi.getReportMarkdown(runId);
        setMarkdown(md);
      } catch {
        setMarkdown("Failed to load report.");
      } finally {
        setLoadingMd(false);
      }
    }
    setShowMarkdown(true);
    setShowJson(false);
  };

  const toggleJson = async () => {
    if (showJson) {
      setShowJson(false);
      return;
    }
    if (!json) {
      setLoadingJson(true);
      try {
        const data = await evalApi.getReportJson(runId);
        setJson(data);
      } catch {
        setJson({ error: "Failed to load report." });
      } finally {
        setLoadingJson(false);
      }
    }
    setShowJson(true);
    setShowMarkdown(false);
  };

  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">Reports</h2>
      <div className="flex items-center gap-2">
        <button
          onClick={toggleMarkdown}
          disabled={loadingMd}
          className="rounded border border-border px-3 py-1.5 text-xs hover:bg-muted disabled:opacity-50"
        >
          {loadingMd ? "Loading..." : showMarkdown ? "Hide Markdown Report" : "Markdown Report"}
        </button>
        <button
          onClick={toggleJson}
          disabled={loadingJson}
          className="rounded border border-border px-3 py-1.5 text-xs hover:bg-muted disabled:opacity-50"
        >
          {loadingJson ? "Loading..." : showJson ? "Hide JSON Report" : "JSON Report"}
        </button>
      </div>

      {showMarkdown && markdown != null && (
        <div className="rounded-lg border border-border bg-muted/10 p-4">
          <pre className="text-xs whitespace-pre-wrap font-mono text-foreground overflow-x-auto">
            {markdown}
          </pre>
        </div>
      )}

      {showJson && json != null && (
        <div className="rounded-lg border border-border bg-muted/10 p-4">
          <pre className="text-xs whitespace-pre-wrap font-mono text-foreground overflow-x-auto">
            {JSON.stringify(json, null, 2)}
          </pre>
        </div>
      )}
    </section>
  );
}

// ── Main Page ────────────────────────────────────────────────────

export default function RunDetailPage() {
  const t = useTranslations("evalDashboard");
  const params = useParams();
  const runId = Array.isArray(params.id) ? params.id[0] : (params.id as string);

  const [run, setRun] = useState<RunResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchRun = useCallback(async () => {
    setLoading(true);
    try {
      const data = await evalApi.getRun(runId);
      setRun(data);
    } catch {
      // API may not be available
    } finally {
      setLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    fetchRun();
  }, [fetchRun]);

  if (loading) {
    return (
      <div className="min-h-screen bg-background p-6 max-w-6xl mx-auto">
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      </div>
    );
  }

  if (!run) {
    return (
      <div className="min-h-screen bg-background p-6 max-w-6xl mx-auto">
        <p className="text-sm text-muted-foreground">Run not found.</p>
        <Link href="/eval-dashboard" className="mt-4 inline-block text-xs text-primary hover:underline">
          Back to Dashboard
        </Link>
      </div>
    );
  }

  const { summary } = run;

  return (
    <div className="min-h-screen bg-background p-6 space-y-8 max-w-6xl mx-auto">
      {/* Breadcrumb */}
      <div className="flex items-center gap-1 text-xs text-muted-foreground">
        <Link href="/eval-dashboard" className="hover:underline">Eval Dashboard</Link>
        <span>/</span>
        <Link href={`/eval-dashboard/suites/${run.suiteId}`} className="hover:underline">{run.suiteName}</Link>
        <span>/</span>
        <span>Run {shortId(run.id)}</span>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-semibold font-mono">Run {shortId(run.id)}</h1>
            <StatusBadge status={run.status} />
          </div>
          <div className="mt-1 text-xs text-muted-foreground space-x-3">
            <span>Suite: <span className="text-foreground">{run.suiteName}</span></span>
            <span>Model: <span className="font-mono text-foreground">{run.model ?? "-"}</span></span>
            <span>k = <span className="font-mono text-foreground">{run.trialsPerTask}</span></span>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label="Pass Rate" value={pct(summary?.overallPassRate)} />
        <StatCard label="Pass@k" value={pct(summary?.passAtK)} />
        <StatCard label="Pass^k" value={pct(summary?.passPowerK)} />
        <StatCard label="Duration" value={summary ? fmt(summary.totalDurationMs) : "-"} />
      </div>

      {summary && (
        <div className="flex items-center gap-6 text-xs text-muted-foreground">
          <span>Total Trials: <span className="text-foreground font-mono">{summary.totalTrials}</span></span>
          <span>Passed: <span className="text-green-400 font-mono">{summary.passedTrials}</span></span>
          <span>Failed: <span className="text-red-400 font-mono">{summary.failedTrials}</span></span>
          <span>Errors: <span className="text-muted-foreground font-mono">{summary.errorTrials}</span></span>
          <span>Avg Score: <span className="text-foreground font-mono">{summary.averageScore.toFixed(2)}</span></span>
        </div>
      )}

      {/* Trials Table */}
      <section className="space-y-3">
        <h2 className="text-base font-semibold">Trials ({run.trials.length})</h2>

        {run.trials.length === 0 ? (
          <div className="rounded-lg border border-border py-8 text-center">
            <p className="text-sm text-muted-foreground">No trials recorded yet.</p>
          </div>
        ) : (
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">Task Name</th>
                  <th className="px-4 py-2 text-center font-medium">Trial #</th>
                  <th className="px-4 py-2 text-left font-medium">Outcome</th>
                  <th className="px-4 py-2 text-center font-medium">Score</th>
                  <th className="px-4 py-2 text-center font-medium">Assertions</th>
                  <th className="px-4 py-2 text-center font-medium"></th>
                </tr>
              </thead>
              <tbody>
                {run.trials.map(trial => (
                  <TrialRow key={trial.id} trial={trial} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Reports */}
      <ReportSection runId={runId} />
    </div>
  );
}

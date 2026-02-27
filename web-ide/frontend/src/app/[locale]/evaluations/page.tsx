"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";

interface EvaluationSummary {
  groupKey: string;
  groupType: string;
  avgIntentScore: number;
  avgCompletionScore: number;
  avgQualityScore: number;
  avgExperienceScore: number;
  count: number;
}

interface EvaluationRecord {
  id: string;
  sessionId: string;
  profile: string;
  mode: string;
  capabilityCategory: string;
  intentScore: number;
  completionScore: number;
  qualityScore: number;
  experienceScore: number;
  toolCallCount: number;
  turnCount: number;
  durationMs: number;
  manualIntentScore: number | null;
  manualCompletionScore: number | null;
  manualQualityScore: number | null;
  manualExperienceScore: number | null;
  userFeedback: string | null;
  createdAt: string;
}

const CATEGORY_LABELS: Record<string, string> = {
  A: "Analysis (ANALYZE)",
  B: "Content Generation (GENERATE)",
  C: "Bug Fix (FIX)",
  D: "Knowledge Mgmt (KNOWLEDGE)",
  E: "Delivery (DELIVER)",
};

function ScoreBar({ value, label }: { value: number; label: string }) {
  const pct = Math.round(value * 100);
  const color =
    value >= 0.8
      ? "bg-green-500"
      : value >= 0.5
        ? "bg-yellow-500"
        : "bg-red-500";
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="w-20 text-muted-foreground">{label}</span>
      <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full ${color}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="w-8 text-right font-mono">{pct}%</span>
    </div>
  );
}

function RadarChart({ scores }: { scores: { intent: number; completion: number; quality: number; experience: number } }) {
  const size = 120;
  const center = size / 2;
  const radius = 45;
  const labels = [
    { key: "intent", label: "Intent", angle: -90 },
    { key: "completion", label: "Complete", angle: 0 },
    { key: "quality", label: "Quality", angle: 90 },
    { key: "experience", label: "UX", angle: 180 },
  ] as const;

  const toXY = (angle: number, r: number) => ({
    x: center + r * Math.cos((angle * Math.PI) / 180),
    y: center + r * Math.sin((angle * Math.PI) / 180),
  });

  const points = labels.map((l) => {
    const value = scores[l.key];
    return toXY(l.angle, radius * value);
  });

  const polygon = points.map((p) => `${p.x},${p.y}`).join(" ");

  return (
    <svg width={size} height={size} className="mx-auto">
      {/* Grid circles */}
      {[0.25, 0.5, 0.75, 1.0].map((r) => (
        <circle
          key={r}
          cx={center}
          cy={center}
          r={radius * r}
          fill="none"
          stroke="currentColor"
          strokeOpacity={0.1}
        />
      ))}
      {/* Axes */}
      {labels.map((l) => {
        const end = toXY(l.angle, radius);
        return (
          <line
            key={l.key}
            x1={center}
            y1={center}
            x2={end.x}
            y2={end.y}
            stroke="currentColor"
            strokeOpacity={0.15}
          />
        );
      })}
      {/* Data polygon */}
      <polygon
        points={polygon}
        fill="hsl(var(--primary))"
        fillOpacity={0.2}
        stroke="hsl(var(--primary))"
        strokeWidth={1.5}
      />
      {/* Labels */}
      {labels.map((l) => {
        const pos = toXY(l.angle, radius + 14);
        return (
          <text
            key={l.key}
            x={pos.x}
            y={pos.y}
            textAnchor="middle"
            dominantBaseline="middle"
            className="fill-muted-foreground text-[8px]"
          >
            {l.label}
          </text>
        );
      })}
    </svg>
  );
}

export default function EvaluationsPage() {
  const t = useTranslations("evaluations");
  const [summaryByProfile, setSummaryByProfile] = useState<EvaluationSummary[]>([]);
  const [summaryByCategory, setSummaryByCategory] = useState<EvaluationSummary[]>([]);
  const [recentEvaluations, setRecentEvaluations] = useState<EvaluationRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [days, setDays] = useState(30);
  const [expandedRow, setExpandedRow] = useState<string | null>(null);
  const [profileFilter, setProfileFilter] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [summaryRes, trendRes] = await Promise.all([
        fetch(`/api/evaluations/summary?days=${days}`),
        fetch(`/api/evaluations/trend?days=${days}`),
      ]);
      if (summaryRes.ok) {
        const data = await summaryRes.json();
        setSummaryByProfile(data.byProfile ?? []);
        setSummaryByCategory(data.byCategory ?? []);
      }
      if (trendRes.ok) {
        setRecentEvaluations(await trendRes.json());
      }
    } catch (err) {
      console.error("Failed to fetch evaluations:", err);
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const overallScores = {
    intent: summaryByProfile.length > 0
      ? summaryByProfile.reduce((s, p) => s + p.avgIntentScore * p.count, 0) / summaryByProfile.reduce((s, p) => s + p.count, 0)
      : 0,
    completion: summaryByProfile.length > 0
      ? summaryByProfile.reduce((s, p) => s + p.avgCompletionScore * p.count, 0) / summaryByProfile.reduce((s, p) => s + p.count, 0)
      : 0,
    quality: summaryByProfile.length > 0
      ? summaryByProfile.reduce((s, p) => s + p.avgQualityScore * p.count, 0) / summaryByProfile.reduce((s, p) => s + p.count, 0)
      : 0,
    experience: summaryByProfile.length > 0
      ? summaryByProfile.reduce((s, p) => s + p.avgExperienceScore * p.count, 0) / summaryByProfile.reduce((s, p) => s + p.count, 0)
      : 0,
  };

  const filteredEvaluations = profileFilter
    ? recentEvaluations.filter((e) => e.profile === profileFilter)
    : recentEvaluations;

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <span className="text-muted-foreground text-sm">{t("loadingEvaluations")}</span>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background p-6 space-y-6 max-w-6xl mx-auto">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <div className="flex items-center gap-2">
          <label className="text-xs text-muted-foreground">{t("period")}</label>
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            className="rounded border border-input bg-background px-2 py-1 text-xs"
          >
            <option value={7}>7 days</option>
            <option value={30}>30 days</option>
            <option value={90}>90 days</option>
          </select>
        </div>
      </div>

      {/* Overall Radar + Summary */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="rounded-lg border border-border p-4">
          <h2 className="text-sm font-medium mb-2">{t("overall4DScore")}</h2>
          <RadarChart scores={overallScores} />
          <div className="mt-2 space-y-1">
            <ScoreBar value={overallScores.intent} label={t("intent")} />
            <ScoreBar value={overallScores.completion} label={t("complete")} />
            <ScoreBar value={overallScores.quality} label={t("quality")} />
            <ScoreBar value={overallScores.experience} label={t("ux")} />
          </div>
        </div>

        {/* By Profile */}
        <div className="rounded-lg border border-border p-4">
          <h2 className="text-sm font-medium mb-3">{t("byProfile")}</h2>
          {summaryByProfile.length === 0 ? (
            <p className="text-xs text-muted-foreground">{t("noData")}</p>
          ) : (
            <div className="space-y-3">
              {summaryByProfile.map((s) => (
                <div
                  key={s.groupKey}
                  className={`space-y-1 cursor-pointer rounded-md p-1.5 -mx-1.5 transition-colors ${
                    profileFilter === s.groupKey ? "bg-primary/10 ring-1 ring-primary/30" : "hover:bg-muted/50"
                  }`}
                  onClick={() => setProfileFilter(profileFilter === s.groupKey ? null : s.groupKey)}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-medium">{s.groupKey.replace("-profile", "")}</span>
                    <span className="text-muted-foreground">{s.count} {t("interactions")}</span>
                  </div>
                  <ScoreBar value={s.avgIntentScore} label={t("intent")} />
                  <ScoreBar value={s.avgCompletionScore} label={t("complete")} />
                  <ScoreBar value={s.avgQualityScore} label={t("quality")} />
                  <ScoreBar value={s.avgExperienceScore} label={t("ux")} />
                </div>
              ))}
            </div>
          )}
        </div>

        {/* By Capability Category */}
        <div className="rounded-lg border border-border p-4">
          <h2 className="text-sm font-medium mb-3">{t("byCapability")}</h2>
          {summaryByCategory.length === 0 ? (
            <p className="text-xs text-muted-foreground">{t("noData")}</p>
          ) : (
            <div className="space-y-3">
              {summaryByCategory.map((s) => (
                <div key={s.groupKey} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-medium">{CATEGORY_LABELS[s.groupKey] ?? s.groupKey}</span>
                    <span className="text-muted-foreground">{s.count}</span>
                  </div>
                  <ScoreBar value={s.avgIntentScore} label={t("intent")} />
                  <ScoreBar value={s.avgCompletionScore} label={t("complete")} />
                  <ScoreBar value={s.avgQualityScore} label={t("quality")} />
                  <ScoreBar value={s.avgExperienceScore} label={t("ux")} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Recent Evaluations Table */}
      <div className="rounded-lg border border-border">
        <div className="px-4 py-3 border-b border-border flex items-center justify-between">
          <h2 className="text-sm font-medium">
            Recent Interactions ({filteredEvaluations.length})
            {profileFilter && (
              <button
                onClick={() => setProfileFilter(null)}
                className="ml-2 text-xs text-primary hover:underline"
              >
                {t("clearFilter")}
              </button>
            )}
          </h2>
        </div>
        <div className="overflow-auto max-h-96">
          <table className="w-full text-xs">
            <thead className="bg-muted/50 sticky top-0">
              <tr>
                <th className="px-3 py-2 text-left font-medium">{t("time")}</th>
                <th className="px-3 py-2 text-left font-medium">{t("profile")}</th>
                <th className="px-3 py-2 text-left font-medium">{t("category")}</th>
                <th className="px-3 py-2 text-center font-medium">{t("intent")}</th>
                <th className="px-3 py-2 text-center font-medium">{t("complete")}</th>
                <th className="px-3 py-2 text-center font-medium">{t("quality")}</th>
                <th className="px-3 py-2 text-center font-medium">{t("ux")}</th>
                <th className="px-3 py-2 text-center font-medium">{t("tools")}</th>
                <th className="px-3 py-2 text-center font-medium">{t("duration")}</th>
              </tr>
            </thead>
            <tbody>
              {filteredEvaluations.map((e) => (
                <React.Fragment key={e.id}>
                  <tr
                    className="border-t border-border hover:bg-muted/30 cursor-pointer"
                    onClick={() => setExpandedRow(expandedRow === e.id ? null : e.id)}
                  >
                    <td className="px-3 py-2 text-muted-foreground">
                      {new Date(e.createdAt).toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
                    </td>
                    <td className="px-3 py-2">{e.profile.replace("-profile", "")}</td>
                    <td className="px-3 py-2 text-muted-foreground">{CATEGORY_LABELS[e.capabilityCategory] ?? (e.capabilityCategory || "-")}</td>
                    <td className="px-3 py-2 text-center font-mono">{Math.round(e.intentScore * 100)}%</td>
                    <td className="px-3 py-2 text-center font-mono">{Math.round(e.completionScore * 100)}%</td>
                    <td className="px-3 py-2 text-center font-mono">{Math.round(e.qualityScore * 100)}%</td>
                    <td className="px-3 py-2 text-center font-mono">{Math.round(e.experienceScore * 100)}%</td>
                    <td className="px-3 py-2 text-center">{e.toolCallCount}</td>
                    <td className="px-3 py-2 text-center text-muted-foreground">{(e.durationMs / 1000).toFixed(1)}s</td>
                  </tr>
                  {expandedRow === e.id && (
                    <tr className="border-t border-border bg-muted/20">
                      <td colSpan={9} className="px-4 py-3">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                          <div>
                            <span className="text-muted-foreground">{t("session")}</span>{" "}
                            <span className="font-mono">{e.sessionId.slice(0, 8)}...</span>
                          </div>
                          <div>
                            <span className="text-muted-foreground">{t("mode")}</span> {e.mode || "-"}
                          </div>
                          <div>
                            <span className="text-muted-foreground">{t("turns")}</span> {e.turnCount}
                          </div>
                          <div>
                            <span className="text-muted-foreground">{t("tools")}</span> {e.toolCallCount} calls
                          </div>
                          {e.userFeedback && (
                            <div className="col-span-full">
                              <span className="text-muted-foreground">{t("feedback")}</span> {e.userFeedback}
                            </div>
                          )}
                          {(e.manualIntentScore !== null) && (
                            <div className="col-span-full text-muted-foreground">
                              {t("manualScores", {
                                intent: e.manualIntentScore ?? 0,
                                complete: e.manualCompletionScore ?? 0,
                                quality: e.manualQualityScore ?? 0,
                                ux: e.manualExperienceScore ?? 0,
                              })}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </React.Fragment>
              ))}
              {filteredEvaluations.length === 0 && (
                <tr>
                  <td colSpan={9} className="px-3 py-8 text-center text-muted-foreground">
                    {profileFilter ? t("noRecords") : t("noEvalRecords")}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

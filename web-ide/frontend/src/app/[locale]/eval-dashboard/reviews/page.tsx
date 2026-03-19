"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
import {
  evalApi,
  type ReviewQueueItem,
  type CalibrationMetrics,
} from "@/lib/eval-api";

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    PENDING: "bg-yellow-500/15 text-yellow-400",
    IN_PROGRESS: "bg-blue-500/15 text-blue-400",
    COMPLETED: "bg-green-500/15 text-green-400",
    SKIPPED: "bg-muted text-muted-foreground",
  };
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${colors[status] ?? "bg-muted text-muted-foreground"}`}>
      {status}
    </span>
  );
}

function MetricCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-2xl font-semibold font-mono">{value}</div>
      {hint && <div className="mt-1 text-[10px] text-muted-foreground">{hint}</div>}
    </div>
  );
}

export default function ReviewsPage() {
  const t = useTranslations("evalDashboard");
  const [queue, setQueue] = useState<ReviewQueueItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [calibration, setCalibration] = useState<CalibrationMetrics | null>(null);
  const [loading, setLoading] = useState(true);
  const [reviewingId, setReviewingId] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [queueRes, calRes] = await Promise.all([
        evalApi.getReviewQueue({ size: 50 }),
        evalApi.getCalibration(),
      ]);
      setQueue(queueRes.content);
      setTotalElements(queueRes.totalElements);
      setCalibration(calRes);
    } catch {
      // API may not be available
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const kappaLabel = (k: number): string => {
    if (k >= 0.8) return t("kappaAlmostPerfect");
    if (k >= 0.6) return t("kappaSubstantial");
    if (k >= 0.4) return t("kappaModerate");
    if (k >= 0.2) return t("kappaFair");
    return t("kappaSlight");
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background p-6 max-w-6xl mx-auto">
        <p className="text-sm text-muted-foreground">{t("loading")}</p>
      </div>
    );
  }

  const pendingItems = queue.filter(q => q.status === "PENDING");
  const completedItems = queue.filter(q => q.status === "COMPLETED");

  return (
    <div className="min-h-screen bg-background p-6 space-y-8 max-w-6xl mx-auto">
      {/* Breadcrumb */}
      <div className="flex items-center gap-1 text-xs text-muted-foreground">
        <Link href="/eval-dashboard" className="hover:underline">{t("title")}</Link>
        <span>/</span>
        <span>{t("humanReviews")}</span>
      </div>

      <h1 className="text-xl font-semibold">{t("humanReviews")}</h1>
      <p className="text-xs text-muted-foreground">
        {t("humanReviewsDesc")}
      </p>

      {/* Calibration Metrics */}
      {calibration && (
        <section className="space-y-3">
          <h2 className="text-base font-semibold">{t("calibrationMetrics")}</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <MetricCard
              label={t("totalReviews")}
              value={String(calibration.totalReviews)}
            />
            <MetricCard
              label={t("agreementRate")}
              value={calibration.totalReviews > 0 ? `${(calibration.agreementRate * 100).toFixed(0)}%` : "-"}
              hint={t("agreementRateHint")}
            />
            <MetricCard
              label={t("cohensKappa")}
              value={calibration.totalReviews > 0 ? calibration.cohensKappa.toFixed(2) : "-"}
              hint={calibration.totalReviews > 0 ? kappaLabel(calibration.cohensKappa) : undefined}
            />
            <MetricCard
              label={t("scoreDelta")}
              value={calibration.totalReviews > 0 ? `${calibration.scoreDelta >= 0 ? "+" : ""}${calibration.scoreDelta.toFixed(2)}` : "-"}
              hint={t("scoreDeltaHint")}
            />
          </div>
          {calibration.totalReviews > 0 && (
            <div className="flex gap-6 text-xs text-muted-foreground">
              <span>{t("autoAvg")}<span className="font-mono text-foreground">{calibration.averageAutoScore.toFixed(2)}</span></span>
              <span>{t("humanAvg")}<span className="font-mono text-foreground">{calibration.averageHumanScore.toFixed(2)}</span></span>
            </div>
          )}
        </section>
      )}

      {/* Pending Reviews Queue */}
      <section className="space-y-3">
        <h2 className="text-base font-semibold">{t("pendingReviewsSection", { count: pendingItems.length })}</h2>

        {pendingItems.length === 0 ? (
          <div className="rounded-lg border border-border py-8 text-center">
            <p className="text-sm text-muted-foreground">{t("noPendingReviews")}</p>
          </div>
        ) : (
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">{t("colTask")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("colAutoScore")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("colConfidence")}</th>
                  <th className="px-4 py-2 text-left font-medium">{t("colTriggerReason")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("colAction")}</th>
                </tr>
              </thead>
              <tbody>
                {pendingItems.map(item => (
                  <React.Fragment key={item.gradeId}>
                    <tr className="border-t border-border hover:bg-muted/30">
                      <td className="px-4 py-2">{item.taskName}</td>
                      <td className="px-4 py-2 text-center font-mono text-xs">{item.autoScore.toFixed(2)}</td>
                      <td className="px-4 py-2 text-center font-mono text-xs">{item.confidence.toFixed(2)}</td>
                      <td className="px-4 py-2 text-xs text-muted-foreground">
                        {item.reviewReasons.join("; ")}
                      </td>
                      <td className="px-4 py-2 text-center">
                        <button
                          onClick={() => setReviewingId(reviewingId === item.gradeId ? null : item.gradeId)}
                          className="rounded bg-primary px-2 py-1 text-[10px] font-medium text-primary-foreground hover:bg-primary/90"
                        >
                          {reviewingId === item.gradeId ? t("cancelReview") : t("review")}
                        </button>
                      </td>
                    </tr>
                    {reviewingId === item.gradeId && (
                      <tr className="border-t border-border bg-muted/10">
                        <td colSpan={5} className="px-4 py-3">
                          <ReviewForm
                            gradeId={item.gradeId}
                            autoScore={item.autoScore}
                            onSubmitted={() => {
                              setReviewingId(null);
                              fetchData();
                            }}
                          />
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Completed Reviews */}
      {completedItems.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-base font-semibold">{t("completedReviews", { count: completedItems.length })}</h2>
          <div className="rounded-lg border border-border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">{t("colTask")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("colAutoScore")}</th>
                  <th className="px-4 py-2 text-center font-medium">{t("colStatus")}</th>
                </tr>
              </thead>
              <tbody>
                {completedItems.map(item => (
                  <tr key={item.gradeId} className="border-t border-border">
                    <td className="px-4 py-2">{item.taskName}</td>
                    <td className="px-4 py-2 text-center font-mono text-xs">{item.autoScore.toFixed(2)}</td>
                    <td className="px-4 py-2 text-center"><StatusBadge status={item.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

// ── Review Form ──────────────────────────────────────────────────

function ReviewForm({
  gradeId,
  autoScore,
  onSubmitted,
}: {
  gradeId: string;
  autoScore: number;
  onSubmitted: () => void;
}) {
  const t = useTranslations("evalDashboard");
  const [score, setScore] = useState(String(autoScore.toFixed(2)));
  const [passed, setPassed] = useState(autoScore >= 0.5);
  const [explanation, setExplanation] = useState("");
  const [reviewer, setReviewer] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reviewer.trim() || !explanation.trim()) return;
    setSubmitting(true);
    try {
      await evalApi.submitReview(gradeId, {
        score: parseFloat(score),
        passed,
        explanation: explanation.trim(),
        reviewer: reviewer.trim(),
      });
      onSubmitted();
    } catch {
      // TODO: error handling
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-3 max-w-lg">
      <div className="text-xs text-muted-foreground">
        {t("autoScoreLabel", { score: autoScore.toFixed(2) })}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-muted-foreground mb-1">{t("yourScore")}</label>
          <input
            type="number"
            step="0.01"
            min="0"
            max="1"
            value={score}
            onChange={e => {
              setScore(e.target.value);
              setPassed(parseFloat(e.target.value) >= 0.5);
            }}
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm font-mono"
            required
          />
        </div>
        <div>
          <label className="block text-xs text-muted-foreground mb-1">{t("passedQuestion")}</label>
          <select
            value={passed ? "yes" : "no"}
            onChange={e => setPassed(e.target.value === "yes")}
            className="w-full rounded border border-input bg-background px-2 py-1.5 text-sm"
          >
            <option value="yes">Yes — PASS</option>
            <option value="no">No — FAIL</option>
          </select>
        </div>
      </div>

      <div>
        <label className="block text-xs text-muted-foreground mb-1">{t("explanationLabel")}</label>
        <textarea
          value={explanation}
          onChange={e => setExplanation(e.target.value)}
          rows={2}
          placeholder={t("explanationPlaceholder")}
          className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm resize-y"
          required
        />
      </div>

      <div>
        <label className="block text-xs text-muted-foreground mb-1">{t("reviewerLabel")}</label>
        <input
          value={reviewer}
          onChange={e => setReviewer(e.target.value)}
          placeholder={t("reviewerPlaceholder")}
          className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm"
          required
        />
      </div>

      <button
        type="submit"
        disabled={submitting || !reviewer.trim() || !explanation.trim()}
        className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
      >
        {submitting ? t("submittingReview") : t("submitReview")}
      </button>
    </form>
  );
}

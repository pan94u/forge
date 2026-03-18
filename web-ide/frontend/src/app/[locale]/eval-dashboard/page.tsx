"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Link } from "@/navigation";
import {
  evalApi,
  type SuiteResponse,
  type PageResponse,
  type Platform,
  type AgentType,
  type CreateSuiteRequest,
} from "@/lib/eval-api";

const PLATFORMS: Platform[] = ["FORGE", "SYNAPSE", "APPLICATION"];
const AGENT_TYPES: AgentType[] = ["CODING", "CONVERSATIONAL", "RESEARCH", "COMPUTER_USE"];

function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-2xl font-semibold">{value}</div>
    </div>
  );
}

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

export default function EvalDashboardPage() {
  const t = useTranslations("evalDashboard");
  const [suites, setSuites] = useState<SuiteResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [platformFilter, setPlatformFilter] = useState("");
  const [agentTypeFilter, setAgentTypeFilter] = useState("");
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [pendingReviews, setPendingReviews] = useState(0);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (platformFilter) params.platform = platformFilter;
      if (agentTypeFilter) params.agentType = agentTypeFilter;
      const result = await evalApi.listSuites(params);
      setSuites(result.content);
      setTotalElements(result.totalElements);
    } catch {
      // API may not be available in dev mode
    } finally {
      setLoading(false);
    }
  }, [platformFilter, agentTypeFilter]);

  useEffect(() => {
    fetchData();
    evalApi.getReviewQueue({ size: 1 }).then(r => setPendingReviews(r.totalElements)).catch(() => {});
  }, [fetchData]);

  const totalTasks = suites.reduce((s, suite) => s + suite.taskCount, 0);

  return (
    <div className="min-h-screen bg-background p-6 space-y-6 max-w-6xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">{t("title")}</h1>
        <button
          onClick={() => setShowCreateForm(true)}
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90"
        >
          + {t("createSuite")}
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard label={t("suites")} value={totalElements} />
        <StatCard label={t("tasks")} value={totalTasks} />
        <StatCard label={t("runs")} value="-" />
        <Link href="/eval-dashboard/reviews" className="block">
          <div className="rounded-lg border border-border bg-card p-4 hover:bg-muted/30 transition-colors">
            <div className="text-xs text-muted-foreground">{t("pendingReviews")}</div>
            <div className="mt-1 text-2xl font-semibold">{pendingReviews}</div>
            {pendingReviews > 0 && <div className="mt-1 text-[10px] text-yellow-400">Click to review →</div>}
          </div>
        </Link>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3">
        <select
          value={platformFilter}
          onChange={e => setPlatformFilter(e.target.value)}
          className="rounded border border-input bg-background px-2 py-1 text-xs"
        >
          <option value="">{t("allPlatforms")}</option>
          {PLATFORMS.map(p => <option key={p} value={p}>{p}</option>)}
        </select>
        <select
          value={agentTypeFilter}
          onChange={e => setAgentTypeFilter(e.target.value)}
          className="rounded border border-input bg-background px-2 py-1 text-xs"
        >
          <option value="">{t("allAgentTypes")}</option>
          {AGENT_TYPES.map(a => <option key={a} value={a}>{a}</option>)}
        </select>
      </div>

      {/* Suite Table */}
      {loading ? (
        <div className="py-12 text-center text-sm text-muted-foreground">{t("loading")}</div>
      ) : suites.length === 0 ? (
        <div className="py-12 text-center">
          <p className="text-sm text-muted-foreground">{t("noSuites")}</p>
          <p className="mt-1 text-xs text-muted-foreground">{t("noSuitesHint")}</p>
        </div>
      ) : (
        <div className="rounded-lg border border-border overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/50">
              <tr>
                <th className="px-4 py-2 text-left font-medium">{t("name")}</th>
                <th className="px-4 py-2 text-left font-medium">{t("platform")}</th>
                <th className="px-4 py-2 text-left font-medium">{t("agentType")}</th>
                <th className="px-4 py-2 text-left font-medium">{t("lifecycle")}</th>
                <th className="px-4 py-2 text-center font-medium">{t("taskCount")}</th>
                <th className="px-4 py-2 text-left font-medium">{t("tags")}</th>
              </tr>
            </thead>
            <tbody>
              {suites.map(suite => (
                <tr key={suite.id} className="border-t border-border hover:bg-muted/30">
                  <td className="px-4 py-2">
                    <Link
                      href={`/eval-dashboard/suites/${suite.id}`}
                      className="font-medium text-primary hover:underline"
                    >
                      {suite.name}
                    </Link>
                    {suite.description && (
                      <p className="mt-0.5 text-xs text-muted-foreground truncate max-w-xs">{suite.description}</p>
                    )}
                  </td>
                  <td className="px-4 py-2"><PlatformBadge platform={suite.platform} /></td>
                  <td className="px-4 py-2 text-xs">{suite.agentType}</td>
                  <td className="px-4 py-2"><LifecycleBadge lifecycle={suite.lifecycle} /></td>
                  <td className="px-4 py-2 text-center font-mono text-xs">{suite.taskCount}</td>
                  <td className="px-4 py-2">
                    <div className="flex gap-1 flex-wrap">
                      {suite.tags.map(tag => (
                        <span key={tag} className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">{tag}</span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Suite Modal */}
      {showCreateForm && (
        <CreateSuiteModal
          onClose={() => setShowCreateForm(false)}
          onCreated={() => { setShowCreateForm(false); fetchData(); }}
        />
      )}
    </div>
  );
}

function CreateSuiteModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const t = useTranslations("evalDashboard");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [platform, setPlatform] = useState<Platform>("FORGE");
  const [agentType, setAgentType] = useState<AgentType>("CODING");
  const [tags, setTags] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setSubmitting(true);
    try {
      const req: CreateSuiteRequest = {
        name: name.trim(),
        description: description.trim() || undefined,
        platform,
        agentType,
        tags: tags.trim() ? tags.split(",").map(t => t.trim()).filter(Boolean) : undefined,
      };
      await evalApi.createSuite(req);
      onCreated();
    } catch {
      // TODO: show error toast
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <form
        onClick={e => e.stopPropagation()}
        onSubmit={handleSubmit}
        className="w-full max-w-md rounded-lg border border-border bg-card p-6 space-y-4 shadow-lg"
      >
        <h2 className="text-lg font-semibold">{t("createSuite")}</h2>

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
          <label className="block text-xs text-muted-foreground mb-1">{t("description")}</label>
          <input
            value={description}
            onChange={e => setDescription(e.target.value)}
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs text-muted-foreground mb-1">{t("platform")}</label>
            <select
              value={platform}
              onChange={e => setPlatform(e.target.value as Platform)}
              className="w-full rounded border border-input bg-background px-2 py-1.5 text-sm"
            >
              {PLATFORMS.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-xs text-muted-foreground mb-1">{t("agentType")}</label>
            <select
              value={agentType}
              onChange={e => setAgentType(e.target.value as AgentType)}
              className="w-full rounded border border-input bg-background px-2 py-1.5 text-sm"
            >
              {AGENT_TYPES.map(a => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-xs text-muted-foreground mb-1">{t("tags")} (comma separated)</label>
          <input
            value={tags}
            onChange={e => setTags(e.target.value)}
            placeholder="kotlin, code-gen"
            className="w-full rounded border border-input bg-background px-3 py-1.5 text-sm"
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="rounded px-3 py-1.5 text-xs text-muted-foreground hover:bg-muted">
            {t("cancel")}
          </button>
          <button
            type="submit"
            disabled={submitting || !name.trim()}
            className="rounded bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {submitting ? t("creating") : t("create")}
          </button>
        </div>
      </form>
    </div>
  );
}

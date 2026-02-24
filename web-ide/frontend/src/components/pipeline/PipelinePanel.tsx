"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
  RefreshCw,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Play,
  TrendingUp,
  Lightbulb,
  Database,
} from "lucide-react";

interface GapStats {
  total: number;
  unresolved: number;
  resolved: number;
  frequentTopics: Array<{ topic: string; count: number }>;
}

interface SkillQualitySummary {
  skillName: string;
  total: number;
  passed: number;
  failed: number;
  passRate: number;
  avgExecutionTimeMs: number;
}

interface LearnedPattern {
  id: string;
  skillName: string;
  patternType: string;
  description: string;
  confidence: number;
  sampleSize: number;
  suggestion: string;
  status: string;
}

interface SkillSuggestion {
  skillName: string;
  type: string;
  passRate: number;
  sampleSize: number;
  suggestion: string;
}

interface PipelineStatus {
  knowledgeGaps: GapStats;
  skillQuality: { recentRecords: number; passRate: number };
  learnedPatterns: { pending: number; patterns: LearnedPattern[] };
}

export function PipelinePanel() {
  const [status, setStatus] = useState<PipelineStatus | null>(null);
  const [skillQuality, setSkillQuality] = useState<SkillQualitySummary[]>([]);
  const [suggestions, setSuggestions] = useState<SkillSuggestion[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [statusRes, qualityRes, suggestionsRes] = await Promise.all([
        fetch("/api/knowledge-pipeline/status"),
        fetch("/api/knowledge-pipeline/skill-quality?days=30"),
        fetch("/api/knowledge-pipeline/skill-suggestions?days=30"),
      ]);

      if (statusRes.ok) setStatus(await statusRes.json());
      if (qualityRes.ok) {
        const data = await qualityRes.json();
        setSkillQuality(data.summary ?? []);
      }
      if (suggestionsRes.ok) setSuggestions(await suggestionsRes.json());
    } catch (err) {
      setError("Failed to load pipeline data");
      console.error("Pipeline fetch error:", err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRunPipeline = async () => {
    setRunning(true);
    try {
      const res = await fetch("/api/knowledge-pipeline/run", { method: "POST" });
      if (res.ok) {
        await fetchData();
      }
    } catch (err) {
      console.error("Pipeline run error:", err);
    } finally {
      setRunning(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full text-muted-foreground">
        <RefreshCw className="h-4 w-4 animate-spin mr-2" />
        <span className="text-sm">加载中...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-muted-foreground">
        <p className="text-sm">{error}</p>
        <button onClick={fetchData} className="mt-2 text-xs text-primary hover:underline">
          重试
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4 p-4 overflow-auto h-full">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold">知识管道</h3>
        <div className="flex items-center gap-1">
          <button
            onClick={handleRunPipeline}
            disabled={running}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs bg-primary text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            title="手动触发管道"
          >
            {running ? <RefreshCw className="h-3 w-3 animate-spin" /> : <Play className="h-3 w-3" />}
            {running ? "运行中..." : "运行"}
          </button>
          <button onClick={fetchData} className="rounded p-1 text-muted-foreground hover:bg-accent" title="刷新">
            <RefreshCw className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-3 gap-2">
        <SummaryCard
          icon={<AlertTriangle className="h-4 w-4" />}
          label="知识缺口"
          value={status?.knowledgeGaps?.unresolved ?? 0}
          color={status?.knowledgeGaps?.unresolved ? "text-yellow-400" : "text-green-400"}
        />
        <SummaryCard
          icon={<Database className="h-4 w-4" />}
          label="质量记录"
          value={status?.skillQuality?.recentRecords ?? 0}
        />
        <SummaryCard
          icon={<Lightbulb className="h-4 w-4" />}
          label="待确认模式"
          value={status?.learnedPatterns?.pending ?? 0}
          color={status?.learnedPatterns?.pending ? "text-blue-400" : undefined}
        />
      </div>

      {/* Skill Quality */}
      {skillQuality.length > 0 && (
        <div className="space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">Skill 质量（30天）</span>
          <div className="space-y-1.5">
            {skillQuality.map((sq) => (
              <div key={sq.skillName} className="rounded-md border border-border p-2 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono font-medium">{sq.skillName}</span>
                  <span className={`text-xs font-medium ${sq.passRate >= 0.8 ? "text-green-400" : sq.passRate >= 0.5 ? "text-yellow-400" : "text-red-400"}`}>
                    {(sq.passRate * 100).toFixed(0)}%
                  </span>
                </div>
                <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                  <div
                    className={`h-full rounded-full ${sq.passRate >= 0.8 ? "bg-green-500" : sq.passRate >= 0.5 ? "bg-yellow-500" : "bg-red-500"}`}
                    style={{ width: `${sq.passRate * 100}%` }}
                  />
                </div>
                <div className="flex justify-between text-[10px] text-muted-foreground">
                  <span>
                    <CheckCircle className="inline h-3 w-3 text-green-400 mr-0.5" />
                    {sq.passed}
                    <XCircle className="inline h-3 w-3 text-red-400 ml-1.5 mr-0.5" />
                    {sq.failed}
                  </span>
                  <span>{(sq.avgExecutionTimeMs / 1000).toFixed(1)}s avg</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Knowledge Gaps */}
      {status?.knowledgeGaps && status.knowledgeGaps.total > 0 && (
        <div className="space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">知识缺口统计</span>
          <div className="rounded-md border border-border p-2 space-y-1">
            <div className="flex justify-between text-xs">
              <span>总计</span>
              <span className="font-medium">{status.knowledgeGaps.total}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span>未解决</span>
              <span className="font-medium text-yellow-400">{status.knowledgeGaps.unresolved}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span>已解决</span>
              <span className="font-medium text-green-400">{status.knowledgeGaps.resolved}</span>
            </div>
            {status.knowledgeGaps.frequentTopics?.length > 0 && (
              <div className="mt-1 pt-1 border-t border-border">
                <span className="text-[10px] text-muted-foreground">高频主题:</span>
                <div className="flex flex-wrap gap-1 mt-0.5">
                  {status.knowledgeGaps.frequentTopics.map((t, i) => (
                    <span key={i} className="rounded bg-muted px-1.5 py-0.5 text-[10px]">
                      {t.topic} ({t.count})
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Self-Learned Patterns */}
      {status?.learnedPatterns?.patterns && status.learnedPatterns.patterns.length > 0 && (
        <div className="space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">自学习模式（待确认）</span>
          <div className="space-y-1.5">
            {status.learnedPatterns.patterns.map((p) => (
              <div key={p.id} className="rounded-md border border-blue-500/20 bg-blue-500/5 p-2 space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono font-medium">{p.skillName}</span>
                  <span className="text-[10px] text-muted-foreground">
                    {(p.confidence * 100).toFixed(0)}% 置信度
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">{p.description}</p>
                {p.suggestion && (
                  <div className="flex items-start gap-1 mt-1 pt-1 border-t border-blue-500/10">
                    <TrendingUp className="h-3 w-3 text-blue-400 mt-0.5 flex-shrink-0" />
                    <span className="text-[10px] text-blue-400">{p.suggestion}</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Skill Update Suggestions */}
      {suggestions.length > 0 && (
        <div className="space-y-1.5">
          <span className="text-xs font-medium text-muted-foreground">Skill 改进建议</span>
          <div className="space-y-1.5">
            {suggestions.map((s, i) => (
              <div key={i} className="rounded-md border border-border p-2 space-y-1">
                <div className="flex items-center gap-1.5">
                  <span className={`rounded px-1 py-0.5 text-[10px] ${
                    s.type === "QUALITY_IMPROVEMENT" ? "bg-red-500/20 text-red-400" : "bg-yellow-500/20 text-yellow-400"
                  }`}>
                    {s.type === "QUALITY_IMPROVEMENT" ? "质量" : "性能"}
                  </span>
                  <span className="text-xs font-mono font-medium">{s.skillName}</span>
                </div>
                <p className="text-[11px] text-muted-foreground leading-relaxed">{s.suggestion}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Empty state */}
      {!status?.knowledgeGaps?.total && skillQuality.length === 0 && suggestions.length === 0 && (
        <div className="text-center text-muted-foreground text-sm py-8">
          暂无管道数据。运行 AI 对话并触发管道后数据将出现在这里。
        </div>
      )}
    </div>
  );
}

function SummaryCard({
  icon,
  label,
  value,
  color,
}: {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  color?: string;
}) {
  return (
    <div className="rounded-md border border-border bg-card p-2 space-y-1">
      <div className="flex items-center gap-1 text-muted-foreground">
        {icon}
        <span className="text-[10px]">{label}</span>
      </div>
      <div className={`text-lg font-semibold ${color ?? ""}`}>{value}</div>
    </div>
  );
}

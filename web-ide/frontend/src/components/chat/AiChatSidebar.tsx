"use client";

import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  Send,
  Paperclip,
  RotateCcw,
  StopCircle,
  Eye,
  Compass,
  Brain,
  Zap,
  CheckCircle2,
  Settings,
  ChevronDown,
  ChevronRight,
  Activity,
  GitCommit,
} from "lucide-react";
import { ChatMessage, type Message, type ContentSegment } from "@/components/chat/ChatMessage";
import {
  ContextPicker,
  type ContextItem,
} from "@/components/chat/ContextPicker";
import { ModelSelector } from "@/components/chat/ModelSelector";
import { ModelSettingsDialog } from "@/components/chat/ModelSettingsDialog";
import {
  claudeClient,
  type StreamEvent,
  type OodaPhase,
} from "@/lib/claude-client";
import { HitlApprovalPanel } from "@/components/chat/HitlApprovalPanel";
import { QualityPanel } from "@/components/dashboard/QualityPanel";
import { MemoryPanel } from "@/components/memory/MemoryPanel";
import { WorkspaceSkillPanel } from "@/components/skills/WorkspaceSkillPanel";
import { PipelinePanel } from "@/components/pipeline/PipelinePanel";

const OODA_PHASES: {
  key: OodaPhase;
  label: string;
  icon: React.ElementType;
}[] = [
  { key: "observe", label: "Observe", icon: Eye },
  { key: "orient", label: "Orient", icon: Compass },
  { key: "decide", label: "Decide", icon: Brain },
  { key: "act", label: "Act", icon: Zap },
  { key: "complete", label: "Done", icon: CheckCircle2 },
];

interface SubStep {
  message: string;
  timestamp: string;
}

interface BaselineResult {
  status: string;
  attempt?: number;
  summary?: string;
  baselines?: string[];
}

interface AiChatSidebarProps {
  workspaceId: string;
  activeFile: string | null;
  fileContent: string;
}

export function AiChatSidebar({
  workspaceId,
  activeFile,
  fileContent,
}: AiChatSidebarProps) {
  const [activeTab, setActiveTab] = useState<"chat" | "quality" | "skills" | "memory" | "pipeline">("chat");
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [selectedModel, setSelectedModel] = useState(() => {
    if (typeof window === "undefined") return "claude-sonnet-4-6";
    return localStorage.getItem("forge_selected_model") ?? "claude-sonnet-4-6";
  });
  const [showContextPicker, setShowContextPicker] = useState(false);
  const [showModelSettings, setShowModelSettings] = useState(false);
  const [selectedContexts, setSelectedContexts] = useState<ContextItem[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    return localStorage.getItem(`forge_chat_session_${workspaceId}`);
  });
  const [thinkingText, setThinkingText] = useState<string>("");
  const [activeProfile, setActiveProfile] = useState<{
    name: string;
    skills: string[];
    reason: string;
    confidence: number;
  } | null>(null);
  const [oodaPhase, setOodaPhase] = useState<OodaPhase | null>(null);
  const [activityLog, setActivityLog] = useState<SubStep[]>([]);
  const [showActivityLog, setShowActivityLog] = useState(false);
  const [currentTurn, setCurrentTurn] = useState<{ turn: number; maxTurns: number } | null>(null);
  const [contextUsage, setContextUsage] = useState<{ tokensUsed: number; tokenBudget: number; compressionPhase: number; turn: number } | null>(null);
  const [oodaDetail, setOodaDetail] = useState<string>("");
  const [baselineResult, setBaselineResult] = useState<BaselineResult | null>(null);
  const [gitConfirm, setGitConfirm] = useState<{ tool: string; preview: string } | null>(null);
  const [hitlPending, setHitlPending] = useState(false);
  const [hitlData, setHitlData] = useState<{
    profile: string;
    checkpoint: string;
    deliverables: string[];
    baselineResults?: Array<{ name: string; status: string; output?: string }>;
    timeoutSeconds: number;
  } | null>(null);
  const [activatedSkills, setActivatedSkills] = useState<string[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  // Restore chat history when session is loaded from localStorage
  useEffect(() => {
    if (!sessionId) return;
    (async () => {
      try {
        const res = await fetch(`/api/chat/sessions/${sessionId}/messages`);
        if (!res.ok) {
          // Session expired or invalid, clear it
          localStorage.removeItem(`forge_chat_session_${workspaceId}`);
          setSessionId(null);
          return;
        }
        const data = (await res.json()) as Array<{
          id: string;
          role: string;
          content: string;
          timestamp: string;
        }>;
        if (data.length > 0) {
          setMessages(
            data.map((m) => ({
              id: m.id,
              role:
                m.role.toLowerCase() === "user" ? ("user" as const) : ("assistant" as const),
              content: m.content,
              timestamp: m.timestamp,
            })),
          );
        }
      } catch {
        // Ignore - fresh session
      }
    })();
  }, [sessionId, workspaceId]);

  // Scroll to bottom on new messages
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, thinkingText]);

  // Listen for AI explain events from the editor
  useEffect(() => {
    const handleExplain = (e: Event) => {
      const detail = (e as CustomEvent).detail as {
        code: string;
        filePath: string;
        language: string;
      };
      setInput(
        `Explain this ${detail.language} code from ${detail.filePath}:\n\`\`\`${detail.language}\n${detail.code}\n\`\`\``,
      );
      inputRef.current?.focus();
    };

    const handleExplainFile = (e: Event) => {
      const detail = (e as CustomEvent).detail as { filePath: string };
      setInput(`Explain the file: ${detail.filePath}`);
      inputRef.current?.focus();
    };

    window.addEventListener("forge:ai-explain", handleExplain);
    window.addEventListener("forge:ai-explain-file", handleExplainFile);

    return () => {
      window.removeEventListener("forge:ai-explain", handleExplain);
      window.removeEventListener("forge:ai-explain-file", handleExplainFile);
    };
  }, []);

  const initSession = useCallback(async () => {
    if (sessionId) return sessionId;
    try {
      const res = await fetch("/api/chat/sessions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ workspaceId }),
      });
      if (!res.ok) throw new Error("Failed to create chat session");
      const data = (await res.json()) as { id: string };
      setSessionId(data.id);
      localStorage.setItem(`forge_chat_session_${workspaceId}`, data.id);
      return data.id;
    } catch (err) {
      console.error("Failed to init session:", err);
      return null;
    }
  }, [workspaceId, sessionId]);

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!input.trim() || isStreaming) return;

    const sid = await initSession();
    if (!sid) {
      setMessages((prev) => [
        ...prev,
        {
          id: `err-${Date.now()}`,
          role: "assistant",
          content: "Failed to start chat session. Please try again.",
          timestamp: new Date().toISOString(),
        },
      ]);
      return;
    }

    // Build context attachments
    const contexts: Array<{ type: string; id: string; content?: string }> = [];
    for (const ctx of selectedContexts) {
      contexts.push({ type: ctx.type, id: ctx.id, content: ctx.preview });
    }
    if (activeFile && fileContent) {
      contexts.push({
        type: "file",
        id: activeFile,
        content: fileContent.slice(0, 10000),
      });
    }

    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: "user",
      content: input,
      timestamp: new Date().toISOString(),
      contexts: selectedContexts.map((c) => ({
        type: c.type,
        label: c.label,
      })),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setSelectedContexts([]);
    setShowContextPicker(false);
    setIsStreaming(true);
    setThinkingText("");
    setActivityLog([]);
    setBaselineResult(null);

    const assistantId = `assistant-${Date.now()}`;
    let fullContent = "";
    const toolCalls: Message["toolCalls"] = [];
    const segments: ContentSegment[] = [];

    const abortController = new AbortController();
    abortRef.current = abortController;

    try {
      await claudeClient.streamMessage(
        sid,
        input,
        contexts,
        (event: StreamEvent) => {
          switch (event.type) {
            case "ooda_phase":
              setOodaPhase(event.phase ?? null);
              if (event.detail) setOodaDetail(event.detail);
              if (event.turn && event.maxTurns) {
                setCurrentTurn({ turn: event.turn, maxTurns: event.maxTurns });
              }
              break;
            case "sub_step":
              setActivityLog((prev) => [
                ...prev.slice(-49),
                { message: event.message ?? "", timestamp: event.timestamp ?? new Date().toISOString() },
              ]);
              break;
            case "baseline_check":
              setBaselineResult({
                status: event.status ?? "running",
                attempt: event.attempt,
                summary: event.summary,
                baselines: event.baselines,
              });
              break;
            case "context_usage":
              setContextUsage({
                tokensUsed: event.tokensUsed ?? 0,
                tokenBudget: event.tokenBudget ?? 25000,
                compressionPhase: event.compressionPhase ?? 0,
                turn: event.turn ?? 0,
              });
              break;
            case "tool_use_start":
              setOodaDetail(event.toolName ?? "");
              break;
            case "hitl_checkpoint":
              if (event.status === "awaiting_approval") {
                setHitlPending(true);
                setHitlData({
                  profile: event.profile ?? "",
                  checkpoint: event.checkpoint ?? "",
                  deliverables: event.deliverables ?? [],
                  baselineResults: event.baselineResults,
                  timeoutSeconds: event.timeoutSeconds ?? 300,
                });
              } else {
                // approved / rejected / timeout / modified
                setHitlPending(false);
                setHitlData(null);
              }
              break;
            case "git_confirm":
              setGitConfirm({
                tool: event.gitConfirmTool ?? "",
                preview: event.gitConfirmPreview ?? "",
              });
              break;
            case "skills_activated":
              setActivatedSkills(event.skills ?? []);
              break;
            case "profile_active":
              setActiveProfile({
                name: event.activeProfile ?? "unknown",
                skills: event.loadedSkills ?? [],
                reason: event.routingReason ?? "",
                confidence: event.confidence ?? 0,
              });
              break;
            case "thinking":
              setThinkingText(event.content ?? "");
              break;
            case "content": {
              const chunk = event.content ?? "";
              fullContent += chunk;
              // Build segments: append to last text segment or create new one
              const lastSeg = segments[segments.length - 1];
              if (lastSeg && lastSeg.type === "text") {
                lastSeg.content += chunk;
              } else {
                segments.push({ type: "text", content: chunk });
              }
              setMessages((prev) => {
                const existing = prev.find((m) => m.id === assistantId);
                if (existing) {
                  return prev.map((m) =>
                    m.id === assistantId ? { ...m, content: fullContent, segments: [...segments] } : m,
                  );
                }
                return [
                  ...prev,
                  {
                    id: assistantId,
                    role: "assistant",
                    content: fullContent,
                    timestamp: new Date().toISOString(),
                    toolCalls,
                    segments: [...segments],
                  },
                ];
              });
              setThinkingText("");
              break;
            }
            case "tool_use": {
              const tc = {
                id: event.toolCallId ?? `tool-${Date.now()}`,
                name: event.toolName ?? "unknown",
                input: event.toolInput ?? {},
                output: undefined,
                status: "running" as const,
              };
              toolCalls.push(tc);
              segments.push({ type: "tool_call", toolCall: tc });
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? { ...m, toolCalls: [...toolCalls], segments: [...segments] }
                    : m,
                ),
              );
              break;
            }
            case "tool_result":
              {
                const idx = toolCalls.findIndex(
                  (tc) => tc.id === event.toolCallId,
                );
                if (idx >= 0) {
                  toolCalls[idx] = {
                    ...toolCalls[idx],
                    output: event.content,
                    status: "complete",
                  };
                }
                // Also update the tool_call in segments
                for (const seg of segments) {
                  if (seg.type === "tool_call" && seg.toolCall.id === event.toolCallId) {
                    seg.toolCall = { ...seg.toolCall, output: event.content, status: "complete" };
                  }
                }
                setMessages((prev) =>
                  prev.map((m) =>
                    m.id === assistantId
                      ? { ...m, toolCalls: [...toolCalls], segments: [...segments] }
                      : m,
                  ),
                );
              }
              break;
            case "error":
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId
                    ? {
                        ...m,
                        content:
                          fullContent +
                          "\n\n[Error: " +
                          (event.content ?? "Unknown error") +
                          "]",
                      }
                    : m,
                ),
              );
              // BUG-048: Clear streaming state on error so UI doesn't stay stuck in "Thinking..."
              setIsStreaming(false);
              setThinkingText("");
              setOodaPhase(null);
              break;
            case "file_changed": {
              // Notify workspace page to refresh file tree and open the file
              window.dispatchEvent(
                new CustomEvent("forge:file-changed", {
                  detail: {
                    path: event.path ?? "",
                    action: event.action ?? "created",
                  },
                }),
              );
              break;
            }
            case "done":
              break;
          }
        },
        abortController.signal,
        workspaceId,
        selectedModel,
      );
    } catch (err) {
      if ((err as Error).name !== "AbortError") {
        console.error("Chat stream error:", err);
        setMessages((prev) => [
          ...prev.filter((m) => m.id !== assistantId),
          {
            id: assistantId,
            role: "assistant",
            content:
              fullContent || "An error occurred while processing your message.",
            timestamp: new Date().toISOString(),
          },
        ]);
      }
    } finally {
      setIsStreaming(false);
      setThinkingText("");
      setOodaPhase(null);
      setOodaDetail("");
      setCurrentTurn(null);
      setBaselineResult(null);
      // contextUsage intentionally NOT cleared — keep showing after stream ends
      setHitlPending(false);
      setHitlData(null);
      setGitConfirm(null);
      abortRef.current = null;
    }
  };

  const handleStop = () => {
    abortRef.current?.abort();
    setIsStreaming(false);
    setThinkingText("");
    setOodaPhase(null);
    setOodaDetail("");
    setCurrentTurn(null);
    setBaselineResult(null);
    setContextUsage(null);
    setHitlPending(false);
    setHitlData(null);
    setGitConfirm(null);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "@") {
      setShowContextPicker(true);
    }
    // When ContextPicker is open, block printable chars from entering textarea
    // so they go to the picker's search input instead
    if (
      showContextPicker &&
      e.key.length === 1 &&
      e.key !== "@" &&
      !e.ctrlKey &&
      !e.metaKey
    ) {
      e.preventDefault();
    }
    if (showContextPicker && e.key === "Backspace") {
      e.preventDefault();
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleContextSelect = (item: ContextItem) => {
    if (item.type === "profile") {
      // Profile tags: replace the trailing @ in input with @tag so backend ProfileRouter detects it
      const tag = `@${item.label} `;
      setInput((prev) => {
        const lastAt = prev.lastIndexOf("@");
        if (lastAt >= 0) {
          return prev.substring(0, lastAt) + tag + prev.substring(lastAt + 1);
        }
        return tag + prev;
      });
      setShowContextPicker(false);
      inputRef.current?.focus();
      return;
    }
    // Non-profile context: add as chip and remove the trailing @ from input
    setSelectedContexts((prev) => [...prev, item]);
    setInput((prev) => {
      const lastAt = prev.lastIndexOf("@");
      if (lastAt >= 0) {
        return prev.substring(0, lastAt) + prev.substring(lastAt + 1);
      }
      return prev;
    });
    setShowContextPicker(false);
    inputRef.current?.focus();
  };

  const handleRemoveContext = (id: string) => {
    setSelectedContexts((prev) => prev.filter((c) => c.id !== id));
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header with Tabs */}
      <div className="border-b border-border">
        {/* Row 1: Tab buttons */}
        <div className="flex items-center gap-0 px-2 pt-1.5">
          {([
            { key: "chat" as const, label: "对话" },
            { key: "quality" as const, label: "质量" },
            { key: "skills" as const, label: "Skills" },
            { key: "memory" as const, label: "记忆" },
            { key: "pipeline" as const, label: "管道" },
          ]).map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`px-3 py-1.5 text-xs font-medium transition-colors ${
                activeTab === tab.key
                  ? "border-b-2 border-primary text-foreground"
                  : "border-b-2 border-transparent text-muted-foreground hover:text-foreground"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
        {/* Row 2: Model selector and action buttons */}
        <div className="flex items-center justify-between px-3 py-1.5">
          <ModelSelector
            selectedModel={selectedModel}
            onModelChange={(modelId) => {
              setSelectedModel(modelId);
              localStorage.setItem("forge_selected_model", modelId);
            }}
          />
          <div className="flex items-center gap-1">
            <button
              onClick={() => {
                setMessages([]);
                setSessionId(null);
              }}
              className="rounded p-1 text-muted-foreground hover:bg-accent"
              title="New conversation"
            >
              <RotateCcw className="h-3.5 w-3.5" />
            </button>
            <button
              onClick={() => setShowModelSettings(true)}
              className="rounded p-1 text-muted-foreground hover:bg-accent"
              title="Model settings"
            >
              <Settings className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Model Settings Dialog */}
      <ModelSettingsDialog
        isOpen={showModelSettings}
        onClose={() => setShowModelSettings(false)}
      />

      {/* Quality Panel Tab */}
      {activeTab === "quality" && (
        <div className="flex-1 overflow-hidden">
          <QualityPanel />
        </div>
      )}

      {/* Skills Tab */}
      {activeTab === "skills" && (
        <div className="flex-1 overflow-hidden">
          <WorkspaceSkillPanel workspaceId={workspaceId} />
        </div>
      )}

      {/* Memory Tab */}
      {activeTab === "memory" && (
        <div className="flex-1 overflow-hidden">
          <MemoryPanel workspaceId={workspaceId} />
        </div>
      )}

      {/* Pipeline Tab */}
      {activeTab === "pipeline" && (
        <div className="flex-1 overflow-auto">
          <PipelinePanel />
        </div>
      )}

      {/* Messages (Chat Tab) */}
      <div ref={scrollRef} className={`flex-1 overflow-auto p-4 space-y-4 ${activeTab !== "chat" ? "hidden" : ""}`}>
        {messages.length === 0 && (
          <div className="flex h-full items-center justify-center">
            <div className="text-center text-muted-foreground">
              <p className="text-sm font-medium">Start a conversation</p>
              <p className="mt-1 text-xs">
                Ask about code, architecture, or use @ to attach context
              </p>
            </div>
          </div>
        )}
        {messages.map((message) => (
          <ChatMessage
            key={message.id}
            message={message}
            workspaceId={workspaceId}
          />
        ))}
        {(isStreaming || activityLog.length > 0) && (activeProfile || oodaPhase || activityLog.length > 0) && (
          <div className="space-y-1.5 mx-1">
            {/* OODA Phase Indicator — enhanced with Turn info */}
            {oodaPhase && isStreaming && (
              <div className="flex items-center gap-0.5">
                {OODA_PHASES.map((p) => {
                  const isActive = p.key === oodaPhase;
                  const phaseIdx = OODA_PHASES.findIndex(
                    (x) => x.key === oodaPhase,
                  );
                  const thisIdx = OODA_PHASES.findIndex((x) => x.key === p.key);
                  const isPast = thisIdx < phaseIdx;
                  const Icon = p.icon;
                  return (
                    <div
                      key={p.key}
                      className={`flex items-center gap-0.5 rounded px-1.5 py-0.5 text-xs transition-colors ${
                        isActive
                          ? "bg-primary/15 text-primary font-medium"
                          : isPast
                            ? "text-green-400"
                            : "text-muted-foreground/40"
                      }`}
                      title={p.label}
                    >
                      <Icon className="h-3 w-3" />
                      {isActive && <span>{p.label}</span>}
                    </div>
                  );
                })}
                {/* Turn counter */}
                {currentTurn && (
                  <span className="ml-1 text-xs text-muted-foreground">
                    Turn {currentTurn.turn}/{currentTurn.maxTurns}
                  </span>
                )}
                {/* Current tool name */}
                {oodaDetail && oodaPhase === "act" && (
                  <span className="ml-1 text-xs text-muted-foreground font-mono truncate max-w-[120px]">
                    {oodaDetail}
                  </span>
                )}
              </div>
            )}
            {/* Profile Badge */}
            {activeProfile && isStreaming && (
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground border border-border rounded-md px-2 py-1 bg-muted/50">
                {/* Confidence dot */}
                <span
                  className={`h-1.5 w-1.5 rounded-full flex-shrink-0 ${
                    activeProfile.confidence >= 0.8
                      ? "bg-green-400"
                      : activeProfile.confidence >= 0.5
                        ? "bg-yellow-400"
                        : "bg-muted-foreground"
                  }`}
                  title={`Confidence: ${Math.round(activeProfile.confidence * 100)}%`}
                />
                <span className="font-medium text-primary">
                  {activeProfile.name.replace("-profile", "")}
                </span>
                <span className="text-border">|</span>
                <span className="truncate">
                  {activeProfile.skills.slice(0, 3).join(", ")}
                  {activeProfile.skills.length > 3 &&
                    ` +${activeProfile.skills.length - 3}`}
                </span>
                {/* Routing reason */}
                {activeProfile.reason && (
                  <>
                    <span className="text-border">|</span>
                    <span className="truncate italic">
                      {activeProfile.reason}
                    </span>
                  </>
                )}
              </div>
            )}
            {/* Delivery Stage Indicator */}
            {activeProfile && (
              <div className="flex items-center gap-1 text-xs text-muted-foreground">
                {["Planning", "Design", "Development", "Testing", "Ops", "Evaluation"].map((stage) => {
                  const profileName = activeProfile.name.toLowerCase();
                  const stageMap: Record<string, string> = {
                    "requirement-engineering-profile": "Planning",
                    "planning-profile": "Planning",
                    "architecture-design-profile": "Design",
                    "detailed-design-profile": "Design",
                    "design-profile": "Design",
                    "development-profile": "Development",
                    "code-generation-profile": "Development",
                    "test-case-writing-profile": "Testing",
                    "testing-profile": "Testing",
                    "deployment-ops-profile": "Ops",
                    "ops-profile": "Ops",
                    "evaluation-profile": "Evaluation",
                  };
                  const currentStage = stageMap[profileName] || "Development";
                  const isActive = stage === currentStage;
                  const stageIdx = ["Planning", "Design", "Development", "Testing", "Ops", "Evaluation"].indexOf(stage);
                  const currentIdx = ["Planning", "Design", "Development", "Testing", "Ops", "Evaluation"].indexOf(currentStage);
                  const isPast = stageIdx < currentIdx;
                  return (
                    <span
                      key={stage}
                      className={`px-1.5 py-0.5 rounded text-[10px] ${
                        isActive
                          ? "bg-primary/15 text-primary font-medium"
                          : isPast
                            ? "text-green-400/70"
                            : "text-muted-foreground/30"
                      }`}
                    >
                      {stage}
                    </span>
                  );
                })}
              </div>
            )}
            {/* Baseline Result */}
            {baselineResult && isStreaming && (
              <div className={`flex items-center gap-1.5 text-xs border rounded-md px-2 py-1 ${
                baselineResult.status === "passed" ? "border-green-500/30 bg-green-500/10 text-green-400"
                : baselineResult.status === "failed" ? "border-red-500/30 bg-red-500/10 text-red-400"
                : baselineResult.status === "running" ? "border-blue-500/30 bg-blue-500/10 text-blue-400"
                : "border-yellow-500/30 bg-yellow-500/10 text-yellow-400"
              }`}>
                <span className="font-medium">
                  {baselineResult.status === "passed" ? "✅ 底线通过" :
                   baselineResult.status === "failed" ? "❌ 底线失败" :
                   baselineResult.status === "running" ? "🔄 底线检查中..." :
                   baselineResult.status === "exhausted" ? "⚠️ 底线重试耗尽" :
                   `底线: ${baselineResult.status}`}
                </span>
                {baselineResult.attempt && (
                  <span className="text-muted-foreground">
                    (第 {baselineResult.attempt} 次)
                  </span>
                )}
                {baselineResult.summary && (
                  <span className="truncate max-w-[200px]">{baselineResult.summary}</span>
                )}
              </div>
            )}
            {/* Context Usage Indicator */}
            {contextUsage && (
              <div className="flex items-center gap-1.5 text-xs text-muted-foreground border border-border rounded-md px-2 py-1">
                <span>Context:</span>
                <div className="flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${
                      contextUsage.tokensUsed / contextUsage.tokenBudget > 0.8
                        ? "bg-red-400"
                        : contextUsage.tokensUsed / contextUsage.tokenBudget > 0.5
                          ? "bg-yellow-400"
                          : "bg-green-400"
                    }`}
                    style={{ width: `${Math.min(100, (contextUsage.tokensUsed / contextUsage.tokenBudget) * 100)}%` }}
                  />
                </div>
                <span className="text-[10px]">
                  {Math.round((contextUsage.tokensUsed / contextUsage.tokenBudget) * 100)}%
                  {contextUsage.turn > 0 && ` · T${contextUsage.turn}`}
                  {contextUsage.compressionPhase > 0 && ` · P${contextUsage.compressionPhase}`}
                </span>
              </div>
            )}
            {/* Activity Log (collapsible) */}
            {activityLog.length > 0 && (
              <div className="border border-border rounded-md bg-muted/30">
                <button
                  onClick={() => setShowActivityLog(!showActivityLog)}
                  className="flex w-full items-center gap-1 px-2 py-1 text-xs text-muted-foreground hover:text-foreground"
                >
                  {showActivityLog ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                  <Activity className="h-3 w-3" />
                  <span>活动日志 ({activityLog.length})</span>
                </button>
                {showActivityLog && (
                  <div className="max-h-32 overflow-auto border-t border-border px-2 py-1 space-y-0.5">
                    {activityLog.map((step, i) => (
                      <div key={i} className="flex items-start gap-1 text-xs text-muted-foreground">
                        <span className="flex-shrink-0 text-muted-foreground/60">
                          {new Date(step.timestamp).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" })}
                        </span>
                        <span>{step.message}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}
        {/* Skills Activated Tags */}
        {activatedSkills.length > 0 && isStreaming && (
          <div className="mx-1 flex flex-wrap gap-1">
            {activatedSkills.slice(0, 5).map((skill) => (
              <span
                key={skill}
                className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] text-primary border border-primary/20"
              >
                {skill}
              </span>
            ))}
            {activatedSkills.length > 5 && (
              <span className="text-[10px] text-muted-foreground">
                +{activatedSkills.length - 5}
              </span>
            )}
          </div>
        )}
        {/* HITL Approval Panel */}
        {hitlPending && hitlData && (
          <HitlApprovalPanel
            profile={hitlData.profile}
            checkpoint={hitlData.checkpoint}
            deliverables={hitlData.deliverables}
            baselineResults={hitlData.baselineResults}
            timeoutSeconds={hitlData.timeoutSeconds}
            onResolved={() => {
              setHitlPending(false);
              setHitlData(null);
            }}
          />
        )}
        {thinkingText && (
          <div className="flex items-start gap-2 text-sm text-muted-foreground">
            <div className="flex space-x-1 pt-1">
              <span
                className="h-1.5 w-1.5 rounded-full bg-primary animate-thinking-dot"
                style={{ animationDelay: "0ms" }}
              />
              <span
                className="h-1.5 w-1.5 rounded-full bg-primary animate-thinking-dot"
                style={{ animationDelay: "200ms" }}
              />
              <span
                className="h-1.5 w-1.5 rounded-full bg-primary animate-thinking-dot"
                style={{ animationDelay: "400ms" }}
              />
            </div>
            <span className="italic text-xs">Thinking...</span>
          </div>
        )}
      </div>

      {/* Context Chips (chat tab only) */}
      {activeTab === "chat" && selectedContexts.length > 0 && (
        <div className="flex flex-wrap gap-1 border-t border-border px-4 pt-2">
          {selectedContexts.map((ctx) => (
            <span
              key={ctx.id}
              className="flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary"
            >
              @{ctx.label}
              <button
                onClick={() => handleRemoveContext(ctx.id)}
                className="ml-0.5 hover:text-destructive"
              >
                x
              </button>
            </span>
          ))}
        </div>
      )}

      {/* Context Picker (chat tab only) */}
      {activeTab === "chat" && showContextPicker && (
        <div className="border-t border-border">
          <ContextPicker
            workspaceId={workspaceId}
            onSelect={handleContextSelect}
            onClose={() => setShowContextPicker(false)}
          />
        </div>
      )}

      {/* Git Confirmation Card (chat tab only) */}
      {activeTab === "chat" && gitConfirm && (
        <div className="border-t border-amber-200 dark:border-amber-800 bg-amber-50 dark:bg-amber-950/20 px-3 py-2">
          <div className="flex items-center gap-2 text-sm font-medium text-amber-800 dark:text-amber-200">
            <GitCommit className="h-4 w-4 flex-shrink-0" />
            <span>确认执行 {gitConfirm.tool}？</span>
          </div>
          <pre className="mt-2 text-xs font-mono bg-muted p-2 rounded whitespace-pre-wrap break-all max-h-32 overflow-auto">
            {gitConfirm.preview}
          </pre>
          <div className="mt-2 flex gap-2">
            <button
              onClick={() => {
                claudeClient.sendGitConfirmResponse(true);
                setGitConfirm(null);
              }}
              className="px-3 py-1 text-xs rounded bg-primary text-primary-foreground hover:bg-primary/90"
            >
              ✅ 确认
            </button>
            <button
              onClick={() => {
                claudeClient.sendGitConfirmResponse(false);
                setGitConfirm(null);
              }}
              className="px-3 py-1 text-xs rounded bg-muted hover:bg-muted/80 border border-border"
            >
              ❌ 取消
            </button>
          </div>
        </div>
      )}

      {/* Input Area (chat tab only) */}
      <div className={`border-t border-border p-3 ${activeTab !== "chat" ? "hidden" : ""}`}>
        <form onSubmit={handleSubmit} className="flex items-end gap-2">
          <div className="flex-1 relative">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              className="w-full resize-none rounded-md border border-input bg-background px-3 py-2 pr-10 text-sm outline-none focus:ring-1 focus:ring-ring"
              placeholder="Ask anything... (@ for context)"
              rows={Math.min(input.split("\n").length, 5)}
              disabled={isStreaming}
            />
            <button
              type="button"
              onClick={() => setShowContextPicker(!showContextPicker)}
              className="absolute bottom-2 right-2 rounded p-1 text-muted-foreground hover:text-foreground"
              title="Attach context"
            >
              <Paperclip className="h-4 w-4" />
            </button>
          </div>
          {isStreaming ? (
            <button
              type="button"
              onClick={handleStop}
              className="rounded-md bg-destructive p-2 text-destructive-foreground hover:bg-destructive/90"
            >
              <StopCircle className="h-4 w-4" />
            </button>
          ) : (
            <button
              type="submit"
              disabled={!input.trim()}
              className="rounded-md bg-primary p-2 text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              <Send className="h-4 w-4" />
            </button>
          )}
        </form>
      </div>
    </div>
  );
}

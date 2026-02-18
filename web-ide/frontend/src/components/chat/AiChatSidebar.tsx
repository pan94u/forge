"use client";

import React, { useState, useRef, useEffect, useCallback } from "react";
import { Send, Paperclip, RotateCcw, StopCircle, Eye, Compass, Brain, Zap, CheckCircle2 } from "lucide-react";
import { ChatMessage, type Message } from "@/components/chat/ChatMessage";
import { ContextPicker, type ContextItem } from "@/components/chat/ContextPicker";
import { claudeClient, type StreamEvent, type OodaPhase } from "@/lib/claude-client";

const OODA_PHASES: { key: OodaPhase; label: string; icon: React.ElementType }[] = [
  { key: "observe", label: "Observe", icon: Eye },
  { key: "orient", label: "Orient", icon: Compass },
  { key: "decide", label: "Decide", icon: Brain },
  { key: "act", label: "Act", icon: Zap },
  { key: "complete", label: "Done", icon: CheckCircle2 },
];

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
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [isStreaming, setIsStreaming] = useState(false);
  const [showContextPicker, setShowContextPicker] = useState(false);
  const [selectedContexts, setSelectedContexts] = useState<ContextItem[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [thinkingText, setThinkingText] = useState<string>("");
  const [activeProfile, setActiveProfile] = useState<{
    name: string;
    skills: string[];
    reason: string;
    confidence: number;
  } | null>(null);
  const [oodaPhase, setOodaPhase] = useState<OodaPhase | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const abortRef = useRef<AbortController | null>(null);

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
        `Explain this ${detail.language} code from ${detail.filePath}:\n\`\`\`${detail.language}\n${detail.code}\n\`\`\``
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
    setIsStreaming(true);
    setThinkingText("");

    const assistantId = `assistant-${Date.now()}`;
    let fullContent = "";
    const toolCalls: Message["toolCalls"] = [];

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
            case "content":
              fullContent += event.content ?? "";
              setMessages((prev) => {
                const existing = prev.find((m) => m.id === assistantId);
                if (existing) {
                  return prev.map((m) =>
                    m.id === assistantId
                      ? { ...m, content: fullContent }
                      : m
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
                  },
                ];
              });
              setThinkingText("");
              break;
            case "tool_use":
              toolCalls.push({
                id: event.toolCallId ?? `tool-${Date.now()}`,
                name: event.toolName ?? "unknown",
                input: event.toolInput ?? {},
                output: undefined,
                status: "running",
              });
              setMessages((prev) =>
                prev.map((m) =>
                  m.id === assistantId ? { ...m, toolCalls: [...toolCalls] } : m
                )
              );
              break;
            case "tool_result":
              {
                const idx = toolCalls.findIndex(
                  (tc) => tc.id === event.toolCallId
                );
                if (idx >= 0) {
                  toolCalls[idx] = {
                    ...toolCalls[idx],
                    output: event.content,
                    status: "complete",
                  };
                }
                setMessages((prev) =>
                  prev.map((m) =>
                    m.id === assistantId
                      ? { ...m, toolCalls: [...toolCalls] }
                      : m
                  )
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
                    : m
                )
              );
              break;
            case "done":
              break;
          }
        },
        abortController.signal
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
              fullContent ||
              "An error occurred while processing your message.",
            timestamp: new Date().toISOString(),
          },
        ]);
      }
    } finally {
      setIsStreaming(false);
      setThinkingText("");
      setOodaPhase(null);
      abortRef.current = null;
    }
  };

  const handleStop = () => {
    abortRef.current?.abort();
    setIsStreaming(false);
    setThinkingText("");
    setOodaPhase(null);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "@") {
      setShowContextPicker(true);
    }
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const handleContextSelect = (item: ContextItem) => {
    setSelectedContexts((prev) => [...prev, item]);
    setShowContextPicker(false);
  };

  const handleRemoveContext = (id: string) => {
    setSelectedContexts((prev) => prev.filter((c) => c.id !== id));
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-4 py-2">
        <h3 className="text-sm font-semibold">AI Assistant</h3>
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
      </div>

      {/* Messages */}
      <div ref={scrollRef} className="flex-1 overflow-auto p-4 space-y-4">
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
          <ChatMessage key={message.id} message={message} />
        ))}
        {isStreaming && (activeProfile || oodaPhase) && (
          <div className="space-y-1.5 mx-1">
            {/* OODA Phase Indicator */}
            {oodaPhase && (
              <div className="flex items-center gap-0.5">
                {OODA_PHASES.map((p) => {
                  const isActive = p.key === oodaPhase;
                  const phaseIdx = OODA_PHASES.findIndex(x => x.key === oodaPhase);
                  const thisIdx = OODA_PHASES.findIndex(x => x.key === p.key);
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
              </div>
            )}
            {/* Profile Badge */}
            {activeProfile && (
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
                  {activeProfile.skills.length > 3 && ` +${activeProfile.skills.length - 3}`}
                </span>
                {/* Routing reason */}
                {activeProfile.reason && (
                  <>
                    <span className="text-border">|</span>
                    <span className="truncate italic">{activeProfile.reason}</span>
                  </>
                )}
              </div>
            )}
          </div>
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

      {/* Context Chips */}
      {selectedContexts.length > 0 && (
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

      {/* Context Picker */}
      {showContextPicker && (
        <div className="border-t border-border">
          <ContextPicker
            workspaceId={workspaceId}
            onSelect={handleContextSelect}
            onClose={() => setShowContextPicker(false)}
          />
        </div>
      )}

      {/* Input Area */}
      <div className="border-t border-border p-3">
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

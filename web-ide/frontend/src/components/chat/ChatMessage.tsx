"use client";

import React, { useMemo } from "react";
import {
  User,
  Bot,
  Wrench,
  CheckCircle,
  Loader2,
  AlertCircle,
  Copy,
  Check,
  FileDown,
} from "lucide-react";
import { workspaceApi } from "@/lib/workspace-api";

export interface ToolCall {
  id: string;
  name: string;
  input: Record<string, unknown>;
  output?: string;
  status: "running" | "complete" | "error";
}

export interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: string;
  contexts?: Array<{ type: string; label: string }>;
  toolCalls?: ToolCall[];
}

interface ChatMessageProps {
  message: Message;
  workspaceId?: string;
}

function CodeBlock({
  code,
  language,
  workspaceId,
}: {
  code: string;
  language: string;
  workspaceId?: string;
}) {
  const [copied, setCopied] = React.useState(false);
  const [applying, setApplying] = React.useState(false);
  const [applied, setApplied] = React.useState(false);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleApply = async () => {
    if (!workspaceId || applying) return;

    // Suggest a file name based on language
    const extMap: Record<string, string> = {
      typescript: ".ts",
      ts: ".ts",
      tsx: ".tsx",
      javascript: ".js",
      js: ".js",
      jsx: ".jsx",
      kotlin: ".kt",
      java: ".java",
      python: ".py",
      go: ".go",
      rust: ".rs",
      html: ".html",
      css: ".css",
      json: ".json",
      yaml: ".yml",
      yml: ".yml",
      sql: ".sql",
      bash: ".sh",
      sh: ".sh",
      markdown: ".md",
      md: ".md",
    };
    const ext = extMap[language] || `.${language || "txt"}`;
    const suggestedName = `src/new-file${ext}`;

    const fileName = window.prompt("Save as:", suggestedName);
    if (!fileName) return;

    setApplying(true);
    try {
      await workspaceApi.createFile(workspaceId, fileName, code);
      setApplied(true);
      setTimeout(() => setApplied(false), 3000);
      // Notify workspace to refresh
      window.dispatchEvent(
        new CustomEvent("forge:file-changed", {
          detail: { path: fileName, action: "created" },
        })
      );
    } catch (err) {
      console.error("Failed to apply code:", err);
    } finally {
      setApplying(false);
    }
  };

  return (
    <div className="group relative my-2 rounded-md border border-border bg-[#1e1e1e]">
      <div className="flex items-center justify-between border-b border-border px-3 py-1">
        <span className="text-xs text-muted-foreground">{language || "code"}</span>
        <div className="flex items-center gap-1">
          {workspaceId && (
            <button
              onClick={handleApply}
              disabled={applying}
              className="rounded p-0.5 text-muted-foreground transition-opacity hover:text-foreground disabled:opacity-50"
              title="Apply to workspace"
            >
              {applied ? (
                <Check className="h-3.5 w-3.5 text-green-400" />
              ) : applying ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <FileDown className="h-3.5 w-3.5" />
              )}
            </button>
          )}
          <button
            onClick={handleCopy}
            className="rounded p-0.5 text-muted-foreground transition-opacity hover:text-foreground"
          >
            {copied ? (
              <Check className="h-3.5 w-3.5 text-green-400" />
            ) : (
              <Copy className="h-3.5 w-3.5" />
            )}
          </button>
        </div>
      </div>
      <pre className="overflow-x-auto p-3 text-xs">
        <code>{code}</code>
      </pre>
    </div>
  );
}

function renderMarkdown(content: string, workspaceId?: string): React.ReactNode[] {
  const parts: React.ReactNode[] = [];
  const codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = codeBlockRegex.exec(content)) !== null) {
    // Add text before code block
    if (match.index > lastIndex) {
      const textBefore = content.slice(lastIndex, match.index);
      parts.push(
        <span key={`text-${lastIndex}`}>
          {renderInlineMarkdown(textBefore)}
        </span>
      );
    }

    // Add code block
    parts.push(
      <CodeBlock
        key={`code-${match.index}`}
        language={match[1]}
        code={match[2].trim()}
        workspaceId={workspaceId}
      />
    );

    lastIndex = match.index + match[0].length;
  }

  // Add remaining text
  if (lastIndex < content.length) {
    const remaining = content.slice(lastIndex);
    parts.push(
      <span key={`text-${lastIndex}`}>
        {renderInlineMarkdown(remaining)}
      </span>
    );
  }

  return parts;
}

function renderInlineMarkdown(text: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  const lines = text.split("\n");

  lines.forEach((line, lineIdx) => {
    // Headers
    const headerMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headerMatch) {
      const level = headerMatch[1].length;
      const headerText = headerMatch[2];
      const sizes: Record<number, string> = {
        1: "text-lg font-bold",
        2: "text-base font-bold",
        3: "text-sm font-semibold",
        4: "text-sm font-medium",
        5: "text-xs font-medium",
        6: "text-xs font-medium",
      };
      nodes.push(
        <div key={`h-${lineIdx}`} className={`mt-2 mb-1 ${sizes[level]}`}>
          {headerText}
        </div>
      );
      return;
    }

    // Bold and inline code
    let processed: React.ReactNode = line;

    // Inline code
    if (typeof processed === "string" && processed.includes("`")) {
      const codeParts = (processed as string).split(/`([^`]+)`/);
      processed = codeParts.map((part, i) =>
        i % 2 === 1 ? (
          <code
            key={`ic-${lineIdx}-${i}`}
            className="rounded bg-muted px-1 py-0.5 font-mono text-xs"
          >
            {part}
          </code>
        ) : (
          <React.Fragment key={`it-${lineIdx}-${i}`}>{part}</React.Fragment>
        )
      );
    }

    // Bold
    if (typeof processed === "string" && processed.includes("**")) {
      const boldParts = (processed as string).split(/\*\*([^*]+)\*\*/);
      processed = boldParts.map((part, i) =>
        i % 2 === 1 ? (
          <strong key={`b-${lineIdx}-${i}`}>{part}</strong>
        ) : (
          <React.Fragment key={`bt-${lineIdx}-${i}`}>{part}</React.Fragment>
        )
      );
    }

    // List items
    const listMatch = line.match(/^(\s*)[-*]\s+(.+)$/);
    if (listMatch) {
      const indent = Math.floor(listMatch[1].length / 2);
      nodes.push(
        <div
          key={`li-${lineIdx}`}
          className="flex items-start gap-1"
          style={{ marginLeft: `${indent * 16}px` }}
        >
          <span className="mt-1.5 h-1 w-1 flex-shrink-0 rounded-full bg-muted-foreground" />
          <span>{listMatch[2]}</span>
        </div>
      );
      return;
    }

    if (line.trim() === "") {
      nodes.push(<div key={`br-${lineIdx}`} className="h-2" />);
    } else {
      nodes.push(
        <div key={`line-${lineIdx}`}>
          {Array.isArray(processed) ? processed : processed}
        </div>
      );
    }
  });

  return nodes;
}

function ToolCallDisplay({ toolCall }: { toolCall: ToolCall }) {
  const [expanded, setExpanded] = React.useState(false);

  return (
    <div className="my-2 rounded-md border border-border bg-card text-xs">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex w-full items-center gap-2 px-3 py-2 hover:bg-accent"
      >
        <Wrench className="h-3 w-3 text-muted-foreground" />
        <span className="font-mono font-medium">{toolCall.name}</span>
        <span className="flex-1" />
        {toolCall.status === "running" && (
          <Loader2 className="h-3 w-3 animate-spin text-primary" />
        )}
        {toolCall.status === "complete" && (
          <CheckCircle className="h-3 w-3 text-green-400" />
        )}
        {toolCall.status === "error" && (
          <AlertCircle className="h-3 w-3 text-destructive" />
        )}
      </button>
      {expanded && (
        <div className="border-t border-border px-3 py-2 space-y-2">
          <div>
            <span className="text-muted-foreground">Input:</span>
            <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 font-mono">
              {JSON.stringify(toolCall.input, null, 2)}
            </pre>
          </div>
          {toolCall.output && (
            <div>
              <span className="text-muted-foreground">Output:</span>
              <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 font-mono">
                {toolCall.output}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function ChatMessage({ message, workspaceId }: ChatMessageProps) {
  const isUser = message.role === "user";
  const renderedContent = useMemo(
    () => renderMarkdown(message.content, isUser ? undefined : workspaceId),
    [message.content, isUser, workspaceId]
  );

  return (
    <div className={`flex gap-3 ${isUser ? "flex-row-reverse" : ""}`}>
      {/* Avatar */}
      <div
        className={`flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full ${
          isUser ? "bg-primary" : "bg-forge-600"
        }`}
      >
        {isUser ? (
          <User className="h-4 w-4 text-primary-foreground" />
        ) : (
          <Bot className="h-4 w-4 text-white" />
        )}
      </div>

      {/* Content */}
      <div
        className={`max-w-[85%] space-y-1 ${isUser ? "text-right" : ""}`}
      >
        {/* Context chips */}
        {message.contexts && message.contexts.length > 0 && (
          <div className={`flex flex-wrap gap-1 ${isUser ? "justify-end" : ""}`}>
            {message.contexts.map((ctx, i) => (
              <span
                key={i}
                className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
              >
                @{ctx.label}
              </span>
            ))}
          </div>
        )}

        {/* Message body */}
        <div
          className={`rounded-lg px-3 py-2 text-sm ${
            isUser
              ? "bg-primary text-primary-foreground"
              : "bg-card border border-border"
          }`}
        >
          {renderedContent}
        </div>

        {/* Tool calls */}
        {message.toolCalls && message.toolCalls.length > 0 && (
          <div className="space-y-1">
            {message.toolCalls.map((tc) => (
              <ToolCallDisplay key={tc.id} toolCall={tc} />
            ))}
          </div>
        )}

        {/* Timestamp */}
        <p className="text-xs text-muted-foreground">
          {new Date(message.timestamp).toLocaleTimeString()}
        </p>
      </div>
    </div>
  );
}

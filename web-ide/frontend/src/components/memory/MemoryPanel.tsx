"use client";

import React, { useState, useEffect, useCallback } from "react";
import { StageMemoryView } from "./StageMemoryView";
import { SessionHistoryView } from "./SessionHistoryView";

interface MemoryPanelProps {
  workspaceId: string;
}

export function MemoryPanel({ workspaceId }: MemoryPanelProps) {
  const [activeSubTab, setActiveSubTab] = useState<
    "workspace" | "stage" | "sessions"
  >("workspace");
  const [workspaceMemory, setWorkspaceMemory] = useState("");
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState("");
  const [loading, setLoading] = useState(true);

  const loadWorkspaceMemory = useCallback(async () => {
    try {
      const res = await fetch(`/api/memory/workspace/${workspaceId}`);
      if (res.ok) {
        const data = (await res.json()) as { content: string };
        setWorkspaceMemory(data.content);
      }
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, [workspaceId]);

  useEffect(() => {
    loadWorkspaceMemory();
  }, [loadWorkspaceMemory]);

  const handleSave = async () => {
    try {
      const res = await fetch(`/api/memory/workspace/${workspaceId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: editContent }),
      });
      if (res.ok) {
        setWorkspaceMemory(editContent);
        setIsEditing(false);
      }
    } catch {
      // ignore
    }
  };

  const handleClear = async () => {
    if (!confirm("确定要清空工作区记忆吗？")) return;
    try {
      const res = await fetch(`/api/memory/workspace/${workspaceId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content: "" }),
      });
      if (res.ok) {
        setWorkspaceMemory("");
        setIsEditing(false);
      }
    } catch {
      // ignore
    }
  };

  return (
    <div className="flex h-full flex-col">
      {/* Guide */}
      <div className="px-3 py-2 text-xs text-muted-foreground bg-muted/30 border-b border-border">
        AI 会自动记住工作上下文。工作区记忆跨会话保留，阶段记忆在任务切换时更新，会话历史记录每次对话摘要。
      </div>
      {/* Sub-tabs */}
      <div className="flex items-center gap-1 border-b border-border px-3 py-1.5">
        {(
          [
            { key: "workspace", label: "工作区记忆" },
            { key: "stage", label: "阶段记忆" },
            { key: "sessions", label: "会话历史" },
          ] as const
        ).map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveSubTab(tab.key)}
            className={`rounded px-2 py-1 text-xs font-medium transition-colors ${
              activeSubTab === tab.key
                ? "bg-primary/10 text-primary"
                : "text-muted-foreground hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto p-3">
        {activeSubTab === "workspace" && (
          <div className="space-y-3">
            {loading ? (
              <p className="text-xs text-muted-foreground">加载中...</p>
            ) : isEditing ? (
              <div className="space-y-2">
                <textarea
                  value={editContent}
                  onChange={(e) => setEditContent(e.target.value)}
                  className="w-full rounded-md border border-input bg-background p-2 text-xs font-mono outline-none focus:ring-1 focus:ring-ring"
                  rows={15}
                  maxLength={4000}
                  placeholder="输入工作区记忆内容（Markdown 格式，最多 4000 字符）..."
                />
                <div className="flex items-center justify-between">
                  <span className="text-xs text-muted-foreground">
                    {editContent.length}/4000
                  </span>
                  <div className="flex gap-1">
                    <button
                      onClick={() => setIsEditing(false)}
                      className="rounded px-2 py-1 text-xs text-muted-foreground hover:text-foreground"
                    >
                      取消
                    </button>
                    <button
                      onClick={handleSave}
                      className="rounded bg-primary px-2 py-1 text-xs text-primary-foreground hover:bg-primary/90"
                    >
                      保存
                    </button>
                  </div>
                </div>
              </div>
            ) : workspaceMemory ? (
              <div className="space-y-2">
                <pre className="whitespace-pre-wrap rounded-md border border-border bg-muted/30 p-2 text-xs font-mono">
                  {workspaceMemory}
                </pre>
                <div className="flex justify-end gap-1">
                  <button
                    onClick={() => {
                      setEditContent(workspaceMemory);
                      setIsEditing(true);
                    }}
                    className="rounded px-2 py-1 text-xs text-muted-foreground hover:bg-accent hover:text-foreground"
                  >
                    编辑
                  </button>
                  <button
                    onClick={handleClear}
                    className="rounded px-2 py-1 text-xs text-destructive hover:bg-destructive/10"
                  >
                    清空
                  </button>
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-8 text-center">
                <p className="text-sm text-muted-foreground">
                  暂无工作区记忆
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  AI 会在会话中自动积累工作区知识，也可以手动编辑
                </p>
                <button
                  onClick={() => {
                    setEditContent("");
                    setIsEditing(true);
                  }}
                  className="mt-3 rounded bg-primary px-3 py-1 text-xs text-primary-foreground hover:bg-primary/90"
                >
                  创建记忆
                </button>
              </div>
            )}
          </div>
        )}

        {activeSubTab === "stage" && (
          <StageMemoryView workspaceId={workspaceId} />
        )}

        {activeSubTab === "sessions" && (
          <SessionHistoryView workspaceId={workspaceId} />
        )}
      </div>
    </div>
  );
}

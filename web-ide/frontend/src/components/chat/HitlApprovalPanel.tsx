"use client";

import React, { useState, useEffect } from "react";
import { CheckCircle, XCircle, Edit3 } from "lucide-react";
import { claudeClient, type HitlAction } from "@/lib/claude-client";

interface HitlApprovalPanelProps {
  sessionId: string;
  profile: string;
  checkpoint: string;
  deliverables: string[];
  baselineResults?: Array<{ name: string; status: string; output?: string }>;
  timeoutSeconds: number;
  onResolved: () => void;
}

export function HitlApprovalPanel({
  sessionId,
  profile,
  checkpoint,
  deliverables,
  baselineResults,
  timeoutSeconds,
  onResolved,
}: HitlApprovalPanelProps) {
  const [remaining, setRemaining] = useState(timeoutSeconds);
  const [showModifyInput, setShowModifyInput] = useState(false);
  const [modifyText, setModifyText] = useState("");
  const [resolved, setResolved] = useState(false);

  useEffect(() => {
    if (resolved) return;
    const timer = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [resolved]);

  const handleAction = (
    action: HitlAction,
    feedback?: string,
    modifiedPrompt?: string,
  ) => {
    setResolved(true);
    claudeClient.sendHitlResponse(sessionId, action, feedback, modifiedPrompt);
    onResolved();
  };

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  if (resolved) return null;

  return (
    <div className="mx-1 rounded-lg border-2 border-orange-500/50 bg-orange-500/5 p-3 space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-sm font-semibold text-orange-400">
            {checkpoint}
          </span>
          <span className="text-xs text-muted-foreground">
            ({profile.replace("-profile", "")})
          </span>
        </div>
        <span
          className={`text-xs font-mono ${remaining <= 30 ? "text-red-400" : "text-muted-foreground"}`}
        >
          {formatTime(remaining)}
        </span>
      </div>

      {/* Deliverables */}
      {deliverables.length > 0 && (
        <div className="space-y-1">
          <span className="text-xs text-muted-foreground">已生成文件:</span>
          <div className="space-y-0.5">
            {deliverables.map((file, i) => (
              <div
                key={i}
                className="flex items-center gap-1 text-xs text-foreground"
              >
                <span className="text-green-400">+</span>
                <span className="font-mono">{file}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Baseline Results */}
      {baselineResults && baselineResults.length > 0 && (
        <div className="space-y-1">
          <span className="text-xs text-muted-foreground">底线检查:</span>
          <div className="flex flex-wrap gap-1">
            {baselineResults.map((br, i) => (
              <span
                key={i}
                className={`rounded px-1.5 py-0.5 text-xs ${
                  br.status === "PASS"
                    ? "bg-green-500/20 text-green-400"
                    : "bg-red-500/20 text-red-400"
                }`}
              >
                {br.status === "PASS" ? "✅" : "❌"} {br.name}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Modify Input */}
      {showModifyInput && (
        <div className="space-y-2">
          <textarea
            value={modifyText}
            onChange={(e) => setModifyText(e.target.value)}
            className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-ring resize-none"
            placeholder="输入修改指令..."
            rows={3}
            autoFocus
          />
          <div className="flex gap-2">
            <button
              onClick={() => handleAction("modify", modifyText, modifyText)}
              disabled={!modifyText.trim()}
              className="rounded-md bg-blue-600 px-3 py-1 text-xs text-white hover:bg-blue-700 disabled:opacity-50"
            >
              提交修改
            </button>
            <button
              onClick={() => setShowModifyInput(false)}
              className="rounded-md px-3 py-1 text-xs text-muted-foreground hover:text-foreground"
            >
              取消
            </button>
          </div>
        </div>
      )}

      {/* Action Buttons */}
      {!showModifyInput && (
        <div className="flex gap-2">
          <button
            onClick={() => handleAction("approve")}
            className="flex items-center gap-1 rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700"
          >
            <CheckCircle className="h-3.5 w-3.5" />
            批准继续
          </button>
          <button
            onClick={() => handleAction("reject", "用户拒绝")}
            className="flex items-center gap-1 rounded-md bg-red-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-red-700"
          >
            <XCircle className="h-3.5 w-3.5" />
            拒绝停止
          </button>
          <button
            onClick={() => setShowModifyInput(true)}
            className="flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-accent"
          >
            <Edit3 className="h-3.5 w-3.5" />
            修改指令
          </button>
        </div>
      )}
    </div>
  );
}

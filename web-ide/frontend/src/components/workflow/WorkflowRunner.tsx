"use client";

import React, { useState, useEffect, useRef } from "react";
import {
  CheckCircle,
  XCircle,
  Loader2,
  Clock,
  X,
  ChevronDown,
  ChevronRight,
} from "lucide-react";
import type { WorkflowNode } from "./WorkflowCanvas";

interface StepExecution {
  nodeId: string;
  nodeName: string;
  status: "pending" | "running" | "success" | "error" | "skipped";
  startedAt?: string;
  completedAt?: string;
  output?: string;
  error?: string;
  duration?: number;
}

interface WorkflowRunnerProps {
  workflowId: string;
  nodes: WorkflowNode[];
  onClose: () => void;
}

export function WorkflowRunner({
  workflowId,
  nodes,
  onClose,
}: WorkflowRunnerProps) {
  const [steps, setSteps] = useState<StepExecution[]>(() =>
    nodes.map((node) => ({
      nodeId: node.id,
      nodeName: (node.data.label as string) ?? node.type,
      status: "pending",
    }))
  );
  const [overallStatus, setOverallStatus] = useState<
    "running" | "success" | "error" | "idle"
  >("idle");
  const [expandedStep, setExpandedStep] = useState<string | null>(null);
  const wsRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!workflowId) return;

    setOverallStatus("running");

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws/workflow/${workflowId}`;

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as {
            type: string;
            nodeId: string;
            status: StepExecution["status"];
            output?: string;
            error?: string;
            timestamp?: string;
            duration?: number;
          };

          switch (data.type) {
            case "step_start":
              setSteps((prev) =>
                prev.map((s) =>
                  s.nodeId === data.nodeId
                    ? { ...s, status: "running", startedAt: data.timestamp }
                    : s
                )
              );
              break;

            case "step_complete":
              setSteps((prev) =>
                prev.map((s) =>
                  s.nodeId === data.nodeId
                    ? {
                        ...s,
                        status: data.status,
                        completedAt: data.timestamp,
                        output: data.output,
                        error: data.error,
                        duration: data.duration,
                      }
                    : s
                )
              );
              break;

            case "workflow_complete":
              setOverallStatus("success");
              break;

            case "workflow_error":
              setOverallStatus("error");
              break;
          }
        } catch {
          // Ignore parse errors
        }
      };

      ws.onerror = () => {
        setOverallStatus("error");
      };

      ws.onclose = () => {
        if (overallStatus === "running") {
          setOverallStatus("error");
        }
      };
    } catch {
      setOverallStatus("error");
    }

    return () => {
      wsRef.current?.close();
    };
  }, [workflowId]);

  const getStatusIcon = (status: StepExecution["status"]) => {
    switch (status) {
      case "pending":
        return <Clock className="h-4 w-4 text-muted-foreground" />;
      case "running":
        return <Loader2 className="h-4 w-4 animate-spin text-primary" />;
      case "success":
        return <CheckCircle className="h-4 w-4 text-green-400" />;
      case "error":
        return <XCircle className="h-4 w-4 text-red-400" />;
      case "skipped":
        return <Clock className="h-4 w-4 text-yellow-400" />;
    }
  };

  const getStatusColor = (status: StepExecution["status"]) => {
    switch (status) {
      case "pending":
        return "border-border";
      case "running":
        return "border-primary bg-primary/5";
      case "success":
        return "border-green-500/30 bg-green-500/5";
      case "error":
        return "border-red-500/30 bg-red-500/5";
      case "skipped":
        return "border-yellow-500/30 bg-yellow-500/5";
    }
  };

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div className="flex items-center gap-3">
          <h3 className="text-sm font-semibold">Workflow Execution</h3>
          {overallStatus === "running" && (
            <span className="flex items-center gap-1 text-xs text-primary">
              <Loader2 className="h-3 w-3 animate-spin" />
              Running...
            </span>
          )}
          {overallStatus === "success" && (
            <span className="flex items-center gap-1 text-xs text-green-400">
              <CheckCircle className="h-3 w-3" />
              Complete
            </span>
          )}
          {overallStatus === "error" && (
            <span className="flex items-center gap-1 text-xs text-red-400">
              <XCircle className="h-3 w-3" />
              Failed
            </span>
          )}
        </div>
        <button
          onClick={onClose}
          className="rounded p-1 hover:bg-accent"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Steps */}
      <div className="flex-1 overflow-auto p-4 space-y-2">
        {steps.map((step, idx) => (
          <div
            key={step.nodeId}
            className={`rounded-md border ${getStatusColor(step.status)}`}
          >
            <button
              onClick={() =>
                setExpandedStep(
                  expandedStep === step.nodeId ? null : step.nodeId
                )
              }
              className="flex w-full items-center gap-3 p-3 text-left"
            >
              {/* Step number */}
              <div className="flex h-6 w-6 items-center justify-center rounded-full bg-muted text-xs font-bold">
                {idx + 1}
              </div>

              {/* Status icon */}
              {getStatusIcon(step.status)}

              {/* Step name */}
              <span className="flex-1 text-sm font-medium">
                {step.nodeName}
              </span>

              {/* Duration */}
              {step.duration !== undefined && (
                <span className="text-xs text-muted-foreground">
                  {step.duration < 1000
                    ? `${step.duration}ms`
                    : `${(step.duration / 1000).toFixed(1)}s`}
                </span>
              )}

              {/* Expand indicator */}
              {expandedStep === step.nodeId ? (
                <ChevronDown className="h-4 w-4 text-muted-foreground" />
              ) : (
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              )}
            </button>

            {/* Expanded content */}
            {expandedStep === step.nodeId && (
              <div className="border-t border-border px-4 py-3 space-y-2">
                {step.startedAt && (
                  <div className="text-xs text-muted-foreground">
                    Started: {new Date(step.startedAt).toLocaleTimeString()}
                  </div>
                )}
                {step.completedAt && (
                  <div className="text-xs text-muted-foreground">
                    Completed:{" "}
                    {new Date(step.completedAt).toLocaleTimeString()}
                  </div>
                )}
                {step.output && (
                  <div>
                    <label className="text-xs font-medium text-muted-foreground">
                      Output:
                    </label>
                    <pre className="mt-1 overflow-x-auto rounded bg-[#1e1e1e] p-2 text-xs font-mono">
                      {step.output}
                    </pre>
                  </div>
                )}
                {step.error && (
                  <div>
                    <label className="text-xs font-medium text-destructive">
                      Error:
                    </label>
                    <pre className="mt-1 overflow-x-auto rounded bg-red-500/10 p-2 text-xs font-mono text-red-400">
                      {step.error}
                    </pre>
                  </div>
                )}
                {step.status === "pending" && (
                  <p className="text-xs text-muted-foreground italic">
                    Waiting to execute...
                  </p>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

"use client";

import React, { useState, useCallback } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { WorkflowCanvas, type WorkflowNode, type WorkflowEdge } from "@/components/workflow/WorkflowCanvas";
import { NodePalette } from "@/components/workflow/NodePalette";
import { WorkflowRunner } from "@/components/workflow/WorkflowRunner";
import { Save, Play, Plus, Trash2, FolderOpen } from "lucide-react";

interface Workflow {
  id: string;
  name: string;
  description: string;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  createdAt: string;
  updatedAt: string;
}

export default function WorkflowsPage() {
  const queryClient = useQueryClient();
  const [selectedWorkflow, setSelectedWorkflow] = useState<Workflow | null>(null);
  const [nodes, setNodes] = useState<WorkflowNode[]>([]);
  const [edges, setEdges] = useState<WorkflowEdge[]>([]);
  const [isRunning, setIsRunning] = useState(false);
  const [showRunner, setShowRunner] = useState(false);
  const [propertiesNode, setPropertiesNode] = useState<WorkflowNode | null>(null);

  const { data: workflows, isLoading } = useQuery<Workflow[]>({
    queryKey: ["workflows"],
    queryFn: async () => {
      const res = await fetch("/api/workflows");
      if (!res.ok) throw new Error("Failed to fetch workflows");
      return res.json();
    },
  });

  const saveMutation = useMutation({
    mutationFn: async (workflow: {
      id?: string;
      name: string;
      description: string;
      nodes: WorkflowNode[];
      edges: WorkflowEdge[];
    }) => {
      const method = workflow.id ? "PUT" : "POST";
      const url = workflow.id
        ? `/api/workflows/${workflow.id}`
        : "/api/workflows";
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(workflow),
      });
      if (!res.ok) throw new Error("Failed to save workflow");
      return res.json();
    },
    onSuccess: (data: Workflow) => {
      setSelectedWorkflow(data);
      queryClient.invalidateQueries({ queryKey: ["workflows"] });
    },
  });

  const runMutation = useMutation({
    mutationFn: async (workflowId: string) => {
      const res = await fetch(`/api/workflows/${workflowId}/run`, {
        method: "POST",
      });
      if (!res.ok) throw new Error("Failed to run workflow");
      return res.json();
    },
  });

  const handleSave = useCallback(() => {
    const name = selectedWorkflow?.name ?? "Untitled Workflow";
    const description = selectedWorkflow?.description ?? "";
    saveMutation.mutate({
      id: selectedWorkflow?.id,
      name,
      description,
      nodes,
      edges,
    });
  }, [selectedWorkflow, nodes, edges, saveMutation]);

  const handleRun = useCallback(async () => {
    if (!selectedWorkflow?.id) {
      handleSave();
      return;
    }
    setIsRunning(true);
    setShowRunner(true);
    try {
      await runMutation.mutateAsync(selectedWorkflow.id);
    } finally {
      setIsRunning(false);
    }
  }, [selectedWorkflow, runMutation, handleSave]);

  const handleLoadWorkflow = (workflow: Workflow) => {
    setSelectedWorkflow(workflow);
    setNodes(workflow.nodes);
    setEdges(workflow.edges);
    setShowRunner(false);
  };

  const handleNewWorkflow = () => {
    setSelectedWorkflow(null);
    setNodes([]);
    setEdges([]);
    setShowRunner(false);
  };

  const handleNodeSelect = (node: WorkflowNode | null) => {
    setPropertiesNode(node);
  };

  const handleAddNode = (nodeType: string, nodeData: Record<string, unknown>) => {
    const newNode: WorkflowNode = {
      id: `node-${Date.now()}`,
      type: nodeType,
      position: { x: 250, y: nodes.length * 100 + 50 },
      data: nodeData,
    };
    setNodes((prev) => [...prev, newNode]);
  };

  return (
    <div className="flex h-full flex-col">
      {/* Top Toolbar */}
      <div className="flex h-12 items-center justify-between border-b border-border bg-card px-4">
        <div className="flex items-center gap-4">
          <h1 className="text-sm font-semibold">Workflow Editor</h1>
          {selectedWorkflow && (
            <span className="text-sm text-muted-foreground">
              {selectedWorkflow.name}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleNewWorkflow}
            className="flex items-center gap-1 rounded-md px-3 py-1.5 text-xs hover:bg-accent"
          >
            <Plus className="h-3.5 w-3.5" />
            New
          </button>
          <button
            onClick={handleSave}
            disabled={saveMutation.isPending}
            className="flex items-center gap-1 rounded-md bg-secondary px-3 py-1.5 text-xs hover:bg-secondary/80 disabled:opacity-50"
          >
            <Save className="h-3.5 w-3.5" />
            {saveMutation.isPending ? "Saving..." : "Save"}
          </button>
          <button
            onClick={handleRun}
            disabled={isRunning || nodes.length === 0}
            className="flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-xs text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            <Play className="h-3.5 w-3.5" />
            {isRunning ? "Running..." : "Run"}
          </button>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Left - Node Palette + Workflow List */}
        <div className="w-64 flex-shrink-0 border-r border-border overflow-auto">
          <div className="border-b border-border p-3">
            <h3 className="text-xs font-semibold uppercase text-muted-foreground">
              Saved Workflows
            </h3>
            <div className="mt-2 space-y-1">
              {isLoading ? (
                <div className="animate-pulse space-y-1">
                  {[1, 2, 3].map((i) => (
                    <div key={i} className="h-8 rounded bg-muted" />
                  ))}
                </div>
              ) : workflows && workflows.length > 0 ? (
                workflows.map((wf) => (
                  <button
                    key={wf.id}
                    onClick={() => handleLoadWorkflow(wf)}
                    className={`flex w-full items-center gap-2 rounded px-2 py-1.5 text-xs text-left hover:bg-accent ${
                      selectedWorkflow?.id === wf.id ? "bg-accent" : ""
                    }`}
                  >
                    <FolderOpen className="h-3 w-3 flex-shrink-0" />
                    <span className="truncate">{wf.name}</span>
                  </button>
                ))
              ) : (
                <p className="text-xs text-muted-foreground">No workflows</p>
              )}
            </div>
          </div>
          <NodePalette onAddNode={handleAddNode} />
        </div>

        {/* Center - Canvas */}
        <div className="flex-1 overflow-hidden">
          {showRunner ? (
            <WorkflowRunner
              workflowId={selectedWorkflow?.id ?? ""}
              nodes={nodes}
              onClose={() => setShowRunner(false)}
            />
          ) : (
            <WorkflowCanvas
              nodes={nodes}
              edges={edges}
              onNodesChange={setNodes}
              onEdgesChange={setEdges}
              onNodeSelect={handleNodeSelect}
            />
          )}
        </div>

        {/* Right - Properties Panel */}
        {propertiesNode && !showRunner && (
          <div className="w-72 flex-shrink-0 border-l border-border overflow-auto p-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold">Properties</h3>
              <button
                onClick={() => {
                  setNodes((prev) =>
                    prev.filter((n) => n.id !== propertiesNode.id)
                  );
                  setPropertiesNode(null);
                }}
                className="rounded p-1 text-destructive hover:bg-destructive/10"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </div>
            <div className="mt-4 space-y-3">
              <div>
                <label className="block text-xs font-medium text-muted-foreground">
                  Node ID
                </label>
                <p className="mt-0.5 text-sm font-mono">{propertiesNode.id}</p>
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground">
                  Type
                </label>
                <p className="mt-0.5 text-sm">{propertiesNode.type}</p>
              </div>
              <div>
                <label className="block text-xs font-medium text-muted-foreground">
                  Label
                </label>
                <input
                  className="mt-0.5 w-full rounded border border-input bg-background px-2 py-1 text-sm"
                  value={(propertiesNode.data.label as string) ?? ""}
                  onChange={(e) => {
                    const updated = {
                      ...propertiesNode,
                      data: { ...propertiesNode.data, label: e.target.value },
                    };
                    setPropertiesNode(updated);
                    setNodes((prev) =>
                      prev.map((n) => (n.id === updated.id ? updated : n))
                    );
                  }}
                />
              </div>
              {propertiesNode.data.config != null &&
                typeof propertiesNode.data.config === "object" ? (
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground">
                      Configuration
                    </label>
                    <textarea
                      className="mt-0.5 w-full rounded border border-input bg-background px-2 py-1 text-xs font-mono"
                      rows={6}
                      value={JSON.stringify(propertiesNode.data.config, null, 2)}
                      onChange={(e) => {
                        try {
                          const config = JSON.parse(e.target.value);
                          const updated = {
                            ...propertiesNode,
                            data: { ...propertiesNode.data, config },
                          };
                          setPropertiesNode(updated);
                          setNodes((prev) =>
                            prev.map((n) =>
                              n.id === updated.id ? updated : n
                            )
                          );
                        } catch {
                          // Invalid JSON, ignore
                        }
                      }}
                    />
                  </div>
                ) : null}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

"use client";

import React, { useCallback, useMemo } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  addEdge,
  useNodesState,
  useEdgesState,
  type Connection,
  type Node,
  type Edge,
  type NodeMouseHandler,
  MarkerType,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

export interface WorkflowNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
}

export interface WorkflowEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
}

interface WorkflowCanvasProps {
  nodes: WorkflowNode[];
  edges: WorkflowEdge[];
  onNodesChange: (nodes: WorkflowNode[]) => void;
  onEdgesChange: (edges: WorkflowEdge[]) => void;
  onNodeSelect: (node: WorkflowNode | null) => void;
}

const nodeColors: Record<string, string> = {
  trigger: "#3b82f6",
  "mcp-tool": "#8b5cf6",
  action: "#10b981",
  condition: "#f59e0b",
  loop: "#ec4899",
  transform: "#06b6d4",
  output: "#6366f1",
};

function getNodeStyle(type: string) {
  const color = nodeColors[type] ?? "#6b7280";
  return {
    background: `${color}15`,
    border: `2px solid ${color}`,
    borderRadius: type === "condition" ? "0px" : type === "loop" ? "16px" : "8px",
    padding: "10px 16px",
    color: "#e5e7eb",
    fontSize: "12px",
    fontWeight: 500,
    minWidth: "140px",
    textAlign: "center" as const,
  };
}

export function WorkflowCanvas({
  nodes: inputNodes,
  edges: inputEdges,
  onNodesChange: onNodesUpdate,
  onEdgesChange: onEdgesUpdate,
  onNodeSelect,
}: WorkflowCanvasProps) {
  const flowNodes: Node[] = useMemo(
    () =>
      inputNodes.map((n) => ({
        id: n.id,
        type: "default",
        position: n.position,
        data: { label: (n.data.label as string) ?? n.type },
        style: getNodeStyle(n.type),
        draggable: true,
      })),
    [inputNodes]
  );

  const flowEdges: Edge[] = useMemo(
    () =>
      inputEdges.map((e) => ({
        id: e.id,
        source: e.source,
        target: e.target,
        label: e.label,
        style: { stroke: "#6b7280" },
        markerEnd: { type: MarkerType.ArrowClosed, color: "#6b7280" },
        animated: false,
      })),
    [inputEdges]
  );

  const [nodes, setNodes, onNodesChange] = useNodesState(flowNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(flowEdges);

  // Sync flow state back when flow updates
  React.useEffect(() => {
    setNodes(flowNodes);
  }, [flowNodes, setNodes]);

  React.useEffect(() => {
    setEdges(flowEdges);
  }, [flowEdges, setEdges]);

  const onConnect = useCallback(
    (connection: Connection) => {
      const newEdge: WorkflowEdge = {
        id: `edge-${connection.source}-${connection.target}`,
        source: connection.source!,
        target: connection.target!,
      };
      onEdgesUpdate([...inputEdges, newEdge]);
      setEdges((eds) => addEdge(connection, eds));
    },
    [inputEdges, onEdgesUpdate, setEdges]
  );

  const onNodeClick: NodeMouseHandler = useCallback(
    (_event, node) => {
      const workflowNode = inputNodes.find((n) => n.id === node.id);
      onNodeSelect(workflowNode ?? null);
    },
    [inputNodes, onNodeSelect]
  );

  const onNodeDragStop = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      const updated = inputNodes.map((n) =>
        n.id === node.id ? { ...n, position: node.position } : n
      );
      onNodesUpdate(updated);
    },
    [inputNodes, onNodesUpdate]
  );

  const onPaneClick = useCallback(() => {
    onNodeSelect(null);
  }, [onNodeSelect]);

  return (
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        onNodeDragStop={onNodeDragStop}
        onPaneClick={onPaneClick}
        fitView
        snapToGrid
        snapGrid={[16, 16]}
        connectionMode={"loose" as any}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="#374151" gap={16} />
        <Controls position="bottom-right" />
        <MiniMap style={{ background: "#111827" }} />
      </ReactFlow>
    </div>
  );
}

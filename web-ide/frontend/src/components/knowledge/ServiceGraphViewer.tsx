"use client";

import React, { useCallback, useState, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type NodeMouseHandler,
  MarkerType,
  Panel,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { AlertTriangle, Info, Zap } from "lucide-react";

interface ServiceNode {
  id: string;
  name: string;
  type: "service" | "database" | "queue" | "external";
  status: "healthy" | "degraded" | "down";
  team: string;
  dependencies: string[];
}

interface ServiceGraph {
  services: ServiceNode[];
}

function getNodeColor(type: ServiceNode["type"], status: ServiceNode["status"]) {
  const statusColors = {
    healthy: { border: "#22c55e", bg: "#052e16" },
    degraded: { border: "#eab308", bg: "#422006" },
    down: { border: "#ef4444", bg: "#450a0a" },
  };
  return statusColors[status];
}

function getNodeStyle(type: ServiceNode["type"], status: ServiceNode["status"]) {
  const colors = getNodeColor(type, status);
  return {
    background: colors.bg,
    border: `2px solid ${colors.border}`,
    borderRadius: type === "database" ? "12px" : type === "queue" ? "20px" : "8px",
    padding: "12px 16px",
    color: "#e5e7eb",
    fontSize: "12px",
    fontWeight: 500,
    minWidth: "120px",
    textAlign: "center" as const,
  };
}

export function ServiceGraphViewer() {
  const [selectedService, setSelectedService] = useState<ServiceNode | null>(null);
  const [impactMode, setImpactMode] = useState(false);
  const [highlightedNodes, setHighlightedNodes] = useState<Set<string>>(new Set());

  const { data: graph, isLoading } = useQuery<ServiceGraph>({
    queryKey: ["service-graph"],
    queryFn: async () => {
      const res = await fetch("/api/knowledge/services");
      if (!res.ok) throw new Error("Failed to fetch service graph");
      return res.json();
    },
  });

  const { flowNodes, flowEdges } = useMemo(() => {
    if (!graph) return { flowNodes: [], flowEdges: [] };

    const cols = 4;
    const xSpacing = 220;
    const ySpacing = 120;

    const flowNodes: Node[] = graph.services.map((svc, idx) => ({
      id: svc.id,
      position: {
        x: (idx % cols) * xSpacing + 50,
        y: Math.floor(idx / cols) * ySpacing + 50,
      },
      data: { label: svc.name },
      style: {
        ...getNodeStyle(svc.type, svc.status),
        opacity: highlightedNodes.size === 0 || highlightedNodes.has(svc.id) ? 1 : 0.3,
      },
    }));

    const flowEdges: Edge[] = [];
    graph.services.forEach((svc) => {
      svc.dependencies.forEach((depId) => {
        flowEdges.push({
          id: `${svc.id}-${depId}`,
          source: svc.id,
          target: depId,
          animated: highlightedNodes.has(svc.id) && highlightedNodes.has(depId),
          style: {
            stroke:
              highlightedNodes.size === 0 ||
              (highlightedNodes.has(svc.id) && highlightedNodes.has(depId))
                ? "#6b7280"
                : "#374151",
            strokeWidth: highlightedNodes.has(svc.id) && highlightedNodes.has(depId) ? 2 : 1,
          },
          markerEnd: { type: MarkerType.ArrowClosed, color: "#6b7280" },
        });
      });
    });

    return { flowNodes, flowEdges };
  }, [graph, highlightedNodes]);

  const [nodes, setNodes, onNodesChange] = useNodesState(flowNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(flowEdges);

  // Update nodes/edges when data changes
  React.useEffect(() => {
    setNodes(flowNodes);
    setEdges(flowEdges);
  }, [flowNodes, flowEdges, setNodes, setEdges]);

  const findImpactedServices = useCallback(
    (serviceId: string): Set<string> => {
      if (!graph) return new Set();
      const impacted = new Set<string>([serviceId]);
      const queue = [serviceId];

      while (queue.length > 0) {
        const current = queue.shift()!;
        // Find services that depend on current
        graph.services.forEach((svc) => {
          if (svc.dependencies.includes(current) && !impacted.has(svc.id)) {
            impacted.add(svc.id);
            queue.push(svc.id);
          }
        });
      }

      // Also include direct dependencies
      const directService = graph.services.find((s) => s.id === serviceId);
      if (directService) {
        directService.dependencies.forEach((dep) => impacted.add(dep));
      }

      return impacted;
    },
    [graph]
  );

  const handleNodeClick: NodeMouseHandler = useCallback(
    (_event, node) => {
      const service = graph?.services.find((s) => s.id === node.id);
      setSelectedService(service ?? null);

      if (impactMode && service) {
        const impacted = findImpactedServices(service.id);
        setHighlightedNodes(impacted);
      } else {
        setHighlightedNodes(new Set());
      }
    },
    [graph, impactMode, findImpactedServices]
  );

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="flex h-full">
      {/* Graph */}
      <div className="flex-1">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={handleNodeClick}
          fitView
          proOptions={{ hideAttribution: true }}
        >
          <Background color="#374151" gap={20} />
          <Controls position="bottom-left" />
          <MiniMap
            nodeColor={(node) => {
              const svc = graph?.services.find((s) => s.id === node.id);
              if (!svc) return "#6b7280";
              return getNodeColor(svc.type, svc.status).border;
            }}
            style={{ background: "#111827" }}
          />
          <Panel position="top-left">
            <button
              onClick={() => {
                setImpactMode(!impactMode);
                if (impactMode) {
                  setHighlightedNodes(new Set());
                }
              }}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium ${
                impactMode
                  ? "bg-yellow-500/20 text-yellow-400 border border-yellow-500/30"
                  : "bg-card border border-border text-muted-foreground hover:text-foreground"
              }`}
            >
              <Zap className="h-3.5 w-3.5" />
              Impact Mode {impactMode ? "ON" : "OFF"}
            </button>
          </Panel>
        </ReactFlow>
      </div>

      {/* Service Details */}
      {selectedService && (
        <div className="w-72 flex-shrink-0 border-l border-border overflow-auto p-4">
          <div className="flex items-start gap-3">
            <div
              className={`mt-0.5 h-3 w-3 rounded-full ${
                selectedService.status === "healthy"
                  ? "bg-green-400"
                  : selectedService.status === "degraded"
                    ? "bg-yellow-400"
                    : "bg-red-400"
              }`}
            />
            <div>
              <h3 className="font-semibold">{selectedService.name}</h3>
              <p className="text-xs text-muted-foreground capitalize">
                {selectedService.type} - {selectedService.status}
              </p>
            </div>
          </div>

          <div className="mt-4 space-y-3">
            <div>
              <label className="text-xs font-medium text-muted-foreground">
                Team
              </label>
              <p className="text-sm">{selectedService.team}</p>
            </div>

            <div>
              <label className="text-xs font-medium text-muted-foreground">
                Dependencies ({selectedService.dependencies.length})
              </label>
              <div className="mt-1 space-y-1">
                {selectedService.dependencies.length > 0 ? (
                  selectedService.dependencies.map((depId) => {
                    const dep = graph?.services.find((s) => s.id === depId);
                    return (
                      <div
                        key={depId}
                        className="flex items-center gap-2 text-sm"
                      >
                        <div
                          className={`h-2 w-2 rounded-full ${
                            dep?.status === "healthy"
                              ? "bg-green-400"
                              : dep?.status === "degraded"
                                ? "bg-yellow-400"
                                : "bg-red-400"
                          }`}
                        />
                        {dep?.name ?? depId}
                      </div>
                    );
                  })
                ) : (
                  <p className="text-xs text-muted-foreground">
                    No dependencies
                  </p>
                )}
              </div>
            </div>

            {impactMode && highlightedNodes.size > 0 && (
              <div className="rounded-md border border-yellow-500/30 bg-yellow-500/10 p-3">
                <div className="flex items-center gap-2 text-xs font-medium text-yellow-400">
                  <AlertTriangle className="h-3.5 w-3.5" />
                  Impact Analysis
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {highlightedNodes.size - 1} services would be affected if{" "}
                  {selectedService.name} goes down.
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

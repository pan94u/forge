"use client";

import React from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Zap,
  Wrench,
  Play,
  GitBranch,
  Repeat,
  ArrowRightLeft,
  FileOutput,
  Puzzle,
} from "lucide-react";

interface McpTool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
}

interface NodeTemplate {
  type: string;
  label: string;
  description: string;
  icon: React.ElementType;
  category: "trigger" | "mcp" | "action" | "control";
  defaultConfig?: Record<string, unknown>;
}

const builtInNodes: NodeTemplate[] = [
  {
    type: "trigger",
    label: "Trigger",
    description: "Start the workflow",
    icon: Zap,
    category: "trigger",
    defaultConfig: { triggerType: "manual" },
  },
  {
    type: "action",
    label: "Custom Action",
    description: "Run a custom action",
    icon: Play,
    category: "action",
    defaultConfig: { script: "" },
  },
  {
    type: "condition",
    label: "If / Else",
    description: "Branch based on condition",
    icon: GitBranch,
    category: "control",
    defaultConfig: { condition: "", trueBranch: "", falseBranch: "" },
  },
  {
    type: "loop",
    label: "Loop",
    description: "Iterate over items",
    icon: Repeat,
    category: "control",
    defaultConfig: { items: "[]", variable: "item" },
  },
  {
    type: "transform",
    label: "Transform",
    description: "Transform data between steps",
    icon: ArrowRightLeft,
    category: "action",
    defaultConfig: { expression: "" },
  },
  {
    type: "output",
    label: "Output",
    description: "Workflow output/result",
    icon: FileOutput,
    category: "action",
    defaultConfig: { format: "json" },
  },
];

const categoryLabels: Record<string, string> = {
  trigger: "Triggers",
  mcp: "MCP Tools",
  action: "Actions",
  control: "Control Flow",
};

const categoryOrder = ["trigger", "mcp", "action", "control"];

interface NodePaletteProps {
  onAddNode: (nodeType: string, nodeData: Record<string, unknown>) => void;
}

export function NodePalette({ onAddNode }: NodePaletteProps) {
  const { data: mcpTools } = useQuery<McpTool[]>({
    queryKey: ["mcp-tools"],
    queryFn: async () => {
      const res = await fetch("/api/mcp/tools");
      if (!res.ok) return [];
      return res.json();
    },
  });

  const mcpNodeTemplates: NodeTemplate[] = (mcpTools ?? []).map((tool) => ({
    type: "mcp-tool",
    label: tool.name,
    description: tool.description,
    icon: Wrench,
    category: "mcp" as const,
    defaultConfig: {
      toolName: tool.name,
      inputSchema: tool.inputSchema,
      parameters: {},
    },
  }));

  const allNodes = [...builtInNodes, ...mcpNodeTemplates];

  const groupedNodes = categoryOrder.reduce(
    (acc, category) => {
      const nodes = allNodes.filter((n) => n.category === category);
      if (nodes.length > 0) {
        acc[category] = nodes;
      }
      return acc;
    },
    {} as Record<string, NodeTemplate[]>
  );

  const handleDragStart = (
    e: React.DragEvent,
    template: NodeTemplate
  ) => {
    e.dataTransfer.setData(
      "application/forge-node",
      JSON.stringify({
        type: template.type,
        label: template.label,
        config: template.defaultConfig,
      })
    );
    e.dataTransfer.effectAllowed = "move";
  };

  return (
    <div className="p-3">
      <h3 className="mb-3 text-xs font-semibold uppercase text-muted-foreground">
        Node Palette
      </h3>
      <div className="space-y-4">
        {categoryOrder.map((category) => {
          const nodes = groupedNodes[category];
          if (!nodes) return null;

          return (
            <div key={category}>
              <h4 className="mb-2 text-xs font-medium text-muted-foreground">
                {categoryLabels[category]}
              </h4>
              <div className="space-y-1">
                {nodes.map((template, idx) => (
                  <button
                    key={`${template.type}-${idx}`}
                    className="flex w-full items-center gap-2 rounded-md border border-border p-2 text-left text-xs transition-colors hover:border-primary/50 hover:bg-accent cursor-grab active:cursor-grabbing"
                    draggable
                    onDragStart={(e) => handleDragStart(e, template)}
                    onClick={() =>
                      onAddNode(template.type, {
                        label: template.label,
                        config: template.defaultConfig,
                      })
                    }
                  >
                    <template.icon className="h-3.5 w-3.5 flex-shrink-0 text-muted-foreground" />
                    <div className="flex-1 min-w-0">
                      <p className="font-medium truncate">{template.label}</p>
                      <p className="text-muted-foreground truncate">
                        {template.description}
                      </p>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          );
        })}

        {mcpTools && mcpTools.length === 0 && (
          <div className="rounded-md border border-dashed border-border p-3 text-center">
            <Puzzle className="mx-auto h-5 w-5 text-muted-foreground/50" />
            <p className="mt-1 text-xs text-muted-foreground">
              No MCP tools discovered. Connect an MCP server to see available
              tools.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

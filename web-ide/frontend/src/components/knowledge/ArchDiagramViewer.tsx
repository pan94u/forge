"use client";

import React, { useEffect, useRef, useState, useCallback } from "react";
import { useQuery } from "@tanstack/react-query";
import { ZoomIn, ZoomOut, Maximize2, RotateCcw, Download } from "lucide-react";

interface ArchDiagram {
  id: string;
  title: string;
  description: string;
  mermaidCode: string;
  updatedAt: string;
}

export function ArchDiagramViewer() {
  const containerRef = useRef<HTMLDivElement>(null);
  const [selectedDiagram, setSelectedDiagram] = useState<ArchDiagram | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });
  const [renderError, setRenderError] = useState<string | null>(null);

  const { data: diagrams, isLoading } = useQuery<ArchDiagram[]>({
    queryKey: ["arch-diagrams"],
    queryFn: async () => {
      const res = await fetch("/api/knowledge/diagrams");
      if (!res.ok) throw new Error("Failed to fetch diagrams");
      return res.json();
    },
  });

  // Render mermaid diagram
  useEffect(() => {
    if (!selectedDiagram || !containerRef.current) return;

    const renderDiagram = async () => {
      try {
        const mermaid = (await import("mermaid")).default;
        mermaid.initialize({
          startOnLoad: false,
          theme: "dark",
          securityLevel: "loose",
          fontFamily: "Inter, system-ui, sans-serif",
        });

        const container = containerRef.current;
        if (!container) return;

        container.innerHTML = "";
        const { svg } = await mermaid.render(
          `mermaid-${selectedDiagram.id}`,
          selectedDiagram.mermaidCode
        );
        container.innerHTML = svg;
        setRenderError(null);
      } catch (err) {
        console.error("Mermaid render error:", err);
        setRenderError(
          err instanceof Error ? err.message : "Failed to render diagram"
        );
      }
    };

    renderDiagram();
  }, [selectedDiagram]);

  const handleZoomIn = () => setZoom((z) => Math.min(z + 0.25, 3));
  const handleZoomOut = () => setZoom((z) => Math.max(z - 0.25, 0.25));
  const handleReset = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (e.button === 0) {
        setIsPanning(true);
        setPanStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
      }
    },
    [pan]
  );

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (isPanning) {
        setPan({
          x: e.clientX - panStart.x,
          y: e.clientY - panStart.y,
        });
      }
    },
    [isPanning, panStart]
  );

  const handleMouseUp = useCallback(() => {
    setIsPanning(false);
  }, []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? -0.1 : 0.1;
    setZoom((z) => Math.max(0.25, Math.min(3, z + delta)));
  }, []);

  const handleExportSvg = () => {
    if (!containerRef.current) return;
    const svgEl = containerRef.current.querySelector("svg");
    if (!svgEl) return;
    const svgData = new XMLSerializer().serializeToString(svgEl);
    const blob = new Blob([svgData], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${selectedDiagram?.title ?? "diagram"}.svg`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex h-full gap-4">
      {/* Diagram List */}
      <div className="w-64 flex-shrink-0 space-y-2">
        <h3 className="text-sm font-semibold">Architecture Diagrams</h3>
        {isLoading ? (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-16 animate-pulse rounded-md bg-muted" />
            ))}
          </div>
        ) : diagrams && diagrams.length > 0 ? (
          diagrams.map((diagram) => (
            <button
              key={diagram.id}
              onClick={() => setSelectedDiagram(diagram)}
              className={`w-full rounded-md border p-3 text-left transition-colors ${
                selectedDiagram?.id === diagram.id
                  ? "border-primary bg-primary/5"
                  : "border-border hover:border-primary/50"
              }`}
            >
              <h4 className="text-sm font-medium">{diagram.title}</h4>
              <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
                {diagram.description}
              </p>
            </button>
          ))
        ) : (
          <p className="text-sm text-muted-foreground">No diagrams available</p>
        )}
      </div>

      {/* Diagram Viewer */}
      <div className="flex flex-1 flex-col rounded-md border border-border">
        {selectedDiagram ? (
          <>
            {/* Toolbar */}
            <div className="flex items-center justify-between border-b border-border px-3 py-2">
              <span className="text-sm font-medium">
                {selectedDiagram.title}
              </span>
              <div className="flex items-center gap-1">
                <button
                  onClick={handleZoomOut}
                  className="rounded p-1 hover:bg-accent"
                  title="Zoom out"
                >
                  <ZoomOut className="h-4 w-4" />
                </button>
                <span className="px-2 text-xs text-muted-foreground">
                  {Math.round(zoom * 100)}%
                </span>
                <button
                  onClick={handleZoomIn}
                  className="rounded p-1 hover:bg-accent"
                  title="Zoom in"
                >
                  <ZoomIn className="h-4 w-4" />
                </button>
                <button
                  onClick={handleReset}
                  className="rounded p-1 hover:bg-accent"
                  title="Reset view"
                >
                  <RotateCcw className="h-4 w-4" />
                </button>
                <button
                  onClick={handleExportSvg}
                  className="rounded p-1 hover:bg-accent"
                  title="Export SVG"
                >
                  <Download className="h-4 w-4" />
                </button>
              </div>
            </div>

            {/* Canvas */}
            <div
              className="flex-1 overflow-hidden cursor-grab active:cursor-grabbing"
              onMouseDown={handleMouseDown}
              onMouseMove={handleMouseMove}
              onMouseUp={handleMouseUp}
              onMouseLeave={handleMouseUp}
              onWheel={handleWheel}
            >
              {renderError ? (
                <div className="flex h-full items-center justify-center p-8">
                  <div className="text-center">
                    <p className="text-destructive">
                      Failed to render diagram
                    </p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {renderError}
                    </p>
                  </div>
                </div>
              ) : (
                <div
                  ref={containerRef}
                  className="flex h-full items-center justify-center [&_svg]:max-w-none"
                  style={{
                    transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
                    transformOrigin: "center center",
                  }}
                />
              )}
            </div>
          </>
        ) : (
          <div className="flex h-full items-center justify-center text-muted-foreground">
            <div className="text-center">
              <Maximize2 className="mx-auto h-8 w-8 opacity-50" />
              <p className="mt-3 text-sm">Select a diagram to view</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

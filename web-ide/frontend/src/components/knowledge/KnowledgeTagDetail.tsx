"use client";

import React, { useState, useMemo, useCallback } from "react";
import { KnowledgeTagView, knowledgeTagApi } from "@/lib/knowledge-tag-api";
import { MonacoEditor } from "@/components/editor/MonacoEditor";
import { Pencil, Save, X, Clock, List, CheckCircle, RotateCw, FileQuestion, Loader2 } from "lucide-react";

interface KnowledgeTagDetailProps {
  tag: KnowledgeTagView;
  onUpdated: (tag: KnowledgeTagView) => void;
  onReExtract?: (tagId: string) => void;
  reExtracting?: boolean;
}

interface TocEntry {
  id: string;
  text: string;
  level: number;
}

function extractToc(markdown: string): TocEntry[] {
  const headingRegex = /^(#{1,6})\s+(.+)$/gm;
  const entries: TocEntry[] = [];
  let match: RegExpExecArray | null;

  while ((match = headingRegex.exec(markdown)) !== null) {
    const level = match[1].length;
    const text = match[2].trim();
    const id = text
      .toLowerCase()
      .replace(/[^a-z0-9\u4e00-\u9fff]+/g, "-")
      .replace(/^-|-$/g, "");
    entries.push({ id, text, level });
  }

  return entries;
}

function MermaidBlock({ code, id }: { code: string; id: string }) {
  const containerRef = React.useRef<HTMLDivElement>(null);
  const viewportRef = React.useRef<HTMLDivElement>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [zoom, setZoom] = React.useState(1);
  const [fitZoom, setFitZoom] = React.useState(1);
  const [pan, setPan] = React.useState({ x: 0, y: 0 });
  const [dragging, setDragging] = React.useState(false);
  const [dragStart, setDragStart] = React.useState({ x: 0, y: 0 });
  const [containerHeight, setContainerHeight] = React.useState(320);

  React.useEffect(() => {
    let cancelled = false;
    // 切换图表时重置所有状态
    setLoading(true);
    setError(null);
    setZoom(1);
    setFitZoom(1);
    setPan({ x: 0, y: 0 });

    const safeId = `mermaid-kd-${id.replace(/[^a-zA-Z0-9]/g, "-")}`;
    import("mermaid").then(({ default: mermaid }) => {
      mermaid.initialize({ startOnLoad: false, theme: "dark", securityLevel: "loose" });
      mermaid
        .render(safeId, code)
        .then(({ svg }: { svg: string }) => {
          if (!cancelled && containerRef.current && viewportRef.current) {
            containerRef.current.innerHTML = svg;

            // 从 viewBox 计算自适应缩放和容器高度
            const svgEl = containerRef.current.querySelector("svg");
            if (svgEl) {
              const vb = svgEl.viewBox?.baseVal;
              if (vb && vb.width > 0 && vb.height > 0) {
                const vpWidth = viewportRef.current.clientWidth || 700;
                const fit = Math.min(1, (vpWidth - 32) / vb.width);
                const h = Math.min(560, Math.max(200, vb.height * fit + 32));
                setContainerHeight(h);
                setZoom(fit);
                setFitZoom(fit);
              }
            }
            setLoading(false);
          }
        })
        .catch((err: Error) => {
          if (!cancelled) {
            setError(err.message);
            setLoading(false);
          }
        });
    });
    return () => { cancelled = true; };
  }, [code, id]);

  const exportSvg = () => {
    const svgEl = containerRef.current?.querySelector("svg");
    if (!svgEl) return;
    const a = Object.assign(document.createElement("a"), {
      href: URL.createObjectURL(
        new Blob([new XMLSerializer().serializeToString(svgEl)], { type: "image/svg+xml" })
      ),
      download: `flow-diagram-${id}.svg`,
    });
    a.click();
    URL.revokeObjectURL(a.href);
  };

  return (
    <div className="my-4 rounded-md border border-border overflow-hidden">
      <div className="flex items-center justify-between border-b border-border px-3 py-1.5 bg-[#1a1a1a]">
        <span className="text-xs text-muted-foreground">mermaid</span>
        <div className="flex gap-1">
          <button
            onClick={() => setZoom((z) => Math.max(0.25, z - 0.25))}
            className="rounded px-1.5 py-0.5 text-xs text-muted-foreground hover:bg-accent"
          >
            −
          </button>
          <button
            onClick={() => { setZoom(fitZoom); setPan({ x: 0, y: 0 }); }}
            className="rounded px-1.5 py-0.5 text-xs text-muted-foreground hover:bg-accent"
            title="Reset to fit"
          >
            {Math.round(zoom * 100)}%
          </button>
          <button
            onClick={() => setZoom((z) => Math.min(3, z + 0.25))}
            className="rounded px-1.5 py-0.5 text-xs text-muted-foreground hover:bg-accent"
          >
            +
          </button>
          <button
            onClick={exportSvg}
            className="rounded px-1.5 py-0.5 text-xs text-muted-foreground hover:bg-accent"
          >
            SVG
          </button>
        </div>
      </div>
      <div
        ref={viewportRef}
        className="overflow-hidden bg-[#1e1e1e]"
        style={{ height: loading ? 120 : containerHeight }}
        onMouseDown={(e) => {
          if (error) return;
          setDragging(true);
          setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
        }}
        onMouseMove={(e) => {
          if (dragging) setPan({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y });
        }}
        onMouseUp={() => setDragging(false)}
        onMouseLeave={() => setDragging(false)}
      >
        {loading ? (
          <div className="flex h-full items-center justify-center">
            <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : error ? (
          <pre className="overflow-auto h-full p-4 text-xs text-destructive whitespace-pre-wrap">
            {error}
            {"\n\n--- source ---\n"}
            {code}
          </pre>
        ) : (
          <div
            ref={containerRef}
            className="flex h-full items-center justify-center [&_svg]:max-w-none"
            style={{
              transform: `translate(${pan.x}px,${pan.y}px) scale(${zoom})`,
              transformOrigin: "center center",
              cursor: dragging ? "grabbing" : "grab",
            }}
          />
        )}
      </div>
    </div>
  );
}

function renderDocMarkdown(content: string): React.ReactNode {
  const lines = content.split("\n");
  const elements: React.ReactNode[] = [];

  let inCodeBlock = false;
  let codeBlockLang = "";
  let codeLines: string[] = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    if (line.startsWith("```")) {
      if (inCodeBlock) {
        const codeContent = codeLines.join("\n");
        if (codeBlockLang === "mermaid") {
          elements.push(
            <MermaidBlock key={`mermaid-${i}`} code={codeContent} id={String(i)} />
          );
        } else {
          elements.push(
            <div key={`code-${i}`} className="my-4 rounded-md border border-border bg-[#1e1e1e]">
              <div className="border-b border-border px-3 py-1 text-xs text-muted-foreground">
                {codeBlockLang || "code"}
              </div>
              <pre className="overflow-x-auto p-4 text-sm">
                <code>{codeContent}</code>
              </pre>
            </div>
          );
        }
        codeLines = [];
        inCodeBlock = false;
        codeBlockLang = "";
      } else {
        inCodeBlock = true;
        codeBlockLang = line.slice(3).trim();
      }
      continue;
    }

    if (inCodeBlock) {
      codeLines.push(line);
      continue;
    }

    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const level = headingMatch[1].length;
      const text = headingMatch[2];
      const id = text
        .toLowerCase()
        .replace(/[^a-z0-9\u4e00-\u9fff]+/g, "-")
        .replace(/^-|-$/g, "");
      const Tag = `h${level}` as keyof React.JSX.IntrinsicElements;
      const sizes: Record<number, string> = {
        1: "text-2xl font-bold mt-8 mb-4",
        2: "text-xl font-bold mt-6 mb-3",
        3: "text-lg font-semibold mt-5 mb-2",
        4: "text-base font-semibold mt-4 mb-2",
        5: "text-sm font-medium mt-3 mb-1",
        6: "text-sm font-medium mt-2 mb-1",
      };
      elements.push(
        <Tag key={`h-${i}`} id={id} className={sizes[level]}>
          {text}
        </Tag>
      );
      continue;
    }

    if (/^---+$/.test(line.trim())) {
      elements.push(<hr key={`hr-${i}`} className="my-6 border-border" />);
      continue;
    }

    if (line.startsWith("> ")) {
      elements.push(
        <blockquote
          key={`bq-${i}`}
          className="my-2 border-l-4 border-primary/30 pl-4 italic text-muted-foreground"
        >
          {line.slice(2)}
        </blockquote>
      );
      continue;
    }

    const listMatch = line.match(/^(\s*)[-*]\s+(.+)$/);
    if (listMatch) {
      const indent = Math.floor(listMatch[1].length / 2);
      elements.push(
        <div
          key={`li-${i}`}
          className="flex items-start gap-2 py-0.5"
          style={{ paddingLeft: `${indent * 20 + 8}px` }}
        >
          <span className="mt-2 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-muted-foreground" />
          <span>{listMatch[2]}</span>
        </div>
      );
      continue;
    }

    if (line.trim() === "") {
      elements.push(<div key={`br-${i}`} className="h-2" />);
      continue;
    }

    elements.push(
      <p key={`p-${i}`} className="my-1 leading-relaxed">
        {line}
      </p>
    );
  }

  return <>{elements}</>;
}

export function KnowledgeTagDetail({ tag, onUpdated, onReExtract, reExtracting = false }: KnowledgeTagDetailProps) {
  const [editing, setEditing] = useState(false);
  const [editContent, setEditContent] = useState(tag.content);
  const [saving, setSaving] = useState(false);
  const [approving, setApproving] = useState(false);
  const [showToc, setShowToc] = useState(true);

  const toc = useMemo(() => extractToc(tag.content), [tag.content]);
  const renderedContent = useMemo(() => renderDocMarkdown(tag.content), [tag.content]);

  const handleEdit = useCallback(() => {
    setEditContent(tag.content);
    setEditing(true);
  }, [tag.content]);

  const handleCancel = useCallback(() => {
    setEditing(false);
    setEditContent(tag.content);
  }, [tag.content]);

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const updated = await knowledgeTagApi.updateTag(tag.id, {
        content: editContent,
      });
      onUpdated(updated);
      setEditing(false);
    } catch (err) {
      console.error("Failed to save tag:", err);
    } finally {
      setSaving(false);
    }
  }, [tag.id, editContent, onUpdated]);

  const handleApprove = useCallback(async () => {
    setApproving(true);
    try {
      const updated = await knowledgeTagApi.updateTag(tag.id, {
        status: "active",
      });
      onUpdated(updated);
    } catch (err) {
      console.error("Failed to approve tag:", err);
    } finally {
      setApproving(false);
    }
  }, [tag.id, onUpdated]);

  // Empty state
  if (tag.status === "empty") {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        <div className="text-center">
          <FileQuestion className="mx-auto h-12 w-12 opacity-50" />
          <p className="mt-4 text-lg font-medium">{tag.name}</p>
          <p className="mt-1 text-sm">
            Click &quot;Extract All&quot; to generate this document from codebase
          </p>
          {onReExtract && (
            <button
              onClick={() => onReExtract(tag.id)}
              disabled={reExtracting}
              className="mt-4 flex items-center gap-1.5 mx-auto rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {reExtracting ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <RotateCw className="h-4 w-4" />
              )}
              {reExtracting ? "Extracting..." : "Extract This Tag"}
            </button>
          )}
        </div>
      </div>
    );
  }

  // Not applicable state
  if (tag.status === "not_applicable") {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        <div className="text-center max-w-md">
          <FileQuestion className="mx-auto h-12 w-12 opacity-30" />
          <p className="mt-4 text-lg font-medium text-gray-400">{tag.name}</p>
          <p className="mt-2 text-sm">Not applicable to this codebase</p>
          {tag.description && (
            <p className="mt-2 text-xs text-gray-500 italic">{tag.description}</p>
          )}
          {onReExtract && (
            <button
              onClick={() => onReExtract(tag.id)}
              disabled={reExtracting}
              className="mt-4 flex items-center gap-1.5 mx-auto rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent disabled:opacity-50"
            >
              {reExtracting ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <RotateCw className="h-3.5 w-3.5" />
              )}
              {reExtracting ? "Extracting..." : "Re-extract"}
            </button>
          )}
        </div>
      </div>
    );
  }

  if (editing) {
    return (
      <div className="flex h-full flex-col">
        {/* Edit header */}
        <div className="flex items-center justify-between border-b border-border px-4 py-3">
          <div className="flex items-center gap-2">
            <Pencil className="h-4 w-4 text-primary" />
            <span className="text-sm font-medium">{tag.name}</span>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={handleCancel}
              className="flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm hover:bg-accent"
            >
              <X className="h-3.5 w-3.5" />
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={saving}
              className="flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              <Save className="h-3.5 w-3.5" />
              {saving ? "Saving..." : "Save"}
            </button>
          </div>
        </div>
        {/* Monaco Editor */}
        <div className="flex-1">
          <MonacoEditor
            value={editContent}
            onChange={(value) => setEditContent(value ?? "")}
            filePath="tag.md"
            readOnly={false}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full">
      {/* TOC */}
      {showToc && toc.length > 0 && (
        <div className="w-56 flex-shrink-0 overflow-auto border-r border-border p-4">
          <h4 className="mb-3 text-xs font-semibold uppercase text-muted-foreground">
            On this page
          </h4>
          <nav className="space-y-1">
            {toc.map((entry) => (
              <a
                key={entry.id}
                href={`#${entry.id}`}
                className="block truncate text-xs text-muted-foreground transition-colors hover:text-foreground"
                style={{ paddingLeft: `${(entry.level - 1) * 12}px` }}
              >
                {entry.text}
              </a>
            ))}
          </nav>
        </div>
      )}

      {/* Content */}
      <div className="flex-1 overflow-auto">
        <div className="mx-auto max-w-3xl p-8">
          {/* Draft banner */}
          {tag.status === "draft" && (
            <div className="mb-4 flex items-center justify-between rounded-md border border-amber-500/30 bg-amber-500/10 px-4 py-3">
              <span className="text-sm text-amber-600">
                AI Generated — Review before approving
              </span>
              <div className="flex items-center gap-2">
                {onReExtract && (
                  <button
                    onClick={() => onReExtract(tag.id)}
                    disabled={reExtracting}
                    className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1 text-xs hover:bg-accent disabled:opacity-50"
                  >
                    {reExtracting ? (
                      <Loader2 className="h-3 w-3 animate-spin" />
                    ) : (
                      <RotateCw className="h-3 w-3" />
                    )}
                    {reExtracting ? "Extracting..." : "Re-extract"}
                  </button>
                )}
                <button
                  onClick={handleApprove}
                  disabled={approving}
                  className="flex items-center gap-1 rounded-md bg-green-600 px-2.5 py-1 text-xs text-white hover:bg-green-700 disabled:opacity-50"
                >
                  <CheckCircle className="h-3 w-3" />
                  {approving ? "Approving..." : "Approve"}
                </button>
              </div>
            </div>
          )}

          {/* Header */}
          <div className="mb-6">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span
                className={`rounded px-2 py-0.5 font-medium ${
                  tag.status === "active"
                    ? "bg-green-500/10 text-green-500"
                    : tag.status === "draft"
                      ? "bg-amber-500/10 text-amber-500"
                      : "bg-yellow-500/10 text-yellow-500"
                }`}
              >
                {tag.status}
              </span>
              <span className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                {new Date(tag.updatedAt).toLocaleDateString()}
              </span>
              <button
                onClick={() => setShowToc(!showToc)}
                className="ml-auto rounded p-1 hover:bg-accent"
                title="Toggle table of contents"
              >
                <List className="h-3.5 w-3.5" />
              </button>
              {onReExtract && (
                <button
                  onClick={() => onReExtract(tag.id)}
                  disabled={reExtracting}
                  className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1 text-xs hover:bg-accent disabled:opacity-50"
                >
                  {reExtracting ? (
                    <Loader2 className="h-3 w-3 animate-spin" />
                  ) : (
                    <RotateCw className="h-3 w-3" />
                  )}
                  {reExtracting ? "Extracting..." : "Re-extract"}
                </button>
              )}
              <button
                onClick={handleEdit}
                className="flex items-center gap-1 rounded-md border border-border px-2.5 py-1 text-xs hover:bg-accent"
              >
                <Pencil className="h-3 w-3" />
                Edit
              </button>
            </div>
            <h1 className="mt-3 text-2xl font-bold">{tag.name}</h1>
            {tag.description && (
              <p className="mt-1 text-sm text-muted-foreground">
                {tag.description}
              </p>
            )}
          </div>

          {/* Rendered content */}
          <div className="prose-sm">{renderedContent}</div>
        </div>
      </div>
    </div>
  );
}

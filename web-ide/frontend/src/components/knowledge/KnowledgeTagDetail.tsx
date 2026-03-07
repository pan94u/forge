"use client";

import React, { useState, useMemo, useCallback } from "react";
import { KnowledgeTagView, knowledgeTagApi } from "@/lib/knowledge-tag-api";
import { MonacoEditor } from "@/components/editor/MonacoEditor";
import { Pencil, Save, X, Clock, List, CheckCircle, RotateCw, FileQuestion, Loader2, Download } from "lucide-react";

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

/** 修复 AI 生成 mermaid 常见语法问题 */
function sanitizeMermaid(code: string): string {
  return code
    .replace(/：/g, ":")   // 全角冒号 → 半角
    .replace(/（/g, "(")   // 全角左括号
    .replace(/）/g, ")")   // 全角右括号
    .replace(/，/g, ",")   // 全角逗号
    .replace(/。/g, ".")   // 全角句号
    .replace(/\r\n/g, "\n"); // 统一换行符
}

function MermaidToolbar({
  zoom, fitZoom, onZoomOut, onZoomIn, onReset, onExport, onDownloadCode, onDownloadPng, onFullscreen, disabled,
}: {
  zoom: number; fitZoom: number;
  onZoomOut: () => void; onZoomIn: () => void; onReset: () => void;
  onExport: () => void; onDownloadCode: () => void; onDownloadPng: () => void;
  onFullscreen: () => void; disabled?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const wrapRef = React.useRef<HTMLDivElement>(null);

  React.useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  const btnCls = "rounded px-1.5 py-0.5 text-xs text-muted-foreground hover:bg-accent disabled:opacity-40";
  return (
    <div className="flex items-center gap-1">
      <button onClick={onZoomOut} disabled={disabled} className={btnCls}>−</button>
      <button onClick={onReset} disabled={disabled} className={btnCls} title="重置缩放">
        {Math.round(zoom * 100)}%
      </button>
      <button onClick={onZoomIn} disabled={disabled} className={btnCls}>+</button>
      <span className="mx-0.5 text-border">|</span>

      {/* 下载下拉菜单 */}
      <div ref={wrapRef} className="relative">
        <button
          disabled={disabled}
          className={`${btnCls} flex items-center gap-1`}
          onClick={() => setOpen(v => !v)}
          title="下载"
        >
          <Download className="h-3 w-3" />
          <span>下载</span>
        </button>
        {open && !disabled && (
          <div className="absolute right-0 top-full mt-1 z-50 min-w-[150px] rounded-md border border-border bg-popover shadow-md py-1 text-xs">
            <button
              className="w-full px-3 py-1.5 text-left hover:bg-accent flex justify-between items-center"
              onClick={() => { onDownloadCode(); setOpen(false); }}
            >
              <span>Mermaid 源码</span>
              <span className="text-muted-foreground ml-2">.mmd</span>
            </button>
            <button
              className="w-full px-3 py-1.5 text-left hover:bg-accent flex justify-between items-center"
              onClick={() => { onExport(); setOpen(false); }}
            >
              <span>矢量图</span>
              <span className="text-muted-foreground ml-2">SVG</span>
            </button>
            <button
              className="w-full px-3 py-1.5 text-left hover:bg-accent flex justify-between items-center"
              onClick={() => { onDownloadPng(); setOpen(false); }}
            >
              <span>图片</span>
              <span className="text-muted-foreground ml-2">PNG</span>
            </button>
          </div>
        )}
      </div>

      <span className="mx-0.5 text-border">|</span>
      <button onClick={onFullscreen} disabled={disabled} className={btnCls} title="全屏预览">⛶</button>
    </div>
  );
}

function MermaidViewer({
  svgHtml, height, fullscreen, onDownloadCode, onDownloadPng,
}: {
  svgHtml: string; height: number; fullscreen?: boolean;
  onDownloadCode?: () => void; onDownloadPng?: () => void;
}) {
  const viewportRef = React.useRef<HTMLDivElement>(null);
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = React.useState(1);
  const [fitZoom, setFitZoom] = React.useState(1);
  const [pan, setPan] = React.useState({ x: 0, y: 0 });
  const [dragging, setDragging] = React.useState(false);
  const [dragStart, setDragStart] = React.useState({ x: 0, y: 0 });
  const [viewH, setViewH] = React.useState(height);

  React.useEffect(() => {
    if (!containerRef.current || !viewportRef.current) return;
    containerRef.current.innerHTML = svgHtml;
    setZoom(1); setFitZoom(1); setPan({ x: 0, y: 0 });

    const svgEl = containerRef.current.querySelector("svg");
    if (svgEl) {
      const vb = svgEl.viewBox?.baseVal;
      if (vb && vb.width > 0 && vb.height > 0) {
        const vpWidth = viewportRef.current.clientWidth || (fullscreen ? window.innerWidth * 0.9 : 700);
        const fit = Math.min(1, (vpWidth - 32) / vb.width);
        if (!fullscreen) setViewH(Math.min(560, Math.max(200, vb.height * fit + 32)));
        setZoom(fit); setFitZoom(fit);
      }
    }
  }, [svgHtml, fullscreen]);

  const exportSvg = () => {
    const svgEl = containerRef.current?.querySelector("svg");
    if (!svgEl) return;
    const a = Object.assign(document.createElement("a"), {
      href: URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svgEl)], { type: "image/svg+xml" })),
      download: `flow-diagram.svg`,
    });
    a.click(); URL.revokeObjectURL(a.href);
  };

  return (
    <div className={fullscreen ? "flex flex-col h-full" : ""}>
      {fullscreen && (
        <div className="flex items-center justify-end gap-1 px-3 py-1.5 border-b border-border bg-[#1a1a1a]">
          <MermaidToolbar
            zoom={zoom} fitZoom={fitZoom}
            onZoomOut={() => setZoom(z => Math.max(0.1, z - 0.25))}
            onZoomIn={() => setZoom(z => Math.min(5, z + 0.25))}
            onReset={() => { setZoom(fitZoom); setPan({ x: 0, y: 0 }); }}
            onExport={exportSvg}
            onDownloadCode={onDownloadCode ?? (() => {})}
            onDownloadPng={onDownloadPng ?? (() => {})}
            onFullscreen={() => {}}
          />
        </div>
      )}
      <div
        ref={viewportRef}
        className="overflow-hidden bg-[#1e1e1e]"
        style={fullscreen ? { flex: 1 } : { height: viewH }}
        onMouseDown={e => { setDragging(true); setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y }); }}
        onMouseMove={e => { if (dragging) setPan({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y }); }}
        onMouseUp={() => setDragging(false)}
        onMouseLeave={() => setDragging(false)}
      >
        <div
          ref={containerRef}
          className="flex h-full items-center justify-center [&_svg]:max-w-none"
          style={{
            transform: `translate(${pan.x}px,${pan.y}px) scale(${zoom})`,
            transformOrigin: "center center",
            cursor: dragging ? "grabbing" : "grab",
          }}
        />
      </div>
    </div>
  );
}

function MermaidBlock({ code, id }: { code: string; id: string }) {
  const [svgHtml, setSvgHtml] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [showSource, setShowSource] = React.useState(false);
  const [fullscreen, setFullscreen] = React.useState(false);

  React.useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(null); setSvgHtml(null); setShowSource(false);

    const safeId = `mermaid-kd-${id.replace(/[^a-zA-Z0-9]/g, "-")}`;
    import("mermaid").then(({ default: mermaid }) => {
      mermaid.initialize({ startOnLoad: false, theme: "dark", securityLevel: "loose" });
      mermaid.render(safeId, sanitizeMermaid(code))
        .then(({ svg }: { svg: string }) => {
          if (!cancelled) { setSvgHtml(svg); setLoading(false); }
        })
        .catch((err: Error) => {
          if (!cancelled) { setError(err.message); setLoading(false); }
        });
    });
    return () => { cancelled = true; };
  }, [code, id]);

  // ESC 关闭全屏
  React.useEffect(() => {
    if (!fullscreen) return;
    const fn = (e: KeyboardEvent) => { if (e.key === "Escape") setFullscreen(false); };
    window.addEventListener("keydown", fn);
    return () => window.removeEventListener("keydown", fn);
  }, [fullscreen]);

  const exportSvg = () => {
    if (!svgHtml) return;
    const tmp = document.createElement("div");
    tmp.innerHTML = svgHtml;
    const svgEl = tmp.querySelector("svg");
    if (!svgEl) return;
    const a = Object.assign(document.createElement("a"), {
      href: URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svgEl)], { type: "image/svg+xml" })),
      download: `flow-diagram-${id}.svg`,
    });
    a.click(); URL.revokeObjectURL(a.href);
  };

  const downloadCode = () => {
    const a = Object.assign(document.createElement("a"), {
      href: URL.createObjectURL(new Blob([code], { type: "text/plain" })),
      download: `flow-diagram-${id}.mmd`,
    });
    a.click(); URL.revokeObjectURL(a.href);
  };

  const downloadPng = () => {
    if (!svgHtml) return;
    const tmp = document.createElement("div");
    tmp.innerHTML = svgHtml;
    const svgEl = tmp.querySelector("svg");
    if (!svgEl) return;
    const vb = svgEl.viewBox?.baseVal;
    const w = (vb && vb.width > 0) ? vb.width : 800;
    const h = (vb && vb.height > 0) ? vb.height : 600;
    svgEl.setAttribute("width", String(w));
    svgEl.setAttribute("height", String(h));
    // 用 data URL 而非 blob URL，避免 Canvas 跨源污染导致 onload 不触发
    const svgStr = new XMLSerializer().serializeToString(svgEl);
    const dataUrl = `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svgStr)))}`;
    const img = new Image();
    img.onload = () => {
      const scale = 2;
      const canvas = document.createElement("canvas");
      canvas.width = w * scale; canvas.height = h * scale;
      const ctx = canvas.getContext("2d")!;
      ctx.scale(scale, scale);
      ctx.fillStyle = "#1e1e1e";
      ctx.fillRect(0, 0, w, h);
      ctx.drawImage(img, 0, 0, w, h);
      canvas.toBlob(blob => {
        if (!blob) return;
        const a = Object.assign(document.createElement("a"), {
          href: URL.createObjectURL(blob),
          download: `flow-diagram-${id}.png`,
        });
        a.click(); URL.revokeObjectURL(a.href);
      }, "image/png");
    };
    img.src = dataUrl;
  };

  return (
    <>
      <div className="my-4 rounded-md border border-border overflow-hidden">
        <div className="flex items-center justify-between border-b border-border px-3 py-1.5 bg-[#1a1a1a]">
          <span className="text-xs text-muted-foreground">mermaid</span>
          {!error && (
            <MermaidToolbar
              zoom={1} fitZoom={1}
              onZoomOut={() => {}} onZoomIn={() => {}} onReset={() => {}}
              onExport={exportSvg}
              onDownloadCode={downloadCode}
              onDownloadPng={downloadPng}
              onFullscreen={() => setFullscreen(true)}
              disabled={loading || !svgHtml}
            />
          )}
        </div>

        {loading ? (
          <div className="flex items-center justify-center bg-[#1e1e1e]" style={{ height: 120 }}>
            <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : error ? (
          <div className="p-4 space-y-3 bg-[#1e1e1e]">
            <div className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-3">
              <span className="mt-0.5 text-destructive text-xs">⚠</span>
              <div className="flex-1 min-w-0">
                <p className="text-xs font-medium text-destructive">Mermaid 语法错误</p>
                <p className="mt-1 text-xs text-muted-foreground break-words">{error}</p>
              </div>
            </div>
            <p className="text-xs text-muted-foreground">可点击上方「Re-extract」重新生成，或「Edit」手动修正。</p>
            <button onClick={() => setShowSource(v => !v)} className="text-xs text-muted-foreground hover:text-foreground underline">
              {showSource ? "收起源码" : "查看源码"}
            </button>
            {showSource && (
              <pre className="overflow-auto max-h-60 rounded bg-[#111] p-3 text-xs text-muted-foreground whitespace-pre-wrap">{code}</pre>
            )}
          </div>
        ) : svgHtml ? (
          <MermaidViewer svgHtml={svgHtml} height={320} />
        ) : null}
      </div>

      {/* 全屏遮罩 */}
      {fullscreen && svgHtml && (
        <div className="fixed inset-0 z-50 flex flex-col bg-[#141414]">
          <div className="flex items-center justify-between border-b border-border px-4 py-2 bg-[#1a1a1a]">
            <span className="text-xs text-muted-foreground">流程图全屏预览 &nbsp;·&nbsp; ESC 或点击 ✕ 关闭</span>
            <button
              onClick={() => setFullscreen(false)}
              className="rounded p-1 text-muted-foreground hover:bg-accent hover:text-foreground"
            >
              ✕
            </button>
          </div>
          <div className="flex-1 overflow-hidden">
            <MermaidViewer svgHtml={svgHtml} height={0} fullscreen onDownloadCode={downloadCode} onDownloadPng={downloadPng} />
          </div>
        </div>
      )}
    </>
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

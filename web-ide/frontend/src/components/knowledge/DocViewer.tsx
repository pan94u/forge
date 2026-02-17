"use client";

import React, { useMemo, useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { List, ExternalLink, Clock, User } from "lucide-react";

interface KnowledgeDocFull {
  id: string;
  title: string;
  type: string;
  content: string;
  author: string;
  updatedAt: string;
  tags: string[];
  relatedDocs: Array<{ id: string; title: string }>;
}

interface DocViewerProps {
  documentId: string;
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
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-|-$/g, "");
    entries.push({ id, text, level });
  }

  return entries;
}

function renderDocMarkdown(content: string): React.ReactNode {
  const lines = content.split("\n");
  const elements: React.ReactNode[] = [];

  let inCodeBlock = false;
  let codeBlockLang = "";
  let codeLines: string[] = [];

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Code block boundaries
    if (line.startsWith("```")) {
      if (inCodeBlock) {
        elements.push(
          <div key={`code-${i}`} className="my-4 rounded-md border border-border bg-[#1e1e1e]">
            <div className="border-b border-border px-3 py-1 text-xs text-muted-foreground">
              {codeBlockLang || "code"}
            </div>
            <pre className="overflow-x-auto p-4 text-sm">
              <code>{codeLines.join("\n")}</code>
            </pre>
          </div>
        );
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

    // Headings
    const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      const level = headingMatch[1].length;
      const text = headingMatch[2];
      const id = text
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, "-")
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

    // Horizontal rule
    if (/^---+$/.test(line.trim())) {
      elements.push(<hr key={`hr-${i}`} className="my-6 border-border" />);
      continue;
    }

    // Blockquote
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

    // List items
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

    // Empty line
    if (line.trim() === "") {
      elements.push(<div key={`br-${i}`} className="h-2" />);
      continue;
    }

    // Regular paragraph
    elements.push(
      <p key={`p-${i}`} className="my-1 leading-relaxed">
        {line}
      </p>
    );
  }

  return <>{elements}</>;
}

export function DocViewer({ documentId }: DocViewerProps) {
  const [showToc, setShowToc] = useState(true);

  const { data: doc, isLoading, error } = useQuery<KnowledgeDocFull>({
    queryKey: ["knowledge-doc", documentId],
    queryFn: async () => {
      const res = await fetch(`/api/knowledge/docs/${documentId}`);
      if (!res.ok) throw new Error("Failed to load document");
      return res.json();
    },
  });

  const toc = useMemo(() => {
    if (!doc?.content) return [];
    return extractToc(doc.content);
  }, [doc?.content]);

  const renderedContent = useMemo(() => {
    if (!doc?.content) return null;
    return renderDocMarkdown(doc.content);
  }, [doc?.content]);

  if (isLoading) {
    return (
      <div className="p-8 space-y-4">
        <div className="h-8 w-1/2 animate-pulse rounded bg-muted" />
        <div className="h-4 w-full animate-pulse rounded bg-muted" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-muted" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-muted" />
      </div>
    );
  }

  if (error || !doc) {
    return (
      <div className="flex h-full items-center justify-center">
        <p className="text-destructive">Failed to load document.</p>
      </div>
    );
  }

  return (
    <div className="flex h-full">
      {/* Table of Contents */}
      {showToc && toc.length > 0 && (
        <div className="w-56 flex-shrink-0 border-r border-border overflow-auto p-4">
          <h4 className="mb-3 text-xs font-semibold uppercase text-muted-foreground">
            On this page
          </h4>
          <nav className="space-y-1">
            {toc.map((entry) => (
              <a
                key={entry.id}
                href={`#${entry.id}`}
                className="block truncate text-xs text-muted-foreground hover:text-foreground transition-colors"
                style={{ paddingLeft: `${(entry.level - 1) * 12}px` }}
              >
                {entry.text}
              </a>
            ))}
          </nav>
        </div>
      )}

      {/* Document Content */}
      <div className="flex-1 overflow-auto">
        <div className="mx-auto max-w-3xl p-8">
          {/* Document Header */}
          <div className="mb-6">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span className="rounded bg-muted px-2 py-0.5 font-medium uppercase">
                {doc.type}
              </span>
              <span className="flex items-center gap-1">
                <User className="h-3 w-3" />
                {doc.author}
              </span>
              <span className="flex items-center gap-1">
                <Clock className="h-3 w-3" />
                {new Date(doc.updatedAt).toLocaleDateString()}
              </span>
              <button
                onClick={() => setShowToc(!showToc)}
                className="ml-auto rounded p-1 hover:bg-accent"
                title="Toggle table of contents"
              >
                <List className="h-3.5 w-3.5" />
              </button>
            </div>
            <h1 className="mt-3 text-2xl font-bold">{doc.title}</h1>
            {doc.tags.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-1">
                {doc.tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Content */}
          <div className="prose-sm">{renderedContent}</div>

          {/* Related Documents */}
          {doc.relatedDocs.length > 0 && (
            <div className="mt-8 border-t border-border pt-6">
              <h3 className="mb-3 text-sm font-semibold">Related Documents</h3>
              <div className="space-y-1">
                {doc.relatedDocs.map((related) => (
                  <a
                    key={related.id}
                    href={`#doc-${related.id}`}
                    className="flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-accent"
                    onClick={(e) => {
                      e.preventDefault();
                      // This would trigger navigation in a real app
                    }}
                  >
                    <ExternalLink className="h-3.5 w-3.5 text-muted-foreground" />
                    {related.title}
                  </a>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

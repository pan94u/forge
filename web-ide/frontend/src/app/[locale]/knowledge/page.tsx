"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { KnowledgeSearch } from "@/components/knowledge/KnowledgeSearch";
import { DocViewer } from "@/components/knowledge/DocViewer";
import { ArchDiagramViewer } from "@/components/knowledge/ArchDiagramViewer";
import { ServiceGraphViewer } from "@/components/knowledge/ServiceGraphViewer";
import { ApiExplorer } from "@/components/knowledge/ApiExplorer";
import { KnowledgeTagList } from "@/components/knowledge/KnowledgeTagList";
import { KnowledgeTagDetail } from "@/components/knowledge/KnowledgeTagDetail";
import { KnowledgeTagView, knowledgeTagApi } from "@/lib/knowledge-tag-api";
import { Workspace, workspaceApi } from "@/lib/workspace-api";
import { BookOpen, BookMarked, GitBranch, Network, Globe, Sparkles, Loader2, ChevronDown } from "lucide-react";

type KnowledgeTab = "docs" | "standards" | "architecture" | "services" | "apis";

export default function KnowledgePage() {
  const t = useTranslations("knowledge");
  const [activeTab, setActiveTab] = useState<KnowledgeTab>("docs");
  const [selectedDocId, setSelectedDocId] = useState<string | null>(null);
  const searchParams = useSearchParams();

  const tabs: { id: KnowledgeTab; label: string; icon: React.ElementType }[] = [
    { id: "docs", label: t("tabDocs"), icon: BookOpen },
    { id: "standards", label: t("tabStandards"), icon: BookMarked },
    { id: "architecture", label: t("tabArchitecture"), icon: GitBranch },
    { id: "services", label: t("tabServices"), icon: Network },
    { id: "apis", label: t("tabApis"), icon: Globe },
  ];

  // Workspace selector state
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | undefined>(
    searchParams.get("workspaceId") ?? undefined
  );

  // Standards tab state
  const [tags, setTags] = useState<KnowledgeTagView[]>([]);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [selectedTagId, setSelectedTagId] = useState<string | null>(null);
  const [tagSearchQuery, setTagSearchQuery] = useState("");

  // Extraction state
  const [extracting, setExtracting] = useState(false);
  const [extractionProgress, setExtractionProgress] = useState<{
    totalTags: number;
    completedTags: number;
    currentTag: string | null;
  } | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Load workspace list
  useEffect(() => {
    workspaceApi.listWorkspaces().then(setWorkspaces).catch(console.error);
  }, []);

  const loadTags = useCallback(async () => {
    setTagsLoading(true);
    try {
      const data = await knowledgeTagApi.listTags(selectedWorkspaceId);
      setTags(data);
    } catch (err) {
      console.error("Failed to load tags:", err);
    } finally {
      setTagsLoading(false);
    }
  }, [selectedWorkspaceId]);

  useEffect(() => {
    if (activeTab === "standards") {
      loadTags();
    }
  }, [activeTab, loadTags]);

  // Reset selected tag when workspace changes
  useEffect(() => {
    setSelectedTagId(null);
  }, [selectedWorkspaceId]);

  // Cleanup polling on unmount
  useEffect(() => {
    return () => {
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  const selectedTag = tags.find((tt) => tt.id === selectedTagId) ?? null;

  const handleTagUpdated = useCallback((updated: KnowledgeTagView) => {
    setTags((prev) => prev.map((tt) => (tt.id === updated.id ? updated : tt)));
  }, []);

  const handleExtractAll = useCallback(async () => {
    if (!selectedWorkspaceId || extracting) return;
    setExtracting(true);
    setExtractionProgress(null);

    try {
      const { jobId } = await knowledgeTagApi.triggerExtraction(selectedWorkspaceId);

      // Poll for progress
      pollRef.current = setInterval(async () => {
        try {
          const status = await knowledgeTagApi.getJobStatus(jobId);
          setExtractionProgress(status.progress);

          if (status.status === "completed" || status.status === "failed") {
            if (pollRef.current) clearInterval(pollRef.current);
            pollRef.current = null;
            setExtracting(false);
            setExtractionProgress(null);
            // Refresh tags
            loadTags();
          }
        } catch {
          // Continue polling on transient errors
        }
      }, 2000);
    } catch (err) {
      console.error("Failed to trigger extraction:", err);
      setExtracting(false);
    }
  }, [selectedWorkspaceId, extracting, loadTags]);

  const handleReExtract = useCallback(
    async (tagId: string) => {
      if (!selectedWorkspaceId) return;
      try {
        const { jobId } = await knowledgeTagApi.triggerExtraction(
          selectedWorkspaceId,
          tagId
        );

        // Poll for this single tag
        const poll = setInterval(async () => {
          try {
            const status = await knowledgeTagApi.getJobStatus(jobId);
            if (status.status === "completed" || status.status === "failed") {
              clearInterval(poll);
              loadTags();
            }
          } catch {
            // Continue polling
          }
        }, 2000);
      } catch (err) {
        console.error("Failed to re-extract tag:", err);
      }
    },
    [selectedWorkspaceId, loadTags]
  );

  return (
    <div className="flex h-full flex-col">
      {/* Tab Bar */}
      <div className="flex items-center gap-1 border-b border-border bg-card px-4">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => {
              setActiveTab(tab.id);
              setSelectedDocId(null);
            }}
            className={`flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition-colors ${
              activeTab === tab.id
                ? "border-primary text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            <tab.icon className="h-4 w-4" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex flex-1 overflow-hidden">
        {activeTab === "docs" && (
          <>
            <div className="w-96 flex-shrink-0 border-r border-border overflow-auto">
              <KnowledgeSearch
                onSelectDocument={(docId) => setSelectedDocId(docId)}
                selectedDocId={selectedDocId}
                workspaceId={selectedWorkspaceId}
              />
            </div>
            <div className="flex-1 overflow-auto">
              {selectedDocId ? (
                <DocViewer documentId={selectedDocId} />
              ) : (
                <div className="flex h-full items-center justify-center text-muted-foreground">
                  <div className="text-center">
                    <BookOpen className="mx-auto h-12 w-12 opacity-50" />
                    <p className="mt-4 text-lg font-medium">
                      {t("selectDocument")}
                    </p>
                    <p className="mt-1 text-sm">
                      {t("searchBrowse")}
                    </p>
                  </div>
                </div>
              )}
            </div>
          </>
        )}

        {activeTab === "standards" && (
          <>
            <div className="w-80 flex-shrink-0 border-r border-border overflow-auto">
              {/* Workspace Selector + Extract All */}
              <div className="border-b border-border p-3 space-y-2">
                <div className="relative">
                  <select
                    value={selectedWorkspaceId ?? ""}
                    onChange={(e) => setSelectedWorkspaceId(e.target.value || undefined)}
                    className="w-full appearance-none rounded-md border border-border bg-background px-3 py-2 pr-8 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  >
                    <option value="">{t("selectWorkspace")}</option>
                    {workspaces
                      .filter((w) => w.status === "active")
                      .map((w) => (
                        <option key={w.id} value={w.id}>
                          {w.name}
                        </option>
                      ))}
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-2 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                </div>

                {selectedWorkspaceId && (
                  <>
                    {extracting ? (
                      <div className="space-y-2">
                        <div className="flex items-center gap-2 text-sm text-blue-500">
                          <Loader2 className="h-4 w-4 animate-spin" />
                          <span>
                            {extractionProgress
                              ? t("analyzingProgress", {
                                  completed: extractionProgress.completedTags,
                                  total: extractionProgress.totalTags,
                                })
                              : t("startingExtraction")}
                          </span>
                        </div>
                        {extractionProgress &&
                          extractionProgress.totalTags > 0 && (
                            <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                              <div
                                className="h-full rounded-full bg-blue-500 transition-all"
                                style={{
                                  width: `${(extractionProgress.completedTags / extractionProgress.totalTags) * 100}%`,
                                }}
                              />
                            </div>
                          )}
                        {extractionProgress?.currentTag && (
                          <p className="text-xs text-muted-foreground truncate">
                            Current: {extractionProgress.currentTag}
                          </p>
                        )}
                      </div>
                    ) : (
                      <button
                        onClick={handleExtractAll}
                        className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
                      >
                        <Sparkles className="h-4 w-4" />
                        {t("extractAll")}
                      </button>
                    )}
                  </>
                )}
              </div>

              <KnowledgeTagList
                tags={tags}
                selectedTagId={selectedTagId}
                onSelect={setSelectedTagId}
                loading={tagsLoading}
                searchQuery={tagSearchQuery}
                onSearchChange={setTagSearchQuery}
              />
            </div>
            <div className="flex-1 overflow-auto">
              {selectedTag ? (
                <KnowledgeTagDetail
                  tag={selectedTag}
                  onUpdated={handleTagUpdated}
                  onReExtract={selectedWorkspaceId ? handleReExtract : undefined}
                />
              ) : (
                <div className="flex h-full items-center justify-center text-muted-foreground">
                  <div className="text-center">
                    <BookMarked className="mx-auto h-12 w-12 opacity-50" />
                    <p className="mt-4 text-lg font-medium">
                      {selectedWorkspaceId
                        ? t("selectStandard")
                        : t("selectWorkspaceFirst")}
                    </p>
                    <p className="mt-1 text-sm">
                      {selectedWorkspaceId
                        ? t("browseStandards")
                        : t("chooseWorkspace")}
                    </p>
                  </div>
                </div>
              )}
            </div>
          </>
        )}

        {activeTab === "architecture" && (
          <div className="flex-1 overflow-auto p-4">
            <ArchDiagramViewer />
          </div>
        )}

        {activeTab === "services" && (
          <div className="flex-1 overflow-auto">
            <ServiceGraphViewer />
          </div>
        )}

        {activeTab === "apis" && (
          <div className="flex-1 overflow-auto">
            <ApiExplorer />
          </div>
        )}
      </div>
    </div>
  );
}

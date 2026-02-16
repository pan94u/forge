"use client";

import React, { useState, useEffect, useCallback } from "react";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronRight,
  PanelLeftClose,
  PanelLeftOpen,
  PanelRightClose,
  PanelRightOpen,
  Terminal as TerminalIcon,
  Save,
  Play,
  Settings,
} from "lucide-react";
import { MonacoEditor } from "@/components/editor/MonacoEditor";
import { FileExplorer } from "@/components/editor/FileExplorer";
import { TerminalPanel } from "@/components/editor/TerminalPanel";
import { AiChatSidebar } from "@/components/chat/AiChatSidebar";
import { workspaceApi, type Workspace, type FileNode } from "@/lib/workspace-api";

export default function WorkspacePage() {
  const params = useParams();
  const workspaceId = params.id as string;

  const [leftPanelOpen, setLeftPanelOpen] = useState(true);
  const [rightPanelOpen, setRightPanelOpen] = useState(true);
  const [bottomPanelOpen, setBottomPanelOpen] = useState(false);
  const [activeFile, setActiveFile] = useState<string | null>(null);
  const [fileContent, setFileContent] = useState<string>("");
  const [isEditing, setIsEditing] = useState(false);
  const [openFiles, setOpenFiles] = useState<string[]>([]);

  const { data: workspace, isLoading: workspaceLoading } = useQuery<Workspace>({
    queryKey: ["workspace", workspaceId],
    queryFn: () => workspaceApi.getWorkspace(workspaceId),
    enabled: workspaceId !== "new",
  });

  const { data: fileTree } = useQuery<FileNode[]>({
    queryKey: ["files", workspaceId],
    queryFn: () => workspaceApi.getFileTree(workspaceId),
    enabled: workspaceId !== "new" && !!workspace,
  });

  const handleFileSelect = useCallback(
    async (filePath: string) => {
      setActiveFile(filePath);
      if (!openFiles.includes(filePath)) {
        setOpenFiles((prev) => [...prev, filePath]);
      }
      try {
        const content = await workspaceApi.getFileContent(workspaceId, filePath);
        setFileContent(content);
      } catch (err) {
        console.error("Failed to load file:", err);
        setFileContent("// Failed to load file content");
      }
    },
    [workspaceId, openFiles]
  );

  const handleSave = useCallback(async () => {
    if (!activeFile) return;
    try {
      await workspaceApi.saveFile(workspaceId, activeFile, fileContent);
    } catch (err) {
      console.error("Failed to save file:", err);
    }
  }, [workspaceId, activeFile, fileContent]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "s") {
        e.preventDefault();
        handleSave();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [handleSave]);

  const handleCloseFile = (filePath: string) => {
    setOpenFiles((prev) => prev.filter((f) => f !== filePath));
    if (activeFile === filePath) {
      const remaining = openFiles.filter((f) => f !== filePath);
      setActiveFile(remaining.length > 0 ? remaining[remaining.length - 1] : null);
    }
  };

  const breadcrumbParts = activeFile?.split("/").filter(Boolean) ?? [];

  if (workspaceId === "new") {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="max-w-md space-y-6 text-center">
          <h2 className="text-2xl font-bold">Create New Workspace</h2>
          <p className="text-muted-foreground">
            Set up a new development workspace with AI assistance.
          </p>
          <form
            className="space-y-4 text-left"
            onSubmit={async (e) => {
              e.preventDefault();
              const form = e.target as HTMLFormElement;
              const name = (form.elements.namedItem("name") as HTMLInputElement).value;
              const desc = (form.elements.namedItem("description") as HTMLInputElement).value;
              try {
                const ws = await workspaceApi.createWorkspace({ name, description: desc });
                window.location.href = `/workspace/${ws.id}`;
              } catch (err) {
                console.error("Failed to create workspace:", err);
              }
            }}
          >
            <div>
              <label className="block text-sm font-medium" htmlFor="name">
                Workspace Name
              </label>
              <input
                id="name"
                name="name"
                required
                className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                placeholder="my-project"
              />
            </div>
            <div>
              <label className="block text-sm font-medium" htmlFor="description">
                Description
              </label>
              <input
                id="description"
                name="description"
                className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                placeholder="A brief description"
              />
            </div>
            <button
              type="submit"
              className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
            >
              Create Workspace
            </button>
          </form>
        </div>
      </div>
    );
  }

  if (workspaceLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col">
      {/* Top Toolbar */}
      <div className="flex h-10 items-center justify-between border-b border-border bg-card px-2">
        <div className="flex items-center gap-1">
          <button
            onClick={() => setLeftPanelOpen(!leftPanelOpen)}
            className="rounded p-1 hover:bg-accent"
            title="Toggle file explorer"
          >
            {leftPanelOpen ? (
              <PanelLeftClose className="h-4 w-4" />
            ) : (
              <PanelLeftOpen className="h-4 w-4" />
            )}
          </button>
          {/* Breadcrumb */}
          <div className="flex items-center gap-1 text-sm text-muted-foreground">
            <span className="font-medium text-foreground">
              {workspace?.name ?? "Workspace"}
            </span>
            {breadcrumbParts.map((part, idx) => (
              <React.Fragment key={idx}>
                <ChevronRight className="h-3 w-3" />
                <span
                  className={
                    idx === breadcrumbParts.length - 1
                      ? "text-foreground"
                      : ""
                  }
                >
                  {part}
                </span>
              </React.Fragment>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleSave}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs hover:bg-accent"
            title="Save (Cmd+S)"
          >
            <Save className="h-3.5 w-3.5" />
            Save
          </button>
          <button
            onClick={() => setBottomPanelOpen(!bottomPanelOpen)}
            className="flex items-center gap-1 rounded px-2 py-1 text-xs hover:bg-accent"
            title="Toggle terminal"
          >
            <TerminalIcon className="h-3.5 w-3.5" />
            Terminal
          </button>
          <button
            className="flex items-center gap-1 rounded px-2 py-1 text-xs hover:bg-accent"
            title="Run"
          >
            <Play className="h-3.5 w-3.5" />
            Run
          </button>
          <button
            className="rounded p-1 hover:bg-accent"
            title="Settings"
          >
            <Settings className="h-3.5 w-3.5" />
          </button>
          <button
            onClick={() => setRightPanelOpen(!rightPanelOpen)}
            className="rounded p-1 hover:bg-accent"
            title="Toggle AI chat"
          >
            {rightPanelOpen ? (
              <PanelRightClose className="h-4 w-4" />
            ) : (
              <PanelRightOpen className="h-4 w-4" />
            )}
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left Panel - File Explorer */}
        {leftPanelOpen && (
          <div className="w-64 flex-shrink-0 border-r border-border overflow-auto">
            <FileExplorer
              files={fileTree ?? []}
              activeFile={activeFile}
              onFileSelect={handleFileSelect}
              workspaceId={workspaceId}
            />
          </div>
        )}

        {/* Center - Editor + Terminal */}
        <div className="flex flex-1 flex-col overflow-hidden">
          {/* File Tabs */}
          {openFiles.length > 0 && (
            <div className="flex h-9 items-center border-b border-border bg-card overflow-x-auto">
              {openFiles.map((fp) => {
                const fileName = fp.split("/").pop() ?? fp;
                const isActive = fp === activeFile;
                return (
                  <div
                    key={fp}
                    className={`flex items-center gap-1 border-r border-border px-3 py-1 text-xs cursor-pointer ${
                      isActive
                        ? "bg-background text-foreground"
                        : "text-muted-foreground hover:text-foreground"
                    }`}
                    onClick={() => handleFileSelect(fp)}
                  >
                    <span>{fileName}</span>
                    <button
                      className="ml-1 rounded-sm hover:bg-muted p-0.5"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleCloseFile(fp);
                      }}
                    >
                      x
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {/* Editor */}
          <div className="flex-1 overflow-hidden">
            {activeFile ? (
              <MonacoEditor
                value={fileContent}
                onChange={(value) => setFileContent(value ?? "")}
                filePath={activeFile}
                readOnly={!isEditing}
                onToggleEdit={() => setIsEditing(!isEditing)}
              />
            ) : (
              <div className="flex h-full items-center justify-center text-muted-foreground">
                <div className="text-center">
                  <p className="text-lg font-medium">No file open</p>
                  <p className="mt-1 text-sm">
                    Select a file from the explorer to start editing
                  </p>
                </div>
              </div>
            )}
          </div>

          {/* Bottom Panel - Terminal */}
          {bottomPanelOpen && (
            <div className="h-64 flex-shrink-0 border-t border-border">
              <TerminalPanel workspaceId={workspaceId} />
            </div>
          )}
        </div>

        {/* Right Panel - AI Chat */}
        {rightPanelOpen && (
          <div className="w-96 flex-shrink-0 border-l border-border">
            <AiChatSidebar
              workspaceId={workspaceId}
              activeFile={activeFile}
              fileContent={fileContent}
            />
          </div>
        )}
      </div>
    </div>
  );
}

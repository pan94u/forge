"use client";

import React, { useState, useEffect, useCallback, useRef } from "react";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
  Maximize2,
  Minimize2,
} from "lucide-react";
import { MonacoEditor } from "@/components/editor/MonacoEditor";
import { FileExplorer } from "@/components/editor/FileExplorer";
import { TerminalPanel } from "@/components/editor/TerminalPanel";
import { AiChatSidebar } from "@/components/chat/AiChatSidebar";
import { ServicePanel } from "@/components/workspace/ServicePanel";
import { Loader2 } from "lucide-react";
import { workspaceApi, type Workspace, type FileNode } from "@/lib/workspace-api";

// Progress bar milestones for simulated progress during git clone
const PROGRESS_STEPS = [
  { target: 15, duration: 2000 },
  { target: 40, duration: 8000 },
  { target: 70, duration: 12000 },
  { target: 90, duration: 8000 },
  { target: 99, duration: 30000 },
];

function CreateWorkspaceForm() {
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cloning, setCloning] = useState(false);
  const [cloningWsId, setCloningWsId] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);
  const progressRef = useRef<NodeJS.Timeout | null>(null);
  const pollRef = useRef<NodeJS.Timeout | null>(null);

  // Simulated progress animation
  const startProgress = useCallback(() => {
    let stepIdx = 0;
    let current = 0;

    const tick = () => {
      if (stepIdx >= PROGRESS_STEPS.length) return;
      const step = PROGRESS_STEPS[stepIdx];
      const increment = (step.target - current) / (step.duration / 100);

      const interval = setInterval(() => {
        current = Math.min(current + increment, step.target);
        setProgress(Math.round(current));
        if (current >= step.target) {
          clearInterval(interval);
          stepIdx++;
          if (stepIdx < PROGRESS_STEPS.length) {
            tick();
          }
        }
      }, 100);

      progressRef.current = interval;
    };

    tick();
  }, []);

  // Poll workspace status during clone
  const startPolling = useCallback((wsId: string) => {
    const poll = async () => {
      try {
        const ws = await workspaceApi.getWorkspace(wsId);
        if (ws.status === "active") {
          // Clone succeeded
          setProgress(100);
          if (progressRef.current) clearInterval(progressRef.current);
          if (pollRef.current) clearInterval(pollRef.current);
          setTimeout(() => {
            window.location.href = `/workspace/${wsId}`;
          }, 1000);
        } else if (ws.status === "error") {
          // Clone failed
          if (progressRef.current) clearInterval(progressRef.current);
          if (pollRef.current) clearInterval(pollRef.current);
          setError(ws.errorMessage || "Git clone failed");
          setCloning(false);
          setCreating(false);
          setProgress(0);
        }
        // status === "creating" → keep polling
      } catch {
        // Network error, keep polling
      }
    };

    pollRef.current = setInterval(poll, 2000);
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (progressRef.current) clearInterval(progressRef.current);
      if (pollRef.current) clearInterval(pollRef.current);
    };
  }, []);

  return (
    <div className="flex h-full items-center justify-center">
      <div className="max-w-md space-y-6 text-center">
        <h2 className="text-2xl font-bold">Create New Workspace</h2>
        <p className="text-muted-foreground">
          Set up a new development workspace with AI assistance.
        </p>
        {error && (
          <div className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive text-left">
            {error}
          </div>
        )}

        {/* Progress bar during git clone */}
        {cloning && (
          <div className="space-y-3">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">
                {progress < 100 ? "Cloning repository..." : "Clone complete!"}
              </span>
              <span className="font-mono text-primary">{progress}%</span>
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
              <div
                className={`h-full rounded-full transition-all duration-300 ${
                  progress >= 100
                    ? "bg-green-500"
                    : "bg-primary"
                }`}
                style={{ width: `${progress}%` }}
              />
            </div>
            <p className="text-xs text-muted-foreground">
              {progress < 40
                ? "Connecting to repository..."
                : progress < 70
                  ? "Downloading files..."
                  : progress < 100
                    ? "Finalizing workspace..."
                    : "Redirecting..."}
            </p>
          </div>
        )}

        {!cloning && (
        <form
          className="space-y-4 text-left"
          onSubmit={async (e) => {
            e.preventDefault();
            if (creating) return;
            setError(null);
            const form = e.target as HTMLFormElement;
            const name = (form.elements.namedItem("name") as HTMLInputElement).value.trim();
            const desc = (form.elements.namedItem("description") as HTMLInputElement).value.trim();
            const repo = (form.elements.namedItem("repository") as HTMLInputElement).value.trim();
            const branch = (form.elements.namedItem("branch") as HTMLInputElement).value.trim();
            const accessToken = (form.elements.namedItem("accessToken") as HTMLInputElement).value.trim();
            if (!name) { setError("Workspace 名称不能为空"); return; }
            if (repo && !/^https?:\/\/.+/.test(repo)) {
              setError("Git URL 格式无效，需以 http:// 或 https:// 开头");
              return;
            }
            setCreating(true);
            try {
              const ws = await workspaceApi.createWorkspace({
                name,
                description: desc,
                repository: repo || undefined,
                branch: branch || undefined,
                accessToken: accessToken || undefined,
              });

              if (ws.status === "creating") {
                // Async clone — show progress bar and poll
                setCloningWsId(ws.id);
                setCloning(true);
                startProgress();
                startPolling(ws.id);
              } else {
                // No git repo — workspace is already active
                window.location.href = `/workspace/${ws.id}`;
              }
            } catch (err) {
              setError("创建失败: " + (err instanceof Error ? err.message : String(err)));
              setCreating(false);
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
              disabled={creating}
              className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-50"
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
              disabled={creating}
              className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-50"
              placeholder="A brief description"
            />
          </div>
          <div className="rounded-md border border-border p-3 space-y-3">
            <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
              Import from Git (optional)
            </p>
            <div>
              <label className="block text-sm font-medium" htmlFor="repository">
                Git Repository URL
              </label>
              <input
                id="repository"
                name="repository"
                disabled={creating}
                className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-50"
                placeholder="https://github.com/user/repo.git"
              />
            </div>
            <div>
              <label className="block text-sm font-medium" htmlFor="branch">
                Branch
              </label>
              <input
                id="branch"
                name="branch"
                disabled={creating}
                className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-50"
                placeholder="main"
              />
            </div>
            <div>
              <label className="block text-sm font-medium" htmlFor="accessToken">
                Access Token（私有仓库）
              </label>
              <input
                id="accessToken"
                name="accessToken"
                type="password"
                disabled={creating}
                className="mt-1 w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-50"
                placeholder="glpat-xxxxxx（GitLab）或 ghp_xxxxxx（GitHub）"
              />
              <p className="mt-1 text-xs text-muted-foreground">
                GitLab：Settings → Access Tokens → read_repository + write_repository 权限
              </p>
            </div>
          </div>
          <button
            type="submit"
            disabled={creating}
            className="flex w-full items-center justify-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {creating ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                {" 创建中..."}
              </>
            ) : (
              "Create Workspace"
            )}
          </button>
        </form>
        )}
      </div>
    </div>
  );
}

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
  const [focusChat, setFocusChat] = useState(false);
  const [leftWidth, setLeftWidth] = useState(256);
  const [rightWidth, setRightWidth] = useState(384);
  const isDraggingRef = useRef<"left" | "right" | null>(null);
  const preFocusStateRef = useRef({ left: true, right: true });

  // Persist panel widths to localStorage
  useEffect(() => {
    const saved = localStorage.getItem("forge_panel_widths");
    if (saved) {
      try {
        const { left, right } = JSON.parse(saved);
        if (left) setLeftWidth(left);
        if (right) setRightWidth(right);
      } catch { /* ignore */ }
    }
  }, []);

  useEffect(() => {
    if (!isDraggingRef.current) {
      localStorage.setItem("forge_panel_widths", JSON.stringify({ left: leftWidth, right: rightWidth }));
    }
  }, [leftWidth, rightWidth]);

  // Draggable splitter logic
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDraggingRef.current) return;
      e.preventDefault();
      if (isDraggingRef.current === "left") {
        setLeftWidth(Math.max(160, Math.min(480, e.clientX)));
      } else if (isDraggingRef.current === "right") {
        setRightWidth(Math.max(280, Math.min(800, window.innerWidth - e.clientX)));
      }
    };
    const handleMouseUp = () => {
      if (isDraggingRef.current) {
        isDraggingRef.current = null;
        document.body.style.cursor = "";
        document.body.style.userSelect = "";
      }
    };
    window.addEventListener("mousemove", handleMouseMove);
    window.addEventListener("mouseup", handleMouseUp);
    return () => {
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mouseup", handleMouseUp);
    };
  }, []);

  const startDrag = (panel: "left" | "right") => {
    isDraggingRef.current = panel;
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  };

  const toggleFocusChat = () => {
    if (focusChat) {
      // Exit focus mode: restore previous panel state
      setFocusChat(false);
      setLeftPanelOpen(preFocusStateRef.current.left);
      setRightPanelOpen(preFocusStateRef.current.right);
    } else {
      // Save current state before entering focus mode
      preFocusStateRef.current = { left: leftPanelOpen, right: rightPanelOpen };
      setFocusChat(true);
      setLeftPanelOpen(false);
      setRightPanelOpen(true);
    }
  };

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

  const queryClient = useQueryClient();
  const [unsavedFiles, setUnsavedFiles] = useState<Set<string>>(new Set());
  const autoSaveTimerRef = useRef<NodeJS.Timeout | null>(null);

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

  // Listen for file_changed events from AI chat
  useEffect(() => {
    const handler = (e: Event) => {
      const { path, action } = (e as CustomEvent).detail as {
        path: string;
        action: string;
      };
      // Refresh file tree
      queryClient.invalidateQueries({ queryKey: ["files", workspaceId] });
      // Auto-open newly created or modified files
      if (path && (action === "created" || action === "modified")) {
        // Small delay to let the tree refresh
        setTimeout(() => handleFileSelect(path), 300);
      }
    };
    window.addEventListener("forge:file-changed", handler);
    return () => window.removeEventListener("forge:file-changed", handler);
  }, [workspaceId, queryClient, handleFileSelect]);

  const handleSave = useCallback(async () => {
    if (!activeFile) return;
    try {
      await workspaceApi.saveFile(workspaceId, activeFile, fileContent);
      setUnsavedFiles((prev) => {
        const next = new Set(prev);
        next.delete(activeFile);
        return next;
      });
    } catch (err) {
      console.error("Failed to save file:", err);
    }
  }, [workspaceId, activeFile, fileContent]);

  // Track unsaved changes and auto-save after 5 seconds of inactivity
  const handleContentChange = useCallback(
    (value: string | undefined) => {
      setFileContent(value ?? "");
      if (activeFile) {
        setUnsavedFiles((prev) => new Set(prev).add(activeFile));
        if (autoSaveTimerRef.current) clearTimeout(autoSaveTimerRef.current);
        autoSaveTimerRef.current = setTimeout(async () => {
          if (activeFile) {
            try {
              await workspaceApi.saveFile(workspaceId, activeFile, value ?? "");
              setUnsavedFiles((prev) => {
                const next = new Set(prev);
                next.delete(activeFile);
                return next;
              });
            } catch {
              // Silent fail for auto-save
            }
          }
        }, 5000);
      }
    },
    [workspaceId, activeFile]
  );

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
    return <CreateWorkspaceForm />;
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
            onClick={toggleFocusChat}
            className={`flex items-center gap-1 rounded px-2 py-1 text-xs hover:bg-accent ${focusChat ? "bg-primary/10 text-primary" : ""}`}
            title={focusChat ? "Exit Focus Chat" : "Focus Chat (fullscreen AI)"}
          >
            {focusChat ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
            {focusChat ? "Exit" : "Focus"}
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
        {leftPanelOpen && !focusChat && (
          <>
            <div style={{ width: leftWidth }} className="flex-shrink-0 border-r border-border overflow-auto">
              <FileExplorer
                files={fileTree ?? []}
                activeFile={activeFile}
                onFileSelect={handleFileSelect}
                workspaceId={workspaceId}
                onFileTreeChanged={() =>
                  queryClient.invalidateQueries({ queryKey: ["files", workspaceId] })
                }
              />
            </div>
            {/* Left splitter */}
            <div
              className="w-1 flex-shrink-0 cursor-col-resize bg-transparent hover:bg-primary/30 active:bg-primary/50 transition-colors"
              onMouseDown={() => startDrag("left")}
            />
          </>
        )}

        {/* Center - Editor + Terminal (hidden in Focus Chat mode) */}
        {!focusChat && (
          <div className="flex flex-1 flex-col overflow-hidden">
            {/* File Tabs */}
            {openFiles.length > 0 && (
              <div className="flex h-9 items-center border-b border-border bg-card overflow-x-auto">
                {openFiles.map((fp) => {
                  const fileName = fp.split("/").pop() ?? fp;
                  const isActive = fp === activeFile;
                  const isUnsaved = unsavedFiles.has(fp);
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
                      {isUnsaved && (
                        <span className="h-2 w-2 rounded-full bg-primary flex-shrink-0" title="Unsaved changes" />
                      )}
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
                  onChange={handleContentChange}
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

            {/* Bottom Panel - Terminal + Services */}
            {bottomPanelOpen && (
              <div className="h-64 flex-shrink-0 border-t border-border flex flex-col">
                <div className="flex-1 overflow-hidden">
                  <TerminalPanel workspaceId={workspaceId} />
                </div>
                <ServicePanel workspaceId={workspaceId} />
              </div>
            )}
            {!bottomPanelOpen && (
              <ServicePanel workspaceId={workspaceId} />
            )}
          </div>
        )}

        {/* Right splitter (when not in focus mode) */}
        {rightPanelOpen && !focusChat && (
          <div
            className="w-1 flex-shrink-0 cursor-col-resize bg-transparent hover:bg-primary/30 active:bg-primary/50 transition-colors"
            onMouseDown={() => startDrag("right")}
          />
        )}

        {/* Right Panel - AI Chat */}
        {rightPanelOpen && (
          <div
            style={focusChat ? undefined : { width: rightWidth }}
            className={`flex-shrink-0 border-l border-border ${focusChat ? "flex-1" : ""}`}
          >
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

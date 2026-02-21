"use client";

import React, { useState, useCallback } from "react";
import {
  ChevronRight,
  ChevronDown,
  File,
  Folder,
  FolderOpen,
  FileCode,
  FileJson,
  FileText,
  Image,
  MoreVertical,
  Copy,
  Wand2,
  ExternalLink,
  FilePlus,
  FolderPlus,
  Pencil,
  Trash2,
  Plus,
} from "lucide-react";
import type { FileNode } from "@/lib/workspace-api";
import { workspaceApi } from "@/lib/workspace-api";

interface FileExplorerProps {
  files: FileNode[];
  activeFile: string | null;
  onFileSelect: (path: string) => void;
  workspaceId: string;
  onFileTreeChanged?: () => void;
}

function getFileIcon(name: string, isDirectory: boolean, isOpen: boolean) {
  if (isDirectory) {
    return isOpen ? (
      <FolderOpen className="h-4 w-4 text-forge-400" />
    ) : (
      <Folder className="h-4 w-4 text-forge-400" />
    );
  }

  const ext = name.split(".").pop()?.toLowerCase();
  switch (ext) {
    case "ts":
    case "tsx":
    case "js":
    case "jsx":
    case "kt":
    case "java":
    case "py":
    case "go":
    case "rs":
      return <FileCode className="h-4 w-4 text-blue-400" />;
    case "json":
    case "yaml":
    case "yml":
    case "toml":
      return <FileJson className="h-4 w-4 text-yellow-400" />;
    case "md":
    case "txt":
    case "rst":
      return <FileText className="h-4 w-4 text-gray-400" />;
    case "png":
    case "jpg":
    case "jpeg":
    case "gif":
    case "svg":
    case "ico":
      return <Image className="h-4 w-4 text-green-400" role="presentation" />;
    default:
      return <File className="h-4 w-4 text-gray-400" />;
  }
}

/** Check if a name already exists among siblings at the same level */
function hasDuplicateSibling(nodes: FileNode[], fullPath: string): boolean {
  const parts = fullPath.split("/");
  const name = parts[parts.length - 1];

  // Navigate to the parent directory level
  let siblings = nodes;
  for (let i = 0; i < parts.length - 1; i++) {
    const dir = siblings.find(
      (n) => n.name === parts[i] && n.type === "directory",
    );
    if (!dir?.children) return false; // Parent doesn't exist yet
    siblings = dir.children;
  }

  return siblings.some((n) => n.name === name);
}

interface ContextMenuState {
  visible: boolean;
  x: number;
  y: number;
  path: string;
  isDirectory: boolean;
}

interface TreeNodeProps {
  node: FileNode;
  depth: number;
  activeFile: string | null;
  onFileSelect: (path: string) => void;
  onContextMenu: (e: React.MouseEvent, path: string, isDir: boolean) => void;
}

function TreeNode({
  node,
  depth,
  activeFile,
  onFileSelect,
  onContextMenu,
}: TreeNodeProps) {
  const [expanded, setExpanded] = useState(depth === 0);
  const isActive = node.path === activeFile;

  const handleClick = () => {
    if (node.type === "directory") {
      setExpanded(!expanded);
    } else {
      onFileSelect(node.path);
    }
  };

  return (
    <div>
      <div
        className={`flex cursor-pointer items-center gap-1 py-0.5 pr-2 text-sm hover:bg-accent ${
          isActive ? "bg-accent text-foreground" : "text-muted-foreground"
        }`}
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
        onClick={handleClick}
        onContextMenu={(e) => {
          e.stopPropagation(); // Prevent bubbling to container
          onContextMenu(e, node.path, node.type === "directory");
        }}
      >
        {node.type === "directory" ? (
          expanded ? (
            <ChevronDown className="h-3 w-3 flex-shrink-0" />
          ) : (
            <ChevronRight className="h-3 w-3 flex-shrink-0" />
          )
        ) : (
          <span className="w-3 flex-shrink-0" />
        )}
        {getFileIcon(node.name, node.type === "directory", expanded)}
        <span className="truncate">{node.name}</span>
      </div>
      {node.type === "directory" && expanded && node.children && (
        <div>
          {node.children
            .sort((a, b) => {
              if (a.type === b.type) return a.name.localeCompare(b.name);
              return a.type === "directory" ? -1 : 1;
            })
            .map((child) => (
              <TreeNode
                key={child.path}
                node={child}
                depth={depth + 1}
                activeFile={activeFile}
                onFileSelect={onFileSelect}
                onContextMenu={onContextMenu}
              />
            ))}
        </div>
      )}
    </div>
  );
}

export function FileExplorer({
  files,
  activeFile,
  onFileSelect,
  workspaceId,
  onFileTreeChanged,
}: FileExplorerProps) {
  const [contextMenu, setContextMenu] = useState<ContextMenuState>({
    visible: false,
    x: 0,
    y: 0,
    path: "",
    isDirectory: false,
  });

  const handleContextMenu = useCallback(
    (e: React.MouseEvent, path: string, isDir: boolean) => {
      e.preventDefault();
      setContextMenu({
        visible: true,
        x: e.clientX,
        y: e.clientY,
        path,
        isDirectory: isDir,
      });
    },
    [],
  );

  const closeContextMenu = useCallback(() => {
    setContextMenu((prev) => ({ ...prev, visible: false }));
  }, []);

  const handleCopyPath = useCallback(() => {
    navigator.clipboard.writeText(contextMenu.path);
    closeContextMenu();
  }, [contextMenu.path, closeContextMenu]);

  const handleAiExplain = useCallback(() => {
    if (!contextMenu.isDirectory) {
      onFileSelect(contextMenu.path);
      setTimeout(() => {
        window.dispatchEvent(
          new CustomEvent("forge:ai-explain-file", {
            detail: { filePath: contextMenu.path },
          }),
        );
      }, 500);
    }
    closeContextMenu();
  }, [contextMenu, onFileSelect, closeContextMenu]);

  /** Get parent path for context menu actions */
  const getParentPath = useCallback(() => {
    if (!contextMenu.path) return undefined;
    if (contextMenu.isDirectory) return contextMenu.path;
    // For files, extract parent directory
    const lastSlash = contextMenu.path.lastIndexOf("/");
    return lastSlash > 0 ? contextMenu.path.substring(0, lastSlash) : undefined;
  }, [contextMenu.path, contextMenu.isDirectory]);

  const handleNewFile = useCallback(
    async (parentPath?: string) => {
      const prefix = parentPath ? `${parentPath}/` : "";
      const fileName = window.prompt("New file name:", `${prefix}new-file.ts`);
      if (!fileName) return;

      // Sibling-level duplicate check
      if (hasDuplicateSibling(files, fileName)) {
        const name = fileName.split("/").pop();
        window.alert(`"${name}" already exists in this directory.`);
        return;
      }

      try {
        await workspaceApi.createFile(workspaceId, fileName, "");
        onFileTreeChanged?.();
        onFileSelect(fileName);
      } catch (err) {
        console.error("Failed to create file:", err);
        window.alert("Failed to create file.");
      }
      closeContextMenu();
    },
    [workspaceId, files, onFileTreeChanged, onFileSelect, closeContextMenu],
  );

  const handleNewFolder = useCallback(
    async (parentPath?: string) => {
      const prefix = parentPath ? `${parentPath}/` : "";
      const folderName = window.prompt(
        "New folder name:",
        `${prefix}new-folder`,
      );
      if (!folderName) return;

      // Sibling-level duplicate check
      if (hasDuplicateSibling(files, folderName)) {
        const name = folderName.split("/").pop();
        window.alert(`"${name}" already exists in this directory.`);
        return;
      }

      try {
        await workspaceApi.createFile(
          workspaceId,
          `${folderName}/.gitkeep`,
          "",
        );
        onFileTreeChanged?.();
      } catch (err) {
        console.error("Failed to create folder:", err);
        window.alert("Failed to create folder.");
      }
      closeContextMenu();
    },
    [workspaceId, files, onFileTreeChanged, closeContextMenu],
  );

  const handleRename = useCallback(async () => {
    const oldPath = contextMenu.path;
    const newPath = window.prompt("Rename to:", oldPath);
    if (!newPath || newPath === oldPath) {
      closeContextMenu();
      return;
    }

    // Sibling-level duplicate check
    if (hasDuplicateSibling(files, newPath)) {
      const name = newPath.split("/").pop();
      window.alert(`"${name}" already exists in this directory.`);
      return;
    }

    try {
      const content = await workspaceApi.getFileContent(workspaceId, oldPath);
      await workspaceApi.createFile(workspaceId, newPath, content);
      await workspaceApi.deleteFile(workspaceId, oldPath);
      onFileTreeChanged?.();
      onFileSelect(newPath);
    } catch (err) {
      console.error("Failed to rename file:", err);
      window.alert("Failed to rename file.");
    }
    closeContextMenu();
  }, [
    workspaceId,
    files,
    contextMenu.path,
    onFileTreeChanged,
    onFileSelect,
    closeContextMenu,
  ]);

  const handleDelete = useCallback(async () => {
    const confirmed = window.confirm(`Delete "${contextMenu.path}"?`);
    if (!confirmed) {
      closeContextMenu();
      return;
    }
    try {
      await workspaceApi.deleteFile(workspaceId, contextMenu.path);
      onFileTreeChanged?.();
    } catch (err) {
      console.error("Failed to delete file:", err);
    }
    closeContextMenu();
  }, [workspaceId, contextMenu.path, onFileTreeChanged, closeContextMenu]);

  return (
    <div
      className="h-full select-none"
      onClick={() => {
        if (contextMenu.visible) closeContextMenu();
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between border-b border-border px-3 py-2">
        <span className="text-xs font-semibold uppercase text-muted-foreground">
          Explorer
        </span>
        <div className="flex items-center gap-0.5">
          <button
            className="rounded p-0.5 hover:bg-accent"
            title="New File"
            onClick={() => handleNewFile()}
          >
            <FilePlus className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
          <button
            className="rounded p-0.5 hover:bg-accent"
            title="New Folder"
            onClick={() => handleNewFolder()}
          >
            <FolderPlus className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        </div>
      </div>

      {/* File Tree */}
      <div
        className="py-1 flex-1"
        onContextMenu={(e) => {
          e.preventDefault();
          setContextMenu({
            visible: true,
            x: e.clientX,
            y: e.clientY,
            path: "",
            isDirectory: true,
          });
        }}
      >
        {files.length === 0 ? (
          <div className="px-3 py-8 text-center text-xs text-muted-foreground">
            No files in workspace.
            <br />
            Right-click to create a file.
          </div>
        ) : (
          files
            .sort((a, b) => {
              if (a.type === b.type) return a.name.localeCompare(b.name);
              return a.type === "directory" ? -1 : 1;
            })
            .map((node) => (
              <TreeNode
                key={node.path}
                node={node}
                depth={0}
                activeFile={activeFile}
                onFileSelect={onFileSelect}
                onContextMenu={handleContextMenu}
              />
            ))
        )}
      </div>

      {/* Context Menu */}
      {contextMenu.visible && (
        <div
          className="fixed z-50 min-w-[160px] rounded-md border border-border bg-popover shadow-md"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(e) => e.stopPropagation()}
        >
          {!contextMenu.isDirectory && (
            <button
              className="flex w-full items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
              onClick={() => {
                onFileSelect(contextMenu.path);
                closeContextMenu();
              }}
            >
              <ExternalLink className="h-3.5 w-3.5" />
              Open
            </button>
          )}
          <button
            className="flex w-full items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
            onClick={() => handleNewFile(getParentPath())}
          >
            <FilePlus className="h-3.5 w-3.5" />
            New File
          </button>
          <button
            className="flex w-full items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
            onClick={() => handleNewFolder(getParentPath())}
          >
            <FolderPlus className="h-3.5 w-3.5" />
            New Folder
          </button>
          {contextMenu.path && (
            <>
              <div className="my-1 border-t border-border" />
              <button
                className="flex w-full items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
                onClick={handleCopyPath}
              >
                <Copy className="h-3.5 w-3.5" />
                Copy Path
              </button>
            </>
          )}
          {!contextMenu.isDirectory && contextMenu.path && (
            <>
              <button
                className="flex w-full items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
                onClick={handleRename}
              >
                <Pencil className="h-3.5 w-3.5" />
                Rename
              </button>
              <button
                className="flex w-full items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent"
                onClick={handleAiExplain}
              >
                <Wand2 className="h-3.5 w-3.5" />
                AI Explain
              </button>
            </>
          )}
          {contextMenu.path && (
            <>
              <div className="my-1 border-t border-border" />
              <button
                className="flex w-full items-center gap-2 px-3 py-1.5 text-sm text-destructive hover:bg-accent"
                onClick={handleDelete}
              >
                <Trash2 className="h-3.5 w-3.5" />
                Delete
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}

"use client";

import React, { useEffect, useState, useCallback } from "react";
import {
  skillApi,
  SkillDetailView,
  ScriptResult,
} from "@/lib/skill-api";
import {
  FileText,
  Terminal,
  Play,
  ChevronRight,
  Tag,
  ToggleLeft,
  ToggleRight,
  Loader2,
  AlertCircle,
  CheckCircle2,
  XCircle,
} from "lucide-react";

interface SkillDetailPanelProps {
  skillName: string;
  workspaceId?: string;
  onEnableToggle?: (name: string, enabled: boolean) => void;
}

export function SkillDetailPanel({
  skillName,
  workspaceId,
  onEnableToggle,
}: SkillDetailPanelProps) {
  const [skill, setSkill] = useState<SkillDetailView | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeSubFile, setActiveSubFile] = useState<string | null>(null);
  const [subFileContent, setSubFileContent] = useState<string | null>(null);
  const [loadingSubFile, setLoadingSubFile] = useState(false);
  const [scriptResult, setScriptResult] = useState<ScriptResult | null>(null);
  const [runningScript, setRunningScript] = useState<string | null>(null);

  const loadSkill = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await skillApi.getSkill(skillName, workspaceId);
      setSkill(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load skill");
    } finally {
      setLoading(false);
    }
  }, [skillName, workspaceId]);

  useEffect(() => {
    loadSkill();
    setActiveSubFile(null);
    setSubFileContent(null);
    setScriptResult(null);
  }, [loadSkill]);

  const handleReadSubFile = async (path: string) => {
    if (activeSubFile === path) {
      setActiveSubFile(null);
      setSubFileContent(null);
      return;
    }
    setActiveSubFile(path);
    setLoadingSubFile(true);
    try {
      const content = await skillApi.readSkillContent(skillName, path);
      setSubFileContent(content);
    } catch {
      setSubFileContent("Failed to load content");
    } finally {
      setLoadingSubFile(false);
    }
  };

  const handleRunScript = async (scriptPath: string) => {
    setRunningScript(scriptPath);
    setScriptResult(null);
    try {
      const result = await skillApi.runScript(skillName, scriptPath);
      setScriptResult(result);
    } catch (e) {
      setScriptResult({
        exitCode: -1,
        stdout: "",
        stderr: e instanceof Error ? e.message : "Execution failed",
      });
    } finally {
      setRunningScript(null);
    }
  };

  const handleToggleEnable = async () => {
    if (!skill || !workspaceId) return;
    try {
      if (skill.enabled) {
        await skillApi.disableSkill(workspaceId, skillName);
      } else {
        await skillApi.enableSkill(workspaceId, skillName);
      }
      setSkill({ ...skill, enabled: !skill.enabled });
      onEnableToggle?.(skillName, !skill.enabled);
    } catch {
      // silently fail
    }
  };

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error || !skill) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        <div className="text-center">
          <AlertCircle className="mx-auto h-8 w-8 opacity-50" />
          <p className="mt-2 text-sm">{error || "Skill not found"}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col overflow-auto">
      {/* Header */}
      <div className="border-b border-border px-6 py-4">
        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-lg font-semibold text-foreground">
              {skill.name}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {skill.description}
            </p>
          </div>
          {workspaceId && (
            <button
              onClick={handleToggleEnable}
              className="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm transition-colors hover:bg-accent"
              title={skill.enabled ? "Disable skill" : "Enable skill"}
            >
              {skill.enabled ? (
                <>
                  <ToggleRight className="h-4 w-4 text-green-500" />
                  <span className="text-green-500">Enabled</span>
                </>
              ) : (
                <>
                  <ToggleLeft className="h-4 w-4 text-muted-foreground" />
                  <span className="text-muted-foreground">Disabled</span>
                </>
              )}
            </button>
          )}
        </div>

        {/* Metadata */}
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span
            className={`rounded px-2 py-0.5 text-xs font-medium uppercase ${
              skill.scope === "PLATFORM"
                ? "bg-blue-500/10 text-blue-500"
                : skill.scope === "WORKSPACE"
                  ? "bg-green-500/10 text-green-500"
                  : "bg-orange-500/10 text-orange-500"
            }`}
          >
            {skill.scope.toLowerCase()}
          </span>
          <span className="rounded bg-accent px-2 py-0.5 text-xs text-muted-foreground">
            {skill.category.toLowerCase()}
          </span>
          <span className="text-xs text-muted-foreground">
            v{skill.version}
          </span>
          {skill.author && (
            <span className="text-xs text-muted-foreground">
              by {skill.author}
            </span>
          )}
        </div>

        {/* Tags */}
        {skill.tags.length > 0 && (
          <div className="mt-2 flex flex-wrap gap-1">
            {skill.tags.map((tag) => (
              <span
                key={tag}
                className="flex items-center gap-1 rounded-full bg-accent px-2 py-0.5 text-xs text-muted-foreground"
              >
                <Tag className="h-2.5 w-2.5" />
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* SKILL.md Content */}
      <div className="border-b border-border px-6 py-4">
        <h3 className="mb-2 text-sm font-medium text-foreground">
          Content
        </h3>
        <pre className="max-h-96 overflow-auto rounded-md bg-accent/50 p-3 text-xs text-foreground">
          {skill.content}
        </pre>
      </div>

      {/* Sub Files */}
      {skill.subFiles.length > 0 && (
        <div className="border-b border-border px-6 py-4">
          <h3 className="mb-2 text-sm font-medium text-foreground">
            Reference Files ({skill.subFiles.length})
          </h3>
          <div className="space-y-1">
            {skill.subFiles.map((file) => (
              <div key={file.path}>
                <button
                  onClick={() => handleReadSubFile(file.path)}
                  className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent"
                >
                  <ChevronRight
                    className={`h-3 w-3 transition-transform ${
                      activeSubFile === file.path ? "rotate-90" : ""
                    }`}
                  />
                  <FileText className="h-3.5 w-3.5 text-muted-foreground" />
                  <span className="flex-1 text-foreground">{file.path}</span>
                  <span className="text-xs text-muted-foreground">
                    {file.type.toLowerCase()}
                  </span>
                </button>
                {activeSubFile === file.path && (
                  <div className="ml-7 mt-1">
                    {loadingSubFile ? (
                      <div className="py-2 text-xs text-muted-foreground">
                        Loading...
                      </div>
                    ) : (
                      <pre className="max-h-64 overflow-auto rounded-md bg-accent/50 p-2 text-xs text-foreground">
                        {subFileContent}
                      </pre>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Scripts */}
      {skill.scripts.length > 0 && (
        <div className="px-6 py-4">
          <h3 className="mb-2 text-sm font-medium text-foreground">
            Scripts ({skill.scripts.length})
          </h3>
          <div className="space-y-2">
            {skill.scripts.map((script) => (
              <div
                key={script.path}
                className="rounded-md border border-border p-3"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Terminal className="h-3.5 w-3.5 text-muted-foreground" />
                    <span className="text-sm font-medium text-foreground">
                      {script.path}
                    </span>
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-medium uppercase ${
                        script.scriptType === "EXTRACTION"
                          ? "bg-purple-500/10 text-purple-500"
                          : "bg-yellow-500/10 text-yellow-500"
                      }`}
                    >
                      {script.scriptType.toLowerCase()}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {script.language}
                    </span>
                  </div>
                  <button
                    onClick={() => handleRunScript(script.path)}
                    disabled={runningScript !== null}
                    className="flex items-center gap-1 rounded-md bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
                  >
                    {runningScript === script.path ? (
                      <Loader2 className="h-3 w-3 animate-spin" />
                    ) : (
                      <Play className="h-3 w-3" />
                    )}
                    Run
                  </button>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  {script.description}
                </p>
              </div>
            ))}
          </div>

          {/* Script Result */}
          {scriptResult && (
            <div className="mt-3 rounded-md border border-border p-3">
              <div className="flex items-center gap-2 text-sm font-medium">
                {scriptResult.exitCode === 0 ? (
                  <CheckCircle2 className="h-4 w-4 text-green-500" />
                ) : (
                  <XCircle className="h-4 w-4 text-red-500" />
                )}
                <span className="text-foreground">
                  Exit code: {scriptResult.exitCode}
                </span>
              </div>
              {scriptResult.stdout && (
                <pre className="mt-2 max-h-64 overflow-auto rounded bg-accent/50 p-2 text-xs text-foreground">
                  {scriptResult.stdout}
                </pre>
              )}
              {scriptResult.stderr && (
                <pre className="mt-2 max-h-32 overflow-auto rounded bg-red-500/5 p-2 text-xs text-red-500">
                  {scriptResult.stderr}
                </pre>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

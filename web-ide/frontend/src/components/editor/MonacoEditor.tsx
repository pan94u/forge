"use client";

import React, { useRef, useCallback } from "react";
import Editor, { type OnMount, type OnChange } from "@monaco-editor/react";
import type { editor } from "monaco-editor";
import { Lock, Unlock, Wand2 } from "lucide-react";

interface MonacoEditorProps {
  value: string;
  onChange: (value: string | undefined) => void;
  filePath: string;
  readOnly?: boolean;
  onToggleEdit?: () => void;
  theme?: "vs-dark" | "light";
}

function getLanguageFromPath(filePath: string): string {
  const ext = filePath.split(".").pop()?.toLowerCase();
  const languageMap: Record<string, string> = {
    ts: "typescript",
    tsx: "typescriptreact",
    js: "javascript",
    jsx: "javascriptreact",
    json: "json",
    md: "markdown",
    py: "python",
    kt: "kotlin",
    kts: "kotlin",
    java: "java",
    go: "go",
    rs: "rust",
    yaml: "yaml",
    yml: "yaml",
    toml: "toml",
    xml: "xml",
    html: "html",
    css: "css",
    scss: "scss",
    less: "less",
    sql: "sql",
    sh: "shell",
    bash: "shell",
    zsh: "shell",
    dockerfile: "dockerfile",
    graphql: "graphql",
    proto: "protobuf",
    tf: "hcl",
    vue: "vue",
    svelte: "svelte",
  };
  return languageMap[ext ?? ""] ?? "plaintext";
}

export function MonacoEditor({
  value,
  onChange,
  filePath,
  readOnly = false,
  onToggleEdit,
  theme = "vs-dark",
}: MonacoEditorProps) {
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);
  const language = getLanguageFromPath(filePath);

  const handleMount: OnMount = useCallback((editorInstance) => {
    editorRef.current = editorInstance;
    editorInstance.focus();
  }, []);

  const handleChange: OnChange = useCallback(
    (newValue) => {
      onChange(newValue);
    },
    [onChange]
  );

  const handleAiExplain = useCallback(() => {
    const editorInstance = editorRef.current;
    if (!editorInstance) return;

    const selection = editorInstance.getSelection();
    if (!selection) return;

    const selectedText = editorInstance.getModel()?.getValueInRange(selection);
    if (selectedText) {
      const event = new CustomEvent("forge:ai-explain", {
        detail: {
          code: selectedText,
          filePath,
          language,
          startLine: selection.startLineNumber,
          endLine: selection.endLineNumber,
        },
      });
      window.dispatchEvent(event);
    }
  }, [filePath, language]);

  return (
    <div className="flex h-full flex-col">
      {/* Editor Toolbar */}
      <div className="flex h-8 items-center justify-between border-b border-border bg-card px-3">
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span className="rounded bg-muted px-1.5 py-0.5 font-mono">
            {language}
          </span>
          <span className="truncate">{filePath}</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={handleAiExplain}
            className="flex items-center gap-1 rounded px-2 py-0.5 text-xs text-muted-foreground hover:bg-accent hover:text-foreground"
            title="AI Explain Selection"
          >
            <Wand2 className="h-3 w-3" />
            Explain
          </button>
          {onToggleEdit && (
            <button
              onClick={onToggleEdit}
              className={`flex items-center gap-1 rounded px-2 py-0.5 text-xs ${
                readOnly
                  ? "text-muted-foreground hover:bg-accent"
                  : "bg-primary/10 text-primary"
              }`}
              title={readOnly ? "Switch to edit mode" : "Switch to read-only"}
            >
              {readOnly ? (
                <>
                  <Lock className="h-3 w-3" />
                  Read-only
                </>
              ) : (
                <>
                  <Unlock className="h-3 w-3" />
                  Editing
                </>
              )}
            </button>
          )}
        </div>
      </div>

      {/* Monaco Editor */}
      <div className="flex-1">
        <Editor
          height="100%"
          language={language}
          value={value}
          onChange={handleChange}
          theme={theme}
          onMount={handleMount}
          options={{
            readOnly,
            minimap: { enabled: true, maxColumn: 80 },
            fontSize: 13,
            fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
            fontLigatures: true,
            lineNumbers: "on",
            renderWhitespace: "selection",
            bracketPairColorization: { enabled: true },
            guides: {
              bracketPairs: true,
              indentation: true,
            },
            scrollBeyondLastLine: false,
            smoothScrolling: true,
            cursorBlinking: "smooth",
            cursorSmoothCaretAnimation: "on",
            wordWrap: "off",
            tabSize: 2,
            formatOnPaste: true,
            formatOnType: true,
            autoClosingBrackets: "always",
            autoClosingQuotes: "always",
            suggestOnTriggerCharacters: true,
            quickSuggestions: true,
            parameterHints: { enabled: true },
            folding: true,
            foldingStrategy: "indentation",
            showFoldingControls: "mouseover",
            padding: { top: 8 },
          }}
        />
      </div>
    </div>
  );
}

"use client";

import React, { useEffect, useRef, useState, useCallback } from "react";
import { X, Maximize2, Minimize2 } from "lucide-react";

interface TerminalPanelProps {
  workspaceId: string;
}

interface TerminalLine {
  id: number;
  content: string;
  type: "input" | "output" | "error" | "system";
}

export function TerminalPanel({ workspaceId }: TerminalPanelProps) {
  const [lines, setLines] = useState<TerminalLine[]>([
    {
      id: 0,
      content: `Connected to workspace ${workspaceId}`,
      type: "system",
    },
  ]);
  const [input, setInput] = useState("");
  const [isConnected, setIsConnected] = useState(false);
  const [isMaximized, setIsMaximized] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const lineIdRef = useRef(1);

  const addLine = useCallback(
    (content: string, type: TerminalLine["type"]) => {
      const id = lineIdRef.current++;
      setLines((prev) => [...prev.slice(-500), { id, content, type }]);
    },
    []
  );

  useEffect(() => {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const wsUrl = `${protocol}//${window.location.host}/ws/terminal/${workspaceId}`;

    try {
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        addLine("Terminal session started.", "system");
      };

      ws.onmessage = (event) => {
        const data = event.data as string;
        try {
          const parsed = JSON.parse(data) as {
            type: string;
            content: string;
          };
          addLine(
            parsed.content,
            parsed.type === "error" ? "error" : "output"
          );
        } catch {
          addLine(data, "output");
        }
      };

      ws.onerror = () => {
        addLine("Connection error. Retrying...", "error");
        setIsConnected(false);
      };

      ws.onclose = () => {
        addLine("Terminal session ended.", "system");
        setIsConnected(false);
      };
    } catch (err) {
      addLine(
        `Failed to connect: ${err instanceof Error ? err.message : "Unknown error"}`,
        "error"
      );
    }

    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, [workspaceId, addLine]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [lines]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;

    addLine(`$ ${input}`, "input");

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "command", content: input }));
    } else {
      addLine("Not connected to terminal.", "error");
    }

    setInput("");
  };

  const getLineColor = (type: TerminalLine["type"]) => {
    switch (type) {
      case "input":
        return "text-green-400";
      case "error":
        return "text-red-400";
      case "system":
        return "text-yellow-400";
      default:
        return "text-foreground";
    }
  };

  return (
    <div
      className={`flex h-full flex-col bg-[#1e1e1e] ${
        isMaximized ? "fixed inset-0 z-50" : ""
      }`}
    >
      {/* Terminal Header */}
      <div className="flex items-center justify-between border-b border-border px-3 py-1">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-muted-foreground">
            Terminal
          </span>
          <span
            className={`h-1.5 w-1.5 rounded-full ${
              isConnected ? "bg-green-400" : "bg-red-400"
            }`}
          />
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setIsMaximized(!isMaximized)}
            className="rounded p-0.5 hover:bg-accent"
          >
            {isMaximized ? (
              <Minimize2 className="h-3.5 w-3.5 text-muted-foreground" />
            ) : (
              <Maximize2 className="h-3.5 w-3.5 text-muted-foreground" />
            )}
          </button>
          <button className="rounded p-0.5 hover:bg-accent">
            <X className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        </div>
      </div>

      {/* Terminal Output */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-auto p-2 font-mono text-xs leading-relaxed"
        onClick={() => inputRef.current?.focus()}
      >
        {lines.map((line) => (
          <div key={line.id} className={getLineColor(line.type)}>
            <pre className="whitespace-pre-wrap break-all">{line.content}</pre>
          </div>
        ))}
      </div>

      {/* Terminal Input */}
      <form onSubmit={handleSubmit} className="flex items-center border-t border-border">
        <span className="pl-2 font-mono text-xs text-green-400">$</span>
        <input
          ref={inputRef}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          className="flex-1 bg-transparent px-2 py-1.5 font-mono text-xs text-foreground outline-none"
          placeholder={isConnected ? "Enter command..." : "Disconnected"}
          disabled={!isConnected}
          autoFocus
        />
      </form>
    </div>
  );
}

"use client";

import { useState, useEffect, useCallback } from "react";

interface BuildInfo {
  status: string;
  build: {
    commit: string;
    branch: string;
    builtAt: string;
  };
  uptime: string;
  timestamp: string;
}

export function VersionOverlay() {
  const [visible, setVisible] = useState(false);
  const [info, setInfo] = useState<BuildInfo | null>(null);

  const fetchInfo = useCallback(async () => {
    try {
      const res = await fetch("/api/health");
      if (res.ok) setInfo(await res.json());
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    const keys = new Set<string>();

    const onDown = (e: KeyboardEvent) => {
      keys.add(e.key);
      // Ctrl+Shift+V or Cmd+Shift+V
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === "V") {
        e.preventDefault();
        setVisible(true);
        fetchInfo();
      }
    };

    const onUp = (e: KeyboardEvent) => {
      keys.delete(e.key);
      if (e.key === "V" || e.key === "Shift" || e.key === "Control" || e.key === "Meta") {
        setVisible(false);
      }
    };

    const onBlur = () => {
      keys.clear();
      setVisible(false);
    };

    window.addEventListener("keydown", onDown);
    window.addEventListener("keyup", onUp);
    window.addEventListener("blur", onBlur);
    return () => {
      window.removeEventListener("keydown", onDown);
      window.removeEventListener("keyup", onUp);
      window.removeEventListener("blur", onBlur);
    };
  }, [fetchInfo]);

  if (!visible) return null;

  return (
    <div
      style={{
        position: "fixed",
        bottom: 16,
        right: 16,
        zIndex: 9999,
        background: "rgba(0, 0, 0, 0.85)",
        color: "#e0e0e0",
        borderRadius: 8,
        padding: "12px 16px",
        fontFamily: "monospace",
        fontSize: 12,
        lineHeight: 1.6,
        backdropFilter: "blur(8px)",
        border: "1px solid rgba(255,255,255,0.1)",
        minWidth: 240,
        pointerEvents: "none",
      }}
    >
      <div style={{ color: "#7dd3fc", fontWeight: 600, marginBottom: 4 }}>Forge Build Info</div>
      {info ? (
        <>
          <div>commit: <span style={{ color: "#4ade80" }}>{info.build.commit}</span></div>
          <div>branch: <span style={{ color: "#fbbf24" }}>{info.build.branch}</span></div>
          <div>built:  {info.build.builtAt}</div>
          <div>uptime: {info.uptime}</div>
          <div>status: {info.status}</div>
        </>
      ) : (
        <div style={{ color: "#9ca3af" }}>Loading...</div>
      )}
    </div>
  );
}

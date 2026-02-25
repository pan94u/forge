"use client";

import React, { useState, useEffect, useCallback } from "react";
import { ExternalLink, Square, RefreshCw, Globe } from "lucide-react";
import { workspaceApi, type ServiceInfo } from "@/lib/workspace-api";

interface ServicePanelProps {
  workspaceId: string;
}

export function ServicePanel({ workspaceId }: ServicePanelProps) {
  const [services, setServices] = useState<ServiceInfo[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchServices = useCallback(async () => {
    try {
      const data = await workspaceApi.listServices(workspaceId);
      setServices(data);
    } catch {
      // Silently ignore polling errors
    }
  }, [workspaceId]);

  // Poll every 5 seconds
  useEffect(() => {
    fetchServices();
    const interval = setInterval(fetchServices, 5000);
    return () => clearInterval(interval);
  }, [fetchServices]);

  const handleStop = async (port: number) => {
    try {
      await workspaceApi.stopService(workspaceId, port);
      setServices((prev) => prev.filter((s) => s.port !== port));
    } catch (err) {
      console.error("Failed to stop service:", err);
    }
  };

  const handleOpen = (port: number) => {
    const url = workspaceApi.getProxyUrl(workspaceId, port);
    window.open(url, "_blank");
  };

  if (services.length === 0) {
    return (
      <div className="flex items-center gap-2 px-3 py-2 text-xs text-muted-foreground">
        <Globe className="h-3 w-3" />
        <span>No running services</span>
        <button
          onClick={() => { setLoading(true); fetchServices().finally(() => setLoading(false)); }}
          className="ml-auto rounded p-0.5 hover:bg-accent"
          title="Refresh"
        >
          <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>
    );
  }

  return (
    <div className="border-t border-border">
      <div className="flex items-center justify-between px-3 py-1.5 bg-card">
        <span className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
          Services ({services.length})
        </span>
        <button
          onClick={() => { setLoading(true); fetchServices().finally(() => setLoading(false)); }}
          className="rounded p-0.5 hover:bg-accent"
          title="Refresh"
        >
          <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>
      <div className="divide-y divide-border">
        {services.map((svc) => (
          <div key={svc.port} className="flex items-center gap-2 px-3 py-1.5 text-xs">
            <span
              className={`h-2 w-2 rounded-full flex-shrink-0 ${
                svc.status === "running" ? "bg-green-500" : "bg-red-500"
              }`}
            />
            <span className="font-mono font-medium">{svc.port}</span>
            <span className="text-muted-foreground truncate flex-1" title={svc.command}>
              {svc.command.length > 30
                ? svc.command.substring(0, 30) + "..."
                : svc.command}
            </span>
            <button
              onClick={() => handleOpen(svc.port)}
              className="rounded p-1 hover:bg-accent"
              title="Open in Browser"
            >
              <ExternalLink className="h-3 w-3" />
            </button>
            <button
              onClick={() => handleStop(svc.port)}
              className="rounded p-1 hover:bg-destructive/10 text-destructive"
              title="Stop"
            >
              <Square className="h-3 w-3" />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}

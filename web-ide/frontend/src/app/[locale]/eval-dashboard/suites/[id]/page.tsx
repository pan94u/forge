"use client";

import { useParams } from "next/navigation";

export default function SuiteDetailPage() {
  const params = useParams();
  return (
    <div className="min-h-screen bg-background p-6 max-w-6xl mx-auto">
      <h1 className="text-xl font-semibold">Suite Detail</h1>
      <p className="mt-2 text-sm text-muted-foreground">Suite ID: {params.id}</p>
      <p className="mt-4 text-xs text-muted-foreground">Phase 2 — Task + Run + Report</p>
    </div>
  );
}

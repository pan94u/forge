import React from "react";
import "./globals.css";
import { VersionOverlay } from "@/components/common/VersionOverlay";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh" className="dark">
      <body className="min-h-screen bg-background font-sans antialiased">
        {children}
        <VersionOverlay />
      </body>
    </html>
  );
}

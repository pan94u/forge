import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Sidebar } from "@/components/Sidebar";
import { Providers } from "./providers";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Enterprise Console — Forge",
  description: "Forge Enterprise Management Console",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body className={inter.className}>
        <Providers>
          <div className="flex h-screen overflow-hidden bg-gray-950">
            <Sidebar />
            <main className="flex-1 overflow-y-auto">
              <div className="min-h-full p-6">{children}</div>
            </main>
          </div>
        </Providers>
      </body>
    </html>
  );
}

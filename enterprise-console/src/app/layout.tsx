import type { ReactNode } from "react";

// Minimal root layout — actual HTML structure is in app/[locale]/layout.tsx
export default function RootLayout({ children }: { children: ReactNode }) {
  return children as React.ReactElement;
}

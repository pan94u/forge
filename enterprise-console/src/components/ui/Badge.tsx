type Color = "green" | "red" | "yellow" | "blue" | "gray";

import { ReactNode } from "react";

interface BadgeProps {
  children: ReactNode;
  color?: Color;
}

const colorClasses: Record<Color, string> = {
  green: "bg-green-900/50 text-green-300 border border-green-800",
  red: "bg-red-900/50 text-red-300 border border-red-800",
  yellow: "bg-yellow-900/50 text-yellow-300 border border-yellow-800",
  blue: "bg-blue-900/50 text-blue-300 border border-blue-800",
  gray: "bg-gray-700 text-gray-300 border border-gray-600",
};

export function Badge({ children, color = "gray" }: BadgeProps) {
  return (
    <span
      className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colorClasses[color]}`}
    >
      {children}
    </span>
  );
}

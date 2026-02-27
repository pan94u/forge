import { ReactNode } from "react";

interface CardProps {
  children: ReactNode;
  className?: string;
  title?: string;
}

export function Card({ children, className = "", title }: CardProps) {
  return (
    <div
      className={`rounded-lg border border-gray-700 bg-gray-800 p-4 ${className}`}
    >
      {title && (
        <h3 className="mb-3 text-sm font-semibold text-gray-200">{title}</h3>
      )}
      {children}
    </div>
  );
}

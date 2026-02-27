import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        // Reuse web-ide color tokens
        primary: {
          DEFAULT: "#6366f1",
          dark: "#4f46e5",
          light: "#818cf8",
        },
        surface: {
          DEFAULT: "#1e1e2e",
          raised: "#2a2a3e",
          border: "#3a3a5c",
        },
        text: {
          primary: "#e2e2f0",
          secondary: "#a0a0c0",
          muted: "#6b6b8a",
        },
        success: "#22c55e",
        warning: "#f59e0b",
        error: "#ef4444",
        info: "#3b82f6",
      },
    },
  },
  plugins: [],
};

export default config;

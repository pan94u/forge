import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  webpack: (config, { isServer }) => {
    // Monaco Editor webpack configuration
    if (!isServer) {
      config.resolve.fallback = {
        ...config.resolve.fallback,
        fs: false,
        path: false,
        os: false,
      };
    }

    // Handle Monaco Editor workers
    config.module.rules.push({
      test: /\.ttf$/,
      type: "asset/resource",
    });

    return config;
  },
  async rewrites() {
    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
      // WebSocket /ws/ is proxied by Nginx in Docker, or directly by the browser in local dev.
      // Next.js rewrites don't support ws:// protocol, so we proxy via http:// here
      // (only used in local dev without Nginx).
      {
        source: "/ws/:path*",
        destination: `${backendUrl}/ws/:path*`,
      },
    ];
  },
};

export default withNextIntl(nextConfig);

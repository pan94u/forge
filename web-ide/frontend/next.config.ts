import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  // Disable Turbopack to use webpack (for Monaco Editor compatibility)
  turbopack: false,
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
    // API Gateway URL (via Nginx in production)
    const gatewayUrl = process.env.GATEWAY_URL || "http://localhost:9443";
    // Direct backend URL (for local development without Gateway)
    const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
    // Use Gateway if configured, otherwise use direct backend
    const apiUrl = process.env.USE_GATEWAY === "true" ? gatewayUrl : backendUrl;

    return [
      // API requests go through Gateway (or directly to backend)
      {
        source: "/api/:path*",
        destination: `${apiUrl}/api/:path*`,
      },
      // WebSocket: only works with direct backend (Gateway doesn't support ws:// yet)
      {
        source: "/ws/:path*",
        destination: `${backendUrl}/ws/:path*`,
      },
    ];
  },
};

export default nextConfig;

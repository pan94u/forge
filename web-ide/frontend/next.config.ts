import type { NextConfig } from "next";

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
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_URL || "http://localhost:8080"}/api/:path*`,
      },
      {
        source: "/ws/:path*",
        destination: `${process.env.BACKEND_WS_URL || "ws://localhost:8080"}/ws/:path*`,
      },
    ];
  },
};

export default nextConfig;

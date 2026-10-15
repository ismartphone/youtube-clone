import type { NextConfig } from "next";

// URL de l'API NestJS. Le navigateur n'appelle jamais l'API en direct : il passe par /api,
// que Next.js relaie vers l'API. Le back-office et l'API partagent ainsi la même origine,
// sans configuration CORS côté backend. Le back-office ne contacte jamais PostgreSQL.
// API_URL est lue à la build (npm run build), pas au démarrage : la changer impose de reconstruire.
const apiUrl = process.env.API_URL ?? "http://localhost:3000";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/:path*` }];
  },
};

export default nextConfig;

/// <reference types="vitest/config" />
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

// The backend service name inside the docker-compose network is "backend".
// When running outside Docker it can be overridden with VITE_BACKEND_URL.
const backend = process.env.VITE_BACKEND_URL ?? "http://backend:8080";

export default defineConfig({
  plugins: [react()],
  // NB: sockjs-client references Node's `global`; we shim it at runtime in
  // index.html (a Vite `define` does not reach pre-bundled deps in dev).
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      "/api": { target: backend, changeOrigin: true },
      "/ws": { target: backend, changeOrigin: true, ws: true },
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});

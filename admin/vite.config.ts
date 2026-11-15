import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Built into dist/ and served by the backend at /admin. In dev, API calls are proxied to the backend.
export default defineConfig({
  base: "/admin/",
  plugins: [react()],
  server: { port: 5174, proxy: { "/api": process.env.API_PROXY ?? "http://127.0.0.1:8010" } },
  test: { environment: "jsdom" },
});

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

// API calls go to /api; in dev they are proxied to the FastAPI backend.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["icon.svg"],
      manifest: {
        name: "DelhiRaahi — Delhi bus travel",
        short_name: "DelhiRaahi",
        description: "Plan Delhi bus trips by time and expected crowding, track buses, check fares.",
        theme_color: "#0f766e",
        background_color: "#ffffff",
        display: "standalone",
        start_url: "/",
        lang: "en",
        icons: [{ src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any maskable" }],
      },
      workbox: {
        navigateFallback: "/index.html",
        // timetable-type answers may be served from cache when offline; live data never is
        runtimeCaching: [
          { urlPattern: /\/api\/v1\/(stops|routes|fares\/rules|insights)/, handler: "StaleWhileRevalidate",
            options: { cacheName: "api-static", expiration: { maxEntries: 200, maxAgeSeconds: 86400 } } },
          { urlPattern: /tile\.openstreetmap\.org/, handler: "CacheFirst",
            options: { cacheName: "tiles", expiration: { maxEntries: 500, maxAgeSeconds: 604800 } } },
        ],
      },
    }),
  ],
  // API_PROXY lets the backend run on another port, e.g. API_PROXY=http://127.0.0.1:8010 npm run dev
  server: { port: 5173, proxy: { "/api": process.env.API_PROXY ?? "http://127.0.0.1:8000" } },
  test: { environment: "jsdom" },
});

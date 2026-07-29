import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwind from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwind()],
  // Relative base so the built site works when hosted from a sub-path (GitHub Pages).
  base: "./",
});

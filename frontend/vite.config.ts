/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { outDir: 'dist', emptyOutDir: true },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  // Tests live under tests/ rather than beside the modules they cover, so src/ stays
  // production-only and the build never has to exclude test files.
  test: { environment: 'jsdom', include: ['tests/**/*.test.ts?(x)'] },
});

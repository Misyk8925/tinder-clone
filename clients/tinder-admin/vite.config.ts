import { defineConfig } from 'vite';

// The container serves this app behind nginx, which proxies /api to the gateway so the
// browser only ever sees one origin. The dev server mirrors that proxy, so no CORS
// origin has to be opened up for the admin app in either environment.
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: process.env.GATEWAY_URL ?? 'http://localhost:8222',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
});

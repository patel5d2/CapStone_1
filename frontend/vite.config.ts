import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // The Spring Boot API this SPA talks to during local development.
      // In production the built assets are served by Spring itself, so no
      // proxy is needed there — see build.outDir below.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/student': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Ships the compiled SPA straight into the Spring Boot static resources
    // folder so `./mvnw spring-boot:run` (and the Docker image) serve it
    // without a separate frontend server or container in production.
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})

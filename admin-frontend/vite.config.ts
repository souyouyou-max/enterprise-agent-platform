import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'url'
import path from 'path'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/analyze': {
        target: 'http://localhost:8099',
        changeOrigin: true,
      },
      '/health': {
        target: 'http://localhost:8099',
        changeOrigin: true,
      },
    },
  },
})

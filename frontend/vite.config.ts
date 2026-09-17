import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 17532,
    proxy: {
      // 前端开发时反向代理 API 到后端（后端端口 18088）
      '/api': {
        target: 'http://localhost:18088',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
  },
})
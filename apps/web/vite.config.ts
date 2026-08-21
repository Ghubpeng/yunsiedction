import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 用户端 Web MVP（Stage 1.8）。
// /api 代理到本地 Java 服务（api-design：所有请求走 /api/v1）。
// VITE_API_BASE_URL 可覆盖为完整后端地址（生产部署用）；默认走同源代理。
const proxy = {
  '/api': {
    target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy,
  },
  preview: {
    port: 5173,
    proxy,
  },
})

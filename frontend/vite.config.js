import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发服务器把 /api 代理到后端 8080，避免跨域、也更接近生产形态
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      // HLS 视频流代理到 MediaMTX，转成同源避免跨域/cookie 问题（W9）
      '/cam01': 'http://localhost:8888'
    }
  }
})

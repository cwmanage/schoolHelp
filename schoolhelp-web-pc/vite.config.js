import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: true,
    port: 5174,
    proxy: {
      // 前端开发时把 /api 代理到网关 8080
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        configure: (proxy) => {
          // 防御性处理：剔除出站请求的 Expect: 100-continue 头。
          // 背景：.NET HttpClient / curl 显式带该头时，http-proxy 转发给 Spring
          // Cloud Gateway(Netty) 会触发 “Data after Connection: close” → 502。
          // 真实浏览器 fetch/XHR 不发此头（已用 Node fetch 验证 5/5 全通），
          // 此块仅作兜底，避免非浏览器客户端连 dev server 时踩坑。
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('Expect')
            proxyReq.removeHeader('expect')
          })
        }
      }
    }
  }
})

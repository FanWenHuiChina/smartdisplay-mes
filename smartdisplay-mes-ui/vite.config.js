import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const enableMockFallback = mode !== 'production' || process.env.VITE_ENABLE_MOCK_FALLBACK === 'true'

  return {
    plugins: [vue()],
    define: {
      __DEV_MOCK_FALLBACK__: JSON.stringify(enableMockFallback)
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: '0.0.0.0',
      port: 8888,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})

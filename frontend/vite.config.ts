import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath } from 'node:url'

export default defineConfig(({ mode }) => {
  const root = fileURLToPath(new URL('..', import.meta.url))
  const port = process.env.SERVER_PORT || loadEnv(mode, root, 'SERVER_').SERVER_PORT || '8080'
  if (!/^[0-9]+$/.test(port) || Number(port) < 1 || Number(port) > 65535) throw new Error('SERVER_PORT must be a valid port')
  const proxy = { '/api': { target: `http://127.0.0.1:${port}`, changeOrigin: true, timeout: 20_000 } }
  return {
  plugins: [vue()],
  server: {
    fs: { allow: [fileURLToPath(new URL('.', import.meta.url)), fileURLToPath(new URL('../backend/src/main/resources/fixtures', import.meta.url))] },
    port: 5173,
    strictPort: true,
    proxy,
  },
  preview: { port: 5173, strictPort: true, proxy },
  }
})

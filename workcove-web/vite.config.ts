import fs from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

import { applicationHostPlugin } from './dev/applicationHostPlugin.mjs'

const apiTarget =
  process.env.WORKCOVE_API_TARGET ?? 'http://127.0.0.1:8790'
const releaseInfo = JSON.parse(
  fs.readFileSync(
    fileURLToPath(
      new URL('../workcove-desktop/release-info.json', import.meta.url),
    ),
    'utf8',
  ),
)

export default defineConfig({
  define: {
    __WORKCOVE_VERSION__: JSON.stringify(releaseInfo.version),
    __WORKCOVE_RELEASE_CODE__: JSON.stringify(releaseInfo.releaseCode),
  },
  plugins: [vue(), applicationHostPlugin()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  clearScreen: false,
  server: {
    host: '127.0.0.1',
    port: 32581,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
      '/ws': {
        target: apiTarget,
        changeOrigin: true,
        ws: true,
      },
    },
  },
})

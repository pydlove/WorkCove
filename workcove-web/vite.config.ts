import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, type Plugin } from 'vite'

import { applicationHostPlugin } from './dev/applicationHostPlugin.mjs'

const apiTarget =
  process.env.WORKCOVE_API_TARGET ?? 'http://127.0.0.1:8790'
const releaseInfoPath = fileURLToPath(
  new URL('../workcove-desktop/release-info.json', import.meta.url),
)
const releaseInfoModuleId = 'virtual:workcove-release-info'
const resolvedReleaseInfoModuleId = `\0${releaseInfoModuleId}`

function releaseInfoPlugin(): Plugin {
  return {
    name: 'workcove-release-info',
    resolveId(id) {
      return id === releaseInfoModuleId
        ? resolvedReleaseInfoModuleId
        : null
    },
    load(id) {
      if (id !== resolvedReleaseInfoModuleId) return null
      const releaseInfo = JSON.parse(
        fs.readFileSync(releaseInfoPath, 'utf8'),
      )
      return [
        `export const version = ${JSON.stringify(releaseInfo.version)}`,
        `export const releaseCode = ${JSON.stringify(releaseInfo.releaseCode)}`,
      ].join('\n')
    },
    buildStart() {
      this.addWatchFile(releaseInfoPath)
    },
    configureServer(server) {
      server.watcher.add(releaseInfoPath)
      server.watcher.on('change', (file) => {
        if (path.resolve(file) === releaseInfoPath) {
          void server.restart()
        }
      })
    },
  }
}

export default defineConfig({
  plugins: [releaseInfoPlugin(), vue(), applicationHostPlugin()],
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

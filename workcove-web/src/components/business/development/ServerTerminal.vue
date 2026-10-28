<script setup lang="ts">
import { FitAddon } from '@xterm/addon-fit'
import { Terminal } from '@xterm/xterm'
import '@xterm/xterm/css/xterm.css'
import {
  ChevronLeft,
  Circle,
  Globe2,
  RefreshCw,
  Settings2,
  X,
  Zap,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import QuickCommandManagerDialog from '@/components/business/development/QuickCommandManagerDialog.vue'
import { useAuthStore } from '@/stores/useAuthStore'
import { useQuickCommandStore } from '@/stores/useQuickCommandStore'
import type { ManagedServer } from '@/types/managedServer'
import type { TerminalQuickCommand } from '@/types/quickCommand'

const props = defineProps<{
  server: ManagedServer
}>()

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const session = useAuthStore()
const quickCommands = useQuickCommandStore()
const terminalHost = ref<HTMLElement | null>(null)
const status = ref<'connecting' | 'connected' | 'closed' | 'error'>(
  'connecting',
)
const fingerprint = ref(props.server.hostKeyFingerprint ?? '')
const firstTrust = ref(false)
const quickCommandManagerOpen = ref(false)
const activeQuickCommandId = ref('')

const serverQuickCommands = computed(() =>
  quickCommands.commands
    .filter(
      (command) =>
        command.workspaceId === props.server.workspaceId &&
        (command.scope === 'global' ||
          command.serverId === props.server.id),
    )
    .sort(
      (left, right) =>
        Number(right.scope === 'global') -
        Number(left.scope === 'global'),
    ),
)

let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let socket: WebSocket | null = null
let resizeObserver: ResizeObserver | null = null
let failureTimer: number | null = null
let connectionErrorShown = false

watch(
  () => session.email,
  (email) => quickCommands.load(email),
  { immediate: true },
)

function websocketUrl() {
  const url = new URL(
    `/ws/development/servers/${props.server.id}/ssh`,
    window.location.href,
  )
  url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const dimensions = fitAddon?.proposeDimensions()
  url.searchParams.set('cols', String(dimensions?.cols ?? 120))
  url.searchParams.set('rows', String(dimensions?.rows ?? 32))
  return url.toString()
}

function send(message: unknown) {
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(message))
  }
}

function fitTerminal() {
  if (!fitAddon || !terminal) return
  fitAddon.fit()
  send({
    type: 'resize',
    columns: terminal.cols,
    rows: terminal.rows,
  })
}

function connect() {
  if (!terminal) return
  activeQuickCommandId.value = ''
  socket?.close()
  if (failureTimer !== null) {
    window.clearTimeout(failureTimer)
    failureTimer = null
  }
  connectionErrorShown = false
  status.value = 'connecting'
  fingerprint.value = props.server.hostKeyFingerprint ?? ''
  firstTrust.value = false
  terminal.reset()
  terminal.writeln('\x1b[38;5;110m正在建立 SSH 连接…\x1b[0m')
  const currentSocket = new WebSocket(websocketUrl())
  socket = currentSocket

  currentSocket.addEventListener('message', (event) => {
    if (socket !== currentSocket) return
    let message: {
      type?: string
      data?: string
      fingerprint?: string
      firstTrust?: boolean
      message?: string
    }
    try {
      message = JSON.parse(String(event.data)) as typeof message
    } catch {
      return
    }

    if (message.type === 'output' && message.data) {
      terminal?.write(message.data)
      return
    }
    if (message.type === 'connected') {
      if (failureTimer !== null) {
        window.clearTimeout(failureTimer)
        failureTimer = null
      }
      connectionErrorShown = false
      status.value = 'connected'
      fingerprint.value = message.fingerprint ?? ''
      firstTrust.value = Boolean(message.firstTrust)
      if (message.fingerprint) {
        terminal?.writeln(
          `\x1b[38;5;244mSSH ${message.firstTrust ? '首次信任主机' : '主机指纹'} ${message.fingerprint}\x1b[0m\r\n`,
        )
      }
      terminal?.focus()
      return
    }
    if (message.type === 'error' || message.type === 'closed') {
      if (failureTimer !== null) {
        window.clearTimeout(failureTimer)
        failureTimer = null
      }
      connectionErrorShown = true
      status.value = message.type === 'error' ? 'error' : 'closed'
      if (message.message) {
        terminal?.writeln(
          `\r\n\x1b[38;5;203m${message.message}\x1b[0m`,
        )
      }
    }
  })

  currentSocket.addEventListener('error', () => {
    if (socket !== currentSocket) return
    status.value = 'error'
    if (!connectionErrorShown && failureTimer === null) {
      failureTimer = window.setTimeout(() => {
        failureTimer = null
        writeConnectionFailure(
          'SSH 连接失败，请检查服务器地址、端口、用户名和密码。',
        )
      }, 120)
    }
  })

  currentSocket.addEventListener('close', () => {
    if (socket !== currentSocket) return
    if (failureTimer !== null) {
      window.clearTimeout(failureTimer)
      failureTimer = null
    }
    if (status.value === 'connecting') {
      status.value = 'error'
      writeConnectionFailure(
        'SSH 连接失败，请检查服务器地址、端口、用户名和密码。',
      )
    } else if (status.value === 'error') {
      writeConnectionFailure(
        'SSH 连接失败，请检查服务器地址、端口、用户名和密码。',
      )
    } else if (status.value === 'connected') {
      status.value = 'closed'
      writeConnectionFailure('SSH 连接已断开')
    }
  })
}

function writeConnectionFailure(message: string) {
  if (connectionErrorShown) return
  connectionErrorShown = true
  terminal?.writeln(
    `\r\n\x1b[38;5;203m${message}\x1b[0m`,
  )
}

onMounted(async () => {
  await nextTick()
  if (!terminalHost.value) return

  terminal = new Terminal({
    cursorBlink: true,
    cursorStyle: 'block',
    fontFamily:
      '"SFMono-Regular", "SF Mono", Menlo, "PingFang SC", monospace',
    fontSize: 13,
    lineHeight: 1.25,
    scrollback: 10_000,
    theme: {
      background: '#11151b',
      foreground: '#d7dde5',
      cursor: '#79c0ff',
      selectionBackground: '#264f78',
      black: '#11151b',
      red: '#ff7b72',
      green: '#7ee787',
      yellow: '#d29922',
      blue: '#79c0ff',
      magenta: '#d2a8ff',
      cyan: '#56d4dd',
      white: '#d7dde5',
      brightBlack: '#6e7681',
      brightRed: '#ffa198',
      brightGreen: '#aff5b4',
      brightYellow: '#e3b341',
      brightBlue: '#a5d6ff',
      brightMagenta: '#e2c5ff',
      brightCyan: '#b3f0f5',
      brightWhite: '#ffffff',
    },
  })
  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.open(terminalHost.value)
  fitAddon.fit()
  terminal.onData((data) => send({ type: 'input', data }))
  terminal.onResize(({ cols, rows }) =>
    send({ type: 'resize', columns: cols, rows }),
  )
  resizeObserver = new ResizeObserver(() => fitTerminal())
  resizeObserver.observe(terminalHost.value)
  connect()
  terminal.focus()
})

onBeforeUnmount(() => {
  if (failureTimer !== null) {
    window.clearTimeout(failureTimer)
    failureTimer = null
  }
  resizeObserver?.disconnect()
  socket?.close()
  terminal?.dispose()
})

function closeTerminal() {
  send({ type: 'close' })
  socket?.close()
  emit('close')
}

function insertQuickCommand(command: TerminalQuickCommand) {
  if (status.value !== 'connected') return
  activeQuickCommandId.value = command.id
  send({ type: 'input', data: command.command })
  terminal?.focus()
}

function statusLabel() {
  if (status.value === 'connected') return '已连接'
  if (status.value === 'error') return '连接失败'
  if (status.value === 'closed') return '已断开'
  return '正在连接'
}
</script>

<template>
  <section class="server-terminal" aria-label="服务器终端">
    <header class="server-terminal__bar">
      <div class="server-terminal__traffic" aria-hidden="true">
        <span class="server-terminal__close-dot" />
        <span class="server-terminal__minimize-dot" />
        <span class="server-terminal__zoom-dot" />
      </div>
      <button
        class="server-terminal__back"
        type="button"
        aria-label="返回服务器列表"
        @click="closeTerminal"
      >
        <ChevronLeft :size="15" />
      </button>
      <div class="server-terminal__title">
        <strong>{{ server.name }}</strong>
        <small>{{ server.username }}@{{ server.host }}:{{ server.port }}</small>
      </div>
      <span
        class="server-terminal__status"
        :class="`server-terminal__status--${status}`"
        :title="fingerprint"
      >
        <Circle :size="7" fill="currentColor" />
        {{ statusLabel() }}
      </span>
      <button
        v-if="status === 'closed' || status === 'error'"
        class="server-terminal__retry"
        type="button"
        @click="connect"
      >
        <RefreshCw :size="13" />
        重连
      </button>
      <button
        class="server-terminal__close"
        type="button"
        aria-label="关闭终端"
        @click="closeTerminal"
      >
        <X :size="15" />
      </button>
    </header>
    <div
      class="server-terminal__quick-commands"
      role="group"
      aria-label="快捷命令"
    >
      <span class="server-terminal__quick-label">
        <Zap :size="12" />
        快捷命令
      </span>
      <div class="server-terminal__quick-list">
        <button
          v-for="command in serverQuickCommands"
          :key="command.id"
          type="button"
          :class="{
            'is-active': activeQuickCommandId === command.id,
          }"
          :disabled="status !== 'connected'"
          :aria-pressed="activeQuickCommandId === command.id"
          :title="
            command.scope === 'global'
              ? `全局命令：${command.command}`
              : `仅当前服务器：${command.command}`
          "
          @click="insertQuickCommand(command)"
        >
          <Globe2 v-if="command.scope === 'global'" :size="10" />
          {{ command.name }}
        </button>
        <span v-if="serverQuickCommands.length === 0">
          暂无快捷命令
        </span>
      </div>
      <button
        class="server-terminal__quick-manage"
        type="button"
        aria-label="管理快捷命令"
        title="管理快捷命令"
        @click="quickCommandManagerOpen = true"
      >
        <Settings2 :size="13" />
        管理
      </button>
    </div>
    <div ref="terminalHost" class="server-terminal__host" />
    <footer class="server-terminal__footer">
      <span v-if="fingerprint">主机指纹 {{ fingerprint }}</span>
      <span v-if="firstTrust">首次连接，已记录该指纹</span>
    </footer>

    <QuickCommandManagerDialog
      v-if="quickCommandManagerOpen"
      :server="server"
      @close="quickCommandManagerOpen = false"
      @notify="emit('notify', $event)"
    />
  </section>
</template>

<style scoped>
.server-terminal {
  display: flex;
  height: 100%;
  min-height: 0;
  max-height: 100%;
  overflow: hidden;
  flex-direction: column;
  background: #11151b;
  border-radius: var(--radius-window);
  box-shadow:
    0 0 0 0.5px rgb(255 255 255 / 10%),
    0 18px 44px rgb(0 0 0 / 34%);
}

.server-terminal__bar {
  display: grid;
  min-height: 44px;
  grid-template-columns: auto auto minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  color: rgb(255 255 255 / 82%);
  background: #181d24;
  border-bottom: 1px solid rgb(255 255 255 / 8%);
}

.server-terminal__traffic {
  display: flex;
  gap: 7px;
}

.server-terminal__traffic span {
  width: 11px;
  height: 11px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 20%);
}

.server-terminal__close-dot {
  background: var(--traffic-close);
}

.server-terminal__minimize-dot {
  background: var(--traffic-minimize);
  opacity: 0.55;
}

.server-terminal__zoom-dot {
  background: var(--traffic-zoom);
  opacity: 0.55;
}

.server-terminal__back,
.server-terminal__close,
.server-terminal__retry {
  display: inline-flex;
  height: 26px;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 0 7px;
  color: rgb(255 255 255 / 58%);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-control);
}

.server-terminal__back:hover,
.server-terminal__retry:hover,
.server-terminal__close:hover {
  color: #ffffff;
  background: rgb(255 255 255 / 7%);
}

.server-terminal__title {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.server-terminal__title strong {
  overflow: hidden;
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-terminal__title small {
  overflow: hidden;
  margin-top: 1px;
  color: rgb(255 255 255 / 38%);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-terminal__status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: rgb(255 255 255 / 48%);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.server-terminal__status--connected {
  color: #7ee787;
}

.server-terminal__status--error {
  color: #ff7b72;
}

.server-terminal__quick-commands {
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  background: #151a21;
  border-bottom: 1px solid rgb(255 255 255 / 6%);
}

.server-terminal__quick-label {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  color: rgb(255 255 255 / 42%);
  font-size: var(--font-caption);
}

.server-terminal__quick-label svg {
  color: #d2a8ff;
}

.server-terminal__quick-list {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 5px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.server-terminal__quick-list > button {
  display: inline-flex;
  height: 22px;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  color: rgb(255 255 255 / 68%);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: rgb(255 255 255 / 6%);
  border-radius: var(--radius-badge);
}

.server-terminal__quick-list > button:hover:not(:disabled) {
  color: #ffffff;
  background: rgb(121 192 255 / 16%);
}

.server-terminal__quick-list > button.is-active {
  color: #ffffff;
  background: rgb(121 192 255 / 22%);
  box-shadow:
    inset 0 0 0 1px rgb(121 192 255 / 38%),
    0 0 12px rgb(121 192 255 / 12%);
}

.server-terminal__quick-list > button:focus-visible {
  outline: 2px solid #79c0ff;
  outline-offset: 1px;
}

.server-terminal__quick-list > button:disabled {
  opacity: 0.38;
}

.server-terminal__quick-list > span {
  color: rgb(255 255 255 / 25%);
  font-size: var(--font-caption);
  white-space: nowrap;
}

.server-terminal__quick-manage {
  display: inline-flex;
  height: 24px;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  padding: 0 7px;
  color: rgb(255 255 255 / 45%);
  font-size: var(--font-caption);
  background: transparent;
  border-radius: var(--radius-control);
}

.server-terminal__quick-manage:hover {
  color: #ffffff;
  background: rgb(255 255 255 / 7%);
}

.server-terminal__host {
  position: relative;
  min-height: 0;
  flex: 1;
  padding: var(--space-3);
  overflow: hidden;
}

.server-terminal__host :deep(.xterm) {
  height: 100%;
  overflow: hidden;
}

.server-terminal__host :deep(.xterm-viewport) {
  overflow-y: scroll !important;
}

.server-terminal__footer {
  display: flex;
  min-height: 26px;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-3);
  color: rgb(255 255 255 / 30%);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  border-top: 1px solid rgb(255 255 255 / 6%);
}

@media (max-width: 640px) {
  .server-terminal {
    min-height: 0;
    border-radius: var(--radius-panel);
  }

  .server-terminal__bar {
    display: flex;
  }

  .server-terminal__traffic {
    display: none;
  }

  .server-terminal__title {
    flex: 1;
  }

  .server-terminal__title small {
    display: none;
  }

  .server-terminal__retry {
    flex: 0 0 auto;
  }

  .server-terminal__quick-label {
    display: none;
  }

  .server-terminal__quick-manage {
    width: 26px;
    padding: 0;
    font-size: 0;
    justify-content: center;
  }

  .server-terminal__footer {
    min-height: 38px;
    align-items: flex-start;
    justify-content: center;
    gap: 2px;
    flex-direction: column;
  }

  .server-terminal__footer span {
    overflow: hidden;
    max-width: 100%;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>

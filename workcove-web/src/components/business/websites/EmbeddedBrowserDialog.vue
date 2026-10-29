<script setup lang="ts">
import {
  ArrowLeft,
  ArrowRight,
  Ellipsis,
  Globe2,
  GripVertical,
  KeyRound,
  Maximize2,
  Minimize2,
  Minus,
  Plus,
  RotateCw,
  X,
} from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type CSSProperties,
} from 'vue'

import UiDialog from '@/components/ui/UiDialog.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiFidgetSpinner from '@/components/ui/UiFidgetSpinner.vue'
import VaultPasswordPicker from '@/components/business/vault/VaultPasswordPicker.vue'
import type { VaultEntry } from '@/types/vault'

const props = defineProps<{
  url: string
  title?: string
  workspaceId?: string
}>()

const emit = defineEmits<{
  close: []
  notify: [message: string]
}>()

const desktop = window.workCoveDesktop
const surface = ref<HTMLElement | null>(null)
const restoreButton = ref<HTMLButtonElement | null>(null)
const addressInput = ref<HTMLInputElement | null>(null)
const initialAddress = props.url === 'about:blank' ? '' : props.url
const address = ref(initialAddress)
const currentUrl = ref(props.url)
const pageTitle = ref(props.title ?? '')
const tabs = ref<EmbeddedBrowserTabState[]>([])
const activeTabId = ref('')
const loading = ref(true)
const canGoBack = ref(false)
const canGoForward = ref(false)
const fullscreen = ref(false)
const minimized = ref(false)
const browserError = ref('')
const addressFocused = ref(false)
const credentialPickerOpen = ref(false)
const vaultPickerBusy = ref(false)
const credentialFillBusy = ref(false)
const credentialPickerError = ref('')
const automationCredentialRequestId = ref('')
const automationCredentialName = ref('')
const dock = ref<HTMLElement | null>(null)
const dockPosition = ref<{ left: number; top: number } | null>(null)
const dockDragging = ref(false)
const previousFocus = ref<HTMLElement | null>(
  document.activeElement instanceof HTMLElement
    ? document.activeElement
    : null,
)
let resizeObserver: ResizeObserver | null = null
let dockResizeObserver: ResizeObserver | null = null
let syncFrame = 0
let dockClampFrame = 0
let closed = false
let dockDragOffset = { x: 0, y: 0 }

const dockMargin = 8

const activeTab = computed(() =>
  tabs.value.find((tab) => tab.id === activeTabId.value),
)
const windowTitle = computed(
  () =>
    activeTab.value?.title ||
    pageTitle.value ||
    props.title ||
    '内置浏览器',
)
const browserLocationLabel = computed(() =>
  currentUrl.value === 'about:blank' ? '新建浏览' : currentUrl.value,
)
const currentOrigin = computed(() => {
  try {
    const parsed = new URL(currentUrl.value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
      ? parsed.origin
      : ''
  } catch {
    return ''
  }
})
const credentialPickerBusy = computed(
  () => vaultPickerBusy.value || credentialFillBusy.value,
)
const canFillFromVault = computed(
  () =>
    Boolean(props.workspaceId) &&
    Boolean(desktop?.fillEmbeddedBrowserCredentials) &&
    Boolean(currentOrigin.value),
)
const dockStyle = computed<CSSProperties | undefined>(() =>
  dockPosition.value
    ? {
        left: `${dockPosition.value.left}px`,
        top: `${dockPosition.value.top}px`,
      }
    : undefined,
)

function withDefaultProtocol(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return ''
  return /^[a-z][a-z\d+.-]*:\/\//i.test(trimmed)
    ? trimmed
    : `http://${trimmed}`
}

function syncBrowserBounds() {
  if (
    minimized.value ||
    credentialPickerOpen.value ||
    !surface.value ||
    !desktop
  ) {
    return
  }
  const rect = surface.value.getBoundingClientRect()
  if (rect.width < 1 || rect.height < 1) return
  desktop.setEmbeddedBrowserBounds({
    x: Math.round(rect.left),
    y: Math.round(rect.top),
    width: Math.round(rect.width),
    height: Math.round(rect.height),
  })
}

function scheduleBoundsSync() {
  if (minimized.value || credentialPickerOpen.value) return
  if (syncFrame) window.cancelAnimationFrame(syncFrame)
  syncFrame = window.requestAnimationFrame(() => {
    syncFrame = 0
    syncBrowserBounds()
  })
}

function clampDockPosition(left: number, top: number) {
  const rect = dock.value?.getBoundingClientRect()
  const width = rect?.width ?? 0
  const height = rect?.height ?? 0
  return {
    left: Math.min(
      Math.max(dockMargin, left),
      Math.max(dockMargin, window.innerWidth - width - dockMargin),
    ),
    top: Math.min(
      Math.max(dockMargin, top),
      Math.max(dockMargin, window.innerHeight - height - dockMargin),
    ),
  }
}

function startDockDrag(event: PointerEvent) {
  if (
    event.button !== 0 ||
    !dock.value ||
    (event.target as Element | null)?.closest('button')
  ) {
    return
  }
  const rect = dock.value.getBoundingClientRect()
  dockPosition.value = {
    left: rect.left,
    top: rect.top,
  }
  dockDragOffset = {
    x: event.clientX - rect.left,
    y: event.clientY - rect.top,
  }
  dockDragging.value = true
  dock.value.setPointerCapture(event.pointerId)
  event.preventDefault()
}

function moveDockDrag(event: PointerEvent) {
  if (!dockDragging.value) return
  dockPosition.value = clampDockPosition(
    event.clientX - dockDragOffset.x,
    event.clientY - dockDragOffset.y,
  )
}

function endDockDrag(event?: PointerEvent) {
  if (!dockDragging.value) return
  dockDragging.value = false
  if (
    event &&
    dock.value?.hasPointerCapture(event.pointerId)
  ) {
    dock.value.releasePointerCapture(event.pointerId)
  }
}

function handleWindowResize() {
  scheduleBoundsSync()
  if (!dockPosition.value) return
  if (dockClampFrame) window.cancelAnimationFrame(dockClampFrame)
  dockClampFrame = window.requestAnimationFrame(() => {
    dockClampFrame = 0
    if (!dockPosition.value) return
    dockPosition.value = clampDockPosition(
      dockPosition.value.left,
      dockPosition.value.top,
    )
  })
}

function applyBrowserState(state: EmbeddedBrowserState) {
  if (!state.active) return
  tabs.value = Array.isArray(state.tabs) ? state.tabs : []
  activeTabId.value = state.activeTabId || ''
  const tab = tabs.value.find(
    (item) => item.id === activeTabId.value,
  )
  currentUrl.value = tab?.url || state.url || currentUrl.value
  pageTitle.value = tab?.title || state.title || ''
  loading.value = tab?.loading ?? state.loading
  canGoBack.value = tab?.canGoBack ?? state.canGoBack
  canGoForward.value = tab?.canGoForward ?? state.canGoForward
  fullscreen.value = state.fullscreen
  browserError.value = tab?.error ?? state.error
  if (!addressFocused.value) {
    address.value =
      currentUrl.value === 'about:blank' ? '' : currentUrl.value
  }
}

async function navigate() {
  const target = withDefaultProtocol(address.value)
  if (!target || !desktop) return
  browserError.value = ''
  address.value = target
  try {
    const error = await desktop.navigateEmbeddedBrowser(
      target,
      activeTabId.value,
    )
    if (error) browserError.value = error
  } catch (error) {
    browserError.value =
      error instanceof Error ? error.message : '无法打开网址'
  }
}

function tabLabel(tab: EmbeddedBrowserTabState) {
  const title = tab.title.trim()
  if (title) return title
  if (!tab.url || tab.url === 'about:blank') return '新标签页'
  try {
    return new URL(tab.url).hostname || '新标签页'
  } catch {
    return tab.url
  }
}

async function activateTab(tabId: string) {
  if (!desktop || !tabId || tabId === activeTabId.value) return
  browserError.value = ''
  try {
    await desktop.activateEmbeddedBrowserTab(tabId)
    await nextTick()
    scheduleBoundsSync()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法切换标签页',
    )
  }
}

async function createTab() {
  if (!desktop) return
  browserError.value = ''
  try {
    await desktop.newEmbeddedBrowserTab('about:blank')
    await nextTick()
    addressInput.value?.focus()
    addressInput.value?.select()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法新建标签页',
    )
  }
}

async function closeTab(tabId: string) {
  if (!desktop) return
  try {
    const result = await desktop.closeEmbeddedBrowserTab(tabId)
    if (result.closedAll) {
      closed = true
      emit('close')
      return
    }
    await nextTick()
    scheduleBoundsSync()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法关闭标签页',
    )
  }
}

async function minimizeBrowser() {
  if (minimized.value) return
  minimized.value = true
  if (syncFrame) window.cancelAnimationFrame(syncFrame)
  try {
    await desktop?.setEmbeddedBrowserVisible(false)
  } finally {
    await nextTick()
    restoreButton.value?.focus()
  }
}

async function restoreBrowser() {
  if (!minimized.value) return
  minimized.value = false
  await desktop?.setEmbeddedBrowserVisible(true)
  await nextTick()
  scheduleBoundsSync()
}

async function toggleFullscreen() {
  if (!desktop) return
  if (minimized.value) {
    await restoreBrowser()
  }
  try {
    fullscreen.value =
      await desktop.toggleEmbeddedBrowserFullscreen()
    await nextTick()
    scheduleBoundsSync()
  } catch (error) {
    emit(
      'notify',
      error instanceof Error ? error.message : '无法切换全屏',
    )
  }
}

function showMenu() {
  void desktop?.showEmbeddedBrowserMenu()
}

function handleCommand(command: string) {
  if (command === 'close') {
    void closeBrowser()
  }
  if (command === 'toggle-fullscreen') {
    void toggleFullscreen()
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (credentialPickerOpen.value) return
  if (event.key === 'Escape') {
    event.preventDefault()
    void closeBrowser()
    return
  }
  if (event.key === 'F11') {
    event.preventDefault()
    void toggleFullscreen()
  }
}

async function closeBrowser() {
  if (closed) return
  closed = true
  await completeAutomationCredentialFill({
    cancelled: true,
    message: '内置浏览器已关闭，密码库填充已取消',
  })
  try {
    await desktop?.closeEmbeddedBrowser()
  } finally {
    emit('close')
  }
}

async function openCredentialPicker() {
  if (!canFillFromVault.value || !desktop) {
    emit('notify', '当前页面无法从密码库填充登录信息')
    return
  }
  credentialPickerError.value = ''
  vaultPickerBusy.value = false
  credentialFillBusy.value = false
  credentialPickerOpen.value = true
  try {
    await desktop.setEmbeddedBrowserVisible(false)
  } catch (error) {
    credentialPickerOpen.value = false
    emit(
      'notify',
      error instanceof Error ? error.message : '无法打开密码库',
    )
  }
}

async function completeAutomationCredentialFill(payload: {
  result?: EmbeddedBrowserCredentialFillResult
  cancelled?: boolean
  error?: string
  message?: string
}) {
  const requestId = automationCredentialRequestId.value
  if (!requestId) return
  automationCredentialRequestId.value = ''
  automationCredentialName.value = ''
  await desktop?.completeEmbeddedBrowserCredentialFill({
    requestId,
    ...payload,
  })
}

async function handleCredentialFillRequest(
  request: EmbeddedBrowserCredentialFillRequest,
) {
  if (!request.requestId || !request.origin) {
    await completeAutomationCredentialFill({
      error: '密码库填充请求无效',
    })
    return
  }
  if (automationCredentialRequestId.value) {
    await completeAutomationCredentialFill({
      cancelled: true,
      message: '新的密码库填充请求已替代旧请求',
    })
  }
  automationCredentialRequestId.value = request.requestId
  automationCredentialName.value = request.credentialName || ''
  if (minimized.value) {
    await restoreBrowser()
  }
  emit('notify', 'Codex 请求从密码库填充登录信息')
  await openCredentialPicker()
  if (!credentialPickerOpen.value) {
    await completeAutomationCredentialFill({
      error: '无法打开密码库选择器',
    })
  }
}

async function restoreBrowserAfterCredentialPicker() {
  await nextTick()
  if (closed || minimized.value) return
  await desktop?.setEmbeddedBrowserVisible(true)
  scheduleBoundsSync()
}

async function closeCredentialPicker() {
  if (credentialPickerBusy.value) return
  await completeAutomationCredentialFill({
    cancelled: true,
    message: '用户取消了密码库填充',
  })
  credentialPickerOpen.value = false
  credentialPickerError.value = ''
  await restoreBrowserAfterCredentialPicker()
}

async function fillSelectedCredential(
  selection: { entry: VaultEntry; password: string } | null,
) {
  if (!selection || !desktop || !currentOrigin.value) return
  credentialFillBusy.value = true
  credentialPickerError.value = ''
  try {
    const result = await desktop.fillEmbeddedBrowserCredentials({
      origin: currentOrigin.value,
      username: selection.entry.username,
      password: selection.password,
    })
    credentialPickerOpen.value = false
    emit(
      'notify',
      result.filledUsername
        ? '已从密码库填充用户名和密码'
        : '已从密码库填充密码，但未识别到用户名输入框',
    )
    try {
      await restoreBrowserAfterCredentialPicker()
    } finally {
      await completeAutomationCredentialFill({ result })
    }
  } catch (error) {
    credentialPickerError.value =
      error instanceof Error
        ? error.message
        : '无法填充密码库登录信息'
  } finally {
    credentialFillBusy.value = false
  }
}

function handleAddressFocus(event: FocusEvent) {
  addressFocused.value = true
  const input = event.target as HTMLInputElement
  window.requestAnimationFrame(() => input.select())
}

function handleAddressBlur() {
  addressFocused.value = false
  address.value =
    currentUrl.value === 'about:blank' ? '' : currentUrl.value
}

watch(minimized, async (active) => {
  await nextTick()
  if (!active) {
    dockResizeObserver?.disconnect()
    return
  }
  if (!dock.value) return
  dockResizeObserver ??= new ResizeObserver(() => {
    if (!dockPosition.value) return
    dockPosition.value = clampDockPosition(
      dockPosition.value.left,
      dockPosition.value.top,
    )
  })
  dockResizeObserver.observe(dock.value)
})

onMounted(async () => {
  if (!desktop) {
    emit('notify', '内置浏览器仅支持桌面客户端')
    emit('close')
    return
  }
  desktop.onEmbeddedBrowserState(applyBrowserState)
  desktop.onEmbeddedBrowserCommand(handleCommand)
  desktop.onEmbeddedBrowserCredentialFillRequest(
    handleCredentialFillRequest,
  )
  resizeObserver = new ResizeObserver(scheduleBoundsSync)
  if (surface.value) resizeObserver.observe(surface.value)
  window.addEventListener('resize', handleWindowResize)
  window.addEventListener('scroll', scheduleBoundsSync, true)
  window.addEventListener('keydown', handleKeydown)

  try {
    const error = await desktop.openEmbeddedBrowser(props.url)
    if (error) browserError.value = error
  } catch (error) {
    browserError.value =
      error instanceof Error ? error.message : '无法打开内置浏览器'
  }
  await nextTick()
  scheduleBoundsSync()
})

onBeforeUnmount(() => {
  if (syncFrame) window.cancelAnimationFrame(syncFrame)
  if (dockClampFrame) window.cancelAnimationFrame(dockClampFrame)
  resizeObserver?.disconnect()
  dockResizeObserver?.disconnect()
  window.removeEventListener('resize', handleWindowResize)
  window.removeEventListener('scroll', scheduleBoundsSync, true)
  window.removeEventListener('keydown', handleKeydown)
  desktop?.offEmbeddedBrowserState()
  desktop?.offEmbeddedBrowserCommand()
  desktop?.offEmbeddedBrowserCredentialFillRequest()
  if (automationCredentialRequestId.value) {
    void desktop?.completeEmbeddedBrowserCredentialFill({
      requestId: automationCredentialRequestId.value,
      cancelled: true,
      message: '内置浏览器已关闭，密码库填充已取消',
    })
  }
  if (!closed) void desktop?.closeEmbeddedBrowser()
  window.requestAnimationFrame(() => previousFocus.value?.focus())
})
</script>

<template>
  <UiDialog
    :title="windowTitle"
    variant="browser"
    height="tall"
    :suspended="minimized"
    @close="closeBrowser"
  >
    <section
      class="embedded-browser-window"
      :class="{
        'embedded-browser-window--fullscreen': fullscreen,
      }"
    >
        <header class="embedded-browser-titlebar">
          <div class="embedded-browser-window-controls">
            <button
              type="button"
              aria-label="关闭内置浏览器"
              title="关闭"
              @click="closeBrowser"
            >
              <X :size="14" />
            </button>
            <button
              type="button"
              aria-label="最小化内置浏览器"
              title="最小化到页脚"
              @click="minimizeBrowser"
            >
              <Minus :size="14" />
            </button>
            <button
              type="button"
              :aria-label="fullscreen ? '退出全屏' : '全屏'"
              :title="fullscreen ? '退出全屏 (F11)' : '全屏 (F11)'"
              @click="toggleFullscreen"
            >
              <Minimize2 v-if="fullscreen" :size="14" />
              <Maximize2 v-else :size="14" />
            </button>
          </div>

          <div class="embedded-browser-title">
            <strong>{{ windowTitle }}</strong>
            <small>{{ browserLocationLabel }}</small>
          </div>

          <button
            class="embedded-browser-menu-button"
            type="button"
            aria-label="浏览器菜单"
            title="更多"
            @click="showMenu"
          >
            <Ellipsis :size="18" />
          </button>
        </header>

        <div class="embedded-browser__tabs">
          <div
            class="embedded-browser__tab-list"
            role="tablist"
            aria-label="浏览器标签页"
          >
            <div
              v-for="tab in tabs"
              :key="tab.id"
              class="embedded-browser__tab"
              :class="{
                'embedded-browser__tab--active':
                  tab.id === activeTabId,
              }"
            >
              <button
                class="embedded-browser__tab-main"
                type="button"
                role="tab"
                :aria-selected="tab.id === activeTabId"
                :title="tabLabel(tab)"
                @click="activateTab(tab.id)"
              >
                <UiFidgetSpinner
                  v-if="tab.loading"
                  :size="12"
                />
                <Globe2 v-else :size="12" />
                <span class="embedded-browser__tab-title">
                  {{ tabLabel(tab) }}
                </span>
              </button>
              <button
                class="embedded-browser__tab-close"
                type="button"
                :aria-label="`关闭标签页：${tabLabel(tab)}`"
                title="关闭标签页"
                @click.stop="closeTab(tab.id)"
              >
                <X :size="11" />
              </button>
            </div>
          </div>
          <button
            class="embedded-browser__new-tab"
            type="button"
            aria-label="新建标签页"
            title="新建标签页"
            @click="createTab"
          >
            <Plus :size="14" />
          </button>
        </div>

        <form class="embedded-browser__toolbar" @submit.prevent="navigate">
          <div class="embedded-browser__navigation">
            <button
              class="embedded-browser__icon-button"
              type="button"
              aria-label="后退"
              title="后退"
              :disabled="!canGoBack"
              @click="desktop?.backEmbeddedBrowser(activeTabId)"
            >
              <ArrowLeft :size="16" />
            </button>
            <button
              class="embedded-browser__icon-button"
              type="button"
              aria-label="前进"
              title="前进"
              :disabled="!canGoForward"
              @click="desktop?.forwardEmbeddedBrowser(activeTabId)"
            >
              <ArrowRight :size="16" />
            </button>
            <button
              class="embedded-browser__icon-button"
              type="button"
              :aria-label="loading ? '停止加载' : '刷新'"
              :title="loading ? '停止加载' : '刷新'"
              @click="
                loading
                  ? desktop?.stopEmbeddedBrowser(activeTabId)
                  : desktop?.reloadEmbeddedBrowser(activeTabId)
              "
            >
              <UiFidgetSpinner v-if="loading" :size="15" />
              <RotateCw v-else :size="15" />
            </button>
          </div>

          <label class="embedded-browser__address">
            <Globe2 :size="14" />
            <input
              ref="addressInput"
              v-model="address"
              type="text"
              inputmode="url"
              aria-label="浏览器地址"
              placeholder="输入网址"
              spellcheck="false"
              autocomplete="off"
              @focus="handleAddressFocus"
              @blur="handleAddressBlur"
            />
          </label>
          <button
            class="embedded-browser__icon-button"
            type="button"
            aria-label="从密码库填充登录信息"
            title="从密码库填充"
            :disabled="!canFillFromVault"
            @click="openCredentialPicker"
          >
            <KeyRound :size="15" />
          </button>
        </form>

        <p
          v-if="browserError"
          class="embedded-browser__error"
          role="alert"
        >
          {{ browserError }}
        </p>

        <div
          ref="surface"
          class="embedded-browser__surface"
          aria-label="Chromium 网页内容"
        >
          <div class="embedded-browser__placeholder" aria-hidden="true">
            <UiFidgetSpinner :size="18" />
            正在启动 Chromium
          </div>
        </div>
    </section>
  </UiDialog>

  <Teleport to="body">
    <div
      v-if="minimized"
      ref="dock"
      class="embedded-browser-dock"
      :class="{
        'embedded-browser-dock--positioned': dockPosition,
        'embedded-browser-dock--dragging': dockDragging,
      }"
      :style="dockStyle"
      title="拖动可移动位置"
      @pointerdown="startDockDrag"
      @pointermove="moveDockDrag"
      @pointerup="endDockDrag"
      @pointercancel="endDockDrag"
    >
        <span class="embedded-browser-dock__grip" aria-hidden="true">
          <GripVertical :size="13" />
        </span>
        <button
          ref="restoreButton"
          class="embedded-browser-dock__restore"
          type="button"
          aria-label="恢复内置浏览器"
          title="恢复浏览器"
          @click="restoreBrowser"
        >
          <Globe2 :size="14" />
          <span>{{ windowTitle }}</span>
          <small>已最小化</small>
        </button>
        <button
          class="embedded-browser-dock__close"
          type="button"
          aria-label="关闭内置浏览器"
          title="关闭"
          @click="closeBrowser"
        >
          <X :size="13" />
        </button>
    </div>
  </Teleport>

  <UiDialog
    v-if="credentialPickerOpen"
    title="从密码库填充"
    description="选择密码库条目后自动填充当前登录页，不会自动提交。"
    size="wide"
    height="standard"
    scroll-mode="body"
    @close="closeCredentialPicker"
  >
    <div class="embedded-browser__vault-picker">
      <VaultPasswordPicker
        :workspace-id="workspaceId || ''"
        :preferred-url="currentUrl"
        :preferred-name="automationCredentialName"
        label="登录信息"
        description="优先显示与当前网站匹配的密码库条目。"
        @select="fillSelectedCredential"
        @busy="vaultPickerBusy = $event"
      />
      <p
        v-if="credentialPickerError"
        class="embedded-browser__vault-error"
        role="alert"
      >
        {{ credentialPickerError }}
      </p>
    </div>

    <template #footer>
      <UiButton
        size="lg"
        :disabled="credentialPickerBusy"
        @click="closeCredentialPicker"
      >
        取消
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.embedded-browser-window {
  display: flex;
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  background: var(--window-material);
}

.embedded-browser-window--fullscreen {
  background: var(--canvas);
}

.embedded-browser-titlebar {
  display: grid;
  min-height: 46px;
  flex: 0 0 auto;
  align-items: center;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: var(--space-3);
  padding: 0 var(--space-3);
  -webkit-app-region: drag;
  border-bottom: 1px solid var(--line-soft);
}

.embedded-browser-window-controls {
  display: flex;
  align-items: center;
  gap: 2px;
  -webkit-app-region: no-drag;
}

.embedded-browser-window-controls button,
.embedded-browser-menu-button {
  display: grid;
  width: 27px;
  height: 27px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.embedded-browser-window-controls button:hover,
.embedded-browser-menu-button:hover {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.embedded-browser-window-controls button:first-child:hover {
  color: var(--system-red);
  background: color-mix(
    in srgb,
    var(--system-red) 8%,
    transparent
  );
}

.embedded-browser-title {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.embedded-browser-title strong,
.embedded-browser-title small {
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.embedded-browser-title strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.embedded-browser-title small {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.embedded-browser__tabs {
  display: flex;
  min-width: 0;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-1);
  padding: 4px var(--space-3) 0;
  background: var(--window-material);
}

.embedded-browser__tab-list {
  display: flex;
  min-width: 0;
  flex: 1;
  gap: 3px;
  overflow-x: auto;
  scrollbar-width: none;
}

.embedded-browser__tab-list::-webkit-scrollbar {
  display: none;
}

.embedded-browser__tab {
  display: flex;
  min-width: 120px;
  max-width: 220px;
  height: 30px;
  flex: 0 1 190px;
  align-items: center;
  color: var(--text-muted);
  background: color-mix(
    in srgb,
    var(--field-bg) 66%,
    transparent
  );
  border: 1px solid transparent;
  border-radius: var(--radius-control);
}

.embedded-browser__tab--active {
  color: var(--text);
  background: var(--field-bg);
  border-color: var(--field-border);
  box-shadow: var(--shadow-control);
}

.embedded-browser__tab-main {
  display: flex;
  min-width: 0;
  height: 100%;
  flex: 1;
  align-items: center;
  gap: 6px;
  padding: 0 4px 0 var(--space-2);
  color: inherit;
  text-align: left;
  background: transparent;
}

.embedded-browser__tab-main > svg,
.embedded-browser__tab-main :deep(.ui-fidget-spinner) {
  flex: 0 0 auto;
}

.embedded-browser__tab-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.embedded-browser__tab-close {
  display: grid;
  width: 24px;
  height: 24px;
  flex: 0 0 auto;
  padding: 0;
  margin-right: 2px;
  color: var(--text-subtle);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.embedded-browser__tab-close:hover,
.embedded-browser__new-tab:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.embedded-browser__tab-close:hover {
  color: var(--system-red);
}

.embedded-browser__new-tab {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.embedded-browser-menu-button {
  -webkit-app-region: no-drag;
}

.embedded-browser__toolbar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
}

.embedded-browser__navigation {
  display: flex;
  align-items: center;
  gap: 2px;
}

.embedded-browser__icon-button {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.embedded-browser__icon-button:hover:not(:disabled) {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.embedded-browser__icon-button:disabled {
  opacity: 0.38;
}

.embedded-browser__address {
  display: flex;
  min-width: 0;
  height: 32px;
  flex: 1;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  color: var(--text-subtle);
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.embedded-browser__address:focus-within {
  border-color: var(--system-blue);
  box-shadow: 0 0 0 2px var(--accent-soft);
}

.embedded-browser__address input {
  min-width: 0;
  flex: 1;
  padding: 0;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: transparent;
  border: 0;
  outline: 0;
}

.embedded-browser__error {
  flex: 0 0 auto;
  margin: 0 var(--space-3) var(--space-2);
  padding: var(--space-2) var(--space-3);
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-red) 8%,
    transparent
  );
  border-radius: var(--radius-control);
}

.embedded-browser__surface {
  position: relative;
  min-height: 0;
  flex: 1;
  margin: 0 var(--space-3) var(--space-3);
  overflow: hidden;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.embedded-browser__placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.embedded-browser__vault-picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.embedded-browser__vault-error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-red) 8%,
    transparent
  );
  border-radius: var(--radius-control);
}

.embedded-browser-dock {
  position: fixed;
  right: var(--space-4);
  bottom: 52px;
  z-index: var(--z-overlay);
  display: flex;
  max-width: min(360px, calc(100vw - 32px));
  align-items: center;
  gap: 2px;
  padding: 4px;
  color: var(--text);
  background: var(--window-material);
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  box-shadow: var(--shadow-popover);
  backdrop-filter: blur(24px) saturate(1.4);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.embedded-browser-dock--positioned {
  right: auto;
  bottom: auto;
}

.embedded-browser-dock--dragging {
  cursor: grabbing;
}

.embedded-browser-dock__grip {
  display: grid;
  width: 18px;
  height: 28px;
  flex: 0 0 auto;
  color: var(--text-subtle);
  place-items: center;
}

.embedded-browser-dock__restore,
.embedded-browser-dock__close {
  display: flex;
  min-width: 0;
  align-items: center;
  color: var(--text);
  background: transparent;
  border-radius: var(--radius-control);
}

.embedded-browser-dock__restore {
  height: 30px;
  gap: 6px;
  padding: 0 var(--space-2);
}

.embedded-browser-dock__restore:hover,
.embedded-browser-dock__close:hover {
  background: var(--hover-bg);
}

.embedded-browser-dock__restore span {
  overflow: hidden;
  max-width: 210px;
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.embedded-browser-dock__restore small {
  flex: 0 0 auto;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.embedded-browser-dock__close {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 auto;
  padding: 0;
  color: var(--icon);
  place-items: center;
}

.embedded-browser-dock__close:hover {
  color: var(--system-red);
}

@media (max-width: 640px) {
  .embedded-browser-titlebar {
    gap: var(--space-1);
    padding: 0 var(--space-2);
  }

  .embedded-browser-title small {
    display: none;
  }

  .embedded-browser__toolbar {
    padding: var(--space-2);
  }

  .embedded-browser__tabs {
    padding-right: var(--space-2);
    padding-left: var(--space-2);
  }

  .embedded-browser__tab {
    min-width: 108px;
    max-width: 170px;
  }

  .embedded-browser__surface {
    margin: 0 var(--space-2) var(--space-2);
  }

  .embedded-browser__error {
    margin-right: var(--space-2);
    margin-left: var(--space-2);
  }
}
</style>

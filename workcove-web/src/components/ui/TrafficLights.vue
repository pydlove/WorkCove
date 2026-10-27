<script setup lang="ts">
import { Maximize2, Minus, X } from '@lucide/vue'

const desktop = window.workCoveDesktop

async function closeWindow() {
  if (desktop) {
    await desktop.close()
    return
  }

  window.dispatchEvent(new CustomEvent('workcove:window-close'))
}

async function minimizeWindow() {
  if (desktop) {
    await desktop.minimize()
    return
  }

  window.dispatchEvent(new CustomEvent('workcove:window-minimize'))
}

async function maximizeWindow() {
  if (desktop) {
    await desktop.toggleMaximize()
    return
  }

  if (document.fullscreenElement) {
    await document.exitFullscreen()
  } else {
    await document.documentElement.requestFullscreen()
  }
}
</script>

<template>
  <div class="traffic-lights" aria-label="窗口控制">
    <button
      class="traffic-light traffic-light--close"
      type="button"
      aria-label="关闭窗口"
      title="关闭"
      @click="closeWindow"
    >
      <X :size="7" :stroke-width="2.8" />
    </button>
    <button
      class="traffic-light traffic-light--minimize"
      type="button"
      aria-label="最小化窗口"
      title="最小化"
      @click="minimizeWindow"
    >
      <Minus :size="7" :stroke-width="2.8" />
    </button>
    <button
      class="traffic-light traffic-light--maximize"
      type="button"
      aria-label="最大化窗口"
      title="全屏"
      @click="maximizeWindow"
    >
      <Maximize2 :size="6" :stroke-width="2.5" />
    </button>
  </div>
</template>

<style scoped>
.traffic-lights {
  display: flex;
  align-items: center;
  gap: 8px;
  -webkit-app-region: no-drag;
}

.traffic-light {
  display: grid;
  width: 12px;
  height: 12px;
  padding: 0;
  place-items: center;
  color: rgb(0 0 0 / 0%);
  border: 0;
  border-radius: 999px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 14%);
  cursor: default;
  transition:
    color 120ms ease,
    filter 120ms ease;
}

.traffic-light svg {
  opacity: 0;
  transition: opacity 100ms ease;
}

.traffic-lights:hover .traffic-light svg,
.traffic-light:focus-visible svg {
  opacity: 1;
}

.traffic-light--close {
  background: #ff5f57;
}

.traffic-light--minimize {
  background: #febc2e;
}

.traffic-light--maximize {
  background: #28c840;
}

.traffic-light:active {
  filter: brightness(0.82);
}

.traffic-light:focus-visible {
  outline: 2px solid rgb(31 41 38 / 42%);
  outline-offset: 2px;
}

@media (hover: none) {
  .traffic-light svg {
    opacity: 1;
  }
}
</style>

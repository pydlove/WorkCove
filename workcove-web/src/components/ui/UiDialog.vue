<script setup lang="ts">
import { X } from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
  type CSSProperties,
} from 'vue'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    initialFocus?: string
    suspended?: boolean
    size?: 'default' | 'wide' | 'extra-wide'
    variant?: 'window' | 'screensaver' | 'browser'
    height?: 'auto' | 'compact' | 'standard' | 'tall'
    scrollMode?: 'contained' | 'body'
    maxHeight?: string
    titleEmphasis?: 'default' | 'prominent'
    surfaceStyle?: CSSProperties
  }>(),
  {
    description: '',
    initialFocus: '',
    suspended: false,
    size: 'default',
    variant: 'window',
    height: 'standard',
    scrollMode: 'contained',
    maxHeight: '',
    titleEmphasis: 'default',
  },
)

const emit = defineEmits<{
  close: []
}>()

const titleId = useId()
const descriptionId = useId()
const dialog = ref<HTMLElement | null>(null)
const previousFocus = ref<HTMLElement | null>(null)
const dialogStyle = computed<CSSProperties | undefined>(() =>
  props.maxHeight || props.surfaceStyle
    ? ({
        ...props.surfaceStyle,
        ...(props.maxHeight
          ? { '--dialog-max-height': props.maxHeight }
          : {}),
      } as CSSProperties)
    : undefined,
)

function focusableElements() {
  if (!dialog.value) return []
  return Array.from(
    dialog.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((element) => element.offsetParent !== null)
}

function handleKeydown(event: KeyboardEvent) {
  const dialogs = Array.from(
    document.querySelectorAll<HTMLElement>('[data-ui-dialog]'),
  ).filter((element) => element.offsetParent !== null)
  if (dialogs.at(-1) !== dialog.value) return

  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key !== 'Tab') return

  const focusable = focusableElements()
  if (focusable.length === 0) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (
    event.shiftKey &&
    (active === first || !dialog.value?.contains(active))
  ) {
    event.preventDefault()
    last.focus()
  } else if (
    !event.shiftKey &&
    (active === last || !dialog.value?.contains(active))
  ) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => {
  previousFocus.value =
    document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null
  window.addEventListener('keydown', handleKeydown)
  void nextTick(() => {
    const initial = props.initialFocus
      ? dialog.value?.querySelector<HTMLElement>(props.initialFocus)
      : null
    const target = initial ?? focusableElements()[0] ?? dialog.value
    target?.focus()
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  previousFocus.value?.focus()
})
</script>

<template>
  <Teleport to="body">
    <div
      v-show="!suspended"
      class="ui-dialog-layer"
      :class="{
        'ui-dialog-layer--screensaver': variant === 'screensaver',
        'ui-dialog-layer--browser': variant === 'browser',
      }"
    >
      <div class="ui-dialog-backdrop" aria-hidden="true" />
      <section
        ref="dialog"
        class="ui-dialog"
        :class="{
          'ui-dialog--wide': size === 'wide',
          'ui-dialog--extra-wide': size === 'extra-wide',
          'ui-dialog--screensaver': variant === 'screensaver',
          'ui-dialog--browser': variant === 'browser',
          'ui-dialog--title-prominent':
            titleEmphasis === 'prominent',
          [`ui-dialog--height-${height}`]: true,
          'ui-dialog--body-scroll': scrollMode === 'body',
        }"
        data-ui-dialog
        :style="dialogStyle"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="description ? descriptionId : undefined"
        tabindex="-1"
      >
        <header class="ui-dialog__header">
          <div class="ui-dialog__traffic">
            <button
              class="ui-dialog__traffic-close"
              type="button"
              aria-label="关闭弹框"
              title="关闭"
              @click="emit('close')"
            >
              <X :size="8" :stroke-width="1.6" />
            </button>
            <span class="ui-dialog__traffic-minimize" aria-hidden="true" />
            <span class="ui-dialog__traffic-zoom" aria-hidden="true" />
          </div>
          <div class="ui-dialog__heading">
            <h2 :id="titleId">{{ title }}</h2>
            <p v-if="description" :id="descriptionId">{{ description }}</p>
          </div>
        </header>
        <div class="ui-dialog__content">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="ui-dialog__footer">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ui-dialog-layer {
  position: fixed;
  z-index: var(--z-overlay);
  inset: 0;
  display: grid;
  padding: var(--space-6);
  place-items: center;
}

.ui-dialog-backdrop {
  position: absolute;
  inset: 0;
  background: rgb(0 0 0 / 24%);
}

.ui-dialog {
  --dialog-height: var(--dialog-height-standard);
  --dialog-max-height: 100dvh;
  --dialog-safe-inset: 96px;

  position: relative;
  display: flex;
  width: min(100%, 440px);
  height: min(
    var(--dialog-height),
    calc(100dvh - var(--dialog-safe-inset))
  );
  max-height: min(
    var(--dialog-max-height),
    calc(100dvh - var(--dialog-safe-inset))
  );
  flex-direction: column;
  overflow: hidden;
  color: var(--text);
  background: var(--window-material);
  border-radius: var(--radius-window);
  box-shadow: var(--window-shadow);
  backdrop-filter: blur(30px) saturate(1.45);
  outline: none;
  animation: dialog-enter var(--duration-window) var(--ease-apple) both;
}

.ui-dialog--wide {
  width: min(100%, 720px);
}

.ui-dialog--extra-wide {
  width: min(100%, 1040px);
}

.ui-dialog--height-auto {
  height: auto;
}

.ui-dialog--height-compact {
  --dialog-height: var(--dialog-height-compact);
}

.ui-dialog--height-standard {
  --dialog-height: var(--dialog-height-standard);
}

.ui-dialog--height-tall {
  --dialog-height: var(--dialog-height-tall);
}

.ui-dialog-layer--screensaver {
  padding: 0;
}

.ui-dialog-layer--browser {
  padding: 0;
}

.ui-dialog--browser {
  width: 100%;
  height: 100dvh;
  max-height: none;
  background: var(--window-material);
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
  animation: none;
}

.ui-dialog--browser .ui-dialog__header {
  display: none;
}

.ui-dialog--browser .ui-dialog__content {
  overflow: hidden;
}

.ui-dialog--screensaver {
  width: 100%;
  height: 100dvh;
  max-height: none;
  color: var(--rest-screen-text);
  background: var(--rest-screen-bg);
  border-radius: 0;
  box-shadow: none;
  backdrop-filter: none;
}

.ui-dialog--screensaver .ui-dialog__header {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

.ui-dialog--screensaver .ui-dialog__traffic {
  display: none;
}

.ui-dialog--screensaver .ui-dialog__content {
  overflow: hidden;
}

.ui-dialog--screensaver .ui-dialog__footer {
  justify-content: center;
  color: var(--rest-screen-muted);
  background: transparent;
  border-top-color: var(--rest-screen-line);
}

.ui-dialog--body-scroll .ui-dialog__content {
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.ui-dialog__header {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  padding: 17px var(--space-5) 14px;
  background: var(--window-material);
}

.ui-dialog__traffic {
  display: flex;
  height: 12px;
  align-items: center;
  gap: var(--space-2);
}

.ui-dialog__traffic-close,
.ui-dialog__traffic-minimize,
.ui-dialog__traffic-zoom {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 16%);
}

.ui-dialog__traffic-close {
  display: grid;
  padding: 0;
  color: rgb(91 0 0 / 58%);
  background: var(--traffic-close);
  border: 0;
  cursor: pointer;
  place-items: center;
}

.ui-dialog__traffic-minimize {
  background: var(--traffic-minimize);
  opacity: 0.52;
}

.ui-dialog__traffic-zoom {
  background: var(--traffic-zoom);
  opacity: 0.52;
}

.ui-dialog__traffic svg {
  opacity: 0;
  transition: opacity 100ms ease;
}

.ui-dialog__traffic:hover svg {
  opacity: 1;
}

.ui-dialog__traffic-close:focus-visible {
  outline: 2px solid var(--system-blue);
  outline-offset: 2px;
}

.ui-dialog__heading {
  margin-top: var(--space-3);
}

.ui-dialog__heading h2 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-strong);
  line-height: var(--line-title);
  overflow-wrap: anywhere;
}

.ui-dialog--title-prominent .ui-dialog__heading h2 {
  font-size: var(--font-dialog-title-prominent);
  line-height: var(--line-dialog-title-prominent);
  font-weight: var(--weight-strong);
}

.ui-dialog__heading p {
  margin: var(--space-1) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.ui-dialog__content {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.ui-dialog__footer {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-5) var(--space-4);
  background: var(--canvas-raised);
  border-top: 1px solid var(--line-soft);
}

@media (max-width: 520px) {
  .ui-dialog-layer {
    padding: var(--space-4);
  }

  .ui-dialog-layer--browser {
    padding: 0;
  }

  .ui-dialog-layer--screensaver {
    padding: 0;
  }

  .ui-dialog {
    --dialog-safe-inset: 64px;

    height: min(
      var(--dialog-height),
      calc(100dvh - var(--dialog-safe-inset))
    );
  }

  .ui-dialog--height-auto {
    height: auto;
  }

  .ui-dialog--browser {
    height: 100dvh;
    max-height: none;
  }

  .ui-dialog--screensaver {
    width: 100%;
    height: 100dvh;
    max-height: none;
  }

  .ui-dialog__header {
    padding: var(--space-4) var(--space-4) var(--space-3);
  }

  .ui-dialog__footer {
    padding: var(--space-3) var(--space-4);
  }

  .ui-dialog__close {
    top: 13px;
    left: var(--space-4);
  }
}

@keyframes dialog-enter {
  from {
    opacity: 0;
    transform: scale(0.96);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>

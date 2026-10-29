<script setup lang="ts">
import { ArrowRight, Coffee } from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  type CSSProperties,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import { useScreensaverStore } from '@/stores/useScreensaverStore'
import { formatWellnessDuration } from '@/utils/wellness'

const props = defineProps<{
  startedAt: string
}>()

const emit = defineEmits<{
  continue: []
}>()

const screensaver = useScreensaverStore()
const now = ref(Date.now())
let timer: number | null = null
let previousOverflow = ''

const dialogSurfaceStyle = computed<CSSProperties>(() => {
  const resolvedId = screensaver.resolvedId
  const presetId =
    resolvedId === 'custom' ? 'deep-forest' : resolvedId
  const style: Record<string, string> = {
    '--rest-screen-text': `var(--screensaver-${presetId}-text)`,
    '--rest-screen-muted': `var(--screensaver-${presetId}-muted)`,
    '--rest-screen-line': `var(--screensaver-${presetId}-line)`,
    backgroundColor: `var(--screensaver-${presetId}-bg)`,
  }

  if (resolvedId === 'custom' && screensaver.customImageDataUrl) {
    style.backgroundImage = [
      'linear-gradient(',
      'var(--screensaver-image-scrim),',
      'var(--screensaver-image-scrim)',
      `), url("${screensaver.customImageDataUrl}")`,
    ].join('')
    style.backgroundPosition = 'center'
    style.backgroundRepeat = 'no-repeat'
    style.backgroundSize = 'cover'
  }

  return style as CSSProperties
})

const elapsedMs = computed(() =>
  Math.max(0, now.value - new Date(props.startedAt).getTime()),
)

const elapsedLabel = computed(() => {
  const totalSeconds = Math.floor(elapsedMs.value / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
})

const restMessage = computed(() => {
  if (elapsedMs.value < 60_000) {
    return '先把视线从屏幕上移开，慢慢呼吸。'
  }
  if (elapsedMs.value < 5 * 60_000) {
    return '可以起身走动一下，顺便喝几口水。'
  }
  return '休息够了再回来，清醒比匆忙更重要。'
})

const restSummary = computed(() =>
  elapsedMs.value < 60_000
    ? '本次休息刚刚开始'
    : `本次已休息 ${formatWellnessDuration(elapsedMs.value)}`,
)

function continueWork() {
  emit('continue')
}

onMounted(() => {
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow
  if (timer !== null) window.clearInterval(timer)
})
</script>

<template>
  <UiDialog
    title="休息中"
    description="离开屏幕，放松眼睛，或起身走动一会儿。"
    variant="screensaver"
    :surface-style="dialogSurfaceStyle"
    initial-focus="#wellness-continue-work"
    @close="continueWork"
  >
    <div class="wellness-rest">
      <header class="wellness-rest__header">
        <span class="wellness-rest__brand">
          <Coffee :size="16" />
          WorkCove
        </span>
        <span>休息中</span>
      </header>

      <main class="wellness-rest__main">
        <p class="wellness-rest__kicker">离开屏幕一会儿</p>
        <h1>休息中</h1>
        <strong class="wellness-rest__timer">{{ elapsedLabel }}</strong>
        <p class="wellness-rest__message">{{ restMessage }}</p>
        <p class="wellness-rest__summary">{{ restSummary }}</p>
        <UiButton
          id="wellness-continue-work"
          variant="primary"
          size="lg"
          @click="continueWork"
        >
          继续工作
          <ArrowRight :size="14" />
        </UiButton>
      </main>
    </div>

    <template #footer>
      <span class="wellness-rest__footer-copy">
        本次休息时间会记录到今日关怀
      </span>
    </template>
  </UiDialog>
</template>

<style scoped>
.wellness-rest {
  display: flex;
  width: 100%;
  height: 100%;
  padding: var(--space-6);
  color: var(--rest-screen-text);
  flex-direction: column;
}

.wellness-rest__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--rest-screen-muted);
  font-size: var(--font-caption);
}

.wellness-rest__brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--rest-screen-text);
  font-weight: var(--weight-emphasis);
}

.wellness-rest__main {
  width: min(100%, 560px);
  margin: auto;
  text-align: center;
}

.wellness-rest__kicker {
  margin: 0;
  color: var(--rest-screen-muted);
  font-size: var(--font-caption);
  letter-spacing: 0;
}

.wellness-rest h1 {
  margin: var(--space-3) 0 var(--space-4);
  color: var(--rest-screen-text);
  font-size: var(--font-display);
  font-weight: var(--weight-emphasis);
  line-height: 1.2;
}

.wellness-rest__timer {
  display: block;
  color: var(--rest-screen-text);
  font-family: var(--font-mono);
  font-size: var(--font-rest-timer);
  font-variant-numeric: tabular-nums;
  font-weight: var(--weight-regular);
  line-height: 1;
}

.wellness-rest__message {
  margin: var(--space-5) 0 0;
  color: var(--rest-screen-text);
  font-size: var(--font-body);
  line-height: var(--line-body);
}

.wellness-rest__summary {
  margin: var(--space-2) 0 var(--space-5);
  color: var(--rest-screen-muted);
  font-size: var(--font-caption);
}

.wellness-rest__main :deep(.ui-button) {
  min-width: 132px;
}

.wellness-rest__footer-copy {
  font-size: var(--font-caption);
}

@media (max-width: 520px) {
  .wellness-rest {
    padding: var(--space-5) var(--space-4);
  }

  .wellness-rest__timer {
    font-size: var(--font-hero);
  }
}

</style>

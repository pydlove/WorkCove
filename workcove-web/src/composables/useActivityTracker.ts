import {
  onBeforeUnmount,
  onMounted,
  watch,
} from 'vue'

import { useActivityStore } from '@/stores/useActivityStore'
import { useAuthStore } from '@/stores/useAuthStore'

const COUNTED_INPUT_TYPES = new Set([
  'insertText',
  'insertCompositionText',
  'insertFromComposition',
  'insertReplacementText',
])

export function useActivityTracker() {
  const auth = useAuthStore()
  const activity = useActivityStore()
  let tracking = false
  let sogouRefreshTimer: number | undefined

  function handleInput(event: Event) {
    if (!(event instanceof InputEvent) || event.isComposing) return
    if (!COUNTED_INPUT_TYPES.has(event.inputType)) return
    const inserted = event.data
    if (!inserted) return
    const count = [...inserted].filter(
      (character) => !/\s/u.test(character),
    ).length
    activity.recordTypedCharacters(count)
  }

  function handlePointerDown(event: PointerEvent) {
    if (event.button !== 0 || event.pointerType === 'touch') return
    activity.recordMouseClick()
  }

  function startTracking() {
    if (tracking || !auth.email) return
    document.addEventListener('input', handleInput, true)
    window.addEventListener('pointerdown', handlePointerDown, true)
    tracking = true
  }

  function stopTracking() {
    if (!tracking) return
    document.removeEventListener('input', handleInput, true)
    window.removeEventListener('pointerdown', handlePointerDown, true)
    tracking = false
  }

  watch(
    () => auth.email,
    (email) => {
      activity.load(email)
      if (email) {
        startTracking()
        void activity.refreshSogouStats()
      }
      else stopTracking()
    },
    { immediate: true },
  )

  onMounted(() => {
    if (auth.email) startTracking()
    sogouRefreshTimer = window.setInterval(() => {
      if (auth.email) void activity.refreshSogouStats()
    }, 60_000)
  })

  onBeforeUnmount(() => {
    stopTracking()
    if (sogouRefreshTimer) window.clearInterval(sogouRefreshTimer)
  })
}

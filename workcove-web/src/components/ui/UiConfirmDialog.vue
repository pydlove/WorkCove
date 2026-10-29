<script setup lang="ts">
import { TriangleAlert } from '@lucide/vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    warning?: string
    confirmLabel?: string
    busyLabel?: string
    busy?: boolean
  }>(),
  {
    description: '',
    warning: '此操作无法撤销，请确认后继续。',
    confirmLabel: '删除',
    busyLabel: '处理中…',
    busy: false,
  },
)

const emit = defineEmits<{
  close: []
  confirm: []
}>()

function close() {
  if (!props.busy) emit('close')
}
</script>

<template>
  <UiDialog
    :title="title"
    :description="description"
    initial-focus=".ui-confirm-dialog__cancel"
    height="auto"
    @close="close"
  >
    <div class="ui-confirm-dialog">
      <div class="ui-confirm-dialog__warning">
        <span class="ui-confirm-dialog__icon">
          <TriangleAlert :size="18" />
        </span>
        <p>{{ warning }}</p>
      </div>
      <div v-if="$slots.default" class="ui-confirm-dialog__target">
        <slot />
      </div>
    </div>

    <template #footer>
      <UiButton
        class="ui-confirm-dialog__cancel"
        size="lg"
        :disabled="busy"
        @click="close"
      >
        取消
      </UiButton>
      <UiButton
        variant="danger"
        size="lg"
        :loading="busy"
        @click="emit('confirm')"
      >
        {{ busy ? busyLabel : confirmLabel }}
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.ui-confirm-dialog {
  padding: 0 var(--space-5) var(--space-4);
}

.ui-confirm-dialog__warning {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3);
  color: var(--system-red);
  background: color-mix(in srgb, var(--system-red) 7%, transparent);
  border-radius: var(--radius-control);
}

.ui-confirm-dialog__icon {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  background: color-mix(in srgb, var(--system-red) 12%, transparent);
  border-radius: 50%;
  place-items: center;
}

.ui-confirm-dialog__warning p {
  padding-top: 1px;
  margin: 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.ui-confirm-dialog__target {
  padding: var(--space-3);
  margin-top: var(--space-3);
  color: var(--text-muted);
  font-size: var(--font-caption);
  background: var(--canvas);
  border-radius: var(--radius-control);
}

@media (max-width: 520px) {
  .ui-confirm-dialog {
    padding: 0 var(--space-4) var(--space-3);
  }
}
</style>

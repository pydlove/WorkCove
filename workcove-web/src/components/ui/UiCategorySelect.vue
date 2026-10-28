<script setup lang="ts">
import { computed } from 'vue'

import UiSelect from '@/components/ui/UiSelect.vue'

const props = withDefaults(
  defineProps<{
    value: string
    options: string[]
    label: string
    title?: string
  }>(),
  {
    title: '',
  },
)

const emit = defineEmits<{
  change: [value: string]
  focus: []
}>()

const selectOptions = computed(() =>
  props.options.map((option) => ({
    value: option,
    label: option,
  })),
)
</script>

<template>
  <UiSelect
    :model-value="value"
    :options="selectOptions"
    :aria-label="label"
    :title="props.title"
    size="sm"
    variant="chip"
    placement="bottom"
    @update:model-value="emit('change', $event)"
    @focus="emit('focus')"
  />
</template>

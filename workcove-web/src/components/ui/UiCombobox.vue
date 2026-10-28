<script setup lang="ts">
import { Check, ChevronDown } from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
} from 'vue'

const props = withDefaults(
  defineProps<{
    id?: string
    options: string[]
    placeholder?: string
    ariaLabel?: string
    placement?: 'top' | 'bottom'
  }>(),
  {
    id: undefined,
    placeholder: '',
    ariaLabel: '选择或输入',
    placement: 'bottom',
  },
)

const model = defineModel<string>({ default: '' })
const root = ref<HTMLElement | null>(null)
const input = ref<HTMLInputElement | null>(null)
const open = ref(false)
const filtering = ref(false)
const activeIndex = ref(-1)
const listId = useId()

const normalizedOptions = computed(() =>
  [
    ...new Set(
      props.options.map((option) => option.trim()).filter(Boolean),
    ),
  ],
)

const filteredOptions = computed(() => {
  const query = model.value.trim().toLocaleLowerCase('zh-CN')
  if (!filtering.value || !query) return normalizedOptions.value
  return normalizedOptions.value.filter((option) =>
    option.toLocaleLowerCase('zh-CN').includes(query),
  )
})

const activeOption = computed(
  () => filteredOptions.value[activeIndex.value] ?? '',
)

function optionId(index: number) {
  return `${listId}-option-${index}`
}

function showOptions() {
  filtering.value = false
  open.value = true
  const selectedIndex = filteredOptions.value.indexOf(model.value.trim())
  activeIndex.value = selectedIndex >= 0 ? selectedIndex : 0
}

function closeOptions() {
  open.value = false
  filtering.value = false
  activeIndex.value = -1
}

function toggleOptions() {
  if (open.value) {
    closeOptions()
    return
  }
  showOptions()
  void nextTick(() => input.value?.focus())
}

function selectOption(option: string) {
  model.value = option
  closeOptions()
}

function handleInput(event: Event) {
  model.value = (event.target as HTMLInputElement).value
  filtering.value = true
  open.value = true
  activeIndex.value = filteredOptions.value.length > 0 ? 0 : -1
}

function handleFocus() {
  if (!open.value) showOptions()
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (!open.value) {
      showOptions()
      return
    }
    activeIndex.value =
      filteredOptions.value.length === 0
        ? -1
        : (activeIndex.value + 1) % filteredOptions.value.length
    return
  }

  if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) {
      showOptions()
      return
    }
    activeIndex.value =
      filteredOptions.value.length === 0
        ? -1
        : (activeIndex.value - 1 + filteredOptions.value.length) %
          filteredOptions.value.length
    return
  }

  if (event.key === 'Enter' && open.value && activeOption.value) {
    event.preventDefault()
    selectOption(activeOption.value)
    return
  }

  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    event.stopPropagation()
    closeOptions()
    return
  }

  if (event.key === 'Tab') closeOptions()
}

function handlePointerDown(event: PointerEvent) {
  if (
    open.value &&
    event.target instanceof Node &&
    !root.value?.contains(event.target)
  ) {
    closeOptions()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
})
</script>

<template>
  <div
    ref="root"
    class="ui-combobox"
    :class="`ui-combobox--${placement}`"
  >
    <input
      :id="id"
      ref="input"
      class="ui-combobox__input"
      type="text"
      role="combobox"
      :value="model"
      :placeholder="placeholder"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      :aria-controls="listId"
      :aria-activedescendant="
        open && activeIndex >= 0 ? optionId(activeIndex) : undefined
      "
      aria-autocomplete="list"
      autocomplete="off"
      spellcheck="false"
      @input="handleInput"
      @focus="handleFocus"
      @keydown="handleKeydown"
    />
    <button
      class="ui-combobox__toggle"
      type="button"
      tabindex="-1"
      :aria-label="open ? '收起选项' : '展开选项'"
      @click="toggleOptions"
    >
      <ChevronDown
        :class="{ 'ui-combobox__toggle--open': open }"
        :size="14"
      />
    </button>

    <div
      v-if="open"
      :id="listId"
      class="ui-combobox__menu"
      role="listbox"
    >
      <button
        v-for="(option, index) in filteredOptions"
        :id="optionId(index)"
        :key="option"
        class="ui-combobox__option"
        :class="{ 'ui-combobox__option--active': index === activeIndex }"
        type="button"
        role="option"
        :aria-selected="model === option"
        @mouseenter="activeIndex = index"
        @mousedown.prevent
        @click="selectOption(option)"
      >
        <Check v-if="model === option" :size="13" />
        <span v-else class="ui-combobox__option-spacer" />
        <span>{{ option }}</span>
      </button>
      <p v-if="filteredOptions.length === 0" class="ui-combobox__empty">
        可直接输入新分类
      </p>
    </div>
  </div>
</template>

<style scoped>
.ui-combobox {
  position: relative;
  width: 100%;
}

.ui-combobox__input {
  width: 100%;
  height: var(--control-lg);
  padding: 0 34px 0 8px;
  color: var(--text);
  font-size: var(--font-body);
  line-height: 1;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 4%);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple);
}

.ui-combobox__input:focus {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.ui-combobox__input:focus-visible {
  outline: none;
}

.ui-combobox__toggle {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  width: 28px;
  height: 24px;
  padding: 0;
  color: var(--text-muted);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.ui-combobox__toggle:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.ui-combobox__toggle svg {
  transition: transform var(--duration-fast) var(--ease-apple);
}

.ui-combobox__toggle--open {
  transform: rotate(180deg);
}

.ui-combobox__menu {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 20;
  max-height: 168px;
  padding: var(--space-1);
  overflow-y: auto;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-radius);
  box-shadow: var(--dropdown-shadow);
  backdrop-filter: blur(24px) saturate(1.35);
  animation: ui-combobox-menu-in var(--duration-popover) var(--ease-apple) both;
}

.ui-combobox--bottom .ui-combobox__menu {
  top: calc(100% + 4px);
}

.ui-combobox--top .ui-combobox__menu {
  bottom: calc(100% + 4px);
}

.ui-combobox__option {
  display: grid;
  width: 100%;
  min-height: var(--dropdown-item-height);
  grid-template-columns: 14px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-1);
  padding: 0 7px;
  color: var(--text-muted);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: var(--dropdown-item-radius);
}

.ui-combobox__option--active {
  color: var(--text);
  background: var(--hover-bg);
}

.ui-combobox__option[aria-selected="true"] {
  color: var(--text);
  font-weight: var(--weight-emphasis);
  background: var(--selection-bg);
}

.ui-combobox__option-spacer {
  display: block;
}

.ui-combobox__empty {
  padding: 6px 7px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

@keyframes ui-combobox-menu-in {
  from {
    opacity: 0;
    transform: translateY(-2px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>

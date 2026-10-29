<script setup lang="ts">
import { Check, ChevronDown, Search } from '@lucide/vue'
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
  type CSSProperties,
} from 'vue'

interface UiSelectOption {
  value: string
  label: string
  description?: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    id?: string
    options: UiSelectOption[]
    placeholder?: string
    ariaLabel?: string
    size?: 'sm' | 'regular' | 'lg'
    variant?: 'field' | 'chip'
    placement?: 'top' | 'bottom'
    menuHeight?: number
    searchable?: boolean
    disabled?: boolean
    title?: string
  }>(),
  {
    id: undefined,
    placeholder: '请选择',
    ariaLabel: '选择选项',
    size: 'regular',
    variant: 'field',
    placement: 'bottom',
    menuHeight: 0,
    searchable: false,
    disabled: false,
    title: '',
  },
)

const model = defineModel<string>({ default: '' })
const emit = defineEmits<{
  focus: []
}>()

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const searchInput = ref<HTMLInputElement | null>(null)
const open = ref(false)
const activeIndex = ref(-1)
const searchQuery = ref('')
const actualPlacement = ref<'top' | 'bottom'>(props.placement)
const menuStyle = ref<CSSProperties>({})
const listId = useId()

const selectedOption = computed(
  () => props.options.find((option) => option.value === model.value) ?? null,
)
const filteredOptions = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase('zh-CN')
  if (!props.searchable || !query) return props.options
  return props.options.filter((option) =>
    `${option.label} ${option.description ?? ''}`
      .toLocaleLowerCase('zh-CN')
      .includes(query),
  )
})
const activeOption = computed(
  () => filteredOptions.value[activeIndex.value] ?? null,
)

function optionId(index: number) {
  return `${listId}-option-${index}`
}

function firstEnabledIndex() {
  return filteredOptions.value.findIndex((option) => !option.disabled)
}

function showOptions() {
  if (props.disabled || props.options.length === 0) return
  open.value = true
  searchQuery.value = ''
  const selectedIndex = filteredOptions.value.findIndex(
    (option) => option.value === model.value && !option.disabled,
  )
  activeIndex.value =
    selectedIndex >= 0 ? selectedIndex : firstEnabledIndex()
  updateMenuPosition()
  void nextTick(() => {
    updateMenuPosition()
    if (props.searchable) searchInput.value?.focus()
  })
}

function closeOptions(restoreFocus = false) {
  open.value = false
  activeIndex.value = -1
  searchQuery.value = ''
  if (restoreFocus) trigger.value?.focus()
}

function moveActive(direction: 1 | -1) {
  if (filteredOptions.value.length === 0) return
  let index = activeIndex.value
  for (let step = 0; step < filteredOptions.value.length; step += 1) {
    index =
      (index + direction + filteredOptions.value.length) %
      filteredOptions.value.length
    if (!filteredOptions.value[index]?.disabled) {
      activeIndex.value = index
      return
    }
  }
}

function selectOption(option: UiSelectOption) {
  if (option.disabled) return
  model.value = option.value
  closeOptions(true)
}

function handleTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && open.value) {
    event.preventDefault()
    event.stopPropagation()
    closeOptions()
    return
  }

  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!open.value) {
      showOptions()
      return
    }
    moveActive(event.key === 'ArrowDown' ? 1 : -1)
    return
  }

  if (event.key === 'Home' && open.value) {
    event.preventDefault()
    activeIndex.value = firstEnabledIndex()
    return
  }

  if (event.key === 'End' && open.value) {
    event.preventDefault()
    for (let index = filteredOptions.value.length - 1; index >= 0; index -= 1) {
      if (!filteredOptions.value[index].disabled) {
        activeIndex.value = index
        break
      }
    }
    return
  }

  if (
    (event.key === 'Enter' || event.key === ' ') &&
    open.value &&
    activeOption.value
  ) {
    event.preventDefault()
    selectOption(activeOption.value)
    return
  }

  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    showOptions()
  }
}

function handleSearchInput(event: Event) {
  searchQuery.value = (event.target as HTMLInputElement).value
  activeIndex.value = firstEnabledIndex()
  void nextTick(updateMenuPosition)
}

function handleSearchKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(event.key === 'ArrowDown' ? 1 : -1)
    return
  }
  if (event.key === 'Home') {
    event.preventDefault()
    activeIndex.value = firstEnabledIndex()
    return
  }
  if (event.key === 'End') {
    event.preventDefault()
    for (let index = filteredOptions.value.length - 1; index >= 0; index -= 1) {
      if (!filteredOptions.value[index].disabled) {
        activeIndex.value = index
        break
      }
    }
    return
  }
  if (event.key === 'Enter' && activeOption.value) {
    event.preventDefault()
    selectOption(activeOption.value)
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    closeOptions(true)
    return
  }
  if (event.key === 'Tab') closeOptions()
}

function handlePointerDown(event: PointerEvent) {
  if (
    open.value &&
    event.target instanceof Node &&
    !root.value?.contains(event.target) &&
    !menu.value?.contains(event.target)
  ) {
    closeOptions()
  }
}

function updateMenuPosition() {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const viewportPadding = 12
  const gap = 4
  const desiredWidth = Math.min(
    props.variant === 'chip'
      ? Math.max(180, rect.width)
      : Math.max(220, rect.width),
    Math.max(120, window.innerWidth - viewportPadding * 2),
  )
  const left = Math.min(
    Math.max(viewportPadding, rect.left),
    Math.max(
      viewportPadding,
      window.innerWidth - viewportPadding - desiredWidth,
    ),
  )
  const measuredHeight =
    menu.value?.offsetHeight ||
    Math.min(
      220,
      Math.max(48, filteredOptions.value.length * 48 + 8),
    )
  const preferredHeight =
    props.menuHeight > 0 ? props.menuHeight : measuredHeight
  const spaceBelow =
    window.innerHeight - rect.bottom - gap - viewportPadding
  const spaceAbove = rect.top - gap - viewportPadding
  const placeTop =
    props.placement === 'top' ||
    (props.placement === 'bottom' &&
      spaceBelow < Math.min(140, preferredHeight) &&
      spaceAbove > spaceBelow)
  const height = Math.max(
    48,
    Math.min(preferredHeight, placeTop ? spaceAbove : spaceBelow),
  )

  actualPlacement.value = placeTop ? 'top' : 'bottom'
  menuStyle.value = {
    left: `${left}px`,
    top: placeTop
      ? `${Math.max(viewportPadding, rect.top - gap - height)}px`
      : `${rect.bottom + gap}px`,
    width: `${desiredWidth}px`,
    maxHeight: `${height}px`,
    height:
      props.menuHeight > 0
        ? `${height}px`
        : undefined,
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown)
  window.addEventListener('resize', updateMenuPosition)
  window.addEventListener('scroll', updateMenuPosition, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handlePointerDown)
  window.removeEventListener('resize', updateMenuPosition)
  window.removeEventListener('scroll', updateMenuPosition, true)
})
</script>

<template>
  <div
    ref="root"
    class="ui-select"
    :class="[
      `ui-select--${size}`,
      `ui-select--${variant}`,
      `ui-select--${actualPlacement}`,
      { 'ui-select--open': open, 'ui-select--disabled': disabled },
    ]"
  >
    <button
      :id="id"
      ref="trigger"
      class="ui-select__trigger"
      type="button"
      :title="title || selectedOption?.label || placeholder"
      :disabled="disabled"
      aria-haspopup="listbox"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      :aria-controls="listId"
      :aria-activedescendant="
        open && activeIndex >= 0 ? optionId(activeIndex) : undefined
      "
      @click="open ? closeOptions() : showOptions()"
      @focus="emit('focus')"
      @keydown="handleTriggerKeydown"
    >
      <span class="ui-select__value">
        {{
          selectedOption?.label ||
          placeholder
        }}
      </span>
      <ChevronDown
        class="ui-select__chevron"
        :size="variant === 'chip' ? 11 : 14"
      />
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        :id="listId"
        ref="menu"
        class="ui-select__menu"
        :class="`ui-select__menu--${actualPlacement}`"
        :style="menuStyle"
        role="listbox"
        :aria-label="ariaLabel"
      >
        <label
          v-if="searchable"
          class="ui-select__search"
          @click.stop
        >
          <Search :size="13" aria-hidden="true" />
          <input
            ref="searchInput"
            type="search"
            :value="searchQuery"
            :aria-label="`搜索${ariaLabel}`"
            placeholder="搜索选项"
            autocomplete="off"
            spellcheck="false"
            @input="handleSearchInput"
            @keydown="handleSearchKeydown"
          />
        </label>
        <button
          v-for="(option, index) in filteredOptions"
          :id="optionId(index)"
          :key="option.value"
          class="ui-select__option"
          :class="{
            'ui-select__option--active': activeIndex === index,
            'ui-select__option--selected': model === option.value,
          }"
          type="button"
          role="option"
          :disabled="option.disabled"
          :aria-selected="model === option.value"
          @mouseenter="!option.disabled && (activeIndex = index)"
          @mousedown.prevent
          @click="selectOption(option)"
        >
          <span class="ui-select__check">
            <Check v-if="model === option.value" :size="13" />
          </span>
          <span class="ui-select__option-copy">
            <strong>{{ option.label }}</strong>
            <small v-if="option.description">
              {{ option.description }}
            </small>
          </span>
        </button>
        <p
          v-if="searchable && filteredOptions.length === 0"
          class="ui-select__empty"
        >
          没有匹配的选项
        </p>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.ui-select {
  position: relative;
  min-width: 0;
}

.ui-select__trigger {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: var(--space-2);
  color: var(--text);
  text-align: left;
  background: var(--field-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
  box-shadow: inset 0 1px 2px rgb(0 0 0 / 4%);
  transition:
    border-color var(--duration-fast) var(--ease-apple),
    box-shadow var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.ui-select--sm .ui-select__trigger {
  height: var(--control-sm);
  font-size: var(--font-caption);
}

.ui-select--regular .ui-select__trigger {
  height: var(--control-md);
  font-size: var(--font-body);
}

.ui-select--lg .ui-select__trigger {
  height: var(--control-lg);
  font-size: var(--font-body);
}

.ui-select__trigger {
  padding: 0 8px;
}

.ui-select__trigger:hover {
  background: var(--hover-bg);
}

.ui-select--open .ui-select__trigger {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.ui-select__trigger:focus-visible {
  border-color: var(--system-blue);
  outline: none;
  box-shadow: var(--focus-ring);
}

.ui-select--disabled {
  opacity: 0.45;
}

.ui-select__value {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ui-select__chevron {
  flex: 0 0 auto;
  color: var(--text-subtle);
  transition: transform var(--duration-fast) var(--ease-apple);
}

.ui-select--open .ui-select__chevron {
  transform: rotate(180deg);
}

.ui-select--chip .ui-select__trigger {
  justify-self: start;
  padding: 0 var(--space-1);
  color: var(--category-chip-color);
  background: var(--category-chip-bg);
  border: 0;
  border-radius: var(--radius-badge);
  box-shadow: inset 0 0 0 0.5px var(--category-chip-border);
}

.ui-select--chip {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  justify-self: start;
}

.ui-select--chip .ui-select__trigger {
  width: auto;
  max-width: 100%;
}

.ui-select--chip.ui-select--open .ui-select__trigger,
.ui-select--chip .ui-select__trigger:focus-visible {
  border: 0;
  outline: 2px solid var(--system-blue);
  outline-offset: 1px;
  box-shadow: none;
}

.ui-select--chip .ui-select__chevron {
  color: currentcolor;
}

.ui-select__menu {
  position: fixed;
  z-index: var(--z-popover);
  padding: var(--space-1);
  overflow-y: auto;
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-radius);
  box-shadow: var(--dropdown-shadow);
  backdrop-filter: blur(24px) saturate(1.35);
  animation: ui-select-menu-in var(--duration-popover) var(--ease-apple) both;
}

.ui-select__menu--bottom {
  transform-origin: top left;
}

.ui-select__menu--top {
  transform-origin: bottom left;
}

.ui-select__search {
  position: sticky;
  z-index: 1;
  top: calc(var(--space-1) * -1);
  display: flex;
  height: var(--control-md);
  align-items: center;
  gap: var(--space-1);
  padding: 0 var(--space-2);
  margin: 0 0 var(--space-1);
  color: var(--text-subtle);
  background: var(--dropdown-bg);
  border: 1px solid var(--field-border);
  border-radius: var(--radius-control);
}

.ui-select__search:focus-within {
  border-color: var(--system-blue);
  box-shadow: var(--focus-ring);
}

.ui-select__search input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0;
  color: var(--text);
  font-size: var(--font-body);
  background: transparent;
  border: 0;
}

.ui-select__search input::-webkit-search-cancel-button {
  display: none;
}

.ui-select__search input:focus-visible {
  outline: none;
}

.ui-select__option {
  display: grid;
  width: 100%;
  min-width: 0;
  min-height: var(--dropdown-item-height);
  grid-template-columns: 16px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: var(--dropdown-item-radius);
}

.ui-select__empty {
  padding: 6px 7px;
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.ui-select__option:hover,
.ui-select__option--active {
  color: var(--text);
  background: var(--hover-bg);
}

.ui-select__option--selected {
  color: var(--text);
  background: var(--selection-bg);
}

.ui-select__option:disabled {
  cursor: default;
  opacity: 0.4;
}

.ui-select__check {
  display: grid;
  color: var(--accent);
  place-items: center;
}

.ui-select__option-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.ui-select__option-copy strong,
.ui-select__option-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ui-select__option-copy strong {
  font-size: inherit;
  font-weight: var(--weight-regular);
}

.ui-select__option-copy small {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

@keyframes ui-select-menu-in {
  from {
    opacity: 0;
    transform: translateY(-2px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 620px) {
  .ui-select:not(.ui-select--chip) .ui-select__trigger {
    min-height: var(--touch-target);
  }

  .ui-select--chip .ui-select__trigger {
    position: relative;
  }

  .ui-select--chip .ui-select__trigger::after {
    position: absolute;
    content: "";
    inset: -12px -4px;
  }
}
</style>

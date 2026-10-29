<script setup lang="ts">
import { Ellipsis } from '@lucide/vue'
import {
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  useId,
  watch,
} from 'vue'
import type { Component, CSSProperties } from 'vue'

interface ActionMenuItem {
  id: string
  label: string
  icon?: Component
  iconSrc?: string
  disabled?: boolean
  danger?: boolean
  dividerBefore?: boolean
}

const props = withDefaults(
  defineProps<{
    items: ActionMenuItem[]
    label?: string
    title?: string
    disabled?: boolean
  }>(),
  {
    label: '更多操作',
    title: '更多操作',
    disabled: false,
  },
)

const emit = defineEmits<{
  select: [id: string]
}>()

const menuId = useId()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const menu = ref<HTMLElement | null>(null)
const open = ref(false)
const activeIndex = ref(-1)
const actualPlacement = ref<'bottom' | 'top'>('bottom')
const menuStyle = ref<CSSProperties>({})

function enabledIndexes() {
  return props.items
    .map((item, index) => (item.disabled ? -1 : index))
    .filter((index) => index >= 0)
}

function focusItem(index: number) {
  activeIndex.value = index
  void nextTick(() => {
    menu.value
      ?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]')
      [index]?.focus()
  })
}

function moveActive(direction: 1 | -1) {
  const indexes = enabledIndexes()
  if (indexes.length === 0) return
  const current = indexes.indexOf(activeIndex.value)
  const next =
    current < 0
      ? direction > 0
        ? indexes[0]
        : indexes[indexes.length - 1]
      : indexes[
          (current + direction + indexes.length) % indexes.length
        ]
  focusItem(next)
}

function updateMenuPosition() {
  if (!open.value || !trigger.value || !menu.value) return
  const rect = trigger.value.getBoundingClientRect()
  const menuWidth = menu.value.offsetWidth
  const menuHeight = menu.value.offsetHeight
  const viewportPadding = 12
  const gap = 4
  const maximumLeft = Math.max(
    viewportPadding,
    window.innerWidth - viewportPadding - menuWidth,
  )
  const left = Math.min(
    Math.max(viewportPadding, rect.right - menuWidth),
    maximumLeft,
  )
  const spaceBelow =
    window.innerHeight - rect.bottom - gap - viewportPadding
  const spaceAbove = rect.top - gap - viewportPadding
  const placeTop =
    spaceBelow < menuHeight && spaceAbove > spaceBelow
  actualPlacement.value = placeTop ? 'top' : 'bottom'
  menuStyle.value = {
    left: `${left}px`,
    top: placeTop
      ? `${rect.top - gap - Math.min(menuHeight, spaceAbove)}px`
      : `${rect.bottom + gap}px`,
  }
}

function showMenu() {
  if (props.disabled || open.value) return
  open.value = true
  const first = enabledIndexes()[0]
  activeIndex.value = first ?? -1
  void nextTick(() => {
    updateMenuPosition()
    if (first !== undefined) focusItem(first)
    else menu.value?.focus()
  })
}

function closeMenu(returnFocus = false) {
  if (!open.value) return
  open.value = false
  activeIndex.value = -1
  if (returnFocus) trigger.value?.focus()
}

function selectItem(item: ActionMenuItem) {
  if (item.disabled) return
  emit('select', item.id)
  closeMenu(true)
}

function handleTriggerKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    showMenu()
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    showMenu()
    void nextTick(() => moveActive(-1))
  }
}

function handleMenuKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    closeMenu(true)
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveActive(1)
    return
  }
  if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(-1)
    return
  }
  if (event.key === 'Home') {
    event.preventDefault()
    const first = enabledIndexes()[0]
    if (first !== undefined) focusItem(first)
    return
  }
  if (event.key === 'End') {
    event.preventDefault()
    const indexes = enabledIndexes()
    if (indexes.length > 0) focusItem(indexes[indexes.length - 1])
    return
  }
  if (event.key === 'Tab') closeMenu()
}

function handlePointerDown(event: PointerEvent) {
  if (
    !open.value ||
    !(event.target instanceof Node) ||
    root.value?.contains(event.target) ||
    menu.value?.contains(event.target)
  ) {
    return
  }
  closeMenu()
}

watch(
  () => props.items,
  () => closeMenu(),
)

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
  <div ref="root" class="ui-action-menu">
    <button
      ref="trigger"
      class="ui-action-menu__trigger"
      type="button"
      :disabled="disabled"
      :aria-label="label"
      :title="title"
      aria-haspopup="menu"
      :aria-expanded="open"
      :aria-controls="menuId"
      @click="open ? closeMenu() : showMenu()"
      @keydown="handleTriggerKeydown"
    >
      <Ellipsis :size="16" />
    </button>

    <Teleport to="body">
      <div
        v-if="open"
        :id="menuId"
        ref="menu"
        class="ui-action-menu__menu"
        :class="`ui-action-menu__menu--${actualPlacement}`"
        :style="menuStyle"
        role="menu"
        :aria-label="label"
        tabindex="-1"
        @keydown="handleMenuKeydown"
      >
        <template v-for="(item, index) in items" :key="item.id">
          <span
            v-if="item.dividerBefore && index > 0"
            class="ui-action-menu__divider"
            role="separator"
          />
          <button
            class="ui-action-menu__item"
            :class="{
              'ui-action-menu__item--active': activeIndex === index,
              'ui-action-menu__item--danger': item.danger,
            }"
            type="button"
            role="menuitem"
            :disabled="item.disabled"
            @mouseenter="!item.disabled && (activeIndex = index)"
            @click="selectItem(item)"
          >
            <span class="ui-action-menu__icon">
              <img
                v-if="item.iconSrc"
                :src="item.iconSrc"
                alt=""
                width="16"
                height="16"
              />
              <component
                :is="item.icon"
                v-else-if="item.icon"
                :size="15"
              />
            </span>
            <span>{{ item.label }}</span>
          </button>
        </template>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.ui-action-menu {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.ui-action-menu__trigger {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--icon);
  background: transparent;
  border-radius: var(--radius-control);
  place-items: center;
}

.ui-action-menu__trigger:hover:not(:disabled),
.ui-action-menu__trigger[aria-expanded="true"] {
  color: var(--icon-hover);
  background: var(--hover-bg);
}

.ui-action-menu__menu {
  position: fixed;
  z-index: var(--z-popover);
  width: var(--action-menu-width);
  max-height: var(--action-menu-max-height);
  padding: var(--space-1);
  overflow-y: auto;
  color: var(--text);
  background: var(--dropdown-bg);
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-radius);
  box-shadow: var(--dropdown-shadow);
  outline: none;
  animation: ui-action-menu-in var(--duration-popover)
    var(--ease-apple) both;
}

.ui-action-menu__menu--bottom {
  transform-origin: top right;
}

.ui-action-menu__menu--top {
  transform-origin: bottom right;
}

.ui-action-menu__item {
  display: grid;
  width: 100%;
  min-height: var(--dropdown-item-height);
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
  text-align: left;
  background: transparent;
  border-radius: var(--dropdown-item-radius);
}

.ui-action-menu__item > span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ui-action-menu__item:hover:not(:disabled),
.ui-action-menu__item--active:not(:disabled) {
  background: var(--hover-bg);
}

.ui-action-menu__item--danger {
  color: var(--system-red);
}

.ui-action-menu__item--danger:hover:not(:disabled),
.ui-action-menu__item--danger.ui-action-menu__item--active:not(:disabled) {
  background: color-mix(in srgb, var(--system-red) 8%, transparent);
}

.ui-action-menu__icon {
  display: grid;
  width: 18px;
  height: 18px;
  color: currentColor;
  place-items: center;
}

.ui-action-menu__icon img {
  width: 16px;
  height: 16px;
  object-fit: contain;
}

.ui-action-menu__divider {
  display: block;
  height: 1px;
  margin: var(--space-1) 0;
  background: var(--line-soft);
}

@keyframes ui-action-menu-in {
  from {
    opacity: 0;
    transform: scale(0.98) translateY(-2px);
  }

  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}
</style>

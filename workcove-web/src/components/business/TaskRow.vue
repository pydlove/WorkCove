<script setup lang="ts">
import { Check, MoreHorizontal } from '@lucide/vue'

import type { WorkTask } from '@/types/workbench'

defineProps<{
  task: WorkTask
}>()

defineEmits<{
  toggle: [id: number]
}>()

const priorityLabels = {
  high: '优先',
  medium: '普通',
  low: '低',
} as const
</script>

<template>
  <article class="task-row" :class="{ 'task-row--done': task.done }">
    <button
      class="task-check"
      type="button"
      :aria-label="task.done ? `将“${task.title}”标记为未完成` : `完成“${task.title}”`"
      @click="$emit('toggle', task.id)"
    >
      <Check v-if="task.done" :size="12" :stroke-width="2.6" />
    </button>

    <div class="task-copy">
      <p>{{ task.title }}</p>
      <span>{{ task.project }} · {{ task.time }}</span>
    </div>

    <span class="task-priority" :class="`task-priority--${task.priority}`">
      {{ priorityLabels[task.priority] }}
    </span>

    <button class="task-more" type="button" aria-label="更多任务操作">
      <MoreHorizontal :size="16" />
    </button>
  </article>
</template>

<style scoped>
.task-row {
  display: grid;
  min-height: 58px;
  grid-template-columns: 21px minmax(0, 1fr) auto 26px;
  align-items: center;
  gap: 10px;
  padding: 9px 3px;
  border-bottom: 1px solid var(--line-soft);
}

.task-row:last-of-type {
  border-bottom: 0;
}

.task-check {
  display: grid;
  width: 18px;
  height: 18px;
  padding: 0;
  color: white;
  background: transparent;
  border: 1.5px solid rgb(31 35 40 / 24%);
  border-radius: 50%;
  place-items: center;
  transition:
    background 140ms ease,
    border-color 140ms ease,
    transform 140ms ease;
}

.task-check:hover {
  background: var(--accent-soft);
  border-color: var(--accent);
}

.task-check:active {
  transform: scale(0.9);
}

.task-row--done .task-check {
  background: var(--accent);
  border-color: var(--accent);
}

.task-copy {
  min-width: 0;
}

.task-copy p {
  overflow: hidden;
  margin: 0;
  color: var(--text);
  font-size: 13px;
  font-weight: 520;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 140ms ease;
}

.task-copy span {
  display: block;
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.35;
}

.task-row--done .task-copy p {
  color: var(--text-muted);
  text-decoration: line-through;
  text-decoration-color: #afb8c1;
}

.task-priority {
  padding: 3px 7px;
  font-size: 11px;
  font-weight: 620;
  border-radius: 5px;
}

.task-priority--high {
  color: #cf222e;
  background: #ffebe9;
}

.task-priority--medium {
  color: #9a6700;
  background: #fff8c5;
}

.task-priority--low {
  color: #0969da;
  background: #ddf4ff;
}

.task-more {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--text-subtle);
  background: transparent;
  border-radius: 6px;
  opacity: 0;
  place-items: center;
  transition:
    opacity 120ms ease,
    background 120ms ease,
    color 120ms ease;
}

.task-row:hover .task-more,
.task-more:focus-visible {
  opacity: 1;
}

.task-more:hover {
  color: var(--text);
  background: rgb(31 35 40 / 6%);
}

html[data-theme="dark"] {
  .task-check {
    border-color: rgb(240 246 252 / 24%);
  }

  .task-check:hover {
    background: rgb(56 139 253 / 18%);
  }

  .task-row--done .task-copy p {
    color: #6e7681;
    text-decoration-color: #484f58;
  }

  .task-priority--high {
    color: #ff7b72;
    background: rgb(248 81 73 / 12%);
  }

  .task-priority--medium {
    color: #e3b341;
    background: rgb(210 153 34 / 12%);
  }

  .task-priority--low {
    color: #79c0ff;
    background: rgb(56 139 253 / 12%);
  }

  .task-more:hover {
    background: rgb(240 246 252 / 6%);
  }
}

@media (max-width: 560px) {
  .task-row {
    grid-template-columns: 21px minmax(0, 1fr) auto;
  }

  .task-more {
    display: none;
  }

  .task-priority {
    font-size: 11px;
  }
}
</style>

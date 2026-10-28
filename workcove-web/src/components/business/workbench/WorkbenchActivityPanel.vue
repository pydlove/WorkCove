<script setup lang="ts">
import {
  ChevronRight,
  MousePointerClick,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import ActivityHistoryDialog from '@/components/business/workbench/ActivityHistoryDialog.vue'
import { useActivityStore } from '@/stores/useActivityStore'
import { useAuthStore } from '@/stores/useAuthStore'
import {
  formatCompactCount,
  formatCount,
} from '@/utils/numberFormat'

const session = useAuthStore()
const activity = useActivityStore()
const historyOpen = ref(false)

const today = computed(() => activity.today)
const sourceLabel = computed(() =>
  activity.sogouActive ? '搜狗' : '页面',
)

watch(
  () => session.email,
  (email) => activity.load(email),
  { immediate: true },
)

</script>

<template>
  <button
    class="activity-strip"
    type="button"
    aria-label="查看输入轨迹历史"
    @click="historyOpen = true"
  >
    <span class="activity-strip__identity">
      <span class="activity-strip__copy">
        <strong>输入轨迹</strong>
        <small>
          <i
            :class="{ 'activity-strip__source-dot--active': activity.sogouActive }"
          />
          {{ sourceLabel }}
        </small>
      </span>
    </span>

    <span class="activity-strip__metrics">
      <span class="activity-strip__metric">
        <small>今日输入</small>
        <span>
          <strong>{{ formatCompactCount(today?.typedCharacters ?? 0) }}</strong>
          <em>字</em>
        </span>
      </span>
      <span class="activity-strip__metric activity-strip__metric--cumulative">
        <small>累计输入</small>
        <span>
          <strong>
            {{ formatCompactCount(activity.totalTypedCharacters) }}
          </strong>
          <em>字</em>
        </span>
      </span>
      <span class="activity-strip__metric">
        <small>当前速度</small>
        <span>
          <strong class="activity-strip__metric-live">
            {{ formatCompactCount(activity.sogou.currentSpeed) }}
          </strong>
          <em>字/分</em>
        </span>
      </span>
      <span class="activity-strip__metric activity-strip__metric--average">
        <small>平均速度</small>
        <span>
          <strong>{{ formatCompactCount(activity.sogou.historySpeed) }}</strong>
          <em>字/分</em>
        </span>
      </span>
      <span
        class="activity-strip__metric"
        :title="`累计点击 ${formatCount(activity.totalMouseClicks)} 次`"
      >
        <small>
          <MousePointerClick :size="11" />
          今日点击
        </small>
        <span>
          <strong>{{ formatCompactCount(today?.mouseClicks ?? 0) }}</strong>
          <em>次</em>
        </span>
      </span>
    </span>

    <span class="activity-strip__open">
      <span>历史</span>
      <ChevronRight :size="13" />
    </span>
  </button>

  <ActivityHistoryDialog
    v-if="historyOpen"
    @close="historyOpen = false"
  />
</template>

<style scoped>
.activity-strip {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  min-width: 0;
  height: 56px;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
  padding: 0 var(--space-2) 0 var(--space-3);
  color: rgb(255 255 255 / 78%);
  text-align: left;
  background: rgb(255 255 255 / 5.5%);
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: 8px;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 4%);
  transition:
    background var(--duration-fast) var(--ease-apple),
    border-color var(--duration-fast) var(--ease-apple),
    transform var(--duration-fast) var(--ease-apple);
}

.activity-strip:hover {
  background: rgb(255 255 255 / 8.5%);
  border-color: rgb(255 255 255 / 14%);
}

.activity-strip:focus-visible {
  outline: 2px solid #79c0ff;
  outline-offset: 2px;
}

.activity-strip__identity {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  padding-right: var(--space-3);
}

.activity-strip__copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.activity-strip__copy strong {
  color: rgb(255 255 255 / 90%);
  font-size: 12px;
  font-weight: var(--weight-emphasis);
  line-height: 14px;
  white-space: nowrap;
}

.activity-strip__copy small {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: rgb(255 255 255 / 42%);
  font-size: 11px;
  line-height: 13px;
  white-space: nowrap;
}

.activity-strip__copy small i {
  width: 5px;
  height: 5px;
  background: rgb(255 255 255 / 28%);
  border-radius: 50%;
}

.activity-strip__copy small .activity-strip__source-dot--active {
  background: var(--system-green);
  box-shadow: 0 0 0 3px rgb(52 199 89 / 10%);
}

.activity-strip__metrics {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  align-self: stretch;
}

.activity-strip__metric {
  position: relative;
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 0 var(--space-3);
  white-space: nowrap;
}

.activity-strip__metric::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 1px;
  height: 24px;
  content: "";
  background: rgb(255 255 255 / 8%);
  transform: translateY(-50%);
}

.activity-strip__metric:first-child {
  padding-left: 0;
}

.activity-strip__metric:first-child::before {
  display: none;
}

.activity-strip__metric small {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: rgb(255 255 255 / 36%);
  font-size: 11px;
  line-height: 12px;
}

.activity-strip__metric > span {
  display: inline-flex;
  min-width: 0;
  align-items: baseline;
  gap: 4px;
}

.activity-strip__metric strong {
  overflow: hidden;
  color: rgb(255 255 255 / 92%);
  font-family: var(--font-mono);
  font-size: 15px;
  font-weight: var(--weight-strong);
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  text-overflow: ellipsis;
}

.activity-strip__metric .activity-strip__metric-live {
  color: #a9d7ff;
}

.activity-strip__metric em {
  flex: 0 0 auto;
  color: rgb(255 255 255 / 34%);
  font-size: 11px;
  font-style: normal;
}

.activity-strip__open {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 2px;
  height: 28px;
  padding: 0 7px 0 9px;
  color: rgb(255 255 255 / 46%);
  font-size: 11px;
  background: rgb(255 255 255 / 4%);
  border-radius: 6px;
  transition:
    color var(--duration-fast) var(--ease-apple),
    background var(--duration-fast) var(--ease-apple);
}

.activity-strip:hover .activity-strip__open {
  color: rgb(255 255 255 / 68%);
  background: rgb(255 255 255 / 7%);
}

@media (max-width: 920px) {
  .activity-strip__metric {
    flex: 0 0 auto;
    min-width: 96px;
  }

  .activity-strip__metrics {
    justify-content: flex-start;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .activity-strip__metrics::-webkit-scrollbar {
    display: none;
  }
}

@media (max-width: 620px) {
  .activity-strip {
    height: 52px;
    padding-left: var(--space-2);
  }

  .activity-strip__copy small {
    display: none;
  }

  .activity-strip__identity {
    padding-right: var(--space-1);
  }

  .activity-strip__metric--cumulative,
  .activity-strip__metric--average {
    display: none;
  }

  .activity-strip__metric {
    min-width: 88px;
    padding: 0 var(--space-2);
  }

  .activity-strip__metric strong {
    font-size: 14px;
  }

  .activity-strip__open span {
    display: none;
  }
}
</style>

<script setup lang="ts">
import {
  CalendarDays,
  Minus,
  Plus,
  Settings2,
} from '@lucide/vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import {
  DEFAULT_WORKDAY_INSIGHTS,
  loadWorkdayInsights,
  saveWorkdayInsights,
} from '@/services/workdayInsights'
import { useAuthStore } from '@/stores/useAuthStore'
import type { WorkdayInsightsSettings } from '@/types/workdayInsights'
import {
  nextFestivalInfo,
  workdayInfo,
} from '@/utils/workday'

const emit = defineEmits<{
  notify: [message: string]
}>()

const auth = useAuthStore()
const now = ref(new Date())
const settings = ref<WorkdayInsightsSettings>({
  ...DEFAULT_WORKDAY_INSIGHTS,
})
const settingsOpen = ref(false)
const formPayday = ref(String(DEFAULT_WORKDAY_INSIGHTS.payday))
const formIncome = ref(String(DEFAULT_WORKDAY_INSIGHTS.dailyIncome))
const formError = ref('')
let refreshTimer: number | null = null

const paydayOptions = Array.from({ length: 31 }, (_, index) => {
  const day = index + 1
  return { value: String(day), label: `每月 ${day} 日` }
})

const paydayDays = computed(() =>
  daysUntilPayday(now.value, settings.value.payday),
)
const fridayDays = computed(() => (5 - now.value.getDay() + 7) % 7)
const festival = computed(() => nextFestivalInfo(now.value))
const currentWorkday = computed(() => workdayInfo(now.value))
const todayIncome = computed(() => {
  if (!currentWorkday.value.workday) return 0
  const current = now.value
  const minutes =
    current.getHours() * 60 +
    current.getMinutes() +
    current.getSeconds() / 60
  const start = 9 * 60
  const end = 18 * 60
  const progress = Math.min(
    1,
    Math.max(0, (minutes - start) / (end - start)),
  )
  return Math.round(settings.value.dailyIncome * progress * 100) / 100
})

watch(
  () => auth.email,
  (email) => {
    settings.value = email
      ? loadWorkdayInsights(email)
      : { ...DEFAULT_WORKDAY_INSIGHTS }
  },
  { immediate: true },
)

onMounted(() => {
  refreshTimer = window.setInterval(() => {
    now.value = new Date()
  }, 1_000)
})

onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

function openSettings() {
  formPayday.value = String(settings.value.payday)
  formIncome.value = String(settings.value.dailyIncome)
  formError.value = ''
  settingsOpen.value = true
}

function saveSettings() {
  const dailyIncome = Number(
    formIncome.value.replace(/,/g, '').trim(),
  )
  if (
    !Number.isFinite(dailyIncome) ||
    dailyIncome < 0 ||
    dailyIncome > 1_000_000_000_000
  ) {
    formError.value = '请输入有效的每日收入金额'
    return
  }
  const next: WorkdayInsightsSettings = {
    payday: Number(formPayday.value),
    dailyIncome: Math.round(dailyIncome * 100) / 100,
  }
  settings.value = next
  if (auth.email) saveWorkdayInsights(auth.email, next)
  settingsOpen.value = false
  emit('notify', '工作状态设置已保存')
}

function adjustIncome(direction: -1 | 1) {
  const current = Number(formIncome.value.replace(/,/g, '')) || 0
  formIncome.value = String(
    Math.max(0, Math.round((current + direction * 1_000) * 100) / 100),
  )
}

function daysUntilPayday(date: Date, payday: number) {
  const today = new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
    12,
  )
  const target = paydayDate(today.getFullYear(), today.getMonth(), payday)
  const next =
    target.getTime() < today.getTime()
      ? paydayDate(today.getFullYear(), today.getMonth() + 1, payday)
      : target
  return Math.round(
    (next.getTime() - today.getTime()) / 86_400_000,
  )
}

function paydayDate(year: number, month: number, payday: number) {
  const lastDay = new Date(year, month + 1, 0).getDate()
  return new Date(year, month, Math.min(payday, lastDay), 12)
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

function formatCompactMoney(value: number) {
  if (value >= 100_000_000) {
    return `${(value / 100_000_000).toFixed(2)}亿`
  }
  if (value >= 10_000) {
    return `${(value / 10_000).toFixed(2)}万`
  }
  return formatMoney(value)
}
</script>

<template>
  <section
    class="workday-insights"
    :title="`今天是${currentWorkday.workday ? '工作日' : '休息日'}${
      currentWorkday.festival ? ` · ${currentWorkday.festival}` : ''
    }`"
    aria-label="工作状态"
  >
    <div class="workday-insights__summary">
      <span class="workday-insights__title">
        <CalendarDays :size="12" />
        工作状态
      </span>
      <span class="workday-insights__item">
        发薪
        <strong>{{ paydayDays }}天</strong>
      </span>
      <span class="workday-insights__item">
        周五
        <strong>{{ fridayDays }}天</strong>
      </span>
      <span class="workday-insights__item">
        {{ festival?.name ?? '节日' }}
        <strong>{{ festival?.days ?? '--' }}天</strong>
      </span>
      <span class="workday-insights__item workday-insights__income">
        今天赚了
        <strong class="workday-insights__amount--full">
          ¥{{ formatMoney(todayIncome) }}
        </strong>
        <strong class="workday-insights__amount--compact">
          ¥{{ formatCompactMoney(todayIncome) }}
        </strong>
      </span>
    </div>

    <button
      class="workday-insights__settings"
      type="button"
      aria-label="工作状态设置"
      title="工作状态设置"
      @click="openSettings"
    >
      <Settings2 :size="14" />
    </button>

    <UiDialog
      v-if="settingsOpen"
      title="工作状态设置"
      description="发薪日和收入仅用于当前账号的工作台展示。"
      height="compact"
      @close="settingsOpen = false"
    >
      <div class="workday-insights-settings">
        <UiFormField label="发薪日">
          <UiSelect
            v-model="formPayday"
            :options="paydayOptions"
            size="lg"
            aria-label="每月发薪日"
          />
        </UiFormField>

        <UiFormField
          label="每天的收入"
          hint="今日收入按 09:00–18:00 的工作进度计算"
        >
          <div class="workday-income-field">
            <button
              type="button"
              aria-label="减少每日收入"
              title="减少 1,000 元"
              @click="adjustIncome(-1)"
            >
              <Minus :size="14" />
            </button>
            <UiInput
              v-model="formIncome"
              size="lg"
              inputmode="decimal"
              aria-label="每天的收入金额"
              placeholder="0.00"
            />
            <span>元</span>
            <button
              type="button"
              aria-label="增加每日收入"
              title="增加 1,000 元"
              @click="adjustIncome(1)"
            >
              <Plus :size="14" />
            </button>
          </div>
        </UiFormField>

        <p v-if="formError" class="workday-insights-settings__error">
          {{ formError }}
        </p>
      </div>
      <template #footer>
        <UiButton size="lg" @click="settingsOpen = false">
          取消
        </UiButton>
        <UiButton
          variant="primary"
          size="lg"
          @click="saveSettings"
        >
          保存设置
        </UiButton>
      </template>
    </UiDialog>
  </section>
</template>

<style scoped>
.workday-insights {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 2px 11px;
}

.workday-insights__summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 16px;
  overflow: hidden;
}

.workday-insights__title,
.workday-insights__item {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  color: rgb(255 255 255 / 40%);
  font-size: 11px;
  line-height: var(--line-caption);
  white-space: nowrap;
}

.workday-insights__title {
  color: #ffbe69;
}

.workday-insights__item strong {
  color: rgb(255 255 255 / 78%);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: var(--weight-emphasis);
}

.workday-insights__income strong {
  color: #91d8ff;
}

.workday-insights__amount--compact {
  display: none;
}

.workday-insights__settings {
  display: grid;
  height: 24px;
  width: 24px;
  flex: 0 0 auto;
  padding: 0;
  color: rgb(255 255 255 / 42%);
  background: transparent;
  border-radius: var(--radius-badge);
  place-items: center;
}

.workday-insights__settings:hover {
  color: rgb(255 255 255 / 78%);
  background: rgb(255 255 255 / 7%);
}

.workday-insights-settings {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5) var(--space-4);
}

.workday-income-field {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto 32px;
  align-items: center;
  gap: var(--space-2);
}

.workday-income-field > button {
  display: grid;
  width: 32px;
  height: 32px;
  padding: 0;
  color: var(--text-muted);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
  place-items: center;
}

.workday-income-field > button:hover {
  color: var(--text);
  background: var(--hover-bg);
}

.workday-income-field > span {
  color: var(--text-muted);
  font-size: var(--font-caption);
}

.workday-insights-settings__error {
  margin: calc(-1 * var(--space-2)) 0 0;
  color: var(--system-red);
  font-size: var(--font-caption);
}

@media (max-width: 900px) {
  .workday-insights__summary {
    gap: 10px;
  }
}

@media (max-width: 760px) {
  .workday-insights {
    align-items: flex-start;
    padding-bottom: 10px;
  }

  .workday-insights__summary {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 3px 12px;
    flex: 1;
  }

  .workday-insights__title {
    display: none;
  }

  .workday-insights__item {
    gap: 3px;
  }

  .workday-insights__amount--full {
    display: none;
  }

  .workday-insights__amount--compact {
    display: inline;
  }

  .workday-insights-settings {
    padding: 0 var(--space-4) var(--space-4);
  }
}
</style>

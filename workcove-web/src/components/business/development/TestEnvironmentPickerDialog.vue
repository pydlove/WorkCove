<script setup lang="ts">
import {
  Bug,
  FlaskConical,
  KeyRound,
  Play,
  Settings2,
} from '@lucide/vue'
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import UiButton from '@/components/ui/UiButton.vue'
import UiDialog from '@/components/ui/UiDialog.vue'
import { useTestEnvironmentStore } from '@/stores/useTestEnvironmentStore'
import type { TestEnvironment } from '@/types/testEnvironment'
import type { ZentaoBug } from '@/types/zentao'

const props = defineProps<{
  bug: ZentaoBug
  workspaceId: string
  launching: boolean
}>()

const emit = defineEmits<{
  close: []
  manage: []
  start: [environment: TestEnvironment]
}>()

const environments = useTestEnvironmentStore()
const selectedId = ref<number | null>(null)
const loadError = ref('')

const selectedEnvironment = computed(
  () =>
    environments.environments.find(
      (environment) => environment.id === selectedId.value,
    ) ?? null,
)

onMounted(async () => {
  try {
    await environments.load(props.workspaceId, true)
    selectedId.value =
      environments.environments[0]?.id ?? null
  } catch (error) {
    loadError.value =
      error instanceof Error
        ? error.message
        : '测试环境加载失败'
  }
})

function startTest() {
  if (!selectedEnvironment.value || props.launching) return
  emit('start', selectedEnvironment.value)
}
</script>

<template>
  <UiDialog
    :title="bug.title"
    :description="`Bug #${bug.remoteId} · 选择 Codex 测试环境`"
    size="wide"
    height="standard"
    title-emphasis="prominent"
    :suspended="launching"
    @close="emit('close')"
  >
    <div class="test-environment-picker">
      <section class="test-environment-picker__bug">
        <span>
          <Bug :size="16" />
        </span>
        <div>
          <strong>#{{ bug.remoteId }}</strong>
          <p>
            {{
              bug.projectName ||
              bug.productName ||
              '未关联项目'
            }}
          </p>
        </div>
      </section>

      <p
        v-if="loadError"
        class="test-environment-picker__error"
        role="alert"
      >
        {{ loadError }}
      </p>

      <div
        v-if="
          environments.loading &&
          environments.environments.length === 0
        "
        class="test-environment-picker__state"
      >
        <FlaskConical :size="21" />
        <span>正在读取测试环境</span>
      </div>

      <div
        v-else-if="environments.environments.length > 0"
        class="test-environment-picker__list"
        role="radiogroup"
        aria-label="测试环境"
      >
        <button
          v-for="environment in environments.environments"
          :key="environment.id"
          class="test-environment-picker__option"
          :class="{
            'test-environment-picker__option--selected':
              selectedId === environment.id,
          }"
          type="button"
          role="radio"
          :aria-checked="selectedId === environment.id"
          @click="selectedId = environment.id"
        >
          <span class="test-environment-picker__icon">
            <FlaskConical :size="17" />
          </span>
          <span class="test-environment-picker__copy">
            <strong>{{ environment.name }}</strong>
            <small :title="environment.url">
              {{ environment.url }}
            </small>
          </span>
          <span class="test-environment-picker__account">
            <KeyRound :size="12" />
            {{ environment.username }}
          </span>
          <span class="test-environment-picker__radio" />
        </button>
      </div>

      <div v-else class="test-environment-picker__state">
        <FlaskConical :size="21" />
        <strong>还没有可用的测试环境</strong>
        <span>先保存环境地址和登录信息，再发起 Codex 测试。</span>
        <UiButton variant="primary" @click="emit('manage')">
          <Settings2 :size="14" />
          管理测试环境
        </UiButton>
      </div>
    </div>

    <template #footer>
      <UiButton
        v-if="environments.environments.length > 0"
        variant="ghost"
        size="lg"
        :disabled="launching"
        @click="emit('manage')"
      >
        <Settings2 :size="14" />
        管理环境
      </UiButton>
      <span class="test-environment-picker__footer-spacer" />
      <UiButton
        size="lg"
        :disabled="launching"
        @click="emit('close')"
      >
        取消
      </UiButton>
      <UiButton
        variant="primary"
        size="lg"
        :loading="launching"
        :disabled="!selectedEnvironment"
        @click="startTest"
      >
        <Play :size="14" />
        开始测试
      </UiButton>
    </template>
  </UiDialog>
</template>

<style scoped>
.test-environment-picker {
  display: flex;
  min-height: 0;
  flex-direction: column;
  gap: var(--space-3);
  padding: 0 var(--space-5) var(--space-5);
}

.test-environment-picker__bug {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3);
  background: var(--surface-inset);
  border-radius: var(--radius-control);
}

.test-environment-picker__bug > span {
  display: grid;
  width: var(--control-md);
  height: var(--control-md);
  flex: 0 0 auto;
  color: var(--system-orange);
  background: color-mix(
    in srgb,
    var(--system-orange) 10%,
    transparent
  );
  border-radius: var(--radius-control);
  place-items: center;
}

.test-environment-picker__bug div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.test-environment-picker__bug strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.test-environment-picker__bug p {
  margin: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-caption);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-environment-picker__error {
  padding: var(--space-2) var(--space-3);
  margin: 0;
  color: var(--system-red);
  font-size: var(--font-caption);
  background: color-mix(
    in srgb,
    var(--system-red) 7%,
    transparent
  );
  border-radius: var(--radius-control);
}

.test-environment-picker__list {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow-y: auto;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.test-environment-picker__option {
  display: grid;
  min-width: 0;
  grid-template-columns:
    var(--control-lg)
    minmax(0, 1fr)
    minmax(100px, auto)
    var(--control-sm);
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  text-align: left;
  background: var(--canvas-raised);
  border-bottom: 1px solid var(--line-soft);
}

.test-environment-picker__option:last-child {
  border-bottom: 0;
}

.test-environment-picker__option:hover {
  background: var(--hover-bg);
}

.test-environment-picker__option--selected {
  background: var(--selection-bg);
}

.test-environment-picker__icon {
  display: grid;
  width: var(--control-lg);
  height: var(--control-lg);
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: var(--radius-control);
  place-items: center;
}

.test-environment-picker__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.test-environment-picker__copy strong,
.test-environment-picker__copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-environment-picker__copy strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.test-environment-picker__copy small,
.test-environment-picker__account {
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.test-environment-picker__account {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.test-environment-picker__radio {
  width: var(--control-sm);
  height: var(--control-sm);
  border: 1px solid var(--control-border);
  border-radius: 50%;
  box-shadow: inset 0 0 0 var(--space-1) var(--canvas-raised);
}

.test-environment-picker__option--selected
  .test-environment-picker__radio {
  background: var(--system-blue);
  border-color: var(--system-blue);
}

.test-environment-picker__state {
  display: flex;
  min-height: 220px;
  flex: 1;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
  text-align: center;
  background: var(--canvas-raised);
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-panel);
}

.test-environment-picker__state strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.test-environment-picker__footer-spacer {
  flex: 1;
}

@media (max-width: 640px) {
  .test-environment-picker {
    padding: 0 var(--space-4) var(--space-4);
  }

  .test-environment-picker__option {
    grid-template-columns:
      var(--control-lg)
      minmax(0, 1fr)
      var(--control-sm);
  }

  .test-environment-picker__account {
    display: none;
  }

  .test-environment-picker__footer-spacer {
    display: none;
  }
}
</style>

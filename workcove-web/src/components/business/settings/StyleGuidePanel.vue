<script setup lang="ts">
import {
  ChevronDown,
  Code2,
  FolderCode,
  Globe2,
  Image as ImageIcon,
  Server,
} from '@lucide/vue'
import { ref } from 'vue'

import { PRODUCT_NAME } from '@/brand'
import UiButton from '@/components/ui/UiButton.vue'
import UiFormField from '@/components/ui/UiFormField.vue'
import UiInput from '@/components/ui/UiInput.vue'
import UiSegmentedControl from '@/components/ui/UiSegmentedControl.vue'
import UiSelect from '@/components/ui/UiSelect.vue'

const sampleSegment = ref('regular')
const sampleInput = ref('')
const sampleSelect = ref('doing')
const sampleCategory = ref('未分类')

const segmentItems = [
  { value: 'compact', label: '紧凑' },
  { value: 'regular', label: '标准' },
  { value: 'relaxed', label: '宽松' },
]

const selectOptions = [
  { value: 'wait', label: '未开始' },
  { value: 'doing', label: '进行中', description: '当前激活的项目' },
  { value: 'suspended', label: '已挂起' },
]

const categoryOptions = [
  { value: '未分类', label: '未分类' },
  { value: '研发工具', label: '研发工具' },
  { value: '项目资料', label: '项目资料' },
]

const colors = [
  { name: '系统蓝', token: '--system-blue', value: '#007AFF / #0A84FF' },
  { name: '系统红', token: '--system-red', value: '#FF3B30 / #FF453A' },
  { name: '系统橙', token: '--system-orange', value: '#FF9500 / #FF9F0A' },
  { name: '系统绿', token: '--system-green', value: '#34C759 / #30D158' },
]
</script>

<template>
  <section class="style-guide" aria-labelledby="style-guide-title">
    <header class="style-guide__header">
      <div>
        <h3 id="style-guide-title">{{ PRODUCT_NAME }}样式规范</h3>
        <p>后续页面统一使用这里的颜色、字号、控件和窗口关系。</p>
      </div>
      <span class="style-guide__version">v1.1</span>
    </header>

    <div class="style-guide__sections">
      <section class="guide-section">
        <header>
          <h4>系统颜色</h4>
          <p>颜色只表达交互和状态；数值依次为浅色 / 深色。</p>
        </header>
        <div class="color-list">
          <div v-for="color in colors" :key="color.token" class="color-item">
            <span
              class="color-item__swatch"
              :style="{ background: `var(${color.token})` }"
            />
            <div>
              <strong>{{ color.name }}</strong>
              <small>{{ color.value }}</small>
            </div>
          </div>
        </div>
      </section>

      <section class="guide-section guide-section--wide">
        <header>
          <h4>下拉选择</h4>
          <p>
            禁止使用原生 select 弹层。统一使用不透明 raised surface、10px
            圆角、选中勾和键盘导航。
          </p>
        </header>
        <div class="select-samples">
          <UiFormField
            label="标准选择"
            control-id="style-guide-select"
          >
            <UiSelect
              id="style-guide-select"
              v-model="sampleSelect"
              :options="selectOptions"
              size="lg"
              aria-label="项目状态示例"
            />
          </UiFormField>
          <div class="select-samples__chip">
            <span>分类快捷切换</span>
            <UiSelect
              v-model="sampleCategory"
              :options="categoryOptions"
              aria-label="分类示例"
              size="sm"
              variant="chip"
            />
          </div>
        </div>
      </section>

      <section class="guide-section">
        <header>
          <h4>字体层级</h4>
          <p>跟随全局字体设置，正文 13px，辅助信息最低 11px。</p>
        </header>
        <div class="type-list">
          <div>
            <span class="type-list__label">标题 15px</span>
            <strong class="type-list__title">清晰、克制、可扫读</strong>
          </div>
          <div>
            <span class="type-list__label">正文 13px</span>
            <p>用于正文、按钮、输入框和主要操作说明。</p>
          </div>
          <div>
            <span class="type-list__label">辅助 11px</span>
            <small>用于提示、元信息和低优先级内容。</small>
          </div>
        </div>
      </section>

      <section class="guide-section guide-section--wide">
        <header>
          <h4>基础控件</h4>
          <p>按钮、输入框和分段控件必须复用基础组件。</p>
        </header>
        <div class="control-samples">
          <div class="control-samples__row">
            <UiButton>取消</UiButton>
            <UiButton variant="primary">保存</UiButton>
            <UiButton variant="danger">删除</UiButton>
            <UiButton variant="ghost">次要操作</UiButton>
          </div>
          <UiSegmentedControl
            v-model="sampleSegment"
            :items="segmentItems"
            aria-label="密度示例"
          />
          <UiFormField label="输入框" control-id="style-guide-input">
            <UiInput
              id="style-guide-input"
              v-model="sampleInput"
              size="lg"
              placeholder="13px 全局字体"
            />
          </UiFormField>
        </div>
      </section>

      <section class="guide-section">
        <header>
          <h4>导航层级</h4>
          <p>一级菜单负责领域，二级菜单放具体工作入口。</p>
        </header>
        <div class="navigation-sample" aria-hidden="true">
          <span class="navigation-sample__primary">
            <Code2 :size="15" />
            研发
            <ChevronDown :size="13" />
          </span>
          <span class="navigation-sample__child">
            <FolderCode :size="14" />
            工作目录
          </span>
          <span class="navigation-sample__child">
            <Server :size="14" />
            服务器管理
          </span>
        </div>
      </section>

      <section class="guide-section">
        <header>
          <h4>窗口与弹窗</h4>
          <p>
            12px 圆角、材质背景；长内容只滚动正文，禁止整窗滚动。
          </p>
        </header>
        <div class="window-sample" aria-hidden="true">
          <div class="window-sample__traffic">
            <span class="window-sample__close" />
            <span class="window-sample__minimize" />
            <span class="window-sample__zoom" />
          </div>
          <strong>添加常用网址</strong>
          <p>标题左对齐，说明紧随标题，操作位于右下角。</p>
          <div class="window-sample__actions">
            <span>取消</span>
            <span class="window-sample__primary">保存</span>
          </div>
        </div>
        <div class="dialog-scroll-standard">
          <span><strong>固定头部</strong>三个窗口按钮和标题不随滚轮移动</span>
          <span><strong>滚动正文</strong>只让正文出现滚动条，禁止整窗滚动</span>
          <span><strong>固定操作</strong>底部按钮通过 footer 插槽常驻</span>
        </div>
      </section>

      <section class="guide-section">
        <header>
          <h4>快捷图标</h4>
          <p>加载成功后移除占位图，透明区域不再透出地球。</p>
        </header>
        <div class="icon-rules">
          <div>
            <span class="icon-rules__example icon-rules__example--fallback">
              <Globe2 :size="20" />
            </span>
            <span>
              <strong>仅失败时显示</strong>
              <small>没有图标或所有来源加载失败</small>
            </span>
          </div>
          <div>
            <span class="icon-rules__example">
              <ImageIcon :size="20" />
            </span>
            <span>
              <strong>成功加载后隐藏占位</strong>
              <small>透明 PNG / SVG 只显示自身内容</small>
            </span>
          </div>
        </div>
        <p class="website-usage-rule">
          <strong>网址 Top 10</strong>
          网址页按分类管理，分类可拖拽排序；工作台仅按点击量展示前 10 条，同点击量时按创建时间倒序。
        </p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.style-guide {
  padding: 20px 16px 22px;
  border-top: 1px solid var(--line-soft);
}

.style-guide__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.style-guide__header h3 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
  line-height: var(--line-title);
}

.style-guide__header p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.style-guide__version {
  flex: 0 0 auto;
  padding: 3px 7px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
  background: var(--canvas);
  border-radius: var(--radius-badge);
}

.style-guide__sections {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px 32px;
  margin-top: 24px;
}

.guide-section {
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.guide-section--wide {
  grid-column: 1 / -1;
}

.guide-section > header h4 {
  margin: 0;
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.guide-section > header p {
  margin: 4px 0 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.color-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 18px;
  margin-top: 14px;
}

.color-item {
  display: flex;
  align-items: center;
  gap: 9px;
}

.color-item__swatch {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 14%);
}

.color-item > div {
  display: flex;
  flex-direction: column;
}

.color-item strong,
.icon-rules strong {
  color: var(--text);
  font-size: var(--font-caption);
  font-weight: var(--weight-emphasis);
}

.color-item small,
.icon-rules small {
  margin-top: 2px;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.type-list {
  display: flex;
  margin-top: 14px;
  flex-direction: column;
  gap: 12px;
}

.type-list > div {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: baseline;
  gap: 12px;
}

.type-list__label {
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: var(--font-caption);
}

.type-list p,
.type-list small,
.type-list__title {
  margin: 0;
}

.type-list p {
  color: var(--text);
  font-size: var(--font-body);
  line-height: var(--line-body);
}

.type-list small {
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.type-list__title {
  color: var(--text);
  font-size: var(--font-title);
  font-weight: var(--weight-emphasis);
}

.control-samples {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 260px);
  align-items: end;
  gap: 16px 24px;
  margin-top: 16px;
}

.control-samples__row {
  display: flex;
  grid-column: 1 / -1;
  flex-wrap: wrap;
  gap: 10px;
}

.select-samples {
  display: grid;
  grid-template-columns: minmax(220px, 320px) minmax(180px, 1fr);
  align-items: end;
  gap: var(--space-4);
  margin-top: var(--space-4);
}

.select-samples__chip {
  display: flex;
  min-height: var(--control-lg);
  align-items: center;
  gap: var(--space-2);
  color: var(--text-subtle);
  font-size: var(--font-caption);
}

.navigation-sample {
  display: flex;
  padding: var(--space-2);
  margin-top: var(--space-3);
  background: var(--canvas);
  border-radius: var(--radius-panel);
  flex-direction: column;
  gap: 2px;
}

.navigation-sample__primary,
.navigation-sample__child {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.navigation-sample__primary {
  min-height: 30px;
  padding: 0 var(--space-2);
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.navigation-sample__primary svg:last-child {
  margin-left: auto;
  color: var(--text-subtle);
}

.navigation-sample__child {
  min-height: 28px;
  padding-left: var(--space-5);
  margin-left: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  border-left: 1px solid var(--line-soft);
}

.window-sample {
  padding: 16px;
  margin-top: 14px;
  background: var(--window-material);
  border-radius: var(--radius-window);
  box-shadow: var(--shadow-popover);
}

.window-sample__traffic {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.window-sample__traffic span {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 0.5px rgb(0 0 0 / 14%);
}

.window-sample__close {
  background: var(--traffic-close);
}

.window-sample__minimize {
  background: var(--traffic-minimize);
  opacity: 0.52;
}

.window-sample__zoom {
  background: var(--traffic-zoom);
  opacity: 0.52;
}

.window-sample > strong {
  color: var(--text);
  font-size: var(--font-body);
  font-weight: var(--weight-emphasis);
}

.window-sample > p {
  margin: 4px 0 14px;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.window-sample__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.window-sample__actions span {
  display: inline-flex;
  height: 24px;
  align-items: center;
  padding: 0 10px;
  color: var(--text);
  font-size: var(--font-caption);
  background: var(--control-bg);
  border-radius: var(--radius-control);
  box-shadow: var(--control-shadow);
}

.window-sample__actions .window-sample__primary {
  color: #ffffff;
  background: var(--system-blue);
}

.dialog-scroll-standard {
  display: flex;
  margin-top: var(--space-3);
  flex-direction: column;
  gap: var(--space-2);
}

.dialog-scroll-standard span {
  display: flex;
  gap: var(--space-2);
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

.dialog-scroll-standard strong {
  flex: 0 0 64px;
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

.icon-rules {
  display: flex;
  margin-top: 14px;
  flex-direction: column;
  gap: 12px;
}

.icon-rules > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-rules > div > span:last-child {
  display: flex;
  flex-direction: column;
}

.icon-rules__example {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  color: var(--text-muted);
  background: var(--canvas);
  border-radius: 10px;
  box-shadow: inset 0 0 0 0.5px var(--separator);
  place-items: center;
}

.icon-rules__example--fallback {
  color: var(--text-subtle);
}

.website-usage-rule {
  padding: var(--space-2);
  margin: var(--space-3) 0 0;
  color: var(--text-muted);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
  background: var(--canvas);
  border-radius: var(--radius-control);
}

.website-usage-rule strong {
  display: block;
  margin-bottom: 2px;
  color: var(--text);
  font-weight: var(--weight-emphasis);
}

@media (max-width: 760px) {
  .style-guide__sections {
    grid-template-columns: 1fr;
  }

  .guide-section--wide {
    grid-column: auto;
  }

  .control-samples {
    grid-template-columns: 1fr;
  }

  .select-samples {
    grid-template-columns: 1fr;
  }
}
</style>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    controlId?: string
    hint?: string
    required?: boolean
    layout?: 'stacked' | 'dialog'
  }>(),
  {
    controlId: undefined,
    hint: '',
    required: false,
    layout: 'stacked',
  },
)
</script>

<template>
  <div class="ui-form-field" :class="`ui-form-field--${layout}`">
    <component
      :is="controlId ? 'label' : 'span'"
      class="ui-form-field__label"
      :for="controlId"
    >
      {{ label }}
      <span v-if="required" class="ui-form-field__required" aria-hidden="true">*</span>
    </component>
    <div class="ui-form-field__content">
      <slot />
      <p v-if="hint" class="ui-form-field__hint">{{ hint }}</p>
    </div>
  </div>
</template>

<style scoped>
.ui-form-field {
  min-width: 0;
}

.ui-form-field--stacked {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ui-form-field--dialog {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}

.ui-form-field__label {
  color: var(--text);
  font-size: var(--font-body);
  line-height: 16px;
}

.ui-form-field--dialog .ui-form-field__label {
  padding-top: 6px;
  text-align: right;
}

.ui-form-field__required {
  margin-left: 2px;
  color: var(--system-red);
}

.ui-form-field__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.ui-form-field__hint {
  margin: 0;
  color: var(--text-subtle);
  font-size: var(--font-caption);
  line-height: var(--line-caption);
}

@media (max-width: 520px) {
  .ui-form-field--dialog {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .ui-form-field--dialog .ui-form-field__label {
    padding-top: 0;
    text-align: left;
  }
}
</style>

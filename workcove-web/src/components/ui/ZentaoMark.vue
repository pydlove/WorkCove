<script setup lang="ts">
import zentaoMark from '@/assets/integrations/zentao.png'

defineOptions({
  inheritAttrs: false,
})

withDefaults(
  defineProps<{
    size?: number
  }>(),
  {
    size: 16,
  },
)
</script>

<template>
  <span
    class="zentao-mark"
    aria-hidden="true"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      maskImage: `url(${zentaoMark})`,
      WebkitMaskImage: `url(${zentaoMark})`,
    }"
  />
</template>

<style scoped>
.zentao-mark {
  display: inline-block;
  flex: 0 0 auto;
  background: currentcolor;
  mask-position: center;
  mask-repeat: no-repeat;
  mask-size: contain;
  user-select: none;
}
</style>

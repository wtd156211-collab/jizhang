<script setup lang="ts">
import Odometer from './Odometer.vue'

/* 显示级读数：等宽大数字 + 上标单位。size 对应 --fs-display-xl / l / m 三档 */
withDefaults(defineProps<{
  value?: number | null
  /** 精确读数文本，适用于财务金额等不能转为 Number 的数据。 */
  displayText?: string
  unit?: string
  decimals?: number
  size?: 'xl' | 'l' | 'm' | 's'
  label?: string
}>(), {
  decimals: 0,
  size: 'm',
})
</script>

<template>
  <div :class="['metric', `metric-${size}`]">
    <span v-if="label" class="metric-label">{{ label }}</span>
    <span class="metric-figure">
      <Odometer :value="value" :decimals="decimals" :display-text="displayText" />
      <sup v-if="unit" class="metric-unit">{{ unit }}</sup>
    </span>
    <slot />
  </div>
</template>

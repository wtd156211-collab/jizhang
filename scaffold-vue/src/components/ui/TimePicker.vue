<script setup lang="ts">
import { ref } from 'vue'
import PickerPopover from './PickerPopover.vue'
import Button from './Button.vue'
import { validTime } from '@/utils/finance'
defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{ modelValue?: string | null; placeholder?: string; disabled?: boolean; invalid?: boolean }>(), { placeholder: '时间未记录' })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const parts = ref(['', '', '']), headings = ['时', '分', '秒']
function init() { parts.value = props.modelValue && validTime(props.modelValue) ? [...props.modelValue.split(':'), '00'].slice(0, 3) : ['', '', ''] }
function choose(value: string, close: () => void) { emit('update:modelValue', value); close() }
function keys(e: KeyboardEvent, index: number, value: number) {
  const count = index === 0 ? 24 : 60
  const step = e.key === 'ArrowDown' ? 1 : e.key === 'ArrowUp' ? -1 : 0
  if (!step) return
  e.preventDefault(); parts.value[index] = String((value + step + count) % count).padStart(2, '0')
  const target = (e.currentTarget as HTMLElement).parentElement?.querySelector<HTMLElement>(`[data-time-value="${parts.value[index]}"]`)
  target?.focus(); target?.scrollIntoView({ block: 'nearest' })
}
</script>
<template>
  <PickerPopover v-bind="$attrs" :value="modelValue" :placeholder="placeholder" :disabled="disabled" :invalid="invalid" label="选择时间" icon="clock" @show="init">
    <template #default="{ close }">
      <div class="picker-head"><b>实际发生时间</b><span class="faint">{{ parts.every(Boolean) ? parts.join(':') : '未记录' }}</span></div>
      <div class="time-columns"><div v-for="(heading, index) in headings" :key="heading" class="time-column"><span class="time-heading">{{ heading }}</span><div class="time-options" :aria-label="heading">
        <button v-for="n in index === 0 ? 24 : 60" :key="n" type="button" :data-time-value="String(n - 1).padStart(2, '0')" :data-picker-focus="index === 0 && n === Number(parts[0] || '0') + 1 ? '' : undefined" :aria-label="`${String(n - 1).padStart(2, '0')}${heading}`" :aria-pressed="parts[index] === String(n - 1).padStart(2, '0')" :class="['picker-time', parts[index] === String(n - 1).padStart(2, '0') && 'is-selected']" @keydown="keys($event, index, n - 1)" @click="parts[index] = String(n - 1).padStart(2, '0')">{{ String(n - 1).padStart(2, '0') }}</button>
      </div></div></div>
      <div class="picker-foot"><button type="button" class="link-btn" @click="choose('', close)">清空时间</button><Button size="sm" variant="primary" :disabled="!parts.every(Boolean)" @click="choose(parts.join(':'), close)">确定</Button></div>
    </template>
  </PickerPopover>
</template>

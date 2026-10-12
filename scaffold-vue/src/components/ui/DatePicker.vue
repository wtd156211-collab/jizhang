<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import PickerPopover from './PickerPopover.vue'
import Icon from '@/components/Icon.vue'
import { localDate, monthDays, shiftDate, validDate } from '@/utils/finance'
defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{ modelValue?: string | null; placeholder?: string; disabled?: boolean; invalid?: boolean; clearable?: boolean }>(), { placeholder: '选择日期', clearable: true })
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const month = ref(localDate(new Date()).slice(0, 7)), focused = ref('')
const days = computed(() => monthDays(month.value)), today = computed(() => localDate(new Date()))
function init() { focused.value = validDate(props.modelValue ?? '') ? props.modelValue! : today.value; month.value = focused.value.slice(0, 7) }
function moveMonth(step: number) {
  const [year, index] = month.value.split('-').map(Number), next = localDate(new Date(year, index - 1 + step, 1, 12))
  if (!validDate(next)) return
  month.value = next.slice(0, 7); focused.value = next
}
function moveFocus(e: KeyboardEvent, date: string) {
  const step = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[e.key]
  if (!step) return
  e.preventDefault(); const next = shiftDate(date, step); if (!validDate(next)) return
  focused.value = next; month.value = next.slice(0, 7)
  nextTick(() => document.querySelector<HTMLElement>(`[data-date="${next}"]`)?.focus())
}
function choose(date: string, close: () => void) { emit('update:modelValue', date); close() }
</script>
<template>
  <PickerPopover v-bind="$attrs" :value="modelValue" :placeholder="placeholder" :disabled="disabled" :invalid="invalid" label="选择日期" icon="calendar" @show="init">
    <template #default="{ close }">
      <div class="picker-head"><button type="button" class="picker-nav" aria-label="上个月" @click="moveMonth(-1)"><Icon name="chevronLeft" :size="16" /></button><b class="tnum">{{ month.replace('-', ' 年 ') }} 月</b><button type="button" class="picker-nav" aria-label="下个月" @click="moveMonth(1)"><Icon name="chevronRight" :size="16" /></button></div>
      <div class="picker-week"><span v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}</span></div>
      <div class="picker-calendar">
        <template v-for="(date, i) in days" :key="date || i"><button v-if="date" type="button" :data-date="date" :data-picker-focus="date === focused ? '' : undefined" :tabindex="date === focused ? 0 : -1" :aria-label="date" :aria-pressed="date === modelValue" :aria-current="date === today ? 'date' : undefined" :class="['picker-day', date === modelValue && 'is-selected', date === today && 'is-today']" @keydown="moveFocus($event, date)" @click="choose(date, close)">{{ Number(date.slice(-2)) }}</button><span v-else /></template>
      </div>
      <div class="picker-foot"><button v-if="clearable" type="button" class="link-btn" @click="choose('', close)">清空</button><button type="button" class="link-btn" @click="choose(today, close)">今天</button></div>
    </template>
  </PickerPopover>
</template>

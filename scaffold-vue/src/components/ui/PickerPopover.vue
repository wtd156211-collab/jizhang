<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import Icon from '@/components/Icon.vue'
import { overlayEnter, overlayLeave } from '@/composables/motion'
import { floatingLayerFor } from '@/composables/focus'
defineOptions({ inheritAttrs: false })
const props = withDefaults(defineProps<{ value?: string | null; placeholder?: string; icon?: string; disabled?: boolean; invalid?: boolean; label: string }>(), { placeholder: '请选择', icon: 'calendar' })
const emit = defineEmits<{ show: [] }>()
const trigger = ref<HTMLElement | null>(null), menu = ref<HTMLElement | null>(null), open = ref(false)
const pos = ref({ top: 0, left: 0, width: 288 }), id = `picker-${Math.random().toString(36).slice(2, 9)}`
function place() {
  const rect = trigger.value?.getBoundingClientRect(); if (!rect) return
  const width = Math.min(288, window.innerWidth - 16), height = menu.value?.offsetHeight ?? 320
  const below = window.innerHeight - rect.bottom - 8
  const top = below < height && rect.top > height + 8 ? rect.top - height - 6 : rect.bottom + 6
  pos.value = { top: Math.max(8, Math.min(top, window.innerHeight - Math.min(height, window.innerHeight - 16) - 8)), left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)), width }
}
function close(refocus = true) {
  open.value = false
  document.removeEventListener('pointerdown', outside, true); window.removeEventListener('scroll', place, true); window.removeEventListener('resize', place)
  if (refocus) trigger.value?.focus()
}
function outside(e: PointerEvent) { if (!trigger.value?.contains(e.target as Node) && !menu.value?.contains(e.target as Node)) close(false) }
async function show() {
  if (props.disabled) return
  emit('show'); open.value = true; place(); await nextTick(); place()
  const focus = menu.value?.querySelector<HTMLElement>('[data-picker-focus]') ?? menu.value?.querySelector<HTMLElement>('button:not(:disabled)')
  focus?.focus()
  document.addEventListener('pointerdown', outside, true); window.addEventListener('scroll', place, true); window.addEventListener('resize', place)
}
function key(e: KeyboardEvent) {
  if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); close() }
  if (e.key === 'Tab') {
    const items = Array.from(menu.value?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled)') ?? [])
    const index = items.indexOf(document.activeElement as HTMLElement)
    if ((e.shiftKey && index === 0) || (!e.shiftKey && index === items.length - 1)) { e.preventDefault(); items[e.shiftKey ? items.length - 1 : 0]?.focus() }
  }
}
onBeforeUnmount(() => close(false))
</script>
<template>
  <button v-bind="$attrs" ref="trigger" type="button" :class="['input-shell', 'select-trigger', invalid && 'is-invalid', disabled && 'is-disabled']" :disabled="disabled" :aria-invalid="invalid || undefined" aria-haspopup="dialog" :aria-expanded="open" :aria-controls="id" @click="open ? close() : show()" @keydown.down.prevent="show">
    <span :class="['select-value', !value && 'is-placeholder']">{{ value || placeholder }}</span><Icon :name="icon" :size="15" class="select-caret" />
  </button>
  <Teleport to="body"><Transition :css="false" @enter="(el, done) => overlayEnter(el, done, 'pop')" @leave="(el, done) => overlayLeave(el, done, 'pop')">
    <div v-if="open" :id="id" ref="menu" role="dialog" :aria-label="label" class="select-menu picker-menu m-float" :style="{ top: `${pos.top}px`, left: `${pos.left}px`, width: `${pos.width}px`, zIndex: floatingLayerFor(trigger), maxHeight: 'calc(100dvh - 16px)' }" @keydown="key"><slot :close="close" /></div>
  </Transition></Teleport>
</template>

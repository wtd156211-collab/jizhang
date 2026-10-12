<script setup lang="ts">
import { computed } from 'vue'
import { pathOf } from '@/composables/auth'
const links = computed(() => [{ label: '工作台', path: pathOf('dashboard/index') }, { label: '财务工作台', path: pathOf('bookkeeping/dashboard/index') }].filter(item => item.path))
</script>
<template><nav class="workspace-switch" aria-label="工作台切换"><template v-for="(item, index) in links" :key="item.path!"><span v-if="index" class="workspace-divider" aria-hidden="true" /><RouterLink :to="item.path!" active-class="is-active">{{ item.label }}</RouterLink></template><span v-if="links.length > 1" class="workspace-divider" aria-hidden="true" /></nav></template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@/components/Icon.vue'
import { Button, DatePicker, EmptyState, Field, PageHeader, SectionLabel, Segmented } from '@/components/ui'
import FinanceOverview from '@/components/bookkeeping/FinanceOverview.vue'
import RecordDetail from '@/components/bookkeeping/RecordDetail.vue'
import { bookkeepingApi, type FinanceSummary } from '@/api'
import { pathOf } from '@/composables/auth'
import { toastError } from '@/composables/toast'
import { hasPermission } from '@/directives/permission'
import { dateRange, validDate } from '@/utils/finance'
const router = useRouter(), range = ref('30'), query = reactive(dateRange(30)), summary = ref<FinanceSummary | null>(null)
const loading = ref(false), error = ref(false), detail = ref<number | null>(null)
let requestId = 0
const shortcuts = computed(() => [
  { component: 'bookkeeping/records/index', title: '记一笔', desc: '新增与查看收支', icon: 'plus' },
  { component: 'bookkeeping/reviews/index', title: '审核中心', desc: '核对待审核账目', icon: 'check' },
  { component: 'bookkeeping/reports/index', title: '财务报表', desc: '汇总与 Excel 导出', icon: 'chart' },
  { component: 'bookkeeping/config/index', title: '基础配置', desc: '分类、账户与项目', icon: 'settings' },
].map(item => ({ ...item, path: pathOf(item.component) })).filter(item => item.path))
async function load() {
  if (!validDate(query.occurredFrom) || !validDate(query.occurredTo) || query.occurredFrom > query.occurredTo) { toastError(new Error('请选择正确的发生日期范围'), '日期范围不正确'); return }
  const id = ++requestId; loading.value = true; error.value = false
  try { const result = await bookkeepingApi.dashboard({ ...query }); if (id === requestId) summary.value = result }
  catch (e) { if (id === requestId) { error.value = true; summary.value = null; toastError(e, '财务统计加载失败') } }
  finally { if (id === requestId) loading.value = false }
}
function selectRange(value: string) { range.value = value; if (value !== 'custom') { Object.assign(query,dateRange(Number(value))); load() } }
onMounted(load)
</script>
<template>
  <div class="page finance-page">
    <PageHeader title="财务工作台" desc="公司收支、费用结构与入账进度。金额仅统计已入账记录。"><Button icon="refresh" :loading="loading" @click="load">刷新</Button></PageHeader>
    <div class="finance-period"><Segmented :model-value="range" label="统计周期" :options="[{ label: '今天', value: '1' }, { label: '7 天', value: '7' }, { label: '30 天', value: '30' }, { label: '自定义', value: 'custom' }]" @update:model-value="selectRange" /><form v-if="range === 'custom'" class="finance-date-range" @submit.prevent="load"><Field label="发生日期起"><DatePicker v-model="query.occurredFrom" /></Field><Field label="发生日期止"><DatePicker v-model="query.occurredTo" /></Field><Button type="submit" variant="primary" :loading="loading">应用</Button></form><span class="faint">{{ summary ? `${summary.from} — ${summary.to}` : '按实际发生日期' }}</span></div>
    <EmptyState v-if="error" title="财务数据读取失败" desc="点击刷新重试" icon="alert" />
    <FinanceOverview v-else :summary="summary" :loading="loading" show-latest :can-detail="hasPermission('bookkeeping:record:list')" @detail="detail = $event"><template #actions><button v-if="pathOf('bookkeeping/reports/index')" class="pill-btn is-dark" type="button" @click="router.push(pathOf('bookkeeping/reports/index')!)">查看财务报表 <Icon name="arrowRight" :size="14" /></button></template></FinanceOverview>
    <section v-if="shortcuts.length" class="bento"><div class="bento-head"><SectionLabel title="常用入口" /></div><div class="shortcut-grid"><button v-for="item in shortcuts" :key="item.component" type="button" class="quick-link lift m-surface" @click="router.push(item.path!)"><span class="quick-ico m-groove"><Icon :name="item.icon" :size="17" /></span><span class="quick-text"><span class="quick-title">{{ item.title }}</span><span class="quick-sub">{{ item.desc }}</span></span><Icon name="arrowRight" :size="15" class="quick-go" /></button></div></section>
    <RecordDetail :id="detail" :revision="0" readonly @close="detail = null" />
  </div>
</template>

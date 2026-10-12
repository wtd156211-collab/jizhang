<script setup lang="ts">
import { ref } from 'vue'
import { DatePicker, Field, Select, type Column } from '@/components/ui'
import { ListPage } from '@/components/business'
import FinanceOverview from '@/components/bookkeeping/FinanceOverview.vue'
import FinanceCategories from '@/components/bookkeeping/FinanceCategories.vue'
import { bookkeepingApi, type FinanceSummary } from '@/api'
import { useListPage } from '@/composables/list'
import { hasPermission } from '@/directives/permission'
import { dateRange, displayMoney, validDate } from '@/utils/finance'
import { RECORD_TYPES } from '@/utils/bookkeeping'
const summary = ref<FinanceSummary | null>(null)
let requestId = 0
const list = useListPage({ query: { ...dateRange(30), recordType: '' }, fetch: async params => {
  if (!validDate(String(params.occurredFrom ?? '')) || !validDate(String(params.occurredTo ?? '')) || String(params.occurredFrom) > String(params.occurredTo)) throw new Error('请选择正确的发生日期范围')
  const id = ++requestId
  try {
    const [data, overview] = await Promise.all([bookkeepingApi.reports.daily(params),bookkeepingApi.reports.summary(params)])
    if (id === requestId) summary.value = overview
    return data
  } catch (e) { if (id === requestId) summary.value = null; throw e }
}, exporter: hasPermission('bookkeeping:record:export') ? params => bookkeepingApi.reports.export(params) : undefined, exportName: '财务报表.xlsx' })
const columns: Column[] = [{ key: 'date', title: '发生日期', width: 170 }, { key: 'income', title: '收入（元）', align: 'right' }, { key: 'expense', title: '支出（元）', align: 'right' }, { key: 'net', title: '净额（元）', align: 'right' }, { key: 'count', title: '已入账笔数', align: 'right', width: 125 }]
</script>
<template>
  <ListPage title="报表与导出" desc="按实际发生日期统计；仅计入已入账账目。Excel 包含统计口径、每日收支、分类汇总、账目与明细。" table-title="每日收支" :list="list" :columns="columns" row-key="date" :min-width="760">
    <template #before><FinanceOverview :summary="summary" :loading="list.loading.value" /><FinanceCategories :rows="summary?.categories ?? []" :loading="list.loading.value" /></template>
    <template #search><Field label="发生日期起"><DatePicker v-model="list.query.occurredFrom" /></Field><Field label="发生日期止"><DatePicker v-model="list.query.occurredTo" /></Field><Field label="收支类型"><Select v-model="list.query.recordType" :options="[{ label: '全部收支', value: '' }, ...RECORD_TYPES]" /></Field></template>
    <template #cell-income="{ row }"><b class="tnum">{{ displayMoney(row.income) }}</b></template>
    <template #cell-expense="{ row }"><b class="tnum">{{ displayMoney(row.expense) }}</b></template>
    <template #cell-net="{ row }"><b class="tnum">{{ displayMoney(row.net) }}</b></template>
  </ListPage>
</template>

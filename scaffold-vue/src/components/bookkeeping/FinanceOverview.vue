<script setup lang="ts">
import { computed } from 'vue'
import { Avatar, DeltaPill, EmptyState, Metric, SectionLabel, Tag } from '@/components/ui'
import TrendChart from '@/components/charts/TrendChart.vue'
import BarChart from '@/components/charts/BarChart.vue'
import TickRing from '@/components/charts/TickRing.vue'
import { trackGlow } from '@/composables/motion'
import type { FinanceSummary } from '@/api'
import { displayMoney, moneyChange, moneyRatio } from '@/utils/finance'
import { STATUS_LABELS } from '@/utils/bookkeeping'
const props = defineProps<{ summary: FinanceSummary | null; loading?: boolean; showLatest?: boolean; canDetail?: boolean }>()
const emit = defineEmits<{ detail: [id: number] }>()
const days = computed(() => props.summary?.daily ?? [])
const lastDays = computed(() => days.value.slice(-7))
const expenses = computed(() => (props.summary?.categories ?? []).filter(row => row.recordType === 'EXPENSE'))
const categories = computed(() => expenses.value.slice(0, 5))
const change = computed(() => moneyChange(props.summary?.income ?? '0.00', props.summary?.previous.income ?? '0.00'))
const postedRate = computed(() => props.summary?.activeCount ? props.summary.postedCount / props.summary.activeCount * 100 : 0)
const states = computed(() => Object.entries(props.summary?.states ?? {}).filter(([state]) => state !== 'VOIDED'))
const chartTick = (value: number) => value >= 1e8 ? `${(value / 1e8).toFixed(0)}亿` : value >= 1e4 ? `${(value / 1e4).toFixed(0)}万` : String(Math.round(value))
</script>
<template>
  <div class="bento-grid finance-grid" :aria-busy="loading">
    <section class="bento bento-hero m-glow" @pointermove="trackGlow">
      <span class="glow-layer" aria-hidden="true" />
      <div class="hero-head"><SectionLabel title="收支脉搏" /><Tag tone="ok" dot>仅已入账</Tag></div>
      <div class="hero-body">
        <div class="hero-figure"><span class="side-label">本期收入</span><Metric :display-text="displayMoney(summary?.income)" unit="元" size="l" />
          <div class="hero-delta"><DeltaPill v-if="summary" :value="change.value" :fresh="change.fresh" label="收入较上一等长周期" /><span class="faint">较上一等长周期</span></div>
          <dl class="hero-readouts"><div><dt>已入账</dt><dd class="tnum">{{ summary?.postedCount ?? '—' }} 笔</dd></div><div><dt>统计天数</dt><dd class="tnum">{{ summary ? days.length : '—' }} 天</dd></div></dl>
        </div>
        <div class="hero-chart"><TrendChart :values="days.map(d => Number(d.income))" :secondary="days.map(d => Number(d.expense))" :display-values="days.map(d => displayMoney(d.income))" :secondary-display-values="days.map(d => displayMoney(d.expense))" :tick-formatter="chartTick" :labels="days.map(d => d.date.slice(5))" :height="212" unit=" 元" primary-label="收入" secondary-label="支出" tone="ink" /><div class="finance-legend"><span><i class="tip-key is-primary" />收入</span><span><i class="tip-key is-secondary" />支出</span></div></div>
      </div>
      <div class="hero-actions"><slot name="actions" /></div>
      <span class="faint">{{ summary ? `${summary.from} — ${summary.to}` : '正在读取数据' }} · 按实际发生日期</span>
    </section>
    <section class="bento bento-side"><div class="side-top"><span class="side-label">本期支出</span><Tag tone="neutral">人民币</Tag></div><Metric :display-text="displayMoney(summary?.expense)" unit="元" size="l" /><div class="side-foot"><span class="muted">已审核通过的费用合计</span></div></section>
    <section class="bento bento-side"><div class="side-top"><span class="side-label">本期净额</span><Tag :tone="summary?.net.startsWith('-') ? 'danger' : 'ok'">收入 − 支出</Tag></div><Metric :display-text="displayMoney(summary?.net)" unit="元" size="l" /><div class="side-foot"><span class="muted">期间收支差额</span></div></section>
    <section class="bento bento-third"><div class="bento-head"><SectionLabel title="每日支出" /><span class="bento-meta">本期最后 {{ lastDays.length }} 天</span></div><BarChart :values="lastDays.map(d => Number(d.expense))" :display-values="lastDays.map(d => displayMoney(d.expense))" :labels="lastDays.map(d => d.date.slice(5))" :highlight="lastDays.length - 1" unit=" 元" /></section>
    <section class="bento bento-third bento-ring"><div class="bento-head"><SectionLabel title="入账进度" /><span class="bento-meta">按笔数</span></div><div class="ring-wrap"><TickRing :value="summary ? postedRate : null" label="已入账" :size="164" /><dl class="ring-readouts"><div><dt>已入账 / 有效账目</dt><dd class="tnum">{{ summary?.postedCount ?? '—' }} / {{ summary?.activeCount ?? '—' }}</dd></div><div><dt>待审核</dt><dd class="tnum">{{ summary?.pendingCount ?? '—' }} 笔</dd></div></dl></div><p class="cell-sub">本期账目；排除作废与已删除记录</p></section>
    <section class="bento bento-third"><div class="bento-head"><SectionLabel title="费用分类" /><span class="bento-meta">支出金额前 5</span></div><ul class="share-list finance-shares"><li v-for="row in categories" :key="row.categoryId"><span class="ellipsis" :title="row.categoryName">{{ row.categoryName }}</span><span class="share-track"><i :style="{ width: `${moneyRatio(row.amount, summary?.expense ?? '0.00')}%` }" /></span><b class="tnum" :title="`${displayMoney(row.amount)} 元`">{{ moneyRatio(row.amount, summary?.expense ?? '0.00') }}%</b></li></ul><EmptyState v-if="!loading && !categories.length" title="本期暂无已入账支出" icon="chart" /></section>
    <section class="bento" :class="showLatest ? 'bento-narrow' : 'bento-full'"><div class="bento-head"><SectionLabel title="账目状态" /><span class="bento-meta">本期</span></div><div class="finance-state-list"><div v-for="[state, count] in states" :key="state"><span class="muted">{{ STATUS_LABELS[state as keyof typeof STATUS_LABELS] }}</span><b class="tnum">{{ count }} 笔</b></div></div><p class="cell-sub">已作废 {{ summary?.states.VOIDED ?? 0 }} 笔 · 金额不计入统计</p></section>
    <section v-if="showLatest" class="bento bento-wide"><div class="bento-head"><SectionLabel title="最近入账" /><span class="bento-meta">实际发生时间从新到旧</span></div><div class="activity-list"><div v-for="row in summary?.latest ?? []" :key="row.id" class="activity"><Avatar :name="row.recordType === 'INCOME' ? '收入' : '支出'" :size="30" /><div class="activity-main"><button v-if="canDetail" type="button" class="link-btn" @click="emit('detail', row.id)">{{ row.summary }}</button><b v-else>{{ row.summary }}</b><div class="cell-sub tnum">{{ row.occurredDate }} · {{ row.occurredTime || '时间未记录' }}</div></div><b class="tnum finance-recent-amount">{{ displayMoney(String(row.totalAmount)) }}</b><Tag :tone="row.recordType === 'INCOME' ? 'ok' : 'neutral'">{{ row.recordType === 'INCOME' ? '收入' : '支出' }}</Tag></div><EmptyState v-if="!loading && !summary?.latest.length" title="本期暂无已入账记录" icon="file" /></div></section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DataTable, Pagination, SectionLabel, Tag, type Column } from '@/components/ui'
import type { FinanceCategory } from '@/api'
import { displayMoney } from '@/utils/finance'
const props = defineProps<{ rows: FinanceCategory[]; loading?: boolean }>()
const page = ref(1), size = ref(10)
const visible = computed(() => props.rows.slice((page.value - 1) * size.value,page.value * size.value))
watch(() => props.rows, () => page.value = 1)
const columns: Column[] = [{ key: 'categoryName', title: '分类' }, { key: 'recordType', title: '收支类型', width: 100 }, { key: 'amount', title: '明细金额（元）', align: 'right' }, { key: 'recordCount', title: '涉及账目笔数', align: 'right', width: 145 }]
</script>
<template><section class="bento finance-category-table"><div class="bento-head"><SectionLabel title="分类汇总" /><span class="bento-meta">共 {{ rows.length }} 项</span></div><DataTable :data="visible" :columns="columns" :loading="loading" :min-width="600" row-key="categoryId"><template #cell-recordType="{ row }"><Tag :tone="row.recordType === 'INCOME' ? 'ok' : 'neutral'">{{ row.recordType === 'INCOME' ? '收入' : '支出' }}</Tag></template><template #cell-amount="{ row }"><b class="tnum">{{ displayMoney(row.amount) }}</b></template></DataTable><Pagination :total="rows.length" :page="page" :page-size="size" @update:page="page = $event" @update:page-size="size = $event; page = 1" /><p class="cell-sub">分类金额按明细汇总；同一账目跨分类时分别计入涉及笔数。随本页 Excel 一并导出。</p></section></template>

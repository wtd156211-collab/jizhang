<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button, DataTable, Drawer, Pagination, Tag, type Column } from '@/components/ui'
import { PermissionButton } from '@/components/business'
import ReceiptPanel from './ReceiptPanel.vue'
import { bookkeepingApi, type RecordRow, type OcrTaskRow, type ReviewHistoryRow, type RecordVersionRow, type ReviewAction } from '@/api'
import { hasPermission } from '@/directives/permission'
import { ACTION_LABELS, editable, formatAmount, STATUS_LABELS, statusTone } from '@/utils/bookkeeping'
import { toastError } from '@/composables/toast'
const props = defineProps<{ id: number | null; revision?: number; readonly?: boolean }>()
const emit = defineEmits<{ close: []; edit: [record: RecordRow, task?: OcrTaskRow]; action: [record: RecordRow, action: ReviewAction] }>()
const record = ref<RecordRow | null>(null), history = ref<ReviewHistoryRow[]>([]), versions = ref<RecordVersionRow[]>([])
const loading = ref(false), failed = ref(false), historyFailed = ref(false)
const historyPage = ref(1), historyTotal = ref(0), versionPage = ref(1), versionTotal = ref(0)
const snapshot = ref<RecordVersionRow | null>(null)
let request = 0
async function load() {
  const seq = ++request; if (!props.id) return
  record.value = null; loading.value = true; failed.value = false; snapshot.value = null
  try { const data = await bookkeepingApi.records.get(props.id); if (seq === request) record.value = data }
  catch (e) { if (seq === request) { failed.value = true; toastError(e, '账目详情加载失败') } }
  finally { if (seq === request) loading.value = false }
  if (seq === request && record.value) await loadHistory()
}
async function loadHistory() {
  if (!props.id) return
  const id = props.id, seq = request; historyFailed.value = false
  try {
    const results = await Promise.all([bookkeepingApi.reviews.history(id, { pageNum: historyPage.value, pageSize: 5 }), bookkeepingApi.reviews.versions(id, { pageNum: versionPage.value, pageSize: 5 })])
    if (seq !== request) return
    history.value = results[0].rows; historyTotal.value = results[0].total; versions.value = results[1].rows; versionTotal.value = results[1].total
  } catch (e) { if (seq === request) { historyFailed.value = true; toastError(e, '历史记录加载失败') } }
}
watch(() => [props.id, props.revision], () => { historyPage.value = 1; versionPage.value = 1; history.value = []; versions.value = []; load() }, { immediate: true })
onBeforeUnmount(() => request++)
const columns: Column[] = [{ key: 'categoryName', title: '分类' }, { key: 'amount', title: '金额（元）', align: 'right' }, { key: 'attribution', title: '归属' }, { key: 'remark', title: '备注' }]
const diffRows = computed(() => {
  if (!snapshot.value) return []
  const labels: Record<string, string> = { summary: '摘要', recordType: '收支类型', occurredDate: '发生日期', occurredTime: '发生时间', totalAmount: '总金额', status: '状态', paymentMethodId: '支付方式', fundAccountId: '资金账户', remark: '备注', details: '明细', attachments: '附件信息' }
  return Object.entries(labels).map(([key, label]) => ({ label, before: snapshot.value?.before?.[key], after: snapshot.value?.after?.[key] })).filter(row => JSON.stringify(row.before) !== JSON.stringify(row.after))
})
const display = (value: unknown) => value == null || value === '' ? '—' : typeof value === 'object' ? JSON.stringify(value, null, 2) : STATUS_LABELS[value as keyof typeof STATUS_LABELS] ?? String(value)
</script>
<template>
  <Drawer :open="id !== null" title="账目详情" :sub="record?.recordNo" :width="820" origin-from="tr" @close="emit('close')">
    <p v-if="loading" class="soft">正在加载账目…</p>
    <p v-else-if="failed" class="field-error">无法加载账目。<Button variant="text" @click="load">重试</Button></p>
    <div v-else-if="record" class="drawer-stack">
      <div class="bk-summary"><div><Tag :tone="statusTone(record.status)" dot>{{ STATUS_LABELS[record.status] }}</Tag><p class="bk-summary-title">{{ record.summary }}</p><span class="soft">{{ record.recordType === 'INCOME' ? '收入' : '支出' }} · {{ record.occurredDate }} {{ record.occurredTime || '（时间未记录）' }}</span></div><b class="mono bk-amount">¥ {{ formatAmount(record.totalAmount) }}</b></div>
      <p v-if="record.remark" class="soft">{{ record.remark }}</p>
      <div class="m-surface panel"><DataTable :columns="columns" :data="record.details" :min-width="530">
        <template #cell-amount="{ row }"><span class="mono">{{ formatAmount(row.amount) }}</span></template>
        <template #cell-attribution="{ row }"><span class="soft">{{ [row.handlerId ? `经办人 #${row.handlerId}` : '', row.deptId ? `部门 #${row.deptId}` : '', row.projectId ? `项目 #${row.projectId}` : ''].filter(Boolean).join(' / ') }}</span></template>
      </DataTable></div>
      <ReceiptPanel :key="`${record.id}-${record.version}`" :record="record" @confirm-ocr="emit('edit', record, $event)" />
      <section class="bk-section"><h3 class="section-label">审核历史</h3>
        <p v-if="historyFailed" class="field-error">历史加载失败。<Button size="sm" variant="text" @click="loadHistory">重试</Button></p>
        <p v-else-if="!history.length" class="soft">尚未提交审核。</p>
        <ol class="bk-history"><li v-for="row in history" :key="row.id"><b>{{ ACTION_LABELS[row.action] }}</b><span class="soft">{{ row.operatorName || `用户 #${row.operatorId}` }} · {{ row.reviewTime }}</span><p>{{ STATUS_LABELS[row.fromStatus] }} → {{ STATUS_LABELS[row.toStatus] }}<template v-if="row.reason"> · {{ row.reason }}</template></p></li></ol>
        <Pagination v-if="historyTotal > 5" :total="historyTotal" :page="historyPage" :page-size="5" :sizes="[5]" @update:page="historyPage = $event; loadHistory()" />
      </section>
      <section class="bk-section"><h3 class="section-label">版本留痕</h3>
        <div v-for="row in versions" :key="row.id" class="bk-file"><div><b>版本 {{ row.versionNo }} · {{ ACTION_LABELS[row.action] }}</b><p class="soft">{{ row.operatorName || `用户 #${row.operatorId}` }} · {{ row.createTime }}</p><p v-if="row.reason" class="soft">{{ row.reason }}</p></div><Button size="sm" variant="text" @click="snapshot = snapshot?.id === row.id ? null : row">{{ snapshot?.id === row.id ? '收起变更' : '查看变更' }}</Button></div>
        <Pagination v-if="versionTotal > 5" :total="versionTotal" :page="versionPage" :page-size="5" :sizes="[5]" @update:page="versionPage = $event; loadHistory()" />
        <section v-if="snapshot" class="bk-diff" aria-label="版本变更"><h4>版本 {{ snapshot.versionNo }} 的变更</h4><div v-for="row in diffRows" :key="row.label"><b>{{ row.label }}</b><pre>{{ display(row.before) }}</pre><span class="soft">变更为</span><pre>{{ display(row.after) }}</pre></div></section>
      </section>
    </div>
    <template v-if="record && !readonly" #footer>
      <PermissionButton v-if="editable(record.status)" perm="bookkeeping:record:edit" icon="edit" @click="emit('edit', record)">编辑</PermissionButton>
      <PermissionButton v-if="editable(record.status)" perm="bookkeeping:record:submit" variant="primary" @click="emit('action', record, 'SUBMIT')">提交审核</PermissionButton>
      <PermissionButton v-if="record.status === 'PENDING_REVIEW'" perm="bookkeeping:record:review" variant="primary" @click="emit('action', record, 'APPROVE')">审核通过</PermissionButton>
      <PermissionButton v-if="record.status === 'PENDING_REVIEW'" perm="bookkeeping:record:return" @click="emit('action', record, 'RETURN')">退回</PermissionButton>
      <PermissionButton v-if="record.status === 'POSTED'" perm="bookkeeping:record:withdraw" @click="emit('action', record, 'WITHDRAW')">撤回重审</PermissionButton>
      <PermissionButton v-if="record.status === 'POSTED'" perm="bookkeeping:record:void" danger @click="emit('action', record, 'VOID')">作废</PermissionButton>
    </template>
  </Drawer>
</template>

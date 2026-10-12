<script setup lang="ts">
import { ref } from 'vue'
import { DatePicker, Field, Select, Tag, TextInput, type Column } from '@/components/ui'
import { ListPage, PermissionButton } from '@/components/business'
import RecordEditor from './RecordEditor.vue'
import RecordDetail from './RecordDetail.vue'
import ReviewActionDialog from './ReviewActionDialog.vue'
import { bookkeepingApi, type RecordRow, type OcrTaskRow, type ReviewAction } from '@/api'
import { useListPage } from '@/composables/list'
import { editable, formatAmount, RECORD_TYPES, STATUS_LABELS, STATUS_OPTIONS, statusTone } from '@/utils/bookkeeping'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { hasPermission } from '@/directives/permission'
const props = withDefaults(defineProps<{ review?: boolean }>(), { review: false })
const list = useListPage({ query: { keyword: '', recordType: '', status: '', occurredFrom: '', occurredTo: '' }, fetch: params => props.review ? bookkeepingApi.reviews.pending(params) : bookkeepingApi.records.list(params),
  exporter: hasPermission('bookkeeping:record:export') ? params => bookkeepingApi.records.export({ ...params, status: props.review ? 'PENDING_REVIEW' : params.status }) : undefined,
  exportName: props.review ? '待审核账目.xlsx' : '账目明细.xlsx',
})
const columns: Column[] = [{ key: 'summary', title: '账目', minWidth: 230 }, { key: 'recordType', title: '收支', width: 80 }, { key: 'occurredDate', title: '实际发生时间', width: 170 }, { key: 'totalAmount', title: '金额（元）', align: 'right', width: 155 }, { key: 'status', title: '状态', width: 115 }, { key: 'actions', title: '操作', width: 170, className: 'col-actions' }]
const detailId = ref<number | null>(null), revision = ref(0), editor = ref(false)
const editRecord = ref<RecordRow | null>(null), ocrTask = ref<OcrTaskRow | null>(null)
const actionRecord = ref<RecordRow | null>(null), action = ref<ReviewAction | null>(null)
const deleting = ref(false)
async function edit(row?: RecordRow, task?: OcrTaskRow) {
  try { editRecord.value = row ? await bookkeepingApi.records.get(row.id) : null; ocrTask.value = task ?? null; editor.value = true }
  catch (e) { toastError(e, '账目加载失败') }
}
async function saved(id: number) { editor.value = false; detailId.value = id; revision.value++; await list.load() }
function openAction(row: RecordRow, next: ReviewAction) { actionRecord.value = row; action.value = next }
async function acted() { action.value = null; actionRecord.value = null; revision.value++; await list.load() }
async function remove(row: RecordRow) {
  if (deleting.value || !await confirm({ title: '删除草稿', message: `确认删除「${row.summary}」？操作留痕仍保留。`, danger: true, confirmText: '删除' })) return
  deleting.value = true
  try { await bookkeepingApi.records.remove(row.id); pushToast('草稿已删除'); await list.load() }
  catch (e) { toastError(e, '删除失败') }
  finally { deleting.value = false }
}
</script>
<template>
  <ListPage :title="review ? '审核中心' : '账目列表'" :desc="review ? '核对收入、支出与票据，审核通过后正式入账。' : '按实际发生时间查看公司收支，草稿经审核后正式入账。'" :table-title="review ? '待审核账目' : '收支记录'" :list="list" :columns="columns" :min-width="900">
    <template #actions><PermissionButton v-if="!review" perm="bookkeeping:record:add" variant="primary" icon="plus" @click="edit()">新增账目</PermissionButton></template>
    <template #search>
      <Field label="关键词" wide><TextInput v-model="list.query.keyword" icon="search" placeholder="摘要 / 账目编号" clearable @enter="list.search()" /></Field>
      <Field label="收支类型"><Select v-model="list.query.recordType" :options="[{ label: '全部收支', value: '' }, ...RECORD_TYPES]" /></Field>
      <Field v-if="!review" label="状态"><Select v-model="list.query.status" :options="[{ label: '全部状态', value: '' }, ...STATUS_OPTIONS]" /></Field>
      <Field label="发生日期起"><DatePicker v-model="list.query.occurredFrom" /></Field>
      <Field label="发生日期止"><DatePicker v-model="list.query.occurredTo" /></Field>
    </template>
    <template #cell-summary="{ row }"><div><button v-if="hasPermission('bookkeeping:record:list')" type="button" class="link-btn" @click="detailId = row.id">{{ row.summary }}</button><span v-else>{{ row.summary }}</span><div class="cell-sub mono">{{ row.recordNo }}</div></div></template>
    <template #cell-recordType="{ row }"><Tag :tone="row.recordType === 'INCOME' ? 'ok' : 'neutral'">{{ row.recordType === 'INCOME' ? '收入' : '支出' }}</Tag></template>
    <template #cell-occurredDate="{ row }"><div class="tnum">{{ row.occurredDate }}<div class="cell-sub">{{ row.occurredTime || '时间未记录' }}</div></div></template>
    <template #cell-totalAmount="{ row }"><b class="mono">{{ formatAmount(row.totalAmount) }}</b></template>
    <template #cell-status="{ row }"><Tag :tone="statusTone(row.status)" dot>{{ STATUS_LABELS[row.status as keyof typeof STATUS_LABELS] }}</Tag></template>
    <template #cell-actions="{ row }"><div class="row-actions">
      <PermissionButton perm="bookkeeping:record:list" icon-only icon="eye" label="查看详情" @click="detailId = row.id" />
      <template v-if="editable(row.status) && !review"><PermissionButton perm="bookkeeping:record:edit" icon-only icon="edit" label="编辑账目" @click="edit(row)" /><PermissionButton perm="bookkeeping:record:submit" icon-only icon="check" label="提交审核" @click="openAction(row, 'SUBMIT')" /><PermissionButton perm="bookkeeping:record:edit" icon-only icon="trash" label="删除草稿" danger :disabled="deleting" @click="remove(row)" /></template>
      <template v-if="row.status === 'PENDING_REVIEW'"><PermissionButton perm="bookkeeping:record:review" variant="text" size="sm" @click="openAction(row, 'APPROVE')">通过</PermissionButton><PermissionButton perm="bookkeeping:record:return" variant="text" size="sm" @click="openAction(row, 'RETURN')">退回</PermissionButton></template>
    </div></template>
    <RecordDetail :id="detailId" :revision="revision" @close="detailId = null" @edit="edit" @action="openAction" />
    <RecordEditor :open="editor" :record="editRecord" :ocr-task="ocrTask" @close="editor = false" @saved="saved" />
    <ReviewActionDialog :record="actionRecord" :action="action" @close="action = null; actionRecord = null" @saved="acted" />
  </ListPage>
</template>

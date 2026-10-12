<script setup lang="ts">
import { computed, reactive, ref, toRaw, watch } from 'vue'
import { Button, DatePicker, TimePicker, Field, Select, Segmented, TextInput } from '@/components/ui'
import { DeptSelect, FormDialog, UserSelect } from '@/components/business'
import { bookkeepingApi, type BookkeepingOptions, type RecordRow, type RecordWriteRequest, type OcrTaskRow } from '@/api'
import { currentUser } from '@/composables/auth'
import { pushToast, toastError } from '@/composables/toast'
import { applyOcrSuggestions, RECORD_TYPES, recordPayload, sumAmounts, validateRecord } from '@/utils/bookkeeping'

const props = defineProps<{ open: boolean; record?: RecordRow | null; ocrTask?: OcrTaskRow | null }>()
const emit = defineEmits<{ close: []; saved: [id: number] }>()
const empty = (): RecordWriteRequest => ({ recordType: 'EXPENSE', summary: '', occurredDate: new Date().toLocaleDateString('en-CA'), occurredTime: '', totalAmount: '', paymentMethodId: 0, fundAccountId: 0, deptId: currentUser.value?.deptId, remark: '', details: [{ categoryId: 0, amount: '', handlerId: currentUser.value?.id }] })
const form = reactive<RecordWriteRequest>(empty())
const options = ref<BookkeepingOptions | null>(null)
const loading = ref(false)
const saving = ref(false)
const errors = ref<Record<string, string>>({})
let loadId = 0
watch(() => props.open, async open => {
  const id = ++loadId
  if (!open) return
  errors.value = {}; options.value = null
  const source = props.record ? structuredClone(toRaw(props.record)) : empty()
  // MyBatis Map 会省略 NULL 字段，切换账目时先清空可选字段；部门默认值仅用于新增。
  Object.assign(form, empty(), source, { version: props.record?.version,
    deptId: props.record ? props.record.deptId : currentUser.value?.deptId,
    occurredTime: source.occurredTime ?? '', remark: source.remark ?? '' })
  if (props.ocrTask?.normalizedResult) Object.assign(form, applyOcrSuggestions(toRaw(form), props.ocrTask.normalizedResult))
  loading.value = true
  try { const data = await bookkeepingApi.options(); if (id === loadId) options.value = data }
  catch (error) { if (id === loadId) toastError(error, '录入选项加载失败') }
  finally { if (id === loadId) loading.value = false }
}, { immediate: true })
const categories = computed(() => (options.value?.categories ?? []).filter(row => row.categoryType === form.recordType).map(row => ({ label: row.categoryName, value: row.id })))
const choices = (key: 'paymentMethods' | 'fundAccounts' | 'projects', name: string) => (options.value?.[key] ?? []).map(row => ({ label: String(row[name]), value: row.id }))
const total = computed(() => sumAmounts(form.details))
function changeType(value: string | number) { form.recordType = value as RecordWriteRequest['recordType']; form.details.forEach(row => row.categoryId = 0) }
async function save() {
  if (saving.value || loading.value || !options.value) return
  errors.value = validateRecord(form)
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    const payload = recordPayload(toRaw(form))
    let id = props.record?.id
    if (props.ocrTask) await bookkeepingApi.ocr.confirm(props.ocrTask.id, payload)
    else if (id) await bookkeepingApi.records.update(id, payload)
    else id = (await bookkeepingApi.records.create(payload)).id
    pushToast(props.ocrTask ? '人工核对结果已保存，仍需提交审核' : '账目草稿已保存')
    emit('saved', id!)
  } catch (error) { toastError(error, '账目保存失败') }
  finally { saving.value = false }
}
</script>

<template>
  <FormDialog :open="open" :title="ocrTask ? '核对 OCR 识别结果' : record ? '编辑账目' : '新增账目'" sub="保存为草稿，核对完成后再提交审核。" :width="900" :saving="saving || loading" submit-text="保存草稿" @close="!saving && emit('close')" @submit="save">
    <p v-if="loading" class="soft span-2">正在加载录入选项…</p>
    <Field label="收支类型" group><Segmented :model-value="form.recordType" :options="RECORD_TYPES" @update:model-value="changeType" /></Field>
    <Field label="所属部门"><DeptSelect v-model="form.deptId" :data="options?.depts ?? []" clearable /></Field>
    <Field label="摘要" required span :error="errors.summary"><TextInput v-model="form.summary" placeholder="例如：采购办公用品" :maxlength="500" /></Field>
    <Field label="实际发生日期" required :error="errors.occurredDate"><DatePicker v-model="form.occurredDate" :invalid="!!errors.occurredDate" /></Field>
    <Field label="实际发生时间" :error="errors.occurredTime" hint="时间不确定时留空，只记录日期。"><TimePicker v-model="form.occurredTime" :invalid="!!errors.occurredTime" /></Field>
    <Field label="支付方式" required :error="errors.paymentMethodId"><Select v-model="form.paymentMethodId" :options="choices('paymentMethods', 'methodName')" filterable /></Field>
    <Field label="资金账户" required :error="errors.fundAccountId"><Select v-model="form.fundAccountId" :options="choices('fundAccounts', 'accountName')" filterable /></Field>
    <Field label="总金额（元）" required :error="errors.totalAmount"><TextInput v-model="form.totalAmount" inputmode="decimal" placeholder="0.00" /></Field>
    <div class="bk-total"><span class="soft">明细合计</span><b class="mono">¥ {{ total }}</b><Button size="sm" variant="text" @click="form.totalAmount = total">使用合计</Button></div>
    <section class="span-2 bk-details" aria-label="账目明细">
      <div class="drawer-toolbar"><h3 class="section-label">账目明细</h3><Button icon="plus" size="sm" :disabled="form.details.length >= 100" @click="form.details.push({ categoryId: 0, amount: '', handlerId: currentUser?.id })">添加明细</Button></div>
      <p v-if="errors.details" class="field-error" role="alert">{{ errors.details }}</p>
      <div v-for="(row, index) in form.details" :key="index" class="bk-detail-row">
        <div class="drawer-toolbar"><b>明细 {{ index + 1 }}</b><Button size="sm" variant="text" danger :disabled="form.details.length <= 1" @click="form.details.splice(index, 1)">移除</Button></div>
        <div class="form-grid">
          <Field label="分类" required><Select v-model="row.categoryId" :options="categories" filterable /></Field>
          <Field label="金额（元）" required><TextInput v-model="row.amount" inputmode="decimal" placeholder="0.00" /></Field>
          <Field label="经办人"><UserSelect v-model="row.handlerId" :data="options?.users ?? []" /></Field>
          <Field label="归属部门"><DeptSelect v-model="row.deptId" :data="options?.depts ?? []" clearable /></Field>
          <Field label="项目"><Select v-model="row.projectId" :options="choices('projects', 'projectName')" clearable filterable /></Field>
          <Field label="明细备注"><TextInput v-model="row.remark" :maxlength="500" /></Field>
        </div>
      </div>
      <p class="field-hint">每条明细至少选择经办人、部门或项目中的一项。</p>
    </section>
    <Field label="备注" span><TextInput v-model="form.remark" multiline :rows="2" :maxlength="1000" /></Field>
    <Field v-if="ocrTask?.normalizedResult?.text" label="识别原文" span><TextInput :model-value="ocrTask.normalizedResult.text" multiline :rows="4" readonly /></Field>
    <p v-if="!loading && !options" class="field-error span-2">录入选项未加载，请关闭后重试。</p>
  </FormDialog>
</template>

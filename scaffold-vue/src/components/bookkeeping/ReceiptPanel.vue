<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button, EmptyState, Field, Pagination, Select, Tag } from '@/components/ui'
import { PermissionButton } from '@/components/business'
import { bookkeepingApi, type RecordRow, type AttachmentRow, type OcrTaskRow, type OcrDocumentType } from '@/api'
import { hasPermission } from '@/directives/permission'
import { editable } from '@/utils/bookkeeping'
import { downloadBlob } from '@/utils/download'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
const props = defineProps<{ record: RecordRow }>()
const emit = defineEmits<{ confirmOcr: [task: OcrTaskRow] }>()
const attachments = ref<AttachmentRow[]>([]), tasks = ref<OcrTaskRow[]>([])
const busy = ref(false), loading = ref(false), failed = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const preview = ref(''), previewName = ref('')
const type = ref<OcrDocumentType>('Invoice'), page = ref(1), total = ref(0)
const canRead = computed(() => hasPermission('bookkeeping:attachment:view'))
const canOcr = computed(() => canRead.value && hasPermission('bookkeeping:ocr:recognize'))
let requestId = 0, previewId = 0
function clearPreview() { previewId++; if (preview.value) URL.revokeObjectURL(preview.value); preview.value = ''; previewName.value = '' }
async function load() {
  const id = ++requestId; loading.value = true; failed.value = false
  try {
    const files = canRead.value ? await bookkeepingApi.attachments.list(props.record.id) : []
    const history = canOcr.value ? await bookkeepingApi.ocr.tasks(props.record.id, { pageNum: page.value, pageSize: 5 }) : null
    if (id !== requestId) return
    attachments.value = files; tasks.value = history?.rows ?? []; total.value = history?.total ?? 0
  } catch (e) { if (id === requestId) { failed.value = true; toastError(e, '票据加载失败') } }
  finally { if (id === requestId) loading.value = false }
}
watch(() => props.record.id, () => { clearPreview(); page.value = 1; attachments.value = []; tasks.value = []; load() }, { immediate: true })
onBeforeUnmount(() => { requestId++; clearPreview() })
async function run(action: () => Promise<unknown>, message: string) {
  if (busy.value) return
  busy.value = true
  try { await action(); if (message) pushToast(message); await load() }
  catch (e) { toastError(e, '票据操作失败') }
  finally { busy.value = false }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; input.value = ''
  if (!file) return
  if (file.size > 10 * 1024 * 1024 || !['image/png', 'image/jpeg', 'application/pdf'].includes(file.type)) { pushToast('请选择10MB以内的 PNG、JPEG 或 PDF 文件', 'danger'); return }
  await run(() => bookkeepingApi.attachments.upload(props.record.id, file, attachments.value.length === 0), '原始票据已保存')
}
async function view(file: AttachmentRow) {
  clearPreview(); const id = previewId
  try {
    const blob = await bookkeepingApi.attachments.download(file.id)
    if (id !== previewId) return
    if (file.mimeType === 'application/pdf') downloadBlob(blob, file.originalName)
    else { preview.value = URL.createObjectURL(blob); previewName.value = file.originalName }
  } catch (e) { toastError(e, '票据读取失败') }
}
async function remove(file: AttachmentRow) {
  if (await confirm({ title: '移除票据', message: `确认移除「${file.originalName}」吗？历史记录仍保留。`, danger: true, confirmText: '移除' })) await run(() => bookkeepingApi.attachments.remove(file.id), '票据已移除')
}
async function recognize(file: AttachmentRow) {
  await run(async () => {
    const task = await bookkeepingApi.ocr.start(props.record.id, { attachmentId: file.id, version: props.record.version, documentType: type.value })
    const result = await bookkeepingApi.ocr.task(task.id)
    pushToast(result.status === 'FAILED' ? (result.errorMessage ?? '识别失败，可继续人工录入') : '识别完成，请人工核对', result.status === 'FAILED' ? 'danger' : 'ok')
  }, '')
}
</script>
<template>
  <section class="bk-section" aria-label="票据与 OCR">
    <div class="drawer-toolbar"><h3 class="section-label">原始票据</h3><PermissionButton v-if="editable(record.status)" perm="bookkeeping:attachment:upload" size="sm" icon="upload" :loading="busy" @click="fileInput?.click()">上传票据</PermissionButton></div>
    <input ref="fileInput" hidden type="file" accept="image/png,image/jpeg,application/pdf" aria-label="上传票据文件" @change="upload" />
    <p v-if="!canRead" class="soft">当前账号没有票据查看权限。</p>
    <p v-else-if="loading" class="soft">正在加载票据…</p>
    <p v-else-if="failed" class="field-error">加载失败。<Button size="sm" variant="text" @click="load">重试</Button></p>
    <EmptyState v-else-if="!attachments.length" title="暂无票据" desc="保存上传原图，识别失败也可以继续手工记账。" />
    <div v-for="file in attachments" :key="file.id" class="bk-file">
      <div><button type="button" class="link-btn" @click="view(file)">{{ file.originalName }}</button><p class="faint">{{ (file.fileSize / 1024).toFixed(1) }} KB · {{ file.createTime }}</p></div>
      <Tag v-if="file.isPrimary === 1" tone="info">主票据</Tag>
      <div class="row-actions">
        <PermissionButton v-if="editable(record.status) && !file.isPrimary" perm="bookkeeping:attachment:upload" variant="text" size="sm" :disabled="busy" @click="run(() => bookkeepingApi.attachments.setPrimary(file.id), '主票据已更新')">设为主票据</PermissionButton>
        <PermissionButton v-if="editable(record.status) && canRead" perm="bookkeeping:ocr:recognize" variant="text" size="sm" :disabled="busy" @click="recognize(file)">识别</PermissionButton>
        <PermissionButton v-if="editable(record.status)" perm="bookkeeping:attachment:remove" variant="text" size="sm" danger :disabled="busy" @click="remove(file)">移除</PermissionButton>
      </div>
    </div>
    <figure v-if="preview" class="bk-preview"><img :src="preview" :alt="previewName" /><figcaption class="drawer-toolbar"><span class="soft">{{ previewName }}</span><Button size="sm" variant="text" @click="clearPreview">关闭预览</Button></figcaption></figure>
    <template v-if="canOcr">
      <Field v-if="editable(record.status)" label="识别类型"><Select v-model="type" :disabled="busy" :options="[{ label: '增值税发票', value: 'Invoice' }, { label: '购物小票', value: 'ShoppingReceipt' }, { label: '支付详情截图', value: 'PaymentRecord' }, { label: '通用文字', value: 'Advanced' }]" /></Field>
      <h3 class="section-label bk-section">识别记录</h3>
      <p v-if="!tasks.length" class="soft">暂无识别记录。</p>
      <article v-for="task in tasks" :key="task.id" class="bk-file">
        <div><b>{{ task.status === 'CONFIRMED' ? '已人工确认' : task.status === 'SUCCEEDED' ? '待人工核对' : '识别失败' }}</b><p class="soft">{{ task.errorMessage || task.normalizedResult?.summary || '识别结果仅作建议' }}</p><span class="faint">{{ task.createTime }}</span></div>
        <PermissionButton v-if="task.status === 'SUCCEEDED' && editable(record.status)" perm="bookkeeping:record:edit" size="sm" :disabled="busy" @click="emit('confirmOcr', task)">核对并填写</PermissionButton>
      </article>
      <Pagination v-if="total > 5" :total="total" :page="page" :page-size="5" :sizes="[5]" @update:page="page = $event; load()" />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Field, TextInput } from '@/components/ui'
import { FormDialog } from '@/components/business'
import { bookkeepingApi, type RecordRow, type ReviewAction } from '@/api'
import { ACTION_LABELS } from '@/utils/bookkeeping'
import { pushToast, toastError } from '@/composables/toast'
const props = defineProps<{ record: RecordRow | null; action: ReviewAction | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const reason = ref(''), error = ref(''), saving = ref(false)
const requiredReason = computed(() => ['RETURN', 'WITHDRAW', 'VOID'].includes(props.action ?? ''))
watch(() => props.action, () => { reason.value = ''; error.value = '' })
async function save() {
  if (!props.record || !props.action || saving.value) return
  if (requiredReason.value && !reason.value.trim()) { error.value = '请填写操作原因'; return }
  saving.value = true
  try {
    const handlers = { SUBMIT: bookkeepingApi.reviews.submit, APPROVE: bookkeepingApi.reviews.approve, RETURN: bookkeepingApi.reviews.returnRecord, WITHDRAW: bookkeepingApi.reviews.withdraw, VOID: bookkeepingApi.reviews.voidRecord }
    await handlers[props.action](props.record.id, { version: props.record.version, reason: reason.value.trim() || undefined })
    pushToast(`${ACTION_LABELS[props.action]}成功`); emit('saved')
  } catch (e) { toastError(e, '操作失败，请核对账目当前状态') }
  finally { saving.value = false }
}
</script>
<template>
  <FormDialog :open="Boolean(record && action)" :title="ACTION_LABELS[action ?? ''] ?? ''" :sub="record?.summary" :saving="saving" :submit-text="ACTION_LABELS[action ?? '']" @close="!saving && emit('close')" @submit="save">
    <p class="span-2 soft">{{ action === 'APPROVE' ? '审核通过后正式入账，并保存本次审核记录。' : action === 'VOID' ? '作废后不能恢复；账目、票据和操作历史会保留。' : action === 'WITHDRAW' ? '撤回后可以修改，修改完成须重新提交审核。' : action === 'SUBMIT' ? '提交后账目和票据将锁定，等待审核。' : '退回后由录入人员修订，再重新提交审核。' }}</p>
    <Field label="操作原因" :required="requiredReason" span :error="error"><TextInput v-model="reason" multiline :rows="3" :maxlength="1000" :placeholder="requiredReason ? '请说明原因' : '可填写说明'" /></Field>
  </FormDialog>
</template>

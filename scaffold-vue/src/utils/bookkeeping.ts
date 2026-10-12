import type { RecordWriteRequest, OcrNormalizedResult, RecordStatus } from '../api/bookkeeping.ts'

export const RECORD_TYPES = [{ label: '支出', value: 'EXPENSE' }, { label: '收入', value: 'INCOME' }]
export const STATUS_LABELS: Record<RecordStatus, string> = { DRAFT: '草稿', PENDING_REVIEW: '待审核', RETURNED: '已退回', POSTED: '已入账', WITHDRAWN: '撤回重审', VOIDED: '已作废' }
export const STATUS_OPTIONS = Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))
export const ACTION_LABELS: Record<string, string> = { CREATE: '新增草稿', UPDATE: '修改草稿', DELETE: '删除草稿', SUBMIT: '提交审核', APPROVE: '审核通过', RETURN: '退回', WITHDRAW: '撤回重审', VOID: '作废', OCR_CONFIRM: 'OCR 人工确认' }
export const editable = (status: RecordStatus) => ['DRAFT', 'RETURNED', 'WITHDRAWN'].includes(status)
export const statusTone = (status: RecordStatus) => status === 'POSTED' ? 'ok' : status === 'PENDING_REVIEW' ? 'info' : ['RETURNED', 'VOIDED'].includes(status) ? 'danger' : 'neutral'

/** 金额以十进制字符串和整数分处理，避免超过 Number 精度或 0.1+0.2 误差。 */
export function moneyCents(value: string | number | null | undefined): bigint | null {
  const text = String(value ?? '').trim()
  if (!/^\d{1,16}(\.\d{1,2})?$/.test(text)) return null
  const [whole, fraction = ''] = text.split('.')
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
  return cents > 0n && cents <= 999999999999999999n ? cents : null
}
const decimal = (cents: bigint) => `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
export function sumAmounts(rows: Array<{ amount: string | number }>) {
  return decimal(rows.reduce((sum, row) => sum + (moneyCents(row.amount) ?? 0n), 0n))
}
export function formatAmount(value: string | number | undefined) {
  const cents = moneyCents(value)
  if (cents === null) return value === 0 || value === '0.00' ? '0.00' : '—'
  const [whole, part] = decimal(cents).split('.')
  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${part}`
}
export function validateRecord(form: RecordWriteRequest): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!form.summary.trim() || form.summary.length > 500) errors.summary = '请输入500字以内的摘要'
  const date = new Date(`${form.occurredDate}T00:00:00Z`)
  if (!/^\d{4}-\d{2}-\d{2}$/.test(form.occurredDate) || isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== form.occurredDate) errors.occurredDate = '请选择有效的实际发生日期'
  if (form.occurredTime && !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(form.occurredTime)) errors.occurredTime = '请输入有效时间'
  if (!form.paymentMethodId) errors.paymentMethodId = '请选择支付方式'
  if (!form.fundAccountId) errors.fundAccountId = '请选择资金账户'
  const amount = moneyCents(form.totalAmount)
  if (amount === null) errors.totalAmount = '金额须大于0，最多16位整数、2位小数'
  else if (String(sumAmounts(form.details)) !== decimal(amount)) errors.totalAmount = '总金额必须等于明细合计'
  if (!form.details.length || form.details.length > 100 || form.details.some(row => !row.categoryId || moneyCents(row.amount) === null || !(row.handlerId || row.deptId || row.projectId))) errors.details = '每条明细需选择分类、填写有效金额，并选择经办人、部门或项目中的至少一项'
  return errors
}
export function recordPayload(form: RecordWriteRequest): RecordWriteRequest {
  return {
    recordType: form.recordType, summary: form.summary.trim(), occurredDate: form.occurredDate,
    totalAmount: decimal(moneyCents(form.totalAmount)!),
    occurredTime: form.occurredTime ? (form.occurredTime.length === 5 ? `${form.occurredTime}:00` : form.occurredTime) : undefined,
    paymentMethodId: form.paymentMethodId, fundAccountId: form.fundAccountId,
    deptId: form.deptId || undefined, remark: form.remark, version: form.version,
    details: form.details.map((row, sort) => ({
      categoryId: row.categoryId, amount: decimal(moneyCents(row.amount)!),
      handlerId: row.handlerId || undefined, deptId: row.deptId || undefined,
      projectId: row.projectId || undefined, remark: row.remark, sort,
    })),
  }
}
export function applyOcrSuggestions(form: RecordWriteRequest, suggestions: OcrNormalizedResult): RecordWriteRequest {
  const next = structuredClone(form)
  for (const key of ['summary', 'totalAmount', 'occurredDate', 'occurredTime'] as const) if (suggestions[key]) next[key] = suggestions[key]!
  return next
}

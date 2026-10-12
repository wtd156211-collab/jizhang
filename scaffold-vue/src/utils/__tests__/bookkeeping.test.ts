import test from 'node:test'
import assert from 'node:assert/strict'
import { moneyCents, sumAmounts, validateRecord, recordPayload, applyOcrSuggestions } from '../bookkeeping.ts'

const draft = () => ({ recordType: 'EXPENSE' as const, summary: '办公采购', occurredDate: '2026-10-12', occurredTime: '', totalAmount: '0.30', paymentMethodId: 1, fundAccountId: 1, details: [{ categoryId: 3, amount: '0.10', projectId: 1 }, { categoryId: 3, amount: '0.20', deptId: 5 }] })
test('money arithmetic keeps cents exact and rejects fractions and overflow', () => {
  assert.equal(sumAmounts(draft().details), '0.30')
  assert.equal(moneyCents('9999999999999999.99'), 999999999999999999n)
  for (const invalid of ['0', '-1', '1.001', '1e2', '10000000000000000', 'NaN', '']) assert.equal(moneyCents(invalid), null)
})
test('record validation rejects mismatched totals, invalid dates and missing detail attribution', () => {
  assert.deepEqual(validateRecord(draft()), {})
  assert.ok(validateRecord({ ...draft(), totalAmount: '0.31' }).totalAmount)
  assert.ok(validateRecord({ ...draft(), occurredDate: '2026-02-30' }).occurredDate)
  assert.ok(validateRecord({ ...draft(), details: [{ categoryId: 3, amount: '0.30' }] }).details)
})
test('draft serialization preserves missing time and decimal strings with current version', () => {
  const payload = recordPayload({ ...draft(), version: 7 })
  assert.equal(payload.version, 7)
  assert.equal(payload.totalAmount, '0.30')
  assert.equal(payload.occurredTime, undefined)
  assert.equal(recordPayload({ ...draft(), occurredTime: '09:08' }).occurredTime, '09:08:00')
})
test('draft serialization excludes read metadata from records and detail rows', () => {
  const source = { ...draft(), id: 99, status: 'POSTED', recordNo: 'REC99', createdBy: 1,
    details: [{ ...draft().details[0], id: 3, recordId: 99, categoryName: '办公费用', createTime: '2026-10-12' }] }
  const payload = recordPayload(source)
  for (const field of ['id', 'status', 'recordNo', 'createdBy']) assert.equal(Object.hasOwn(payload, field), false)
  for (const field of ['id', 'recordId', 'categoryName', 'createTime']) assert.equal(Object.hasOwn(payload.details[0], field), false)
})
test('OCR applies suggestions to an editable copy and leaves detail totals for human verification', () => {
  const source = draft()
  const next = applyOcrSuggestions(source, { summary: 'OCR 建议', totalAmount: '12.00', sourceVersion: 0, documentType: 'Invoice' })
  assert.equal(next.summary, 'OCR 建议')
  assert.equal(source.summary, '办公采购')
  assert.equal(next.occurredTime, '')
  assert.equal(sumAmounts(next.details), '0.30')
  assert.ok(validateRecord(next).totalAmount)
})

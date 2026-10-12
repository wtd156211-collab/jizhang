import test from 'node:test'
import assert from 'node:assert/strict'
import { displayMoney, moneyRatio, moneyChange, dateRange, monthDays, validDate, shiftDate, validTime } from '../finance.ts'

test('totals keep negative and more than 16 digits exact', () => {
  assert.equal(displayMoney('19999999999999999.98'), '19,999,999,999,999,999.98')
  assert.equal(displayMoney('-1234.50'), '-1,234.50')
  assert.equal(displayMoney('0.00'), '0.00')
  assert.equal(moneyRatio('0.10', '0.30'), 33.33)
  assert.deepEqual(moneyChange('0.30', '0.20'), { value: 50, fresh: false })
  assert.deepEqual(moneyChange('0.30', '0.00'), { value: 0, fresh: true })
})
test('calendar validates leap days and local dates without UTC offsets', () => {
  assert.equal(validDate('2024-02-29'), true)
  assert.equal(validDate('2026-02-29'), false)
  assert.equal(validDate('2026-13-01'), false)
  assert.equal(shiftDate('2026-01-01', -1), '2025-12-31')
  assert.equal(monthDays('2024-02').filter(Boolean).length, 29)
  assert.deepEqual(dateRange(7, new Date(2026, 0, 1)), { occurredFrom: '2025-12-26', occurredTo: '2026-01-01' })
})
test('optional time retains unknown, validates hour minute second', () => {
  assert.equal(validTime(''), true)
  assert.equal(validTime('23:59:59'), true)
  assert.equal(validTime('24:00:00'), false)
  assert.equal(validTime('12:60:00'), false)
})

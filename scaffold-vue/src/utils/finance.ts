/** 汇总金额不受单条账目上限限制。金额运算使用整数分，Number 仅用于比例及图形坐标。 */
export function totalCents(value: string): bigint {
  if (!/^-?\d+(\.\d{1,2})?$/.test(value)) return 0n
  const negative = value.startsWith('-'), [whole, part = ''] = value.replace('-', '').split('.')
  const cents = BigInt(whole) * 100n + BigInt(part.padEnd(2, '0'))
  return negative ? -cents : cents
}
export function displayMoney(value?: string | null): string {
  if (value === null || value === undefined) return '—'
  const cents = totalCents(value), abs = cents < 0n ? -cents : cents
  return `${cents < 0n ? '-' : ''}${String(abs / 100n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${String(abs % 100n).padStart(2, '0')}`
}
export function moneyRatio(value: string, total: string) {
  const denominator = totalCents(total)
  return denominator > 0n ? Number(totalCents(value) * 10000n / denominator) / 100 : 0
}
export function moneyChange(value: string, previous: string) {
  const a = totalCents(value), b = totalCents(previous)
  return { value: b === 0n ? 0 : Number((a - b) * 1000n / (b < 0n ? -b : b)) / 10, fresh: b === 0n && a !== 0n }
}
export function localDate(date: Date) {
  return `${String(date.getFullYear()).padStart(4, '0')}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
export function validDate(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  if (year < 1000 || year > 9998) return false
  const date = new Date(year, month - 1, day, 12)
  return localDate(date) === value
}
export function shiftDate(value: string, days: number) {
  const [year, month, day] = value.split('-').map(Number)
  return localDate(new Date(year, month - 1, day + days, 12))
}
export function dateRange(days: number, now = new Date()) {
  const occurredTo = localDate(now)
  return { occurredFrom: shiftDate(occurredTo, 1 - days), occurredTo }
}
/** 周一作为月历首列，空字符串表示月初前占位格。 */
export function monthDays(month: string) {
  const [year, index] = month.split('-').map(Number)
  const first = new Date(year, index - 1, 1, 12), count = new Date(year, index, 0, 12).getDate()
  return [...Array.from({ length: (first.getDay() + 6) % 7 }, () => ''), ...Array.from({ length: count }, (_, i) => `${month}-${String(i + 1).padStart(2, '0')}`)]
}
export function validTime(value: string) { return value === '' || /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(value) }

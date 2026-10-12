import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../http.ts'
import { createBookkeepingApi } from '../bookkeeping.ts'

test('financial API uses occurrence filters and protected Excel downloads', async () => {
  const calls: Array<{ url: string; auth: unknown }> = []
  const api = createBookkeepingApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'finance-token', fetcher: async (url,init) => {
    calls.push({ url: String(url), auth: (init?.headers as Record<string,string>).Authorization })
    return String(url).includes('/export') ? new Response('PK-test') : new Response(JSON.stringify({ code: 200, data: {} }))
  } }))
  const query = { occurredFrom: '2026-10-01', occurredTo: '2026-10-12', recordType: 'EXPENSE' }
  await api.dashboard(query); await api.reports.summary(query); await api.reports.daily(query); await api.reports.categories(query)
  assert.equal(await (await api.reports.export(query)).text(), 'PK-test')
  await api.records.export({ ...query, status: 'POSTED', keyword: '办公' })
  assert.deepEqual(calls.slice(0,5).map(call => call.url.split('?')[0]), ['/api/bookkeeping/dashboard/summary','/api/bookkeeping/reports/summary','/api/bookkeeping/reports/daily','/api/bookkeeping/reports/category','/api/bookkeeping/reports/export'])
  assert.match(calls[5].url, /status=POSTED/)
  assert.ok(calls.every(call => call.auth === 'Bearer finance-token'))
  assert.ok(calls.every(call => call.url.includes('occurredFrom=2026-10-01&occurredTo=2026-10-12')))
})

test('bookkeeping config api maps list and CRUD endpoints', async () => {
  const calls: Array<{ url: string; method: string; body?: string }> = []
  const fetcher = async (url: string | URL | Request, init?: RequestInit) => {
    calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body as string | undefined })
    return new Response(JSON.stringify({ code: 200, message: 'success', data: { id: 9, rows: [], total: 0 }, traceId: 't' }), {
      status: 200,
      headers: { 'content-type': 'application/json' },
    })
  }
  const api = createBookkeepingApi(createHttpClient({ baseUrl: '/api', tokenProvider: () => 'token', fetcher }))

  await api.config.categories.list({ pageNum: 2, pageSize: 20, categoryType: 'EXPENSE', status: 1 })
  await api.config.categories.create({ categoryType: 'EXPENSE', categoryCode: 'OFFICE', categoryName: '办公费用', parentId: 0, sort: 1, status: 1 })
  await api.config.categories.update(9, { categoryType: 'EXPENSE', categoryCode: 'OFFICE', categoryName: '办公费用', parentId: 0, sort: 1, status: 0 })
  await api.config.categories.remove(9)
  await api.config.paymentMethods.list({ pageNum: 1, pageSize: 10 })
  await api.config.fundAccounts.list({ pageNum: 1, pageSize: 10 })
  await api.config.projects.list({ pageNum: 1, pageSize: 10 })
  await api.records.list({ pageNum: 1, pageSize: 10, recordType: 'EXPENSE', occurredFrom: '2026-10-01' })
  await api.records.get(9)

  assert.equal(calls[0].url, '/api/bookkeeping/config/categories?pageNum=2&pageSize=20&categoryType=EXPENSE&status=1')
  assert.equal(calls[1].method, 'POST')
  assert.equal(calls[1].url, '/api/bookkeeping/config/categories')
  assert.equal(calls[2].method, 'PUT')
  assert.equal(calls[3].method, 'DELETE')
  assert.deepEqual(calls.slice(4).map(call => call.url), [
    '/api/bookkeeping/config/payment-methods?pageNum=1&pageSize=10',
    '/api/bookkeeping/config/fund-accounts?pageNum=1&pageSize=10',
    '/api/bookkeeping/config/projects?pageNum=1&pageSize=10',
    '/api/bookkeeping/records?pageNum=1&pageSize=10&recordType=EXPENSE&occurredFrom=2026-10-01',
    '/api/bookkeeping/records/9',
  ])
})

test('attachment api sends the original receipt and uses protected download', async () => {
  const calls: Array<{ url: string; init: RequestInit }> = []
  const api = createBookkeepingApi(createHttpClient({
    baseUrl: '/api', tokenProvider: () => 'receipt-session',
    fetcher: async (url, init) => {
      calls.push({ url: String(url), init: init ?? {} })
      if (String(url).endsWith('/download')) return new Response('%PDF-original')
      return new Response(JSON.stringify({ code: 200, data: { id: 12 } }))
    },
  }))
  const file = new File(['%PDF-original'], '票据.pdf', { type: 'application/pdf' })

  await api.attachments.upload(9, file, true)
  await api.attachments.list(9)
  const downloaded = await api.attachments.download(12)
  await api.attachments.setPrimary(12)
  await api.attachments.remove(12)

  assert.deepEqual(calls.map(call => [call.url, call.init.method]), [
    ['/api/bookkeeping/records/9/attachments', 'POST'],
    ['/api/bookkeeping/records/9/attachments', 'GET'],
    ['/api/bookkeeping/attachments/12/download', 'GET'],
    ['/api/bookkeeping/attachments/12/primary', 'PUT'],
    ['/api/bookkeeping/attachments/12', 'DELETE'],
  ])
  const form = calls[0].init.body as FormData
  assert.equal(await (form.get('file') as File).text(), '%PDF-original')
  assert.equal((form.get('file') as File).name, '票据.pdf')
  assert.equal(form.get('primary'), 'true')
  assert.equal(await downloaded.text(), '%PDF-original')
  assert.equal(new Headers(calls[2].init.headers).get('authorization'), 'Bearer receipt-session')
})

test('review actions send version and reason and histories use paged endpoints', async () => {
  const calls: Array<{ url: string; method: string; body?: unknown }> = []
  const api = createBookkeepingApi(createHttpClient({
    baseUrl: '/api', tokenProvider: () => 'review-session',
    fetcher: async (url, init) => {
      calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined })
      return new Response(JSON.stringify({ code: 200, data: { rows: [], total: 0 } }))
    },
  }))
  await api.reviews.pending({ pageNum: 1, pageSize: 20, occurredFrom: '2026-10-01' })
  await api.reviews.submit(9, { version: 2 })
  await api.reviews.approve(9, { version: 3 })
  await api.reviews.returnRecord(9, { version: 3, reason: '补充票据' })
  await api.reviews.withdraw(9, { version: 4, reason: '修订金额' })
  await api.reviews.voidRecord(9, { version: 4, reason: '重复记账' })
  await api.reviews.history(9, { pageNum: 2, pageSize: 20 })
  await api.reviews.versions(9, { pageNum: 1, pageSize: 10 })
  assert.deepEqual(calls, [
    { url: '/api/bookkeeping/reviews/pending?pageNum=1&pageSize=20&occurredFrom=2026-10-01', method: 'GET', body: undefined },
    { url: '/api/bookkeeping/records/9/submit', method: 'POST', body: { version: 2 } },
    { url: '/api/bookkeeping/records/9/review/approve', method: 'POST', body: { version: 3 } },
    { url: '/api/bookkeeping/records/9/review/return', method: 'POST', body: { version: 3, reason: '补充票据' } },
    { url: '/api/bookkeeping/records/9/withdraw', method: 'POST', body: { version: 4, reason: '修订金额' } },
    { url: '/api/bookkeeping/records/9/void', method: 'POST', body: { version: 4, reason: '重复记账' } },
    { url: '/api/bookkeeping/records/9/reviews?pageNum=2&pageSize=20', method: 'GET', body: undefined },
    { url: '/api/bookkeeping/records/9/versions?pageNum=1&pageSize=10', method: 'GET', body: undefined },
  ])
})

test('ocr uses saved attachment, editable confirmation and write-only cloud credentials', async () => {
  const calls: Array<{ url: string; method: string; body?: unknown }> = []
  const api = createBookkeepingApi(createHttpClient({
    baseUrl: '/api', tokenProvider: () => 'ocr-session',
    fetcher: async (url, init) => {
      calls.push({ url: String(url), method: init?.method ?? 'GET', body: init?.body ? JSON.parse(String(init.body)) : undefined })
      return new Response(JSON.stringify({ code: 200, data: { id: 7 } }))
    },
  }))
  await api.ocr.start(9, { attachmentId: 12, version: 2, documentType: 'Invoice' })
  await api.ocr.task(7)
  await api.ocr.tasks(9, { pageNum: 1, pageSize: 10 })
  await api.ocr.configStatus()
  await api.ocr.saveConfig({ provider: 'aliyun', accessKeyId: 'test-access-id', apiKey: 'test-secret' })
  const draft = { recordType: 'EXPENSE' as const, summary: '人工修正', occurredDate: '2026-10-12', totalAmount: '100.00',
    paymentMethodId: 1, fundAccountId: 1, version: 2, details: [{ categoryId: 3, amount: '100.00', deptId: 5 }] }
  await api.ocr.confirm(7, draft)
  assert.deepEqual(calls, [
    { url: '/api/bookkeeping/records/9/ocr', method: 'POST', body: { attachmentId: 12, version: 2, documentType: 'Invoice' } },
    { url: '/api/bookkeeping/ocr/tasks/7', method: 'GET', body: undefined },
    { url: '/api/bookkeeping/records/9/ocr/tasks?pageNum=1&pageSize=10', method: 'GET', body: undefined },
    { url: '/api/bookkeeping/ocr/config/status', method: 'GET', body: undefined },
    { url: '/api/bookkeeping/ocr/config', method: 'PUT', body: { provider: 'aliyun', accessKeyId: 'test-access-id', apiKey: 'test-secret' } },
    { url: '/api/bookkeeping/ocr/tasks/7/confirm', method: 'POST', body: draft },
  ])
})

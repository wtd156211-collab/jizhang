// 独立浏览器回归：真实 Vue 组件，模拟 HTTP 合同；不连接开发数据库、不调用收费 OCR。
const { chromium } = require(process.env.BOOKKEEPING_PLAYWRIGHT_PATH || 'playwright')
const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const origin = process.env.BOOKKEEPING_WEB_URL || 'http://127.0.0.1:5176'
const artifacts = path.resolve(__dirname, '../output/playwright')
fs.mkdirSync(artifacts, { recursive: true })

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, reducedMotion: 'reduce' })
  const user = { id: 1, username: 'admin', nickname: '系统管理员', deptId: 5, roles: ['super_admin'], permissions: ['*'] }
  const records = [{ id: 1, recordNo: 'REC202610120001', recordType: 'EXPENSE', status: 'DRAFT', summary: '办公用品采购', occurredDate: '2026-10-12', occurredTime: null, totalAmount: '320.50', paymentMethodId: 1, fundAccountId: 1, deptId: 5, version: 0, details: [{ id: 1, categoryId: 3, categoryName: '办公费用', amount: '320.50', handlerId: 1, sort: 0 }] }, { id: 2, recordNo: 'REC202610110002', recordType: 'INCOME', status: 'PENDING_REVIEW', summary: '客户项目服务收入', occurredDate: '2026-10-11', occurredTime: '15:42:00', totalAmount: '6800.00', paymentMethodId: 1, fundAccountId: 1, version: 1, details: [{ id: 2, categoryId: 1, categoryName: '服务收入', amount: '6800.00', projectId: 1 }] }]
  const categories = [{ id: 1, categoryType: 'INCOME', categoryName: '服务收入', categoryCode: 'SERVICE', status: 1, sort: 0 }, { id: 3, categoryType: 'EXPENSE', categoryName: '办公费用', categoryCode: 'OFFICE', status: 1, sort: 0 }]
  const config = { categories, paymentMethods: [{ id: 1, methodName: '银行转账', methodCode: 'BANK', status: 1, sort: 1 }], fundAccounts: [{ id: 1, accountName: '公司基本账户', accountCode: 'MAIN_BANK', accountType: 'BANK', status: 1, sort: 1 }], projects: [{ id: 1, projectName: '日常经营', projectCode: 'GENERAL', status: 1, sort: 1 }] }
  const files = [{ id: 1, recordId: 1, originalName: '办公采购票据.png', mimeType: 'image/png', fileSize: 18432, isPrimary: 1, createTime: '2026-10-12 09:20:00' }]
  const tasks = [], reviewHistory = [], versionHistory = []
  const ocr = { provider: 'aliyun', endpoint: '', encryptionReady: true, keyConfigured: true, keySuffix: '******1234', accessKeyIdSuffix: '******5678', recognitionAvailable: true }
  const mutations = []
  let nextId = 3
  const pageData = rows => ({ rows, total: rows.length, pageNum: 1, pageSize: 10 })
  const routes = [{ id: 1, name: '工作台', path: '/dashboard', component: 'dashboard/index', icon: 'dashboard' }, { id: 400, name: '记账管理', path: '/bookkeeping', children: [['dashboard', '财务工作台', 'bookkeeping/dashboard/index'], ['records', '账目列表', 'bookkeeping/records/index'], ['reviews', '审核中心', 'bookkeeping/reviews/index'], ['reports', '报表与导出', 'bookkeeping/reports/index'], ['config', '记账配置', 'bookkeeping/config/index'], ['ocr-config', 'OCR 配置', 'bookkeeping/ocr-config/index']].map(([key, name, component], index) => ({ id: 401 + index, name, path: `/bookkeeping/${key}`, component, icon: 'file' })) }]
  const finance = { from: '2026-09-13', to: '2026-10-12', income: '6800.00', expense: '1320.50', net: '5479.50', postedCount: 5, pendingCount: 1, activeCount: 7, states: { DRAFT: 1, PENDING_REVIEW: 1, RETURNED: 0, POSTED: 5, WITHDRAWN: 0, VOIDED: 0 }, daily: Array.from({ length: 30 }, (_, i) => ({ date: new Date(Date.UTC(2026, 8, 13 + i)).toISOString().slice(0, 10), income: i === 28 ? '6800.00' : '0.00', expense: i === 27 ? '1000.00' : i === 29 ? '320.50' : '0.00', net: i === 28 ? '6800.00' : i === 27 || i === 29 ? '-1000.00' : '0.00', count: i === 27 || i === 28 || i === 29 ? 1 : 0 })), categories: [{ categoryId: 3, categoryName: '办公费用', recordType: 'EXPENSE', amount: '1000.00', recordCount: 2 }, { categoryId: 4, categoryName: '差旅交通', recordType: 'EXPENSE', amount: '320.50', recordCount: 1 }], latest: [], previous: { income: '0.00', expense: '500.00', net: '-500.00', postedCount: 1 } }
  await context.addInitScript(user => { localStorage.setItem('scaffold.token', 'local-browser-test'); localStorage.setItem('scaffold.user', JSON.stringify(user)); localStorage.setItem('scaffold.expiresAt', String(Date.now() + 3600000)) }, user)
  await context.route(url => url.pathname.startsWith('/api/'), async route => {
    const req = route.request(), url = new URL(req.url()), p = url.pathname.replace('/api', ''), method = req.method()
    const body = req.postDataJSON?.bind(req)
    let data = null
    if (method !== 'GET') mutations.push({ path: p, method, body: req.headers()['content-type']?.includes('application/json') ? body() : null })
    if (p === '/auth/me') data = user
    else if (p === '/auth/routes') data = { routes }
    else if (p === '/bookkeeping/dashboard/summary' || p === '/bookkeeping/reports/summary') data = { ...finance, latest: records.filter(r => r.status === 'POSTED').slice(0, 6) }
    else if (p === '/bookkeeping/reports/daily') data = pageData(finance.daily)
    else if (p === '/bookkeeping/reports/category') data = finance.categories
    else if (p === '/bookkeeping/reports/export' || p === '/bookkeeping/records/export') { await route.fulfill({ contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: Buffer.from('PK\x03\x04mock-xlsx') }); return }
    else if (p === '/bookkeeping/config/options') data = { ...config, users: [{ id: 1, nickname: '系统管理员', username: 'admin' }], depts: [{ id: 5, parentId: 0, name: '财务部', status: 1 }] }
    else if (p === '/bookkeeping/ocr/config/status') data = ocr
    else if (p === '/bookkeeping/ocr/config' && method === 'PUT') { const payload = body(); ocr.provider = payload.provider; if (payload.clearApiKey) { ocr.keyConfigured = false; ocr.keySuffix = ''; ocr.accessKeyIdSuffix = ''; ocr.recognitionAvailable = false } }
    else if (p === '/bookkeeping/records' && method === 'GET') { const keyword = url.searchParams.get('keyword') || ''; data = pageData(records.filter(r => r.summary.includes(keyword) && (!url.searchParams.get('status') || r.status === url.searchParams.get('status')))) }
    else if (p === '/bookkeeping/records' && method === 'POST') { const payload = body(); const id = nextId++; records.push({ ...payload, id, recordNo: `REC20261012${id}`, status: 'DRAFT', version: 0, details: payload.details.map((row, index) => ({ ...row, id: index + 100, categoryName: categories.find(c => c.id === row.categoryId)?.categoryName })) }); data = { id } }
    else if (p === '/bookkeeping/reviews/pending') data = pageData(records.filter(r => r.status === 'PENDING_REVIEW'))
    else if (/\/records\/\d+\/attachments$/.test(p)) { const id = Number(p.split('/')[3]); if (method === 'POST') { files.push({ ...files[0], id: files.length + 1, recordId: id, originalName: '补充票据.png', isPrimary: 0 }); data = { id: files.length } } else data = files.filter(file => file.recordId === id) }
    else if (/\/attachments\/\d+\/download$/.test(p)) { await route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE1sAAAAASUVORK5CYII=', 'base64') }); return }
    else if (/\/records\/\d+\/ocr\/tasks$/.test(p)) data = pageData(tasks.filter(t => t.recordId === Number(p.split('/')[3])))
    else if (/\/records\/\d+\/ocr$/.test(p)) { const id = tasks.length + 1, payload = body(); tasks.push({ id, recordId: Number(p.split('/')[3]), attachmentId: payload.attachmentId, status: 'SUCCEEDED', normalizedResult: { summary: '识别建议：办公采购', totalAmount: '320.50', occurredDate: '2026-10-12', text: '办公用品 320.50元', sourceVersion: payload.version, documentType: payload.documentType }, createTime: '2026-10-12 10:30:00' }); data = { id } }
    else if (/\/ocr\/tasks\/\d+\/confirm$/.test(p)) { const task = tasks.find(t => t.id === Number(p.split('/')[4])), record = records.find(r => r.id === task.recordId); Object.assign(record, body(), { version: record.version + 1 }); task.status = 'CONFIRMED' }
    else if (/\/ocr\/tasks\/\d+$/.test(p)) data = tasks.find(t => t.id === Number(p.split('/')[4]))
    else if (/\/records\/\d+\/reviews$/.test(p)) data = pageData(reviewHistory.filter(r => r.recordId === Number(p.split('/')[3])))
    else if (/\/records\/\d+\/versions$/.test(p)) data = pageData(versionHistory.filter(r => r.recordId === Number(p.split('/')[3])))
    else if (/\/records\/\d+\/(submit|review\/approve|review\/return|withdraw|void)$/.test(p)) { const record = records.find(r => r.id === Number(p.split('/')[3])), before = { ...record }, action = p.endsWith('submit') ? 'SUBMIT' : p.endsWith('approve') ? 'APPROVE' : p.endsWith('return') ? 'RETURN' : p.endsWith('withdraw') ? 'WITHDRAW' : 'VOID'; record.status = ({ SUBMIT: 'PENDING_REVIEW', APPROVE: 'POSTED', RETURN: 'RETURNED', WITHDRAW: 'WITHDRAWN', VOID: 'VOIDED' })[action]; record.version++; reviewHistory.unshift({ id: reviewHistory.length + 1, recordId: record.id, action, fromStatus: before.status, toStatus: record.status, reason: body().reason, operatorName: 'admin', reviewTime: '2026-10-12 10:40:00' }); versionHistory.unshift({ id: versionHistory.length + 1, recordId: record.id, versionNo: record.version, action, before, after: { ...record }, operatorName: 'admin', createTime: '2026-10-12 10:40:00' }) }
    else if (/\/records\/\d+$/.test(p)) { const record = records.find(r => r.id === Number(p.split('/')[3])); if (method === 'PUT') Object.assign(record, body(), { version: record.version + 1 }); else data = record }
    else if (p.startsWith('/bookkeeping/config/')) { const key = { categories: 'categories', 'payment-methods': 'paymentMethods', 'fund-accounts': 'fundAccounts', projects: 'projects' }[p.split('/')[3]]; if (key) data = pageData(config[key]); else throw Error(`Unknown mock config ${p}`) }
    else throw Error(`Unexpected API ${method} ${p}`)
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, message: '成功', data }) })
  })
  const page = await context.newPage(), errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error' || (message.type() === 'warning' && message.text().includes('[Vue warn]'))) errors.push(message.text()) })
  const snap = name => page.screenshot({ path: path.join(artifacts, `${name}.png`), fullPage: true })
  const overflow = async () => assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false)
  try {
    await page.goto(`${origin}/bookkeeping/records`)
    await page.getByRole('button', { name: '办公用品采购', exact: true }).waitFor()
    console.log((await page.locator('body').innerText()).slice(0, 800))
    await snap('bookkeeping-records-desktop'); await overflow()
    await page.getByRole('button', { name: '办公用品采购', exact: true }).click()
    await page.getByRole('dialog', { name: '账目详情' }).waitFor()
    await page.getByRole('button', { name: '办公采购票据.png', exact: true }).click()
    await page.getByRole('img', { name: '办公采购票据.png' }).waitFor()
    await page.getByRole('button', { name: '识别', exact: true }).click()
    await page.getByRole('button', { name: '核对并填写' }).click()
    await page.getByRole('dialog', { name: '核对 OCR 识别结果' }).waitFor()
    await page.getByRole('button', { name: '保存草稿', exact: true }).click()
    await page.getByText('已人工确认', { exact: true }).waitFor()
    assert.equal(records[0].status, 'DRAFT'); assert.equal(tasks[0].status, 'CONFIRMED')
    await snap('bookkeeping-detail-desktop')
    await page.getByRole('dialog', { name: '账目详情' }).getByRole('button', { name: '提交审核', exact: true }).click()
    await page.getByRole('dialog', { name: '提交审核' }).getByRole('button', { name: '提交审核', exact: true }).click()
    await page.getByRole('button', { name: '审核通过', exact: true }).waitFor()
    await page.getByRole('dialog', { name: '账目详情' }).getByRole('button', { name: '审核通过', exact: true }).click()
    await page.getByRole('dialog', { name: '审核通过' }).getByRole('button', { name: '审核通过', exact: true }).click()
    await page.getByRole('button', { name: '撤回重审', exact: true }).waitFor()
    assert.equal(records[0].status, 'POSTED')
    await page.getByRole('button', { name: '撤回重审', exact: true }).click()
    await page.getByRole('dialog', { name: '撤回重审' }).getByRole('button', { name: '撤回重审', exact: true }).click()
    await page.getByText('请填写操作原因', { exact: true }).waitFor()
    await page.getByRole('dialog', { name: '撤回重审' }).getByRole('textbox').fill('调整项目归属')
    await page.getByRole('dialog', { name: '撤回重审' }).getByRole('button', { name: '撤回重审', exact: true }).click()
    await page.getByRole('button', { name: '编辑', exact: true }).waitFor()
    assert.equal(records[0].status, 'WITHDRAWN')
    await page.getByRole('dialog', { name: '账目详情' }).getByRole('button', { name: '关闭', exact: true }).click()
    await page.getByRole('button', { name: '新增账目', exact: true }).click()
    const editor = page.getByRole('dialog', { name: '新增账目' })
    await editor.getByLabel('摘要', { exact: false }).fill('两条明细测试')
    await editor.getByRole('button', { name: '保存草稿', exact: true }).click()
    await page.getByText('请选择支付方式', { exact: true }).waitFor()
    await editor.getByRole('button', { name: '添加明细', exact: true }).click()
    await page.setViewportSize({ width: 390, height: 844 }); await overflow(); await snap('bookkeeping-editor-mobile')
    await editor.getByLabel('支付方式', { exact: false }).click()
    await page.getByRole('option', { name: '银行转账', exact: true }).click()
    await editor.getByLabel('资金账户', { exact: false }).click()
    await page.getByRole('option', { name: '公司基本账户', exact: true }).click()
    const detailRows = editor.locator('.bk-detail-row')
    for (let index = 0; index < 2; index++) {
      await detailRows.nth(index).getByLabel('分类', { exact: false }).click()
      await page.getByRole('option', { name: '办公费用', exact: true }).click()
      await detailRows.nth(index).getByLabel('金额（元）', { exact: false }).fill(index === 0 ? '0.10' : '0.20')
    }
    await editor.getByRole('button', { name: '使用合计', exact: true }).click()
    await editor.getByRole('button', { name: '保存草稿', exact: true }).click()
    await page.getByRole('dialog', { name: '账目详情' }).getByText('两条明细测试', { exact: true }).waitFor()
    const created = records.find(record => record.summary === '两条明细测试')
    assert.equal(created.totalAmount, '0.30'); assert.equal(created.details.length, 2)
    assert.equal(created.details[0].amount, '0.10'); assert.equal(created.details[1].amount, '0.20')
    assert.equal(created.status, 'DRAFT'); assert.equal(created.occurredTime, undefined)
    assert.equal(Object.hasOwn(mutations.find(change => change.method === 'POST' && change.path === '/bookkeeping/records').body, 'id'), false)
    await page.getByLabel('上传票据文件', { exact: true }).setInputFiles({ name: 'test-receipt.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE1sAAAAASUVORK5CYII=', 'base64') })
    await page.getByRole('button', { name: '补充票据.png', exact: true }).waitFor()
    assert.equal(files.at(-1).recordId, created.id)
    await page.getByRole('dialog', { name: '账目详情' }).getByRole('button', { name: '关闭', exact: true }).click()
    records[0].remark = '上一笔独有备注'
    const editRow = async summary => page.getByRole('row').filter({ has: page.getByRole('button', { name: summary, exact: true }) }).getByRole('button', { name: '编辑账目', exact: true }).click()
    await editRow(records[0].summary)
    const editing = page.getByRole('dialog', { name: '编辑账目' })
    await editing.getByLabel('备注', { exact: true }).waitFor()
    assert.equal(await editing.getByLabel('备注', { exact: true }).inputValue(), '上一笔独有备注')
    await editing.getByRole('button', { name: '取消', exact: true }).click()
    // MyBatis Map 省略 NULL 字段：下一笔没有备注/部门时，不能沿用上一笔的值。
    delete created.remark; delete created.deptId
    await editRow(created.summary)
    await editing.getByLabel('备注', { exact: true }).waitFor()
    assert.equal(await editing.getByLabel('备注', { exact: true }).inputValue(), '')
    assert.match(await editing.getByLabel('所属部门', { exact: false }).innerText(), /请选择部门/)
    await editing.getByRole('button', { name: '取消', exact: true }).click()
    await snap('bookkeeping-records-mobile')
    await page.goto(`${origin}/bookkeeping/reviews`)
    await page.getByRole('button', { name: '客户项目服务收入', exact: true }).waitFor(); await overflow(); await snap('bookkeeping-reviews-mobile')
    await page.goto(`${origin}/bookkeeping/config`)
    await page.getByRole('cell', { name: '办公费用', exact: true }).waitFor(); await overflow(); await snap('bookkeeping-config-mobile')
    await page.goto(`${origin}/bookkeeping/ocr-config`)
    await page.getByText('本地配置就绪', { exact: true }).waitFor(); await overflow(); await snap('bookkeeping-ocr-mobile')
    await page.getByRole('textbox').filter({ hasNot: page.locator('[readonly]') }).count()
    await page.getByLabel('AccessKey ID', { exact: false }).fill('test-id-only')
    await page.getByLabel('AccessKey Secret', { exact: false }).fill('test-secret-only')
    await page.getByRole('button', { name: '保存配置', exact: true }).click()
    await page.getByText('OCR 配置已保存', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('AccessKey ID', { exact: false }).inputValue(), '')
    assert.equal(await page.getByLabel('AccessKey Secret', { exact: false }).inputValue(), '')
    await page.evaluate(() => localStorage.setItem('scaffold.appearance', JSON.stringify({ theme: 'dark', density: 'regular', railCollapsed: false })))
    await page.reload(); await page.getByText('本地配置就绪', { exact: true }).waitFor(); await snap('bookkeeping-ocr-dark-mobile')
    await page.setViewportSize({ width: 1440, height: 1000 }); await snap('bookkeeping-ocr-dark-desktop')
    await page.goto(`${origin}/bookkeeping/records`); await page.getByRole('button', { name: '新增账目', exact: true }).waitFor(); await snap('bookkeeping-records-dark-desktop')
    await page.goto(`${origin}/bookkeeping/dashboard`)
    await page.getByRole('heading', { name: '财务工作台', exact: true }).waitFor()
    await page.locator('.finance-grid [aria-label="6,800.00"]').waitFor()
    assert.equal(await page.locator('.workspace-switch').getByRole('link', { name: '工作台', exact: true }).count(), 1)
    assert.equal(await page.locator('.workspace-switch').getByRole('link', { name: '财务工作台', exact: true }).getAttribute('aria-current'), 'page')
    assert.equal(await page.locator('.finance-grid .trend-line').count(), 1)
    assert.equal(await page.locator('.finance-grid .trend-line-2').count(), 1)
    await snap('finance-dashboard-dark-desktop'); await overflow()
    await page.evaluate(() => { localStorage.setItem('scaffold.appearance', JSON.stringify({ theme: 'light', density: 'regular', railCollapsed: false })) })
    await page.reload(); await page.locator('.finance-grid [aria-label="6,800.00"]').waitFor()
    await snap('finance-dashboard-desktop')
    await page.getByRole('radio', { name: '自定义', exact: true }).click()
    await page.getByLabel('发生日期起', { exact: false }).click()
    const datePopup = page.getByRole('dialog', { name: '选择日期', exact: true })
    await datePopup.waitFor(); await snap('finance-date-picker')
    await datePopup.getByRole('button', { name: '下个月', exact: true }).click()
    await datePopup.getByRole('button', { name: '上个月', exact: true }).click()
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: '应用', exact: true }).click()
    await page.goto(`${origin}/bookkeeping/reports`)
    await page.getByRole('heading', { name: '报表与导出', exact: true }).waitFor()
    await page.getByText('办公费用', { exact: true }).last().waitFor()
    await snap('finance-reports-desktop'); await overflow()
    const [reportDownload] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '导出当前查询结果', exact: true }).click()])
    assert.equal(reportDownload.suggestedFilename(), '财务报表.xlsx')
    await page.getByLabel('收支类型', { exact: false }).click()
    await page.getByRole('option', { name: '支出', exact: true }).click()
    const [query] = await Promise.all([page.waitForRequest(req => req.url().includes('/reports/daily') && req.url().includes('recordType=EXPENSE')), page.getByRole('button', { name: '查询', exact: true }).click()])
    assert.match(query.url(), /recordType=EXPENSE/)
    await page.setViewportSize({ width: 390, height: 844 }); await snap('finance-reports-mobile'); await overflow()
    await page.goto(`${origin}/bookkeeping/dashboard`); await page.locator('.finance-grid [aria-label="6,800.00"]').waitFor(); await snap('finance-dashboard-mobile'); await overflow()
    await page.getByRole('radio', { name: '自定义', exact: true }).click()
    await page.getByLabel('发生日期止', { exact: false }).click()
    await page.getByRole('dialog', { name: '选择日期', exact: true }).waitFor(); await overflow(); await snap('finance-picker-mobile')
    await page.keyboard.press('Escape')
    await page.goto(`${origin}/bookkeeping/records`)
    await page.getByRole('button', { name: '新增账目', exact: true }).click()
    const timeEditor = page.getByRole('dialog', { name: '新增账目', exact: true })
    await timeEditor.getByLabel('实际发生日期', { exact: false }).click()
    await page.getByRole('dialog', { name: '选择日期', exact: true }).getByRole('button', { name: '今天', exact: true }).click()
    await timeEditor.getByLabel('实际发生时间', { exact: false }).click()
    const timePopup = page.getByRole('dialog', { name: '选择时间', exact: true })
    await timePopup.getByRole('button', { name: '13时', exact: true }).click()
    await timePopup.getByRole('button', { name: '42分', exact: true }).click()
    await timePopup.getByRole('button', { name: '08秒', exact: true }).click()
    await snap('finance-time-picker-mobile'); await overflow()
    await timePopup.getByRole('button', { name: '确定', exact: true }).click()
    assert.match(await timeEditor.getByLabel('实际发生时间', { exact: false }).innerText(), /13:42:08/)
    await timeEditor.getByLabel('实际发生时间', { exact: false }).click()
    await timePopup.getByRole('button', { name: '清空时间', exact: true }).click()
    assert.match(await timeEditor.getByLabel('实际发生时间', { exact: false }).innerText(), /时间未记录/)
    await timeEditor.getByRole('button', { name: '取消', exact: true }).click()
    assert.equal(await page.locator('input[type=date],input[type=time]').count(), 0)
    const [recordDownload] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '导出当前查询结果', exact: true }).click()])
    assert.equal(recordDownload.suggestedFilename(), '账目明细.xlsx')
    user.permissions = ['bookkeeping:record:list']; user.roles = ['bookkeeper']
    await page.reload(); await page.getByRole('button', { name: '识别建议：办公采购', exact: true }).waitFor()
    assert.equal(await page.getByRole('button', { name: '新增账目', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: '编辑账目', exact: true }).count(), 0)
    assert.equal(await page.getByRole('button', { name: '导出当前查询结果', exact: true }).count(), 0)
    await page.getByRole('button', { name: '识别建议：办公采购', exact: true }).click(); await page.getByText('当前账号没有票据查看权限。', { exact: true }).waitFor()
    assert.deepEqual(errors, [])
    fs.writeFileSync(path.join(artifacts, 'bookkeeping-web-verification.json'), JSON.stringify({ passed: true, checks: ['desktop', '390px', 'dark', 'private preview', 'OCR manual confirmation', 'submit', 'approve', 'withdraw reason', 'editor validation', 'two-detail draft saved with exact cents', 'receipt upload', 'nullable edit fields reset between records', 'config secrets cleared', 'read-only permissions'], mutations: mutations.map(({ path, method }) => ({ path, method })), errors }, null, 2))
    console.log('PASS: bookkeeping browser workflow, views, permissions; simulated HTTP only.')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })

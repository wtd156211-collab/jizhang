import type { HttpClient, QueryParams } from './http'
import type { IdResponse, PageResult } from './system'

export interface CategoryRow {
  id: number
  categoryType: 'INCOME' | 'EXPENSE'
  categoryCode: string
  categoryName: string
  parentId: number
  sort: number
  status: number
  createTime: string
  updateTime: string
}

export interface CategoryWriteRequest {
  categoryType: 'INCOME' | 'EXPENSE'
  categoryCode: string
  categoryName: string
  parentId?: number
  sort: number
  status: number
}

export interface PaymentMethodWriteRequest {
  methodCode: string
  methodName: string
  sort: number
  status: number
}

export interface FundAccountWriteRequest {
  accountCode: string
  accountName: string
  accountType: string
  sort: number
  status: number
  remark?: string
}

export interface ProjectWriteRequest {
  projectCode: string
  projectName: string
  sort: number
  status: number
  remark?: string
}

export interface RecordDetailWriteRequest {
  categoryId: number
  amount: string | number
  handlerId?: number
  deptId?: number
  projectId?: number
  remark?: string
  sort?: number
}

export interface RecordWriteRequest {
  recordType: 'INCOME' | 'EXPENSE'
  summary: string
  occurredDate: string
  occurredTime?: string
  totalAmount: string | number
  paymentMethodId: number
  fundAccountId: number
  deptId?: number
  remark?: string
  version?: number
  details: RecordDetailWriteRequest[]
}

export interface ConfigRow {
  id: number
  [key: string]: unknown
}

export interface RecordRow extends RecordWriteRequest {
  id: number // 账目 ID
  recordNo: string // 账目编号
  status: RecordStatus // 审核状态
  version: number // 当前乐观锁版本
  detailCount?: number // 明细条数
  createTime?: string
  updateTime?: string
}
export interface BookkeepingOptions {
  categories: CategoryRow[]
  paymentMethods: ConfigRow[]
  fundAccounts: ConfigRow[]
  projects: ConfigRow[]
  users: Array<{ id: number; nickname: string; username: string }> // 数据范围内的有效经办人
  depts: Array<{ id: number; name: string; parentId: number; status: number }> // 可用归属部门
}

/** 私有票据元数据；文件通过鉴权下载接口获取。 */
export interface AttachmentRow {
  id: number
  recordId: number // 关联账目
  originalName: string // 上传文件原名
  mimeType: string // 内容类型
  fileSize: number // 字节数
  sha256: string // 原文件校验值
  isPrimary: number // 是否主票据（1/0）
  sourceType: string // 来源（人工上传等）
  uploadedBy: number // 上传用户 ID
  createTime: string // 上传时间
}

export type RecordStatus = 'DRAFT' | 'PENDING_REVIEW' | 'RETURNED' | 'POSTED' | 'WITHDRAWN' | 'VOIDED'
export type ReviewAction = 'SUBMIT' | 'APPROVE' | 'RETURN' | 'WITHDRAW' | 'VOID'

export interface ReviewActionRequest {
  version: number // 页面加载时的当前版本号，冲突时刷新
  reason?: string // 退回、撤回、作废必填原因
}

export interface ReviewHistoryRow {
  id: number // 审核历史 ID
  recordId: number // 账目 ID
  action: ReviewAction // 操作类型
  fromStatus: RecordStatus // 操作前状态
  toStatus: RecordStatus // 操作后状态
  reason: string | null // 原因
  operatorId: number // 操作人 ID
  operatorName: string | null // 操作人当前账号名
  reviewTime: string // 操作时间
}

export interface RecordSnapshot {
  [key: string]: unknown
  details?: Array<Record<string, unknown>> // 当时的明细
  attachments?: AttachmentRow[] // 仅附件权限允许时返回元数据
}

export interface RecordVersionRow {
  id: number // 版本快照 ID
  recordId: number // 账目 ID
  versionNo: number // 修改后的版本号
  action: ReviewAction | 'CREATE' | 'UPDATE' | 'DELETE' | 'OCR_CONFIRM' // 触发动作
  reason: string | null // 原因
  operatorId: number // 操作人 ID
  operatorName: string | null // 操作人当前账号名
  createTime: string // 留痕时间
  before: RecordSnapshot | null // 修改前，首次新增为空
  after: RecordSnapshot // 修改后
}

export type OcrDocumentType = 'Invoice' | 'ShoppingReceipt' | 'PaymentRecord' | 'Advanced'
export interface OcrStartRequest {
  attachmentId: number // 已保存的原始票据附件
  version: number // 当前账目版本号
  documentType?: OcrDocumentType // 发票 / 小票 / 支付截图 / 通用文字，默认发票
}
export interface OcrConfigRequest {
  provider: 'aliyun' | 'disabled' // 阿里云或停用
  endpoint?: string // 阿里云固定官方杭州地址，可留空
  accessKeyId?: string // 替换 AccessKey ID，空值保留原值
  apiKey?: string // 替换 AccessKey Secret，空值保留原值，服务端不回显
  clearApiKey?: boolean // 同时清空两项凭据
}
export interface OcrConfigStatus {
  provider: string
  endpoint: string
  encryptionReady: boolean // 服务端加密主密钥就绪
  keyConfigured: boolean // 已保存 Secret
  keySuffix: string // Secret 脱敏后缀
  accessKeyIdSuffix: string // ID 脱敏后缀
  recognitionAvailable: boolean // 配置就绪，云服务开通/授权仍需实际调用验证
}
export interface OcrNormalizedResult {
  summary?: string // 摘要建议
  occurredDate?: string // 发生日期建议，须人工确认
  occurredTime?: string // 明确识别到的时间，缺失不补午夜
  totalAmount?: string // 总金额建议
  text?: string // 识别原文
  documentType: OcrDocumentType // 当前识别类型
  sourceVersion: number // 发起识别时的账目版本
}
export interface OcrTaskRow {
  id: number
  recordId: number // 关联账目
  attachmentId: number // 关联已保存原图
  provider: string
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CONFIRMED'
  normalizedResult: OcrNormalizedResult | null // 识别建议，禁止直接入账
  errorMessage: string | null // 脱敏失败提示
  requestedBy: number // 发起人
  startedAt: string | null
  finishedAt: string | null
  createTime: string
}

export interface FinanceDay extends Record<string, unknown> {
  date: string // 实际发生日期（ISO）
  income: string // 已入账收入（元，精确字符串）
  expense: string // 已入账支出（元）
  net: string // 收入减支出；可以为负，不是账户余额
  count: number // 已入账账目笔数
}
export interface FinanceCategory {
  categoryId: number // 分类 ID
  categoryName: string // 分类名称
  recordType: 'INCOME' | 'EXPENSE' // 收支类型
  amount: string // 此分类明细合计（元）
  recordCount: number // 涉及账目数（同账目多明细只计一笔）
}
export interface FinanceSummary {
  from: string // 统计起日（含）
  to: string // 统计止日（含）
  income: string
  expense: string
  net: string
  postedCount: number // 已入账笔数
  pendingCount: number // 当前周期待审核笔数
  activeCount: number // 当前周期未作废未删除笔数，作为入账比例分母
  states: Record<RecordStatus, number> // 周期内各状态笔数
  daily: FinanceDay[] // 包括无账目的日期
  categories: FinanceCategory[]
  latest: RecordRow[] // 周期内最近六笔已入账记录
  previous: Pick<FinanceSummary, 'income' | 'expense' | 'net' | 'postedCount'> // 上一等长周期
}

export function createBookkeepingApi(client: HttpClient) {
  return {
    dashboard: (query?: QueryParams) => client.get<FinanceSummary>('/bookkeeping/dashboard/summary', query),
    reports: {
      summary: (query?: QueryParams) => client.get<FinanceSummary>('/bookkeeping/reports/summary', query),
      daily: (query?: QueryParams) => client.get<PageResult<FinanceDay>>('/bookkeeping/reports/daily', query),
      categories: (query?: QueryParams) => client.get<FinanceCategory[]>('/bookkeeping/reports/category', query),
      export: (query?: QueryParams) => client.download('/bookkeeping/reports/export', query),
    },
    options: () => client.get<BookkeepingOptions>('/bookkeeping/config/options'),
    config: {
      categories: pageCrud<CategoryRow, CategoryWriteRequest>(client, '/bookkeeping/config/categories'),
      paymentMethods: pageCrud<ConfigRow, PaymentMethodWriteRequest>(client, '/bookkeeping/config/payment-methods'),
      fundAccounts: pageCrud<ConfigRow, FundAccountWriteRequest>(client, '/bookkeeping/config/fund-accounts'),
      projects: pageCrud<ConfigRow, ProjectWriteRequest>(client, '/bookkeeping/config/projects'),
    },
    records: {
      export: (query?: QueryParams) => client.download('/bookkeeping/records/export', query),
      list: (query?: QueryParams) => client.get<PageResult<RecordRow>>( '/bookkeeping/records', query),
      get: (id: number) => client.get<RecordRow>(`/bookkeeping/records/${id}`),
      create: (body: RecordWriteRequest) => client.post<IdResponse>('/bookkeeping/records', body),
      update: (id: number, body: RecordWriteRequest) => client.put<void>(`/bookkeeping/records/${id}`, body),
      remove: (id: number) => client.delete<void>(`/bookkeeping/records/${id}`),
    },
    reviews: {
      pending: (query?: QueryParams) => client.get<PageResult<RecordRow>>('/bookkeeping/reviews/pending', query),
      submit: (id: number, body: ReviewActionRequest) => client.post<void>(`/bookkeeping/records/${id}/submit`, body),
      approve: (id: number, body: ReviewActionRequest) => client.post<void>(`/bookkeeping/records/${id}/review/approve`, body),
      returnRecord: (id: number, body: ReviewActionRequest) => client.post<void>(`/bookkeeping/records/${id}/review/return`, body),
      withdraw: (id: number, body: ReviewActionRequest) => client.post<void>(`/bookkeeping/records/${id}/withdraw`, body),
      voidRecord: (id: number, body: ReviewActionRequest) => client.post<void>(`/bookkeeping/records/${id}/void`, body),
      history: (id: number, query?: QueryParams) => client.get<PageResult<ReviewHistoryRow>>(`/bookkeeping/records/${id}/reviews`, query),
      versions: (id: number, query?: QueryParams) => client.get<PageResult<RecordVersionRow>>(`/bookkeeping/records/${id}/versions`, query),
    },
    ocr: {
      start: (recordId: number, body: OcrStartRequest) => client.post<IdResponse>(`/bookkeeping/records/${recordId}/ocr`, body),
      task: (id: number) => client.get<OcrTaskRow>(`/bookkeeping/ocr/tasks/${id}`),
      tasks: (recordId: number, query?: QueryParams) => client.get<PageResult<OcrTaskRow>>(`/bookkeeping/records/${recordId}/ocr/tasks`, query),
      confirm: (id: number, body: RecordWriteRequest) => client.post<void>(`/bookkeeping/ocr/tasks/${id}/confirm`, body),
      configStatus: () => client.get<OcrConfigStatus>('/bookkeeping/ocr/config/status'),
      saveConfig: (body: OcrConfigRequest) => client.put<void>('/bookkeeping/ocr/config', body),
    },
    attachments: {
      list: (recordId: number) => client.get<AttachmentRow[]>(`/bookkeeping/records/${recordId}/attachments`),
      upload: (recordId: number, file: File, primary = false) => {
        const body = new FormData()
        body.append('file', file)
        body.append('primary', String(primary))
        return client.post<IdResponse>(`/bookkeeping/records/${recordId}/attachments`, body)
      },
      download: (id: number) => client.download(`/bookkeeping/attachments/${id}/download`),
      setPrimary: (id: number) => client.put<void>(`/bookkeeping/attachments/${id}/primary`),
      remove: (id: number) => client.delete<void>(`/bookkeeping/attachments/${id}`),
    },
  }
}

function pageCrud<T, W>(client: HttpClient, path: string) {
  return {
    list: (query?: QueryParams) => client.get<PageResult<T>>(path, query),
    create: (body: W) => client.post<IdResponse>(path, body),
    update: (id: number, body: W) => client.put<void>(`${path}/${id}`, body),
    remove: (id: number) => client.delete<void>(`${path}/${id}`),
  }
}

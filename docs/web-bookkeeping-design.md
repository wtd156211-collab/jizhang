# Web 记账系统落地设计

> 状态：设计已确认，实施中；基础配置、草稿、私有票据、审核、历史与阿里云 OCR 已接入首批 Web 页面，独立 MySQL 与模拟 HTTP 浏览器验证通过；财务工作台、报表和导出待开发，真实云调用待联调
>
> 范围：基于现有 Spring Boot + Vue 公司管理脚手架，优先建设 Web 端记账闭环。
>
> 本文只定义产品范围、业务规则、系统边界和实施顺序，不包含业务代码改动。

## 1. 目标与边界

系统面向一家公司的内部财务记录和审核，不做多租户 SaaS，也不扩展为完整 ERP 或专业会计系统。

第一版 Web 目标是让一笔收入或支出能够完成：

```text
基础配置 → 新建账目 → 保存草稿 → 保存票据 → 提交审核
→ 通过入账 / 退回修改 → 撤回重审 / 作废 → 统计与导出
```

第一版包含：

- 收入和支出记录；
- 收入分类、支出分类、支付方式、资金账户和项目；
- 多条账目明细及金额合计校验；
- 草稿、提交、退回、通过、撤回重审和作废；
- 图片和 PDF 票据附件；
- 账目详情、审核历史和操作记录；
- 收入、支出、净额、分类统计和 Excel 导出；
- OCR 原图保存、OCR 服务配置管理和人工确认入口。

暂缓实现：

- 自动审批和多级审批流程；
- 银行流水、多币种、汇率、会计凭证、借贷分录和结账；
- 采购、库存、销售、CRM；
- 通知中心、微信小程序和 Flutter 客户端；
- OCR 自动入账。

## 2. 与现有脚手架的衔接

现有项目已经提供登录、JWT 会话、Redis token、用户、角色、部门、菜单、数据权限、字典、系统配置、操作日志、分页、统一响应和 Vue 公共组件。记账模块复用这些能力，不重复实现认证、菜单、表格、弹窗和通用错误处理。

后端新增独立 Maven 模块 `scaffold-bookkeeping`，依赖 `scaffold-common`、`scaffold-framework` 和 `scaffold-system`。模块内部按 Controller、Service、Mapper、DTO、VO 和查询 SQL 分层；复杂报表 SQL 使用独立 Mapper 查询，不把统计逻辑写入页面。

前端继续位于 `scaffold-vue`，新增记账 API、页面和业务组件。页面优先复用 `ListPage`、`SearchForm`、`DataTable`、`FormDialog`、`PermissionButton`、`Drawer`、`Metric`、`TrendChart` 和 `BarChart`。新页面沿用当前导航、间距、表格密度、按钮、状态标签和动效规范。

数据库继续使用当前按版本执行的 SQL 初始化方式，新增：

```text
V3__bookkeeping_schema.sql
V4__bookkeeping_seed.sql
```

`DbInit` 按版本顺序读取脚本；业务表默认包含 `create_by`、`update_by`、`create_time`、`update_time` 四个审计字段。

## 3. 业务数据模型

新增表使用 `acct_` 前缀，系统基础表继续使用 `sys_` 前缀。

| 表 | 用途 |
| --- | --- |
| `acct_category` | 收入和支出分类，支持停用，不物理删除历史引用 |
| `acct_payment_method` | 支付方式 |
| `acct_fund_account` | 现金、银行卡、微信、支付宝等资金账户 |
| `acct_project` | 项目或业务归属 |
| `acct_record` | 账目主表 |
| `acct_record_detail` | 账目明细 |
| `acct_record_attachment` | 图片、PDF 和 OCR 原始票据 |
| `acct_record_review` | 审核动作、状态变化和原因 |
| `acct_record_version` | 关键编辑和状态变化的前后快照 |
| `acct_ocr_task` | OCR 请求、结果、失败信息和人工确认状态 |

### 3.1 账目主表

`acct_record` 至少包含：

| 字段 | 含义 |
| --- | --- |
| `record_no` | 系统生成的账目编号 |
| `record_type` | `INCOME` 收入或 `EXPENSE` 支出 |
| `status` | 草稿、待审核、已退回、已入账、撤回重审、已作废 |
| `summary` | 摘要 |
| `occurred_date` | 实际发生日期，必填 |
| `occurred_time` | 实际发生时间，可为空 |
| `total_amount` | 主记录金额，使用定点小数 |
| `created_by` | 创建人 |
| `dept_id` | 账目所属部门，可为空 |
| `version` | 并发编辑版本号 |

列表默认按 `occurred_date DESC`、同日具体 `occurred_time DESC`、编号倒序排列。缺少具体时间的记录保留日期精度，不能自动转换为零点。所有日报、周报、月报和自定义区间都按 `occurred_date` 过滤。

### 3.2 账目明细

每条明细包含分类、金额、经办人、部门、项目、备注和排序号。主记录金额必须等于明细金额合计；每条明细至少填写经办人、部门、项目中的一项。已提交审核后，明细只能按状态规则修改。

### 3.3 附件与 OCR

附件从上传成功开始保存原文件，不因 OCR 失败而删除。附件记录保存原始文件名、文件类型、大小、校验值、存储键、是否主票据、上传人和访问时间。文件通过鉴权下载接口访问，不生成永久公开 URL。

OCR 首版采用“人工触发、辅助填写、人工确认”模式：

1. 用户上传票据，原图先保存为 `acct_record_attachment`；
2. 用户在账目详情点击“识别票据”；
3. 后端创建 `acct_ocr_task`，调用当前配置的 OCR 适配器；
4. 识别结果保存原始响应和可编辑字段；
5. 用户在确认页修改金额、日期、摘要等字段；
6. 用户确认后只回填草稿，不能直接入账。

用户已选定阿里云 OCR；适配器接口保持可替换。首版使用官方 RecognizeAllText，支持发票、小票、支付截图和通用文字；管理员配置 AccessKey ID 与 AccessKey Secret 后启用。未配置或调用失败保存失败任务，保留原图，允许人工录入。

OCR 使用独立配置接口及后续专用页面，底层复用 `sys_config`；固定 Secret 键名为 `bookkeeping.ocr.api_key`，ID 键名为 `bookkeeping.ocr.access_key_id`，另配供应商和接口地址。通用系统参数接口排除整个 `bookkeeping.ocr.*` 命名空间并禁止修改，避免查询/导出/通用编辑绕过。专用接口只返回“已配置”和脱敏后缀，日志不记录凭据，使用环境变量 `BOOKKEEPING_SECRET_KEY` 提供的服务端主密钥加密。管理员只能替换或清空，不能查询完整密钥。具体联调见 `bookkeeping-ocr.md`。

## 4. 状态和业务规则

```text
草稿
 └─ 提交审核 → 待审核
                 ├─ 通过 → 已入账
                 └─ 退回 → 已退回 → 修改后重新提交
已入账
 ├─ 撤回重审 → 撤回状态 → 修改后重新提交 → 待审核
 └─ 作废 → 已作废
```

规则如下：

- 草稿可编辑、删除和上传附件；
- 已退回记录可查看退回原因、编辑后重新提交；
- 待审核记录由具备审核权限的管理员或会计处理；
- 退回原因、撤回原因和作废原因必填；
- 已入账记录不能直接覆盖修改；
- 已作废记录不进入有效金额统计，但保留详情、附件、版本和审核历史；
- 金额、日期、分类、明细合计和附件类型由后端再次校验；
- 使用版本号防止多人同时编辑覆盖；
- 审核动作携带当前 `version`（版本号），退回、撤回和作废的 `reason`（原因）最多1000个字符；已作废状态没有恢复操作；
- 草稿新增、修改、删除及每个审核动作保存前后快照；快照包含当时账目、明细及附件元数据，读取附件信息仍需票据查看权限；
- 写操作统一记录到现有操作日志，并额外写入账目审核历史。

## 5. 权限与数据范围

记账权限码按以下前缀注册：

```text
bookkeeping:dashboard:view
bookkeeping:record:list
bookkeeping:record:add
bookkeeping:record:edit
bookkeeping:record:submit
bookkeeping:record:review
bookkeeping:record:return
bookkeeping:record:withdraw
bookkeeping:record:void
bookkeeping:record:export
bookkeeping:attachment:view
bookkeeping:attachment:upload
bookkeeping:attachment:remove
bookkeeping:config:list
bookkeeping:config:edit
bookkeeping:review:list
bookkeeping:report:list
bookkeeping:ocr:recognize
bookkeeping:ocr:config
```

普通员工默认只能编辑和查看自己的草稿、已退回记录；已入账的普通字段可按公司规则查看。管理员和会计可按部门数据范围查看账目、审核和导出。待审核记录和附件遵循更严格的权限检查，前端隐藏按钮不能代替后端 403 校验。

## 6. Web 页面

新增一级导航“记账管理”：

```text
记账管理
├── 财务工作台
├── 账目列表
├── 新建账目
├── 审核中心
├── 报表与导出
└── 基础配置
    ├── 收入分类
    ├── 支出分类
    ├── 支付方式
    ├── 资金账户
    └── 项目管理
```

主要页面职责：

- `BookkeepingDashboard.vue`：收入、支出、净额、待审核数量、趋势、分类排行和最近账目；
- `Records.vue`：按类型、状态、发生日期、分类、金额和关键字查询，支持分页和导出；
- `RecordEditor.vue`：编辑主信息、动态明细、附件和草稿操作；
- `RecordDetail.vue`：显示账目、明细、附件、OCR 结果和审核时间线；
- `ReviewCenter.vue`：待审核、通过、退回、审核历史及原因填写；
- `BookkeepingConfig.vue` 及其子页面：维护分类、支付方式、资金账户、项目和 OCR 配置。

金额使用右对齐和统一精度；收入、支出、待审核、已退回和已作废使用稳定的状态色；上传、识别、提交、退回和作废均提供明确的进度、确认和失败重试反馈。

## 7. 后端接口分组

所有接口使用 `/api/bookkeeping` 前缀，继续返回现有 `R<T>` 结构。

```text
GET    /records
POST   /records
GET    /records/{id}
PUT    /records/{id}
DELETE /records/{id}

POST   /records/{id}/submit
POST   /records/{id}/review/approve
POST   /records/{id}/review/return
POST   /records/{id}/withdraw
POST   /records/{id}/void

GET    /reviews/pending
GET    /records/{id}/reviews
GET    /records/{id}/versions

POST   /records/{id}/attachments
GET    /attachments/{id}/download
DELETE /attachments/{id}

POST   /records/{id}/ocr
GET    /ocr/tasks/{id}
GET    /ocr/config/status
PUT    /ocr/config

GET    /dashboard/summary
GET    /reports/summary
GET    /reports/category
GET    /records/export
```

配置、账目写入、审核、附件和 OCR 操作分别绑定权限码和操作日志。报表只读取已入账且未作废的数据。

## 8. 实施顺序

### 阶段 0：数据和权限基础

- 新增记账 Maven 模块和数据库版本脚本；
- 建立基础配置、账目、明细、附件、审核、版本和 OCR 任务表；
- 注册菜单、权限和初始化数据；
- 验证登录、权限和数据库初始化链路。

### 阶段 1：账目录入和查询

- 新建收入和支出；
- 动态明细与金额校验；
- 草稿保存、编辑、删除；
- 账目列表、详情和发生时间排序；
- 基础配置页面。

### 阶段 2：附件和 OCR 配置

- 图片和 PDF 上传、预览、下载和删除；
- 原图持久化和权限访问；
- 管理员通过 OCR 专用配置接口和后续页面维护服务信息；
- OCR Key 脱敏、加密存储和操作日志；
- OCR 任务和结果数据结构。

### 阶段 3：审核闭环

- 提交审核；
- 待审核列表；
- 通过、退回、重新提交；
- 撤回重审和作废；
- 审核历史、版本快照和并发编辑校验。

### 阶段 4：工作台、报表和导出

- 收入、支出、净额和待审核指标；
- 按发生日期和分类统计；
- 趋势图和分类图；
- Excel 导出和导出权限；
- 作废记录从有效统计中排除。

### 阶段 5：OCR 识别和体验完善

- 接入一个确定的 OCR 服务适配器；
- 手动触发识别和状态查询；
- 结果人工确认后回填草稿；
- 失败重试、超时和服务未配置提示；
- 桌面宽屏、窄屏、空状态和异常状态验收。

## 9. 验收标准

第一条完整验收链路如下：

1. 管理员配置分类、支付方式、资金账户和 OCR 配置；
2. 普通用户创建一笔支出；
3. 添加两条明细，明细合计等于主金额；
4. 上传主票据和补充附件，确认原图可以查看和下载；
5. 保存草稿并再次编辑；
6. 提交审核；
7. 管理员退回并填写原因；
8. 用户修改后重新提交；
9. 管理员审核通过；
10. 工作台和报表出现该笔有效账目；
11. 管理员作废记录；
12. 作废后金额从有效统计中消失，但详情、附件和审核历史仍然保留；
13. 无权限账号无法执行审核、导出和附件访问；
14. OCR 识别结果可以修改，确认后只回填草稿，不能自动入账；
15. 列表按真实发生日期和时间从新到旧显示。

## 10. 当前决策与风险

- 已实现基础配置、草稿、私有票据、单级审核、历史快照和阿里云 OCR，并接入四个首批 Web 页面；独立 MySQL 测试验证事务和数据范围，开发库尚未执行 V3/V4。财务工作台、报表和服务端导出待实现；
- 私有票据已接入本地存储适配器，原始字节保存、权限下载与回滚清理已有验证；浏览器以模拟 HTTP 验证预览、上传和 OCR 人工核对，真实开发库及云服务仍待联调；
- 用户已选阿里云，官方 SDK 适配器已实现；实际服务仍需云账号开通、RAM 凭据和真实票据验证；
- OCR Key 的加密密钥必须由服务端环境变量提供，不能写入仓库；
- 已入账记录的历史保留和作废规则属于数据正确性边界，后续实现必须优先测试；
- Web 是当前正式开发入口，移动端沿用业务接口，放到 Web 闭环稳定后再规划。
- Web 按模块逐步接入：账目列表、录入、详情/审核与 OCR 配置页面已先交付；下一阶段为财务工作台、报表与服务端导出，沿用现有组件与主题，财务展示采用 Dashboard 工作区布局。

用户已确认方案并要求继续开发，后续按 `web-bookkeeping-implementation-plan.md` 分阶段推进，以实际验证记录为准。

# Web 记账系统 Implementation Plan

> **For agentic workers:** This plan is for the current local repository. Execute tasks in order, keep each task independently verifiable, and preserve existing user configuration changes.

**Goal:** 在现有 Spring Boot + Vue 公司管理脚手架上，分阶段交付可验收的 Web 记账完整闭环：基础配置、收入支出、附件、OCR 配置、审核、作废、统计和导出。

**Architecture:** 新增 `scaffold-bookkeeping` 后端 Maven 模块，复用 `scaffold-common`、`scaffold-framework` 和 `scaffold-system` 的响应、认证、权限、数据范围、审计和数据库能力。前端继续使用 `scaffold-vue` 的现有路由、公共组件和视觉变量；数据库新增 `acct_` 业务表和版本 SQL，业务统计使用独立查询 Mapper。

**Tech Stack:** Java 17 target、Spring Boot 3.5、MyBatis、MySQL、Redis、JWT、Vue 3、TypeScript、Vite、现有自有 UI 组件；文件首版使用本地存储适配器，OCR 使用可替换适配器，导出使用现有下载约定并补充 XLSX 支持。

**当前进度（2026-10-12）：** Task 1–6 和 Task 8A 已实现：配置 / 草稿 / 私有票据 / 审核 / 留痕 / 阿里云 OCR 后端，及账目、审核、基础配置、管理员 OCR 配置四个 Web 页面。新增只读录入选项接口；金额在 API 和历史响应中为精确字符串，前端以整数分计算。独立 MySQL 8.4 验证记账116项（含17项真实 MySQL）、公共4项、框架8项；前端31项、类型/构建、完整后端打包通过。浏览器已验证1440px、390px、深色、两明细保存 / 上传 / OCR 人工确认 / 提交 / 通过 / 撤回原因 / 权限，使用模拟 HTTP。开发库 V3/V4 未执行，真实登录及阿里云调用仍待联调。下一步为 Task 7 + Task 8B：财务工作台、报表和服务端 Excel 导出，展示区域沿用 Dashboard 工作区。

## Global Constraints

- 单公司内部使用，不增加多租户、银行流水、会计凭证、结账、采购、库存、销售和 CRM。
- 业务排序和报表周期使用 `occurred_date` 与可空的 `occurred_time`，不使用创建时间替代实际发生时间。
- 已入账记录禁止静默覆盖；修改必须撤回重审或作废，保留审核历史、原因和版本快照。
- OCR 只辅助填充，人工确认后仍然只是草稿，不能自动入账。
- 票据原图在上传成功后保存，附件下载必须经过登录和权限校验，不能提供永久公开链接。
- OCR API Key 只能由管理员管理，接口不回显原值，日志不得记录原值，服务端使用环境变量提供的加密密钥保存密文。
- 菜单权限必须与初始化脚本、后端注解和前端组件注册使用同一组编码，包括工作台、审核列表、报表列表和 OCR 配置权限。
- 新增后端接口使用统一 `R<T>`、参数校验、权限注解、操作日志和异常处理。
- 新增前端页面优先复用 `ListPage`、`SearchForm`、`DataTable`、`FormDialog`、`PermissionButton`、`Drawer`、`Metric` 和现有图表组件。
- 不覆盖用户当前对 `application.yml`、`application-dev.yml` 和其他已有文件的修改。
- 本计划只描述实施步骤；执行时每个逻辑单元先验证，再提出 Conventional Commit，未经用户确认不提交。

---

### Task 1: 建立业务模块和数据库基线

**Files:**
- Modify: `scaffold-system/pom.xml`
- Create: `scaffold-system/scaffold-bookkeeping/pom.xml`
- Create: `scaffold-system/scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/BookkeepingModuleMarker.java`
- Create: `scaffold-system/sql/V3__bookkeeping_schema.sql`
- Create: `scaffold-system/sql/V4__bookkeeping_seed.sql`
- Modify: `scaffold-system/scaffold-admin/src/main/java/com/scaffold/admin/ScaffoldAdminApplication.java`
- Modify: `scaffold-system/scaffold-admin/src/main/java/com/scaffold/admin/util/DbInit.java` only if module resource loading requires it

**Interfaces:**
- Produces the `scaffold-bookkeeping` Maven artifact and the `acct_` schema consumed by all later tasks.
- Defines `acct_category`, `acct_payment_method`, `acct_fund_account`, `acct_project`, `acct_record`, `acct_record_detail`, `acct_record_attachment`, `acct_record_review`, `acct_record_version` and `acct_ocr_task`.
- Seeds bookkeeping directory/menu/button rows and initial category, payment method and account examples with `INSERT IGNORE`.

- [x] Confirm existing Git diff and preserve the two user-edited application files.
- [x] Add the module to the Maven reactor and make `scaffold-admin` depend on it.
- [x] Add table comments and Chinese column comments; include audit fields on business tables.
- [x] Add indexes for `status`, `record_type`, `occurred_date`, `created_by`, `dept_id` and attachment lookup.
- [x] Add foreign-key-like validation in service code while keeping the current schema style compatible with the existing SQL baseline.
- [x] Run `mvn -B -ntp -pl scaffold-admin -am package -DskipTests`.
- [x] Run `git diff --check` and inspect the generated SQL before any database execution.

### Task 2: Implement bookkeeping configuration APIs

**Files:**
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/BookkeepingConfigController.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/BookkeepingConfigService.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/mapper/BookkeepingConfigMapper.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/dto/*ConfigRequest.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/vo/*ConfigRow.java`
- Create: `scaffold-vue/src/api/bookkeeping.ts`

**Interfaces:**
- `GET /api/bookkeeping/config/categories`
- `POST /api/bookkeeping/config/categories`
- `PUT /api/bookkeeping/config/categories/{id}`
- `DELETE /api/bookkeeping/config/categories/{id}` only when no historical reference exists
- Equivalent list/create/update/disable endpoints for payment methods, fund accounts and projects.
- All list endpoints return `id`, display name, type/status and audit timestamps with Chinese field documentation.

- [x] Reuse existing paging and status conventions.
- [x] Prevent disabling a configuration item that is required by an active draft without returning a clear business error.
- [x] Keep historical records readable after a category or payment method is disabled.
- [x] Add `@RequiresPermission` and `@OperLog` to every write endpoint.
- [x] Add service tests for duplicate names, disabled references and permission boundaries.

Task 2 API 基线已实现：8 个配置服务测试覆盖分类新增、重复、停用、父分类和历史类型保护；真实权限切面测试覆盖配置只读权限不能写入。软删后保留编码，不允许复用；删除 / 停用引用的真实数据库联调尚未完成。

### Task 3: Implement draft records and detail validation

**Files:**
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/RecordController.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/RecordService.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/mapper/RecordMapper.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/entity/*`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/dto/RecordWriteRequest.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/vo/RecordDetailVO.java`
- Create: `scaffold-bookkeeping/src/test/java/com/scaffold/bookkeeping/service/RecordServiceTest.java`

**Interfaces:**
- `GET /api/bookkeeping/records`
- `POST /api/bookkeeping/records`
- `GET /api/bookkeeping/records/{id}`
- `PUT /api/bookkeeping/records/{id}`
- `DELETE /api/bookkeeping/records/{id}` for drafts and returned records only
- Query filters: `recordType`, `status`, `occurredFrom`, `occurredTo`, `categoryId`, `keyword`, `pageNum`, `pageSize`.

- [x] Validate income/expense type, positive decimal amounts, required occurred date and detail rows.
- [x] Validate `totalAmount == sum(detail.amount)` using decimal arithmetic, never floating-point comparison.
- [x] Require at least one of handler, department or project on every detail.
- [x] Enforce data scope on reads and writes using creator/department fields.
- [x] Order records by occurred date, concrete occurred time, then record number.
- [x] Add optimistic version checks and return a conflict error when the record changed after loading.
- [ ] Verify draft creation, edit, delete, own-record visibility and validation failure tests.

Task 3 服务与接口基线已实现：账目草稿 CRUD、明细校验、数据范围 SQL、实际发生时间排序和乐观锁已接入。8 个服务测试覆盖创建、金额合计及上限、缺失类型、无效经办人、并发冲突、更新前数据范围和创建时归属部门范围；成功编辑 / 删除与真实数据库可见性验证尚未完成。删除采用软删除，写入持有账目行锁。

### Task 4: Add local attachment storage and protected download

**Files:**
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/storage/AttachmentStorage.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/storage/LocalAttachmentStorage.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/AttachmentController.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/AttachmentService.java`
- Modify: `scaffold-system/scaffold-admin/src/main/resources/application-dev.yml` only after preserving the user’s current values
- Create: `scaffold-bookkeeping/src/test/java/com/scaffold/bookkeeping/storage/LocalAttachmentStorageTest.java`

**Interfaces:**
- `POST /api/bookkeeping/records/{id}/attachments`
- `GET /api/bookkeeping/records/{id}/attachments`
- `GET /api/bookkeeping/attachments/{id}/download`
- `PUT /api/bookkeeping/attachments/{id}/primary`
- `DELETE /api/bookkeeping/attachments/{id}` for editable records only.

- [x] Store files outside the public static directory using a generated storage key.
- [x] Validate image/PDF type, configured size limit, filename and content signature.
- [x] Keep the original file for later OCR even when OCR fails.
- [x] Support primary receipt selection with one primary attachment per record.
- [x] Check record visibility and attachment permission before metadata or file download.
- [x] Test successful upload, invalid type, size rejection, unauthorized download and deletion after submit.

Task 4 API 基线已实现：9 个附件服务测试、2 个真实磁盘存储测试，以及真实权限切面边界测试通过。覆盖原始字节下载、超限、伪造签名、数据不可见、提交后拒绝变更、事务回滚清理。主票据操作通过账目行锁串行执行，实际数据库并发效果待联调；OCR 尚未实现，本阶段只保证原文件独立保存。公共日志只记录上传元数据并屏蔽 API Key，前端公共 HTTP 客户端支持 FormData。

**历史验证边界（Task 1–4）：** 当时新增记账测试不依赖数据库；早期曾运行现有 admin 全量集成测试并写入开发库测试数据，之后停止在开发库运行集成测试，当时 V3/V4 未执行。Task 5 用户明确授权缺少 `/mysql` 时仿照其流程：先核对数据库、目标库、表结构，再验证写入。本次在独立新建容器执行 V1–V4 与审核集成测试，测试结束清理容器；开发库 V3/V4 仍未执行。后续 admin 集成测试同样必须使用独立测试库。

### Task 5: Implement review, return, withdraw, void and history

本轮实施约定：沿用现有六种状态，`POSTED → WITHDRAWN` 后允许修订，再提交至 `PENDING_REVIEW`；与既有可编辑状态一致。每个动作必须携带当前 `version`；退回、撤回、作废必填原因。状态动作及草稿创建 / 修改 / 删除保存 JSON 前后快照（含当时明细和附件元数据），软删账目仍可按原数据范围查询历史。当前不新增复杂审批节点或自审禁令，审核由现有独立权限及数据范围控制。

执行顺序：先验证全状态矩阵与回归路径 → 实现状态更新 / 历史 / 版本事务 → 接入草稿修改快照及历史读取 → 增加真实权限切面、事务回滚和 Mapper SQL 验证 → 接入前端 API 封装 → 更新文档并运行模块测试 / 构建。V1–V4 仅在独立测试容器执行，不连接开发库写入。

后续 Web 约束（用户确认）：沿用系统 UI 设计，复用既有组件、布局和动效，仅替换业务数据；财务工作台和报表展示区域参照现有 `views/Dashboard.vue` 的工作台展示结构。

**Files:**
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/ReviewController.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/ReviewService.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/mapper/ReviewMapper.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/dto/ReviewActionRequest.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/vo/ReviewHistoryVO.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/ReviewAction.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/RecordHistoryService.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/vo/RecordVersionVO.java`
- Modify: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/RecordService.java`
- Create: `scaffold-bookkeeping/src/test/java/com/scaffold/bookkeeping/service/ReviewServiceTest.java`
- Create: `scaffold-bookkeeping/src/test/java/com/scaffold/bookkeeping/service/ReviewMySqlIntegrationTest.java`
- Create: `scripts/test-bookkeeping-review.ps1`

**Interfaces:**
- `POST /api/bookkeeping/records/{id}/submit`
- `GET /api/bookkeeping/reviews/pending`
- `POST /api/bookkeeping/records/{id}/review/approve`
- `POST /api/bookkeeping/records/{id}/review/return`
- `POST /api/bookkeeping/records/{id}/withdraw`
- `POST /api/bookkeeping/records/{id}/void`
- `GET /api/bookkeeping/records/{id}/reviews`
- `GET /api/bookkeeping/records/{id}/versions`

- [x] Encode allowed transitions in one service policy instead of scattered controller conditions.
- [x] Require a reason for return, withdraw and void.
- [x] Write before/after snapshots and state transitions in the same transaction as the action.
- [x] Preserve details / attachments on void; confirm the record no longer matches posted-only effective queries.
- [x] Prevent duplicate review actions with version checks and transition validation.
- [x] Test the full return-and-resubmit path and all illegal transitions.
- [x] Read paged review / version history for soft-deleted records within original data scope; protect attachment metadata.
- [x] Test actual MySQL transaction rollback and concurrent review, including fresh detail snapshots after waiting for another writer.
- [ ] Task 7 reports must apply `status='POSTED' AND deleted=0`; no report implementation exists in Task 5.

Task 5 验证包括真实权限切面与 MockMvc 参数校验、30组状态矩阵及回归路径；独立 MySQL 测试覆盖主表 / 明细 / 审核 / 快照一致性、唯一键失败回滚、并发只成功一次、软删历史可见性与部门范围。读历史不改变主表，前端已增加类型与 API 封装；记账 Web 页面仍待 Task 8。下一步为 Task 6：OCR 配置、管理员 API Key 管理、保存原图后的 OCR 辅助录入。

验证命令与结果：从项目根目录运行 `./scripts/test-bookkeeping-review.ps1`，记账82项（含7项 MySQL）、公共4项、框架7项全部通过；`npm run test:unit` 25项通过；`npm run build` 前端类型检查 / 生产构建通过；`mvn -B -ntp -pl scaffold-admin -am package -DskipTests` 完整后端打包通过。独立测试容器已清理。真实登录 HTTP 与浏览器界面验收后续进行。

### Task 6: Add OCR configuration, task records and safe key handling

用户已选择阿里云，首版使用官方 `ocr_api20210707:3.1.3` SDK 的 RecognizeAllText。保留可替换适配器和 disabled 状态；阿里云地址固定杭州 HTTPS，直接上传原文件。管理员通过独立接口管理两项加密 AK；通用参数接口排除整个 OCR 命名空间。采用同步识别、5秒连接/15秒读取超时、禁用自动重试及短时间重复请求复用；确认沿用草稿写入和快照事务。

**Files:**
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/ocr/OcrProvider.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/ocr/AliyunOcrProvider.java`、`AliyunOcrTransport.java`、`DisabledOcrProvider.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/OcrService.java`、`OcrConfigService.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/mapper/OcrTaskMapper.java`、`OcrConfigMapper.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/domain/dto/OcrConfigRequest.java`、`OcrStartRequest.java`；`domain/vo/OcrTaskVO.java`、`OcrConfigStatusVO.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/OcrController.java`
- Create: `scaffold-system/src/main/java/com/scaffold/system/service/SecretConfigService.java`（系统通用加密能力，不放在业务 Controller）
- Modify: 系统配置 DTO / Service / Mapper 与公共 OperationLogAspect，保护查询/导出/写操作和日志
- Modify: `RecordService`，复用草稿更新并追加 OCR_CONFIRM 来源快照
- 配置从 `@Value` 读取 `BOOKKEEPING_SECRET_KEY`，保持用户现有 application 文件不变
- Create: `scaffold-bookkeeping/src/test/java/com/scaffold/bookkeeping/security/SecretConfigServiceTest.java`
- Create: OCR 服务、配置、云适配器、权限、Controller 与 MySQL 集成测试；`docs/bookkeeping-ocr.md`

**Interfaces:**
- `POST /api/bookkeeping/records/{id}/ocr`
- `GET /api/bookkeeping/ocr/tasks/{id}`
- `GET /api/bookkeeping/records/{id}/ocr/tasks`
- `POST /api/bookkeeping/ocr/tasks/{id}/confirm`
- `GET /api/bookkeeping/ocr/config/status`
- `PUT /api/bookkeeping/ocr/config`

- [x] Store OCR provider name and endpoint through a controlled configuration key.
- [x] Encrypt both Aliyun credentials with an environment-provided server key; never return or log plaintext.
- [x] Return only configured status and masked suffix to the Web page; protect generic config CRUD/export paths.
- [x] Persist sanitized raw OCR response and normalized editable fields in the OCR task, linked to the saved original attachment.
- [x] Keep the provider adapter replaceable; add official Aliyun adapter and explicit disabled mode.
- [x] Ensure OCR confirmation only updates an editable draft and cannot change record status to posted.
- [x] Test missing configuration, masked response, key replacement, OCR failure and manual correction.
- [x] Verify real MySQL transactions, duplicate requests, simultaneous confirmation, snapshot rollback, task reload and scope boundaries.
- [ ] Verify actual Aliyun account activation, RAM permissions and OCR output with real credentials and a receipt.
- [x] Complete browser OCR workflow and configuration page acceptance in Task 8 (simulated HTTP; real cloud acceptance remains separate).

Task 6 初次验证：记账模块109项（含8项 OCR 和7项审核真实 MySQL 测试）、公共4项、框架8项通过；前端26项、类型检查和生产构建通过，完整后端打包通过。未调用收费云接口，开发库未写入。原图始终保留，确认增加 OCR_CONFIRM 快照，不改变审核状态。Task 8A 已补业务页面和浏览器模拟 HTTP 验证；真实云调用仍未验收。

### Task 7: Implement dashboard, reports and XLSX export

本轮 Task 7 + 8B 实施细化（用户已授权开始）：

- 顶部栏工作台位置增加“工作台｜财务工作台”切换，依当前账号动态菜单显示入口；复用现有 bento 工作区、Metric、TrendChart、BarChart、TickRing、SectionLabel、Tag 和常用入口。
- 统计按发生日期（含首尾日），默认近30天、最多366天；只计入未删除的 POSTED 金额。按主表汇总收支、净额和每日趋势，分类按明细金额汇总，避免重复计算主表金额；状态计数单独展示，标明入账比例分母。
- 报表提供每日收支分页、分类汇总、类型/日期查询和重置；统计与报表 Excel 使用相同筛选。账目列表 Excel 保持现有日期、关键词、分类、类型和状态过滤，分页参数不影响导出。
- 后端新增公共 XLSX 工具，金额与用户内容使用文本单元格，保留精度并防公式执行；导出有独立权限、操作日志、数据范围及明确行数上限，不静默截断。
- 新增公共 DatePicker / TimePicker，沿用 Select 的输入外壳、珍珠玻璃浮层与动效；日期月历、时间时分秒选择、清空与键盘操作，空时间保持未知。
- 先验证统计边界/空周期/作废排除/数据范围/多明细/精度/导出权限，再实施；使用隔离 MySQL 容器及真实浏览器检查桌面、窄屏、深色、日期时间选择、入口和导出。开发库与用户 application 配置保持不动，不提交代码。

**Files:**
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/ReportController.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/service/ReportService.java`
- Create: `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/mapper/ReportMapper.java`
- Create: `scaffold-bookkeeping/src/test/java/com/scaffold/bookkeeping/service/ReportServiceTest.java`
- Modify: `scaffold-system/pom.xml` or bookkeeping module POM to add the selected XLSX library

**Interfaces:**
- `GET /api/bookkeeping/dashboard/summary`
- `GET /api/bookkeeping/reports/summary`
- `GET /api/bookkeeping/reports/category`
- `GET /api/bookkeeping/records/export`

  - [x] Aggregate only posted and non-voided records.
  - [x] Use occurred date for all report boundaries and trend buckets.
  - [x] Return income, expense, net amount, pending count, category totals and trend series.
  - [x] Apply the same filters to on-screen results and export output.
  - [x] Enforce export permission and record the export operation.
  - [x] Test empty periods, date boundaries, voided records and category totals.

### Task 8: Build Web pages and integrate permissions

实施次序调整：先交付账目列表、编辑、附件/OCR、详情/审核与 OCR 配置页面（Task 8A），形成可操作的 Web 闭环；再实施 Task 7 的报表/导出并接入工作台与报表区域（Task 8B）。页面继续复用现有组件、主题和动效，财务展示参照 Dashboard 工作区。各步骤仍独立验证，页面完成必须在浏览器检查桌面和窄屏视口。

Task 8A 执行细化：

- [x] 先写金额精度、日期/时间空值、明细归属和 OCR 建议应用的前端行为测试，再实现共用记账工具。
- [x] 增加 `GET /api/bookkeeping/config/options`，要求账目查看权限；配置只读选项仅含有效项目，经办人/部门沿用数据范围，不返回个人联系方式。现有配置管理接口保持独立权限。
- [x] 用 `ListPage` / `useListPage` 实现账目与审核分页列表；用 `FormDialog` 实现账目编辑和必填原因操作。
- [x] 抽取账目编辑、详情、票据/OCR面板供列表和审核中心复用；私有文件通过鉴权下载生成临时预览，关闭时释放。
- [x] 用现有表单和主题实现阿里云配置及记账基础配置；凭据输入为空时保留旧值，成功保存/退出后清空输入。
- [x] 仅注册本阶段已实现页面；工作台、报表和服务器导出随 Task 7 接入，不注册空页面或用本地 CSV 代替真实导出。
- [x] 运行前端单测、类型/构建、相关后端测试；在真实浏览器检查桌面、390px、深色、权限及编辑/OCR/审核关键交互，说明模拟接口与真实服务验证边界。
- [x] 补金额响应回归：详情、列表、待审核和历史快照的总金额 / 明细金额返回字符串；精确解析已保存的数字快照，不改内部 BigDecimal 或其他模块。

Task 8A 页面与验证记录见 [Web 使用说明](bookkeeping-web-guide.md)。列表暂不提供导出按钮，服务端统一导出随 Task 7 实施；真实云识别、开发库端到端与报表尚未验收。

**Files:**
- Create: `scaffold-vue/src/views/bookkeeping/BookkeepingDashboard.vue`
- Create: `scaffold-vue/src/views/bookkeeping/Records.vue`
- Create: `scaffold-vue/src/views/bookkeeping/Reviews.vue`
- Create: `scaffold-vue/src/views/bookkeeping/Config.vue`
- Create: `scaffold-vue/src/views/bookkeeping/OcrConfig.vue`
- Create: `scaffold-vue/src/components/bookkeeping/RecordList.vue`、`RecordEditor.vue`、`RecordDetail.vue`、`ReviewActionDialog.vue`、`ReceiptPanel.vue`
- Create: `scaffold-vue/src/utils/bookkeeping.ts`、`utils/__tests__/bookkeeping.test.ts`
- Create: `scripts/verify-bookkeeping-web.cjs`
- Modify: `scaffold-vue/src/api/index.ts`
- Modify: `scaffold-vue/src/api/bookkeeping.ts`
- Modify: `scaffold-vue/src/composables/auth.ts` to register `COMPONENT_MAP` entries
- Modify: `scaffold-vue/src/router/index.ts` only if route registration is separate from menu-driven routes
- Create: `scaffold-vue/src/api/__tests__/bookkeeping.test.ts`

**Interfaces:**
- Pages consume the typed `bookkeepingApi` methods from Task 2–7.
- Buttons use the exact backend permission codes and remain disabled while requests are pending.

- [x] Add the “记账管理” navigation tree and the four implemented route components.
- [x] Build the record list with filters, status tags, amounts, occurrence time and row actions.
- [x] Build the editor with dynamic detail rows and live total validation; upload and submit are separate actions after draft save in the detail drawer.
- [x] Build the detail drawer with attachments, OCR result and review history.
- [x] Build the review center with approve, return reason, withdraw and void confirmations.
  - [x] Build dashboard and report views using the existing Dashboard workspace, theme and charts (Task 8B).
- [x] Build configuration panels for categories, payment methods, accounts, projects and masked OCR settings.
- [x] Verify API serialization and permission visibility with tests/browser regression; implement empty/error/loading and retry states.

### Task 9: Full verification and handoff

**Files:**
- Modify: `README.md` with Web bookkeeping startup and initialization notes
- Modify: `PROJECT_MAP.md` with bookkeeping tables, endpoints, permissions and page locations
- Modify: `changelog.md` with the completed feature entries
- Create: `docs/bookkeeping-acceptance-checklist.md`

- [ ] Run backend unit tests and module build (the admin integration suite requires an isolated test database):

```powershell
cd D:\Aiproject\jizhang\scaffold-system
mvn -B -ntp -pl scaffold-admin -am test
mvn -B -ntp -pl scaffold-admin -am package -DskipTests
```

- [ ] Run frontend tests and production build:

```powershell
cd D:\Aiproject\jizhang\scaffold-vue
npm run test:unit
npm run build
```

- [ ] Execute the manual flow: create configuration, create a two-detail expense, upload primary and supplemental files, submit, return, resubmit, approve, report, void, and verify history.
- [ ] Verify ordinary user, reviewer and administrator permissions against real API responses.
- [ ] Verify occurrence-date ordering, date-only entries, timezone behavior and voided-record exclusion.
- [ ] Verify OCR original image retention, masked key response, missing-key behavior and manual confirmation.
- [ ] Verify desktop and narrow-width layouts in the running Web application.
- [ ] Run `git diff --check`, inspect the final diff, and present the suggested Conventional Commit messages for user confirmation.

## Suggested delivery checkpoints

1. `feat: add bookkeeping schema and module foundation`
2. `feat: add bookkeeping records and configuration APIs`
3. `feat: add protected bookkeeping attachments`
4. `feat: add bookkeeping review workflow`
5. `feat: add OCR configuration and task handling`
6. `feat: add bookkeeping reports and Web pages`
7. `test: verify bookkeeping end-to-end flow`

Each checkpoint is independently reviewed and verified before moving to the next one. No checkpoint includes deployment or external publication.

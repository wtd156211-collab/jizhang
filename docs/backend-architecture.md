# 后端架构说明

当前底座使用 Spring Boot 多模块分层，用于后续后台业务开发。

| Maven 模块 | 中文职责 | 主要入口 |
| --- | --- | --- |
| `scaffold-common` | 统一响应、分页、业务异常 | `R`、`PageQuery`、`PageResult`、`BusinessException` |
| `scaffold-framework` | 认证、会话、权限、数据范围、操作审计、异常与请求追踪 | `security`、`datascope`、`audit`、`handler`、`filter` |
| `scaffold-system` | 用户、角色、组织、菜单、字典、参数、日志与工作台；日志统计与当日泳道 | `AuthController`、`SystemManagementController`、`DashboardController`、`MonitorController`、`MonitorStatsService` |
| `scaffold-generator` | 真实表导入、生成配置、预览、ZIP 下载 | `GeneratorController`、`GeneratorService`、`GeneratorMapper` |
| `scaffold-bookkeeping` | 公司内部记账业务；已接入配置、草稿、私有附件、审核、留痕和 OCR；报表继续实施 | `BookkeepingConfigController`、`RecordController`、`AttachmentController`、`ReviewController`、`OcrController` |
| `scaffold-admin` | 启动、配置、初始化工具与集成测试 | `ScaffoldAdminApplication`、`DbInit` |

建筑、工作流模块和 Flowable 依赖已移除。新增业务复用通用模块，不在 Controller 或 Service 重复实现基础安全与返回结构。

## 请求与安全

请求经 TraceId 与 JWT 过滤器进入 Controller → Service → MyBatis Mapper → MySQL，普通 JSON 接口返回 `R<T>`；导出和生成 ZIP 使用文件响应。

- JWT 与 Redis token 必须同时有效，退出撤销当前 token；每次登录的 JWT ID 独立，快速重新登录不会复用已撤销 token。
- JWT 签名密钥通过必填环境变量 `JWT_SECRET` 配置，UTF-8 编码至少 32 字节且不能全为空白；缺失或长度不足会阻止启动，仓库不内置共享签名密钥。
- 登录成功写登录日志并更新 `sys_user.last_login_time`（最近登录时间），用户列表与看板据此展示。
- `/api/health`、验证码与登录可匿名访问，其余接口要求登录。
- `@RequiresPermission` 校验操作权限；超级管理员直接放行，普通角色必须启用且未删除，停用用户不能获得接口权限。
- 菜单来自真实授权关系；超级管理员读取所有启用且可见的菜单，无需逐项授权新菜单。
- `@DataScope`、`SystemDataScopeResolver` 与 `DataScopeCriteria` 当前接入用户查询和导出，新增业务须按业务表字段显式接入。
- `@OperLog` 统一记录写操作及脱敏请求参数；异常由 `GlobalExceptionHandler` 返回统一错误结构。

## 初始化与时间

`sql/V1__init_schema.sql` 创建系统基础表，`V2__seed_data.sql` 初始化组织、岗位、角色、用户、菜单、字典、参数及生成配置。`sql/V3__bookkeeping_schema.sql` 创建记账分类、支付方式、资金账户、项目、账目、附件、审核、版本和 OCR 任务表；`V4__bookkeeping_seed.sql` 初始化记账菜单、按钮权限和最小配置数据。关联表、日志表、生成字段表和 OCR 任务表可不具备全部四个业务审计字段；其余业务表默认含创建人、更新人、创建时间和更新时间。

SQL 是新项目初始化基线，不是已有业务库的自动升级脚本。`DbInit` 使用进程中的 `DB_*` 变量，按版本顺序导入；参数 `fix` 会删除整个目标库，执行前必须核实库名与备份。

数据库连接信息与 Redis 信息来自环境变量。Hikari 通过独立属性传递 `connectionTimeZone` 和 `forceConnectionTimeZoneToSession`，默认 `+08:00`，避免 URL 中的加号被解码成空格；初始化工具同样使用连接属性，不修改 MySQL 全局时区。强制会话时区影响 `NOW()` 等数据库时间函数，配置含义见 [MySQL Connector/J 官方文档](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。

## 只读统计

`MonitorStatsService` + `MonitorStatsMapper` 提供操作 / 登录日志的概览、按时间桶趋势、当日泳道与登录 7×24 热力（`login-logs/heatmap`，含同 IP / 同账号连续失败 `anomalies` 聚合，阈值读 `sys_config`，缺省 5）；`DashboardService` 补充上一周期登录数、近 7 天按日操作量，`stats` 另含 `menus`（菜单项总数）与 `depts`（部门数），上一周期为 0 时以 `fresh` 标记「新增」；在岗 / 持有人数与数据范围分布只计未删除用户。`SystemManagementService.permissionsGraph` 聚合用户、角色、菜单与两组关联表，供权限溯源视图。用户分页 / 统计 / 导出支持可选 `postId`（经 `sys_user_post` 关联过滤）。统计与聚合全部为只读查询，不改表结构；时间边界取数据库 `NOW()` 后以字符串传参，P95 在 MySQL 5.7 下用 `ORDER BY cost LIMIT 1 OFFSET n` 计算。字段说明见 `docs/frontend-backend-integration.md`。

## 引用选项接口

- `/api/system/users/options` 受用户查询权限保护，返回部门结构；具有用户新增 / 编辑权限才返回角色、岗位选项，具有新增 / 重置密码权限才返回初始密码；不返回部门联系人信息。
- `/api/system/dict/options/{dictType}` 提供已启用字典中的启用选项，仅要求已登录；字典管理接口及写入仍由字典权限保护。

## 记账模块当前边界

记账模块已实现基础配置 API：分类、支付方式、资金账户和项目均支持分页查询、新增、修改、软删除；写操作使用 `bookkeeping:config:edit`，删除和停用会检查历史或草稿账目引用。软删除仍保留编码，与唯一索引一致；分类检查同类型上级、循环和下级引用，历史使用后不能改变收入支出类型。接口字段：`categoryType` 分类类型、`categoryCode` 分类编码、`categoryName` 分类名称、`methodCode` 支付方式编码、`accountCode` 资金账户编码、`projectCode` 项目编码、`status` 状态、`createTime` / `updateTime` 审计时间。首批账目、审核、配置与 OCR Web 页面已接入，报表和导出后续实施。

基础配置接口：`GET/POST /api/bookkeeping/config/categories`、`PUT/DELETE /api/bookkeeping/config/categories/{id}`；`GET/POST /api/bookkeeping/config/payment-methods`、`PUT/DELETE /api/bookkeeping/config/payment-methods/{id}`；`GET/POST /api/bookkeeping/config/fund-accounts`、`PUT/DELETE /api/bookkeeping/config/fund-accounts/{id}`；`GET/POST /api/bookkeeping/config/projects`、`PUT/DELETE /api/bookkeeping/config/projects/{id}`。数据库脚本仍是新增基线，执行前应先确认目标库和备份，不会由本次构建自动执行。

`GET /api/bookkeeping/config/options` 为普通录入账号提供只读选项，需 `bookkeeping:record:list`；不依赖用户 / 配置管理权限。响应 `categories`（有效分类）、`paymentMethods`（支付方式）、`fundAccounts`（资金账户）、`projects`（项目）、`users`（经办人，仅 ID / 账号 / 姓名）、`depts`（部门 ID / 名称 / 上级 / 状态）。配置仅启用且未删除的项，用户和部门经 `@DataScope` 限定，避免泄露联系方式或扩大管理能力。

账目草稿接口：`GET/POST /api/bookkeeping/records`、`GET/PUT/DELETE /api/bookkeeping/records/{id}`。`RecordService` 校验收入 / 支出类型、金额精度、明细合计、分类和配置状态；列表与详情使用数据范围，排序为 `occurred_date DESC`、有实际时间优先、`occurred_time DESC`。更新携带 `version` 做乐观锁检查，已入账或作废记录不能直接编辑。

草稿修改和删除先按数据范围锁定主表行，再检查可编辑状态；删除只标记 `deleted`，保留明细和历史。写入检查经办人 / 部门是否启用，账目归属部门限制为当前用户所属部门或授权范围；金额最多16位整数、2位小数，明细最多100条。缺失实际时间保存为 NULL，不替代成午夜。查询返回发生日期和时间字符串，避免 Map 的 JDBC 日期序列化偏移。

金额传输：账目列表、详情、待审核列表及版本 `before` / `after` 的 `totalAmount`（总金额）、`details[].amount`（明细金额）统一返回两位小数的十进制字符串，例如 `"9999999999999999.99"`，避免 JavaScript JSON 数字丢分。`RecordResponse` 集中处理响应转换，历史解析开启精确 BigDecimal 模式并在序列化时复制转换；不改数据库和内部服务 BigDecimal，也不影响脚手架其他模块。写请求同样使用字符串，Jackson 解析为 BigDecimal；前端以 BigInt 整数分合计。

私有附件接口：`GET/POST /api/bookkeeping/records/{recordId}/attachments`、`GET /api/bookkeeping/attachments/{id}/download`、`PUT /api/bookkeeping/attachments/{id}/primary`、`DELETE /api/bookkeeping/attachments/{id}`。独立权限为 `bookkeeping:attachment:view`（列表 / 下载）、`upload`（上传 / 主票据）、`remove`（删除）。读取检查账目可见性，写入锁账目行并限制为草稿、退回或撤回状态。

`AttachmentStorage` 为存储适配器，首版 `LocalAttachmentStorage` 使用日期和 UUID 生成私有存储键，原文件名不参与路径生成。上传支持 PNG、JPEG、PDF，检查后缀、MIME、内容签名及大小，保留原始字节和 SHA-256。下载不公开存储键，使用登录凭据、附件下载头、`no-store` 和 `nosniff`；删除软删元数据并保留原文件，事务回滚清理新文件。`sourceType` 为来源，`originalName` 为原文件名，`fileSize` 为字节数，`isPrimary` 为主票据标记。当前不保证解析整个图片 / PDF，签名检查用于拒绝明显格式伪造。

开发配置新增 `BOOKKEEPING_STORAGE_ROOT`（默认 `./data/bookkeeping`）和 `BOOKKEEPING_ATTACHMENT_MAX_BYTES`（默认 10485760）；Spring multipart 请求限制为文件10MB、请求12MB。更改文件上限时同步两处限制。公共操作日志不序列化上传字节，只记录文件名、内容类型、大小，并屏蔽 `apiKey` / `api_key` 等字段。

审核与历史接口由 `ReviewController` 提供：提交、通过、退回、撤回重审、作废分别使用独立权限；待审核列表复用 `RecordService.list` 的筛选与分页。`ReviewAction` 集中维护允许流转：草稿 / 退回 / 撤回 → 待审核，待审核 → 入账 / 退回，入账 → 撤回 / 作废。撤回后允许修改再提交，作废没有恢复接口，不能编辑、删除或修改附件。

审核请求 `version`（当前版本号）必传；`reason`（原因）退回、撤回、作废必填且最多1000个字符。`ReviewService.act` 按数据范围锁主表，核对版本，再按版本和来源状态条件更新；冲突使用业务错误码409（沿用统一异常处理的 HTTP 400 传输状态）。审核历史和版本快照与账目更新共用事务，历史插入失败会回滚状态和明细。写事务采用 `READ_COMMITTED`，避免等待行锁后快照读取旧明细；附件写入也使用同一主表行锁。

`RecordHistoryService` 为草稿新增 / 修改 / 软删及审核动作追加前后 JSON 快照，首次新增 `before` 为空；包括当时账目、明细与票据元数据，去掉内部存储键。版本号与主表版本同步，历史只追加，没有修改 / 删除接口。日志 / 快照表沿用 V3 中的操作人和操作时间字段，不重复增加业务四字段。

`GET /api/bookkeeping/records/{id}/reviews` 和 `/versions` 使用账目查看权限和原账目数据范围，允许查询软删账目的留痕，默认每页10条、最多100条、最新在前。版本响应字段：`versionNo` 版本号、`action` 动作、`before` / `after` 前后快照、`operatorId` 操作人 ID、`operatorName` 当前账号名、`reason` 原因、`createTime` 快照时间；审核响应 `fromStatus` / `toStatus` 为前后状态，`reviewTime` 为操作时间。只有具备 `bookkeeping:attachment:view` 才返回快照中的附件元数据；文件仍走独立鉴权下载。

单元与权限切面测试使用模拟 Mapper、MockMvc 和临时磁盘；真实事务测试通过 `scripts/test-bookkeeping-review.ps1` 创建独立 MySQL 8.4 容器，先检查库和表结构，再运行 V1–V4 测试基线及审核流程测试，结束后自动清理。集成测试覆盖完整退回 / 撤回重审 / 作废、历史软删查询、并发单次审核、历史写入失败回滚、等待锁后最新明细快照和实际 SQL 数据范围。脚本不读取开发库配置；开发库 V3/V4 仍未执行。真实 Vue 页面已用模拟 HTTP 完成浏览器验证，真实登录与开发库端到端验收另行进行。配置查询沿用脚手架现有 Map 返回风格。

### OCR 配置与人工确认

`OcrConfigService` 在 `sys_config` 保存供应商、官方接口地址和加密的阿里云 AccessKey ID / Secret。系统层 `SecretConfigService` 使用 AES-256-GCM、随机 nonce 与认证标签，主密钥由 `BOOKKEEPING_SECRET_KEY` 环境变量提供；缺少主密钥不阻止其他功能启动，但不能保存/使用凭据。通用参数查询与导出排除 OCR 命名空间，新增/修改/删除也禁止访问，SQL 再次加条件保护。配置 DTO 的凭据字段只写，公共操作日志对通用 key/value 参数补充脱敏。专用配置需 OCR 管理权限及系统管理员角色，状态返回 configured 和脱敏后缀。

`OcrService` 从已有附件读取私有原文件，按账目数据范围锁主表、核对可编辑状态和版本；首版同步调用 `AliyunOcrProvider` / 官方 SDK transport，5秒连接/15秒读取超时，禁用自动重试，不使用用户自定义上传 URL。支持发票、小票、支付截图和通用文字，缺失/无效字段不猜测；成功响应脱敏后存储 raw_result，接口仅返回 normalizedResult（建议字段）、sourceVersion（来源版本）、documentType（识别类型）与任务状态。成功结束后的10秒内复用同一请求的结果，失败仍保存任务且保留原图。

OCR 任务与列表读取需要识别权限、附件查看权限和原账目范围，软删附件不再返回其结果。人工确认额外需要账目编辑权限，复用 `RecordService.confirmOcr` 的完整草稿校验与快照。当前版本检查、明细替换、OCR_CONFIRM 前后快照和任务 CONFIRMED 状态在同一 READ_COMMITTED 事务提交，不自动提交审核或入账。识别时只有任务写入，账目本身不改变。单条配置查询读取一致的凭据，缺配置不标记外层任务事务回滚。

`OcrMySqlIntegrationTest` 在隔离 MySQL 容器验证原图保留、失败与手工补录、历史失败回滚、并发单次确认、重复调用、参数隔离和数据范围。云适配器解析使用官方响应格式夹具；尚未使用真实云账号调用。`ReceiptPanel.vue` / `RecordEditor.vue` 已接入人工确认，`OcrConfig.vue` 已接入管理员凭据配置，浏览器使用模拟服务验证交互。配置与接口说明见 `bookkeeping-ocr.md`。

## 当前开发边界

代码生成输出 7 份开发骨架，Controller、Service、Mapper 的业务 CRUD 仍需实现；字段配置目前只展示，表说明和确认标记可保存。ZIP 不会自动写入当前工程。

`sys_file` 为保留表，当前没有完整文件上传接口；统一防重复提交与新增业务的数据权限闭环按具体项目需要扩展。

模块文件、表、接口字段中文说明与排障入口见根目录 `PROJECT_MAP.md`；测试在独立本地校验库执行，避免混入开发库数据。

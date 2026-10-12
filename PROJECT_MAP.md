# 通用后台管理脚手架项目地图

> 2026-10-12 更新。用于后续业务开发和排障；“已确认”表示代码链路已核对，运行验证记录见 `changelog.md`。记账配置、草稿、私有票据、审核、历史、阿里云 OCR、财务工作台、报表和 Excel 导出已接入 Web 页面；独立 MySQL 与模拟 HTTP 浏览器验证已覆盖关键流程。开发库 V3/V4 与真实云联调仍待使用者完成。

## 一、项目总览

| 端 | 路径 | 技术栈 | 本地入口 | 来源 |
| --- | --- | --- | --- | --- |
| 后端 | `scaffold-system` | Java 17+、Spring Boot 3.5、MyBatis、MySQL、Redis、JWT | `8080`，接口前缀 `/api` | 已确认 |
| 管理后台 | `scaffold-vue` | Vue 3、TypeScript、Vite、Vue Router、GSAP，自有组件 | `5173`，开发代理 `/api` | 已确认 |

- Maven 模块：`scaffold-common`、`scaffold-framework`、`scaffold-system`、`scaffold-generator`、`scaffold-bookkeeping`、`scaffold-admin`。
- 包名前缀：`com.scaffold`；启动类：`scaffold-system/scaffold-admin/src/main/java/com/scaffold/admin/ScaffoldAdminApplication.java`。
- 配置：`scaffold-system/scaffold-admin/src/main/resources/application.yml`、`application-dev.yml`；前端代理：`scaffold-vue/vite.config.ts`。
- 数据库初始化：`scaffold-system/sql/V1__init_schema.sql`、`V2__seed_data.sql`；记账基线：`V3__bookkeeping_schema.sql`、`V4__bookkeeping_seed.sql`；开发工具：`scaffold-admin/src/main/java/com/scaffold/admin/util/DbInit.java`。
- 入口规范：`README.md`、`AGENTS.md`、`DESIGN.md`；建筑与工作流已移除。

## 二、业务术语词典

| 业务说法 | 代码 / 表 | 字段及中文含义 | 来源 |
| --- | --- | --- | --- |
| 后台账号 | `AuthUserRow`、`sys_user` | `username` 登录账号；`nickname` 姓名；`status` 启用状态；`deleted` 删除标记 | 已确认 |
| 角色 | `sys_role`、`sys_user_role` | `role_code` 角色编码；`builtin` 内置标记；`data_scope` 数据范围 | 已确认 |
| 菜单 / 按钮 | `MenuRow`、`sys_menu`、`sys_role_menu` | `menu_type` 为 `dir` 目录 / `menu` 页面 / `btn` 操作；`permission` 权限码 | 已确认 |
| 组织与岗位 | `sys_dept`、`sys_post`、`sys_user_post` | `parent_id` 上级部门；`ancestors` 上级链；`post_code` 岗位编码 | 已确认 |
| 字典 | `sys_dict_type`、`sys_dict_data` | `dict_type` 字典类型；`dict_label` 显示名称；`dict_value` 存储值 | 已确认 |
| 参数 | `sys_config` | `config_key` 参数键；`config_value` 参数值；`builtin` 内置标记 | 已确认 |
| 登录 / 操作审计 | `sys_login_log`、`sys_oper_log` | `login_time` 登录事件时间；`oper_time` 操作时间；`request_params` 脱敏参数 | 已确认 |
| 生成配置 | `gen_table`、`gen_table_column` | `table_name` 表名；`java_field` 属性名；`synced` 配置确认标记 | 已确认 |
| 审计字段 | 业务表公共列 | `create_by` 创建人；`update_by` 更新人；`create_time` 创建时间；`update_time` 更新时间 | 已确认 |

## 三、模块文件地图

下列后端业务路径均相对于 `scaffold-system`，前端路径均相对于 `scaffold-vue/src`。

| 后端模块 | Controller | Service | Mapper / 表 | 来源 |
| --- | --- | --- | --- | --- |
| 认证与路由 | `scaffold-system/src/main/java/com/scaffold/system/controller/AuthController.java` | 同包域 `service/AuthService.java`、`SystemPermissionResolver.java` | `mapper/AuthMapper.java`；用户、角色、菜单及关联表、登录日志 | 已确认 |
| 用户、角色、组织、菜单、字典、参数 | `scaffold-system/src/main/java/com/scaffold/system/controller/SystemManagementController.java` | `service/SystemManagementService.java` | `mapper/SystemManagementMapper.java`；各 `sys_*` 管理表 | 已确认 |
| 工作台 | `scaffold-system/src/main/java/com/scaffold/system/controller/DashboardController.java` | `service/DashboardService.java` | `mapper/DashboardMapper.java`；用户、角色、两类日志 | 已确认 |
| 日志查询、导出与清理 | `scaffold-system/src/main/java/com/scaffold/system/controller/MonitorController.java` | `service/SystemManagementService.java` | `mapper/SystemManagementMapper.java`；两类日志 | 已确认 |
| 日志统计与当日泳道 | 同上 | `service/MonitorStatsService.java` | `mapper/MonitorStatsMapper.java`；两类日志（只读） | 已确认 |
| 代码生成 | `scaffold-generator/src/main/java/com/scaffold/generator/controller/GeneratorController.java` | `service/GeneratorService.java` | `mapper/GeneratorMapper.java`；`information_schema`、两张生成配置表 | 已确认 |
| 记账基础配置 | `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/BookkeepingConfigController.java` | `service/BookkeepingConfigService.java` | `mapper/BookkeepingConfigMapper.java`；分类、支付方式、资金账户、项目表 | 已实现 |
| 账目草稿 | `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/RecordController.java` | `service/RecordService.java` | `mapper/RecordMapper.java`；`acct_record`、`acct_record_detail` | 已实现 |
| 私有票据附件 | `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/AttachmentController.java` | `service/AttachmentService.java`、`storage/LocalAttachmentStorage.java` | `mapper/AttachmentMapper.java`；`acct_record_attachment` | API 已实现，待数据库联调 |
| 账目审核与留痕 | `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/ReviewController.java` | `service/ReviewService.java`、`RecordHistoryService.java`、`domain/ReviewAction.java` | `mapper/ReviewMapper.java`；`acct_record_review`、`acct_record_version` | API 已实现，独立 MySQL 验证 |
| OCR 配置与任务 | `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/OcrController.java` | `service/OcrConfigService.java`、`OcrService.java`、`ocr/AliyunOcrProvider.java` / `AliyunOcrTransport.java`；系统 `SecretConfigService` | `OcrConfigMapper` / `OcrTaskMapper`；`sys_config`、`acct_ocr_task` | 后端与前端 API 已实现，真实云调用待凭据联调 |
| 记账业务（后续） | `scaffold-bookkeeping/src/main/java/com/scaffold/bookkeeping/controller/` | `service/` | 财务工作台、报表、服务端导出 | 待实现 |
| 健康检查 | `scaffold-admin/src/main/java/com/scaffold/admin/controller/HealthController.java` | 无独立业务服务 | 无数据库写入 | 已确认 |

| 前端功能 | 路由 | 页面文件 | API 文件 / 后端模块 | 来源 |
| --- | --- | --- | --- | --- |
| 登录（全幅画布、神经网络背景、时钟、服务健康） | `/login` | `views/Login.vue` + `components/NeuralNet.vue` | `api/auth.ts`、`portal.ts` 的 `health`；认证 | 已确认 |
| 工作台（主次布局 + 会话首次开场 + 模块网络卡） | `/dashboard` | `views/Dashboard.vue` | `api/portal.ts`；工作台；有日志权限时加载当日泳道 | 已确认 |
| 用户（列表 / 看板） | `/system/user` | `views/system/Users.vue` | `api/system.ts`；系统管理 | 已确认 |
| 角色（列表 / 权限矩阵 / 溯源·图谱舞台） | `/system/role` | `views/system/Roles.vue` + `components/business/PermissionTrace.vue` | 同上 + `permissions/graph` | 已确认 |
| 菜单（表格 / 导航星系·图谱舞台 + 侧栏预览） | `/system/menu` | `views/system/Menus.vue` + `components/business/MenuStarMap.vue` | 同上 | 已确认 |
| 部门（组织画布 / 表格，选中路径脉冲） | `/system/dept` | `views/system/Depts.vue` | 同上 | 已确认 |
| 岗位（列表 / 阶梯·图谱舞台） | `/system/post` | `views/system/Posts.vue` + `components/business/PostLadder.vue` | 同上；阶梯成员经 `users?postId=` | 已确认 |
| 字典（表格 / 样本册） | `/system/dict` | `views/system/Dict.vue` + `components/business/DictSwatchbook.vue` | 同上 | 已确认 |
| 参数（设置面板 / 表格） | `/system/config` | `views/system/Configs.vue` | 同上 | 已确认 |
| 操作日志（横带：概览 / 趋势或泳道 / 明细，抽屉级联） | `/monitor/oper-log` | `views/monitor/OperLog.vue` | `api/system.ts`；日志与统计 | 已确认 |
| 登录日志（横带：概览 / 趋势或热力 / 异常 / 明细） | `/monitor/login-log` | `views/monitor/LoginLog.vue` | 同上 + `login-logs/heatmap` | 已确认 |
| 代码生成（三步流水线抽屉） | `/tool/generator` | `views/tool/Generator.vue` + `components/business/GeneratorPipeline.vue` | `api/portal.ts`；生成器 | 已确认 |
| 账目列表 / 编辑 / 详情 / 票据 OCR | `/bookkeeping/records` | `views/bookkeeping/Records.vue` + `components/bookkeeping/RecordList.vue`、`RecordEditor.vue`、`RecordDetail.vue`、`ReceiptPanel.vue` | `api/bookkeeping.ts`；账目、附件、审核与 OCR | 页面已实现，模拟 HTTP 浏览器验证 |
| 审核中心 | `/bookkeeping/reviews` | `views/bookkeeping/Reviews.vue` + 共用 `RecordList.vue`、`ReviewActionDialog.vue` | 同上；审核、版本历史 | 页面已实现 |
| 记账配置 | `/bookkeeping/config` | `views/bookkeeping/Config.vue` | 同上；分类、支付方式、资金账户、项目 CRUD | 页面已实现 |
| 管理员 OCR 凭据 | `/bookkeeping/ocr-config` | `views/bookkeeping/OcrConfig.vue` | 同上；OCR 专用配置接口 | 页面已实现，凭据只写 |
| 无菜单 / 无匹配页面 | `/no-access` / 通配路径 | `views/NoAccess.vue`、`views/NotFound.vue` | 路由与会话状态 | 已确认 |

| 公共能力 | 文件入口 | 用途 |
| --- | --- | --- |
| 列表模板与数据状态 | `components/business/ListPage.vue`、`composables/list.ts` | 查询、重置、分页、导出、勾选、底部批量浮条及树列表；`altView` + `#alt` 换成画布 / 看板 / 矩阵 |
| 版式组件 | `components/business/OrgCanvas.vue`、`BoardView.vue`、`PermissionMatrix.vue`、`PostLadder.vue`、`PermissionTrace.vue`、`MenuStarMap.vue`、`DictSwatchbook.vue`、`GeneratorPipeline.vue`、`composables/view.ts` | 组织画布、看板分列（拖拽 + 键盘移动）、角色权限矩阵、岗位阶梯、权限溯源（三层下钻）、导航星系（三层下钻）、字典样本册、生成流水线、按页面记忆视图 |
| 图谱与问答 | `components/charts/RelationGraph.vue`、`composables/neural.ts`、`components/business/InsightPanel.vue`、`AskDock.vue`、`composables/ask.ts`、`composables/viewport.ts`、`components/NeuralNet.vue` | 通用关系图（弹簧布局变形、脉冲、扇形触须；`variant="stage"` 为珍珠玻璃图谱舞台）、舞台 Canvas 绘制件（珍珠球、玻璃刻度表盘、连线与脉冲、背景网络、轨道、台阶光柱）、分析栏、问答坞、本地规则问答引擎、共享视口平移缩放、登录神经网络背景 |
| 仪器化组件 | `components/ui/Metric.vue`、`Odometer.vue`、`SectionLabel.vue`、`SpecPlate.vue`、`DeltaPill.vue`、`Ruler.vue`、`HoverCard.vue` | 显示级读数、逐位滚动、编号眉标、规格铭牌、环比、刻度尺、悬停名片 |
| 图表 | `components/charts/TrendChart.vue`、`BarChart.vue`、`TickRing.vue`、`Swimlane.vue` | 自绘 SVG：形变折线与准星（稀疏数据茎叶点）、柱状、刻度环、时间轴泳道（默认聚焦有操作时段）；工作台数据权限为页内占比条 |
| 表单与引用选项 | `components/business/FormDialog.vue`、`DictSelect.vue`、`DeptTree.vue`、`DeptSelect.vue`、`UserSelect.vue` | 统一表单、字典、部门、用户选择 |
| 权限 | `components/business/PermissionButton.vue`、`directives/permission.ts` | 以当前用户权限控制操作显示 |
| 外壳 | `components/layout/AppShell.vue`、`Sidebar.vue`、`Topbar.vue`、`CommandPalette.vue` | 导航、移动侧栏、滚动凝结顶栏、命令面板（最近访问 / 页面 / 用户 / 动作）与账号操作 |
| 请求与会话 | `api/http.ts`、`api/session.ts`、`composables/auth.ts`、`router/index.ts` | 响应解包、存储、401 处理、动态路由与会话清理 |
| 外观与动效 | `composables/appearance.ts`、`motion.ts`、`focus.ts` | 主题、密度、GSAP、浮层层级与焦点 |
| 样式 | `styles/tokens.css`、`base.css`、`materials.css`、`components.css`、`instruments.css`、`layout.css`、`pages.css` | 顺序加载的七层样式 |

## 四、场景剧本

**登录与切换账号**：`Login.vue` → `authApi.login` → `AuthController.login` → `AuthService.login` → `AuthMapper` 校验账号、写登录日志 → `JwtService` 生成独立 token、`TokenStore` 写 Redis → `/auth/me` 取角色和权限 → `/auth/routes` 取菜单 → `router/index.ts` 注册页面；退出同步清除会话、菜单、路由和引用缓存，旧请求以会话版本判定是否仍可写回。

**新增用户**：`Users.vue` → `/system/users/options` 取引用选项 → `FormDialog` 校验 → `systemApi.users.create` → `SystemManagementController.createUser` → `SystemManagementService.createUser` → 用户表与角色、岗位关联表 → 列表重载；操作日志由公共切面记录。

**分配权限**：`Roles.vue` → 菜单树与角色菜单接口 → `PermissionTree` 勾选菜单 / 按钮、选择数据范围 → `PUT /system/roles/{id}` → 更新角色及关联表 → 重新登录加载新权限；停用或删除角色不能提供菜单与操作权限。

**泳道定位明细**：`Dashboard.vue` / `OperLog.vue` 的 `Swimlane` → `GET /monitor/oper-logs/timeline` → `MonitorStatsService.operTimeline` 按模块聚合 → 点击时间块以模块名写入操作日志关键词并滚到明细；工作台跳转通过 `pathOf('monitor/operlog/index')` 取当前账号可访问路径。

**看板改状态 / 矩阵改授权**：`BoardView` 拖到「停用」列 → 确认 → `PUT /system/users/{id}/status` → 重载；`PermissionMatrix` 本地累计改动 → 浮条「保存变更」→ 逐个角色 `PUT /system/roles/{id}`（仅带 `menuIds`）→ 重载矩阵；内置角色只读。

**字典维护**：`Dict.vue` → 类型表单 / 数据抽屉 → 类型与数据 CRUD → 两张字典表 → `invalidateDict` 更新公共下拉缓存；业务下拉仅请求启用选项接口。

**代码生成**：`Generator.vue` → 查询当前库 `information_schema` → 导入表与列信息 → 保存说明 / 确认标记 → 预览 7 个文件 → 生成并下载 ZIP；业务 CRUD 实现与代码接入仍由开发者完成。

## 五、API 快速索引

所有路径都以前缀 `/api` 开始；`{id}` 表示记录 ID，`{dictType}` 表示字典类型。

| 功能 | 方法与路径 | Controller | 来源 |
| --- | --- | --- | --- |
| 健康检查 | `GET /health` | `HealthController` | 已确认 |
| 验证码 / 登录 / 退出 | `GET /auth/captcha`、`POST /auth/login`、`POST /auth/logout` | `AuthController` | 已确认 |
| 用户、路由、修改密码 | `GET /auth/me`、`GET /auth/routes`、`PUT /auth/password` | 同上 | 已确认 |
| 工作台 | `GET /dashboard/summary`（含 `trendPrev`、`dailyOps`；`stats` 含 `menus` 菜单项、`depts` 部门数） | `DashboardController` | 已确认 |
| 权限溯源图 | `GET /system/permissions/graph` | `SystemManagementController`（`system:role:list`） | 已确认 |
| 用户 | `GET/POST /system/users`（列表支持 `postId` 岗位过滤）、`PUT/DELETE /system/users/{id}` | `SystemManagementController` | 已确认 |
| 用户引用 / 导出 / 状态 / 密码 | `GET /system/users/options`、`GET /system/users/export`、`PUT /system/users/{id}/status`、`PUT /system/users/{id}/password` | 同上 | 已确认 |
| 角色 | `GET/POST /system/roles`、`PUT/DELETE /system/roles/{id}`、`GET /system/roles/{id}/menus`、`GET /system/roles/export` | 同上 | 已确认 |
| 菜单 | `GET /system/menus/tree`、`POST /system/menus`、`PUT/DELETE /system/menus/{id}`、`GET /system/menus/export` | 同上 | 已确认 |
| 部门 | `GET /system/depts/tree`、`POST /system/depts`、`PUT/DELETE /system/depts/{id}`、`GET /system/depts/export` | 同上 | 已确认 |
| 岗位 / 参数 / 字典类型 | `GET/POST /system/posts`、`/system/configs`、`/system/dict/types`；各资源支持 `PUT/DELETE /{id}` 和 `GET /export` | 同上 | 已确认 |
| 字典数据 / 业务选项 | `GET /system/dict/data/{dictType}`、`POST /system/dict/data`、`PUT/DELETE /system/dict/data/{id}`、`GET /system/dict/options/{dictType}` | 同上 | 已确认 |
| 两类日志 | `GET /monitor/oper-logs`、`/monitor/login-logs`；各支持 `GET /export`、`DELETE /{id}`、`DELETE` 清空 | `MonitorController` | 已确认 |
| 登录热力 | `GET /monitor/login-logs/heatmap?days=7|30`（`cells` 星期×小时、`anomalies` 连续失败聚合） | 同上（`monitor:loginlog:list`） | 已确认 |
| 日志统计 / 泳道 | `GET /monitor/oper-logs/stats`、`GET /monitor/oper-logs/timeline`、`GET /monitor/login-logs/stats` | 同上 | 已确认 |
| 生成器表与导入 | `GET /tool/generator/tables`、`GET /tool/generator/importable-tables`、`POST /tool/generator/import` | `GeneratorController` | 已确认 |
| 生成器配置与输出 | `PUT /tool/generator/tables/{id}`；其后追加 `GET /columns`、`GET /preview`、`POST /generate`、`GET /download` | 同上 | 已确认 |
| 记账基础配置 | `GET/POST /api/bookkeeping/config/categories`、`PUT/DELETE /categories/{id}`；支付方式、资金账户、项目使用对应复数路径 | `BookkeepingConfigController` | 分类、支付方式、资金账户、项目的分页与 CRUD；写入需 `bookkeeping:config:edit` |
| 记账只读录入选项 | `GET /api/bookkeeping/config/options` | `BookkeepingConfigController` → `BookkeepingConfigService.options` → `BookkeepingConfigMapper` | 需 `bookkeeping:record:list`，返回有效配置；经办人 / 部门按数据范围，仅最少字段，不授予配置管理权限 |
| 账目草稿 | `GET/POST /api/bookkeeping/records`、`GET/PUT/DELETE /api/bookkeeping/records/{id}` | `RecordController` | 草稿明细校验、数据范围、实际发生时间排序、版本号并发控制 |
| 私有票据 | `GET/POST /api/bookkeeping/records/{recordId}/attachments`；`GET /api/bookkeeping/attachments/{id}/download`；`PUT /attachments/{id}/primary`；`DELETE /attachments/{id}` | `AttachmentController` | 需 `bookkeeping:attachment:view/upload/remove`；元数据和下载校验账目数据范围；只有可编辑账目允许修改附件 |
| 待审核列表 | `GET /api/bookkeeping/reviews/pending` | `ReviewController` → `ReviewService.pending` → `RecordService.list` | 固定待审核状态，复用查询、分页、发生时间排序及数据范围；需 `bookkeeping:review:list` |
| 审核动作 | `POST /api/bookkeeping/records/{id}/submit`、`/review/approve`、`/review/return`、`/withdraw`、`/void` | `ReviewController` → `ReviewService.act` → `ReviewMapper` / `RecordHistoryService` | 分别需 `bookkeeping:record:submit/review/return/withdraw/void`；当前版本号必传，退回 / 撤回 / 作废原因必填 |
| 审核与版本历史 | `GET /api/bookkeeping/records/{id}/reviews`、`/versions` | `ReviewController` → `RecordHistoryService` → `ReviewMapper` | 需 `bookkeeping:record:list`，按账目范围分页查询（包括软删记录）；版本附件信息额外需 `bookkeeping:attachment:view` |
| OCR 识别与查询 | `POST /api/bookkeeping/records/{id}/ocr`；`GET /api/bookkeeping/ocr/tasks/{id}`、`/records/{id}/ocr/tasks` | `OcrController` → `OcrService` → `OcrProvider` / `OcrTaskMapper` | 需识别与附件查看权限、账目范围；同步识别，原图保留，分页恢复任务，不返回 raw_result |
| OCR 人工确认 | `POST /api/bookkeeping/ocr/tasks/{id}/confirm` | `OcrService` → `RecordService.confirmOcr` → `RecordHistoryService` | 额外需账目编辑权限，复用完整草稿校验与版本检查；OCR_CONFIRM 快照及任务状态共用事务 |
| OCR 配置 | `GET /api/bookkeeping/ocr/config/status`、`PUT /api/bookkeeping/ocr/config` | `OcrConfigService` → 系统 `SecretConfigService` / `OcrConfigMapper` | 需专用权限+系统管理员角色；两项 AK 加密保存，通用参数查询/导出/写操作禁止访问 OCR 命名空间 |

记账前端 API 位于 `scaffold-vue/src/api/bookkeeping.ts`，通过 `src/api/index.ts` 导出；公共 `src/api/http.ts` 自动识别 `FormData`，复用登录凭据和统一错误处理。`auth.ts` 已注册 records / reviews / config / ocr-config 四个组件，未实现的 dashboard / reports 暂不注册。V4 执行后重新登录加载动态菜单。

`RecordResponse` 在响应边界把 `totalAmount`（总金额）和 `details[].amount`（明细金额）转为两位小数的字符串；历史快照以 BigDecimal 读取，`RecordVersionVO` 输出前复制并转换金额。服务和数据库继续使用 BigDecimal，前端 `utils/bookkeeping.ts` 用整数分计算并只提交允许写入的字段。

Web 验证入口：`scripts/verify-bookkeeping-web.cjs` 使用真实 Vue 页面、模拟 HTTP，覆盖两明细精确保存、私有预览、上传、OCR 确认、审核及权限，截图在 `output/playwright/`。启动与验证边界见 `docs/bookkeeping-web-guide.md`。

票据存储使用 `BOOKKEEPING_STORAGE_ROOT`（默认后端工作目录的 `./data/bookkeeping`）；`BOOKKEEPING_ATTACHMENT_MAX_BYTES` 默认 10485760 字节。此目录不是公开静态目录，已加入 Git 忽略。上传审计通过 `OperationLogAspect` 只记录文件元数据，API Key 等敏感字段脱敏。

草稿新增 / 修改 / 删除与审核动作都会追加 `acct_record_version`；审核动作额外追加 `acct_record_review`。写入使用主表行锁、条件更新和 `READ_COMMITTED`，状态、明细与快照共用事务。作废保留原明细与附件，不再处于 `POSTED`；报表后续统一只统计 `POSTED` 且未软删的记录。

数据库验证入口：`scripts/test-bookkeeping-review.ps1` 自动创建独立 MySQL 8.4 容器，检查目标库和表结构、执行 V1–V4 测试基线、运行记账与财务统计测试后清理临时容器。`ReviewMySqlIntegrationTest`、`ReportMySqlIntegrationTest` 默认跳过，只有显式测试 URL 指向 `127.0.0.1:<port>/bookkeeping_review_test` 时才运行，不读取开发库配置。开发库 V3/V4 尚未执行。财务工作台与报表复用现有 `views/Dashboard.vue` 展示结构及公共组件。

OCR 联调见 `docs/bookkeeping-ocr.md`。服务端环境变量 `BOOKKEEPING_SECRET_KEY` 为 Base64 编码的32字节 AES 主密钥；阿里云固定杭州 HTTPS 地址，凭据仅通过专用接口写入。`OcrMySqlIntegrationTest` 与审核测试使用同一独立容器，替换外部服务验证失败、确认回滚、重复请求、并发确认、软删附件及参数隔离；不产生真实云调用。

## 六、改动影响链

| 变更 | 必查位置 |
| --- | --- |
| 新增业务页面 | 业务表 / DTO → Mapper → Service → Controller → `src/api` → `ListPage` / `FormDialog` → `COMPONENT_MAP` → 菜单及角色授权 |
| 改权限 | `sys_role` / `sys_menu` / 关联表 → `AuthMapper`、`SystemPermissionResolver` → `@RequiresPermission` → `auth.ts`、`PermissionButton` → 低权限浏览器 / 接口回归 |
| 改数据范围 | `SystemDataScopeResolver` → `DataScopeCriteria` → 查询与导出 SQL → 用户筛选 / 分页；新增业务必须显式接入 |
| 改字段 | 初始化或后续迁移 SQL → Mapper 字段别名 → DTO 校验 / Service → API 类型 → 页面表单、表格与导出 |
| 改外观 / 公共交互 | 样式令牌或公共组件 → 代表性页面 → 浅色 / 深色 / 窄宽度 / 键盘 / 减少动效 |
| 改认证 | `JwtService`、`TokenStore`、认证过滤器 → `AuthService` → `session.ts`、`http.ts`、`auth.ts`、路由守卫 → 会话回归脚本 |
| 改生成模板 | `GeneratorService.renderFiles` → 全文件预览 → ZIP 内容 → 菜单 SQL 与当前 schema；骨架接口需业务开发者接入 |

## 七、症状定位表

| 症状 | 第一现场 | 排查重点 | 来源 |
| --- | --- | --- | --- |
| 切换账号后停留登录页 / 旧菜单仍可见 | `router/index.ts`、`composables/auth.ts`、`AppShell.vue` | 会话同步清理、旧请求版本、路由初始化取消、失效跳转 | 已确认 |
| 登录成功但没有菜单 | `AuthService.routes`、`AuthMapper`、`NoAccess.vue` | 角色启用状态、菜单授权、组件是否注册 | 已确认 |
| 菜单可见但接口返回 403 | `PermissionAspect`、`SystemPermissionResolver` | 当前角色 / 按钮权限、用户状态；引用选项使用专用接口 | 已确认 |
| 部门筛选漏掉下级 / 数据范围错误 | `SystemManagementMapper`、`SystemDataScopeResolver` | `ancestors`、部门范围、分页和导出是否同口径 | 已确认 |
| 时间差 8 小时 / 连接时区异常 | `application-dev.yml`、`DbInit` | Hikari 连接属性、`DB_TIMEZONE`、MySQL 会话 `NOW()` | 已确认 |
| 页面过渡空白 / 后台浮层未关闭 | `composables/motion.ts` | Vue 过渡完成回调、后台状态与减少动效终态 | 已确认 |
| 读数停在 0 / 动画停在中途 | `Odometer.vue`、`motion.ts` | 后台标签页 rAF 不触发：读数用定时器兜底，GSAP 补间在切到后台时走完 | 已确认 |
| 登录成功后停在对勾圆环 | `Login.vue` | 进入外壳的导航被取消（会话过期等）时须恢复登录按钮 | 已确认 |
| 抽屉内确认框被挡 / Escape 连带关闭 | `focus.ts`、`Modal.vue`、`Drawer.vue`、`Select.vue` | 打开顺序、公共层级、最上层焦点与事件 | 已确认 |
| 预览其他文件无代码 / 菜单 SQL 不匹配 | `GeneratorService`、`Generator.vue` | 每份文件的 `code`、当前菜单字段和类型 | 已确认 |

## 八、权限标识速查

| 模块 | 权限码 | 用途 |
| --- | --- | --- |
| 用户 | `system:user:list/add/edit/remove/resetPwd` | 查询导出 / 新增 / 编辑状态 / 删除 / 重置密码 |
| 角色、菜单、部门、岗位、字典、参数 | `system:role`、`system:menu`、`system:dept`、`system:post`、`system:dict`、`system:config`，后缀 `list/add/edit/remove` | 各模块查询导出与写入操作 |
| 日志 | `monitor:operlog:list/remove`、`monitor:loginlog:list/remove` | 查询导出 / 删除清理 |
| 代码生成 | `tool:gen:list` | 当前生成器全部接口 |
| 记账工作台与报表 | `bookkeeping:dashboard:view`、`bookkeeping:report:list` | 财务总览、每日收支、分类汇总 |
| 记账导出 | `bookkeeping:record:export` | 账目 / 财务报表 XLSX 下载；报表下载还需报表查看权限 |

- 超级管理员角色编码为 `super_admin`，后端操作权限直接放行，菜单按启用和显示状态读取。
- 业务字典选项仅需已登录；字典管理数据和写入仍使用对应权限。
- 用户引用选项要求 `system:user:list`；只读用户不接收角色、岗位及初始密码选项，也不接收部门联系人信息。
- 数据范围：`ALL` 全部、`DEPT_AND_CHILD` 本部门及下级、`DEPT` 本部门、`SELF` 本人、`CUSTOM` 自选部门。

## 九、特殊机制备忘

- Redis token key 前缀为 `auth:token:`；退出删除当前 token，每次登录的 JWT 带独立 ID。
- JWT 签名密钥读取必填环境变量 `JWT_SECRET`，UTF-8 编码至少 32 字节且不能全为空白；缺失或长度不足时阻止启动。
- 验证码保存在 Redis、验证时消费；当前开发底座接口返回四位字符供页面显示。
- `session.ts` 统一管理 localStorage / sessionStorage；会话清理两处都清，外观设置独立保留。
- `sessionVersion` 阻止旧菜单 / 用户请求写回；旧 token 的 401 不能清除新账号会话。
- 字典、部门缓存随账号切换清除；列表以请求序号避免晚到结果覆盖新查询。
- MySQL 时区以连接属性传递并强制到会话；不修改实例全局时区。
- 初始化 SQL 是新项目起点，不是旧库的自动升级迁移；`DbInit fix` 会删除整个目标库。
- 日志表、关联表、生成字段表不强制使用全部四个业务审计字段；`sys_file` 为保留表，当前没有上传业务接口。
- `composables/ask.ts` 是本地规则问答引擎：各模块注册 `intent{patterns, run}`，按 `hasPermission` 过滤不可用意图；命令面板输入 `?` 进入同一引擎。
- 视口平移缩放集中在 `composables/viewport.ts`（`useViewportPan`）：组织画布与关系图共用；`firstTimeInSession` 控制工作台开场等“每会话一次”的编排。
- 用户按岗位过滤走 `sys_user_post` 关联条件，分页、统计、导出一致生效并沿用 `@DataScope`。

<!-- MANUAL-NOTES-START -->
## 十、人工维护备注

后续项目的业务规则、特殊权限和人工定位说明可补充在此区域。
<!-- MANUAL-NOTES-END -->

## 十一、待核实与开发边界

| 项目 | 当前事实 | 后续处理 |
| --- | --- | --- |
| 生成业务 CRUD | 生成器输出文件骨架，尚未实现生成模块的完整 CRUD | 新业务接入时实现并单独验证 |
| 新业务数据权限 | 当前接入用户列表与导出，其他新增业务不会自动具备数据范围 | 按业务表的部门 / 创建人字段接入公共能力 |
| 文件业务与防重复提交 | 文件表已保留，统一上传及防重复提交能力未提供完整业务闭环 | 有实际需求时扩展公共能力 |
| 历史能力边界 | 早期版本包含当前已删除的建筑与工作流模块 | 历史变更见 `changelog.md`，当前范围查本地图与 README |

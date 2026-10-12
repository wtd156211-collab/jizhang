# Web 记账使用与本机验收

## 当前可用范围

Web 页面已接入“财务工作台”“账目列表”“审核中心”“报表与导出”“记账配置”“OCR 配置”，沿用现有珍珠玻璃主题、工作台 Bento 布局、指标、折线图、柱状图、刻度环、列表、弹窗、抽屉、权限按钮和选择组件。顶部“工作台”右侧可切换“财务工作台”。

## 在现有开发库启用

当前开发已启动的系统可以继续使用原数据库，无需重建。此次验证只执行了独立测试库的 V1–V4，没有自动给你的开发库添加记账表。

1. 在数据库软件中连接与后端当前配置一致的 MySQL，选择你已启动系统正在使用的数据库。先用 `SELECT DATABASE();` 核对库名，并查看 `sys_menu`、`sys_role_menu`、`sys_config` 及现有 `acct_` 表，确认目标和已执行版本。
2. 如果尚未执行记账 SQL，在同一目标库先执行 `scaffold-system/sql/V3__bookkeeping_schema.sql`，再执行 `V4__bookkeeping_seed.sql`。V3 创建记账表，V4 添加菜单、权限和最少配置数据。已经存在业务数据时不重跑 V1/V2，不使用 `DbInit fix` 重建。
3. IDEA 刷新根 Maven 工程 `scaffold-system/pom.xml`，重启 `ScaffoldAdminApplication`；VS Code 打开 `scaffold-vue`，在终端运行 `npm run dev`。前端通过现有 `/api` 代理调用后端。
4. 退出并重新登录，加载新动态菜单。`admin` 可访问首批页面；普通账号在角色管理中授予相应记账菜单和按钮权限，并核对数据范围。

首次从空库启动时依次执行 V1、V2、V3、V4；以上步骤针对已成功启动脚手架的开发库。SQL 文件中的 `CREATE TABLE IF NOT EXISTS` 不能修补已存在的旧结构；若你曾执行旧版本 V3，先对照字段，按差异迁移，不能依靠再次执行脚本自动升级。

## 阿里云 OCR 准备

1. 开通阿里云 OCR 统一识别，为 RAM 账号授予 `ocr:RecognizeAllText` 权限，准备 AccessKey ID / Secret。
2. 在 IDEA 后端运行配置的环境变量中设置 `BOOKKEEPING_SECRET_KEY`：随机32字节的 Base64 字符串。它是服务端加密主密钥，与阿里云凭据不同，需固定保存，重启使用同一值。缺少它时手工记账仍可使用，OCR 凭据不能保存 / 使用。
3. 可以在本机 PowerShell 中生成一次主密钥后，自己复制到 IDEA 运行配置并妥善保存：

```powershell
$bookkeepingBytes = New-Object byte[] 32
$bookkeepingRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bookkeepingRng.GetBytes($bookkeepingBytes)
$bookkeepingRng.Dispose()
[Convert]::ToBase64String($bookkeepingBytes)
```

4. 重启后端，登录系统管理员账号，进入“记账管理 → OCR 配置”，启用阿里云，填写两项 AK 并保存。ID 和 Secret 加密保存，页面只显示脱敏后缀；留空保留旧值，成功后输入清空。无需到通用“系统参数”中设置 AK。

票据目录由 `BOOKKEEPING_STORAGE_ROOT` 指定，默认后端工作目录的 `./data/bookkeeping`。保持启动工作目录稳定；如需固定路径，可在 IDEA 环境变量中设置一个绝对路径。原始文件私有保存，预览经鉴权下载，关闭预览后释放临时 URL。

本地配置就绪只表示主密钥和凭据可用，实际识别还依赖阿里云授权、额度和网络；本次未调用真实收费云接口。详细字段与边界见 [OCR 联调说明](bookkeeping-ocr.md)。

## 第一笔账目的操作

1. 在“记账配置”核对分类、支付方式和资金账户，可按实际业务维护项目。
2. 在“账目列表 → 新增账目”填写摘要、实际发生日期、收支类型、支付方式、账户和明细。每条明细至少指定经办人、部门或项目之一。时间不确定就留空，不会补为午夜。
3. 总金额必须等于明细合计。保存草稿后，在详情上传主票据或补充票据，PNG / JPEG / PDF 最大10MB；无需票据也可以手工记账。
4. 如需 OCR，选择识别类型，点击票据旁“识别 → 核对并填写”。核对日期、时间、摘要和金额，手工选择分类及归属，调整明细合计后保存。确认仍为草稿，不会自动提交审核或入账。
5. 核对完成点击“提交审核”，审核人员在“审核中心”通过或填写原因退回。通过后为已入账；修改需“撤回重审”后编辑，或填写原因作废。
6. 账目详情查看审核历史、版本留痕及前后变化。所有审核动作带版本校验，出现冲突时刷新后再操作。

## 已验证与待验收

自动验证覆盖：记账116项（含17项真实 MySQL）、公共4项、框架8项；前端31项、类型检查及生产构建、完整后端打包。独立数据库脚本先核对库和表结构，结束自动清理。

浏览器使用真实 Vue 组件和模拟 HTTP，检查1440px桌面、390px窄屏及深色；验证两明细精确保存、空时间、连续编辑空字段重置、票据上传与鉴权预览、OCR 人工确认、提交 / 通过 / 撤回原因及只读权限隐藏。它不等于真实登录、开发库与阿里云端到端验收；真实环境仍需按上述操作跑一笔完整账目，特别检查退回重提、作废及配置维护。

可重跑独立数据库验证：

```powershell
# 项目根目录，需 Docker Desktop 和 Maven
.\scripts\test-bookkeeping-review.ps1
```

可重跑页面验证：

```powershell
# scaffold-vue 终端
npm run dev -- --host 127.0.0.1 --port 5176 --strictPort
# 另一个项目根目录终端，需本机已安装 Playwright 及 Chrome
$env:BOOKKEEPING_PLAYWRIGHT_PATH = '<本机 playwright 模块绝对目录>'
node scripts\verify-bookkeeping-web.cjs
```

截图和检查结果在 `output/playwright/`，被 Git 忽略；脚本仅写模拟接口内存和本机验证产物，不连接开发库或真实云服务。

财务工作台默认展示最近30天，支持今天、7天、30天和自定义日期；报表页展示每日收支、分类汇总和状态，列表与报表均提供服务端 Excel 下载。统计按实际发生日期，仅计未删除的 `POSTED` 账目；待审核等状态只作为进度计数，不计入收支金额。金额在接口和 Excel 中使用十进制文本以保留精度。

日期和时间已改为主题内置的 `DatePicker` / `TimePicker`：日期使用本地月历，时间使用时、分、秒三列，时间不确定时可以清空并保持“时间未记录”，不会补成午夜。

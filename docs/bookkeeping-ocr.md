# 记账 OCR 配置与联调

OCR 后端及 Web 页面已接入。用户已选定阿里云，使用官方 Java SDK `com.aliyun:ocr_api20210707:3.1.3` 的 `RecognizeAllText`，通过二进制文件流上传已保存的原票据，不生成公网图片地址。没有有效凭据时仍可使用手工录入。管理员入口为“记账管理 → OCR 配置”，账目详情提供上传、预览、识别和人工核对。

## 本地准备

1. 开通阿里云 [OCR 统一识别](https://help.aliyun.com/zh/ocr/developer-reference/api-ocr-api-2021-07-07-recognizealltext)。创建 RAM 用户并授予 `ocr:RecognizeAllText` 的调用权限，取得 AccessKey ID 和 AccessKey Secret。
2. 后端配置一个固定的 `BOOKKEEPING_SECRET_KEY` 环境变量，值为随机32字节的 Base64 编码。建议使用密码管理器生成、保存；重启后保持同一值。不要写进代码、SQL 或 Git 文件。IDEA 启动时在 Run Configuration 的 Environment variables 中填写；在终端设置的进程变量需要在同一终端启动后端才能继承。
3. 开发库需按已有初始化流程执行 V3/V4。此次开发只在独立测试容器执行 SQL，没有初始化开发库。
4. 重启前后端、重新登录加载菜单。使用具有 `bookkeeping:ocr:config` 权限、且拥有启用的 `super_admin`（超级管理员）或 `sys_admin`（系统管理员）角色的账号，在“OCR 配置”页面启用阿里云并保存两项凭据。页面留空保留旧值，保存后清空输入，清空凭据需二次确认。通用参数页面不能查看、导出、创建、修改或删除 `bookkeeping.ocr.*` 参数。完整操作见 [Web 使用说明](bookkeeping-web-guide.md)。

下面使用占位值说明接口格式。不要在聊天、日志或共享请求文件中填写真实凭据。

```json
{
  "provider": "aliyun",
  "endpoint": "",
  "accessKeyId": "<AccessKey ID>",
  "apiKey": "<AccessKey Secret>"
}
```

`provider` 为服务标识：`aliyun`（阿里云）或 `disabled`（停用）；`endpoint` 为接口地址，阿里云固定为 `https://ocr-api.cn-hangzhou.aliyuncs.com`，可留空使用默认值；`accessKeyId` 和 `apiKey` 分别表示 ID 与 Secret，均加密存储。省略或空字符串保留原凭据；`clearApiKey: true` 同时清空两项凭据，不允许与替换操作混用。每项凭据最多256个非空白 ASCII 字符，密文适配现有 `sys_config.config_value VARCHAR(500)`，不需要改表。

查询状态仅返回配置是否存在、主密钥是否就绪以及末尾四字符的脱敏值；四字符以内的值全部隐藏。`recognitionAvailable` 表示本地配置可用，不代表云账号已开通、权限正常、额度充足或网络通畅。如果更换服务端加密主密钥，旧凭据无法解密，需要重新填写两项凭据；清空操作不需要旧主密钥。

## 接口与字段

所有路径以 `/api/bookkeeping` 开头，使用已有登录凭据与统一 `R<T>` 返回结构。

| 方法与路径 | 用途 | 权限和边界 |
| --- | --- | --- |
| `GET /ocr/config/status` | 管理员查看配置状态 | `bookkeeping:ocr:config` + 系统管理员角色；`no-store` |
| `PUT /ocr/config` | 保存/替换/清空配置 | 同上；凭据只写，不回显、不记录在操作日志 |
| `POST /records/{id}/ocr` | 识别该账目的已保存票据 | `bookkeeping:ocr:recognize` + `bookkeeping:attachment:view`，账目数据范围、可编辑状态与版本校验 |
| `GET /ocr/tasks/{id}` | 查看单次任务结果 | 识别与附件查看权限、账目数据范围；不返回原始云响应或存储键 |
| `GET /records/{id}/ocr/tasks` | 页面刷新后恢复识别历史 | 同上；分页，默认10条、最多100条，最新在前 |
| `POST /ocr/tasks/{id}/confirm` | 保存人工核对的完整草稿 | 额外需 `bookkeeping:record:edit`；当前版本号必填，只允许成功且未确认的任务 |

识别请求：`attachmentId`（原票据附件 ID）、`version`（当前账目版本号）、`documentType`（识别类型，默认 `Invoice`）。

| documentType | 中文用途 | 首版建议字段 |
| --- | --- | --- |
| `Invoice` | 增值税发票 | 销售方摘要、价税合计、开票日期 |
| `ShoppingReceipt` | 购物小票 | 店名、实付金额、日期及明确识别的时间 |
| `PaymentRecord` | 支付详情截图 | 商品说明、合计金额、支付日期及时间 |
| `Advanced` | 通用文字高精识别 | 识别文本，金额与时间人工填写 |

金额、日期和时间是建议：开票日期不一定等于实际发生日期，须人工核对。无法可靠解析的字段不自动填写；缺失时间保留为空，不补午夜。不自动推断分类、经办人、部门、项目、支付方式或资金账户。

确认请求直接使用现有完整 `RecordWriteRequest` 草稿格式（含 `version` 和明细），复用总额/明细合计、引用配置、数据范围等校验。成功后仅增加账目版本并记录 `OCR_CONFIRM` 快照与任务来源 ID，状态仍为原来的 `DRAFT`（草稿）、`RETURNED`（退回）或 `WITHDRAWN`（撤回重审）；提交审核与入账使用独立接口。

任务 `status` 为状态：`SUCCEEDED`（识别成功）、`FAILED`（失败）、`CONFIRMED`（人工确认）。`normalizedResult` 为可编辑建议，附 `sourceVersion`（识别时版本）及 `documentType`（识别类型）；`errorMessage` 为固定脱敏提示。`raw_result` 保存脱敏后的原始成功响应，仅后端保留；软删附件后不再通过任务接口读取其识别内容，原文件和历史仍保留。

## 首版执行与验证边界

采用同步识别：云调用连接超时5秒、读取超时15秒，关闭 SDK 自动重试，持有同一账目行锁防止附件/审核动作同时改变原数据。成功完成后的10秒内，相同用户、账目、附件、版本、服务商和类型的重复请求复用结果。独立账目可并发操作；同一账目等待识别完成后再编辑。以后需求增长时再拆分后台队列。

支持已保存的 PNG/JPEG/PDF，最大10MB；PDF 首版仅使用 `Invoice`，识别第一页。一次识别多张票据的结果要求人工处理，不自动合并。原始响应解析后限制1MB、展示文本最多10000字符；SDK 接收响应时的内存分配仍由 SDK 管理。

失败记录不保存可能包含凭据的异常消息，也不删除附件。确认事务中，账目、明细、版本快照与 `CONFIRMED` 状态一起提交，失败一起回滚。

独立数据库验证：在项目根目录运行 `./scripts/test-bookkeeping-review.ps1`。脚本先检查新建测试库和表结构，再执行 V1–V4 并运行审核/OCR 测试，结束后清理容器。OCR 集成测试替换外部服务，使用真实 MySQL、事务和本地原文件。浏览器模拟 HTTP 验证了原图预览、识别、人工确认、凭据保存后清空输入及权限显示；尚未使用真实阿里云凭据调用服务，真实开发库全链路仍待联调。

接口依据：[阿里云 RecognizeAllText 文档](https://help.aliyun.com/zh/ocr/developer-reference/api-ocr-api-2021-07-07-recognizealltext)及[官方 Java SDK](https://github.com/aliyun/alibabacloud-java-sdk/tree/master/ocr-api-20210707)。

# Webhook 端点地址错误码技术方案

> 状态：阶段 2—3 评审稿（2026-10-17）。需求见 [requirement.md](./requirement.md)，范围：3 个错误码，只改 Operations 路径。

## 1. 现状与差异

| 环节 | 现状 | 本次 |
|---|---|---|
| Platform 地址校验 | Operations 新建与修改（`CreateWebhookEndpointProcessor:47`、`UpdateWebhookEndpointProcessor:37`）都调用 `WebhookManagementSupport.requirePublicUrl`（`:132`）：<br>1. `WebhookEndpointUrl.parse` 拒绝时抛 `InvalidParameterException`，对应 `INVALID_REQUEST`，情形包括为空、超长、格式不对、协议不允许、无主机名、带用户信息或片段、localhost。<br>2. 之后 `guard.resolve` 拒绝时抛 `BizCheckFailException`，对应 `BUSINESS_CHECK_FAILED`。 | 只在 `requirePublicUrl` 内改为抛 `FailException(WebhookEndpointErrorCode.*)`。`WebhookEndpointUrl` 本身不改，Client Console 的 `ClientWebhookSupport` 也不动 |
| Platform 状态映射 | `FailException` 默认是 400（`MvcProblemExceptionMapper:135`） | 不用登记状态映射 |
| BFF 解码 | Webhook 客户端复用共享的 `operationsConfigurationPlatformProxyFactory`（`PlatformClientConfiguration:61-65`），只登记 Operations 和 Gateway 两类错误码。Platform 一旦返回新错误码，BFF 会当作未知错误码，变成 502 | Webhook 客户端改用单独的 proxy，额外登记 `WebhookEndpointErrorCode.values()`。字段校验投影仍开启，做法同 `ReviewFileApi`（`:133-138`） |
| BFF 状态映射 | `OperationsErrorStatusResolver` 未覆盖的错误码按默认 400 返回 | 不改 |
| Web 展示 | `session.ts` 的 `ApiError` 只有在 400 `BUSINESS_CHECK_FAILED`/`INVALID_REQUEST`、409 `CONCURRENT_REQUEST` 时才使用服务端说明。`webhook.ts:262` 的 `urlErrorMessage` 用正则匹配 `detail` | `urlErrorMessage` 改为按 code 查本地文案表，把文案显示在 `url` 字段下面；删掉正则 |

**发布顺序：** Platform 必须和 BFF 同时发布，或晚于 BFF。若 Platform 先上线新错误码而 BFF 仍是旧版，地址被拒会从 400 变成 502。mor-test 按 BFF → Platform → Web 的顺序替换。

## 2. 接口与错误码

新增 `platform-support-api/.../enums/WebhookEndpointErrorCode`（`implements CodeMessageEnum`，枚举常量之间空一行）：

| code | message（安全，可直接展示） | 触发 |
|---|---|---|
| `WEBHOOK_URL_NOT_ALLOWED` | 地址格式不正确或协议不允许 | `WebhookEndpointUrl.parse` 拒绝。情形包括协议不在允许列表、无主机名、带用户信息或片段、localhost、格式非法。为空和超长已先由请求体校验返回 422，这里一并兜底 |
| `WEBHOOK_URL_UNRESOLVABLE` | 端点主机名无法解析 | `guard.resolve` 返回 `DNS_FAILED` |
| `WEBHOOK_URL_NOT_PUBLIC` | 端点地址指向内网或保留地址 | `guard.resolve` 的其他拒绝 |

- 状态码均为 400。不新增接口，请求体与响应体不变。
- `WebhookManagementApi` 的接口说明补充这 3 个错误码。
- `requirePublicUrl` 只捕获 `parse` 抛出的 `InvalidParameterException` 并转换，其他校验的异常不受影响。原异常作为 cause 保留，便于日志排查。

## 3. 各系统改动

| 系统 | 文件 | 改动 |
|---|---|---|
| Platform API | `enums/WebhookEndpointErrorCode.java`（新）、`WebhookManagementApi.java`（注释） | 错误码定义 |
| Platform Web | `WebhookManagementSupport.java` | `requirePublicUrl` 抛新错误码 |
| Platform Test | `WebhookEndpointManagementTest` | `test_invalidParameter`：`ftp://` 与带用户信息的地址断言 `WEBHOOK_URL_NOT_ALLOWED`。`test_businessRuleViolation`：内网地址断言 `WEBHOOK_URL_NOT_PUBLIC`。新增无法解析的主机名断言 `WEBHOOK_URL_UNRESOLVABLE`，测试用保留的 `.invalid` 域名。修改端点同样覆盖一例。Client Console 测试不改，以证明该路径行为不变 |
| BFF | `PlatformClientConfiguration.java` | `webhookManagementApi` 改为 `platformProxy(environment, discovery, testBaseUrl, true, WebhookEndpointErrorCode.values())` |
| BFF Test | `WebhookManagementBrowserAutoTest` + `webhook-management-boundary.json` | 3 个错误码经 BFF 返回 400，`detail` 为枚举说明，不含上游 requestId 和上游 detail canary。未登记的 `BUSINESS_CHECK_FAILED` 仍显示"业务校验未通过"（已有用例） |
| Web | `webhook/webhook.ts`、`webhook.test.ts` | `urlErrorMessage(error)`：仅当 status 为 400 且 code 是这 3 个之一时，返回本地文案（与枚举说明一致），否则返回 `null`。测试改为按 code 断言，并覆盖"`BUSINESS_CHECK_FAILED` 即使带地址相关的 detail 也不映射" |

不涉及数据库（无 SQL）、Gateway 路由与权限、页面布局。

## 4. 验证计划

| 阶段 | 内容 |
|---|---|
| 4 | Platform Webhook 测试类、BFF `verify`、Web `npm test` 与 `npm run build` |
| 5 | 本机：重启 Platform 与 BFF，`local_acceptance.py` 的 AC-04 改为断言新错误码；浏览器在"修改"弹窗输入内网地址，确认地址字段下显示原因、弹窗保持打开、不弹通用通知（新增截图） |
| 6—7 | mor-test：与 Operations Webhook 管理相同的干净 worktree 构建方式，基线为当时 mor-test 的运行版本 + 本需求差异；替换顺序 BFF → Platform → Web；经运营入口复验内网地址与 http 地址两种情形。具体在阶段 6 发布清单中确定 |

## 5. 风险

| 风险 | 处理 |
|---|---|
| Platform 先于 BFF 发布导致 502 | 固定发布顺序；本机与 test 都先替换 BFF |
| Client Console 以后要用同样的错误码 | 枚举在 `platform-support-api`，Client BFF 登记后即可，由 Client Console 会话另行处理；本需求完成后告知 code-mor-76 |

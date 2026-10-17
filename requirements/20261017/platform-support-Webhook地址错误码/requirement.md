# Webhook 端点地址错误码需求

> 状态：阶段 1 已确认（2026-10-17，全部按建议）。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

[Operations Webhook 管理](../../20261016/operations-console-Webhook管理/requirement.md) 的 AC-04 要求：端点地址被拒时，原因显示在地址字段下。阶段 5 发现这一点做不到，作为遗留项记录在[交付记录](../../20261016/operations-console-Webhook管理/delivery.md)。2026-10-17 用户选择"只给 Webhook 加专用错误码"。

当前事实（2026-10-17）：

| 项 | 现状 |
|---|---|
| Platform 拒绝方式 | Operations 与 Client Console 两条路径的 `requirePublicUrl` 都抛 `BizCheckFailException`，`detail` 写"端点主机名无法解析"或"端点地址指向内网或保留地址"，code 统一是 `BUSINESS_CHECK_FAILED`。代码位置：`WebhookManagementSupport.java:137`、`ClientWebhookSupport.java:122`。协议不允许、格式不对、localhost 由 `WebhookEndpointUrl.parse` 抛 `InvalidParameterException`，code 是 `INVALID_REQUEST` |
| 内部 HTTP 客户端 | Foundation `HttpServiceClientInterceptor` 只按 code 判断。`BUSINESS_CHECK_FAILED` 的说明一律换成"业务校验未通过"，`INVALID_REQUEST` 一律换成"请求参数不正确"。只有调用方显式登记的错误码，才保留该错误码自己的固定说明；未登记的未知错误码变成 502 |
| 现行规则 | [2026-09-06 错误响应决定](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)：BFF 不把上游原文透传给浏览器，只转换已登记的安全错误码；前端只按 code 分支，不解析 `detail` |
| 页面 | 运营后台只弹出通用错误通知（code + requestId）。`webhook.ts` 里按 `detail` 文字匹配地址错误的正则，经过 BFF 后永远匹配不到，而且本身违反"前端不解析 detail"的规则 |

## 2. 目标

地址被拒时，运营人员在地址字段下直接看到原因，例如"地址指向内网或保留地址"，不用再拿 requestId 去查日志。做法遵守现行错误规则：不透传上游原文，只增加登记过的错误码。

## 3. 本期范围（建议）

1. **Platform 新增错误码**：在 `platform-support-api` 新增 `WebhookEndpointErrorCode`，状态码都是 400，说明固定且可以直接展示：
   - `WEBHOOK_URL_UNRESOLVABLE`：端点主机名无法解析
   - `WEBHOOK_URL_NOT_PUBLIC`：端点地址指向内网或保留地址
   - `WEBHOOK_URL_NOT_ALLOWED`：地址格式不正确，或协议不在允许范围内（含 localhost、带用户信息或片段）

   哪些路径改用这些错误码，见 Q-02。
2. **Operations BFF**：Webhook 客户端登记这 3 个错误码。浏览器收到的仍是 BFF 自己生成的 Problem，带新 code 与固定说明。
3. **Operations Web**：按 code 把说明显示在"接收地址"字段下，弹窗保持打开；删掉按 `detail` 文字匹配的正则。
4. **测试**：Platform 测试断言新错误码；BFF 测试断言这 3 个 code 能透出、未登记的上游原文仍被隐藏；Web 单测覆盖 code 到字段的映射。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| 全局透出业务说明 | 不改 Foundation 客户端，也不改 2026-09-06 决定 |
| 其他 Webhook 业务拒绝 | 端点数量上限、无变化、Replay 不允许等继续用 `BUSINESS_CHECK_FAILED`。页面已有前置提示：满 5 个时按钮不可用、只对已结束的投递显示 Replay |
| 发送时的地址拒绝 | 投递 / 测试事件发送时 DNS 校验失败，记为尝试结果码（已有），不受影响 |

## 5. 待确认问题

| 编号 | 问题 | 选项 | 建议 |
|---|---|---|---|
| Q-01 | 错误码粒度 | A：上面 3 个；B：合并成 1 个 `WEBHOOK_URL_REJECTED`，说明写成"地址不可用：须为可解析的公网 https 地址" | A。三种情况的处理方法不同：改协议、换公网地址、检查域名 |
| Q-02 | Client Console 路径 | A：只改 Operations 路径（`WebhookManagementSupport`），Client Console 路径保持现状；B：两条路径一起改。Client BFF 必须同时登记这 3 个错误码，否则 Client 页面会收到 502；Client BFF / Web 归 Client Console 会话（code-mor-76），需要和它协调 | A。错误码定义在 `platform-support-api`，Client Console 可以另行接入。B 需要两个 BFF 同时发布 |
| Q-03 | 适用阶段 | 1 范围；3 方案与接口（文字说明，不出 Pencil 画板，页面只是字段下多一行错误说明）；4 研发；5 本机验收；6—7 mor-test 发布 Platform、BFF、Web；8 提交 | 按左列。阶段 2 与阶段 3 合并 |

## 6. 验收标准（草案）

| 编号 | 标准 |
|---|---|
| AC-01 | 新建或修改端点时，内网地址、无法解析的主机名、http（在只允许 https 的环境）分别返回对应错误码与固定说明，HTTP 400 |
| AC-02 | 运营页面在"接收地址"字段下显示对应说明，弹窗保持打开；其他业务错误仍是通用通知 |
| AC-03 | BFF 响应不含上游 requestId 与上游 detail 原文；未登记的错误码行为不变 |
| AC-04 | Client Console 路径按 Q-02 的选择处理：选 A 时行为不变 |
| AC-05 | Platform、BFF、Web 测试全部通过；mor-test 经运营入口复验 AC-01、AC-02 |

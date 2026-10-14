# Payment 经 Platform 对接 PayerMax 卡直付

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14）
- 主入口 / Owner：Payment Orchestration（Execution、Card、Channel）与 Platform Support（外部 API 执行）
- 关联：[自建 Card Vault](../../20260912/payment-自建CardVault/requirement.md)、[交易级工具修订](../../20260913/payment-Execution设计/instrument-token-amendment.md)、[状态与渠道恢复修订](../../20260913/payment-Execution设计/execution-state-channel-recovery-amendment.md)、[Platform 执行决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)、[账号材料交接](../../20260914/platform-support-外部API配置与执行/material-handoff.md)、[PayerMax 收银台接入](../platform-support-PayerMax接入/requirement.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景与决定

用户 2026-10-14 决定：先打通 Payment 支付链路；**卡支付走渠道的服务端卡 API（PayerMax Direct API - CARD），不使用渠道收银台**；PCI DSS 认证一定会取得。

讨论中确认的前提：

- 我们的收银台在用户打开时就要决定采集方式。本需求只做“在我们页面采集卡 → 服务端直传渠道”这一种；渠道收银台模式（`integrate=Hosted_Checkout`）不进入 Payment 链路，已配置的 `PAY`/`PAY_QUERY` 收银台实现保留为渠道验证资产，后续按本需求改造。
- PayerMax 直付要求商户持有 PCI DSS 认证并向 PayerMax 提交材料；本需求的开发与验证只用测试卡，**上生产以 PCI 认证与 PayerMax 开通为前提**，不在本需求内完成。

## 2. 现状与冲突

| 事实 | 来源 |
|---|---|
| Payment `PayActionActor`、`PayRiskCheckActionActor` 等全部是 TODO（抛错 → Action `UNKNOWN`）；Payment 没有任何调用 Platform 的客户端 | `paytech-payment-execution/.../core/action/*ActionActor.java` |
| Card 只有合成卡 Mock（`payment.card.mock-enabled`），无真实存储、无 CVV 暂存、无向渠道传卡的能力 | `paytech-payment-card`；[交易级工具修订](../../20260913/payment-Execution设计/instrument-token-amendment.md)第 17、26 行 |
| Card 规则：PAN 解密与向渠道传卡只在 Card 受控范围内；CVV 不落任何持久介质；普通 Channel 适配器不接收 PAN/CVV | [Card 需求](../../20260912/payment-自建CardVault/requirement.md) CARD-01/04；修订第 25 行 |
| Platform 规则：标准 API `input` 不得包含原始卡数据；“需要卡原值的渠道另行设计” | [内部接口](../../20260914/platform-support-外部API配置与执行/internal-api.md)第 66 行；[材料交接](../../20260914/platform-support-外部API配置与执行/material-handoff.md)第 6 行 |
| 渠道协议执行统一由 Platform 承担，Payment 不再自己实现渠道协议 | [Platform 执行决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md) |
| Platform 内部接口沿用完整 Body 日志（W5 暂缓）；沙箱验证证据保存去敏报文，目前只遮认证类 Header，不遮 Body | [内部接口第 6 节](../../20260914/platform-support-外部API配置与执行/internal-api.md#6-日志与数据处理) |
| Foundation 日志组件支持按路径前缀只记元数据（`web.logging.metadata-only-path-prefixes`） | `paytech-mvc-web-starter` `WebLoggingProperties` |
| Payment 支付链路以 `PAY_RISK` 开始，Risk 尚无支付风控接口 | `CardPayFlow`、`PayRiskCheckActionActor` |
| Payment 没有恢复任务（无补偿 Handler、无 Job），`PayResponse` 只有 `attemptId`、`status`，不能返回 3DS 跳转 | 同上；`PayResponse.java` |

**核心冲突：**“渠道协议在 Platform 执行”与“卡原值只在 Card 内、不进 Platform”不能同时成立。用户 2026-10-14 决定：服务间只传卡 Token，Platform 凭 Token 读取卡数据，只在内存中存在、不打印日志（技术方案第 2 节）。

## 3. 目标

1. **卡数据通道**：服务间只传卡 Token；Platform 执行时向 Payment Card 读取卡数据，只在内存中以受控句柄存在（不落库、不进日志、不进证据与响应）。
2. **Platform**：标准 API `PAY` 支持卡支付（含 3DS 跳转指令），PayerMax 实现改为 `Direct_Payment` 卡直付；`PAY_QUERY` 不变。
3. **Payment**：`PAY` Action 经 Platform 执行，按结果推进 Action/Attempt/Intent；3DS 时把跳转指令临时返回调用方。恢复（渠道通知 + 重试）本期不做。
4. **验证**：Payment `auto` 测试（Platform、Risk 用测试 HTTP Mock）；本机端到端：Payment → Platform → PayerMax UAT，用测试卡完成一次免 3DS 成功支付，并验证 3DS 场景返回跳转指令。

## 4. 业务场景

| 场景 | 期望 |
|---|---|
| 卡支付直接成功 | Attempt `SUCCEEDED`，Intent `SUCCEEDED`；记录渠道交易引用（PayerMax `tradeToken`） |
| 需要 3DS | pay 返回 `PROCESSING` 与临时 `nextAction`（跳转地址）；认证后的推进属于恢复，本期不做 |
| 渠道明确拒绝（卡被拒、参数错误等） | Action `FAILED`，Attempt `FAILED`，Intent 释放活动 Attempt，可用新提交重试 |
| 超时、系统繁忙、验签失败 | Action `UNKNOWN`，保持原状（恢复本期不做）；不重发扣款、不重读 CVV |
| 同一提交重放 | 返回原 Attempt 状态，不重复扣款（沿用现有规则） |
| 卡材料缺失（CVV 已领取或过期） | Platform 读卡失败 → 503，保证未发出渠道请求；Action `FAILED` |

## 5. 本期范围（建议，见技术方案第 9 节阶段）

- Platform：`PAYMENT_CARD` 读卡初始化函数与 Payment 客户端、卡字段受控句柄、证据遮蔽、保存时校验；`PAY` 标准 API 字段扩展；PayerMax `ORDER_AND_PAY` 改为卡直付；`auto` 测试。
- Payment：读卡接口与 Card Mock（内存测试卡）；Channel 校验与 Platform 客户端；`PayActionActor` 执行与结果映射；渠道交易引用写入；`PayResponse.nextAction`；最小真实路由；Risk 端口（测试用 Mock）；`auto` 测试。
- 本机端到端（PayerMax UAT 测试卡）。

## 6. 不做

- 渠道收银台模式；MANUAL 捕获（AUTHORIZE/CAPTURE/VOID）；退款对接（`REFUND` 已在 Platform 配好，Payment 侧另行接入）。
- 真实 Card Vault（加密存储、KMS、CVV 暂存介质）、PCI 合规材料、生产开通。
- Risk 真实接入（本期 Mock）、Commerce 入口改造、checkout-web 卡采集与 3DS 页面。
- 恢复：渠道异步通知与重试查询（用户决定本期不做）。
- 共享环境 SQL、Vault 策略、部署、提交与推送（另行授权）。

## 7. 验收标准（草案）

1. 原始卡数据（PAN、CVV、有效期、持卡人）不出现在 Platform/Payment 日志、数据库、沙箱验证证据、标准 API 响应、错误信息与补偿事件中；自动测试对日志与库做检索断言。
2. Payment `auto`：直接成功、3DS 待认证、明确拒绝、结果未知、读卡失败、重放不重复扣款，全部经真实 HTTP 入口与 Platform/Risk 测试 Mock 覆盖。
3. Platform `auto`：读卡初始化、卡句柄只能进入卡字段、遮蔽、读卡失败零渠道请求；全量不回归。
4. 本机端到端：PayerMax UAT 测试卡一次成功（Attempt/Intent `SUCCEEDED`），3DS 场景返回跳转指令；双向验签通过。

## 8. 待确认

见 [technique.md 第 10 节](./technique.md#10-评审点)。

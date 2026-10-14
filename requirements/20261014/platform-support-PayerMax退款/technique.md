# PayerMax 退款技术方案

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14，R1～R6 按建议）
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：[PayerMax 接入技术方案](../platform-support-PayerMax接入/technique.md)与其 [configure.py](../platform-support-PayerMax接入/assets/harness/configure.py)、[交付记录](../platform-support-PayerMax接入/delivery.md)；Payment Execution `ActionType.REFUND`、`ActionStatus`（`PROCESSING/UNKNOWN/SUCCEEDED/FAILED`）与 [Execution 数据设计](../../20260913/payment-Execution设计/data-design.md) 中 `t_channel_refund`；PayerMax 退款官方文档（见需求第 1 节）。

## 1. PayerMax 协议要点

| 项 | 退款 `POST /refund` | 退款查询 `POST /refundQuery` |
|---|---|---|
| 外层 | `version`（文档当前值 `1.1`）、`keyVersion`、`requestTime`、`appId`、`merchantNo`、`data`；签名与验签规则与下单相同 | 同左 |
| 请求 `data` | `outRefundNo`（必填，商户退款号，唯一标识一次退款申请）、`refundAmount`（必填，**JSON 数字**，主币种单位）、`refundCurrency`（必填，须与原交易一致）、`outTradeNo`（必填，原商户订单号）、`comments`（可选，≤512）、`refundNotifyUrl`（可选） | `outRefundNo` 或 `refundTradeNo` 二选一 |
| 响应 `data` | `outRefundNo`、`refundTradeNo`（PayerMax 退款号）、`tradeOrderNo`、`status` | `outRefundNo`、`refundTradeNo`、`refundAmount`、`refundCurrency`、`outTradeNo`、`status`、`resultMsg`（仅失败时） |
| 状态 | `REFUND_PENDING`、`REFUND_SUCCESS`、`REFUND_FAILED`；文档要求以 `data.status` 判断，不以 `code`/`msg` 判断 | 同左 |

其他规则：退款金额不得超过订单可退金额（即支持部分退款，取决于支付方式）；同一订单不能同时进行另一笔退款；交易日起 180 天内可退。文档未说明重复 `outRefundNo` 的行为，也未给出“另一笔退款进行中”的错误码。

文档列出的退款相关错误码：`REFUND_FAILED`（支付方式不支持退款/部分退款、金额过小）、`REFUND_NO_INVALID`（退款号与订单号相同）、`REFUND_INSUFFICIENT_BALANCE`（待结算余额不足）、`ORDER_NOT_EXIST`（原订单不存在；查询时为退款不存在）、`RDR_REFUNDED_NOT_SUPPORT_RETRY`（RDR 已由发卡行退款）；通用码 `PARAMS_INVALID`、`REQUEST_PARAM_INVALID`、`AMOUNT_INVALID`、`SIGN_VERIFY_FAILED`、`MERCHANT_INVALID`、`MERCHANT_APP_INVALID`、`SYSTEM_ERROR`、`REQUEST_TIMEOUT`、`UNEXPECTED_ERROR`。

## 2. 引擎能力核对

| 需要 | 现状 |
|---|---|
| 对最终字节 RSA 签名、响应验签、`requestTime` | 已有 `sealJsonBody`、`rsaSha256Sign`、`requireRsaSha256Signature`、`nowRfc3339`，复用 `PAYERMAX_SIGN`、`PAYERMAX_VERIFY` 脚本 |
| `refundAmount` 请求与响应为 JSON 数字 | 原子 API `decimalEncoding=NUMBER` |
| 状态映射、按出口输出 | 现有状态规则与流程出口表达式 |
| 沙箱验证多步用例（先退款再查询） | 已有，后一步输入引用前一步结果 |

**结论：不改 Platform、BFF、Gateway、前端代码，不改数据库结构。**本需求只新增配置与 harness。

## 3. 标准 API（渠道无关，草案）

### 3.1 `REFUND`

| 输入 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `channelAccount.channelCode` / `channelAccountId` / `entityNo` / `accountVaultToken` | STRING | 是 | 同 `PAY`；Payment 用原支付 Attempt 冻结的渠道账号 |
| `refund.refundId` | STRING | 是 | 我方退款号，调用方保证唯一，同一退款重试沿用（Payment：Channel Refund ID） |
| `refund.paymentAttemptId` | STRING | 是 | 原支付在 `PAY` 中使用的 `payment.attemptId` |
| `refund.amount.value` | DECIMAL | 是 | 本次退款金额 |
| `refund.amount.currency` | STRING | 是 | 须与原支付币种一致（由渠道校验） |
| `refund.reason` | STRING | 否 | 退款说明；不得包含个人信息 |
| `refund.notifyUrl` | STRING | 否 | 渠道异步通知地址，本期只透传 |

| 输出 | 说明 |
|---|---|
| `refundStatus` | SUCCESS：`PENDING` / `SUCCEEDED` / `FAILED`（渠道已受理但同步给出终态）；FAIL：固定 `FAILED` |
| `channelRefundRef` | 渠道退款流水号（PayerMax `refundTradeNo`），SUCCESS 时返回 |
| `channelStatus` | 渠道原始状态，排查用 |
| `failureCode`、`failureMessage` | FAIL 时为渠道错误码与描述 |

`executeStatus` 与 Payment `REFUND` Action 的对应（供 Payment 接入参考，不在本期实现）：SUCCESS + `PENDING` → `PROCESSING`，SUCCESS + `SUCCEEDED`/`FAILED` → 同名终态，FAIL → `FAILED`，ERROR → `UNKNOWN`。

### 3.2 `REFUND_QUERY`

输入：`channelAccount.*`（同上）、`refund.refundId`。

输出：`refundStatus`（SUCCESS 时 `PENDING`/`SUCCEEDED`/`FAILED`；退款不存在的 FAIL 为 `NOT_FOUND`）、`channelRefundRef`、`channelStatus`、`amount.value`、`amount.currency`、`failureCode`、`failureMessage`（渠道失败原因 `resultMsg`）。

只按我方退款号查询：调用方在 `REFUND` 结果未知时拿不到渠道退款号。

## 4. PayerMax 配置

沿用接入方 `PAYERMAX`（UAT）/ `PAYERMAX_MOCK`（本机）、出站地址 `PAYERMAX_GATEWAY`、账号材料与两个脚本，均不修改。

### 4.1 原子 API

公共部分与下单相同：POST、`PAYERMAX_GATEWAY`、连接 2000 ms / 响应 10000 ms、JSON 编解码、`decimalEncoding=NUMBER`、参数 `merchantPrivateKey`/`payermaxPublicKey`、脚本 `PAYERMAX_SIGN` → `PAYERMAX_VERIFY`。外层 `version` 固定值见评审点 R1。

**`REFUND`（`/refund`）**

请求：外层（同下单），`data.outRefundNo`、`data.refundAmount`（DECIMAL）、`data.refundCurrency`、`data.outTradeNo` 必填，`data.comments`、`data.refundNotifyUrl` 可选。响应声明：`code`、`msg`、`data.outRefundNo`、`data.refundTradeNo`、`data.tradeOrderNo`、`data.status`。

| 状态规则（按顺序） | 结果 |
|---|---|
| HTTP 200 且 `code == 'APPLY_SUCCESS'` 且 `data.status` ∈ {`REFUND_PENDING`, `REFUND_SUCCESS`, `REFUND_FAILED`} 且 `data.refundTradeNo != nil` | SUCCESS |
| HTTP 200 且 `code` ∈ 拒绝码集合 | FAIL |
| 其他 | ERROR |

拒绝码集合（明确未创建退款，评审点 R2）：`PARAMS_INVALID`、`REQUEST_PARAM_INVALID`、`AMOUNT_INVALID`、`CURRENCY_INVALID`、`SIGN_VERIFY_FAILED`、`MERCHANT_INVALID`、`MERCHANT_APP_INVALID`、`ORDER_NOT_EXIST`、`REFUND_FAILED`、`REFUND_NO_INVALID`、`REFUND_INSUFFICIENT_BALANCE`、`RDR_REFUNDED_NOT_SUPPORT_RETRY`。`SYSTEM_ERROR`、`REQUEST_TIMEOUT`、`UNEXPECTED_ERROR`、未知码（含可能的重复退款号、退款进行中）归 ERROR。

输出：SUCCESS → `refundTradeNo`、`channelStatus`；FAIL → `errorCode`、`errorMessage`。

**`REFUND_QUERY`（`/refundQuery`）**

请求：外层，`data.outRefundNo`（必填，本期只按我方退款号查询）。响应声明：`code`、`msg`、`data.outRefundNo`、`data.refundTradeNo`、`data.refundAmount`（DECIMAL）、`data.refundCurrency`、`data.outTradeNo`、`data.status`、`data.resultMsg`。

| 状态规则 | 结果 |
|---|---|
| HTTP 200 且 `APPLY_SUCCESS` 且 `data.status` ∈ 三种退款状态 | SUCCESS |
| HTTP 200 且 `code == 'ORDER_NOT_EXIST'` | FAIL |
| 其他 | ERROR |

输出：SUCCESS → `channelStatus`、`refundTradeNo`、`amount`、`currency`、`resultMsg`（后四项非必填）；FAIL → `errorCode`、`errorMessage`。

### 4.2 流程

| 标准 API | 初始化 | 节点 | 出口 |
|---|---|---|---|
| `REFUND` | `channelAccount` = `CHANNEL_ACCOUNT_CONFIG`（必需） | `applyRefund` → `REFUND` 原子 API | SUCCESS → `accepted`；FAIL → `rejected`（`refundStatus=FAILED`）；默认 → `unknown`（ERROR） |
| `REFUND_QUERY` | 同上 | `queryRefund` → `REFUND_QUERY` 原子 API | SUCCESS → `found`；FAIL → `notFound`（`refundStatus=NOT_FOUND`）；默认 → `unknown` |

状态映射（两个流程相同）：`REFUND_PENDING` → `PENDING`、`REFUND_SUCCESS` → `SUCCEEDED`、`REFUND_FAILED` → `FAILED`。`REFUND_QUERY` 的 `failureMessage` 取 `resultMsg`（空串视为无）；`failureCode` 不赋值（PayerMax 查询没有失败码）。

退款 ERROR 不接查询节点（与下单 T3 一致）：调用方按结果未知稍后调用 `REFUND_QUERY`。流程没有重试、冲正类节点。

### 4.3 配置方式

新增 `assets/harness/configure_refund.py`（复用 PayerMax 接入 harness 的 `common.py` 与 `configure.py` 中的字段、脚本与外层定义），经真实管理接口保存两个标准 API、两个原子 API 与两条流程，已存在则按版本修改。不重复保存 `PAY`/`PAY_QUERY` 相关对象。

## 5. 验证

### 5.1 本机合成 PayerMax

- `PayerMaxMock.java` 增加 `/refund`、`/refundQuery`：独立验签请求、检查外层（`version=1.1`）与 `requestTime` 时效、`refundAmount` 为 JSON 数字与必填字段；按 `outRefundNo` 末位无状态地返回各场景（受理、受理即成功、受理即失败、`REFUND_FAILED`、`ORDER_NOT_EXIST`、`SYSTEM_ERROR`、错误私钥签名的响应；查询三种状态与不存在）。
- `verify_mock_refund.py`：覆盖需求验收第 1 项全部场景，另含输入缺字段 422 与账号身份不一致 503（零渠道请求）。
- 运行环境沿用 PayerMax 接入 harness（本机 `mor_platform_external_api` 独立库，Web 代理 → Gateway → BFF → Platform）。

### 5.2 PayerMax UAT（渠道沙箱验证）

在本机 Operations（接入方 `PAYERMAX`，账号 `UAT_MAIN`）新建用例：

| 用例 | 步骤 | 期望 |
|---|---|---|
| `UAT_REFUND_NOT_EXIST` | `REFUND` 一个不存在的 `paymentAttemptId` | FAIL，`failureCode=ORDER_NOT_EXIST`（无需付款，可立即运行） |
| `UAT_REFUND_THEN_QUERY` | ① `REFUND` 已付款订单的部分金额 ② `REFUND_QUERY` 同一退款号 | ① SUCCESS 且有 `channelRefundRef`，② SUCCESS，`refundStatus` ∈ {`PENDING`,`SUCCEEDED`} |
| `UAT_REFUND_QUERY_NOT_FOUND` | `REFUND_QUERY` 一个不存在的退款号 | FAIL，`refundStatus=NOT_FOUND` |

`UAT_REFUND_THEN_QUERY` 需要一笔 UAT 已付款订单：先运行现有 `UAT_PAY_THEN_QUERY` 拿到收银台地址，**由用户在浏览器用 PayerMax 测试支付方式付款**，查询确认 `SUCCEEDED` 后（顺带补完 PayerMax 接入验收第 3 项），再以该订单号运行退款用例。用例期望值是字面量，订单号写在用例输入里。

证据只保存去敏报文（沿用沙箱验证规则），回执不含凭据和收银台地址。

## 6. 影响范围

| 对象 | 改动 |
|---|---|
| 本需求目录 | 需求、技术方案、交付记录、harness（`configure_refund.py`、`verify_mock_refund.py`）与回执 |
| PayerMax 接入 harness | `PayerMaxMock.java` 增加两个退款接口（合成渠道，只用于本机验证） |
| 本机库 `mor_platform_external_api` / `mor_platform` | 经管理接口写入配置与验证用例（本机数据） |
| Platform、BFF、Gateway、Web、Payment、数据库结构 | 不改 |

## 7. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| R1 | 外层 `version` | 退款两个接口按文档用 `1.1`（下单/查询保持 `1.5`）；若 UAT 返回版本不支持，再改为 `1.5` 并记录 |
| R2 | 拒绝码划分 | 按第 4.1 节；重复退款号、退款进行中等文档未列的码一律归 ERROR，UAT 出现新码再补 |
| R3 | 标准 API 编码与字段 | `REFUND`、`REFUND_QUERY`，字段按第 3 节（草案，Payment 接入时可改）；按我方退款号查询 |
| R4 | `REFUND` 同步返回终态 | `APPLY_SUCCESS` + `REFUND_SUCCESS`/`REFUND_FAILED` 也走 SUCCESS 出口，用 `refundStatus` 表达终态；FAIL 只表示渠道明确未创建退款 |
| R5 | UAT 付款 | 需要你在 UAT 收银台付款一笔，用于退款用例；不付款时只能验证两个“不存在”用例 |
| R6 | 阶段划分 | ① 本评审稿；② 配置 + 本机合成 PayerMax 验证；③ UAT 沙箱验证（需 R5）。写入 mor-test 与提交另行授权 |

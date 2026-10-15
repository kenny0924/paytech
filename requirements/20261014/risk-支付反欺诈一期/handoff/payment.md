# 交接：Payment 接入 Risk 统一评估接口

- 来源：支付反欺诈一期（Risk 侧已交付并部署 mor-test，2026-10-15）
- 接收方：负责 payment-orchestration-service 的会话
- 依据：[一期设计第 14 节](../design.md#14-交给-payment-的接入清单不在本会话范围)、[修订设计第 2、6 节](../design-amendment-unified-evaluation.md)。本文是 Risk Owner 给出的合同说明与建议；Payment 侧的状态、表结构与恢复方式由 Payment Owner 决定

## 1. 现状

| 项 | Payment 当前 | 需要改成 |
|---|---|---|
| 客户端 | 自写草案 `integration/risk/PaymentRiskAssessmentApi`，`POST /risk-assessment/v1/payment-assessments`（Risk 从未提供该路径） | 依赖 `com.paytech:risk-api` 的 `RiskDecisionApi`，删除草案与 `PaymentOutboundProperties` 中对应路径 |
| 请求 | 8 个字段 | 统一请求，17 个输入字段（见第 2 节） |
| 响应 | `assessmentId`、`riskResult`、`authenticationRequirement` | `evaluationId`、`result`、`outputs.AUTHENTICATION_REQUIREMENT` |
| 失败处理 | 任何异常立即 `RISK_UNAVAILABLE` 失败 | 按第 3 节区分明确失败与结果未知 |

## 2. 接口合同

- **调用：** `POST http://risk-service/risk-decisions/v1/evaluations`，内部直连（Nacos 服务名 `risk-service`），不经 Gateway；不需要 Gateway 身份头，带 Foundation 的 `X-Request-Id`。
- **构造：** `new PaymentAttemptRiskInput(...).toRequest(paymentAttemptId)`，生成 `pointCode=PAYMENT_ATTEMPT_RISK`、`contractVersion=1`、`businessKey=支付尝试 ID`、`inputs`。可选字段为 null 时自动省略。

| 字段 | 必填 | 格式 | Payment 侧来源（建议） |
|---|---|---|---|
| paymentIntentId | 是 | 正整数 | Intent |
| riskActionId | 是 | 正整数 | 本次风控 Action ID（即现 `paymentActionId`） |
| orderId | 是 | 正整数 | Intent 冻结的 Order |
| clientId | 是 | 正整数 | Intent |
| entityNo | 是 | 5 位大写字母或数字 | Intent |
| paymentMethodCode | 是 | 大写编码 | Attempt |
| captureMode | 是 | `AUTOMATIC` / `MANUAL` | Attempt |
| amount | 是 | 正数，主单位，≤8 位小数 | Intent |
| currency | 是 | ISO 4217 | Intent |
| industryCode | 是 | 大写编码 | 冻结的 Order Context |
| mccCode | 是 | 4 位数字 | 冻结的 Order Context |
| consumerCountryCode | 否 | ISO 3166-1 alpha-2 | Intent |
| consumerReference | 否 | 可见 ASCII ≤128 | Commerce 传入的消费者（玩家）引用 |
| clientIpAddress | 否 | IP 字面量 | Commerce→Payment 受限最小合同 |
| userAgent | 否 | ≤512 | 同上 |
| cardFingerprint | 否 | ≤128 | Card：跨交易稳定指纹；非卡为空 |
| cardBin | 否 | 6～8 位数字 | Card；非卡为空 |

缺少可选字段不报错，但相关规则无法命中（例如没有卡指纹，就算不了同卡频次）。卡测试、盗卡类规则主要依赖 `cardFingerprint`、`clientIpAddress`、`consumerReference`，建议一期就传。

## 3. 响应与处理

| Risk 返回 | 含义 | 建议 Payment 处理 |
|---|---|---|
| 200 `ALLOW` | 放行；`outputs.AUTHENTICATION_REQUIREMENT` 必有：`NOT_REQUIRED` 或 `THREE_DS_REQUIRED` | 继续；`THREE_DS_REQUIRED` 走 3DS |
| 200 `REVIEW` / `DENY` | 风控拦截，`outputs` 为空 | Attempt 失败、释放 Intent（修复现在停在 PROCESSING 的问题） |
| 200 `FAILED` / `TIMED_OUT` | Risk 内部失败或超时，已落库 | 不放行，Attempt 失败 |
| 400 `INVALID_REQUEST` | 风控点或合同版本未登记 | 不放行；属于对接缺陷，告警 |
| 422 `VALIDATION_ERROR` | 输入字段缺失、未知或格式不对，`errors[].field` 如 `inputs.amount` | 不放行；对接缺陷，告警 |
| 409 `CONCURRENT_REQUEST` | ① 同一支付尝试已用不同风控 Action 或不同稳定输入评估过；② 同一请求正在并发处理（抢锁失败） | 用**完全相同的请求**重试一次；仍为 409 则不放行 |
| 503 `RISK_DECISION_NOT_CONFIGURED` | 该环境没有生效的支付发布 | 不放行（失败关闭） |
| 读超时、连接失败、其他 5xx | 结果未知，Risk 可能已评估 | 风控 Action 记为 UNKNOWN，恢复任务以**原支付尝试 ID + 原风控 Action ID + 相同稳定输入**重试，拿到原结果 |

- **幂等：** 同一支付尝试只评估一次。重试时 IP 与 User-Agent 可以变化，其他字段和风控 Action ID 必须与首次相同，否则返回 409。
- **结果要保存的：** `evaluationId`（取代原 `assessmentId`）、`result`、认证要求。Payment 现有字段名 `transactionAssessmentId` 是否改名由 Payment 决定。

## 4. 其他要求

- IP、User-Agent、卡指纹、消费者引用属于受限值：不得写入普通日志、MQ 或对外接口。
- 自动测试按 UNIT_TEST_GUIDE 用 Risk 外部 HTTP DTO 模拟，覆盖：ALLOW 无需认证、ALLOW 需 3DS、REVIEW、DENY、FAILED、TIMED_OUT、503、422、409 重试、读超时后恢复。
- mor-test 现状：Risk 已部署，但支付决策点**没有生效发布**，评估一律返回 503。要在 test 联调，需要先由运营配置并激活支付流程（运营后台能力见 [operations.md](./operations.md)），或由 Risk 会话在 test 临时配置一套联调规则（需另行授权）。

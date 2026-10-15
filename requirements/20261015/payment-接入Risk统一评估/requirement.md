# Payment 接入 Risk 统一评估接口需求

> 状态：阶段 1 已确认（2026-10-15），阶段 2+3 方案见 [technique.md](./technique.md)。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

Risk 已交付支付反欺诈一期并部署 mor-test，统一评估接口为 `POST /risk-decisions/v1/evaluations`（`com.paytech:risk-api` 的 `RiskDecisionApi`）。Risk Owner 的交接见 [handoff/payment.md](../../20261014/risk-支付反欺诈一期/handoff/payment.md)，合同依据为[一期设计第 14 节](../../20261014/risk-支付反欺诈一期/design.md)与[统一评估修订第 2、6 节](../../20261014/risk-支付反欺诈一期/design-amendment-unified-evaluation.md)。交接只给出合同和处理建议，Payment 侧的状态、表结构与恢复方式由 Payment 决定。

Payment 现状（证据为当前源码）：

| 问题 | 证据 |
|---|---|
| 调用的是自写草案 `POST /risk-assessment/v1/payment-assessments`，Risk 没有提供过这个路径 | `paytech-payment-web/.../integration/risk/PaymentRiskAssessmentApi.java`、`PaymentOutboundProperties` |
| 只传 8 个字段，缺订单、行业、MCC、捕获模式及 IP/UA/消费者引用/卡属性 | `PayRiskCheckActionActor.command()` |
| 任何异常都直接记为 `RISK_UNAVAILABLE` 失败，没有区分“明确失败”和“结果未知” | `PayRiskCheckActionActor.execute()` |
| `REVIEW`/`DENY` 的风控动作记为 `SUCCEEDED` 后流程直接结束：Attempt 没有失败，Intent 也没有释放，一直停在 PROCESSING | `CardPayFlow` 的 `end(PAY_RISK, rejected)` + `ActionFlow.advanceAction()` 只有 Action 为 FAILED 时才释放 |

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Payment 卡支付流程 | 每个支付尝试都用 Risk 的统一合同评估一次，并按结果放行、要求 3DS 或拒绝 |
| Risk | 收到完整的 17 个输入字段，卡测试和盗卡类规则能够计算 |
| 消费者 / Commerce | 风控拦截或评估失败后，Attempt 立即失败并释放 Intent，可以重新发起支付；不会卡在处理中 |

## 3. 本期范围

1. **替换客户端**：依赖 `risk-api` 的 `RiskDecisionApi`，经 Nacos 服务名 `risk-service` 内部直连，带 Foundation `X-Request-Id`。删除草案接口、请求和响应类，以及 `PaymentOutboundProperties`/Nacos 中对应的 `risk-context-path` 配置。
2. **统一请求**：用 `PaymentAttemptRiskInput(...).toRequest(paymentAttemptId)` 构造请求，按交接表填写 17 个字段（见 §5）。
3. **结果处理**：按 §6 区分放行、拦截、明确失败和结果未知。
4. **修复拦截后卡住**：`REVIEW`/`DENY`/`FAILED`/`TIMED_OUT` 都让 Attempt 失败并释放 Intent。
5. **结果未知的处理**：读超时、连接失败或其他 5xx 时，在本次请求内用完全相同的请求立即重试一次；仍未取得结果则失败关闭（Attempt 失败、释放 Intent），不使用 UNKNOWN 和恢复任务（Q-02 决定）。
6. **结果保存**：保存 `evaluationId`、`result` 和认证要求。
7. **受限数据保护**：IP、User-Agent、卡指纹、消费者引用不进入普通日志、MQ、对外接口或补偿 payload。
8. **测试与文档**：用 Risk 外部 HTTP DTO Mock 覆盖 §7 的场景；同步 Payment 需求文档、全局时序和 `CURRENT_IMPLEMENTATION.md`。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| Risk 侧任何修改 | 合同以 Risk 已发布的为准；发现合同问题时记录并转交 Risk 会话 |
| 在 mor-test 配置 Risk 支付规则 | 由运营后台或 Risk 会话负责，需另行授权（见 Q-04） |
| 真实 Card 的加密存储与卡指纹密钥管理 | Card 目前是本地 Mock；本期最多做到 Mock 提供卡属性（见 Q-01） |
| 3DS 认证本身 | 本期只根据认证要求选择是否进入现有的 AUTHENTICATE 分支 |
| 风控结果未知的异步恢复 | Q-02 决定不做：风控在资金动作之前，失败关闭是安全的；CVV 只在本次请求内存中，异步恢复拿回 ALLOW 也无法继续扣款 |
| 渠道资金动作的 UNKNOWN 恢复 | 不在本期 |
| 授权结果回流 Risk | 属于另一项需求（[授权结果回流](../../20261014/risk-授权结果回流/requirement.md)，已暂缓） |

## 5. 输入字段与 Payment 来源

| 字段 | 必填 | Payment 来源 | 现状 |
|---|---|---|---|
| paymentIntentId | 是 | Intent | 已有 |
| riskActionId | 是 | 本次 PAY_RISK 的 `paymentActionId` | 已有 |
| orderId | 是 | Intent 冻结的 Order（`sourceId`，`sourceType=ORDER`） | 已有，阶段 2 核对来源类型 |
| clientId / entityNo | 是 | Intent | 已有 |
| paymentMethodCode / captureMode | 是 | Attempt | 已有 |
| amount / currency | 是 | Intent | 已有 |
| industryCode / mccCode | 是 | Intent（冻结的 Order Context） | 已有 |
| consumerCountryCode | 否 | Intent | 已有 |
| consumerReference | 否 | PayRequest | 请求里有，只在本次请求内使用，不保存 |
| clientIpAddress / userAgent | 否 | PayRequest | 请求里有，未保存（重试时允许变化，不需要保存） |
| cardFingerprint / cardBin | 否 | Card | Card 目前不产生，本期由 Card 端口提供（Q-01），只在本次请求内使用，不保存 |

## 6. 结果处理

| Risk 返回 | Payment 处理 |
|---|---|
| 200 `ALLOW` + `NOT_REQUIRED` | 风控 Action 成功，按捕获模式进入 AUTHORIZE 或 PAY |
| 200 `ALLOW` + `THREE_DS_REQUIRED` | 风控 Action 成功，进入 AUTHENTICATE |
| 200 `REVIEW` / `DENY` | 保存结果；Attempt 失败，释放 Intent |
| 200 `FAILED` / `TIMED_OUT` | 保存结果；Attempt 失败，释放 Intent |
| 200 但结论或认证要求缺失、无法识别 | 不放行，Attempt 失败 |
| 400 / 422 / 503 | 不放行，Attempt 失败；400/422 属于对接缺陷，记录告警日志（不含受限值） |
| 409 | 用完全相同的请求立即重试一次；仍为 409 则不放行，Attempt 失败 |
| 读超时、连接失败、其他 5xx | 用相同请求立即重试一次；拿到结果按上表处理，仍未知则 Attempt 失败、释放 Intent |

## 7. 验收标准

| 编号 | 验收标准 |
|---|---|
| AC-01 | Payment 通过 `RiskDecisionApi` 调用 `/risk-decisions/v1/evaluations`；草案接口、类和 `risk-context-path` 配置已删除 |
| AC-02 | 请求 `pointCode=PAYMENT_ATTEMPT_RISK`、`contractVersion=1`、`businessKey=支付尝试 ID`，17 个字段按 §5 填写，可选字段为空时省略 |
| AC-03 | ALLOW + NOT_REQUIRED：保存 `evaluationId`/结论/认证要求，按捕获模式进入 AUTHORIZE 或 PAY |
| AC-04 | ALLOW + THREE_DS_REQUIRED：进入 AUTHENTICATE |
| AC-05 | REVIEW、DENY、FAILED、TIMED_OUT：保存结果，Attempt 失败，Intent 释放，可以发起新的 Attempt |
| AC-06 | 400、422、503、响应不完整：Attempt 失败，Intent 释放，不默认放行 |
| AC-07 | 409：用相同请求重试一次；第二次成功按结果处理，仍为 409 则失败 |
| AC-08 | 读超时、连接失败或其他 5xx：用相同请求立即重试一次，第二次拿到结果按 AC-03～AC-06 处理；仍未知则 Attempt 失败、Intent 释放，不产生 UNKNOWN Action 或补偿任务 |
| AC-09 | IP、User-Agent、卡指纹、消费者引用不出现在普通日志、MQ、对外响应和补偿 payload 中 |
| AC-10 | auto 测试从 Payment 支付入口出发，用 Risk DTO Mock 覆盖 AC-03～AC-08，每个测试结束时检查没有多余的调用 |

## 8. 已确认决定（2026-10-15）

| 编号 | 决定 |
|---|---|
| Q-01 | 卡指纹和 BIN 本期补齐。扩展 Card 端口提供风控卡属性（BIN + 跨交易稳定指纹）；Mock 取合成 PAN 前 6～8 位为 BIN、用配置的测试密钥做 HMAC 得到指纹。真实 Card 接入时只替换实现，正式指纹密钥属于 Card 后续需求 |
| Q-02 | 不做恢复任务，不使用风控 UNKNOWN。读超时、连接失败或其他 5xx 时请求内用相同请求立即重试一次，仍未知则失败关闭。代价：该 Attempt 在 Payment 侧没有 `evaluationId`（Risk 可按 Attempt ID 查到）；若 Risk 实际已评估，消费者重试会使累计多计一次。Payment 读超时从 3 秒调短到约 1 秒，限制最坏耗时。这一处理与交接单建议不同，交接单写明恢复方式由 Payment Owner 决定，不涉及 Risk 合同变更 |
| Q-03 | `t_payment_action.transaction_assessment_id` 列名保留，改存 Risk `evaluationId`，只更新注释 |
| Q-04 | 本期做到阶段 5 本地验收；test 部署与联调在运营激活支付发布后再确认 |

## 9. 阶段安排（建议）

| 阶段 | 安排 |
|---|---|
| 1 | 本评审稿 |
| 2+3 | 合并：现状差异、请求映射、结果处理、测试计划（预计无 DDL，只改注释） |
| 4–5 | 研发与本地验收 |
| 6–7 | 按 Q-04，运营激活后再确认 |
| 8 | 提交需单独授权 |

# Payment 接入 Risk 统一评估接口技术方案

> 状态：阶段 2+3 评审稿（2026-10-15），待确认。需求与已确认决定见 [requirement.md](./requirement.md)。

**规模（本次方案）：** 只改 Payment 一个系统；调用 Risk 现有的 1 个接口，Payment 不新增也不修改对外接口；新增 0 张表、0 个字段，1 个字段只改注释（`t_payment_action.transaction_assessment_id`）；DML 0 行；页面 0 个。

## 1. 现状差异

| 需求项 | 当前行为（证据） | 目标 | 处理 |
|---|---|---|---|
| 客户端（AC-01） | Web 中自写 `PaymentRiskAssessmentApi`，路径 `/risk-assessment/v1/payment-assessments`（`PaymentOutboundConfiguration`） | `risk-api` 的 `RiskDecisionApi`，路径 `/risk-decisions/v1/evaluations` | 替换；删除草案的接口、请求类和响应类 |
| 请求（AC-02） | 8 个字段（`PayRiskCheckActionActor.command()`） | 17 个字段，由 `PaymentAttemptRiskInput.toRequest()` 构造 | 扩展端口命令；新增字段来源见 §3.3 |
| 卡属性 | `CardService` 只有申请、核对和领取材料，不产生 BIN 和指纹 | Card 端口提供 BIN 和指纹 | 新增 `CardService.riskAttributes()`，由 Mock 实现（§3.4） |
| 响应（AC-03/04） | `assessmentId`、`riskResult`、`authenticationRequirement` | `evaluationId`、`result`、`outputs.AUTHENTICATION_REQUIREMENT` | 端口实现改为解析新响应 |
| 拦截后卡住（AC-05） | REVIEW/DENY 记为 SUCCEEDED 后走 `end`，`ActionFlow` 只在 Action FAILED 时释放 Intent | Attempt 失败，Intent 释放 | 新增“失败结束”转移（§3.5） |
| 失败处理（AC-06/07/08） | 任何异常都当作 `RISK_UNAVAILABLE` 处理，不重试 | 409 和不可用时重试一次，其余直接失败关闭 | 在风控 Actor 中按异常类型处理（§3.6） |
| 读超时 | `risk-read-timeout: 3s` | 约 1 秒 | 默认值与 Nacos 配置改为 `1s` |
| 失败原因写入 `channel_response_code` | 风控失败时写 `RISK_UNAVAILABLE`/`RISK_RESULT_INVALID`，与该列注释“仅渠道动作可填”冲突 | 遵守列合同 | 不再写入该列；失败原因改为 WARN 日志（不含受限值） |
| 测试 Mock | `MockRiskController implements PaymentRiskAssessmentApi`，只能配一个结论或一个故障 | 实现 `RiskDecisionApi`，按调用顺序配置响应，记录请求 | 改造 `RiskMocker`（§5） |

**影响面：** 只涉及 `payment-orchestration-service` 的 execution、card、web、test 模块及配置文档。Commerce 已经向 Payment 传了消费者引用、IP 和 UA，Payment 对外的 `PayRequestBody` 不变。Risk 不改。

## 2. 主流程

```text
PayProcessor（Intent TRY_LOCK）
  → 构建上下文、路由、保存 Attempt + PAY_RISK Action（本地事务）
  → ActionFlow 事务外执行 PAY_RISK
      PayRiskCheckActionActor
        → CardService.riskAttributes(instrumentRef)    本进程，非卡支付不调用
        → 构造 PaymentRiskCommand（一次构造，重试复用同一个对象）
        → PaymentRiskClient.evaluate(command)           事务外 HTTP，可能重试一次
  → 结果事务：保存 Action 结果；根据转移结果进入下一步 / 成功结束 / 失败结束
```

事务边界不变：Risk 调用在事务外，结果写入复用 `ActionFlow.advanceAction()` 现有的单个短事务。

## 3. 设计

### 3.1 依赖与配置

- `paytech-payment-web/pom.xml` 增加 `com.paytech:risk-api:1.0.0-SNAPSHOT`，写法与现有的 `platform-support-api` 一致。`risk-api` 只依赖 `paytech-common-core`、`spring-web` 和 `jakarta.validation-api`，Execution 模块不直接依赖它，Enforcer 规则不受影响。
- `PaymentOutboundConfiguration`：Bean 改为 `RiskDecisionApi`，继续使用 `HttpServiceClientOptions.strict().withStrictJsonResponses(8192)`。
- `PaymentOutboundProperties`：保留 `riskContextPath`（Risk 没有 context path，默认空串；测试 Mock 也用空串），只更新注释；`riskReadTimeout` 默认值改为 `1s`。
- Nacos `payment-orchestration-service.yaml`：去掉“草案”字样，`risk-read-timeout: 1s`。
- Card Mock 新增 `payment.card.mock-fingerprint-key`（§3.4）。

### 3.2 Execution 端口

```java
public interface PaymentRiskClient {
    /** 评估一次支付尝试；HTTP 与响应异常按 Foundation 分类抛出，由调用方决定是否重试 */
    PaymentRiskResult evaluate(PaymentRiskCommand command);
}
```

- `PaymentRiskCommand`：包含支付尝试 ID 和 17 个字段，类型与 Payment 内部一致（ID 为 `Long`，金额为 `BigDecimal`）。转成 Wire 字符串由 Web 实现负责。重写 `toString()`，输出脱敏内容。
- `PaymentRiskResult`：`evaluationId`（Long）、`riskResult`、`authenticationRequirement`（只有 ALLOW 时有值）。
- Web 实现 `PaymentRiskClientImpl` 的校验规则：
  - `evaluationId` 必须符合 `^[1-9][0-9]{0,18}$`。
  - `result` 只接受 ALLOW、REVIEW、DENY、FAILED、TIMED_OUT。
  - ALLOW 时 `outputs.AUTHENTICATION_REQUIREMENT` 必须是 `NOT_REQUIRED` 或 `THREE_DS_REQUIRED`。
  - 不满足任一条时抛 `ErrorException(UPSTREAM_INVALID_RESPONSE)`。

### 3.3 字段映射

| 字段 | 取值 | 说明 |
|---|---|---|
| businessKey | `attempt.paymentAttemptId` | 由 `toRequest()` 写入 |
| paymentIntentId / clientId / entityNo | Intent | |
| riskActionId | 当前 PAY_RISK `paymentActionId` | |
| orderId | `intent.sourceId` | `sourceType` 只允许 `COMMERCE_ORDER`（`CreateIntentRequestBody` 已校验） |
| paymentMethodCode / captureMode | Attempt | captureMode 取枚举代码 |
| amount | `intent.amount.stripTrailingZeros().toPlainString()` | Risk 格式：最多 16 位整数、8 位小数；同一 Attempt 每次结果相同 |
| currency / industryCode / mccCode / consumerCountryCode | Intent | |
| consumerReference / clientIpAddress / userAgent | `PayContext.channelInput` | 只在本次请求内使用。`PayRequestBody` 的校验比 Risk 更严或与之相同（引用 128、UA 512）；IP 在 Payment 侧最长 64 字符，Risk 侧 45，超长的非法值会被 Risk 返回 422 并失败关闭 |
| cardFingerprint / cardBin | `CardService.riskAttributes(instToken.instrumentRef)` | 只有卡工具才取；取不到时为 null，请求中省略 |

### 3.4 Card 卡属性

```java
/** 风控卡属性：BIN 与跨交易稳定指纹，不含卡号 */
CardRiskAttributes riskAttributes(String instrumentRef);
```

- 不访问数据库或外部依赖，与 `claimChannelMaterial` 一样只读本地材料，也不领取 CVV。
- **Mock 实现：**
  - BIN：卡号为 16 位及以上时取前 8 位，否则取前 6 位。
  - 指纹：`HMAC-SHA256(payment.card.mock-fingerprint-key, PAN)`，输出小写十六进制 64 位，符合 Risk 的格式要求 `[A-Za-z0-9_\-+/=]{8,128}`。
  - 申请卡工具时计算一次，与合成材料存在一起。
  - 材料不存在或已过期时返回 null，后续 PAY 领取材料时会按现有规则失败。
- 开启 `mock-enabled` 但没有配置指纹密钥时，启动失败。测试配置和本机端到端配置各放一个测试密钥。
- `CardRiskAttributes.toString()` 输出脱敏内容。
- 真实 Card 接入时替换这个实现；正式指纹密钥由 Card 的后续需求负责。

### 3.5 流程转移：失败结束

`ActionTransition` 新增 `fail(from, condition)`：条件成立时，Attempt 失败并释放 Intent。在 `ActionFlow.advanceAction()` 中，它与 Action FAILED 走同一分支（`attempts.updateToFail` + `intents.releaseAttempt`）。

`CardPayFlow` 的 PAY_RISK 规则：

| 条件 | 转移 |
|---|---|
| SUCCEEDED + ALLOW + 需要认证 | next AUTHENTICATE |
| SUCCEEDED + ALLOW + 无需认证 + MANUAL | next AUTHORIZE |
| SUCCEEDED + ALLOW + 无需认证 + AUTOMATIC | next PAY |
| SUCCEEDED + REVIEW/DENY/FAILED/TIMED_OUT | **fail**（原来是 end） |
| FAILED（没有取得结论） | end，现有分支已会让 Attempt 失败 |

只要 Risk 返回了 200 和评估 ID，PAY_RISK Action 就记为 SUCCEEDED，并保存 `transaction_assessment_id`、`risk_result`，表示“评估已完成”。是否放行由转移规则决定，这与列注释“评估完成不等于允许支付”一致。没有取得结论时，Action 记为 FAILED，`risk_result` 为空。

### 3.6 调用与重试（PayRiskCheckActionActor）

Foundation 客户端把 Risk 的各种返回分类成下列异常：

| Risk 返回 | Foundation 异常 | 处理 |
|---|---|---|
| 200 有效 | 返回结果 | 按 §3.5 |
| 409 | `ConcurrentRequestException` | 用同一个请求对象重试一次 |
| 读超时、连接失败、所有 5xx（包括 503 `RISK_DECISION_NOT_CONFIGURED`） | `ServiceUnavailableException` | 用同一个请求对象重试一次 |
| 400 | `InvalidParameterException` | 不重试，失败；WARN“Risk 对接缺陷” |
| 422、响应不合规、200 内容无效 | `ErrorException(UPSTREAM_INVALID_RESPONSE)` | 不重试，失败；WARN |
| 重试后仍然失败 | 任意 | 失败 |

- **503 也会被重试一次：** Foundation 客户端先按 5xx 归类，再读错误代码，所以 503 `RISK_DECISION_NOT_CONFIGURED` 和其他 5xx 区分不出来，同样会重试一次。Risk 在返回 503 时不写评估，多一次调用不会产生副作用。这是 Payment 侧的取舍，不需要 Risk 修改。
- **日志：** 只记录 attemptId、actionId、异常类别和 HTTP 状态，不记录请求、响应、IP、UA、指纹或消费者引用。
- **结果保存：** `transaction_assessment_id` 保存 evaluationId；`risk_result` 保存 result；`authentication_requirement` 只在 ALLOW 时保存，其他结论为空，与列注释一致。

### 3.7 受限数据

- 不持久化消费者引用、IP、UA、卡指纹和 BIN，不加入 MQ，不写补偿 payload（本需求不产生补偿事件）。
- 承载这些值的对象（`PaymentRiskCommand`、`CardRiskAttributes`）重写 `toString()`，输出脱敏内容；出站客户端本来就不记录报文。
- 测试中断言：日志输出不包含这些测试值（§5，T11）。

## 4. 数据库

| 文件 | 变更 |
|---|---|
| PDManer `docs/database/payment-orchestration.pdma.json` | `t_payment_action.transaction_assessment_id` 的注释改为“Risk 统一评估返回的评估 ID（evaluationId）；仅 PAY_RISK 取得结论后填写，已知后不可替换；渠道动作为空。” |
| `docs/database/sql/payment-orchestration-schema.sql`、测试 `init/ddl.sql` | 同步修改注释 |
| `db/ddl.sql`（本需求，阶段 4 产出） | 只改注释的 `ALTER TABLE t_payment_action MODIFY COLUMN ... COMMENT ...`，列类型、可空和默认值保持原样。只在已有环境需要同步注释时由用户授权执行；不影响数据，可重复执行 |

没有结构变化，也没有 DML；本地测试库按现有方式由测试 DDL 初始化。

## 5. 测试计划（auto，入口为 `POST /payment-execution/v1/intents/{id}/pay`）

**Mock 改造：**
- `MockRiskController implements RiskDecisionApi`。
- `RiskMocker` 支持按调用顺序配置响应队列（结论、Problem 故障、延迟超过读超时），记录每次请求体。
- 没有配置就被调用时计为意外调用；`AfterEach` 检查。
- 测试配置的 `risk-read-timeout` 设短，比如 300ms，延迟场景设成大于这个值。

| 编号 | 场景 | 断言 | AC |
|---|---|---|---|
| T1 | ALLOW + NOT_REQUIRED，AUTOMATIC | 进入 PAY 并成功；请求 `pointCode`/`contractVersion`/`businessKey` 正确，17 个字段的值与来源一致（含指纹、BIN、引用、IP、UA）；保存了 evaluationId、结论和认证要求 | 01/02/03 |
| T2 | ALLOW + NOT_REQUIRED，MANUAL | 进入 AUTHORIZE | 03 |
| T3 | ALLOW + THREE_DS_REQUIRED | 进入 AUTHENTICATE，返回 3DS 交互（现有 `test_threeDsChallenge` 改造） | 04 |
| T4 | REVIEW / DENY / FAILED / TIMED_OUT（参数化） | Action SUCCEEDED，保存了结论，认证要求为空；Attempt FAILED；Intent 的 activeAttemptId 为空；没有调用 Platform；用新的来源提交可以再次支付 | 05 |
| T5 | 400 / 422 | 只调用 1 次；Attempt FAILED，Intent 已释放 | 06 |
| T6 | 503 两次 / 503 后 ALLOW | 调用 2 次；前者失败，后者继续进入 PAY | 06/08 |
| T7 | 409 两次 / 409 后 ALLOW | 两次请求体完全相同；前者失败，后者继续 | 07 |
| T8 | 读超时两次 / 超时后 ALLOW | 调用 2 次；前者失败、Action 不是 UNKNOWN；后者继续 | 08 |
| T9 | ALLOW 缺认证要求、未知结论、evaluationId 非法 | 只调用 1 次，失败 | 06 |
| T10 | 原提交重放 | 不再调用 Risk（现有 `test_replayDoesNotExecuteAgain`） | — |
| T11 | 受限值不进日志 | 捕获整个用例的日志，不包含测试 IP、UA、引用和指纹 | 09 |

另外：`PaymentRiskClientImpl` 的映射和 Card Mock 的指纹都通过上述入口用例覆盖，不单独为内部类写测试。

## 6. 文档同步

- `payment-orchestration-service/docs/development/configuration.md`：Risk 配置项、超时、指纹密钥。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 的 Payment 段：风控已接入统一评估接口。
- `requirements-site`：检查 PAY_RISK 相关的接口用例和全局时序是否写到了草案路径或 UNKNOWN 恢复，如有则改正；`npm --prefix requirements-site test`。
- PayerMax 需求的 `technique.md` 属于历史记录，不改写。

## 7. 研发顺序与验证

1. Card：`riskAttributes` 与 Mock 指纹、配置。
2. Execution：端口模型、`ActionTransition.fail`、`CardPayFlow`、`PayRiskCheckActionActor` 重试与日志。
3. Web：依赖、客户端 Bean、`PaymentRiskClientImpl`，删除草案类；配置与 Nacos。
4. Test：Mock 改造与 T1～T11。
5. 数据库注释、文档同步。
6. 验证：本地 `local-environment` 下执行 Payment `./mvnw verify`（JDK 25）；`requirements-site` 测试。`risk-api` 需要先在本机 `mvn install`，只安装，不改 Risk 代码。

## 8. 记录给 Risk 的问题

§3.6 的 503 重试属于 Payment 侧取舍，不需要 Risk 改动。阶段 5 逐字段核对 Risk `PaymentAttemptEvaluator` 输入合同后发现一处格式差异，本机真实 Risk 已复现 422。2026-10-15 用户选择 ②，作为 CCT/Commerce/Payment 的后续需求，本需求不改代码：

| 编号 | 问题 | 影响 | 可选处理 |
|---|---|---|---|
| R-01 | `industryCode`：Risk 要求 `[A-Z][A-Z0-9_]{0,63}`；CCT 行业目录、Commerce 与 Payment `CreateIntentRequestBody` 只限制最长 64 字符，没有格式约束 | 目录中出现小写或含 `-` 等字符的行业代码时，该 Intent 的每次支付都会被 Risk 返回 422，Payment 失败关闭。当前已知取值（`RETAIL`、`DIGITAL_GOODS`）都符合 | ① Risk 放宽为平台目录的实际格式；② 平台行业目录统一约定大写编码，并在 CCT/Commerce/Payment 入口校验；③ 暂不处理，由 422 告警日志发现 |

其余 16 个字段的 Payment 侧约束均不宽于 Risk：ID 为正整数、`entityNo` 为两位国家代码加三位数字、`paymentMethodCode` 同为大写编码、金额为正且最多 8 位小数、`consumerReference` 为可见 ASCII 子集、UA 同为 512、BIN 与指纹由 Card 生成；IP 在 Payment 侧最长 64 字符（Risk 45，并校验 IP 字面量），非法 IP 会被 Risk 拒绝并失败关闭。

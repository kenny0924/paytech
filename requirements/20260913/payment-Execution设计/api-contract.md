# Execution 内部接口合同

> **交易级 Token 修订评审：** 用户已确认统一 pay、交易级工具 Token、Attempt 直接关联及删除旧 Instrument 的方向；详细合同见[修订稿](./instrument-token-amendment.md)。下文保留上一版合同与评审上下文，受影响部分以修订稿的准确取代范围为准；新合同的实现与验证状态只读[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

> 阶段 3 已获用户确认，当前进入阶段 4 Execution 本地切片研发；其他 Owner 仅测试 Mock + TODO。实际实现与验证边界见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)，阶段确认见[交付记录](./delivery.md)。

## 1. 统一约定

- 提供方为 Payment Web，业务 Owner 为 Execution。基础路径 `/payment-execution/v1`，仅私有 HTTP 的受信服务入口；不注册 Gateway、BFF、页面或公开 Callback URL。`*-api` 使用 Spring `@HttpExchange`。
- 命令消费者为 Commerce；Card 提交最终经 Payment 专用 Card 入口转换。查询只向经授权的 Commerce/内部事实消费者开放。本期身份替身固定调用方和 Client 作用域；生产缺少真实调用方认证/授权装配时不可用，不能信任请求自行声明的 callerScope。
- 每个请求包含必填 `clientId`（POST body / GET query）。服务核对真实调用方权限、资源所属 Client、来源订单及 Intent 的一致性；猜中 ID 不取得权限。跨作用域查询返回 404，未经认证/无能力权限分别 401/403。
- ID 为正的十进制 String，最大 Java `long`，无前导零；金额 String，正数、普通十进制、最多 16 位整数和 8 位小数，必须满足当前币种允许精度；币种为已支持的大写 ISO 4217 code。`entityNo` 恰为 CHAR(5) 合法目录值，非调用者自由选路参数。
- 时间为 UTC RFC 3339 String、最多微秒；枚举均为 String code，未知值拒绝。请求没有隐式业务默认值，未知字段拒绝；所有 ID/金额/时间 JSON 示例都用字符串。
- POST 必须有 `Idempotency-Key`，1–128 字符，限定小写 ASCII 字母、数字、`:_-` 且以字母或数字开始；实际幂等作用域由服务身份确定。提交支付的 key 固定为 `submission:<sourceSubmissionId>`。
- 所有读写响应 `Cache-Control: no-store`；本路径只做元数据日志（路径模板、耗时、状态、Request ID）。Card 引用、临时用卡引用、交互指令、网络原值不进日志。
- 2xx 无通用包装。受理完成且事实已知用 200，已受理但执行中/UNKNOWN 用 202；GET 一律 200 返回观察到的事实。业务拒付属于已受理执行结果；结构/权限/幂等冲突属于 HTTP 错误。

## 2. 接口与表影响

缩写：B=Intent Binding，I=Intent，A=Attempt，X=Action，T=Transaction Ref，P=Refund Position，R=Channel Refund，F=Pricing Fact，S=State Transition，C=Command Idempotency，E=Compensation Event。Instrument 与 Duplicate Case 在可信结果需要时写入。写表指完整用例可能影响的范围，各次短事务见[恢复设计](./state-and-recovery.md)。

| 编号 | Method + Path（相对基础路径） | 请求 / 响应 | 主读写表 |
|---|---|---|---|
| EX-I01 | POST `/intents` | CreateIntent / IntentView | 写 B、I、S、C、E（到期任务） |
| EX-I02 | GET `/intents/{intentId}` | clientId / IntentView | 读 B、I、A、X |
| EX-I03 | POST `/intent-creations/close` | CloseCreation / CloseResult | 写 B、C；读 I |
| EX-I04 | POST `/intents/{intentId}/terminate` | Terminate / TerminationResult | 写 B、I、A、X、S、C、E；读 T |
| EX-I05 | POST `/intents/{intentId}/card-payments` | SubmitCard / SubmissionView | 写 A、X、I、T、P、F、S、C、E；条件写 Instrument、Duplicate Case；读 B |
| EX-I06 | GET `/intents/{intentId}/submissions/{sourceSubmissionId}` | clientId / SubmissionView | 读 I、A、X、Instrument |
| EX-I07 | POST `/intents/{intentId}/captures` | Capture / ActionView | 写 X、A、I、T、P、F、S、C、E；读 B |
| EX-I08 | POST `/refunds` | CreateRefund / RefundView | 写 P、R、X、T、S、C、E；读 I、A |
| EX-I09 | GET `/refunds/{refundId}` | clientId / RefundView | 读 R、P、X、I |
| EX-I10 | POST `/intents/{intentId}/reversals` | Reversal / ReversalResult | 写 B、X、A、I、T、S、C、E（Void 分支）；读 P、R；不自行创建 Commerce Refund |
| EX-I11 | GET `/intents/{intentId}/pricing-facts` | clientId / PricingFactView | 读 I、A、F |
| EX-I12 | GET `/actions/{actionId}` | clientId / ActionView | 读 X、A/R/Duplicate Case、I |

GET 不建新 Attempt/Action、不推进状态、不调用有副作用的恢复。若需要重新展示现有 3DS 指令，可在事务外调用 Channel 的只读 `readInteraction`；无法取得时返回指令暂不可用，不伪造 URL 或改变已知支付状态。

### 不单独公开的能力

| 能力 | 调用入口与原因 |
|---|---|
| `authenticate`、`continueAuthentication` | Card Processor 与可信结果 Handler；Commerce 不决定 3DS 技术步骤 |
| `voidAuthorization` | Reversal Processor 或原 Void 恢复 Handler；不增加消费者/Client 直接 Void，Duplicate Case 本期只走其既有 REFUND 补偿 |
| `applyChannelResult` | 受信 Channel 适配器/内部消息 Handler；必须先完成渠道身份核验与归一化，不接受浏览器“成功”作为事实 |
| `recoverAttempt`、`recoverAction` | 本地可靠任务 Handler；资源 ID 定位、重入，非任意执行 HTTP |

## 3. 命令字段

表中 `?` 为可空/可省略，其余全部必填；空字符串不等于缺省。除明确列出的长度外，code ≤64 字符。路径 ID 不在 body 重复。所有 command 都需统一的 `clientId` 和幂等头。

| 请求 | 字段及限制 |
|---|---|
| CreateIntent | `sourceType` 当前只允许 `COMMERCE_ORDER`；`sourceId`=Order ID；`sourceOrderContextId`；`entityNo`；`contextFormat` 必须 `TERM_PRICE`；`clientPricingVersionId`；`partnerShareVersionId?`；`platformChargeVersionId?`；`industryCode`；`mccCode` 4 位数字；`amount`；`currency`；`expiresAt` 必须晚于受理时间。Party 来源等资格由 Mock 预先配置，并验证商业版本组合；不得构造 Scope/Price 替代 ID |
| CloseCreation | `sourceType`、`sourceId`；即使从未创建 Intent 也记录关闭；若已有 Intent 只关闭新建/新尝试资格，返回该 Intent，不宣称资金终止 |
| Terminate | `reason` 固定 `ORDER_CANCELLED` 或 `OPERATING_ENTITY_CLOSED`；关闭新尝试和初始资金发送资格，再按实际资金事实决定能否终止；已经授权需 Reversal，不静默 Void |
| SubmitCard | `sourceSubmissionId`=Commerce command ID；`paymentMethodCode`=Routing/方法目录认可的精确 Card code；`paymentGoal` 为 `PAY_NOW` 或 `AUTHORIZE_ONLY`；`consumerContractId`；`consumerTermsAcceptanceId`、`refundPolicyAcceptanceId`、`privacyNoticeAcceptanceId`；`acceptanceCompletedAt`；`orderVersion`；`cardReference` ≤128 ASCII、不具备持有即用权限；`cardUseSessionRef?` ≤128、只在内存使用；`allowExecution` boolean。实际接口不接 PAN、CVV、Checkout Token、Channel 或 MID |
| Capture | `authorizationActionId`；`amount`；`currency`；仅等于原完整授权金额且与 Intent 同币种；不得指定新渠道 |
| CreateRefund | `commerceRefundId`；`intentId`；`originalPaymentActionId`（成功 SALE 或全额 CAPTURE）；`amount`；`currency`。RefundRequest 与 Payment 原交易必须对应同一 Client/Intent；支持部分/全额、不得超额 |
| Reversal | `reason` 固定 `COMMERCE_REVERSAL`；读取锁内事实判定，不接“我认为该退款/该 Void”的客户端分支参数 |

支付目标、提交身份、Card 引用与法律接受事实受理后不可修改。`allowExecution` 是本次许可，`cardUseSessionRef` 是瞬时输入，都不改变命令业务身份；`orderVersion` 是本次资格证据，首次受理冻结，重放可带更新的版本供当前资格检查，但不能改写原法律接受。换卡、换目标、换法律接受集合需新的 Commerce 提交身份，并先满足新 Attempt 条件。

## 4. 响应字段与状态

所有响应包含 `observedAt`；业务 ID 字段只在对应资源存在时返回。以下响应均包含 `clientId`，无敏感详情或内部凭据。

| 响应 | 字段 |
|---|---|
| IntentView | `intentId, sourceType, sourceId, entityNo, amount, currency, status, newAttemptsAllowed, activeAttemptId?, successfulAttemptId?, expiresAt, version, observedAt`；status 为 Intent 枚举，AUTHORIZED 通过 Attempt 表达；newAttemptsAllowed 仅为当前本地门禁投影，true 不替代提交时实时资格检查 |
| CloseResult | `sourceType, sourceId, closedAt, intentId?, observedAt`；固定关闭创建资格，不能据此向用户宣告“未扣款” |
| TerminationResult | `intentId, decision, blockingActionId?, observedAt`；decision=`TERMINATED / ALREADY_TERMINATED / PENDING / REVERSAL_REQUIRED / ALREADY_PAID`；PENDING 用 202，其余 200 |
| SubmissionView | `sourceSubmissionId, intentId, attemptId, paymentMethodCode, paymentGoal, status, authorizedAmount, capturedAmount, actionId?, nextAction?, failureCode?, last4?, observedAt`；status 为 Attempt 枚举，last4 仅有可信 Card 描述时返回 |
| ActionView | `actionId, intentId, attemptId?, refundId?, type, status, amount, currency, targetActionId?, failureCode?, observedAt`；不暴露 Channel 凭据、幂等键或远程原始响应 |
| RefundView | `refundId, commerceRefundId, intentId, originalPaymentActionId, amount, currency, status, actionId?, failureCode?, observedAt`；status 为 Refund 枚举，内部 UNKNOWN 可返回给 Commerce 内部查询，不能作为 RefundFailed 事件 |
| ReversalResult | `intentId, decision, actionId?, originalPaymentActionId?, amount?, currency?, observedAt`；decision=`VOID_EXECUTING / VOIDED / VOID_FAILED / REFUND_REQUIRED / PENDING / NOT_ELIGIBLE`；前两种执行中情况 VOID_EXECUTING/PENDING 用 202。REFUND_REQUIRED 的原支付 ID/金额/币种必填，Void 相关结果 actionId 必填 |
| PricingFactView | `intentId, readiness, fact? , missingFields, observedAt`；readiness=`NOT_PAID / PENDING / READY`；READY 时 fact 必填且 missingFields 为空，其他状态 fact 为空。缺事实不把 Intent 从 SUCCEEDED 降级 |

`fact` 明确包含现有 Pricing Fact 的 `pricingFactId, factSchemaCode, attemptId, sourceActionId, paymentMethodCode, consumerCountryCode, currency, amount, cardNetwork?, fundingType?, cardLevel?, authenticationClass?, clientPricingVersionId, partnerShareVersionId?, platformChargeVersionId?, paymentSucceededAt`。pricingFactId 映射 payment_fact_id，factSchemaCode 固定 PAYMENT_PRICING_FACTS_1；其他字段按现有列语义投影。Card 本期所有适用维度必须满足固定商业版本的必填要求，否则 PENDING；不得用 UNKNOWN/空串凑 READY。

`nextAction` 是互斥的响应联合类型：

- `THREE_DS_CHALLENGE`：`type, actionId, expiresAt, deliveryStatus` 必填；`deliveryStatus=AVAILABLE` 时 `redirectUrl` 必填，仅允许 Channel 适配器校验过的 HTTPS URL；`UNAVAILABLE` 时 URL 缺省。读回原指令，不创建新的认证。URL 只在当前响应中存活。
- `PROVIDE_CARD_INPUT`：`type, sourceSubmissionId, expiresAt` 必填；调用者在原提交下补充新瞬时材料。此时可以尚无资金 Action，不伪造 actionId。期限为原 Intent 期限；GET 不因到期自行写状态。

其他支付方式的 nextAction 尚未开放。`failureCode` 为稳定的受控原因目录，当前最小值包括 `RISK_DENIED / RISK_UNAVAILABLE / ROUTE_UNAVAILABLE / AUTHENTICATION_FAILED / AUTHENTICATION_EXPIRED / AUTHENTICATION_REQUIREMENT_NOT_MET / CHANNEL_DECLINED / EXECUTION_CLOSED / INPUT_REQUIRED / RESULT_UNKNOWN`；无失败原因时为空，HTTP 错误码不逐节点复制此目录。

## 5. 幂等、重放和错误

1. **先查原身份，再做新执行资格判断。** 命中同提交且内容一致时返回原资源当前观察结果；expiry、关闭、`allowExecution=false` 不能让已成功结果消失。尚在等待输入且 allowExecution=true 时，相同 Card 引用的新瞬时材料可恢复原执行，仍需当前门禁；其他重放不因此重新发送资金，终态只读返回。
2. `allowExecution=false` 只重放/查询；原身份不存在返回 404，不创建命令/Attempt/Action，也不触发 Card/Risk/Routing。true 仍受 Payment 的当前状态、关闭、OE、认证及用卡门禁约束。
3. 同 scope/op/key 异请求返回 409；提交 ID 已属于另一 Intent/Client 返回 409（不泄露另一资源 ID）；即使用新 key，也不能绕开 sourceSubmissionId、source order、原授权 Capture、Commerce Refund 唯一约束。
4. C 表只在单次短事务内记录“命令已受理及资源定位”，`SUCCEEDED` 不是资金成功；同 key 返回相同资源身份与当前状态，不承诺字节级响应不变。该响应可变化，不保存临时 nextAction。
5. Request Hash 由 Payment 用固定、版本化的字段顺序和长度前缀 UTF-8 编码计算 SHA-256，字段取值规范化明确；不散列 raw card/CVV/临时指令。V1 操作类型带版本，编码：每个字段为 `字段名字节长度:字段名|值字节长度:值|`，null 值长度 `-1`、空值长度 `0`；数字/金额按本稿规范化，时间统一 UTC 微秒。SubmitCard 排除 `allowExecution/cardUseSessionRef/orderVersion`，其余 body 加路径 Intent ID 均参与；其他命令全部业务字段和路径 ID 参与。新编码需新操作版本及兼容读取，不能改变既有 key 的解释。本期不新增 Foundation canonicalization/命令幂等消费者迁移。
6. Hash 中金额先去除小数末尾零及无意义小数点，例如 100.00 与 100 统一为 100；ID 保留合法十进制格式，code 不做自由大小写修正；字段按本稿请求字段表顺序，clientId 最前、路径 ID 其次排列。认证引用、Risk 结果等后续派生字段不反过来改变原请求 Hash。
7. 捕获/退款响应丢失后查原资源；外部调用超时不能当 503 引导新付款，已受理则返回 202 + 原身份 + UNKNOWN。

HTTP 错误遵循[统一 Problem 合同](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)：400 输入/未支持方式、401/403 身份权限、404 无匹配资源、409 幂等/资金状态冲突、503 受理前依赖不可用、500 未预期内部错误。`application/problem+json`，无包装、无 body status；必填 `type,title,detail,code,requestId,timestamp`，字段错误才有 `errors`，`X-Request-Id` 与 body 一致。细节不包含其他 Client 或卡引用。

例如 409 使用 Foundation 通用 CONCURRENT_REQUEST 表达业务冲突，不为每一步创建专用 Exception：

```json
{"type":"about:blank","title":"Conflict","detail":"原命令与本次支付内容不一致。","code":"CONCURRENT_REQUEST","requestId":"req-execution-example","timestamp":"2026-09-14T09:00:00Z"}
```

## 6. 示例与现有消费者衔接

CreateIntent 示例：

```json
{"clientId":"101","sourceType":"COMMERCE_ORDER","sourceId":"201","sourceOrderContextId":"202","entityNo":"AE001","contextFormat":"TERM_PRICE","clientPricingVersionId":"301","industryCode":"RETAIL","mccCode":"5732","amount":"100.00","currency":"AED","expiresAt":"2026-09-14T10:00:00Z"}
```

SubmitCard 示例（引用均为合成数据，`Idempotency-Key: submission:401`）：

```json
{"clientId":"101","sourceSubmissionId":"401","paymentMethodCode":"CARD_TEST","paymentGoal":"AUTHORIZE_ONLY","consumerContractId":"501","consumerTermsAcceptanceId":"502","refundPolicyAcceptanceId":"503","privacyNoticeAcceptanceId":"504","acceptanceCompletedAt":"2026-09-14T09:00:00Z","orderVersion":"3","cardReference":"test_card_401","cardUseSessionRef":"test_use_401","allowExecution":true}
```

`CARD_TEST` 仅为测试目录精确 code，不能成为生产默认方法。原提交查询/提交返回等待挑战：

```json
{"clientId":"101","sourceSubmissionId":"401","intentId":"601","attemptId":"701","paymentMethodCode":"CARD_TEST","paymentGoal":"AUTHORIZE_ONLY","status":"REQUIRES_ACTION","authorizedAmount":"0.00","capturedAmount":"0.00","actionId":"801","nextAction":{"type":"THREE_DS_CHALLENGE","actionId":"801","expiresAt":"2026-09-14T09:05:00Z","deliveryStatus":"AVAILABLE","redirectUrl":"https://challenge.example.invalid/mock/801"},"observedAt":"2026-09-14T09:00:01Z"}
```

其余请求/响应逐项实例见 [API 示例集](./api-examples.json)，均是合同样例，不是调用结果。真实 Commerce Port 仍持有 `requestHash`、CardInput、接受回执及订单身份；将来适配必须核对它与本稿 `sourceSubmissionId → Attempt` 的一致性，并按已有 Order/Intent/Session 授权后查询。这里没有把 Commerce Hash 当作 Payment 自己的幂等验证结论。

# Payment Orchestration 平衡型物理模型设计

> **已被后续决策取代：** 本文中的旧 31 表/399 字段与后续 29 表/382 字段基线、`t_payment_execution_gate`/`t_payment_execution_gate_result` 持久化设计、`t_three_ds_context` 和 `provider_token_ref` 均已取消。当前权威依据为 [Payment Execution 精简设计](./2026-08-25-payment-execution-simplification-design.md)、[`PAYMENT_ORCHESTRATION_DATABASE.md`](../database/PAYMENT_ORCHESTRATION_DATABASE.md) 与 [`PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`](../../payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)，当前基线为 29 表/376 字段/63 关系。Provider Account/MID 精简文档仅对对应五表模型的历史决策仍有参考意义；本文其余内容仅供历史追溯。

> **已局部被替代：** [Payment Provider Account 与 MID 精简设计](./2026-08-25-payment-provider-account-mid-simplification-design.md) 已确认并覆盖本文 Provider/MID、Connection Version、MID Credential、MID Limit、Health 与相应下游冻结字段。本文件保留为 34 表阶段的历史设计记录；其余未被覆盖的支付、退款、回调、争议与可靠执行决策继续仅作历史对照，当前权威模型以新设计及同步后的逻辑数据库文档为准。

最后更新：2026-08-25  
状态：已获准按本设计生成第一版 PDManer 模型；待用户在 PDManer 4.9.3 中人工评审

## 1. 目标与范围

本设计把 Payment Orchestration 原 58 张逻辑表收敛为 34 张物理表，在不削弱资金正确性、渠道重试、回调去重、退款并发控制和争议追溯的前提下，删除重复 History、Current Projection、严格 1:1 包装层以及没有当前消费者的 Hash、Active Slot 和 Version。

本次授权覆盖：

- Payment 权威数据库文档、现实场景和跨系统契约同步；
- Payment 物理建模决策、确定性生成器、自动契约测试；
- `payment-orchestration-service/docs/database/payment-orchestration.pdma.json`。

本次不创建 Migration、Java 模块、数据库实例、Nacos/MQ/Vault 环境，也不修改共享 PDManer 模板。

## 2. 方案比较

| 方案 | 表数 | 特点 | 结论 |
|---|---:|---|---|
| 保守型 | 约 38 | 合并重复 History，但保留 Routing Binding、Recovery Task、Conflict/Case 双表和 Dispute Projection | 安全但仍有可证明的重复结构 |
| 平衡型 | 34 | Scope 下沉 Policy；状态历史统一；退款单 Root；UNKNOWN 由业务状态与补偿任务恢复；重复支付和争议当前态各使用一个 Root | 采用 |
| 激进型 | 约 29 | 继续合并 Connection Version、Instrument、3DS、Callback/Dispute 子表 | 会损害密钥轮换、旧回调验签、权限隔离或批量回调表达，不采用 |

## 3. 最终 34 表

### 3.1 Provider 与 MID（8）

1. `t_payment_provider`：Provider 身份、Adapter 与当前状态。
2. `t_provider_connection`：稳定连接槽和当前连接版本指针。
3. `t_provider_connection_version`：Endpoint、Secret 引用和超时的精确版本。
4. `t_provider_mid`：MID、Provider、连接与唯一 Operating Entity 归属。
5. `t_mid_capability`：支付方式、Scheme、MCC、国家、币种和动作能力。
6. `t_mid_limit`：MID 单笔技术金额上下限；不复制 Risk 限额。
7. `t_mid_credential_ref`：MID 专属且可被历史 Action 精确引用的 Secret 版本。
8. `t_mid_health`：路由使用的当前健康结论，不保存原始指标时序。

### 3.2 Routing 与执行门禁（7）

9. `t_routing_policy`：一个确定交易 Scope 的稳定策略 Root和当前发布版本指针。
10. `t_routing_policy_version`：不可变候选集发布版本。
11. `t_routing_policy_mid`：版本内 MID、优先级和权重。
12. `t_routing_decision`：每个 Attempt 的不可变最终路由结论，并冻结选中连接版本。
13. `t_route_candidate_eval`：全部候选、候选连接版本及主要淘汰原因。
14. `t_payment_execution_gate`：资金增加动作提交前的同步权威检查结论。
15. `t_payment_execution_gate_result`：Client、Operating Entity、Compliance 三类来源结果。

### 3.3 支付执行与异常（9）

16. `t_payment_intent`：一个 Commerce Order 的唯一支付目标。
17. `t_payment_attempt`：同一 Intent 下顺序发生的一次支付尝试。
18. `t_payment_instrument`：Provider Token 与最小卡片描述；与 Attempt 0..1 对应并独立授权。
19. `t_payment_action`：`AUTHORIZE/CAPTURE/SALE/VOID/REFUND/QUERY` 真实渠道动作。
20. `t_three_ds_context`：3DS 当前流程与认证结果。
21. `t_provider_transaction_ref`：所有授权、捕获、扣款、撤销和退款外部交易号。
22. `t_provider_interaction`：每次真实 Provider HTTP 交互及可选受控文件证据。
23. `t_duplicate_payment_case`：第二笔真实渠道成功及其补偿处置。
24. `t_payment_state_transition`：Payment 库内白名单聚合的统一状态流转。

### 3.4 Channel Refund（2）

25. `t_payment_refund_position`：Payment 侧渠道可退金额并发控制。
26. `t_channel_refund`：一个 Commerce Refund Request 的唯一渠道退款 Root。

### 3.5 Provider Callback（3）

27. `t_provider_callback`：验签、去重和批次处理信封。
28. `t_provider_callback_item`：批量 Callback 中逐项归一化和目标映射。
29. `t_provider_event_exception`：签名、匹配、乱序或状态冲突的独立处置 Case。

### 3.6 Provider Dispute（3）

30. `t_provider_dispute_notice`：Provider 争议身份、冻结资金事实及当前渠道状态。
31. `t_provider_dispute_event`：不可变 Provider 争议事件流。
32. `t_provider_dispute_action`：按 Risk 指令真实提交举证或查询。

### 3.7 可靠执行（2）

33. `t_command_idempotency`：同步写命令的业务幂等和结果定位。
34. `t_compensation_event`：提交后的 MQ 发布和本地恢复任务。

## 4. 关键精简决策

### 4.1 统一支付运行态流转，删除专用 History

`t_payment_state_transition` 只记录获批支付运行态对象的状态变化，不保存 JSON 快照、Provider 报文或任意字段差异；Provider/MID/连接与路由配置变更采用 Platform 操作审计和当前结构化状态，不在此复制第二套配置 History。核心字段为：

```text
transition_id
aggregate_type / aggregate_id
root_type / root_id
from_status / to_status
transition_no
reason_code
trigger_type / trigger_id
create_by
create_time
```

`uk_payment_transition_no(aggregate_type,aggregate_id,transition_no)` 保证同一对象的顺序；`idx_payment_transition_root(root_type,root_id,create_time)` 支持完整 Intent、Refund、Provider 或 Dispute 时间线。`transition_no` 只表示第几次状态变化，不是乐观锁。

### 4.2 Intent 指针代替 Attempt Slot

保留 `t_payment_intent.active_attempt_id` 与 `successful_attempt_id`，删除 Attempt 的 `active_slot/success_slot`。创建、终止和成功处理都必须锁定 Intent，指针、Attempt、状态流转和业务事件在同一事务提交。

`t_payment_intent.version` 保留，因为支付回调、主动查询、Cancel、主体关闭和超时恢复会并发修改同一 Root。`t_payment_attempt.version`、`t_payment_action.version` 同样保留，用于回调、查询和恢复的 Expected Version/CAS；不可变证据和静态明细不机械增加 Version。

### 4.3 Attempt 只保存稳定生命周期

Attempt 状态固定为：

```text
CREATED
PROCESSING
REQUIRES_ACTION
AUTHORIZED
UNKNOWN
SUCCEEDED
FAILED
CANCELLED
VOIDED
CONFLICTED
```

删除 `RISK_CHECKING/RISK_REJECTED/RISK_FAILED/LIMIT_REJECTED/ROUTING/SUCCESS_CONFLICT`。失败用 `failure_stage + status_reason_code` 表达；`failure_stage` 允许 `READINESS/EXECUTION_GATE/RISK/LIMIT/ROUTING/PROVIDER/RECOVERY/SYSTEM`。

COM-I10 返回的 Consumer Contract、三条 Acceptance、完成时间和 Order Version仍冻结在 Attempt，用于证明该次支付开始前的法律准备状态；不增加集合 Hash、集合版本或 Summary。Risk 只冻结 Assessment ID、实际结果和 Reservation Group ID，不复制 Risk Reservation 当前状态。

### 4.4 一次支付可多次 Attempt，但只能一个活动和一个成功

- 创建 Attempt 前锁定 Intent，要求活动指针为空、尚未成功且未过期。
- 普通失败清空活动指针并使 Intent 回到 `REQUIRES_PAYMENT`。
- `UNKNOWN` 继续占用活动指针，不能创建下一 Attempt。
- 第一笔权威 SALE/全额 Capture 成功设置成功指针。
- 第二笔迟到真实成功不能覆盖指针，Attempt 进入 `CONFLICTED` 并创建 `t_duplicate_payment_case`。

### 4.5 V1 单次全额 Capture，保留未来多 Capture 兼容边界

V1 允许直接 `SALE`，或一次 `AUTHORIZE` 后一次全额 `CAPTURE`。数据库不建立“每个 Attempt 只能一条 CAPTURE”的永久唯一约束；业务校验在 V1 阻止第二次 Capture。Attempt 保留累计 `authorized_amount/captured_amount`，Action 保留独立金额，未来扩展部分/多次 Capture 不需要重做主关系。

### 4.6 Routing Scope 下沉到 Policy

删除 `routing_policy_binding`、`routing_rule`、`routing_rule_condition`。当前没有“一份候选版本被多个独立 Scope 复用”的已确认需求，因此一个 `t_routing_policy` 直接拥有：

```text
scope_type = CLIENT / OPERATING_ENTITY / GLOBAL
scope_id   = Client ID / Operating Entity ID / 0
country_code / currency / payment_method_code / industry_code / mcc_code
current_version_id
```

配置字段统一非空，明确使用 `*` 表示获批通配，不使用 Scope Hash。解析顺序固定为 `CLIENT > OPERATING_ENTITY > GLOBAL`，同层按精确维度数量和 `match_priority` 确定唯一策略；仍并列则返回配置冲突。版本内候选先做 OE、MID 状态、方式、国家、币种、MCC、金额、能力、限额和健康硬过滤，再取最小候选优先级并按权重选择。

`t_routing_decision` 在 `SELECTED` 时必须冻结 `selected_connection_version_id`；`CONFIGURATION_CONFLICT` 没有唯一 Policy，因此 Policy/Version 和选中 Provider/MID/Connection 必须为空。被排除 Candidate 若没有 Current Connection Version，其 `connection_version_id` 允许为空。选中 Candidate、Decision 与首个 `AUTHORIZE/SALE` Action 的 MID 和 Connection Version 必须完全一致。同一 Action 的网络重发不得漂移；后续独立 `QUERY/REFUND/DISPUTE` Action 可以按创建时配置冻结新的连接版本。

### 4.7 Gate 只保护增加资金风险的 Provider 提交

删除 Restriction Projection/Fact。`AUTHORIZE/SALE/CAPTURE` 首次提交前同步查询 CCT 与 Risk，并在 `t_payment_execution_gate + result` 冻结本次实际使用的权威资源 ID、Version、状态和原因。PASS Gate 不保存虚假的失败阶段或原因；来源技术失败且没有取得权威事实时，Result 的资源 ID/Version 成对为空并以 FAILED + Reason 失败关闭。

Attempt 创建仍实时核验 Readiness、主体/Client 与 Risk，但被阻止的请求可以形成一个真实 `FAILED` Attempt。`VOID/REFUND/QUERY` 和重复扣款补偿属于减损动作，不受收款限制阻断。Gate 不保存 Scope Hash、Content Hash、Projection Set Hash、有效期缓存或 MQ 预热投影。

不设置 `ATTEMPT_CREATE` Gate，也不让 Gate 成为 Attempt 的创建凭证：Attempt 自身的 `failure_stage=READINESS/RISK/LIMIT` 已是失败事实；Gate 仅与即将提交 Provider 的资金 Action 一对一，避免“创建门禁”和“提交门禁”形成两套生命周期。

### 4.8 Connection Version 是必须保留的少量版本

`t_provider_connection_version` 不能并回当前 Connection：

- 原 Action 网络重发必须复用原 Endpoint 与 Credential；
- 密钥轮换期间旧 Provider Callback 必须能用旧 Webhook Secret 验签；
- Refund、Query 和 Dispute Action 必须证明实际使用的连接配置。

Version 状态精简为 `DRAFT/ACTIVE/VERIFY_ONLY/RETIRED`；不做 Maker-Checker，不保存 `approved_by/approved_at/content_hash/active_slot`。Connection 的 `current_version_id` 是唯一出站权威指针，但首次发布前必须允许为空，以解除 Root 与 Version 的循环外键；Connection 为 ACTIVE 时必须非空。Routing Policy 的当前版本指针遵守同一规则。

激活连接或路由策略版本时必须锁定稳定 Root，在同一事务退役旧版本、激活新版本并切换 `current_version_id`。运行时只能读取 Root 指针，禁止以“任意 ACTIVE 子版本”作为替代入口。

### 4.9 Refund 只保留一个执行 Root

删除严格 1:1 的 `channel_refund_attempt`，`t_channel_refund` 自己保存状态；`t_payment_action` 通过 `channel_refund_id` 承载 REFUND/QUERY。删除 `refund_transaction_ref`，退款结果统一写 `t_provider_transaction_ref(reference_type=REFUND)`。

Payment 仍保存 Finance Funding Reservation ID、请求版本、绑定版本和绑定 Hash，因为它们是提交 Provider 前实际校验的资金绑定证据；Commerce 不复制这些下游版本或 Hash。

### 4.10 UNKNOWN 恢复不建立重复任务表

删除 `payment_recovery_task`。UNKNOWN 当前状态保存在 Attempt、Action、Channel Refund、Duplicate Case 或 Dispute Action；`t_compensation_event(execution_type=LOCAL_HANDLER)` 提供 due time、次数、Claim/Lease、错误和人工重开。权威查询结果通过原聚合状态、统一状态流转和 Provider Interaction 留痕。

### 4.11 重复支付 Conflict 与 Case 合并

删除 `payment_state_conflict`，由 `t_duplicate_payment_case` 同时保存 Canonical/Duplicate Attempt、Action、两条 Provider Transaction Ref、金额、补偿 Action 与结果。非资金重复、状态矛盾或无法匹配问题进入 `t_provider_event_exception`，不提前建设通用 Conflict Root。

### 4.12 Dispute 使用“当前 Root + 不可变 Event”

删除 `provider_dispute_projection`。`t_provider_dispute_notice` 保存不可修改的 Provider/交易/金额身份字段，同时维护 `current_channel_status/latest_event_id/latest_event_no/version`；`t_provider_dispute_event` 保存完整追加事件。Notice Root 自己承担并发序号和当前查询，不再维护严格 1:1 Projection。

## 5. 状态与并发边界

### 5.1 Intent

`REQUIRES_PAYMENT/PROCESSING/REQUIRES_ACTION/SUCCEEDED/FAILED/EXPIRED/CANCELLED`。

只有 SALE 或累计 Capture 等于 Intent Amount 才可 `SUCCEEDED`。Authorization 只使 Attempt 进入 `AUTHORIZED`，Intent 保持处理中。Cancel 是未支付关单；Void 成功使 Attempt `VOIDED`、Intent `CANCELLED`；已 Capture/Sale 的 Reversal 走标准 Channel Refund，Order 仍保持 PAID。

### 5.2 Action

`CREATED/SUBMITTED/REQUIRES_ACTION/UNKNOWN/SUCCEEDED/FAILED`。Action 创建时冻结 Provider、MID、Connection Version、可选 MID Credential Ref、金额币种、Provider Idempotency Key 和可选目标交易引用。网络重发复用同一 Action，不创建第二条 Action。

### 5.3 Channel Refund

`CREATED/PROCESSING/UNKNOWN/SUCCEEDED/FAILED/CANCELLED`。UNKNOWN 继续占用 Payment Refund Position；SUCCEEDED 把 Reserved 转为 Refunded；FAILED/CANCELLED 释放 Reserved。

## 6. 数据安全

- PAN、CVV/CVC、完整磁道、PIN/PIN Block 和卡有效期不得进入数据库、Vault、事件、文件或日志。
- Payment 只保存 Provider Token、Scheme、最多八位 BIN、Last4、Issuer Country 与 Provider Fingerprint。
- Provider/API/Webhook Secret 和私钥只保存 Secrets Manager/KMS 精确版本引用。
- Provider 原始信封不是默认留档内容；只有通过 Schema、卡数据与 Secret 扫描后，才可把受控加密文件的 `file_id + content_hash` 关联到 Callback 或 Interaction。
- Consumer Email/Phone Stable Token 和 Network Evidence 只在 Commerce→Payment→Risk 同步风险链路透传，Payment 不持久化，也不进入 MQ、普通日志、BCR 或 Compensation Payload。

## 7. 物理与验证规则

- 全部物理表使用 `t_`；唯一索引使用 `uk_`；普通索引使用 `idx_`。
- 表与字段显示名、说明使用直接、可独立理解的中文；闭合枚举完整列值，开放代码注明权威目录。
- 主键为非自增 Snowflake `BIGINT`；金额为 `DECIMAL(24,8)`；比例为 `DECIMAL(18,10)`；时间为 UTC `DATETIME(6)`；币种为 ISO 4217 `CHAR(3)`。
- 不可变 Decision、Candidate、Gate Result、Transaction Ref、Callback Item、Dispute Event 和 State Transition 只保留 `create_time`。
- PDManer 只建立 Payment 本库关系；Commerce、CCT、Risk、Finance、Platform、Vault、Actor 和文件 ID 均不建跨库外键。
- 生成器必须从共享模板确定性生成，`--check` 字节校验、模型结构测试、场景覆盖测试、CCT/Commerce 回归和 requirements-site 测试全部通过。
- PDManer 4.9.3 应用内打开、关系图检查和规范检查由用户人工完成，自动 JSON 校验不能替代该门禁。

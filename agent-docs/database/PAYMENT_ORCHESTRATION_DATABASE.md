# Payment Orchestration 数据库设计规范

> **2026-09-21 Channel 配置后继：** [范围决定](../decisions/2026-09-21-channel-account-mid-capability-queries.md)与[需求正文](../../requirements/20260921/payment-渠道账号与MID能力查询/requirement.md)精确取代第 3 节受影响目标：删除 adapter_code、账号增加 OE/名称/备注及受保护配置引用方向、能力拆为主从表且明细保存 value_type、保留 country_code、scheme_code 表示支付体系，本期移除健康状态表。两个主链路查询与 SQL 变化见需求；消费者国家、账号 Vault JSON 例外和原交易停用矩阵已确认，详细接口见[实施合同](../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)。下文未同步的相关描述是迁移前基线，正式模型、SQL 和环境状态见[当前台账](../CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。

> **Action Flow 后继：** [CARD + PAY 修订](../../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)统一自有动作 PAY_RISK / PAY 名称、条件对象和执行位切换；AUTHORIZE 成功结束本次 Flow，Capture 为独立入口。前序 RISK_CHECK / SALE 表述按该范围被取代。

> **当前字段入口：** [2026-09-14 整批字段精简](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md)取代下列前序决定中受影响的字段。本文三表、路由与退款导航已合并该修订；历史文档和未迁移代码不反向定义当前目标。

> **内部风控 Action 后继：** [已确认修订](../../requirements/20260913/payment-Execution设计/internal-risk-action-amendment.md)将首次评估记录为 RISK_CHECK，承接 Attempt 的评估 ID 与结果。风险步骤也占 active_action_id，由 Risk 恢复，渠道字段为空；渠道步骤保持完整路由与幂等身份。工具切换遵守同稿的新提交/新 Token/新 Attempt 边界，后续字段取舍见本页顶部当前入口。

> **2026-09-14 限额引用后继：** [Payment/Risk 限额关联修订](../../requirements/20260913/payment-Execution设计/risk-limit-correlation-amendment.md)删除 Payment Attempt 的 limit_reservation_group_id 目标字段，Risk 在本地按原 Attempt 定位占用。仅取代受影响字段与消息输入要求；Risk 内部占用机制保留，实现与迁移状态见当前台账。

> **2026-09-14 状态与恢复后继：** capture mode、Intent/Attempt/Action 状态、active_action_id 及删除 QUERY/target_payment_action_id 的目标以[本次修订](../../requirements/20260913/payment-Execution设计/execution-state-channel-recovery-amendment.md)为准；本页及前序 Execution 方案的受影响部分保留为历史基线，其他业务边界继续有效。正式模型、SQL 与数据库状态见当前台账。

> **Commerce 调用方向：** [2026-09-14 修订](../../requirements/20260913/payment-Execution设计/payment-commerce-direction-amendment.md)禁止 Payment 反向调用 Commerce；Intent 新增 `consumer_country_code CHAR(2) NOT NULL`，上游创建时传入且不可变，定价从本地读取。无新增表或索引，执行状态见台账。

> **交易工具后继：** [已确认的交易级 Token 修订](../../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)取代本页旧 Instrument、卡引用与首次支付入口描述；其余资金和渠道业务边界保留。数据库执行状态见当前台账。

> **2026-09-11 模型更新：** 本页的数字 OE、Assignment、Client 多 OE 合同及旧 Scope 冻结描述，仅适用于迁移前记录与接口；现行目标由 [四系统模型决定](../decisions/2026-09-11-cct-commerce-oe-pricing-model.md) 精确取代。Risk 本期不改，Java 与数据库尚未据此迁移，历史订单与 Hash 不重写。

最后更新：2026-09-14

> **Execution 后继范围：** [已确认方案](../../requirements/20260913/payment-Execution设计/data-design.md)精确取代本页的消费者交互表与旧补偿字段：Attempt 原提交/目标、Action AUTHENTICATE/交互事实按该方案；所有实际数据库状态见当前台账。

状态：本期模型采用 2026-09-11 四系统调整；生成统计、验证、Java 与数据库迁移状态统一见 [当前实现台账](../CURRENT_IMPLEMENTATION.md)。

> **2026-09-05 跨主题对齐：** 本文拥有 Payment 业务事实，不单独拥有 Vault、`compensation_event`、物理 CHECK/FK 或测试基础设施规范。当前生成模型中的旧横切字段是 [当前实现台账](../CURRENT_IMPLEMENTATION.md) 记录的迁移债务；新增实现必须遵循 [文档治理路由](../DOCUMENT_GOVERNANCE.md)，不得复制旧契约。

## 1. 目的、边界与统一约定

本文是 Payment Orchestration 的唯一逻辑数据库基线，也是 PDManer 生成器、模型测试、现实场景和后续跨系统契约的共同输入。表名已经使用将来物理模型的 t_ 前缀；所有跨系统 ID 均为业务引用，不建立跨库外键。

Payment 拥有 Channel/MID 配置、路由选择、支付 Intent/Attempt/渠道动作、Channel Refund、Channel 回调、渠道争议和本地可靠执行事实。它不拥有 Order、Checkout Session、消费者合同或退款业务资格；不拥有 Client、Operating Entity、Risk 规则/争议业务结论、Finance 会计账本/结算、文件二进制、IAM 或 Channel Secret。

Payment Intent 用 source_type + source_id 关联来源；MVP 的 source_type 为 COMMERCE_ORDER，但模型不得把来源语义硬编码为 Checkout。Commerce 已冻结 Client、entity_no、industry_code、mcc_code、金额、币种、消费者国家及条款版本；Payment 只按这些时点事实路由，Client 不能选择 Operating Entity、Channel 或 MID。

- 主键是服务生成的 Snowflake BIGINT，时间为 UTC DATETIME(6)，金额为 DECIMAL(24,8)，权重/比例为 DECIMAL(18,10)，币种为 ISO 4217 CHAR(3)，country_code 为 ISO 3166-1 alpha-2。配置 Scope 的 country_code 可以使用获批 *；Intent、Attempt、Action、Ref、Callback 等交易事实不允许 *。
- 可变 Root 的 version 使用 expected-version CAS；不可变证据和版本内明细不机械保存 version。
- Channel/API/Webhook Secret、私钥、证书、Endpoint、超时及其轮换版本不进入 Payment 数据库；Adapter 仅以 Channel Account 的非敏感 integration_config_key 定位运行配置。PAN、CVV/CVC、完整磁道、PIN/PIN Block、卡有效期、Channel Secret、临时 Redirect URL、Client Secret、SDK Token、QR Payload、未审查原文和 Consumer Email/Phone Token/Network Evidence 原值不得进入表、日志、MQ 或补偿 Payload。
- Channel 原始信封默认不留档；仅在 Schema、卡数据与 Secret 扫描通过后，可保存受控加密文件的 file_id + content_hash 引用。
- CANCEL 不是 Payment Action；VOID 是未捕获成功 AUTHORIZE 的全额释放；Reversal 是 Commerce 命令，Payment 原子选择 VOID 或标准全额 REFUND。SALE 成功或累计 CAPTURE 等于 Intent 金额才是 PaymentSucceeded。
- 开放代码必须有权威目录：adapter_code 使用 Adapter Catalog，payment_method_code 使用 Payment Method Catalog，scheme_code 使用 Scheme Catalog，capability_code 使用 MID Capability Catalog，industry_code/mcc_code 使用 CCT/Commerce 冻结分类，reason_code 使用 Reason Catalog，trigger_type 使用 Trigger enum，Channel 返回码使用对应 Channel Adapter Catalog。未列为开放代码的状态/类型字段均为本文逐项列全的闭合枚举。

## 2. 表目录

| 分组 | 数量 | 表 |
|---|---:|---|
| Channel 与 MID | 5 | t_payment_channel、t_channel_account、t_channel_mid、t_mid_capability、t_mid_health_state |
| Payment Routing | 5 | t_routing_policy、t_routing_policy_version、t_routing_policy_mid、t_routing_decision、t_route_candidate_eval |
| 支付执行与异常 | 7 | t_payment_intent、t_payment_attempt、t_payment_instrument_token、t_payment_action、t_channel_transaction_ref、t_channel_interaction、t_duplicate_payment_case |
| Channel Refund | 2 | t_payment_refund_position、t_channel_refund |
| Channel Callback | 3 | t_channel_callback、t_channel_callback_item、t_channel_event_exception |
| Channel Dispute | 3 | t_channel_dispute_notice、t_channel_dispute_event、t_channel_dispute_action |
| 可靠执行 | 2 | t_command_idempotency、t_compensation_event |

## 3. Channel 与 MID（5）

### t_payment_channel

Channel 身份、Adapter 和运行状态。字段：channel_id、channel_code、channel_name、adapter_code、channel_status、create_time、update_time。uk_payment_channel_code(channel_code) 保证外部稳定代码唯一。channel_status 取值 DRAFT、ACTIVE、SUSPENDED、CLOSED；channel_code 创建后不可改，CLOSED 不再恢复。状态命令携带 expected current status，防止并发状态切换；不保存机械 version、可执行 Adapter 或脚本。

### t_channel_account

Channel Account 是 Payment Adapter 调用渠道 API 的技术认证账户或租户，不是 MID，也不是 Finance 的清算账户。字段：channel_account_id、channel_id、account_code、external_account_id、integration_config_key、account_status、create_time、update_time。uk_channel_account_code(channel_id, account_code)、uk_channel_account_external(channel_id, external_account_id)、uk_channel_account_config(channel_id, integration_config_key) 与 uk_channel_account_owner(channel_account_id, channel_id) 保证 Channel 内配置与归属唯一；idx_channel_account_status(channel_id, account_status) 支持路由过滤。account_status 取值 DRAFT、ACTIVE、SUSPENDED、CLOSED；account_code 创建后不可改，停用使用 Expected Current Status 条件更新，不保存机械 version。integration_config_key 是非敏感运行配置定位键，不保存 Endpoint、Secret、证书、Webhook 密钥或其版本；Channel 未提供 external_account_id 时允许为空。

### t_channel_mid

Channel MID 与其唯一 Operating Entity 归属及当前执行账户绑定。字段：mid_id、channel_id、channel_account_id、external_mid_code、entity_no、mid_status、activated_at、closed_at、create_time、update_time。uk_channel_mid(channel_id, external_mid_code) 唯一；idx_channel_mid_account(channel_account_id, mid_status) 与 idx_channel_mid_entity(entity_no, mid_status) 支持路由。一个 Channel 有多个 Account 和 MID，一个 Account 可服务多个 MID；MID 同时保存 channel_id 用于直接表达渠道归属、Channel 内唯一 MID 号和停用筛选。创建或调整 MID 必须验证 channel_mid.channel_id = channel_account.channel_id；channel_account_id 仅能在 MID 非活动时由受控配置命令调整，历史 Attempt 已冻结账户不漂移。mid_status 取值 DRAFT、ACTIVE、SUSPENDED、CLOSED；entity_no 创建后不可变，归属错误须关闭旧 MID 并新建。

### t_mid_capability

MID 的固定路由 Scope 加有类型 Key-Value 静态能力。字段：mid_capability_id、mid_id、payment_method_code、scheme_code、country_code、currency、mcc_code、capability_code、value_type、boolean_value、decimal_value、integer_value、code_value、capability_status、create_time、update_time。Scope 五列均非空：payment_method_code 必须使用精确 Payment Method Catalog 值；scheme_code、country_code、currency、mcc_code 可以使用精确目录值或控制通配符 *。capability_code 来自 MID Capability Catalog。value_type 取 BOOLEAN、DECIMAL、INTEGER、CODE，每行必须且只能填写对应的一个值列，boolean_value 只允许 0/1；capability_status 取 ENABLED、DISABLED。uk_mid_capability_scope(mid_id, payment_method_code, scheme_code, country_code, currency, mcc_code, capability_code) 唯一，不保存 Hash、Version、有效期或 JSON。ACTION.SALE、ACTION.AUTHORIZE、ACTION.CAPTURE、ACTION.VOID、ACTION.REFUND、SECURITY.3DS_MODE、AMOUNT.MIN、AMOUNT.MAX 等能力统一在本表表达；同一生效 Scope 的最小金额不得大于最大金额。路由对每个必需 capability_code 选择最高 Scope 精确度的 ENABLED 行，缺失或同精确度无法唯一决胜均配置冲突并失败关闭。

### t_mid_health_state

路由需要的当前短窗口动态健康聚合，而非原始 Metrics、Trace 或时序历史。字段：mid_health_state_id、mid_id、payment_method_code、health_status、circuit_status、health_score、sample_count、technical_success_count、channel_error_count、timeout_count、technical_success_rate、channel_error_rate、timeout_rate、p95_latency_ms、consecutive_failure_count、window_started_at、window_ended_at、observed_at、valid_until、create_time、update_time。uk_mid_health_state(mid_id, payment_method_code) 唯一。health_status 取 HEALTHY、DEGRADED、UNHEALTHY、UNKNOWN；circuit_status 取 CLOSED、OPEN、HALF_OPEN；health_score 为 0..100，三个 rate 为 0..1，计数和延迟非负，sample_count = technical_success_count + channel_error_count + timeout_count，且 window_started_at < window_ended_at <= observed_at < valid_until。仅新 observed_at 更晚才覆盖当前行。technical_success_count 是协议层取得响应次数，包含正常业务拒绝，不将 Issuer/Consumer 正常拒绝算作渠道故障；阈值和原始监控数据由运行配置与监控平台管理。

## 4. Payment Routing（5）

### t_routing_policy

一个确定交易 Scope 的稳定策略 Root。字段：routing_policy_id、scope_type、scope_id、country_code、currency、payment_method_code、industry_code、mcc_code、match_priority、policy_status、current_version_id、version、create_time、update_time。uk_routing_policy_scope(scope_type, scope_id, country_code, currency, payment_method_code, industry_code, mcc_code, match_priority) 唯一；uk_route_policy_cur_ver(current_version_id) 保证一个非空策略版本至多被一个策略 Root 指为当前版本。为解除 Policy 与首个 Version 的循环外键，current_version_id 在首次发布前可空；ACTIVE 时必须非空且指向属于本 Policy 的 ACTIVE Version。scope_type 取值 CLIENT、OPERATING_ENTITY、GLOBAL；scope_id 分别是 Client ID、Operating Entity ID、0，所有匹配维度非空并可使用获批 *。policy_status 取值 ACTIVE、SUSPENDED、CLOSED。解析顺序固定为 CLIENT > OPERATING_ENTITY > GLOBAL；同层先按精确维度数量、再按 match_priority，仍并列即配置冲突。version 保护发布版本指针与策略状态切换。

### t_routing_policy_version

不可变候选 MID 集合的发布版本。字段：routing_policy_version_id、routing_policy_id、version_no、version_status、create_time。uk_routing_policy_version(routing_policy_id, version_no) 唯一。version_status 取值 DRAFT、ACTIVE、RETIRED。DRAFT 可编辑候选，发布后候选集不可修改；启用时由 policy Root 的 CAS 原子切换 current_version_id，而不是扫描任意 ACTIVE 子版本。本行不用 version，避免与 Root CAS 重复。

### t_routing_policy_mid

一个策略版本的候选 MID、优先级和权重。字段：routing_policy_mid_id、routing_policy_version_id、mid_id、candidate_priority、weight_value、create_time。uk_routing_policy_mid(routing_policy_version_id, mid_id) 唯一。已发布 Policy Version 自身控制完整候选集；版本发布后只读，候选按最小 candidate_priority，再按 weight_value 选择，权重必须为正。

### t_routing_decision

每个 Attempt 的不可变最终路由结论。字段：routing_decision_id、payment_attempt_id、routing_policy_id、routing_policy_version_id、selected_channel_id、selected_channel_account_id、selected_mid_id、decision_status、decision_reason_code、create_time。uk_routing_decision_attempt(payment_attempt_id) 保证一 Attempt 一结论。decision_status 取值 SELECTED、REJECTED、CONFIGURATION_CONFLICT。Policy ID 与 Version ID 必须成对：SELECTED 全部 Policy/Channel/Channel Account/MID 字段非空；CONFIGURATION_CONFLICT 因不存在唯一获胜策略，Policy 对与选中四字段全部为空；REJECTED 的选中四字段为空，无匹配 Policy 时 Policy 对为空，有匹配 Policy 但无合格 MID 时 Policy 对非空。selected_channel_account_id 只在 SELECTED 时必需冻结；选中 Candidate、Decision 与 Attempt 的 Channel、Channel Account、MID 必须逐值一致。重试新 Attempt 才可重选同一 Operating Entity 下的 MID。本表不可变、只追加，不保存 version。

### t_route_candidate_eval

每个决策中全部候选 MID、其执行账户、主要淘汰原因及实际使用的动态健康快照。字段：route_candidate_eval_id、routing_decision_id、channel_id、channel_account_id、mid_id、eligible_flag、candidate_priority、weight_value、exclusion_reason_code、health_status、circuit_status、health_score、technical_success_rate、timeout_rate、p95_latency_ms、sample_count、health_observed_at、create_time。uk_route_candidate_eval(routing_decision_id, mid_id) 唯一。eligible_flag 为 0/1；值为 1 时 channel_account_id 非空且 exclusion_reason_code 为空，值为 0 时 exclusion_reason_code 非空。候选在 Channel/MID/Capability/Account 静态阶段已淘汰时 channel_account_id 与动态健康字段可空；进入动态健康阶段后必须冻结完整健康快照。被选 MID 必须有 eligible_flag=1 行。该表是不可变评估证据，创建后只读，历史 Decision 不反查当前 Health State 解释当时路由。

## 5. 支付执行与异常（8）

### t_payment_intent

一个来源订单唯一的支付目标。字段：payment_intent_id、source_type、source_id、client_id、entity_no、client_pricing_version_id、partner_share_version_id、platform_charge_version_id、industry_code、mcc_code、consumer_country_code、amount、currency、intent_status、active_attempt_id、successful_attempt_id、expires_at、version、create_time、update_time。

source_type + source_id 唯一，Client、OE、金额、币种及条款版本创建后冻结；不再保存 Order Context ID 或 context_format，不反查 Commerce。intent_status 为 OPEN、SUCCEEDED、CANCELLED、EXPIRED。只有 SALE 成功或全额 CAPTURE 成功才 SUCCEEDED；成功 Void 可安全取消。active_attempt_id 保留仍有执行/授权风险的尝试，successful_attempt_id 指向首次全额收款的 canonical Attempt；指针必须属于本 Intent。资源锁、version CAS 与两个单字段唯一索引保护并发和首次成功。

### t_payment_attempt

同一 Intent 内一次工具和方式固定的支付尝试。字段：payment_attempt_id、payment_intent_id、attempt_no、source_submission_id、payment_method_code、capture_mode、payment_instrument_token_id、channel_id、channel_account_id、mid_id、attempt_status、active_action_id、authorized_amount、captured_amount、version、create_time、update_time。

attempt_status 仅 PROCESSING、SUCCEEDED、FAILED、CANCELLED。3DS、Wallet 跳转、UNKNOWN 和已授权等待 Capture 均保持 PROCESSING。AUTHORIZE 成功更新 authorized_amount；SALE/全额 CAPTURE 成功更新 captured_amount 并完成 Attempt。Capture/Void 失败仍须保留未释放授权，不能把 Attempt 简单结束为失败。

支付工具直接关联 Token 内部主键；新执行必须有 source_submission_id、capture_mode 和 payment_instrument_token_id，现有可空定义不授权猜测迁移旧数据。更换工具或明确失败后的新支付使用新 Submission/Token/Attempt。uk_payment_attempt_no、uk_pay_attempt_submission、uk_pay_attempt_token 分别保护顺序、原提交和工具唯一绑定；attempt_no 在 Intent 资源保护下由已有 Attempt 分配，不依赖历史表。

Routing 成功前 channel_id/channel_account_id/mid_id 全空，成功后与 Decision/Candidate 一致地成组冻结，后续操作及退款沿原 Attempt 使用，不能重新读取 MID 当前账户替换。三个普通索引支持按渠道、账户及 MID 定位尝试。法律接受归 Commerce；认证要求、评估 ID/结果归 RISK_CHECK Action；不复制失败阶段、原因码或限额占用组。

active_action_id 只能指向本 Attempt 的一个未终态 Action；CREATED/PROCESSING/UNKNOWN 均占位。结果事务只在指针仍等于原 Action 时释放或接续。等待手动 Capture 时可为空，但 Intent 继续占用本 Attempt；空执行位不代表允许再次扣款。

### t_payment_instrument_token

交易内的工具凭证，先于 Attempt 创建，绑定 client_id、payment_intent_id、source_submission_id 和精确 payment_method_code。字段：payment_instrument_token_id、payment_instrument_token、client_id、payment_intent_id、source_submission_id、payment_method_code、instrument_type、instrument_ref、expires_at、revoked_at、create_time、update_time。Token 值和三元来源 Scope 分别唯一；Token 通过 Attempt 的唯一引用最多用于一次尝试。instrument_type 本期仅 CARD，instrument_ref 指向受控 Card Owner 的稳定引用，不保存 PAN、CVV、卡描述、临时会话材料、JSON 或额外状态机。

期限取 Intent、原提交、显式准备窗口和 Owner 材料期限的最小值，重放不刷新。失效或撤销时，尚未提交首次资金且无认证未决结果的原 Attempt 可安全结束；已 SUBMITTED/UNKNOWN 的原动作继续查询，已授权/扣款后的 Capture、Void、Refund 与晚到事实不受 Token 到期阻断。创建和撤销在同一本地事务登记现有补偿机制的清理任务，Card 材料操作始终在事务外。完整 API、Hash 与恢复边界见[交易级 Token 修订](../../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)。

### t_payment_action

一次内部风控或渠道业务步骤。字段：payment_action_id、payment_attempt_id、channel_refund_id、duplicate_payment_case_id、action_no、action_type、action_status、target_channel_txn_ref_id、amount、currency、channel_idempotency_key、transaction_assessment_id、risk_result、authentication_requirement、interaction_type、interaction_expires_at、channel_response_code、channel_response_msg、channel_result_time、version、create_time、update_time。

父级 payment_attempt_id、channel_refund_id、duplicate_payment_case_id 严格 XOR。Attempt 支持 RISK_CHECK、AUTHENTICATE、AUTHORIZE、SALE、CAPTURE、VOID；Refund/重复支付处置支持 REFUND。action_no 在父级内递增；不存在 QUERY 或 REVERSAL 业务 Action。action_status 为 CREATED、PROCESSING、UNKNOWN、SUCCEEDED、FAILED；原操作超时由相同 Action 恢复。

RISK_CHECK 保存原 transaction_assessment_id、risk_result；可信 ALLOW 同时冻结 authentication_requirement，其他情况为空。AUTHENTICATE.SUCCEEDED 必须表示满足内部认证要求；表单创建和浏览器回跳不构成成功。认证证明、挑战事实与有效期由 Channel 管理并在原资金提交中核验，本表不复制这些字段或认证操作引用。原 Action 保留 interaction_type/interaction_expires_at，交互秘密仅瞬时返回。

渠道坐标由所属 Attempt 取得。Capture/Void 的 target_channel_txn_ref_id 指原 AUTHORIZE Ref，Refund 指原 SALE/CAPTURE Ref，再从该 Ref 的原资金 Action 导航到 Attempt。不得用当前配置或最新成功 Action 替代精确原交易。channel_idempotency_key 为全局唯一、ASCII 区分大小写的原渠道操作键；RISK_CHECK 为空，渠道动作必填，恢复不换键。

channel_response_code/channel_response_msg 只记录真实渠道响应的安全代码与清洗说明，本地错误不冒充渠道响应。最终状态取可信标准结果，不能解析消息文案推进。先在本地事务取得执行权，事务外调用 Risk/Channel，结果与指针及可靠任务另开短事务提交。

### 来源唯一关联

来源直接保存在 Intent 的 `source_type/source_id`，由原唯一索引保证同一来源一个 Intent。`t_payment_intent_binding` 与提前关闭创建的 EX-I03 已删除；Commerce 先确认创建结果，再终止已有 Intent，未决资金仍返回 PENDING。完整规则见[字段修订第 8 节](../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md#8-删除来源-binding)。

### t_channel_transaction_ref

所有 AUTHORIZE、CAPTURE、SALE、VOID、REFUND 的不可变外部交易号。字段：channel_transaction_ref_id、payment_action_id、channel_id、mid_id、reference_type、external_transaction_id、original_channel_txn_ref_id、channel_event_time、amount、currency、create_time。uk_channel_transaction_ref(channel_id, mid_id, reference_type, external_transaction_id) 唯一，且每条 Ref 必须关联产生它的 Action。reference_type 取 AUTHORIZE、CAPTURE、SALE、VOID、REFUND。原 SALE/CAPTURE Ref 通过 payment_action_id → 原资金 Action.payment_attempt_id 导航到 Attempt 冻结的 Channel、Channel Account 与 MID；REFUND Action 的 target_channel_txn_ref_id 必须指向该原 Ref，Adapter 使用原 Ref 的 external_transaction_id 发起退款；退款成功后新增独立 external_transaction_id、reference_type=REFUND 的 Ref，并以 original_channel_txn_ref_id 反向指向原 Ref。本表创建后只追加，不以当前 Channel 配置改写历史。

### t_channel_interaction

每次真实 Channel HTTP 交互的受控通信记录。字段：channel_interaction_id、payment_action_id、channel_dispute_action_id、interaction_no、interaction_status、started_at、finished_at、request_file_id、request_content_hash、response_file_id、response_content_hash、http_status、channel_result_code、latency_ms、create_time、update_time。两父级严格 XOR：恰有一个 payment_action_id、channel_dispute_action_id 非空；uk_channel_interaction_action(payment_action_id, interaction_no) 和 uk_channel_interact_dispute_act(channel_dispute_action_id, interaction_no) 分别保证父级内序号唯一。interaction_status 取 STARTED、RESPONSE_RECEIVED、NETWORK_ERROR、TIMED_OUT。发送前插入 STARTED；收到响应或网络终止时仅允许一次条件更新到终态，终态后不得改变。文件对可空但必须成对出现并通过安全扫描；本表不保存原始凭据/PAN。

### t_duplicate_payment_case

同一 Intent 出现第二个不同、已验证的资金成功事实时建立的合并 Case 和补偿处置。只有不同 Action/Transaction Ref 的 SALE 成功或完成资金的 CAPTURE 成功才触发；重复 Callback、同一 external_transaction_id 重放、原操作恢复确认原 Action，以及正常 AUTHORIZE→CAPTURE 链路都不是重复支付。字段：duplicate_payment_case_id、payment_intent_id、canonical_attempt_id、canonical_action_id、canonical_transaction_ref_id、duplicate_attempt_id、duplicate_action_id、duplicate_transaction_ref_id、duplicate_amount、currency、case_status、compensation_action_id、version、create_time、update_time。uk_dup_case_duplicate_action(duplicate_action_id) 唯一。case_status 取 OPEN、COMPENSATING、COMPENSATION_UNKNOWN、COMPENSATED、MANUAL_REVIEW。第一笔成功保持 canonical，第二笔迟到成功不能覆盖 Intent successful_attempt_id；它必须令后到 Attempt 进入 SUCCEEDED 并由 Case 标识重复成功、创建本 Case，并对 duplicate_transaction_ref_id 发起补偿退款。未 Capture 的重复 Authorization 只是多余授权冻结，应对精确 Authorization Ref 执行 VOID，不进入重复支付退款。version 防止 Callback、原操作恢复和补偿动作结果竞争。


## 6. Channel Refund（2）

### t_payment_refund_position

Payment 侧按成功支付可退款金额的并发控制 Root。首笔权威 SALE 或完成资金的 CAPTURE 成功并确定 canonical Channel Transaction Ref 时创建。字段：payment_refund_position_id、payment_intent_id、successful_attempt_id、original_channel_txn_ref_id、original_amount、reserved_amount、refunded_amount、currency、version、create_time、update_time。uk_payment_refund_position(payment_intent_id, original_channel_txn_ref_id) 唯一。约束为 reserved_amount + refunded_amount <= original_amount，金额均非负。original_channel_txn_ref_id 精确冻结原成功 Ref；从该 Ref 的 payment_action_id → 原资金 Action.payment_attempt_id 可恢复 Attempt 冻结的 Channel、Channel Account 和 MID。version 的真实竞争者是并发的 Channel Refund 创建、FAILED/CANCELLED 释放、SUCCEEDED 消费以及 UNKNOWN 查询恢复。

### t_channel_refund

一个 Commerce Refund Request 的唯一渠道退款 Root。字段：channel_refund_id、commerce_refund_request_id、payment_refund_position_id、refund_amount、currency、refund_status、version、create_time、update_time。`uk_channel_refund_commerce_req(commerce_refund_request_id)` 保证一个 Commerce Refund Request 只有一个渠道退款。Commerce 本地预占后直接调用 PAY-I04；Payment 锁定 Refund Position，以 Version/CAS 原子增加 `reserved_amount` 并创建唯一 Channel Refund，不调用 Finance、不保存 Finance Funding ID 或绑定版本。Intent 与成功 Attempt 已由 Payment Refund Position 唯一冻结，Channel Refund 不重复保存。refund_status 取 CREATED、PROCESSING、UNKNOWN、SUCCEEDED、FAILED、CANCELLED。合法路径 CREATED→PROCESSING/CANCELLED；PROCESSING→UNKNOWN/SUCCEEDED/FAILED/CANCELLED；UNKNOWN→PROCESSING/SUCCEEDED/FAILED/CANCELLED；终态不可复活。UNKNOWN 只保留在 Payment 本地并继续占用 Position，不通知 Finance；SUCCEEDED 把 reserved 转为 refunded并同时通知 Commerce 与 Finance；FAILED/CANCELLED 释放 reserved 且只通知 Commerce。精确退款导航为 Channel Refund → Payment Refund Position → original_channel_txn_ref_id → 原 Ref → 原 payment_action_id → 原 payment_attempt_id → Attempt 的 Channel/Channel Account/MID；新 REFUND Action 沿用此冻结三元组并以 target_channel_txn_ref_id 指向原 Ref，成功 REF 再以 original_channel_txn_ref_id 指向原 Ref。version 保护 Channel Callback、QUERY、主动取消和 Commerce/Finance 成功通知的并发收敛。

## 7. Channel Callback（3）

### t_channel_callback

验签、去重和批量处理的 Callback 信封。字段：channel_callback_id、channel_id、channel_account_id、external_event_id、payload_fingerprint、signature_status、callback_status、received_at、file_id、content_hash、version、create_time、update_time。uk_channel_callback_dedup(channel_id, channel_account_id, external_event_id)；没有 external_event_id 时 uk_channel_callback_fingerprint(channel_id, channel_account_id, payload_fingerprint) 去重。事件号和指纹只要求在同一 Channel Account 内唯一，避免同一 Channel 的不同账户互相误判重复。channel_account_id 在尚未映射到 MID/Action 时提供验签和归一化账户上下文，Adapter 使用其当前和轮换宽限期内的验签密钥集合。signature_status 取 VERIFIED、REJECTED、UNKNOWN；callback_status 取 RECEIVED、PROCESSING、PROCESSED、REJECTED、EXCEPTION。version 保护同一批 Callback worker 重放/认领和最终归档状态；敏感 Header/原文不存本行。

### t_channel_callback_item

批量 Callback 的逐项归一化及目标映射。字段：channel_callback_item_id、channel_callback_id、item_no、event_type、external_transaction_id、target_type、target_id、normalized_status、channel_event_time、mapping_status、create_time。uk_channel_callback_item(channel_callback_id, item_no) 唯一。target_type 取 PAYMENT_ACTION、CHANNEL_REFUND、CHANNEL_DISPUTE_NOTICE、CHANNEL_DISPUTE_ACTION、UNKNOWN；mapping_status 取 MAPPED、UNMATCHED、AMBIGUOUS、IGNORED。该项为不可变输入/映射证据；后续异常另开 Case。

### t_channel_event_exception

签名、匹配、乱序或非资金状态冲突的独立处置 Case。字段：channel_event_exception_id、channel_callback_id、channel_callback_item_id、exception_type、exception_status、reason_code、resolved_by、resolved_at、version、create_time、update_time。exception_type 取 SIGNATURE、MATCHING、OUT_OF_ORDER、STATE_CONFLICT、PAYLOAD_SECURITY；exception_status 取 OPEN、INVESTIGATING、RESOLVED、IGNORED。version 保护 Operations 处置与自动重放竞争；资金第二笔成功必须使用 t_duplicate_payment_case，不使用本表。

## 8. Channel Dispute（3）

### t_channel_dispute_notice

Channel 争议身份、冻结金额事实和当前渠道状态 Root。字段：channel_dispute_notice_id、channel_id、mid_id、original_channel_txn_ref_id、external_dispute_id、dispute_amount、currency、received_at、channel_deadline_at、current_channel_status、latest_event_id、latest_event_no、version、create_time、update_time。uk_channel_dispute_external(channel_id, external_dispute_id) 唯一；uk_dispute_notice_latest(latest_event_id) 保证同一争议事件至多被一个 Notice 指为最新事件。current_channel_status 取 RECEIVED、EVIDENCE_REQUIRED、SUBMITTED、UNDER_REVIEW、WON、LOST、CLOSED。身份/金额/原交易 Ref 创建后不可改；version 真实保护 Callback、Risk 指令和 Channel 结果并发更新当前状态/最新事件指针。

### t_channel_dispute_event

Channel 争议的不可变事件流。字段：channel_dispute_event_id、channel_dispute_notice_id、event_no、event_type、from_status、to_status、channel_event_time、reason_code、create_time。uk_channel_dispute_event(channel_dispute_notice_id, event_no) 唯一。event_type 取 NOTICE_RECEIVED、STATUS_CHANGED、OUTCOME_RECEIVED、DEADLINE_UPDATED。只追加；notice Root 的 version 和 latest_event_no 负责与 Callback/操作竞争。

### t_channel_dispute_action

按 Risk 指令真实提交举证或查询的渠道动作。字段：channel_dispute_action_id、channel_dispute_notice_id、dispute_case_id、dispute_submission_request_id、dispute_submission_request_version、dispute_evidence_package_id、dispute_evidence_package_version、action_type、action_status、channel_id、channel_account_id、mid_id、channel_idempotency_key、channel_result_time、version、create_time、update_time。uk_dispute_action_idempotency(channel_id, channel_idempotency_key) 唯一。action_type 取 SUBMIT_EVIDENCE、QUERY；action_status 取 CREATED、SUBMITTED、UNKNOWN、SUCCEEDED、FAILED。合法路径 CREATED→SUBMITTED/FAILED，SUBMITTED→UNKNOWN/SUCCEEDED/FAILED，UNKNOWN→SUBMITTED/SUCCEEDED/FAILED。version 保护回调、查询和网络重试；Action 冻结 Channel Account 和 Risk Request/Package 精确版本，不自行宣布业务 WON/LOST。

## 9. 可靠执行（2）

### t_command_idempotency

Payment 本库命令的通用 Claim、Lease 和 CAS 持久化由 Foundation `paytech-command-idempotency` 负责。字段：command_idempotency_id、caller_scope、operation_type、idempotency_key、request_hash、request_hash_version、request_snapshot_schema_version、request_snapshot_json、operation_context_time、recovery_stage、process_status、resource_type、resource_id、resource_no、response_code、response_http_status、response_snapshot_schema_version、response_location、response_body_json、lock_owner、lock_until、version、create_time、update_time。精确类型、注释和可空性以正式 PDManer 为准；caller_scope、idempotency_key、resource_no、response_location 使用 ascii_bin，key 长度统一为 255。

uk_command_idempotency(caller_scope, operation_type, idempotency_key) 唯一。process_status 取 PROCESSING、SUCCEEDED、FAILED；version 是公共组件的技术 CAS 版本，独立于业务 Root 版本。Payment 继续拥有 Hash、作用域、业务资源与重放语义。pay 在同一本地事务完成 Claim、Attempt 写入和 Complete，只使用 CLAIMED→COMPLETED，命令成功表示受理完成，响应正文为空，重放投影当前 Attempt/Action。详细约定与空表迁移边界见[公共包接入说明](../../payment-orchestration-service/docs/development/command-idempotency-differences.md)；不新增恢复扫描索引或支付流程阶段。

### t_compensation_event

业务变化同事务登记、提交后执行的事实发布或本地恢复任务。Payment 目标字段采用 Foundation 的最小合同：id、event_key、event_type、sub_event_type、可空 payload_json、execute_status、execute_count、next_execute_at、lock_owner、lock_until、last_error_code、last_error_summary、lock_version、create_time、update_time。唯一身份为 `(event_key,event_type,sub_event_type)`；精确类型、索引、默认值与注释见[Execution 数据方案](../../requirements/20260913/payment-Execution设计/data-design.md)和正式模型。状态仅 INIT、PROCESSING、ERROR、FAILED；确定成功删除，FAILED 不释放业务 UNKNOWN 资金占用。旧 Source/Version/Schema/Hash 字段不再进入目标模型，数据库是否迁移仅由当前台账记录。

## 10. 核心状态、并发和不变量

- 同一 Intent 最多一个活动 Attempt 与一个首笔成功 Attempt；同一 Attempt 最多一个未终态业务 Action。状态、金额、指针和可靠任务在同一事务提交，所有外部调用在事务外。
- Commerce 负责法律接受校验并传入最小支付事实；Payment 不调用 Commerce。内部风险评估是 RISK_CHECK，ALLOW 后才路由，认证要求跟随该风险结果；Risk 自己保存占额事实。
- 同一 Attempt 冻结精确支付方式、Token、capture mode 和选定渠道三元组。Channel 处理原渠道操作的查询与安全重发；Execution 不创建 QUERY Action。
- 资金首次提交前的权威检查保留既有 Fail-Closed 语义，不能把未知请求判为从未发送。UNKNOWN 继续占位，不得借此换卡、重路由或释放退款预占。
- V1 为 SALE 或 AUTHORIZE 后全额 CAPTURE；手动授权等待仍为 PROCESSING。成功 Void 安全取消，普通退款不回退已完成收款事实。
- 可信额外资金成功必须锁 Intent，保留 canonical 指针并创建 Duplicate Case。后到 Attempt 为 SUCCEEDED，以 Case 区分重复；不再次发布首笔成功事件。
- Decision、Candidate、Transaction Ref、Callback Item、Dispute Event 只追加；Channel Interaction 为 STARTED 到一次终态更新。删除通用状态流转表，不删除这些业务证据。
- 通知不再依赖 transition ID；稳定事件身份与发生时刻的后继合同须在代码切换前完成，不能从最新状态重建旧事件。本地补偿表成功后删除，不能作为永久历史。

## 11. 已删除/不采用的旧概念

本轮删除 t_payment_pricing_fact、t_payment_state_transition，不新增同义替代表。定价下游对 payment_fact_id/标准化支付属性的消费需独立迁移；不能以删表为由默认零费率或覆盖既有财务事实。

旧 58 表模型中的 routing_policy_binding、routing_rule、routing_rule_condition 由 Scope 下沉到 t_routing_policy 和版本候选集替代；不采用 active_slot、Scope/Content/Projection Hash、专用 History 或 Current Projection。payment_recovery_task 由 t_compensation_event 的单职责 Handler 和原业务 Root 的 UNKNOWN 状态替代。channel_refund_attempt 已并入 t_channel_refund；refund_transaction_ref 已并入 t_channel_transaction_ref(reference_type=REFUND)。t_channel_connection、t_channel_connection_version、t_mid_limit、t_mid_credential_ref 与原 t_mid_health 已分别由 Channel Account、有类型 Capability 和动态 Health State 精简设计替代。t_payment_execution_gate 与 t_payment_execution_gate_result 已删除：首次资金 Action 提交前仍执行实时 Fail-Closed 检查，但不把技术前置校验复制成 Payment 业务聚合。t_three_ds_context 不采用，旧 t_payment_customer_action 已由原 Action 的最小交互事实替代。旧 t_payment_instrument 由交易级 t_payment_instrument_token 替代，不采用 JSON、EAV/Key-Value、工具会话或方式子表。通用工作流 Binding、Recovery、Conflict 双 Root、Dispute Projection、Restriction Projection、通用 Inbox/Outbox 均不进入当前模型。

## 12. 物理模型状态与后续边界

正式 PDManer、完整 Schema、生成统计及实际验证状态由[当前台账](../CURRENT_IMPLEMENTATION.md#payment-execution-implementation)统一记录。字段/类型/索引以当前模型和同步 Schema 为准，旧方案仅保留其未被取代的业务边界。

本轮维护设计工件，不执行数据库 DDL 或 Java 改造。切换前必须同步映射、流程、Channel 认证、事件发布与定价消费者，再人工应用经评审 SQL；应用启动不创建或迁移业务表。

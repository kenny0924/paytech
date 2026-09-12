# Payment Orchestration 数据库设计规范

> **2026-09-11 模型更新：** 本页的数字 OE、Assignment、Client 多 OE 合同及旧 Scope 冻结描述，仅适用于迁移前记录与接口；现行目标由 [四系统模型决定](../decisions/2026-09-11-cct-commerce-oe-pricing-model.md) 精确取代。Risk 本期不改，Java 与数据库尚未据此迁移，历史订单与 Hash 不重写。

最后更新：2026-08-25

状态：本期模型采用 2026-09-11 四系统调整；生成统计、验证、Java 与数据库迁移状态统一见 [当前实现台账](../CURRENT_IMPLEMENTATION.md)。

> **2026-09-05 跨主题对齐：** 本文拥有 Payment 业务事实，不单独拥有 Vault、`compensation_event`、物理 CHECK/FK 或测试基础设施规范。当前生成模型中的旧横切字段是 [当前实现台账](../CURRENT_IMPLEMENTATION.md) 记录的迁移债务；新增实现必须遵循 [文档治理路由](../DOCUMENT_GOVERNANCE.md)，不得复制旧契约。

## 1. 目的、边界与统一约定

本文是 Payment Orchestration 的唯一逻辑数据库基线，也是 PDManer 生成器、模型测试、现实场景和后续跨系统契约的共同输入。表名已经使用将来物理模型的 t_ 前缀；所有跨系统 ID 均为业务引用，不建立跨库外键。

Payment 拥有 Provider/MID 配置、路由选择、支付 Intent/Attempt/渠道动作、Channel Refund、Provider 回调、渠道争议和本地可靠执行事实。它不拥有 Order、Checkout Session、消费者合同或退款业务资格；不拥有 Client、Operating Entity、Risk 规则/争议业务结论、Finance 会计账本/结算、文件二进制、IAM 或 Provider Secret。

Payment Intent 用 source_type + source_id 关联来源；MVP 的 source_type 为 COMMERCE_ORDER，但模型不得把来源语义硬编码为 Checkout。Commerce 已冻结 Client、Operating Entity、Assignment、industry_code、mcc_code、金额、币种和 Order Context；Payment 只按这些时点事实路由，Client 不能选择 Operating Entity、Provider 或 MID。

- 主键是服务生成的 Snowflake BIGINT，时间为 UTC DATETIME(6)，金额为 DECIMAL(24,8)，权重/比例为 DECIMAL(18,10)，币种为 ISO 4217 CHAR(3)，country_code 为 ISO 3166-1 alpha-2。配置 Scope 的 country_code 可以使用获批 *；Intent、Attempt、Action、Ref、Callback 等交易事实不允许 *。
- 可变 Root 的 version 使用 expected-version CAS；不可变证据、版本内明细和统一状态流转不机械保存 version。
- Provider/API/Webhook Secret、私钥、证书、Endpoint、超时及其轮换版本不进入 Payment 数据库；Adapter 仅以 Provider Account 的非敏感 integration_config_key 定位运行配置。PAN、CVV/CVC、完整磁道、PIN/PIN Block、卡有效期、Provider Secret、临时 Redirect URL、Client Secret、SDK Token、QR Payload、未审查原文和 Consumer Email/Phone Token/Network Evidence 原值不得进入表、日志、MQ 或补偿 Payload。
- Provider 原始信封默认不留档；仅在 Schema、卡数据与 Secret 扫描通过后，可保存受控加密文件的 file_id + content_hash 引用。
- CANCEL 不是 Payment Action；VOID 是未捕获成功 AUTHORIZE 的全额释放；Reversal 是 Commerce 命令，Payment 原子选择 VOID 或标准全额 REFUND。SALE 成功或累计 CAPTURE 等于 Intent 金额才是 PaymentSucceeded。
- 开放代码必须有权威目录：adapter_code 使用 Adapter Catalog，payment_method_code 使用 Payment Method Catalog，scheme_code 使用 Scheme Catalog，capability_code 使用 MID Capability Catalog，industry_code/mcc_code 使用 CCT/Commerce 冻结分类，reason_code 使用 Reason Catalog，trigger_type 使用 Trigger enum，Provider 返回码使用对应 Provider Adapter Catalog。未列为开放代码的状态/类型字段均为本文逐项列全的闭合枚举。

## 2. 权威 29 表目录

| 分组 | 数量 | 表 |
|---|---:|---|
| Provider 与 MID | 5 | t_payment_provider、t_provider_account、t_provider_mid、t_mid_capability、t_mid_health_state |
| Payment Routing | 5 | t_routing_policy、t_routing_policy_version、t_routing_policy_mid、t_routing_decision、t_route_candidate_eval |
| 支付执行与异常 | 9 | t_payment_intent、t_payment_attempt、t_payment_instrument、t_payment_action、t_payment_customer_action、t_provider_transaction_ref、t_provider_interaction、t_duplicate_payment_case、t_payment_state_transition |
| Channel Refund | 2 | t_payment_refund_position、t_channel_refund |
| Provider Callback | 3 | t_provider_callback、t_provider_callback_item、t_provider_event_exception |
| Provider Dispute | 3 | t_provider_dispute_notice、t_provider_dispute_event、t_provider_dispute_action |
| 可靠执行 | 2 | t_command_idempotency、t_compensation_event |

## 3. Provider 与 MID（5）

### t_payment_provider

Provider 身份、Adapter 和运行状态。字段：provider_id、provider_code、provider_name、adapter_code、provider_status、create_time、update_time。uk_payment_provider_code(provider_code) 保证外部稳定代码唯一。provider_status 取值 DRAFT、ACTIVE、SUSPENDED、CLOSED；provider_code 创建后不可改，CLOSED 不再恢复。状态命令携带 expected current status，防止并发状态切换；不保存机械 version、可执行 Adapter 或脚本。

### t_provider_account

Provider Account 是 Payment Adapter 调用渠道 API 的技术认证账户或租户，不是 MID，也不是 Finance 的清算账户。字段：provider_account_id、provider_id、account_code、external_account_id、integration_config_key、account_status、create_time、update_time。uk_provider_account_code(provider_id, account_code)、uk_provider_account_external(provider_id, external_account_id)、uk_provider_account_config(provider_id, integration_config_key) 与 uk_provider_account_owner(provider_account_id, provider_id) 保证 Provider 内配置与归属唯一；idx_provider_account_status(provider_id, account_status) 支持路由过滤。account_status 取值 DRAFT、ACTIVE、SUSPENDED、CLOSED；account_code 创建后不可改，停用使用 Expected Current Status 条件更新，不保存机械 version。integration_config_key 是非敏感运行配置定位键，不保存 Endpoint、Secret、证书、Webhook 密钥或其版本；Provider 未提供 external_account_id 时允许为空。

### t_provider_mid

Provider MID 与其唯一 Operating Entity 归属及当前执行账户绑定。字段：mid_id、provider_id、provider_account_id、external_mid_code、operating_entity_id、mid_status、activated_at、closed_at、create_time、update_time。uk_provider_mid(provider_id, external_mid_code) 唯一；idx_provider_mid_account(provider_account_id, mid_status) 与 idx_provider_mid_entity(operating_entity_id, mid_status) 支持路由。一个 Provider 有多个 Account 和 MID，一个 Account 可服务多个 MID；MID 同时保存 provider_id 用于直接表达渠道归属、Provider 内唯一 MID 号和停用筛选。创建或调整 MID 必须验证 provider_mid.provider_id = provider_account.provider_id；provider_account_id 仅能在 MID 非活动时由受控配置命令调整，历史 Action 已冻结账户不漂移。mid_status 取值 DRAFT、ACTIVE、SUSPENDED、CLOSED；operating_entity_id 创建后不可变，归属错误须关闭旧 MID 并新建。

### t_mid_capability

MID 的固定路由 Scope 加有类型 Key-Value 静态能力。字段：mid_capability_id、mid_id、payment_method_code、scheme_code、country_code、currency、mcc_code、capability_code、value_type、boolean_value、decimal_value、integer_value、code_value、capability_status、create_time、update_time。Scope 五列均非空：payment_method_code 必须使用精确 Payment Method Catalog 值；scheme_code、country_code、currency、mcc_code 可以使用精确目录值或控制通配符 *。capability_code 来自 MID Capability Catalog。value_type 取 BOOLEAN、DECIMAL、INTEGER、CODE，每行必须且只能填写对应的一个值列，boolean_value 只允许 0/1；capability_status 取 ENABLED、DISABLED。uk_mid_capability_scope(mid_id, payment_method_code, scheme_code, country_code, currency, mcc_code, capability_code) 唯一，不保存 Hash、Version、有效期或 JSON。ACTION.SALE、ACTION.AUTHORIZE、ACTION.CAPTURE、ACTION.VOID、ACTION.REFUND、SECURITY.3DS_MODE、AMOUNT.MIN、AMOUNT.MAX 等能力统一在本表表达；同一生效 Scope 的最小金额不得大于最大金额。路由对每个必需 capability_code 选择最高 Scope 精确度的 ENABLED 行，缺失或同精确度无法唯一决胜均配置冲突并失败关闭。

### t_mid_health_state

路由需要的当前短窗口动态健康聚合，而非原始 Metrics、Trace 或时序历史。字段：mid_health_state_id、mid_id、payment_method_code、health_status、circuit_status、health_score、sample_count、technical_success_count、provider_error_count、timeout_count、technical_success_rate、provider_error_rate、timeout_rate、p95_latency_ms、consecutive_failure_count、window_started_at、window_ended_at、observed_at、valid_until、create_time、update_time。uk_mid_health_state(mid_id, payment_method_code) 唯一。health_status 取 HEALTHY、DEGRADED、UNHEALTHY、UNKNOWN；circuit_status 取 CLOSED、OPEN、HALF_OPEN；health_score 为 0..100，三个 rate 为 0..1，计数和延迟非负，sample_count = technical_success_count + provider_error_count + timeout_count，且 window_started_at < window_ended_at <= observed_at < valid_until。仅新 observed_at 更晚才覆盖当前行。technical_success_count 是协议层取得响应次数，包含正常业务拒绝，不将 Issuer/Consumer 正常拒绝算作渠道故障；阈值和原始监控数据由运行配置与监控平台管理。

## 4. Payment Routing（5）

### t_routing_policy

一个确定交易 Scope 的稳定策略 Root。字段：routing_policy_id、scope_type、scope_id、country_code、currency、payment_method_code、industry_code、mcc_code、match_priority、policy_status、current_version_id、version、create_time、update_time。uk_routing_policy_scope(scope_type, scope_id, country_code, currency, payment_method_code, industry_code, mcc_code, match_priority) 唯一；uk_route_policy_cur_ver(current_version_id) 保证一个非空策略版本至多被一个策略 Root 指为当前版本。为解除 Policy 与首个 Version 的循环外键，current_version_id 在首次发布前可空；ACTIVE 时必须非空且指向属于本 Policy 的 ACTIVE Version。scope_type 取值 CLIENT、OPERATING_ENTITY、GLOBAL；scope_id 分别是 Client ID、Operating Entity ID、0，所有匹配维度非空并可使用获批 *。policy_status 取值 ACTIVE、SUSPENDED、CLOSED。解析顺序固定为 CLIENT > OPERATING_ENTITY > GLOBAL；同层先按精确维度数量、再按 match_priority，仍并列即配置冲突。version 保护发布版本指针与策略状态切换。

### t_routing_policy_version

不可变候选 MID 集合的发布版本。字段：routing_policy_version_id、routing_policy_id、version_no、version_status、create_time。uk_routing_policy_version(routing_policy_id, version_no) 唯一。version_status 取值 DRAFT、ACTIVE、RETIRED。DRAFT 可编辑候选，发布后候选集不可修改；启用时由 policy Root 的 CAS 原子切换 current_version_id，而不是扫描任意 ACTIVE 子版本。本行不用 version，避免与 Root CAS 重复。

### t_routing_policy_mid

一个策略版本的候选 MID、优先级和权重。字段：routing_policy_mid_id、routing_policy_version_id、mid_id、candidate_priority、weight_value、create_time。uk_routing_policy_mid(routing_policy_version_id, mid_id) 唯一。已发布 Policy Version 自身控制完整候选集；版本发布后只读，候选按最小 candidate_priority，再按 weight_value 选择，权重必须为正。

### t_routing_decision

每个 Attempt 的不可变最终路由结论。字段：routing_decision_id、payment_attempt_id、routing_policy_id、routing_policy_version_id、selected_provider_id、selected_provider_account_id、selected_mid_id、decision_status、decision_reason_code、create_time。uk_routing_decision_attempt(payment_attempt_id) 保证一 Attempt 一结论。decision_status 取值 SELECTED、REJECTED、CONFIGURATION_CONFLICT。Policy ID 与 Version ID 必须成对：SELECTED 全部 Policy/Provider/Provider Account/MID 字段非空；CONFIGURATION_CONFLICT 因不存在唯一获胜策略，Policy 对与选中四字段全部为空；REJECTED 的选中四字段为空，无匹配 Policy 时 Policy 对为空，有匹配 Policy 但无合格 MID 时 Policy 对非空。selected_provider_account_id 只在 SELECTED 时必需冻结；选中 Candidate、Decision 与首个 AUTHORIZE/SALE Action 的 Provider、Provider Account、MID 必须逐值一致。重试新 Attempt 才可重选同一 Operating Entity 下的 MID。本表不可变、只追加，不保存 version。

### t_route_candidate_eval

每个决策中全部候选 MID、其执行账户、主要淘汰原因及实际使用的动态健康快照。字段：route_candidate_eval_id、routing_decision_id、provider_id、provider_account_id、mid_id、eligible_flag、candidate_priority、weight_value、exclusion_reason_code、health_status、circuit_status、health_score、technical_success_rate、timeout_rate、p95_latency_ms、sample_count、health_observed_at、create_time。uk_route_candidate_eval(routing_decision_id, mid_id) 唯一。eligible_flag 为 0/1；值为 1 时 provider_account_id 非空且 exclusion_reason_code 为空，值为 0 时 exclusion_reason_code 非空。候选在 Provider/MID/Capability/Account 静态阶段已淘汰时 provider_account_id 与动态健康字段可空；进入动态健康阶段后必须冻结完整健康快照。被选 MID 必须有 eligible_flag=1 行。该表是不可变评估证据，创建后只读，历史 Decision 不反查当前 Health State 解释当时路由。

## 5. 支付执行与异常（9）

### t_payment_intent

一个 Commerce Order 的唯一支付目标。字段：payment_intent_id、source_type、source_id、source_order_context_id、client_id、operating_entity_id、assignment_id、industry_code、mcc_code、amount、currency、intent_status、active_attempt_id、successful_attempt_id、expires_at、version、create_time、update_time。uk_payment_intent_source(source_type, source_id) 唯一；uk_payment_intent_active(active_attempt_id) 与 uk_payment_intent_success(successful_attempt_id) 分别保证同一 Attempt 至多被一个 Intent 指为活动或成功尝试。intent_status 取值 REQUIRES_PAYMENT、PROCESSING、REQUIRES_ACTION、SUCCEEDED、FAILED、EXPIRED、CANCELLED。合法路径为 REQUIRES_PAYMENT→PROCESSING→REQUIRES_ACTION/REQUIRES_PAYMENT/SUCCEEDED/FAILED/EXPIRED/CANCELLED；REQUIRES_ACTION 可回 PROCESSING，终态不可复活。只有 SALE 成功或累计 CAPTURE=amount 可 SUCCEEDED；Void 成功使 Intent CANCELLED。version 的真实竞争者是 Attempt 创建/终止/成功、回调、主动查询、Cancel、Operating Entity 关闭和超时恢复；active_attempt_id/successful_attempt_id 代替任何 Attempt slot。

### t_payment_attempt

同一 Intent 内顺序发生的一次支付尝试。字段：payment_attempt_id、payment_intent_id、attempt_no、payment_method_code、attempt_status、failure_stage、status_reason_code、consumer_contract_id、consumer_terms_acceptance_id、refund_policy_acceptance_id、privacy_notice_acceptance_id、acceptance_completed_at、order_version、transaction_assessment_id、risk_result、limit_reservation_group_id、authorized_amount、captured_amount、version、create_time、update_time。payment_method_code 非空并冻结本次 Attempt 从 Payment Method Catalog 选择的精确支付方式，即使一次性 APM 没有可保存的 Instrument 也必须存在。uk_payment_attempt_no(payment_intent_id, attempt_no) 唯一；t_routing_decision.payment_attempt_id 的唯一关系是唯一路由真相。attempt_status 仅取 CREATED、PROCESSING、REQUIRES_ACTION、AUTHORIZED、UNKNOWN、SUCCEEDED、FAILED、CANCELLED、VOIDED、CONFLICTED。合法路径：CREATED→PROCESSING/FAILED/CANCELLED；PROCESSING→REQUIRES_ACTION/AUTHORIZED/UNKNOWN/SUCCEEDED/FAILED/CANCELLED；REQUIRES_ACTION→PROCESSING/UNKNOWN/FAILED/CANCELLED；AUTHORIZED→PROCESSING/UNKNOWN/VOIDED/FAILED；UNKNOWN→PROCESSING/SUCCEEDED/FAILED/VOIDED/CONFLICTED；SUCCEEDED、FAILED、CANCELLED、VOIDED、CONFLICTED 均正常终态。仅已验证且矛盾的 Provider 资金事实可作狭窄权威纠正：FAILED→CONFLICTED；对应 Action 可 FAILED→SUCCEEDED。该事务必须创建 t_duplicate_payment_case、保留两条状态流转、不替换 Intent successful_attempt_id、不发送第二次 PaymentSucceeded，且不得从终态行提交新的 Provider 请求。被 Readiness/Risk/Limit 或首次 Provider 提交前实时检查拦截时形成 FAILED Attempt，并通过 failure_stage/status_reason_code 说明，不建立独立 Gate 记录。version 解决 Callback、QUERY、超时/恢复和操作命令的 CAS 竞争。

### t_payment_instrument

Attempt 0..1 的可选 Provider 工具引用和最小卡片描述，独立受访问授权。V1 采用一个宽单表，不建立 Instrument 子类型表、JSON、EAV/Key-Value，也不预留 Wallet、Bank 或 BNPL 专属字段。字段：payment_instrument_id、payment_attempt_id、provider_instrument_ref、scheme_code、bin_prefix、last4、issuer_country_code、provider_fingerprint、create_time。uk_payment_instrument_attempt(payment_attempt_id) 保证一 Attempt 最多一个。provider_instrument_ref 是 Provider 返回的稳定工具引用，不能反向还原 PAN，也不能携带可直接继续会话的秘密值；bin_prefix 最多八位，last4 只能四位。CARD 或 Apple Pay 等卡轨支付可以保存获批卡片描述；PayPal 等 APM 只有稳定 Provider 工具引用时才创建本行，一次性 Bank Redirect 等没有稳定工具引用时不创建 t_payment_instrument。没有 PAN、CVV 或卡有效期，且创建后不修改。

### t_payment_action

真实 Provider 渠道动作。字段：payment_action_id、payment_attempt_id、channel_refund_id、duplicate_payment_case_id、action_no、action_type、action_status、provider_id、provider_account_id、mid_id、target_provider_transaction_ref_id、amount、currency、provider_idempotency_key、provider_result_time、version、create_time、update_time。三父级严格 XOR：恰有一个 payment_attempt_id、channel_refund_id、duplicate_payment_case_id 非空。uk_payment_action_attempt_no(payment_attempt_id, action_no)、uk_payment_action_refund_no(channel_refund_id, action_no)、uk_pay_action_dup_case_no(duplicate_payment_case_id, action_no) 分别保证父级内序号唯一；uk_payment_action_idempotency(provider_id, provider_idempotency_key) 防止重复网络提交。

| 唯一父级 | 允许 action_type | action_no 规则 |
|---|---|---|
| Payment Attempt | AUTHORIZE、CAPTURE、SALE、VOID、QUERY | 父级内递增 |
| Channel Refund | REFUND、QUERY | REFUND 固定为 1；后续 QUERY 必须 >=2 |
| Duplicate Payment Case | REFUND、QUERY | REFUND 固定为 1；后续 QUERY 必须 >=2 |

Provider Dispute 使用自己的 Action 表。action_status 仅取 CREATED、SUBMITTED、REQUIRES_ACTION、UNKNOWN、SUCCEEDED、FAILED。合法路径 CREATED→SUBMITTED/FAILED；SUBMITTED→REQUIRES_ACTION/UNKNOWN/SUCCEEDED/FAILED；REQUIRES_ACTION→SUBMITTED/UNKNOWN/SUCCEEDED/FAILED；UNKNOWN→SUBMITTED/SUCCEEDED/FAILED；终态不可覆盖，唯一例外是已验证矛盾 Provider 资金事实的 FAILED→SUCCEEDED 权威纠正；该纠正与 Attempt FAILED→CONFLICTED、Duplicate Case 和两条状态流转在同一事务，不能替换 Intent 成功指针、发送第二次 PaymentSucceeded 或从终态行提交新的 Provider 请求。AUTHORIZE、SALE、CAPTURE 从 CREATED 首次转为 SUBMITTED 前必须紧邻执行 Client/Operating Entity 当前状态与 Compliance 限制的同步实时检查；任何明确阻断、依赖失败或结果缺失都 Fail-Closed，Action 与 Attempt 记录 FAILED 及稳定原因且不得创建 Provider Interaction。检查通过结果不持久化为独立 Gate。Action 已进入 SUBMITTED、REQUIRES_ACTION 或 UNKNOWN 后，网络重发必须复用该行、Provider Idempotency Key、Provider Account 与 MID，不重新把可能已发生的渠道请求判定为从未提交；version 解决回调、查询和提交重试竞争。Adapter 从 Action 冻结的 provider_account_id 解析当前运行配置；Provider Account 配置不保存 MID 专属凭据或任何 Secret 内容。

### t_payment_customer_action

一个 Payment Action 要求消费者继续完成的当前交互状态，用于统一表达 3DS、Redirect、SDK、App Switch 和二维码支付，不把某一种渠道协议硬编码为支付主模型。字段：payment_customer_action_id、payment_action_id、customer_action_type、customer_action_status、provider_reference、expires_at、completed_at、version、create_time、update_time。uk_payment_customer_action(payment_action_id) 保证一个 Action 最多一个当前消费者动作；idx_customer_action_status(customer_action_status, expires_at) 支持待完成与过期扫描。customer_action_type 仅取 REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE；customer_action_status 仅取 PENDING、COMPLETED、FAILED、EXPIRED。COMPLETED 必须有 completed_at，其余状态必须为空；expires_at 可空。provider_reference 只能保存不具备会话继续能力的非敏感稳定引用；临时 Redirect URL、Client Secret、SDK Token、QR Payload 或 bearer-like 参数只能在受控同步响应中短暂传递，不能持久化。version 保护浏览器回跳、Provider Callback 与超时处理的 CAS 竞争。本表不重复 payment_attempt_id，统一从 payment_action_id 导航到 Attempt。

### t_provider_transaction_ref

所有 AUTHORIZE、CAPTURE、SALE、VOID、REFUND 的不可变外部交易号。字段：provider_transaction_ref_id、payment_action_id、provider_id、mid_id、reference_type、external_transaction_id、original_provider_transaction_ref_id、provider_event_time、amount、currency、create_time。uk_provider_transaction_ref(provider_id, mid_id, reference_type, external_transaction_id) 唯一，且每条 Ref 必须关联产生它的 Action。reference_type 取 AUTHORIZE、CAPTURE、SALE、VOID、REFUND。原 SALE/CAPTURE Ref 通过 payment_action_id 导航到已冻结的 Provider、Provider Account 与 MID；REFUND Action 的 target_provider_transaction_ref_id 必须指向该原 Ref，Adapter 使用原 Ref 的 external_transaction_id 发起退款；退款成功后新增独立 external_transaction_id、reference_type=REFUND 的 Ref，并以 original_provider_transaction_ref_id 反向指向原 Ref。本表创建后只追加，不以当前 Provider 配置改写历史。

### t_provider_interaction

每次真实 Provider HTTP 交互的受控通信记录。字段：provider_interaction_id、payment_action_id、provider_dispute_action_id、interaction_no、interaction_status、started_at、finished_at、request_file_id、request_content_hash、response_file_id、response_content_hash、http_status、provider_result_code、latency_ms、create_time、update_time。两父级严格 XOR：恰有一个 payment_action_id、provider_dispute_action_id 非空；uk_provider_interaction_action(payment_action_id, interaction_no) 和 uk_provider_interact_dispute_act(provider_dispute_action_id, interaction_no) 分别保证父级内序号唯一。interaction_status 取 STARTED、RESPONSE_RECEIVED、NETWORK_ERROR、TIMED_OUT。发送前插入 STARTED；收到响应或网络终止时仅允许一次条件更新到终态，终态后不得改变。文件对可空但必须成对出现并通过安全扫描；本表不保存原始凭据/PAN。

### t_duplicate_payment_case

同一 Intent 出现第二个不同、已验证的资金成功事实时建立的合并 Case 和补偿处置。只有不同 Action/Transaction Ref 的 SALE 成功或完成资金的 CAPTURE 成功才触发；重复 Callback、同一 external_transaction_id 重放、QUERY 确认原 Action，以及正常 AUTHORIZE→CAPTURE 链路都不是重复支付。字段：duplicate_payment_case_id、payment_intent_id、canonical_attempt_id、canonical_action_id、canonical_transaction_ref_id、duplicate_attempt_id、duplicate_action_id、duplicate_transaction_ref_id、duplicate_amount、currency、case_status、compensation_action_id、version、create_time、update_time。uk_dup_case_duplicate_action(duplicate_action_id) 唯一。case_status 取 OPEN、COMPENSATING、COMPENSATION_UNKNOWN、COMPENSATED、MANUAL_REVIEW。第一笔成功保持 canonical，第二笔迟到成功不能覆盖 Intent successful_attempt_id；它必须令后到 Attempt 进入 CONFLICTED、创建本 Case，并对 duplicate_transaction_ref_id 发起补偿退款。未 Capture 的重复 Authorization 只是多余授权冻结，应对精确 Authorization Ref 执行 VOID，不进入重复支付退款。version 防止 Callback、QUERY 和补偿动作结果竞争。

### t_payment_state_transition

Payment 资金主链白名单聚合的统一追加状态流转。字段：payment_state_transition_id、aggregate_type、aggregate_id、root_type、root_id、from_status、to_status、transition_no、reason_code、trigger_type、trigger_id、create_by、create_time。uk_payment_transition_no(aggregate_type, aggregate_id, transition_no) 保证单对象顺序；idx_payment_transition_root(root_type, root_id, create_time) 支持时间线；idx_payment_transition_trigger(trigger_type, trigger_id, create_time) 支持由同一命令、回调、查询或恢复任务追踪跨聚合变化。aggregate_type 与 root_type 都只允许 PAYMENT_INTENT、PAYMENT_ATTEMPT、PAYMENT_ACTION、CHANNEL_REFUND、DUPLICATE_PAYMENT_CASE。Provider Callback 已有 Callback/Item/Exception 事实，Provider Dispute Notice 已有独立 Event 流，二者不重复写入本表。trigger_id 是最长 128 字符的非空稳定触发标识，不能只依赖 create_by；transition_no 是状态序号不是 CAS。该表只追加，不保存 JSON 快照或 version。Provider/MID/连接与路由配置变更采用 Platform 操作审计和当前结构化状态，不在本表复制第二套配置 History。

## 6. Channel Refund（2）

### t_payment_refund_position

Payment 侧按成功支付可退款金额的并发控制 Root。首笔权威 SALE 或完成资金的 CAPTURE 成功并确定 canonical Provider Transaction Ref 时创建。字段：payment_refund_position_id、payment_intent_id、successful_attempt_id、original_provider_transaction_ref_id、original_amount、reserved_amount、refunded_amount、currency、version、create_time、update_time。uk_payment_refund_position(payment_intent_id, original_provider_transaction_ref_id) 唯一。约束为 reserved_amount + refunded_amount <= original_amount，金额均非负。original_provider_transaction_ref_id 精确冻结原成功 Ref；从该 Ref 的 payment_action_id 可恢复 Provider、Provider Account 和 MID。version 的真实竞争者是并发的 Channel Refund 创建、FAILED/CANCELLED 释放、SUCCEEDED 消费以及 UNKNOWN 查询恢复。

### t_channel_refund

一个 Commerce Refund Request 的唯一渠道退款 Root。字段：channel_refund_id、commerce_refund_request_id、payment_refund_position_id、refund_amount、currency、refund_status、version、create_time、update_time。`uk_channel_refund_commerce_req(commerce_refund_request_id)` 保证一个 Commerce Refund Request 只有一个渠道退款。Commerce 本地预占后直接调用 PAY-I04；Payment 锁定 Refund Position，以 Version/CAS 原子增加 `reserved_amount` 并创建唯一 Channel Refund，不调用 Finance、不保存 Finance Funding ID 或绑定版本。Intent 与成功 Attempt 已由 Payment Refund Position 唯一冻结，Channel Refund 不重复保存。refund_status 取 CREATED、PROCESSING、UNKNOWN、SUCCEEDED、FAILED、CANCELLED。合法路径 CREATED→PROCESSING/CANCELLED；PROCESSING→UNKNOWN/SUCCEEDED/FAILED/CANCELLED；UNKNOWN→PROCESSING/SUCCEEDED/FAILED/CANCELLED；终态不可复活。UNKNOWN 只保留在 Payment 本地并继续占用 Position，不通知 Finance；SUCCEEDED 把 reserved 转为 refunded并同时通知 Commerce 与 Finance；FAILED/CANCELLED 释放 reserved 且只通知 Commerce。精确退款导航为 Channel Refund → Payment Refund Position → original_provider_transaction_ref_id → 原 Ref → 原 payment_action_id → Provider/Provider Account/MID；新 REFUND Action 冻结同一渠道三元组并以 target_provider_transaction_ref_id 指向原 Ref，成功 REF 再以 original_provider_transaction_ref_id 指向原 Ref。version 保护 Provider Callback、QUERY、主动取消和 Commerce/Finance 成功通知的并发收敛。

## 7. Provider Callback（3）

### t_provider_callback

验签、去重和批量处理的 Callback 信封。字段：provider_callback_id、provider_id、provider_account_id、external_event_id、payload_fingerprint、signature_status、callback_status、received_at、file_id、content_hash、version、create_time、update_time。uk_provider_callback_dedup(provider_id, provider_account_id, external_event_id)；没有 external_event_id 时 uk_provider_callback_fingerprint(provider_id, provider_account_id, payload_fingerprint) 去重。事件号和指纹只要求在同一 Provider Account 内唯一，避免同一 Provider 的不同账户互相误判重复。provider_account_id 在尚未映射到 MID/Action 时提供验签和归一化账户上下文，Adapter 使用其当前和轮换宽限期内的验签密钥集合。signature_status 取 VERIFIED、REJECTED、UNKNOWN；callback_status 取 RECEIVED、PROCESSING、PROCESSED、REJECTED、EXCEPTION。version 保护同一批 Callback worker 重放/认领和最终归档状态；敏感 Header/原文不存本行。

### t_provider_callback_item

批量 Callback 的逐项归一化及目标映射。字段：provider_callback_item_id、provider_callback_id、item_no、event_type、external_transaction_id、target_type、target_id、normalized_status、provider_event_time、mapping_status、create_time。uk_provider_callback_item(provider_callback_id, item_no) 唯一。target_type 取 PAYMENT_ACTION、CHANNEL_REFUND、PROVIDER_DISPUTE_NOTICE、PROVIDER_DISPUTE_ACTION、UNKNOWN；mapping_status 取 MAPPED、UNMATCHED、AMBIGUOUS、IGNORED。该项为不可变输入/映射证据；后续异常另开 Case。

### t_provider_event_exception

签名、匹配、乱序或非资金状态冲突的独立处置 Case。字段：provider_event_exception_id、provider_callback_id、provider_callback_item_id、exception_type、exception_status、reason_code、resolved_by、resolved_at、version、create_time、update_time。exception_type 取 SIGNATURE、MATCHING、OUT_OF_ORDER、STATE_CONFLICT、PAYLOAD_SECURITY；exception_status 取 OPEN、INVESTIGATING、RESOLVED、IGNORED。version 保护 Operations 处置与自动重放竞争；资金第二笔成功必须使用 t_duplicate_payment_case，不使用本表。

## 8. Provider Dispute（3）

### t_provider_dispute_notice

Provider 争议身份、冻结金额事实和当前渠道状态 Root。字段：provider_dispute_notice_id、provider_id、mid_id、original_provider_transaction_ref_id、external_dispute_id、dispute_amount、currency、received_at、provider_deadline_at、current_channel_status、latest_event_id、latest_event_no、version、create_time、update_time。uk_provider_dispute_external(provider_id, external_dispute_id) 唯一；uk_dispute_notice_latest(latest_event_id) 保证同一争议事件至多被一个 Notice 指为最新事件。current_channel_status 取 RECEIVED、EVIDENCE_REQUIRED、SUBMITTED、UNDER_REVIEW、WON、LOST、CLOSED。身份/金额/原交易 Ref 创建后不可改；version 真实保护 Callback、Risk 指令和 Provider 结果并发更新当前状态/最新事件指针。

### t_provider_dispute_event

Provider 争议的不可变事件流。字段：provider_dispute_event_id、provider_dispute_notice_id、event_no、event_type、from_status、to_status、provider_event_time、reason_code、create_time。uk_provider_dispute_event(provider_dispute_notice_id, event_no) 唯一。event_type 取 NOTICE_RECEIVED、STATUS_CHANGED、OUTCOME_RECEIVED、DEADLINE_UPDATED。只追加；notice Root 的 version 和 latest_event_no 负责与 Callback/操作竞争。

### t_provider_dispute_action

按 Risk 指令真实提交举证或查询的渠道动作。字段：provider_dispute_action_id、provider_dispute_notice_id、dispute_case_id、dispute_submission_request_id、dispute_submission_request_version、dispute_evidence_package_id、dispute_evidence_package_version、action_type、action_status、provider_id、provider_account_id、mid_id、provider_idempotency_key、provider_result_time、version、create_time、update_time。uk_dispute_action_idempotency(provider_id, provider_idempotency_key) 唯一。action_type 取 SUBMIT_EVIDENCE、QUERY；action_status 取 CREATED、SUBMITTED、UNKNOWN、SUCCEEDED、FAILED。合法路径 CREATED→SUBMITTED/FAILED，SUBMITTED→UNKNOWN/SUCCEEDED/FAILED，UNKNOWN→SUBMITTED/SUCCEEDED/FAILED。version 保护回调、查询和网络重试；Action 冻结 Provider Account 和 Risk Request/Package 精确版本，不自行宣布业务 WON/LOST。

## 9. 可靠执行（2）

### t_command_idempotency

同步写命令的业务幂等与结果定位。字段：command_idempotency_id、caller_scope、operation_type、idempotency_key、request_hash、command_status、resource_type、resource_id、response_code、create_time、update_time。uk_command_idempotency(caller_scope, operation_type, idempotency_key) 唯一；同键不同 request_hash 必须拒绝。command_status 取 PROCESSING、SUCCEEDED、FAILED。version 不存在：唯一键和原子状态条件更新足以处理同键竞争，资源状态仍由业务 Root CAS 保护。

### t_compensation_event

业务变化同事务登记、提交后执行的 MQ 发布或本地恢复任务。字段严格复用本地 `compensation-event` 基础 JAR：id、event_key、event_type、execution_type、source_type、source_id、source_version、schema_version、payload_json、payload_hash、execute_status、execute_count、next_execute_at、lock_owner、lock_until、last_error_code、last_error_summary、lock_version、create_time、update_time。uk_compensation_event_key(event_key) 和 uk_compensation_source(source_type, source_id, source_version, event_type) 唯一；idx_compensation_claim(execute_status, next_execute_at, lock_until) 支持到期 Claim。execution_type 取 MQ_PUBLISH、LOCAL_HANDLER；execute_status 仅取 INIT、PROCESSING、ERROR、FAILED。`lock_version` 只保护多实例 Claim/Lease；确定成功后删除本行，不保存 SUCCEEDED 或 MANUAL_REOPENED 成功历史；人工重开必须记录操作者、原因和工单后把 FAILED 行受控恢复为 ERROR。UNKNOWN 不建立独立恢复表，而由 Attempt、Action、Channel Refund、Duplicate Case 或 Dispute Action 的原状态配合 LOCAL_HANDLER 查询恢复。

## 10. 核心状态、并发和不变量

- 同一 Intent 同时最多一个 active_attempt_id 和一个 successful_attempt_id。创建 Attempt、清空/设置指针、状态流转及业务事件在同一 Payment 本地事务；UNKNOWN 保留 active_attempt_id，第一笔权威成功才设置 successful_attempt_id。
- Attempt 创建实时校验 COM-I10 Readiness、主体/Client 与 Risk。失败请求形成 FAILED Attempt，冻结三条 Acceptance ID、acceptance_completed_at、Order Version、Risk Assessment/结果和 Reservation Group（若已取得）；不持久化 Eligibility 或 ATTEMPT_CREATE Gate。
- AUTHORIZE、SALE、CAPTURE 的首次 Provider 提交必须紧邻同步读取 Client、Operating Entity 和 Compliance 当前权威结论；明确阻断、技术失败、超时或任一结果缺失均 Fail-Closed。Payment 只在 Attempt、Action 与 State Transition 保存失败阶段和稳定原因，权威状态历史由 CCT/Risk 自己持有，不建立 t_payment_execution_gate 或 t_payment_execution_gate_result。VOID、REFUND、QUERY 和已提交/UNKNOWN Action 的幂等恢复不受这项首次提交检查阻断。
- 当前路由只能从 Policy Root 的 current_version_id 解析。Candidate、Decision 和首个 AUTHORIZE/SALE Action 的 Provider、Provider Account、MID 必须一致；独立 QUERY、REFUND、Dispute Action 也在创建时冻结其 Provider Account。PAY-I11 只诊断静态 Policy/MID Capability/Account 状态，绝不以 t_mid_health_state 的瞬时动态结论作为静态覆盖。
- Attempt 必须冻结精确 payment_method_code；Instrument 是可选单表，只有存在稳定 Provider 工具引用时才创建。Customer Action 只保存交互类型、当前状态、期限和非敏感稳定引用，所有临时会话材料仅短暂返回调用方。
- V1 支持 SALE，或一次 AUTHORIZE 后一次全额 CAPTURE。没有永久的“一 Attempt 一 Capture”数据库约束，以便未来多 Capture；V1 业务校验阻止第二次 Capture。
- UNKNOWN 的权威结果由 Callback 或同一父级的 QUERY Action 收敛：Attempt 可建 QUERY；Channel Refund 与 Duplicate Payment Case 在其 REFUND 后只能以 action_no>=2 建 QUERY，并写 Provider Interaction、Ref 和 t_payment_state_transition。不得在 UNKNOWN 期间创建新 Attempt、释放退款 Position 或伪造终态。
- 处理已验证资金成功事实时必须锁定 Intent：successful_attempt_id 为空才设首笔成功；相同 Action/Ref 是幂等重放；不同 SALE/完成资金 CAPTURE 的 Action/Ref 才创建 Duplicate Case。首笔指针保持不变，后到 Attempt 进入 CONFLICTED，并对重复 Ref 补偿退款。
- 所有不可变 Decision、Candidate、Transaction Ref、Callback Item、Dispute Event 和 State Transition 仅追加。Provider Interaction 不是追加历史：先插入 STARTED，再接收恰好一次终态条件更新。MVP 默认保留业务数据，不建设自动 Retention、Legal Hold、归档、匿名化或销毁基础设施。

## 11. 已删除/不采用的旧概念

旧 58 表模型中的 routing_policy_binding、routing_rule、routing_rule_condition 由 Scope 下沉到 t_routing_policy 和版本候选集替代；不采用 active_slot、Scope/Content/Projection Hash、专用 History 或 Current Projection。payment_recovery_task 由 t_compensation_event(LOCAL_HANDLER) 和原业务 Root 的 UNKNOWN 状态替代。channel_refund_attempt 已并入 t_channel_refund；refund_transaction_ref 已并入 t_provider_transaction_ref(reference_type=REFUND)。t_provider_connection、t_provider_connection_version、t_mid_limit、t_mid_credential_ref 与原 t_mid_health 已分别由 Provider Account、有类型 Capability 和动态 Health State 精简设计替代。t_payment_execution_gate 与 t_payment_execution_gate_result 已删除：首次资金 Action 提交前仍执行实时 Fail-Closed 检查，但不把技术前置校验复制成 Payment 业务聚合。t_three_ds_context 不采用，通用 t_payment_customer_action 只表达最小消费者交互状态。Instrument 不采用 JSON、EAV/Key-Value 或类型子表。Binding、Recovery、Conflict 双 Root、Dispute Projection、Restriction Projection、通用 Inbox/Outbox 均不进入当前模型。

## 12. 物理模型状态与后续边界

当前 PDManer 工件按本模型确定性生成为 29 表、373 字段、41 个唯一索引、44 个访问索引、63 条本库关系、7 条 1:1 关系和 7 个 Diagram Groups，并已通过同基线自动契约验证。PDManer 4.9.3 应用内打开、关系图和规范检查仍是不可替代的人工门禁。

本次授权只覆盖 PDManer 设计资产，不授权 Schema DDL/初始化 DML 落库、DataObject/Mapper/Repository、本地数据库自动测试、Java 模块、物理数据库或运行环境。进入数据库实现阶段后，必须在新的明确授权下保持 PDManer、手工 SQL、映射代码和自动测试同步。

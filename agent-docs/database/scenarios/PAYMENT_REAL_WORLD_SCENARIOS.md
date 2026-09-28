# Payment Orchestration 现实场景与逐表字段映射

> **Action Flow 后继：** 当前自有动作 PAY_RISK / PAY 和先路由后风控的 CARD + PAY 流程以[最新修订](../../../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)为准。本页广域场景仍保留前序叙述与外部原始引用；表格枚举同步当前模型。补偿任务类型、其他 Owner 和旧案例时间线不代表当前切片已实现。

> 当前字段位置以[整批精简修订](../../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md)为准；下列数据为设计示例，不代表真实数据库已迁移。

> **交易工具后继：** [已确认的交易级 Token 修订](../../../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)取代本页旧 Instrument、卡引用与首次支付入口描述；其余资金和渠道业务边界保留。数据库执行状态见当前台账。

最后更新：2026-09-14

本 Showcase 使用一组固定 ID，展示同一笔 NI 卡支付从配置、3DS、UNKNOWN 原操作恢复、退款、迟到重复成功补偿、批量 Callback 到争议举证的真实行。金额均为 USD，时间均为 UTC。它是 [Payment 逻辑模型](../PAYMENT_ORCHESTRATION_DATABASE.md) 的逐表场景契约，不是生产数据或 Channel 报文样本。

> **2026-09-05 横切迁移提示：** 本文的逐表记录继续精确描述当前生成模型及当时的消费者假设，不单独拥有
> Vault 或 `compensation_event` 契约。其中与当前三表 Protect/Reveal、补偿三字段身份和 4 KiB 可选 Payload
> 不一致的旧字段属于 [`CURRENT_IMPLEMENTATION.md`](../../CURRENT_IMPLEMENTATION.md) 中 VAULT-01 / COMP-01
> 的待迁移现状；完成 [Vault 消费者对齐](../../../docs/superpowers/plans/2026-09-05-vault-consumer-model-alignment.md)
> 和 [补偿六模型对齐](../../../docs/superpowers/plans/2026-09-05-compensation-event-six-model-alignment.md) 前，
> 不得把这些场景字段复制到新增模型、SQL、DataObject 或代码。

## 1. 固定身份、配置和时间线

| 事实 | 固定值 |
|---|---|
| Channel | NI；channel_id=81001；channel_code=NI |
| Operating Entity | entity_no=AE001 |
| Client | client_id=40001 |
| 订单来源 | source_type=COMMERCE_ORDER；source_id=70001 |
| Intent | payment_intent_id=100001；amount=100.00000000；currency=USD |
| 两个候选 MID | mid_id=82001（NI-A，选中）；mid_id=82002（NI-B，健康降级） |
| 选中执行账户 | channel_account_id=81101；account_code=NI-API-AE；integration_config_key=payment.ni.ae.primary |
| 3DS SALE Attempt | payment_attempt_id=101002；payment_action_id=102002；认证步骤 102101；attempt_no=2；SALE action_no=3 |
| 原始交易号 | channel_transaction_ref_id=103001；external_transaction_id=NI-SALE-90001 |
| 退款 | payment_refund_position_id=104001；channel_refund_id=105001；REFUND Action 的 payment_action_id=102004 |
| 迟到重复成功 | earlier attempt_id=101001；earlier action_id=102001；duplicate ref_id=103003；case_id=106001；compensation_action_id=102005 |
| 批量 Callback | channel_callback_id=107001；item 1/2/3 为 108001/108002/108003 |
| 争议 | channel_dispute_notice_id=109001；event_id=110001；dispute_action_id=111001 |
| 可靠执行 | command_idempotency_id=112001；id=113001 |

时间线：09:00 配置有效；09:30 Attempt 1 完成风控及路由后收到明确失败并释放 Intent 活动指针；10:00 创建 Attempt 2；10:01 完成 RISK_CHECK 后发起 AUTHENTICATE 并要求 3DS；10:03 首次网络结果 UNKNOWN；10:05 原操作恢复/Callback 确认 Attempt 2 SALE 成功；11:00 创建并成功退款 20；11:05 Channel 才报告 Attempt 1 的迟到真实成功，触发补偿；12:00 批量 Callback；次日收到争议并提交 Risk 指定的证据。

## 2. Channel/MID 配置与路由

先配置 NI、同一 Operating Entity 下两个可路由 MID，并在一个 Operating Entity Scope 策略版本中发布候选集。MID-A 健康，MID-B 因超时率被硬过滤；因此 Decision 冻结 MID-A、Channel 81001 与 Channel Account 81101。

| 表 | 实际行与关键字段 |
|---|---|
| t_payment_channel | channel_id=81001，channel_code=NI，channel_name=Network International，adapter_code=NI_CARD_V1，channel_status=ACTIVE |
| t_channel_account | channel_account_id=81101，channel_id=81001，account_code=NI-API-AE，external_account_id=NI-TENANT-AE-01，integration_config_key=payment.ni.ae.primary，account_status=ACTIVE |
| t_channel_mid | mid_id=82001，channel_id=81001，channel_account_id=81101，external_mid_code=NI-MID-A，entity_no=AE001，mid_status=ACTIVE，activated_at=2026-08-20T08:00:00.000000Z，closed_at=NULL；mid_id=82002，channel_id=81001，channel_account_id=81101，external_mid_code=NI-MID-B，entity_no=AE001，mid_status=ACTIVE，activated_at=2026-08-20T08:05:00.000000Z，closed_at=NULL |
| t_mid_capability | 同一 Scope（mid_id=82001，payment_method_code=CARD，scheme_code=VISA，country_code=AE，currency=USD，mcc_code=5732）下：mid_capability_id=82201，capability_code=ACTION.SALE，value_type=BOOLEAN，boolean_value=1，decimal_value=NULL，integer_value=NULL，code_value=NULL，capability_status=ENABLED；82202：ACTION.REFUND/BOOLEAN/1；82203：SECURITY.3DS_MODE/CODE/SUPPORTED；82204：AMOUNT.MIN/DECIMAL/1.00000000；82205：AMOUNT.MAX/DECIMAL/5000.00000000。mid_id=82002 以 82206–82210 保存相同 Scope 与能力值 |
| t_mid_health_state | mid_health_state_id=82401，mid_id=82001，payment_method_code=CARD，health_status=HEALTHY，circuit_status=CLOSED，health_score=96，sample_count=100，technical_success_count=96，channel_error_count=2，timeout_count=2，technical_success_rate=0.96000000，channel_error_rate=0.02000000，timeout_rate=0.02000000，p95_latency_ms=680，consecutive_failure_count=0，window_started_at=2026-08-25T09:48:00.000000Z，window_ended_at=2026-08-25T09:58:00.000000Z，observed_at=2026-08-25T09:58:00.000000Z，valid_until=2026-08-25T10:08:00.000000Z；mid_health_state_id=82402，mid_id=82002，payment_method_code=CARD，health_status=DEGRADED，circuit_status=HALF_OPEN，health_score=54，sample_count=100，technical_success_count=82，channel_error_count=3，timeout_count=15，technical_success_rate=0.82000000，channel_error_rate=0.03000000，timeout_rate=0.15000000，p95_latency_ms=2400，consecutive_failure_count=3，window_started_at=2026-08-25T09:48:00.000000Z，window_ended_at=2026-08-25T09:58:00.000000Z，observed_at=2026-08-25T09:58:00.000000Z，valid_until=2026-08-25T10:08:00.000000Z |
| t_routing_policy | routing_policy_id=83001，scope_type=OPERATING_ENTITY，scope_id=50001，country_code=AE，currency=USD，payment_method_code=CARD，industry_code=ELECTRONICS，mcc_code=5732，match_priority=10，policy_status=ACTIVE，current_version_id=83101，version=2 |
| t_routing_policy_version | routing_policy_version_id=83101，routing_policy_id=83001，version_no=1，version_status=ACTIVE |
| t_routing_policy_mid | routing_policy_mid_id=83201，routing_policy_version_id=83101，mid_id=82001，candidate_priority=10，weight_value=100.0000000000；routing_policy_mid_id=83202，routing_policy_version_id=83101，mid_id=82002，candidate_priority=20，weight_value=100.0000000000 |

NI_CARD_V1 对 81101 的 integration_config_key=payment.ni.ae.primary 解析当前运行配置。SALE、REFUND 与回调均冻结或使用 channel_account_id=81101；Payment 不保存 Endpoint、Secret、证书、Webhook 密钥或其版本，也不维护 MID 专属凭据。

## 3. 一次 SALE、3DS 与首次提交前实时检查

本节按 [Execution 后继方案](../../../requirements/20260913/payment-Execution设计/data-design.md)更新示例；NI、Channel、Risk 等是跨系统目标场景，不是当前执行日志或真实接入证明。

Attempt 1 已在 09:30 经过完整路由、获得 Channel 的明确 FAILED 结果并释放 Intent 活动指针。Commerce 随后创建 Attempt 2；Attempt 从 Payment Method Catalog 冻结 payment_method_code=CARD，三条法律 Acceptance 由 Commerce 校验并保存，Payment 不复制。首先以 RISK_CHECK Action 保存原 Risk 评估的准确身份与 ALLOW 结果，清空风险执行位，再路由并创建 AUTHENTICATE Action；内部 Risk 要求的挑战成功后，再创建同 Attempt 的 SALE Action，Channel 再核验原认证证明。首次 Channel 提交前，Payment 在 10:00:03 紧邻调用 NI 前同步检查 Client/Operating Entity 当前状态与 Compliance 当前限制；Action 创建时即为 PROCESSING；三项均明确允许后才开始 Channel Interaction。检查明确阻断、超时、技术失败或缺少任一结论均 Fail-Closed：不调用 NI，Action 与 Attempt 记为 FAILED，运行原因记入安全日志/可靠任务，真实渠道响应仅记录到 Action.channel_response_code/channel_response_msg。通过结果不持久化为独立 Gate，权威状态历史仍由 CCT/Risk 持有。

交易级工具 Token 绑定 Client、Intent、原 source_submission_id 和精确支付方式，先准备工具，再调用统一 pay；一个 Token 最多关联一个 Attempt。t_payment_attempt.payment_instrument_token_id 直接关联 t_payment_instrument_token，不创建旧 Instrument 行、工具会话表或方式子表。Card Owner 保存稳定 instrument_ref 并在事务外提供瞬时用卡材料；Token 期限不延长，首次资金已提交后不得重新领取材料发送，UNKNOWN 重入原标准操作，由 Channel 按自身特性查询或安全重试。Wallet/Crypto 的具体 Owner 能力后续设计。原 Action 的 interaction_type 目录保留 REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE 统一表达消费者后续动作；本期只写 THREE_DS_CHALLENGE，只保存类型、状态与交互期限；渠道操作引用和认证证明归 Channel，不保存 Redirect URL、Client Secret、SDK Token、QR Payload 或 bearer-like 参数。

| 表 | 实际行与关键字段 |
|---|---|
| t_payment_intent | payment_intent_id=100001，source_type=COMMERCE_ORDER，source_id=70001，client_id=40001，entity_no=AE001，client_pricing_version_id=60002，partner_share_version_id=NULL，platform_charge_version_id=NULL，industry_code=ELECTRONICS，mcc_code=5732，consumer_country_code=AE，amount=100.00000000，currency=USD，intent_status=OPEN，active_attempt_id=101002，successful_attempt_id=NULL，expires_at=2026-08-25T10:30:00.000000Z，version=4 |
| t_payment_attempt | payment_attempt_id=101002，payment_intent_id=100001，channel_id=81001，channel_account_id=81101，mid_id=82001，attempt_no=2，payment_method_code=CARD，source_submission_id=112001，capture_mode=AUTOMATIC，payment_instrument_token_id=101101，attempt_status=PROCESSING，active_action_id=102101，authorized_amount=0，captured_amount=0，version=3 |
| t_payment_instrument_token | payment_instrument_token_id=101101，payment_instrument_token=synthetic_instrument_token_101101，client_id=40001，payment_intent_id=100001，source_submission_id=112001，payment_method_code=CARD，instrument_type=CARD，instrument_ref=synthetic_card_owner_40001，expires_at=2026-08-25T10:09:55.000000Z，revoked_at=NULL，create_time=2026-08-25T09:59:55.000000Z，update_time=2026-08-25T09:59:55.000000Z |
| t_routing_decision | routing_decision_id=83302，payment_attempt_id=101002，routing_policy_id=83001，routing_policy_version_id=83101，selected_channel_id=81001，selected_channel_account_id=81101，selected_mid_id=82001，decision_status=SELECTED，decision_reason_code=LOWEST_PRIORITY_HEALTHY |
| t_route_candidate_eval | route_candidate_eval_id=83403，routing_decision_id=83302，channel_id=81001，channel_account_id=81101，mid_id=82001，eligible_flag=1，candidate_priority=10，weight_value=100.0000000000，health_status=HEALTHY，circuit_status=CLOSED，health_score=96，technical_success_rate=0.96000000，timeout_rate=0.02000000，p95_latency_ms=680，sample_count=100，health_observed_at=2026-08-25T09:58:00.000000Z；route_candidate_eval_id=83404，routing_decision_id=83302，channel_id=81001，channel_account_id=81101，mid_id=82002，eligible_flag=0，candidate_priority=20，weight_value=100.0000000000，exclusion_reason_code=HEALTH_DEGRADED，health_status=DEGRADED，circuit_status=HALF_OPEN，health_score=54，technical_success_rate=0.82000000，timeout_rate=0.15000000，p95_latency_ms=2400，sample_count=100，health_observed_at=2026-08-25T09:58:00.000000Z |
| t_payment_action | payment_action_id=102100，payment_attempt_id=101002，action_no=1，action_type=PAY_RISK，action_status=SUCCEEDED，channel_idempotency_key=NULL，transaction_assessment_id=72001，risk_result=ALLOW，authentication_requirement=CHALLENGE_REQUIRED，amount=100.00000000，currency=USD，version=3 |
| t_payment_action | payment_action_id=102101，payment_attempt_id=101002，action_no=2，action_type=AUTHENTICATE，action_status=PROCESSING，amount=100.00000000，currency=USD，channel_idempotency_key=action:102101，interaction_type=THREE_DS_CHALLENGE，interaction_expires_at=2026-08-25T10:10:00.000000Z，version=3，channel_response_code=CHALLENGE_REQUIRED，channel_response_msg=等待持卡人完成挑战 |
| t_channel_interaction | channel_interaction_id=103101，payment_action_id=102101，channel_dispute_action_id=NULL，interaction_no=1，interaction_status=RESPONSE_RECEIVED，started_at=2026-08-25T10:00:04.000000Z，finished_at=2026-08-25T10:00:05.000000Z，request_file_id=NULL，request_content_hash=NULL，response_file_id=NULL，response_content_hash=NULL，http_status=200，channel_result_code=THREEDS_REQUIRED，latency_ms=1000 |
| t_command_idempotency | command_idempotency_id=112001，caller_scope=COMMERCE_INTERNAL，operation_type=CREATE_PAYMENT_ATTEMPT，idempotency_key=cmd-order-70001-pay-2，request_hash=sha256:request-100001-2，process_status=SUCCEEDED，resource_type=PAYMENT_ATTEMPT，resource_id=101002，response_code=PROCESSING |

## 4. UNKNOWN 原操作恢复为 SALE 成功

下列行表示先后发生的阶段，不能理解为同时存在多个未终态动作。AUTHENTICATE 完成后，才将 active_action_id 从认证动作切换到新建 SALE；各次 HTTP 恢复均关联原 SALE。

Consumer 完成 3DS 且可信认证结果落库后，创建 SALE 102002（action_no=3、认证步骤 102101）；NI 首次资金提交超时。原 SALE Action 转 UNKNOWN，Attempt 保持 PROCESSING、active_action_id=102002，Intent 保持 OPEN 并继续占用 Attempt。LOCAL_HANDLER 重入原 SALE 和原渠道幂等键，Channel 内部按自身特性恢复；确认成功后写不可变外部 Ref，并在本地事务清空两个活动指针、设置唯一成功 Attempt 指针，不新增 QUERY 行。

| 表 | 实际行与关键字段 |
|---|---|
| t_payment_action | payment_action_id=102002，payment_attempt_id=101002，action_no=3，action_type=PAY，action_status=UNKNOWN，amount=100.00000000，currency=USD，channel_idempotency_key=action:102002，version=4；恢复成功时仅将此行改为 SUCCEEDED，保留原身份 |
| t_payment_action | 102101 更新为 action_status=SUCCEEDED，version=4 |
| t_channel_interaction | channel_interaction_id=103102，payment_action_id=102002，channel_dispute_action_id=NULL，interaction_no=2，interaction_status=TIMED_OUT，started_at=2026-08-25T10:03:00.000000Z，finished_at=2026-08-25T10:03:08.000000Z，request_file_id=NULL，request_content_hash=NULL，response_file_id=NULL，response_content_hash=NULL，http_status=NULL，channel_result_code=NETWORK_TIMEOUT，latency_ms=8000；channel_interaction_id=103103，payment_action_id=102002，channel_dispute_action_id=NULL，interaction_no=1，interaction_status=RESPONSE_RECEIVED，started_at=2026-08-25T10:05:00.000000Z，finished_at=2026-08-25T10:05:01.000000Z，request_file_id=NULL，request_content_hash=NULL，response_file_id=NULL，response_content_hash=NULL，http_status=200，channel_result_code=SALE_CONFIRMED，latency_ms=1000 |
| t_channel_transaction_ref | channel_transaction_ref_id=103001，payment_action_id=102002，channel_id=81001，mid_id=82001，reference_type=PAY，external_transaction_id=NI-SALE-90001，original_channel_txn_ref_id=NULL，channel_event_time=2026-08-25T10:03:05.000000Z，amount=100.00000000，currency=USD |
| t_payment_attempt | 101002 更新为 attempt_status=SUCCEEDED，active_action_id=NULL，authorized_amount=0，captured_amount=100.00000000，version=5 |
| t_payment_intent | 100001 更新为 intent_status=SUCCEEDED，active_attempt_id=NULL，successful_attempt_id=101002，version=5 |
| t_compensation_event | id=113001，event_key=40001:102002，event_type=PAYMENT_ACTION_RECOVERY_V1，sub_event_type=-，payload_json=NULL，execute_status=INIT，execute_count=0，next_execute_at=2026-08-25T10:04:00.000000Z，lock_owner=NULL，lock_until=NULL，last_error_code=NULL，last_error_summary=NULL，lock_version=1；10:04 Claim 后以同一 event_key 查询 NI，取得确定 SALE 成功即删除该行，不保留 SUCCEEDED 成功记录 |

## 5. 部分退款和第二笔迟到成功补偿

Payment 为 Attempt 2 的原 SALE 建立可退款 Position。精确导航是 Channel Refund → Payment Refund Position → original_channel_txn_ref_id → 原 Transaction Ref → payment_action_id → 原资金 Action.payment_attempt_id → Attempt 冻结的 Channel、Channel Account 与 MID；REFUND Action 的 target_channel_txn_ref_id 指向原 Ref，成功 REFUND Transaction Ref 再用 original_channel_txn_ref_id 指回原 Ref。Commerce 已以自身事务预占退款资格并提供请求 73001；Commerce 本地预占后直接调用 PAY-I04，Payment 锁定 Refund Position，以 Version/CAS 原子增加 reserved 并创建唯一 Channel Refund 与 REFUND Action，不调用 Finance，也不保存 Finance Funding ID、版本或 Ack。UNKNOWN 只保留在 Payment 本地并继续占用 Position，不通知 Finance；SUCCEEDED 把 reserved 转为 refunded，并同时通知 Commerce 与 Finance；FAILED/CANCELLED 释放 reserved，且只通知 Commerce。

随后 Channel 才对已在 09:30 明确失败、已释放活动指针的 Attempt 1 报告真实 SALE 成功。同一 Intent 下第二个不同 SALE 或完成资金的 CAPTURE 成功事实，且 Action 与 Transaction Ref 均不同，才属于重复支付；重复 Callback、同一外部交易 Ref 重放、原操作恢复确认同一 Action，以及正常 AUTHORIZE→CAPTURE 都不判定为重复支付。Intent 的 successful_attempt_id 不替换，后到 Attempt 1 记为 SUCCEEDED，并以独立 Duplicate Case 标记重复成功，并对重复 Ref 发起补偿退款。未 Capture 的重复 Authorization 应执行 VOID，不进入重复支付退款。Attempt 1 和 Attempt 2 均已各自完成路由及候选评估；二者从未并行活动或 UNKNOWN。

| 表 | 实际行与关键字段 |
|---|---|
| t_payment_refund_position | payment_refund_position_id=104001，payment_intent_id=100001，successful_attempt_id=101002，original_channel_txn_ref_id=103001，original_amount=100.00000000，reserved_amount=0，refunded_amount=20.00000000，currency=USD，version=3 |
| t_channel_refund | channel_refund_id=105001，commerce_refund_request_id=73001，payment_refund_position_id=104001，refund_amount=20.00000000，currency=USD，refund_status=SUCCEEDED，version=4 |
| t_payment_action | payment_action_id=102004，payment_attempt_id=NULL，channel_refund_id=105001，duplicate_payment_case_id=NULL，action_no=1，action_type=REFUND，action_status=SUCCEEDED，target_channel_txn_ref_id=103001，amount=20.00000000，currency=USD，channel_idempotency_key=NI-REFUND-73001，channel_result_time=2026-08-25T11:00:21.000000Z，version=2 |
| t_channel_interaction | channel_interaction_id=103104，payment_action_id=102004，channel_dispute_action_id=NULL，interaction_no=1，interaction_status=RESPONSE_RECEIVED，started_at=2026-08-25T11:00:20.000000Z，finished_at=2026-08-25T11:00:21.000000Z，request_file_id=NULL，request_content_hash=NULL，response_file_id=NULL，response_content_hash=NULL，http_status=200，channel_result_code=REFUND_OK，latency_ms=1000 |
| t_channel_transaction_ref | channel_transaction_ref_id=103002，payment_action_id=102004，channel_id=81001，mid_id=82001，reference_type=REFUND，external_transaction_id=NI-REFUND-90001，original_channel_txn_ref_id=103001，channel_event_time=2026-08-25T11:00:21.000000Z，amount=20.00000000，currency=USD |
| t_payment_attempt | payment_attempt_id=101001，payment_intent_id=100001，channel_id=81001，channel_account_id=81101，mid_id=82001，attempt_no=1，payment_method_code=CARD，attempt_status=SUCCEEDED，captured_amount=100.00000000，version=3；其 09:30 已明确 FAILED、active_attempt_id 已清空，11:05 才由已验证、矛盾的 Channel 资金事实作狭窄权威纠正为 SUCCEEDED 并创建 Duplicate Case；该事务不重开正常终态、不替换 Intent 成功指针、不提交新 Channel 请求，也不发送第二次 PaymentSucceeded |
| t_routing_decision | routing_decision_id=83301，payment_attempt_id=101001，routing_policy_id=83001，routing_policy_version_id=83101，selected_channel_id=81001，selected_channel_account_id=81101，selected_mid_id=82001，decision_status=SELECTED，decision_reason_code=LOWEST_PRIORITY_HEALTHY |
| t_route_candidate_eval | route_candidate_eval_id=83401，routing_decision_id=83301，channel_id=81001，channel_account_id=81101，mid_id=82001，eligible_flag=1，candidate_priority=10，weight_value=100.0000000000，health_status=HEALTHY，circuit_status=CLOSED，health_score=96，technical_success_rate=0.96000000，timeout_rate=0.02000000，p95_latency_ms=680，sample_count=100，health_observed_at=2026-08-25T09:58:00.000000Z；route_candidate_eval_id=83402，routing_decision_id=83301，channel_id=81001，channel_account_id=81101，mid_id=82002，eligible_flag=0，candidate_priority=20，weight_value=100.0000000000，exclusion_reason_code=HEALTH_DEGRADED，health_status=DEGRADED，circuit_status=HALF_OPEN，health_score=54，technical_success_rate=0.82000000，timeout_rate=0.15000000，p95_latency_ms=2400，sample_count=100，health_observed_at=2026-08-25T09:58:00.000000Z |
| t_payment_action | payment_action_id=102001，payment_attempt_id=101001，channel_refund_id=NULL，duplicate_payment_case_id=NULL，action_no=2，action_type=PAY，action_status=SUCCEEDED，amount=100.00000000，currency=USD，channel_idempotency_key=NI-SALE-IDEMP-100001-1，channel_result_time=2026-08-25T11:05:02.000000Z，version=3；09:30 的明确 FAILED 已释放 Intent 指针，11:05 的已验证、矛盾 Channel 资金事实才允许 FAILED→SUCCEEDED；该终态行不提交任何新的 Channel 请求 |
| t_channel_transaction_ref | channel_transaction_ref_id=103003，payment_action_id=102001，channel_id=81001，mid_id=82001，reference_type=PAY，external_transaction_id=NI-SALE-90002，channel_event_time=2026-08-25T11:05:02.000000Z，amount=100.00000000，currency=USD |
| t_duplicate_payment_case | duplicate_payment_case_id=106001，payment_intent_id=100001，canonical_attempt_id=101002，canonical_action_id=102002，canonical_transaction_ref_id=103001，duplicate_attempt_id=101001，duplicate_action_id=102001，duplicate_transaction_ref_id=103003，duplicate_amount=100.00000000，currency=USD，case_status=COMPENSATING，compensation_action_id=102005，version=1 |
| t_payment_action | payment_action_id=102005，payment_attempt_id=NULL，channel_refund_id=NULL，duplicate_payment_case_id=106001，action_no=1，action_type=REFUND，action_status=PROCESSING，target_channel_txn_ref_id=103003，amount=100.00000000，currency=USD，channel_idempotency_key=NI-DUP-COMP-106001，version=1 |

## 6. 一份批量 Callback、异常 Case 与争议举证

NI 的一个验签通过批量 Callback 同时包含退款终态、重复成功补偿状态和新的争议通知。每项单独映射；无法匹配/乱序才入 Event Exception。收到争议后 Payment 保存渠道 Notice 及不可变 Event；Risk 提供 Case、Submission Request 和 Evidence Package 的精确版本，Payment 发起 SUBMIT_EVIDENCE Action。不再使用 t_payment_state_transition。Channel Callback 使用 Callback/Item/Exception，Channel Dispute Notice 使用自己的 Event；资金证据保留 Channel Transaction Ref，不复制通用状态历史。

| 表 | 实际行与关键字段 |
|---|---|
| t_channel_callback | channel_callback_id=107001，channel_id=81001，channel_account_id=81101，external_event_id=NI-BATCH-20260825-1200，payload_fingerprint=sha256:ni-batch-1200，signature_status=VERIFIED，callback_status=PROCESSED，received_at=2026-08-25T12:00:00.000000Z，file_id=NULL，content_hash=NULL，version=2 |
| t_channel_callback_item | channel_callback_item_id=108001，channel_callback_id=107001，item_no=1，event_type=REFUND_STATUS，external_transaction_id=NI-REFUND-90001，target_type=CHANNEL_REFUND，target_id=105001，normalized_status=SUCCEEDED，channel_event_time=2026-08-25T11:00:21.000000Z，mapping_status=MAPPED；108002：item_no=2，event_type=SALE_STATUS，external_transaction_id=NI-SALE-90002，target_type=PAYMENT_ACTION，target_id=102001，normalized_status=SUCCEEDED，channel_event_time=2026-08-25T11:05:02.000000Z， mapping_status=MAPPED；108003：item_no=3，event_type=DISPUTE_NOTICE，external_transaction_id=NI-SALE-90001，target_type=CHANNEL_DISPUTE_NOTICE，target_id=109001，normalized_status=EVIDENCE_REQUIRED， mapping_status=MAPPED |
| t_channel_event_exception | channel_event_exception_id=108101，channel_callback_id=107001，channel_callback_item_id=108002，exception_type=STATE_CONFLICT，exception_status=RESOLVED，reason_code=LATE_SUCCESS_HANDLED_AS_DUPLICATE，resolved_by=SYSTEM_DUPLICATE_HANDLER，resolved_at=2026-08-25T12:00:02.000000Z，version=2 |
| t_channel_dispute_notice | channel_dispute_notice_id=109001，channel_id=81001，mid_id=82001，original_channel_txn_ref_id=103001，external_dispute_id=NI-DSP-4001，dispute_amount=100.00000000，currency=USD，received_at=2026-08-26T09:00:00.000000Z，channel_deadline_at=2026-09-05T23:59:59.000000Z，current_channel_status=SUBMITTED，latest_event_id=110002，latest_event_no=2，version=3 |
| t_channel_dispute_event | channel_dispute_event_id=110001，channel_dispute_notice_id=109001，event_no=1，event_type=NOTICE_RECEIVED，from_status=NULL，to_status=EVIDENCE_REQUIRED，channel_event_time=2026-08-26T09:00:00.000000Z，reason_code=NI_RFI；channel_dispute_event_id=110002，event_no=2，event_type=STATUS_CHANGED，from_status=EVIDENCE_REQUIRED，to_status=SUBMITTED，channel_event_time=2026-08-26T10:01:01.000000Z，reason_code=EVIDENCE_SUBMITTED |
| t_channel_dispute_action | channel_dispute_action_id=111001，channel_dispute_notice_id=109001，dispute_case_id=75001，dispute_submission_request_id=75002，dispute_submission_request_version=4，dispute_evidence_package_id=75003，dispute_evidence_package_version=2，action_type=SUBMIT_EVIDENCE，action_status=SUCCEEDED，channel_id=81001，channel_account_id=81101，mid_id=82001，channel_idempotency_key=NI-DSP-SUB-109001，channel_result_time=2026-08-26T10:01:01.000000Z，version=2 |
| t_channel_interaction | channel_interaction_id=103105，payment_action_id=NULL，channel_dispute_action_id=111001，interaction_no=1，interaction_status=RESPONSE_RECEIVED，started_at=2026-08-26T10:00:52.000000Z，finished_at=2026-08-26T10:01:01.000000Z，request_file_id=76001，request_content_hash=sha256:approved-evidence-package-75003-request，response_file_id=NULL，response_content_hash=NULL，http_status=200，channel_result_code=EVIDENCE_ACCEPTED，latency_ms=9000 |
| t_compensation_event | id=113002，event_key=dispute-notice-109001-v1，event_type=CHANNEL_DISPUTE_FACT_PUBLISH，sub_event_type=DisputeNoticeReceived，payload_json=NULL，execute_status=INIT，execute_count=0，next_execute_at=2026-08-26T09:00:01.000000Z，lock_owner=NULL，lock_until=NULL，last_error_code=NULL，last_error_summary=NULL，lock_version=1 |

本期删除独立 Pricing Fact 表；定价属性获取及下游事件合同需后续迁移，不能用未知属性推导零费率。

## 7. 27 表覆盖核对

| 分组 | 已在本 Showcase 列行的表数 | 表 |
|---|---:|---|
| Channel 与 MID | 5/5 | t_payment_channel、t_channel_account、t_channel_mid、t_mid_capability、t_mid_health_state |
| Payment Routing | 5/5 | t_routing_policy、t_routing_policy_version、t_routing_policy_mid、t_routing_decision、t_route_candidate_eval |
| 支付执行与异常 | 7/7 | t_payment_intent、t_payment_attempt、t_payment_instrument_token、t_payment_action、t_channel_transaction_ref、t_channel_interaction、t_duplicate_payment_case |
| Channel Refund | 2/2 | t_payment_refund_position、t_channel_refund |
| Channel Callback | 3/3 | t_channel_callback、t_channel_callback_item、t_channel_event_exception |
| Channel Dispute | 3/3 | t_channel_dispute_notice、t_channel_dispute_event、t_channel_dispute_action |
| 可靠执行 | 2/2 | t_command_idempotency、t_compensation_event |

本场景不采用 routing_policy_binding、routing_rule_condition、payment_execution_gate、payment_execution_gate_result、t_three_ds_context、payment_recovery_task、channel_refund_attempt、refund_transaction_ref、active_slot、t_channel_connection、t_channel_connection_version、t_mid_limit、t_mid_credential_ref 或 t_mid_health。首次资金 Action 提交前的实时检查保留为应用行为，失败落 Attempt/Action，渠道结果保留安全 code/msg；其余职责分别由 Scope 下沉策略、原 AUTHENTICATE Action 的最小交互事实、原聚合 UNKNOWN 状态加本地补偿事件、Channel Refund Root、统一外部交易引用、Intent 指针，以及 Channel Account/typed Capability/dynamic Health State 承担。

# Payment Orchestration V1 PDManer 物理建模决策

> **Action Flow 后继：** [CARD + PAY 修订](../../../../requirements/20260913/payment-Execution设计/card-pay-actionActor-flow-amendment.md)统一自有动作 PAY_RISK / PAY 名称、条件对象和执行位切换；AUTHORIZE 成功结束本次 Flow，Capture 为独立入口。前序 RISK_CHECK / SALE 表述按该范围被取代。

> **pay 防重后继：** 2026-09-15 用户确认按 Attempt 来源提交唯一键防重，当前规则见 [pay 阅读说明](../pay-interface-walkthrough.md)。前序[公共包接入约定](../development/command-idempotency-differences.md)保留为历史记录；pay 不再访问命令表，本轮未变更下方物理 Manifest 或执行数据库清理。

> **字段精简后继：** [2026-09-14 修订](../../../../requirements/20260913/payment-Execution设计/execution-field-simplification-amendment.md)拥有当前三表字段、Attempt 渠道绑定及删除 Pricing Fact/状态流转表的决定。

> **2026-09-11 模型切片：** 当前物理模型采用 [OE / Client / Pricing 四系统决定](../../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)。本页历史正文中的数字 OE、Assignment、独立 Acceptance、Client 多 OE 合同与 Agreement-OE 结算条款由该决定精确取代；本页摘要描述当前模型产物，运行代码、SQL 执行及待迁移状态仅见 [当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。Risk 本期不动。

> **交易级工具后继：** [已确认修订](../../../../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)取代本页旧 Instrument/卡引用描述。目标采用 Token 绑定与 Attempt 唯一关联，绑定字段不可变，revoked_at 单向撤销；不新增会话表。

最后更新：2026-09-15
状态：模型统计、验证和数据库迁移事实见[当前实施台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)；下方机器可读 Manifest 用于模型工件一致性检查。

> **2026-09-05 横切规范更新：** 前序 `compensation_event`、Vault 元数据及物理 CHECK/FK 说明不再是新增实现目标。Payment 的 Execution 后继模型已按已确认方案对齐补偿目标字段，实际数据库执行仍单独核验。按 [当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 的 COMP-01、VAULT-01、DB-01 完成同步迁移前，不得复制旧字段，也不得声称模型已经迁移。

> **Channel 五表后继：** [2026-09-21 需求](../../../../requirements/20260921/payment-渠道账号与MID能力查询/requirement.md)及其[实施合同](../../../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)取代本页旧账号定位字段、单表能力及健康状态部分；其他历史正文保持证据性质。

## 1. 范围与权威来源

本文件冻结 Payment Orchestration V1 从逻辑目录落到 PDManer 的类型、可空性、索引、本库关系、图组、行级约束和数据库外事务约束。权威输入为：

- [Payment 逻辑数据库模型](../../../../agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md)
- [Payment 现实场景](../../../../agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md)
- [Channel Account 与 MID 精简设计](../../../../agent-docs/plans/2026-08-25-payment-provider-account-mid-simplification-design.md)
- [Payment Execution 支付工具与消费者动作精简设计](../../../../agent-docs/plans/2026-08-25-payment-execution-simplification-design.md)
- [平衡型物理模型设计（本轮被精简设计局部覆盖）](../../../../agent-docs/plans/2026-08-25-payment-balanced-physical-model-design.md)
- [共享数据库设计规范](../../../../agent-docs/DATABASE_DESIGN_GUIDE.md)
- [共享 PDManer 模板](../../../../agent-docs/templates/pdmaner/template.pdma.json)

本文维护模型物理约束；Payment pay 的当前受理流程由后继阅读说明承接，实际数据库与运行环境切换仍单独执行。项目不使用 Flyway、Liquibase 或应用启动时 Schema 迁移。

## 2. 已冻结物理 Manifest

<!-- PAYMENT_PHYSICAL_MANIFEST_START -->
```json
{
  "modelTotals": {
    "tables": 27,
    "fields": 345,
    "uniqueIndexes": 38,
    "accessIndexes": 44,
    "relations": 63,
    "oneToOneRelations": 7,
    "diagramGroups": 7
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "4fb5a4c88f00bd5552dc4c66de1811aee4bc73aee40c40674c264966261e4979",
  "structuralProjectionSha256": "2793402a691608c374c988e9ef2e01a24ad965d3fdec1b0f9acaf8ade026b690"
}
```
<!-- PAYMENT_PHYSICAL_MANIFEST_END -->

### 2.1 按服务的表归属（重构第 05 步，不改表结构）

上方 Manifest 记录的是拆分前的 Payment Orchestration 统一模型统计，表结构与字段不变。重构第 05 步按 [design/03 §2.3](../../../../requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md) 把模型拆为两份规范模型，由同一生成器的分区（`scripts/pdmaner/payment-service-partition.mjs`）产出；跨服务关系不再出现在任一模型中，`t_compensation_event` 每库一份：

| 服务 / 数据库 | 规范模型 | 表 |
|---|---|---|
| payment / `mor_payment` | [`payment.pdma.json`](payment.pdma.json)、[`sql/payment-schema.sql`](sql/payment-schema.sql) | `t_routing_decision`、`t_route_candidate_eval`、`t_payment_intent`、`t_payment_attempt`、`t_payment_instrument_token`、`t_payment_action`、`t_channel_transaction_ref`、`t_duplicate_payment_case`、`t_payment_refund_position`、`t_channel_refund`、`t_compensation_event`；第 12 步前暂居的卡域 4 表 `t_payment_card`、`t_payment_card_credential`、`t_payment_card_instrument`、`t_payment_stored_card`（归属 card）；在 dispute 服务建立前暂留的 `t_channel_dispute_notice`、`t_channel_dispute_event`、`t_channel_dispute_action`（归属 dispute，仅 Schema）；待删除的 `t_command_idempotency`（S10） |
| channel-connector / `mor_channel_connector` | [`channel-connector.pdma.json`](../../../channel-connector/docs/database/channel-connector.pdma.json)、[`sql/channel-connector-schema.sql`](../../../channel-connector/docs/database/sql/channel-connector-schema.sql) | `t_payment_channel`、`t_channel_account`、`t_channel_mid`、`t_mid_capability`、`t_mid_capability_detail`、`t_routing_policy`、`t_routing_policy_version`、`t_routing_policy_mid`、`t_channel_interaction`、`t_channel_callback`、`t_channel_callback_item`、`t_channel_event_exception`、`t_compensation_event` |

拆分后的统计（由两份模型计算）：payment 19 表、34 条本库关系；channel-connector 13 表、14 条本库关系。

七个 Diagram Group 为 `Channel 与 MID`、`支付路由`、`支付执行`、`Channel Refund`、`Channel Callback`、`Channel Dispute`、`可靠执行`。每张表各作为主表出现一次，每条关系在具备完整端点的图中出现；关系总量与一对一数量由下方工件 Manifest 核验。

## 3. 通用物理规则

| 语义 | 决策 |
|---|---|
| 物理命名 | 表使用 `t_` + 小写 `snake_case`；唯一索引使用 `uk_`；普通访问索引使用 `idx_`；Sidecar 逻辑约束名使用 `ck_`，但不生成物理 `CHECK` |
| 主键 | 每表一个非自增 Snowflake `BIGINT` 主键，`autoIncrement=false` |
| 金额与权重 | 金额 `DECIMAL(24,8)`；路由权重 `DECIMAL(18,10)` |
| 时间与币种 | UTC `DATETIME(6)`；ISO 4217 `CHAR(3)` |
| 版本 | 只在存在 Callback、Query、恢复或命令并发竞争的 Root 保留业务 `version`；发布内容用 `version_no`；补偿 Claim 只用 `lock_version`；公共命令表使用 Foundation 技术 CAS `version` |
| 不可变事实 | Decision、Candidate、Transaction Ref、Callback Item、Dispute Event 创建后只追加，不保存机械 `update_time/version` |
| 连接 | `dbConn`、`dbConns` 和 `profile.default.dbConn` 均为空，不保存环境地址、账号或凭据 |
| 关系 | 只表达 Payment 本库逻辑关系，不生成物理 FK；跨系统 ID 仅保存稳定引用，不建立跨库外键；Owner 写入口以本地事务、实际资源锁、完整 Tuple 查询、唯一索引和 CAS 保护 |

Routing Policy 的 `current_version_id` 可空，用于先创建 Root 再创建首个 Version；ACTIVE 时必须非空。其单字段 UK 只约束非空值，利用 MySQL 允许多个 `NULL` 的语义，既解除循环插入又保证一个 Version 不会成为两个 Root 的当前版本。

## 4. 行级逻辑约束（Sidecar）

PDManer 4.9.3 没有可跨数据库稳定生成的约束节点。下表冻结供模型、Owner 应用校验和反例测试追踪的逻辑表达式，不生成物理 `CHECK`。Owner 写入口必须在本地事务中结合 Root/资源锁、完整 Tuple 查询、唯一索引和 CAS 执行校验，并由仓库自有 `local-environment` 真实 MySQL 测试证明。可空比较仍显式处理 `IS NULL/IS NOT NULL`，避免 Java 与 SQL 查询语义分叉。

<!-- PAYMENT_CHECK_CONSTRAINTS_START -->
| 表 | 约束名 | 逻辑表达式 |
|---|---|---|
| `t_channel_account` | `ck_channel_account_status` | `account_status IN ('DRAFT','ACTIVE','SUSPENDED','CLOSED')` |
| `t_channel_mid` | `ck_channel_mid_times` | `(mid_status = 'DRAFT' AND activated_at IS NULL AND closed_at IS NULL) OR (mid_status IN ('ACTIVE','SUSPENDED') AND activated_at IS NOT NULL AND closed_at IS NULL) OR (mid_status = 'CLOSED' AND closed_at IS NOT NULL AND (activated_at IS NULL OR closed_at >= activated_at))` |
| `t_mid_capability_detail` | `ck_mid_capability_values` | `(value_type = 'BOOLEAN' AND boolean_value IS NOT NULL AND boolean_value IN (0,1) AND decimal_value IS NULL AND code_value IS NULL) OR (value_type = 'DECIMAL' AND boolean_value IS NULL AND decimal_value IS NOT NULL AND code_value IS NULL) OR (value_type = 'CODE' AND boolean_value IS NULL AND decimal_value IS NULL AND code_value IS NOT NULL)` |
| `t_routing_policy` | `ck_route_policy_scope` | `(scope_type IN ('CLIENT','OPERATING_ENTITY') AND scope_id > 0) OR (scope_type = 'GLOBAL' AND scope_id = 0)` |
| `t_routing_policy` | `ck_route_policy_priority` | `match_priority > 0` |
| `t_routing_policy` | `ck_route_policy_active` | `policy_status <> 'ACTIVE' OR current_version_id IS NOT NULL` |
| `t_routing_policy` | `ck_route_policy_root_ver` | `version > 0` |
| `t_routing_policy_version` | `ck_route_policy_ver_no` | `version_no > 0` |
| `t_routing_policy_mid` | `ck_route_policy_mid_rank` | `candidate_priority > 0 AND weight_value > 0` |
| `t_routing_decision` | `ck_route_decision_policy_pair` | `(routing_policy_id IS NULL AND routing_policy_version_id IS NULL) OR (routing_policy_id IS NOT NULL AND routing_policy_version_id IS NOT NULL)` |
| `t_routing_decision` | `ck_route_decision_state` | `(decision_status = 'SELECTED' AND routing_policy_id IS NOT NULL AND routing_policy_version_id IS NOT NULL AND selected_channel_id IS NOT NULL AND selected_mid_id IS NOT NULL AND selected_channel_account_id IS NOT NULL) OR (decision_status = 'CONFIGURATION_CONFLICT' AND routing_policy_id IS NULL AND routing_policy_version_id IS NULL AND selected_channel_id IS NULL AND selected_mid_id IS NULL AND selected_channel_account_id IS NULL AND decision_reason_code IS NOT NULL) OR (decision_status = 'REJECTED' AND selected_channel_id IS NULL AND selected_mid_id IS NULL AND selected_channel_account_id IS NULL AND decision_reason_code IS NOT NULL)` |
| `t_route_candidate_eval` | `ck_route_candidate_flag` | `(eligible_flag = 1 AND channel_account_id IS NOT NULL AND health_status IS NOT NULL AND circuit_status IS NOT NULL AND health_score IS NOT NULL AND technical_success_rate IS NOT NULL AND timeout_rate IS NOT NULL AND p95_latency_ms IS NOT NULL AND sample_count IS NOT NULL AND health_observed_at IS NOT NULL AND exclusion_reason_code IS NULL) OR (eligible_flag = 0 AND exclusion_reason_code IS NOT NULL)` |
| `t_route_candidate_eval` | `ck_route_candidate_health` | `(health_status IS NULL AND circuit_status IS NULL AND health_score IS NULL AND technical_success_rate IS NULL AND timeout_rate IS NULL AND p95_latency_ms IS NULL AND sample_count IS NULL AND health_observed_at IS NULL) OR (channel_account_id IS NOT NULL AND health_status IS NOT NULL AND circuit_status IS NOT NULL AND health_score IS NOT NULL AND technical_success_rate IS NOT NULL AND timeout_rate IS NOT NULL AND p95_latency_ms IS NOT NULL AND sample_count IS NOT NULL AND health_observed_at IS NOT NULL)` |
| `t_route_candidate_eval` | `ck_route_candidate_rank` | `candidate_priority > 0 AND weight_value > 0` |
| `t_payment_intent` | `ck_payment_intent_amount` | `amount > 0` |
| `t_payment_intent` | `ck_payment_intent_pointer` | `(intent_status = 'OPEN' AND successful_attempt_id IS NULL) OR (intent_status = 'SUCCEEDED' AND active_attempt_id IS NULL AND successful_attempt_id IS NOT NULL) OR (intent_status IN ('EXPIRED','CANCELLED') AND active_attempt_id IS NULL AND successful_attempt_id IS NULL)` |
| `t_payment_intent` | `ck_payment_intent_version` | `version > 0` |
| `t_payment_attempt` | `ck_payment_attempt_no` | `attempt_no > 0` |
| `t_payment_attempt` | `ck_payment_attempt_amounts` | `authorized_amount >= 0 AND captured_amount >= 0` |
| `t_payment_attempt` | `ck_payment_attempt_version` | `version > 0` |
| `t_payment_action` | `ck_payment_action_parent` | `(payment_attempt_id IS NOT NULL) + (channel_refund_id IS NOT NULL) + (duplicate_payment_case_id IS NOT NULL) = 1` |
| `t_payment_action` | `ck_payment_action_parent_type` | `(payment_attempt_id IS NOT NULL AND action_type IN ('PAY_RISK','AUTHENTICATE','AUTHORIZE','CAPTURE','PAY','VOID')) OR (channel_refund_id IS NOT NULL AND action_type = 'REFUND') OR (duplicate_payment_case_id IS NOT NULL AND action_type = 'REFUND')` |
| `t_payment_action` | `ck_payment_action_values` | `action_no > 0 AND amount > 0 AND version > 0` |
| `t_channel_transaction_ref` | `ck_channel_txn_amount` | `amount > 0` |
| `t_channel_interaction` | `ck_channel_interact_parent` | `(payment_action_id IS NOT NULL) + (channel_dispute_action_id IS NOT NULL) = 1` |
| `t_channel_interaction` | `ck_channel_interact_no` | `interaction_no > 0` |
| `t_channel_interaction` | `ck_channel_interact_req_file` | `(request_file_id IS NULL AND request_content_hash IS NULL) OR (request_file_id IS NOT NULL AND request_content_hash IS NOT NULL)` |
| `t_channel_interaction` | `ck_channel_interact_rsp_file` | `(response_file_id IS NULL AND response_content_hash IS NULL) OR (response_file_id IS NOT NULL AND response_content_hash IS NOT NULL)` |
| `t_channel_interaction` | `ck_channel_interact_state` | `(interaction_status = 'STARTED' AND finished_at IS NULL AND latency_ms IS NULL) OR (interaction_status IN ('RESPONSE_RECEIVED','NETWORK_ERROR','TIMED_OUT') AND finished_at IS NOT NULL AND finished_at >= started_at AND latency_ms IS NOT NULL AND latency_ms >= 0)` |
| `t_duplicate_payment_case` | `ck_duplicate_case_values` | `duplicate_amount > 0 AND version > 0` |
| `t_payment_refund_position` | `ck_refund_position_amounts` | `original_amount > 0 AND reserved_amount >= 0 AND refunded_amount >= 0 AND reserved_amount + refunded_amount <= original_amount AND version > 0` |
| `t_channel_refund` | `ck_channel_refund_values` | `refund_amount > 0 AND version > 0` |
| `t_channel_callback` | `ck_channel_callback_dedup` | `external_event_id IS NOT NULL OR payload_fingerprint IS NOT NULL` |
| `t_channel_callback` | `ck_channel_callback_file` | `(file_id IS NULL AND content_hash IS NULL) OR (file_id IS NOT NULL AND content_hash IS NOT NULL)` |
| `t_channel_callback` | `ck_channel_callback_version` | `version > 0` |
| `t_channel_callback_item` | `ck_channel_callback_item_no` | `item_no > 0` |
| `t_channel_event_exception` | `ck_event_exception_resolution` | `(exception_status IN ('OPEN','INVESTIGATING') AND resolved_by IS NULL AND resolved_at IS NULL) OR (exception_status IN ('RESOLVED','IGNORED') AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)` |
| `t_channel_event_exception` | `ck_event_exception_version` | `version > 0` |
| `t_channel_dispute_notice` | `ck_dispute_notice_values` | `dispute_amount > 0 AND (channel_deadline_at IS NULL OR channel_deadline_at > received_at) AND ((latest_event_id IS NULL AND latest_event_no = 0) OR (latest_event_id IS NOT NULL AND latest_event_no > 0)) AND version > 0` |
| `t_channel_dispute_event` | `ck_channel_dispute_event_no` | `event_no > 0` |
| `t_channel_dispute_action` | `ck_dispute_action_payload` | `(action_type = 'SUBMIT_EVIDENCE' AND dispute_submission_request_id IS NOT NULL AND dispute_submission_request_version IS NOT NULL AND dispute_submission_request_version > 0 AND dispute_evidence_package_id IS NOT NULL AND dispute_evidence_package_version IS NOT NULL AND dispute_evidence_package_version > 0) OR (action_type = 'QUERY' AND dispute_submission_request_id IS NULL AND dispute_submission_request_version IS NULL AND dispute_evidence_package_id IS NULL AND dispute_evidence_package_version IS NULL)` |
| `t_channel_dispute_action` | `ck_dispute_action_version` | `version > 0` |
| `t_command_idempotency` | `ck_command_resource_pair` | `(resource_type IS NULL AND resource_id IS NULL) OR (resource_type IS NOT NULL AND resource_id IS NOT NULL)` |
| `t_compensation_event` | `ck_compensation_counters` | `execute_count >= 0 AND lock_version > 0` |
| `t_compensation_event` | `ck_compensation_lease` | `(execute_status = 'PROCESSING' AND lock_owner IS NOT NULL AND lock_until IS NOT NULL) OR (execute_status IN ('INIT','ERROR','FAILED') AND lock_owner IS NULL AND lock_until IS NULL)` |
<!-- PAYMENT_CHECK_CONSTRAINTS_END -->

上表的 Compensation Counter 行只冻结当前生成模型，旧 Source/Version/Schema 字段不是 COMP-01 目标契约；迁移完成前不得复制到新 SQL 或实现。

## 5. 跨行、路由和资金约束

- 所有外部调用在事务外。Intent 指针、Attempt active_action_id、状态/金额和本地可靠任务在同一结果事务提交；按实际资源串行并使用版本及原指针 CAS 防止迟到结果清除后继动作。
- Intent 固定 Client、entity_no、金额、币种与已获准条款版本；来源唯一。法律接受留在 Commerce，Payment 不反查 Commerce。
- 受理前完成路由；PAY_RISK.ALLOW 才能推进认证或资金动作，认证要求随该风险 Action 冻结，Channel 负责原认证证明的绑定和有效期。风控 UNKNOWN 不允许另起支付。
- SELECTED Decision、获胜 Candidate 与 Attempt 的 Channel/Account/MID 一致；路由前三项全空，选择后同事务固定且不可更换。退款沿 target Ref → 原资金 Action → 原 Attempt 使用冻结账户；Dispute Action 保留自身坐标。
- CAPTURE/VOID 的 target_channel_txn_ref_id 必须为同 Attempt 的成功 AUTHORIZE；REFUND 指明 PAY/CAPTURE。Action 父级保持三选一，不为了读取渠道额外填写另一个父级。
- 全局唯一 channel_idempotency_key 只由 Payment 分配，重试同键；有同键不同渠道的历史库必须先核验，禁止静默合并。渠道 code/msg 不保存原始报文或敏感值。
- V1 为 PAY 或 AUTHORIZE 后全额 CAPTURE。等待手动 Capture 的 Attempt 仍 PROCESSING，active_action_id 可空但 Intent 占位保留。Capture/Void 失败不抹去原授权。
- Refund Position 以 version/CAS 保护 reserved + refunded <= original。UNKNOWN 保留预占且不通知 Finance；SUCCEEDED 通知 Commerce/Finance，FAILED/CANCELLED 释放预占且只通知 Commerce。
- Callback 按 Channel Account 去重并验签，资金 Ref 保持不可变。可信第二笔资金成功保留 canonical Attempt，后到 Attempt 为 SUCCEEDED 并建立 Duplicate Case；不再发一次首笔成功。
- Dispute Notice 的最新 Event 属于同一 Notice；Risk Request/Package ID 与版本须完整核对。未受本次影响的配置、健康和 Callback 行级规则继续有效。

## 6. 可靠执行与审计边界

- pay 使用 `t_payment_attempt.source_submission_id` 唯一键防重，并核对原提交内容。`t_command_idempotency` 的 caller scope、operation type、idempotency key 与 request_hash 规则仅为前序模型合同，不再参与 pay 执行。
- `t_compensation_event` 与业务变化同事务写入，提交后执行。当前目标以 `(event_key,event_type,sub_event_type)` 唯一；可选 `payload_json` 最大 4 KiB UTF-8，只保存无法从本库权威事实恢复的最小非敏感 JSON Object，禁止 Vault Token。状态只允许 INIT、PROCESSING、ERROR、FAILED；PROCESSING 必须持有成对 Lease，确定成功后删除记录。XXL-Job 只负责丢失唤醒后的兜底扫描和分片。旧数据库中的 Source/Version/Schema/Hash 字段不得自动猜测迁移；当前模型已采用最小字段，数据库状态与模型状态分别记录。
- 不建设 Inbox/Outbox、中央补偿库或 `t_payment_recovery_task`。消费者幂等落在业务事实/投影唯一键，UNKNOWN 恢复使用原聚合状态和 LOCAL_HANDLER。
- 不再建立 t_payment_state_transition 或通用替代历史表；保留资金 Ref、Callback/Item/Exception、Dispute Event 和 Duplicate Case。消息稳定身份及发生时刻需在 Java 切换前改造，不能用成功即删除的补偿任务充当永久历史。

## 7. 安全边界

- PAN、CVV/CVC、完整磁道、PIN/PIN Block 和卡有效期不得进入模型、Vault、事件、文件或日志。
- Attempt 直接关联交易级工具 Token 的内部 ID；Token 保存受控 instrument_ref，不复制工具原始材料。
- 原 Action 只保存最小 interaction_type / interaction_expires_at；不保存 Redirect URL、Client Secret、SDK Token、QR Payload 或 bearer-like 参数。
- `integration_config_key` 只是 Adapter 解析当前运行配置的非敏感定位键；Channel/API/Webhook Secret、Endpoint、证书、私钥、超时值和轮换版本均不进入 Payment 模型。Adapter 从获批运行配置与 Secrets Manager/KMS 获取当前可用值，Payment 不保存精确历史 Secret、Endpoint 或凭据版本。
- Channel 原文只有通过 Schema、卡数据和 Secret 扫描后，才可使用受控 `file_id + content_hash` 成对关联。普通字段、错误摘要和 Compensation Payload 不得包含凭据、卡数据、消费者敏感原值或 Vault Token。

## 8. 实施与人工门禁

PDManer 模型是当前设计源，审核后的当前 Schema SQL 是可执行数据库定义。获得数据库实现授权后，必须在同一次变更中同步 PDManer、经人工评审并手工执行的完整 Schema SQL、DO/Mapper/Repository、上述逻辑约束、跨行 Processor 校验和仓库自有 `local-environment` 真实 MySQL `auto` 反例测试。禁止 Flyway、Liquibase、Testcontainers 或应用启动时 Schema/初始化数据迁移；不得让生成 DDL 直接修改共享环境。

自动测试必须验证 JSON 结构、字段、索引、关系、中文说明、枚举/开放代码、确定性摘要和现实场景映射，但不能替代 PDManer 4.9.3 人工门禁。人工 Review 至少确认：文件可打开、七张关系图完整、循环指针显示合理、中文表名/字段说明可读、规范检查无不可接受错误。人工检查前不得宣称 PDManer 应用内兼容性已通过。

# Risk Manual Payout Eligibility 四检查点对齐实施记录

状态：Risk-only 契约已按 Finance 84 表 Manual Payout 权威原位对齐；不新增表、字段、索引或关系。当前结构基线保持 `71 tables / 865 fields / 106 unique indexes / 74 access indexes / 111 local relations / 9 one-to-one relations / 8 Diagram Groups / 83 CHECK constraints`；模型与 CHECK 摘要必须以本次最终生成器输出为准。

## 1. 目标与边界

本次只修正 `t_settlement_risk_assessment`、Decision Point Obligation、Canonicalizer、静态校验、测试、PDManer 和 Risk 权威文档中的跨系统名称与来源矩阵。Settlement Eligibility Control 三表继续表达长期阻断和 Client 最低保证金门槛；表名保留是为了兼容既有 Risk 领域语义，不代表恢复 Finance Settlement Run、Candidate、Promotion、Payment 或 Attempt。

继续使用稳定的 `assessment_id + evaluation_id` 作为不可变、可检索的 RCO-I09 结果坐标；不增加 `eligibility_result_id`、`result_version`、`result_hash` 或通用 Result Root。

## 2. 四个业务检查点

| checkpoint_type | 调用时机 | source_fact_type | 来源坐标 |
|---|---|---|---|
| `PAYOUT_CREATION` | Payout 本地创建和锁账前 | `PAYOUT` | 预分配 `payout_id`，`source_fact_version=1` |
| `PAYOUT_FINAL_APPROVAL` | Final Checker 推进 Payout 状态前 | `PAYOUT` | 审批命令开始时锁定的当前 Payout ID 与变更前 expected version |
| `PAYOUT_EXECUTION_CREATION` | 创建 CREATED Execution 并占用现金前 | `PAYOUT_EXECUTION` | 预分配 `execution_id`，`source_fact_version=1` |
| `PAYOUT_EXTERNAL_SUBMISSION` | 每次真正调用银行前 | `PAYOUT_EXECUTION` | 提交命令开始时锁定的当前 Execution ID 与 CREATED 状态的变更前 expected version |

每个不同检查点都创建新的 PRIMARY Evaluation 和 `t_settlement_risk_assessment`。同一幂等命令重放复用原 pair，即使 Risk 成功后 Finance 本地事务失败或本地根已推进，也不得以新版本重算原命令。前一 Execution 只有权威 FAILED 才能创建连续 `attempt_no` 的新 Retry Execution；新 Execution 必须重新获得 Execution Creation 与 External Submission 两对坐标。UNKNOWN 阻断 Retry、取消和替代提交。

`source_fact_version` 是评估命令观察并锁定的变更前 expected version，不能引用尚未产生的审批后或 SUBMITTED 未来版本。全部版本必须在 MySQL signed INT 的 `1..2147483647` 范围；创建类检查进一步固定为版本 1，使 Risk 成功事实在 Finance 本地事务失败时仍可审计并可由同一命令安全复用。

## 3. 输入、返回与账户门禁

四个检查点共同冻结：

- `role_type/role_id`；
- `operating_entity_id`；
- `gross_amount=owner_debit_amount=cash_required_amount`，不得使用 `requested_amount` 或 `beneficiary_amount`；
- `currency`；
- `bank_country=t_beneficiary_account.bank_country`；
- `source_fact_type/source_fact_id/source_fact_version`；
- `input_hash`。

RCO-I09 返回固定为 `assessment_id`、`evaluation_id`、`decision`、`reason_codes[]`、`matched_controls[]`、`required_amount`、`required_currency` 和 `control_set_hash`。Client 的金额币种成对返回；Local Partner 两列必须同时为 NULL。非 ALLOW、技术失败、Assessment/Evaluation 1:1 不一致、Scope 不一致或 Hash 无法重建均 Fail-Closed。

Assessment 还必须满足 `checkpoint_type = Decision Point.point_code` 与 `input_hash = Evaluation.input_snapshot_hash`。结果只允许 `(eligibility_decision, technical_status, proposed_decision, business_decision)` 的六组映射：`(ALLOW, SUCCEEDED, ALLOW, ALLOW)`、`(RESTRICT, SUCCEEDED, ALLOW, DENY)`、`(NULL, SUCCEEDED, REVIEW, REVIEW)`、`(NULL, SUCCEEDED, DENY, DENY)`、`(NULL, FAILED, NULL, NULL)`、`(NULL, TIMED_OUT, NULL, NULL)`。静态校验同时拒绝空 Assertion/Selector/Mapping、重复 Mapping Key、未知 Alias/Field/Operator 与不闭合 Mapping，避免元数据真空通过。

四个检查点都要求 Finance 重新执行本地账户门禁：Client 的 `CLIENT_RECOVERY_RECEIVABLE/NONE` 必须为零且 `CLIENT_SECURITY_DEPOSIT/NONE` 不低于 Risk 返回门槛；Local Partner 的 `PARTNER_RECOVERY_RECEIVABLE/NONE` 必须为零，并且不得携带或查询 Client 保证金字段。Risk 只返回控制与门槛，不读取或锁定 Finance Account。

## 4. Finance 快照边界

Finance 本地 `eligibility_hash` 只证明完整 RCO-I09 响应与 Owner/OE/币种/Payout 金额 Scope 的 Canonical 完整性和幂等性，不回传 Risk，也不能替代下一检查点的新评估。

- Payout 行只冻结 Final Approval 的完整当前结果，并把 pair、完整结果和 `eligibility_hash` 纳入一次性的 `approval_hash`。
- Execution 行只有一组可变 Risk 快照字段；External Submission 的完整结果成为该 Execution 本地当前/最终快照。
- Execution Creation pair 仍在 Risk 的不可变 Assessment/Evaluation 中长期可检索；不得声称 Finance 同时保存四份不可变本地 Snapshot。
- Execution 一旦成功创建为 CREATED，必须立即占用 `cash_required_amount` 的 BANK_CASH 容量，不能推迟到外部提交。

## 5. 实施与验证范围

原位修改范围：

- `scripts/pdmaner/risk/manifests/03-evaluation.mjs`
- `scripts/pdmaner/risk/model-policy.mjs`
- `scripts/pdmaner/risk/settlement-eligibility-canonicalizer.mjs`
- `scripts/pdmaner/risk/decision-engine-static-validator.mjs`
- Risk Oracle、Decision Engine/Settlement/场景测试
- Risk PDManer 生成模型
- Risk 逻辑数据库、现实场景与物理决策文档

本记录不授权 Java、Migration、物理数据库、银行集成或运行环境；PDManer 4.9.3 应用内视觉检查仍是人工门禁。

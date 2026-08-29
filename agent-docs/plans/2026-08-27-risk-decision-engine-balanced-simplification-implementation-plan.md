# Risk Decision Engine + Evaluation 27-Table Balanced Simplification Implementation Plan

> **For agentic workers:** Execute task-by-task with strict RED → GREEN verification. Do not create a branch or commit; this workspace is intentionally shared with other Codex tasks. Coordinate before changing the six shared entry documents.

**Goal:** 将 Risk & Compliance 的 Decision Engine 与 Evaluation 两个图组从 37 表精简为 27 表，在不削弱自由编排、类型安全 AST、Release 静态检查、Shadow 旁路、审核发布、决策解释和 Action 副作用审计的前提下，使 Risk 总表数从当前 85 表降为 75 表。

**Architecture:** 保留 Decision Point、Point Version、输入/Feature/Rule Set/Obligation 绑定、Rule AST、Action 参数协议、Release、Evaluation、三类输入快照以及 Action Intent/Effect 的独立业务语义；合并只承担重复目录身份、同生命周期一对一明细或同一追加审计流的表。运营自由编排的边界仍是工程注册的 Feature、Context Field、类型化 Operator、Condition Group 和 Action，不开放 SQL、Script、JSON 表达式、EAV、任意类名或任意 HTTP 调用。

**Tech Stack:** Markdown、Node.js ESM、`node:test`、PDManer 4.9.3 JSON、SHA-256、MySQL 8.0 物理约束语义。

**Scope:** 修改 Risk Decision Engine/Evaluation 的 Manifest、Policy、独立 Oracle、生成模型、逻辑/场景/物理文档及相关 requirements 断言；最终按生成结果语义合并共享入口中的 Risk 统计。不得创建 Java 模块、Migration、物理数据库、运行环境、基础设施、分支或 Git 提交。

> **Current-scope notice (2026-08-28):** 本文件仅继续作为 Decision Engine/Evaluation `19 + 8` 表及其 AST、Release、Shadow、Action 契约的历史实施记录。Limit 相关结构与全库统计已由 [Limit 7 表平衡精简设计](./2026-08-28-risk-limit-balanced-simplification-design.md) 和 [对应实施计划](./2026-08-28-risk-limit-balanced-simplification-implementation-plan.md) 取代；下文出现的 Limits 10、Limit Plan 或 75 表全库数字只描述当时阶段，不是当前权威基线。

## 0. 2026-08-28 Evaluation/Action hardening amendment

27 表精简完成后，本计划追加一次不增表的平衡加固：

- `t_evaluation_action_intent` 删除无外键 `source_id`，改为 nullable `rule_action_id/obligation_id` 严格 XOR；Action 型系统义务进入 Intent，`RESERVE_LIMIT_GROUP` 义务不伪造成 Action。
- SUCCEEDED Evaluation 同时保存 proposed/business，调整原因由二者是否相等决定；FAILED/TIMED_OUT 不带任何业务决定或默认决定标记。
- MERGED Intent 与 Effect 必须在 Evaluation、Action Version、Target 和 Merge Policy 上逐值一致，每个 Effect 至少有一个贡献者。
- `RISK_ACTION_EFFECT_V1` 固定数据库可重算的 Effect 身份与贡献者 Schema、RFC 8785 JCS/UTF-8 编码和数值排序；不纳入未持久化 Handler 结果，REAL 幂等键固定派生为 `risk-action-effect:v1:<effect_hash>`。
- Effect 只允许来自 SUCCEEDED Evaluation；RULE/OBLIGATION Intent 来源必须归属于本次 Evaluation 的准确 Point Version。
- `decision_point.business_domain` 是输入快照族的唯一权威：Checkout/Payment/Settlement 恰好各自一类，DD/Dispute 不建空壳快照。

本计划完成时的历史阶段基线为 `75 tables / 897 fields / 105 unique indexes / 82 access indexes / 121 local relations / 9 one-to-one relations / 8 Diagram Groups / 69 CHECK constraints`；Decision Engine/Evaluation 仍为 `19 + 8`，没有恢复或新增表。当前全库统计以 2026-08-28 Limit 方案和生成器输出为准。

## 1. Current Baseline and Target

当前工作区由生成器验证的权威基线是：

| 范围 | 当前 | 目标 | 变化 |
|---|---:|---:|---:|
| Decision Engine 图组 | 27 表 | 19 表 | -8 |
| Evaluation 图组 | 10 表 | 8 表 | -2 |
| 两图组合计 | 37 表 | 27 表 | -10 |
| Risk & Compliance 总计 | 85 表 | 75 表 | -10 |

当前完整物理统计是 `85 tables / 933 fields / 111 unique indexes / 82 access indexes / 131 local relations / 11 one-to-one relations / 8 Diagram Groups / 63 CHECK constraints`。实施时只预先冻结 75 表和 `19 + 8` 图组表数；字段、索引、关系、1:1 和 CHECK 的最终数量必须由完成后的 Generator 实际计算并经独立 Oracle 固化，不在计划阶段猜测。

在本计划实施当时，其他图组为 Due Diligence 19、Risk Data 6、Limits 10、Settlement Controls 4、Dispute & Evidence 7、Reliability 2；其中 Limits 后续已由 2026-08-28 方案精简为 7 表。

## 2. Frozen Simplification Decisions

### 2.1 Ten-table reduction arithmetic

| 当前结构 | 目标结构 | 净变化 | 理由 |
|---|---|---:|---|
| `t_feature_definition + t_feature_version` | 扩展 `t_feature_version` | -1 | Feature Code 与执行版本始终一起被 Release/Point 引用，无当前独立 Definition 生命周期消费者 |
| `t_action_definition + t_action_version` | 扩展 `t_action_version` | -1 | Action Catalog 身份和 Handler 协议可由 Code + Version 唯一表达 |
| `t_rule_set + t_rule_set_version` | 扩展 `t_rule_set_version` | -1 | Rule Set 不跨 Decision Point 复用，稳定根不承载独立状态 |
| `t_rule_operand + t_rule_operand_value` | 多行类型化 `t_rule_operand` | -1 | Literal 只是 Operand 的一种来源，不需要额外聚合根 |
| `t_release_static_check + t_release_approval + t_release_state_change` | `t_release_audit_event` | -2 | 三者都是同一 Release 的不可变、有类型、按时序追加审计事实 |
| `t_release_shadow_run + t_release_shadow_summary` | 扩展 `t_release_shadow_run` | -1 | Summary 与 Run 严格 1:1，且只在 Run 完成时冻结 |
| `t_release_shadow_comparison` | Shadow `t_decision_evaluation` 的比较字段 | -1 | Candidate Evaluation 本身就是逐笔比较的唯一候选侧事实 |
| `t_evaluation_rule_result + t_evaluation_predicate_result` | `t_evaluation_rule_trace` | -1 | 两者都是 Evaluation 下的布尔解释轨迹，可用受控 Row Type 区分 |
| `t_action_effect + t_action_execution` | 扩展 `t_action_effect` | -1 | Execution 与 REAL Effect 严格 1:1；SIMULATED Effect 永远不执行 |

删除 12 张旧表并新增 2 张目标表，净减 10 张：

- 删除：`t_feature_definition`、`t_action_definition`、`t_rule_set`、`t_rule_operand_value`、`t_release_static_check`、`t_release_approval`、`t_release_state_change`、`t_release_shadow_summary`、`t_release_shadow_comparison`、`t_evaluation_rule_result`、`t_evaluation_predicate_result`、`t_action_execution`。
- 新增：`t_release_audit_event`、`t_evaluation_rule_trace`。

### 2.2 Final Decision Engine inventory: 19 tables

1. `t_decision_point`
2. `t_decision_point_version`
3. `t_decision_point_input_field`
4. `t_decision_point_feature`
5. `t_decision_point_rule_set`
6. `t_decision_point_obligation`
7. `t_feature_version`
8. `t_feature_dependency`
9. `t_rule_set_version`
10. `t_risk_rule`
11. `t_rule_node`
12. `t_rule_operand`
13. `t_action_version`
14. `t_action_parameter_definition`
15. `t_rule_action`
16. `t_rule_action_parameter`
17. `t_decision_release`
18. `t_release_audit_event`
19. `t_release_shadow_run`

### 2.3 Final Evaluation inventory: 8 tables

1. `t_decision_evaluation`
2. `t_evaluation_feature_result`
3. `t_evaluation_rule_trace`
4. `t_evaluation_action_intent`
5. `t_action_effect`
6. `t_checkout_input_snapshot`
7. `t_transaction_risk_assessment`
8. `t_settlement_risk_assessment`

`t_decision_evaluation` 必须继续归属 Evaluation 图组，不能为了表数分配将其移动到 Decision Engine 图组。

### 2.4 Concepts that must remain separate

- `decision_point` 与 `decision_point_version`：稳定事件身份和不可变输入/输出协议不是同一生命周期。
- Point Version 的 Input、Feature、Rule Set、Obligation 四类绑定：它们表达不同白名单、顺序和系统不变量。
- `feature_dependency`：需要独立有序边和无环检查。
- `risk_rule`、`rule_node`、`rule_operand`：分别表达规则顺序、AST 结构和类型化值，继续支持任意 AND/OR/NOT 嵌套。
- Action Parameter Definition、Rule Action、Rule Action Parameter：参数协议与具体规则绑定不能混为一张宽表。
- Release 与 Shadow Run：候选配置快照和一次测试批次可以一对多，不能合并。
- Evaluation 与三类输入快照：Checkout、Payment、Settlement 输入所有权和唯一键不同，并按 Decision Point Domain 精确选择一类；DD/Dispute 不建空壳行。
- Action Intent 与 Action Effect：合并前原始命中和冲突处理后的最终副作用必须可分别审计。
- Shadow 与真实灰度：MVP 继续只做旁路，不新增真实流量灰度或动态发布 Scope。

## 3. Frozen Physical Contracts

### 3.1 Version tables absorb catalog identity

`t_feature_version` 吸收 `feature_code`、`feature_name`、`business_category`，删除 `feature_id` 及对 `t_feature_definition` 的关系；唯一键改为 `(feature_code, version_no)`。继续保留 `result_value_type`、`handler_code`、`implementation_version`、`implementation_hash`、`timeout_ms` 和版本状态。Point 白名单、Dependency、Operand、Feature Result 和 Action 参数仍精确引用 `feature_version_id`。

`t_action_version` 吸收 `action_code`、`action_name`、`action_category`，删除 `action_id` 及对 `t_action_definition` 的关系；唯一键改为 `(action_code, version_no)`。继续保留 Handler、Terminal Decision、Merge Policy、Shadow 支持和版本状态。参数定义、规则绑定、系统义务、Intent 与 Effect 仍精确引用 `action_version_id`。

`t_rule_set_version` 吸收 `decision_point_id`、`rule_set_code`、`rule_set_name`，删除 `rule_set_id` 及对 `t_rule_set` 的关系；唯一键改为 `(decision_point_id, rule_set_code, version_no)`。继续保留 `DRAFT/FROZEN/RETIRED`、`content_hash`、冻结人和冻结时间。一个 Code 的新内容只能创建新 Version，FROZEN 版本不得原地改写。

上述三类表都不新增 `current_version_id` 或 Active Slot。运行时只使用 Release/Point Version 冻结的准确 Version ID；运营查询当前可选版本通过 Code、Status 和 Version No 解析。

### 3.2 Rule Operand becomes one typed multi-row table

`t_rule_operand` 目标字段至少包括：

```text
operand_id, node_id, operand_role, value_sequence,
source_type, feature_version_id, input_field_id, value_type,
string_value, number_value, boolean_value, datetime_value, duration_value,
create_time
```

唯一键为 `(node_id, operand_role, value_sequence)`。结构约束：

- 所有行都满足 `value_sequence > 0`。
- `FEATURE`：`value_sequence=1`，只允许 `feature_version_id` 非空，所有 Literal 值列为空。
- `CONTEXT`：`value_sequence=1`，只允许 `input_field_id` 非空，所有 Literal 值列为空。
- `LITERAL`：Feature/Input 引用为空，五个类型值列严格一个非空，非集合值 `value_sequence=1`。
- `IN/NOT_IN` 的 Literal 一侧通过连续 `1..n` 的 `value_sequence` 行表达；`BETWEEN` 的 Literal 一侧必须恰好为顺序 `[1,2]` 的两个值。
- 不新增 JSON、Expression、SQL、Script 或 EAV Value 表。

### 3.3 Release audit uses one typed append-only stream

新表 `t_release_audit_event` 使用 `event_type=STATIC_CHECK/APPROVAL/STATE_CHANGE`，所有行不可变追加。目标字段分为公共列和受控类型列：

```text
audit_event_id, release_id, event_type, event_time,
check_code, check_status, severity, error_code, error_summary,
shadow_run_id, approval_request_id, approval_status, reviewer_id, review_comment,
from_status, to_status, reason_code, trigger_type, trigger_id,
actor_id, aggregate_version, create_time
```

必须建立：

- `uk_release_audit_check(release_id, event_type, check_code)`；
- `uk_release_audit_state(release_id, event_type, aggregate_version)`；
- `uk_release_audit_approval(release_id, event_type, approval_request_id)`；
- Release 时间线访问索引 `(release_id, event_time)`；
- Shadow 审核访问索引 `(shadow_run_id, event_time)`。

CHECK 矩阵必须保证每种 Event 只填写自己的字段：STATIC_CHECK 保存检查结果；APPROVAL 必须绑定稳定 `approval_request_id`、Shadow Run、Reviewer 和批准/拒绝决定；STATE_CHANGE 保存 from/to、Trigger 和正数 Aggregate Version，首条 State Change 的 `from_status` 为空，后续必须非空。Activation 仍需在锁定 Release 的同一本地事务中追加状态事件、激活候选并将旧 Release 置为 SUPERSEDED。

跨行发布门禁必须由静态契约校验器和未来 Processor 共同执行：

- 进入 SHADOWING 前，所有强制 STATIC_CHECK 都存在且为 PASSED。
- APPROVAL 只能绑定同一 Candidate Release 的 `COMPLETED + PASSED` Shadow Run。
- 进入 ACTIVE 前，至少存在两个 `approval_status=APPROVED` 且 `reviewer_id` 不同的 Approval Event；同一审批命令重试复用 `approval_request_id`。
- State Change 的 Aggregate Version 连续递增，from/to 与上一行及允许的状态迁移图一致。
- Candidate ACTIVE、旧 Release SUPERSEDED、两个 Release Root/Active Slot 的 CAS 更新和两条 State Change 必须在同一本地事务原子提交。
- 第二名合格 Reviewer 的最终批准命令可以在同一事务完成 APPROVED → ACTIVE 和旧 Release SUPERSEDED，实现“审核通过直接发布”；不得在没有人工审核的情况下由定时任务自行发布。

### 3.4 Shadow summary belongs to the run; per-sample comparison belongs to evaluation

`t_release_shadow_run` 吸收 Summary 的质量字段：

```text
allow_to_review_count, allow_to_deny_count, deny_to_allow_count,
action_change_count, feature_missing_count, failed_count, timed_out_count,
p95_millis, p99_millis, check_status, version
```

- `version` 用于 Run 状态和计数更新的 CAS。
- Baseline Release 与 Candidate Release 必须不同，并属于同一 Decision Point/Environment。
- 计数不得为负，`sample_count <= sample_target`，`p99_millis >= p95_millis`。
- Summary 质量字段只在 `COMPLETED` 时全部非空；完成后 Run 不得再更新。
- `APPROVED` Release 只能引用 `COMPLETED + PASSED` 的 Candidate Shadow Run。

`t_decision_evaluation` 新增：

```text
shadow_run_id, baseline_evaluation_id,
decision_changed_flag, action_changed_flag,
comparison_severity, compared_time
```

- 仅 `evaluation_mode=SHADOW` 可以填写这些列，并且必须完整填写。
- PRIMARY/REPLAY 的六个比较列必须全部为空。
- 建立 `(shadow_run_id, baseline_evaluation_id)` 唯一键、自引用 Baseline Evaluation 关系和 Shadow Run 关系。
- 跨行静态校验保证 Baseline 是 PRIMARY、Candidate 是 SHADOW，二者 Decision Point、Evaluation Time 和 Input Snapshot Hash 相同，且各自 Release 与 Shadow Run 的 Baseline/Candidate Release 一致。

### 3.5 Rule and Predicate explanations use one typed trace

新表 `t_evaluation_rule_trace` 目标字段：

```text
trace_id, evaluation_id, trace_type,
rule_set_version_id, rule_id, node_id,
execution_sequence, result_value, terminal_flag,
elapsed_millis, failure_code,
rule_result_slot, predicate_result_slot, create_time
```

其中 `trace_type=RULE/PREDICATE`，两个 nullable Slot 固定为 `1` 或 `NULL`，用于在 MySQL NULL 唯一语义下保留精确业务唯一性：

- RULE：`rule_result_slot=1`、Predicate Slot/Node/Failure 为空；Rule Set、Rule、Result、Terminal、Elapsed 必填。
- PREDICATE：`predicate_result_slot=1`、Rule Slot/Rule Set/Terminal/Elapsed 为空；Rule、Node 必填，`result_value` 与 `failure_code` 严格二选一。
- 唯一键 `(evaluation_id, rule_id, rule_result_slot)` 保证每条 Rule 一条结果。
- 唯一键 `(evaluation_id, node_id, predicate_result_slot)` 保证每个 Predicate 一条结果。
- 唯一键 `(evaluation_id, trace_type, execution_sequence)` 保留 Rule 与 Predicate 各自的执行序号空间。
- 发布静态校验保证 Node 属于同一 Rule，Rule 属于冻结的 Rule Set Version。

### 3.6 Action Effect absorbs its one-to-one execution

`t_action_effect` 保留原 Effect 字段，将原 `status` 正名为 `effect_status`，并吸收：

```text
idempotency_key, execution_type, execution_status, attempt_count,
started_time, completed_time, last_error_code, last_error_summary
```

约束：

- `effect_mode=SIMULATED` 时，`effect_status=SIMULATED`，全部 Execution 字段为空。
- `effect_mode=REAL` 时，幂等键、执行类型、执行状态和次数必填；`idempotency_key=risk-action-effect:v1:<effect_hash>` 且全局唯一。
- `execution_status=INIT` 对应 `effect_status=CREATED`、`attempt_count=0`，Start/Completed/Error 全为空。
- `execution_status=PROCESSING` 对应 `effect_status=PENDING_ASYNC`、`attempt_count>=1`，Start 必填，Completed/Error 为空。
- `execution_status=ERROR` 表达可重试错误并对应 `effect_status=PENDING_ASYNC`、`attempt_count>=1`，Start/Error 必填而 Completed 为空。
- `execution_status=SUCCEEDED` 对应 `effect_status=APPLIED`、`attempt_count>=1`，Start/Completed 必填且 Error 为空。
- `execution_status=FAILED` 表达终局失败并对应 `effect_status=FAILED`、`attempt_count>=1`，Start/Completed/Error 必填。
- 本地状态 Action 与正式 Evaluation 仍同事务提交；异步 Action 仍通过同事务 `compensation_event` 可靠执行，不新增通用 Outbox/Execution Log 表。
- Risk Restriction、Risk List Entry 等既有 `source_effect_id` 关系保持不变。

`t_evaluation_action_intent` 的来源和合并契约同时冻结：RULE 只填 `rule_action_id`，OBLIGATION 只填 `obligation_id` 且引用 Action 型义务，两类来源均须归属于 Evaluation 的准确 Point Version；`RESERVE_LIMIT_GROUP` Obligation 不创建 Intent。只有 MERGED 可以填写 `merged_effect_id`。Effect Hash 使用 `RISK_ACTION_EFFECT_V1`，只覆盖数据库持久化的 Effect 身份与按数值 `(intent_sequence,intent_id)` 排序的 MERGED 贡献者身份，不覆盖 Handler/未持久化结果、执行状态、次数、时间或错误。

## 4. Frozen AST Capability

此次精简不降低 AST 支持范围：

| 能力 | 支持范围 |
|---|---|
| Group | `AND`、`OR`、`NOT`，任意树形嵌套 |
| Comparator | `EQ`、`NE`、`GT`、`GTE`、`LT`、`LTE`、`IN`、`NOT_IN`、`BETWEEN`、`IS_NULL`、`IS_NOT_NULL` |
| Value Type | `STRING`、`NUMBER`、`BOOLEAN`、`DATETIME`、`DURATION` |
| Operand Source | `FEATURE`、`CONTEXT`、`LITERAL` |
| Action Parameter Source | `FEATURE`、`CONTEXT`、`LITERAL` |
| Terminal Decision | `ALLOW`、`REVIEW`、`DENY` |

Release 静态校验必须执行以下跨行不变量：

1. 每条 Rule 恰有一个 Root，Node 无环，Parent/Child 属于同一 Rule。
2. AND/OR 至少两个 Child；NOT 恰好一个 Child；PREDICATE 没有 Child。
3. EQ/NE/GT/GTE/LT/LTE 恰好左右各一个单值 Operand。
4. IN/NOT_IN 是一个 Scalar Source 加至少一个同类型 Literal，Literal Sequence 必须连续为 `1..n`；BETWEEN 是一个 Scalar Source 加恰好两个有序同类型 Literal，Sequence 精确为 `[1,2]`。
5. IS_NULL/IS_NOT_NULL 只有一个 Scalar Operand。
6. 两侧类型必须精确一致，不做隐式字符串/数值/时间转换。
7. Feature/Context 必须在同一 Point Version 白名单内，Context 还必须允许 `rule_reference_flag=1`。
8. 一条 Rule 最多一个 Terminal Action；所有 Required Action 参数必须齐全、来源被允许且类型一致。
9. Feature Dependency 必须无环，Feature/Action Handler 必须已注册、已部署且 Implementation Hash 匹配。
10. System Obligation 由 `obligation_type` 区分：ACTION 必须绑定 Action Version，`RESERVE_LIMIT_GROUP` 不绑定 Action 并由 0..N 条 Decision Point Limit Binding 冻结准确 Limit Version；两类义务都属于同一 Point，只有 ACTION 进入 Intent。`PAYMENT_ATTEMPT_RISK + ALLOW` 必须恰有一个启用且 `FAIL_CLOSED` 的 `RESERVE_LIMIT_GROUP`，Local Partner Reserve Requirement、非法目标、跨 Point 或运营禁用/覆盖均 Fail-Closed。该 Limit 子句以 2026-08-28 方案为准。
11. Default Decision、Intent Source、Intent→Effect 和 Domain→Snapshot 由跨行契约执行；Snapshot 缺失、重复、错误类型、双类型或孤儿行均 Fail-Closed。

实现阶段增加一个只服务于设计资产与测试 Fixture 的 Node.js 静态契约校验器；它是 Java 运行时实现之前的可执行规格，不是生产规则执行器，也不授权 Java、Migration 或真实 Release 服务。

### Real-world acceptance example

`CHECKOUT_PREPARE` Point 的一条规则可表达：

```text
AND(
  EQ(FEATURE.IP_RISK_LEVEL, "HIGH"),
  OR(
    EQ(CONTEXT.client_ip_address, "203.0.113.9"),
    EQ(CONTEXT.vpn_flag, true)
  )
)
```

命中后依次产生 `COOLDOWN_SUBJECT`、`ADD_LIST_ENTRY` 和最多一个 `DENY_CHECKOUT` Terminal Action。Shadow Evaluation 只生成 SIMULATED Effect 并写比较字段；静态检查、样本质量门禁和两个不同 Reviewer 的批准全部通过后，Candidate 才能 ACTIVE。PRIMARY 命中后，REAL Effect 与 Evaluation 原子提交，随后分别由 `source_effect_id` 追溯到 Consumer Cooldown 和 IP List Entry。

## 5. Implementation Tasks

### Task 1: Freeze the independent RED contract

**Files:**

- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Create: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`

- [x] 在独立 Oracle 中把 Decision Engine 表名清单改为上述 19 个字面量，把 Evaluation 改为上述 8 个字面量，并将 12 个删除表加入禁止表清单。
- [x] 先冻结 75 总表数、19/8 图组表数、新表名、核心字段名、唯一键、关系和旧字段缺席；此时不要更新字段/索引/关系总数或任何 SHA-256。
- [x] 新增针对 Version 身份吸收、Operand 多行 Literal、Typed Audit Event、Shadow Comparison、Typed Trace 和 Effect Execution 的字面量模型断言。
- [x] 此阶段新 Contract Test 只检查生成模型结构，不 import 尚未创建的 AST Validator。
- [x] Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- [x] 确认 RED 只来自当前 37 表结构与新契约不一致；若出现 import、syntax 或无关模型失败，先修复测试本身再继续。

### Task 2: Collapse catalog roots and literal-value table

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/02-decision-engine.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify only if table-count assumptions require it: `scripts/pdmaner/risk/manifest.mjs`

- [x] 删除 Feature Definition、Action Definition、Rule Set Root 和 Rule Operand Value 四张表及全部关系/索引引用。
- [x] 按 3.1 扩展三张 Version 表，重建 Code + Version 唯一键，确保所有消费者继续只引用准确 Version ID。
- [x] 按 3.2 扩展 `rule_operand`，将 `value_sequence` 与五个类型化 Literal 列放在 Operand 行上。
- [x] 增加 Operand 来源/XOR、值类型/XOR和 Sequence 的 CHECK Policy；不要用 JSON 或通用 Value Payload 代替类型列。
- [x] 更新 Rule AST 关系测试，保证删除父表后没有悬空 `feature_id/action_id/rule_set_id/operand_value_id`。

### Task 3: Consolidate Release audit and Shadow evidence

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/02-decision-engine.mjs`
- Modify: `scripts/pdmaner/risk/manifests/03-evaluation.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`

- [x] 用 `release_audit_event` 替换 Static Check、Approval、State Change 三表，建立三类业务唯一键、稳定 Approval Request 幂等键、关系与 Row-Type CHECK。
- [x] 将 Summary 指标并入 `release_shadow_run`，增加 CAS Version、终态冻结和 Completed/Summary 字段矩阵。
- [x] 删除 `release_shadow_comparison`，把 Run/Baseline/Change/Severity/Compared Time 字段并入 Shadow `decision_evaluation`。
- [x] 增加 Evaluation Mode 比较字段矩阵、Shadow Run/Candidate 唯一键、自引用关系及对应 Oracle 断言。
- [x] 保留 DRAFT → VALIDATING → SHADOWING → REVIEW_PENDING → APPROVED → ACTIVE 生命周期以及旧 Release 同事务 SUPERSEDED 语义，并在 Contract Test 中冻结强制检查全通过、同 Run 两名不同 Reviewer、状态版本连续和原子激活配对。
- [x] 明确不新增真实流量灰度、动态 Scope、自动执行副作用或临时数据检查脚本。

### Task 4: Consolidate evaluation traces and Action execution

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/03-evaluation.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`

- [x] 用 `evaluation_rule_trace` 替换 Rule Result 和 Predicate Result，按 3.5 实现两个 nullable Slot、三组唯一键和 Row-Type CHECK。
- [x] 删除 `action_execution`，将执行字段并入 `action_effect`；将 Effect 状态和 Execution 状态命名分离，避免一个 `status` 同时表达两种生命周期。
- [x] 增加 REAL/SIMULATED 以及 INIT/PROCESSING/ERROR/SUCCEEDED/FAILED 的精确 Effect Status、Attempt、Start/Completed/Error 字段矩阵。
- [x] 用 `rule_action_id/obligation_id` XOR 替换 Intent 通用 `source_id`，增加义务外键、来源/Effect CHECK、双来源唯一键和访问索引。
- [x] 固定 Evaluation success/failure/adjustment 矩阵、Intent→Effect 守恒、`RISK_ACTION_EFFECT_V1` Hash/幂等键，以及 Point Domain→Snapshot 精确基数契约。
- [x] 删除旧 `action_execution.effect_id` 1:1 关系，同时验证所有 Risk Restriction/List/Limit/Dispute `source_effect_id` 关系仍指向 `action_effect`。
- [x] 保证 Shadow/Replay 只允许 SIMULATED Effect，不能生成真实幂等键、执行状态、Restriction、List Entry、Limit Reservation 或业务事件。

### Task 5: Add the executable AST/static-release contract

**Files:**

- Create: `scripts/pdmaner/risk/decision-engine-static-validator.mjs`
- Modify: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`

- [x] 先创建只含文件头、稳定 `validateDecisionRelease/validateReleaseTransition` API 且暂返回空错误集合的 Validator 骨架，避免测试 RED 退化成 module-not-found。
- [x] 再向 Contract Test 加入 AST/Release Fixture：合法深层 AND/OR/NOT、IN 多值、BETWEEN 两值、IS_NULL 单值，以及 Root 重复、Cycle、非连续 Sequence、错误 Arity、混合类型、越权 Context、缺失 Action 参数、双 Terminal、非法 System Obligation、同人重复审批和非法状态迁移的拒绝案例；运行测试，确认旧空实现因错误未被识别而 RED。
- [x] 实现纯函数校验器，输入规范化的 Point/Feature/Rule AST/Action/Release Fixture，输出稳定的 `check_code + severity + error_code`；不得执行 Handler、SQL、Script、HTTP 或副作用。
- [x] 实现第 4 节十类跨行不变量和 3.3 的发布准入/迁移不变量，并使错误定位至少包含 Release ID、Rule Code、Node ID 或 Action Code。
- [x] 固定配置静态检查编码：`REFERENCE_INTEGRITY`、`AST_SHAPE`、`AST_TYPES`、`FEATURE_DEPENDENCY`、`HANDLER_HASH`、`ACTION_PARAMETERS`、`TERMINAL_ACTION`、`SYSTEM_OBLIGATION`；固定状态门禁编码：`SHADOW_GATE`、`APPROVAL_GATE`、`STATE_TRANSITION`、`ACTIVATION_PAIR`。
- [x] 让测试同时覆盖合法的 Cooldown/IP 名单示例和每类失败 Fixture；断言所有失败均为确定性错误，不依赖当前时间或数据库。
- [x] 在文件头明确它是设计/模型可执行规格，不是生产 Java Evaluator。
- [x] Run: `node --test scripts/pdmaner/risk-decision-engine-contract.test.mjs`，确认 AST 和 Release Contract GREEN 后再进入生成阶段。

### Task 6: Generate the 75-table model and make the Oracle GREEN

**Files:**

- Modify: `scripts/pdmaner/risk/manifest.mjs`
- Modify only if generated shape/count logic requires it: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Generate only: `risk-compliance-service/docs/database/risk-compliance.pdma.json`

- [x] 将生产 Manifest 的精确总表约束从 85 改为 75，并生成模型；不得手工编辑 `.pdma.json`。
- [x] 读取 Generator 的实际字段、唯一索引、访问索引、关系、1:1、Diagram Group 和 CHECK 数量，逐项人工核对合并算术。
- [x] 用实际输出更新独立 Oracle 的精确统计、Template/Model/Structural/CHECK Policy SHA-256；禁止从生产 Manifest 动态推导预期值。
- [x] 确认图组严格为 Decision Engine 19、Evaluation 8，其他六组数量不变。
- [x] Run: `node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check`
- [x] Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs scripts/pdmaner/risk-decision-engine-contract.test.mjs`

### Task 7: Synchronize Risk logical, physical and real-world documentation

**Files:**

- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`

- [x] 先将 Scenario 文档测试的现行表清单和关键样例引用改成 75 表目标并运行，确认旧 85 表文档产生预期 RED。
- [x] 将 27 表 Decision Engine、10 表 Evaluation 的目录和关系图分别改为 19/8，逐表使用目标字段名。
- [x] 更新 Release 流程：Static Audit Events → Shadow Run/SHADOW Evaluation → Quality Gate → 两人 Approval Events → Atomic Activation。
- [x] 更新 SCN-RISK-02 的 Feature/Rule/Operand/Release 样例和 SCN-RISK-03 的 Trace/Effect 样例；保留 Cooldown 与 IP List 的现实链路。
- [x] 明确合表没有改变自由编排、Fail-Closed、First Terminal、Intent Merge、Shadow 无副作用和 `source_effect_id` 审计语义。
- [x] 更新物理统计、Diagram Groups、CHECK 数量、摘要 Hash 和删除表禁止项。
- [x] 让 Scenario 文档测试逐一覆盖全部 75 张当前表，并拒绝 12 张旧表作为现行结构出现。

### Task 8: Correct the requirements SQL/Script contradiction

**Files:**

- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Verify only unless a real semantic reference is found: `requirements-site/content/global-use-case-sequences.mjs`

- [x] 先添加 requirements 断言，要求 RCO-12/RCO-13 同时表达自由编排受控 AST、Shadow/Replay 无真实副作用和无 SQL/Script 执行边界；运行 `npm --prefix requirements-site run test:content`，确认当前冲突文案使测试 RED。
- [x] 将 RCO-12 从“代码规则及受控 SQL/Script Artifact”改为“工程注册 Feature/Action Handler + 类型安全 AST + 版本化 Release”，并保留静态校验、Shadow、数据门禁和双人审批。
- [x] 将 canonical `requirements.ts` 顶层约束中的“保留受控 SQL/Script Feature”改为明确禁止运营 SQL/Script，新增 Handler 仍需工程实现、测试和发布；不改写历史计划中的原始记录。
- [x] 用 `rg` 确认 Global Sequence 没有旧物理表名或 SQL/Script 语义；若没有，不改该共享序列文件。

### Task 9: Perform the coordinated shared-baseline merge

**Files:**

- Modify only after coordination and only where Risk baseline/authority is present: `AGENTS.md`
- Modify only after coordination and only where Risk baseline/authority is present: `README.md`
- Modify only after coordination and only where Risk baseline/authority is present: `agent-docs/README.md`
- Modify only after coordination and only where Risk baseline/authority is present: `agent-docs/PROJECT_CONTEXT.md`
- Modify only after coordination and only where Risk baseline/authority is present: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify only after coordination and only where Risk baseline/authority is present: `agent-docs/database/README.md`

- [x] 先检查其他 Codex task 是否正在写共享文件；等待其明确解冻后再基于主目录最新版本做逐段语义合并。
- [x] 将 Risk 总表数更新为 75，并使用 Task 6 生成的准确字段/索引/关系/1:1/CHECK 数量；新增本计划为当前 Risk authority。
- [x] 保留 Finance、Commerce、Payment、CCT 和 Platform 的最新数字与语义，不整文件覆盖，不把旧聊天里的统计写回。
- [x] `git diff` 逐文件检查，确认共享改动只涉及 Risk 段落和本计划链接。

### Task 10: Full regression, stale-reference audit and manual gate

- [x] Run: `node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check`
- [x] Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs scripts/pdmaner/risk-compliance-scenario-doc.test.mjs scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- [x] Run: `node --test scripts/pdmaner/*.test.mjs`
- [x] Run: `npm --prefix requirements-site test`
- [x] Run: `npm --prefix requirements-site run lint`
- [x] Run: `git diff --check`
- [x] 用 `rg` 扫描 12 张删除表、旧 `27/10/37/85/933` 现行统计、`feature_id/action_id/rule_set_id`、`rule_operand_value`、`action_execution` 和“受控 SQL/Script”；历史实施记录可以保留，但必须明确标注为历史阶段或被本计划取代。
- [x] 检查工作区状态和其他 task 的共享文件 diff；若发生重叠，先按语义比较再合并，绝不丢弃用户或其他 task 的改动。
- [ ] 在 PDManer 4.9.3 应用内人工检查 8 个 Diagram Group、19/8 表分组、关系线、中文名、字段顺序、索引与 Comment；自动测试不能替代该门禁。

## 6. Acceptance Criteria

- Risk 生成模型恰好 75 表，Decision Engine 19 表、Evaluation 8 表，其他六组表数不变。
- 12 张删除表不再存在于生产 Manifest、生成模型、现行逻辑/物理文档或场景表目录中。
- Feature、Action、Rule Set 的 Code + Version 身份可完整追溯；Release 仍冻结精确 Point/Feature/Rule Set/Action/Limit 版本组合。
- AST 的 Group、Comparator、Value Type、Operand Source、Action 参数来源和 Terminal Decision 支持范围不减少；IN/BETWEEN 多值语义有可执行 Fixture 证明。
- Release 仍具备静态检查、Shadow、质量门禁、双人审核、原子激活和状态历史；回滚必须创建一个复用历史批准 Version ID 组合的新 Candidate，并重新通过静态检查、Shadow 与双人审批，绝不重激活 SUPERSEDED Release。
- Shadow/Replay 不产生真实 Cooldown、IP 名单、限额、Settlement Restriction、MQ 或其他副作用。
- PRIMARY 命中后可通过 `rule_action/obligation → action_intent → action_effect → source_effect_id` 追溯 Cooldown 和 IP List Entry；真实执行具有由 Effect Hash 派生的唯一幂等键、重试状态和脱敏错误审计。
- Checkout/Payment/Settlement Evaluation 只能拥有与 Point Domain 对应的一类输入快照；DD/Dispute 不创建空壳 Assessment。
- Requirements 不再声称支持 SQL/Script Feature，且与 Risk 文档、场景和模型一致。
- Generator、Risk 定向测试、全量 PDManer 测试、requirements 测试、lint、`git diff --check` 和 PDManer 人工门禁全部通过。

## 7. Explicit Non-goals

- 不实现生产 Java Evaluator、Feature/Action Handler、Processor、Repository、Mapper、Migration 或 Testcontainers 测试。
- 不新增真实流量灰度、按 Client/国家的动态 Release Scope 或无人审核发布；最终合格 Reviewer 批准后的同事务直接激活不属于无人审核发布。
- 不引入 SQL、JavaScript、Groovy、SpEL、MVEL、JSON Logic、通用 EAV 或远程任意调用作为规则语言。
- 不把三类输入快照合成 JSON，不删除 Predicate 级解释能力，不合并 Action Intent 与 Effect。
- 不改变 Payment、Commerce、Finance、CCT 或 Platform 的业务所有权和接口边界。
- 不在未获授权前创建分支、提交、部署或真实基础设施。

# Risk Dispute Balanced Database Optimization Design

## 1. 目标

在不改变已确认 Dispute 业务流程的前提下，收紧 Risk & Compliance 的争议数据模型：

- 保留 Dispute 七张职责明确的表，不为减少表数合并外部结果事实与内部责任判断；
- 删除无独立消费者、可由权威父事实推导或重复保存的字段；
- 让 Finance 直接消费 Risk 明确给出的保证金使用许可，禁止按承担方或原因码推导；
- 补齐 Request、Outcome、更正链、当前指针和同案归属的数据完整性契约；
- 保持 Provider 交互属于 Payment、争议案件与责任分类属于 Risk、账户余额和 Journal 属于 Finance。

实施起点为生成模型的 71 表阶段；完成后生成器实算为 **71 表 / 865 字段 / 106 个唯一索引 / 74 个访问索引 / 111 条本库关系 / 9 个 1:1 / 8 个图组 / 83 条 CHECK**。Dispute 图组保持 7 表并由 88 字段精简为 84 字段。

## 2. 保留的七表边界

| 表 | 保留原因 |
|---|---|
| `t_dispute_case` | 聚合根、Provider Notice 幂等、工作队列、截止时间、当前状态和当前 Outcome 指针 |
| `t_dispute_state_change` | 追加保存 Case 状态、Actor、Trigger、原因和聚合版本 |
| `t_dispute_evidence_package` | 冻结一次证据集合的业务版本和 Content Hash |
| `t_dispute_evidence_ref` | 一个 Package 的多文件引用，保持文件级唯一性和可审计性 |
| `t_dispute_submission_request` | Risk 到 Payment 的提交命令及 `UNKNOWN`/重试生命周期 |
| `t_dispute_outcome` | Provider 或明确授权处置形成的不可变 `WON/LOST` 事实及更正链 |
| `t_dispute_liability_decision` | Risk 内部规则或已授权人工决定形成的责任、费用承担和保证金许可；与 Outcome 严格 1:1 |

`Outcome` 与 `Liability` 不合表。二者虽然同事务冻结，但来源和审计含义不同；使用 Shared Primary Key 表达 1:1，避免额外业务 ID 和冗余唯一键。

## 3. 字段精简

删除以下字段：

1. `t_dispute_liability_decision.liability_id`
   - `outcome_id` 改为主键及 1:1 外键。
2. `t_dispute_liability_decision.release_id`
   - 规则路径由 `decision_evaluation_id -> decision_evaluation.release_id` 精确取得；人工处置路径不伪造 Release。
3. `t_dispute_evidence_package.submission_deadline`
   - V1 只有 Case 级 `client_response_due_time`；没有 Package 独立延期用例。
4. `t_dispute_evidence_ref.document_version`
   - Platform 文件替换产生新 `file_id`；Risk 冻结 `file_id + content_hash`。
5. `t_dispute_submission_request.request_hash`
   - Package `content_hash` 冻结提交内容，命令重试使用稳定 Request ID 和 `t_command_idempotency`。

新增：

```text
t_dispute_liability_decision.security_deposit_use_allowed BOOLEAN NOT NULL
```

该字段为 Finance 的唯一 Risk 许可来源。它为 `1` 时必须同时满足：

- Outcome 为 `LOST`；
- `principal_bearer=CLIENT`；
- 责任决定已经由规则评估或明确授权处置冻结。

Dispute 图组由 88 字段精确收敛为 84 字段；Outcome 与 Liability 的表职责没有因字段精简而合并。

## 4. Outcome 来源与幂等

`decision_source_type` 固定为：

```text
PROVIDER_ACTION
CLIENT_DISPOSITION
OPERATIONS_DISPOSITION
OPERATIONS_CORRECTION
```

- `PROVIDER_ACTION`：仅形成 Version 1；`decision_source_id` 是 Payment Provider Dispute Action ID，`provider_dispute_action_id` 必须相等，可得到权威 `WON/LOST`。
- `CLIENT_DISPOSITION`：仅形成 Version 1 `LOST`，稳定来源是已授权 Client 处置命令；不得伪造 Provider Action。
- `OPERATIONS_DISPOSITION`：仅形成 Version 1 `LOST`，稳定来源是首次人工处置命令；不得伪造 Provider Action。
- `OPERATIONS_CORRECTION`：仅形成 Version N（N>1），稳定来源是更正命令并必须直接引用前序 Outcome；可额外引用促成更正的 Provider Action，但二者语义不合并。

幂等唯一键为：

```text
(decision_source_type, decision_source_id, source_version)
```

同一来源事实重放返回原 Outcome；同一来源身份的不同内容由命令或事件幂等契约拒绝。

## 5. Request 与更正链约束

Submission Request 行内矩阵：

```text
CREATED/SUBMITTED/UNKNOWN
  => active_slot=1 AND resolved_time IS NULL

SUCCEEDED/FAILED/CANCELLED
  => active_slot IS NULL AND resolved_time IS NOT NULL
```

`provider_dispute_action_id` 与 `action_source_version` 必须成对为空或非空；非空版本必须大于零。`SUBMITTED/UNKNOWN/SUCCEEDED` 必须持有准确 Action 坐标；`FAILED/CANCELLED` 明确允许“提交前无 Action”或“提交后有 Action”两种闭合分支。终态 `resolved_time` 不得早于 `requested_time`。

Outcome 更正链：

- Version 1 的 `supersedes_outcome_id` 必须为空；
- Version N 的前序必须是同 Case 的 Version N-1；
- 一个旧 Outcome 最多被一个新 Outcome 直接替代，禁止分叉、自指、跨 Case、跳版和回指；
- `current_outcome_id` 必须指向同 Case 的链尾；
- Outcome、Shared-PK Liability、Case Pointer/Version、State Change 和 `compensation_event` 在同一个 Case Root Lock 本地事务内原子落库。

每个 Liability 必须冻结完整的授权来源：规则路径同时保存 `decision_evaluation_id + action_effect_id`，且 Effect 必须属于该 Evaluation 并为 `REAL/APPLIED/LOCAL_SYNC/SUCCEEDED`；人工处置路径同时不保存二者并保存非空 `decided_by`。Client/Operations 首次处置与 Operations Correction 还必须有明确操作人。`security_deposit_use_allowed=1` 在此基础上再要求对应 Outcome 为 `LOST` 且 `principal_bearer=CLIENT`。

同案归属采用明确的复合外键契约，PDManer 图仍只绘制一条逻辑关系。为让未来 MySQL Migration 能建立这些复合外键，Package 与 Outcome 各保留一条精确父候选键，现有子表导航索引扩展为完整左前缀：

- Request `(package_id, dispute_case_id)` -> Package `(package_id, dispute_case_id)`；
- Case `(current_outcome_id, dispute_case_id)` -> Outcome `(outcome_id, dispute_case_id)`；
- Outcome `(supersedes_outcome_id, dispute_case_id)` -> Outcome `(outcome_id, dispute_case_id)`。

对应键固定为：

```text
uk_dispute_package_identity(package_id, dispute_case_id)
uk_dispute_outcome_identity(outcome_id, dispute_case_id)
uk_dispute_outcome_ptr(current_outcome_id, dispute_case_id)
idx_dispute_req_package(package_id, dispute_case_id)
uk_dispute_out_prev(supersedes_outcome_id, dispute_case_id)
```

连续版本、链尾和“恰好一条 Liability”不是普通 CHECK/FK 能完整表达的条件，由 Risk 聚合事务、跨行契约和未来 Testcontainers 用例共同证明。

## 6. 状态与证据 Hash

Case 状态保持已批准流程：

```text
NULL -> EVIDENCE_REQUIRED
EVIDENCE_REQUIRED -> UNDER_REVIEW | LOST
UNDER_REVIEW -> SUBMITTED | LOST
SUBMITTED -> WON | LOST
WON | LOST -> CLOSED
CLOSED -> CLOSED 仅追加可信 Outcome Correction
```

Case 枚举删除没有状态边和消费者的 `EVIDENCE_READY`；Package 自身的 `READY` 保留。首次 `WON/LOST` State Change 必须与 Version 1 Outcome 结果相同；Trigger Type/ID 必须逐值绑定 V1 来源：`PROVIDER_OUTCOME -> PROVIDER_ACTION`、`CLIENT_DISPOSITION -> CLIENT_DISPOSITION`、`OPERATIONS_DISPOSITION -> OPERATIONS_DISPOSITION`，其中 `trigger_id=decision_source_id`，Provider 路径还必须等于 `provider_dispute_action_id`。Deadline、Timer、未提交证据或 Client 沉默不得形成 `LOST`。每条 `CLOSED -> CLOSED` 只允许按 Outcome Version 顺序与一个新 `OPERATIONS_CORRECTION` 成对，且 `trigger_id=decision_source_id`；缺失、错配或交换更正命令坐标都拒绝。

Package `content_hash` 使用闭合 Canonical Schema，冻结：

- `dispute_case_id`；
- `version_no`；
- 按 `evidence_type + file_id` 排序后的 `evidence_type/file_id/content_hash` 集合。

Status、时间和数据库审计列不进入 Content Hash。替换文件创建新 `file_id`，修改证据集合创建新 Package Version。

## 7. 明确不做

- 不创建 Generic RFI、Workflow、Task、Disposition 或 Finance Account 表。
- 不把证据正文、Provider 原始 Payload、Finance 余额或 Journal 放入 Risk。
- 不删除 `current_outcome_id`、`supersedes_outcome_id`、Case 当前状态或 State Change。
- 不新增 Package Current Pointer/Active Slot。
- 不为尚未证明的 Deadline/Order 查询提前增加访问索引。
- 不修改 Finance、Payment、Platform Support 或其他系统的专属模型。

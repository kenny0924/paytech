# Dispute 物理数据库决策

> 2026-10-18 第 06 步自原 [Risk & Compliance 物理决策](../../../risk-decision/docs/database/RISK_DECISION_PHYSICAL_DECISIONS.md) 拆出：争议 7 表在阶段 0 只定 Schema 归属到 `mor_dispute`（D42），无代码、本步不建库。规范模型为 [dispute.pdma.json](./dispute.pdma.json)，Schema 为 [dispute-schema.sql](./sql/dispute-schema.sql)（逐字切片）。Payment 渠道争议 3 表同归 `mor_dispute`，仍在 payment 模型中，留待争议业务切片合并。

## 0. 按服务归属

- 争议（7）：`t_dispute_case`、`t_dispute_state_change`、`t_dispute_evidence_package`、`t_dispute_evidence_ref`、`t_dispute_submission_request`、`t_dispute_outcome`、`t_dispute_liability_decision`；每库一份 `t_compensation_event`。
- `t_dispute_liability_decision.decision_evaluation_id` 与 `action_effect_id` 指向 risk-decision，只存 ID、不建模关系；后续切片以同步评估合同获取（design/07 §3）。
- `CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1` 保持不变。

### 10.3 Dispute

开案冻结 Channel Notice/Version、Channel/MID、外部争议、Payment Attempt、Order、Client、金额币种和 Client/Channel Deadline；Client Deadline 必须早于 Channel Deadline。案件直接进入 EVIDENCE_REQUIRED，并通过既有 Package/Reference/Submission Request 完成按需 RFI。

Dispute 当前保持 7 张表且职责分离，但删除五个重复字段：Package 不复制 Case Deadline，Evidence Ref 不复制 Platform Document Version，Submission Request 不复制 Request Hash，Liability 不再拥有独立责任决定标识，也不复制可沿 Evaluation 取得的规则发布坐标。Liability 以 `outcome_id` 作为共享主键表达逻辑 1:1，并新增非空 `security_deposit_use_allowed`；不创建物理 FK。Outcome 与 Liability 仍不合表，因为外部结果事实和内部责任判断的来源与审计含义不同。

Payment 拥有 Channel Action，Risk Request 必须回绑准确 `channel_dispute_action_id + action_source_version`。`CREATED/SUBMITTED/UNKNOWN` 占用活动 Slot 且无收口时间，`SUCCEEDED/FAILED/CANCELLED` 释放 Slot 并写收口时间；`UNKNOWN` 不允许发起第二 Request。Request 与 Package、Case 当前 Outcome、Outcome 前序三条路径均由 Case Root 锁内的完整 Tuple 查询、唯一索引与 CAS 保证同案，不创建物理复合 FK。

Outcome 来源闭集为 `CHANNEL_ACTION/CLIENT_DISPOSITION/OPERATIONS_DISPOSITION/OPERATIONS_CORRECTION`，由 Type+ID+Version 唯一定位。Channel、Client、Operations 首次来源只允许 Version 1，其中后两者只产生明确授权的 LOST；Version N（N>1）只允许 Operations Correction，必须直接替代同 Case Version N-1，一个旧 Outcome 最多一个后继，Case 当前指针必须指向链尾。首次 WON/LOST State Change 必须等于 V1 Outcome，Trigger Type/ID 必须匹配其来源；每条 CLOSED 自环按 Outcome Version 顺序绑定一条 Operations Correction 且 Trigger ID 等于 `decision_source_id`。Outcome、Shared-PK Liability、Case 指针/Version、State Change 和 compensation_event 在同一 Case Root Lock 本地事务内提交。

Evidence Package `content_hash` 使用 `CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1`：固定 JSON 字段顺序，ID 使用正 BIGINT 十进制字符串，Version 使用正 INTEGER，Evidence 按 `evidence_type + file_id` 数值顺序排序，元素只含 Content Hash/Type/File ID，UTF-8 后计算小写 SHA-256。Status、Deadline、Package ID 和审计时间不参与；重复 Evidence Identity、开放字段或非规范数值直接拒绝。

`DisputeOpened` 只启动举证，不触发 Finance 记账或冻结，Client 沉默不得自动形成 Lost。直接 LOST 的 Trigger Type/ID 必须与 V1 来源严格匹配，Timer/Deadline/沉默均拒绝；CLOSED 自环只能与新 Operations Correction Outcome 逐版成对。每条 Liability 要么保存同一 Evaluation 下 `REAL/APPLIED/LOCAL_SYNC/SUCCEEDED` 的 Action Effect，要么让 Evaluation/Effect 同时为空并保存明确 `decided_by`；人工处置和更正必须属于后一类，禁止同时带规则与人工来源。Risk 拥有权威 Outcome 和 Liability 分类；只有 `DisputeLost` 触发 Finance Journal，且 Finance 只能在 `LOST + principal_bearer=CLIENT + security_deposit_use_allowed=1` 时使用 `CLIENT_SECURITY_DEPOSIT`，剩余缺口进入 `CLIENT_RECOVERY_RECEIVABLE`。Finance 不得从承担方或原因码推导许可。Outcome 更正只追加新版本，下游只追加补偿 Journal。


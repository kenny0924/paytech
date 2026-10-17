# Risk & Compliance 物理数据库决策

> **2026-09-14 Client 入驻修订：** 新 Client 使用 source_submission_id、可空旧版本引用、RETURNED 终态及任务内分类确认；其他 APPLICATION 来源使用批准的 legacy:applicationId 适配。本文旧 Client Submission/Relation Version 叙述仅适用于未迁移来源。完整合同见[数据设计](../../../requirements/20260914/client-console-注册与客户入驻/data-design.md)和[迁移方案](../../../requirements/20260914/client-console-注册与客户入驻/migration-plan.md)。当前模型、验证和执行状态见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

> **RCO-19 配置增量：** [数据方案](../../../requirements/20260912/operations-console-统一风控变量管理与条件编排/data.md)及[接口合同](../../../requirements/20260912/operations-console-统一风控变量管理与条件编排/api.md)拥有变量定义/成员、规则操作数和动作主体变量引用。第 5 节的 FEATURE/CONTEXT/LITERAL 来源规则保留为旧格式及运行能力合同；新配置使用 VARIABLE/LITERAL，共享值从当前变量库读取，历史内容和 Hash 不改写。模型/SQL 应用及验证状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco19-variables-20260912)。


> RCO-16 的两张字典主表与两个引用查询索引遵循[已确认数据合同](../../../requirements/20260911/operations-console-风控规则集历史与字典管理/data.md)。不增加回执表、标签主体关系或历史规则回填；数据库执行状态见当前台账。

> 2026-09-11 规则集整体管理增量以[已确认需求与方案](../../../requirements/20260911/operations-console-风控规则集列表优化/technique.md)第 10–18 节为准：新增 `t_rule_set` 稳定根；列表按根及当前配置分页，删除只终止整体配置管理并永久保留编码、版本、Candidate、审计与幂等回放。有任一版本被当前 ACTIVE Release 使用时禁止删除；冻结或 Candidate 引用本身不算使用中。此项取代旧设计无管理根、版本列表和无整体删除部分，保留其余业务事实。


> 2026-09-11：规则 V1 配置的可空终态、附加动作、主体绑定、手工拒绝文案及格式/执行门禁以[配置增量决定](../../../agent-docs/decisions/2026-09-11-risk-rule-v1-configuration.md)及其接口合同为准。本文其他执行语义保留；新增配置不表示已实现执行。

最后更新：2026-09-05

历史基线（2026-09-05）：Risk 侧 71 表已投影统一 15 列 Compensation Event、Vault V1 消费者边界、零物理 CHECK/FK 与规则版本审计字段；Settlement Eligibility、Dispute、Manual Payout 四检查点及 CCT Local Partner Application Typed Namespace 均已确定性生成；自动结构验证已通过，PDManer 4.9.3 应用内人工评审待完成

> **2026-09-05 横切规范更新：** Risk 生成模型已完成本库 `compensation_event`、Vault V1 消费者语义与零物理 CHECK/FK 投影。该结论不等于六库 COMP-01 或全局 DB-01 完成；跨库状态仍只由 [当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 按全局证据维护，DB-01 继续受 Vault 物理 Schema 阻塞。

## 1. 文档定位与阶段边界

本文记录 [Risk & Compliance 逻辑数据库模型](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md) 到 PDManer 4.9.3 物理模型的确定性映射。共享边界以 [数据库设计总览](../../../agent-docs/database/README.md)、[PDManer 数据库设计规范](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)、[可靠异步执行规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md) 和 [Vault 架构](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md) 为准。

目标模型位于 [risk-compliance.pdma.json](./risk-compliance.pdma.json)，只能由 [Risk Generator](../../../scripts/pdmaner/risk/generate-risk-compliance-model.mjs) 从共享模板、八组生产 Manifest 和显式物理 Policy 确定性生成，并由独立的 [模型契约测试](../../../scripts/pdmaner/risk-compliance-model.test.mjs) 校验。当前 Dispute 边界以 [7 表平衡优化设计](../../../agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-design.md) 和 [实施计划](../../../agent-docs/plans/2026-08-28-risk-dispute-balanced-optimization-implementation-plan.md) 为准，Settlement Control 三表边界以 [3 表平衡精简设计](../../../agent-docs/plans/2026-08-28-risk-settlement-eligibility-balanced-simplification-design.md) 为准，当前 Manual Payout 四检查点以 [对齐实施记录](../../../agent-docs/plans/2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md) 为准，Limit 边界以 [7 表平衡精简设计](../../../agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-design.md) 和 [实施计划](../../../agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md) 为准，Decision Engine/Evaluation 边界以 [27 表平衡精简计划](../../../agent-docs/plans/2026-08-27-risk-decision-engine-balanced-simplification-implementation-plan.md) 为准，DD 边界以 [19 表精简计划](../../../agent-docs/plans/2026-08-27-risk-dd-simplification-implementation-plan.md) 为准；不得直接修改生成 JSON 绕过 Manifest、Policy 或测试 Oracle。

本文只拥有逻辑模型到 PDManer/物理 SQL 的映射规则，不以自身证明 Java、物理数据库验证、共享环境或部署
状态；这些活态事实只见[当前实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。项目继续禁止 Flyway、
Liquibase 或应用启动时 Schema/初始化数据迁移；本地手工应用也不授权共享环境 DDL、真实流量灰度、生产
旁路任务或任何外部发布。

## 2. 已冻结物理基线（历史快照；现行统计由台账维护）

<!-- RISK_PHYSICAL_MANIFEST_START -->
```json
{
  "modelTotals": {
    "tables": 71,
    "fields": 865,
    "uniqueIndexes": 105,
    "accessIndexes": 75,
    "relations": 111,
    "oneToOneRelations": 9,
    "diagramGroups": 8
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "68b232ffa2f12423b00497db6682ffd71274f5b8b2d1f4c61d78e9a2db4ad3cf",
  "structuralProjectionSha256": "be58d091f5ffe87fce6c3fa67ad079845896475124ad7c866196e48a9d7992d9",
  "checkContracts": 82,
  "checkPolicySha256": "a1cc6af3ecf7dab474ee6a85f1f5c95caf1f08612834df9c128c19cef746ab11"
}
```
<!-- RISK_PHYSICAL_MANIFEST_END -->

八个 Diagram Group 与逻辑边界一一对应：

| 图组 | 主表数 | 职责 |
|---|---:|---|
| 尽调与资格 | 19 | Program、Case、内嵌关系快照的主体、检查、筛查、Qualification 和直接周期调度 |
| 决策点与规则发布 | 19 | Decision Point、版本内嵌目录身份的 Feature/Rule Set/Action、Rule AST、Release、Typed Audit 和 Shadow Run |
| 评估与 Action | 8 | Evaluation、Feature Result、统一 Rule/Predicate Trace、Action Intent/Effect 和三类输入快照 |
| 冷却、名单与风险数据 | 6 | Restriction、List、Payment Attempt Fact 和 Velocity 明细 |
| 限额与占用 | 7 | Limit Version、Point Obligation Binding、Position、Reservation Group、Reservation、State Change 和 Movement |
| 结算资格控制 | 3 | Settlement Control、1:N Scope 和只追加状态历史；最终最低保证金额直接属于 Control |
| 争议与举证 | 7 | Dispute Case、RFI Evidence、Submission Request、Outcome 和 Liability |
| 可靠执行 | 2 | Compensation Event 和 Command Idempotency |

逻辑表 `settlement_control_state_change` 加技术前缀后超过 32 字符，因此物理表名固定为 `t_settle_control_state_change`。这是唯一逻辑—物理表名缩写；模型的 `properties.logicalTableKey` 保留完整逻辑名用于生成和追溯。

## 3. 模板派生与确定性

- 完整克隆 `agent-docs/templates/pdmaner/template.pdma.json`，保留模板顶层、Profile、DDL 模板、Domain、Naming Rule 和 UI 元数据，只定点替换 Risk 模型内容。
- `dbConn`、`dbConns` 与 `profile.default.dbConn` 全部清空；模型不保存主机、端口、用户名、密码或环境凭据。
- 实体、字段、索引、索引字段、Correlation、Diagram 和 Diagram Cell ID 使用 `SHA-256(code-mor/risk-compliance/database/v1:path)` 派生的稳定 UUIDv5 形态值。
- 序列化固定为两空格缩进和一个末尾换行；`--check` 只做逐字节比较，不写文件。
- 生产 Manifest 与测试 Oracle 相互独立。测试不得从 Generator 或生产 Manifest 计算预期表清单、统计量或结构摘要。

## 4. 通用物理规则

| 语义 | 决策 |
|---|---|
| 数据库 | MySQL 8.4 LTS / InnoDB |
| 命名 | 表使用 `t_`；唯一索引使用 `uk_`；访问索引使用 `idx_`；物理名不超过 32 字符 |
| 主键 | 每表一个服务侧 Snowflake `BIGINT`，Java/MyBatis 使用 `Long`，禁止 `AUTO_INCREMENT` |
| 金额/比例 | `DECIMAL(24,8)` / `DECIMAL(18,10)`，Java/MyBatis 使用 `BigDecimal` |
| 时间 | UTC `DATETIME(6)`，Java/MyBatis 使用 `Instant`；有效期采用 `[effective_from,effective_to)` |
| 国家/币种 | ISO 3166-1 alpha-2 `CHAR(2)` / ISO 4217 alpha-3 `CHAR(3)` |
| Hash | SHA-256 小写十六进制 `CHAR(64)`；只在幂等、来源完整性、不可变内容或固定集合绑定有实际消费者时保存 |
| 枚举 | 稳定字符串业务码；闭合枚举在字段说明中列全，开放代码注明权威目录 |
| 审计时间 | 可变表使用 `create_time + update_time`；不可变结果、历史和快照仅使用 `create_time`；DD Evidence 是可审核投影，因此使用双审计时间和 CAS Version |
| 删除 | 不增加通用 `deleted`；使用明确业务终态。MVP 默认保留数据，不自动归档、匿名化或销毁 |

PDManer 的单字段和复合关系都只表达本库逻辑契约，不生成物理 FK；Owner 写入口必须在本地事务中锁定实际 Root、精确查询完整 Tuple，并以 CAS/唯一索引防并发错配。模型 `properties.compositeForeignKeys` 中三条 Dispute 同案契约表达：Request `(package_id,dispute_case_id)` 指向 Package 同名列组，Case 当前指针与 Outcome 前序指针分别以 `(outcome_id,dispute_case_id)` 证明同案。父表候选键和子表左前缀索引继续保留，不能降级为只校验 ID 存在。

业务 `version` 只保留在具有真实并发写入者的 Root 或 Current Projection。DD Dependency 需要并发接受子 Case 决定，Evidence 需要审核状态推进，Screening Run 需要异步完成，因此三者保留 `BIGINT` CAS Version；不可变 Snapshot、Decision、Result、History、Movement 和 Outcome 不机械增加版本。`t_compensation_event.lock_version` 只用于 Claim/Lease，并替代逻辑清单中含混的技术 `version`；`t_command_idempotency` 不使用机械 Version。

## 5. 自由编排规则模型

### 5.1 Event Point 与输入契约

`t_decision_point` 是事件点稳定身份，`t_decision_point_version` 冻结输入/输出契约、默认决定、`FIRST_TERMINAL` 执行策略、`FAIL_CLOSED` 失败策略和超时。Context Field 必须由工程通过 `t_decision_point_input_field` 注册；规则和 Action 不能通过任意字符串读取未注册字段。

一个 Point Version 可以绑定多个有序 Rule Set Version 和 Feature Version。Rule Set 只属于一个 Decision Point，不允许跨 Point 直接复用。Point Version 还必须冻结工程级类型化 Obligation：ACTION 必须绑定 Action Version 并进入 Action Intent；Payment Attempt ALLOW 后的 `RESERVE_LIMIT_GROUP` 不绑定 Action，而由 0..N 条 `t_decision_point_limit_binding` 冻结准确 Limit Version 与 Feature/Context 来源，直接形成 Group，不生成 Action Intent/Effect；四个 Manual Payout Point Version（`PAYOUT_CREATION`、`PAYOUT_FINAL_APPROVAL`、`PAYOUT_EXECUTION_CREATION`、`PAYOUT_EXTERNAL_SUBMISSION`）的 ALLOW 分别恰有一条 `CHECK_SETTLEMENT_ELIGIBILITY`，同样不绑定 Action、不得有 Limit Binding 或 Intent，并固定 `FAIL_CLOSED`。零 Limit Binding 仍产生空 Group。运营不能删除、禁用或覆盖这些系统义务。

### 5.2 Feature、Rule AST 与 Action

- `t_feature_version` 直接吸收 `feature_code/feature_name/business_category` 目录身份，并冻结 Handler Code、Implementation Version/Hash、返回类型和超时；`(feature_code,version_no)` 唯一，只允许工程注册 Handler。
- `t_rule_set_version` 直接吸收 `decision_point_id/rule_set_code/rule_set_name`，由 `(decision_point_id,rule_set_code,version_no)` 唯一定位；`created_by BIGINT NOT NULL` 与 `change_reason VARCHAR(500) NOT NULL` 在创建时冻结创建人及非敏感变更原因，不建立 Current Version 指针，也不跨 Decision Point 复用。
- Rule 使用 `risk_rule -> rule_node -> rule_operand` 的受控 AST。Group 只允许 AND/OR/NOT，Predicate Operator 只允许 EQ、NE、GT、GTE、LT、LTE、IN、NOT_IN、BETWEEN、IS_NULL、IS_NOT_NULL。
- Operand 来源严格为 FEATURE、CONTEXT、LITERAL 三选一。`t_rule_operand` 以 `(node_id,operand_role,value_sequence)` 多行保存 STRING、NUMBER、BOOLEAN、DATETIME、DURATION 类型值；IN/NOT_IN 要求连续 `1..n`，BETWEEN 固定 `[1,2]`，每行五个值列严格一个非空。禁止 JSON、EAV、SQL、Script、Class Name、URL 和运行时表达式。
- `t_action_version` 直接吸收 `action_code/action_name/action_category` 目录身份，并冻结 Handler、Implementation Version/Hash、Terminal Decision、Merge Policy 和 Shadow 支持能力；`(action_code,version_no)` 唯一。`t_action_parameter_definition` 与 `t_rule_action_parameter` 继续执行 FEATURE、CONTEXT、LITERAL 来源和值类型 XOR。
- 一条 Rule 最多一个 Terminal Action；第一次 Terminal Decision 停止后续规则。没有 Terminal 时使用 Point Version 的显式 Default Decision，技术失败绝不伪装成业务 DENY。
- 不恢复独立 Feature/Action/Rule Set 稳定根、Literal Value 子表、分表发布审计/Shadow 汇总/逐笔比较、分表 Rule/Predicate 结果或独立 Effect 执行明细；只有新的独立生命周期消费者才能重新拆表。

## 6. Release、Shadow 与审核

Risk V1 不建设真实流量灰度。每个 Decision Point、每个 Environment 同时最多一个 PRIMARY/ACTIVE `t_decision_release`，业务 Scope 差异继续由规则表达。

发布状态链固定为：

```text
DRAFT → VALIDATING → SHADOWING → REVIEW_PENDING → APPROVED → ACTIVE → SUPERSEDED
                         ↘ REJECTED
```

强制门禁如下：

1. Candidate 创建后冻结 Point Version、Rule Set Version、Feature Version、Action Version、Limit Version、Limit Binding 和 Content Hash，禁止原地改写。SYSTEM_OBLIGATION 静态检查必须装载被绑定 Limit Code 的完整版本闭包，校验 Version/Window/Threshold/Currency/Freeze 形状、同 Code 的 Subject/Metric 稳定性，并通过 `limit-version-canonicalizer.mjs` 按 `CODE-MOR:RISK:LIMIT_VERSION:1` 重算每个 `content_hash`；只有 FROZEN 准确版本可以绑定。
2. `t_release_audit_event` 以不可变 `STATIC_CHECK/APPROVAL/STATE_CHANGE` 行统一保存发布审计，每种 Event 的专属字段严格互斥并分别建立业务唯一键；进入 SHADOWING 前必须存在 REFERENCE_INTEGRITY、AST_SHAPE、AST_TYPES、FEATURE_DEPENDENCY、HANDLER_HASH、ACTION_PARAMETERS、TERMINAL_ACTION、SYSTEM_OBLIGATION 八项 `PASSED` 静态检查。
3. `t_release_shadow_run` 保存批次状态和完成时冻结的 Count、Failure、Timeout、P95/P99、决策迁移与 Action 差异汇总；逐样本差异保存在 SHADOW `t_decision_evaluation` 的 `shadow_run_id/baseline_evaluation_id/decision_changed_flag/action_changed_flag/comparison_severity/compared_time`。Candidate 复用 PRIMARY 输入快照和 `evaluation_time`；任务只传 Assessment/Snapshot ID，不通过 MQ 传 Token 或网络原值。
4. Shadow/Replay 只产生 SIMULATED Effect，执行字段全空；不得创建真实 Restriction、List Entry、Settlement Control、Limit Group/Reservation/Movement、Velocity Event、业务事实或 compensation_event。
5. Shadow Run 必须 `COMPLETED + PASSED` 才能进入 REVIEW_PENDING；两个不同 Reviewer 的 APPROVAL Event 必须绑定同一 Candidate 和同一 Run，同一审批重试复用 `approval_request_id`。
6. 第二名合格 Reviewer 的批准命令在同一本地事务中追加 Approval/State Audit，将 Candidate `APPROVED→ACTIVE`、占用 Active Slot，并把旧 Release `ACTIVE→SUPERSEDED`；不得由定时任务无人工审核自动发布。

## 7. Evaluation 与 Action 副作用

`t_decision_evaluation` 是所有决策点的统一执行事实。SUCCEEDED 必须同时有 `proposed_decision/business_decision`；二者相等时调整原因为空，不同时调整原因必填。FAILED/TIMED_OUT 的两个决定和调整原因全空、`default_decision_used_flag=0` 且 Failure Code 必填，不计为业务 DENY。使用默认决定时，跨行契约保证 proposed 等于准确 Point Version 的 `default_decision`。

Feature 结果写入专用结果表；Rule 与 Predicate 可解释结果统一追加到 `t_evaluation_rule_trace`，以 `trace_type=RULE/PREDICATE` 和两个 nullable Slot 保留每 Rule、每 Predicate 的精确唯一性。命中产生的 Intent 必须先全部收集和类型校验，再按 Action Version 的固定 Merge Policy 形成 Effect：

- `source_type=RULE` 只填 `rule_action_id`，Rule 所属 Rule Set 必须存在 Evaluation 准确 Point Version 的 Binding；`source_type=OBLIGATION` 只填 `obligation_id`，义务必须是绑定 Action Version 的 ACTION 且属于同一 Point Version。通用 `source_id` 已删除，RESERVE_LIMIT_GROUP 义务不进入本表。
- `MERGED` Intent 必须指向 Effect，COLLECTED/DISCARDED/FAILED 不得指向；每个 Effect 至少有一个 MERGED 贡献者，Intent 与 Effect 的 Evaluation、Action Version、解析目标逐值一致，Effect Merge Policy 等于 Action Version。
- `effect_hash` 使用 `RISK_ACTION_EFFECT_V1`：Canonical Object 顶层为 `contributors/effect/schema`；Effect 只含 `action_version_id/effect_mode/evaluation_id/merge_policy/target_key/target_type`，贡献者只含 `intent_id/intent_sequence/obligation_id/resolved_target_key/resolved_target_type/rule_action_id/source_type`，并按数值型 `(intent_sequence,intent_id)` 排序。BIGINT 使用无前导零十进制 String、nullable 来源使用 Null，按 RFC 8785 JCS + UTF-8 序列化后计算 SHA-256 小写十六进制；Effect ID、执行生命周期字段、Handler 返回值和其他未持久化结果全部排除，保证只读数据库即可复算。

- PRIMARY 使用 `effect_mode=REAL`；本地状态 Action、正式 Evaluation 和业务状态在同一本地事务提交。
- SHADOW/REPLAY 使用 `effect_mode=SIMULATED`、`effect_status=SIMULATED`，幂等键、执行类型、执行状态、次数、时间和错误字段全部为空。
- 只有 `technical_status=SUCCEEDED` 的 Evaluation 可以形成 Effect；FAILED/TIMED_OUT 不得形成 REAL 或 SIMULATED Effect。
- REAL Effect 在同一行保存执行矩阵，幂等键固定为 `risk-action-effect:v1:<effect_hash>` 并保持全局唯一；异步 REAL Effect 仍通过同事务 `t_compensation_event` 可靠执行。
- Cooldown、IP/Consumer 拉黑与 Risk List Entry 等状态必须保存 `source_effect_id`；Settlement Control 以唯一 `source_action_effect_id` 保存真实来源，一个 REAL Effect 最多创建一个 Control。精确释放使用授权操作命令，不伪造新的创建 Effect。

## 8. 输入快照与受限数据

`t_checkout_input_snapshot`（2026-10-16 按 [结账点运行期评估](../../../requirements/20261016/risk-结账点运行期评估/design.md) 重建为 17 列）冻结一次 `CHECKOUT_PREPARE` 正式评估使用的 `ORDER_PREPARATION v5` 输入：Commerce 预分配订单 ID（业务身份）、Client、经营主体编号与来源、Local Partner（仅 PARTNER 来源）、消费者国家、币种、商品/折扣/净额、行业、MCC、明细行数与稳定输入摘要；与通用 `t_decision_evaluation` 共同构成结账评估事实，不另建评估表。消费者、网络证据、协议与文件、Finance 税额及 Finance Entity ID 均不进入结账输入。

Checkout 在消费者交互前执行，因此 Consumer Reference、Email/Phone Stable Token 和网络证据允许为空；一旦存在则按受限字段保存。Risk 不保存 Consumer Profile Token，也不建立 Device Identifier。`t_transaction_risk_assessment` 精确冻结 Payment Attempt 的 Order Context、支付方式、金额币种、行业/MCC、Consumer Token 和 Network Evidence ID/Version；不得回查当前 Commerce 改写历史。

跨行 `CONDITIONAL_CARDINALITY` 契约以 `t_decision_point.business_domain` 为权威：CHECKOUT/PAYMENT/SETTLEMENT Evaluation 分别恰好一张 Checkout/Transaction/Settlement 输入行且另两类为零；DUE_DILIGENCE/DISPUTE 三类均为零，同时拒绝缺失、重复、错误类型、双类型和孤儿 Snapshot。

`t_velocity_event` 是滑动窗口的准确性来源。为了实现文档要求的 `(subject_type,subject_key,event_code,occurred_time)` 访问路径，`t_velocity_event_subject` 冻结父事件的 `event_code + occurred_time` 两个只读查询维度；写入事务必须逐值匹配父 Event。它仍是明确事件—主体关系，不是 EAV。`t_velocity_bucket` 不进入 V1。

2026-10-17（结账点实时累计）：`idx_velocity_subject` 末尾追加 `velocity_event_id`，使“最近一次金额 / 距上次秒数”的 `ORDER BY occurred_time DESC, velocity_event_id DESC LIMIT 1` 直接按索引倒序取首条；金额合计与最近一次查询以 `STRAIGHT_JOIN` 固定主体表为驱动表，避免热点主体宽窗口反转为事件主表全表扫描。Client、运营主体等低基数主体的统计方式与窗口由应用层限制（金额合计 ≤ 1 小时，次数与最近一次 ≤ 24 小时，不开放不同主体数），宽窗口预聚合不进入本期。

## 9. Limit 原子占用

硬 Limit 只支持固定 UTC Window；最近十分钟或最近二十四小时等滑动判断属于 Feature/Velocity。

PRIMARY 候选 ALLOW 时固定执行：

1. 读取准确 `RESERVE_LIMIT_GROUP` Obligation 的 0..N 条 `t_decision_point_limit_binding`，逐条解析冻结的 Limit Version、主体键和金额；
2. 非空时按 `limit_version_id + subject_key + currency_scope_key + window_start` 稳定顺序锁定全部 Position；
3. 校验 `committed + reserved + requested` 不超过金额/次数阈值；金额型 Binding 必须有 NUMBER 来源，COUNT 型不得有金额来源；
4. 正式 Evaluation、Assessment 与 Reservation Group 总是同事务提交；每条 Binding 的不可变 Reservation、RESERVE Movement 与 Position 也在该事务提交；没有批准的外部副作用时不机械登记 Compensation Event；
5. 任一 Limit 失败全部回滚，正式决定为 DENY/LIMIT_EXCEEDED；零 Binding 仍创建没有 Reservation 明细的空 Group；
6. Payment UNKNOWN 只把 Group 转为 PAYMENT_UNKNOWN 并继续占用；权威 Success 提交，安全失败/Channel 提交前 Cancel/权威 Void 释放；每次迁移追加 Group State Change，Refund、Dispute 和已 Capture Reversal 默认不恢复额度。

Position 与 Group 使用 CAS Version；Reservation 创建后不可变，Group 是唯一当前状态权威，State Change 与 Movement 只追加。Movement 经 Reservation 定位 Position，避免复制 `position_id`。Redis 只能作为非权威加速，不得替代 MySQL Position。

Group State Change 的 Sidecar 单行表达式固定 `NULL→RESERVED` 首事件及 `RESERVED→PAYMENT_UNKNOWN/COMMITTED/RELEASED`、`PAYMENT_UNKNOWN→COMMITTED/RELEASED` 后续边；COMMITTED/RELEASED 无出边。相邻版本连续性、前一行 `to_status` 与后一行 `from_status` 一致性由锁内 Processor 与仓库自有 `local-environment` 真实 MySQL 契约测试验证，不依赖物理 `CHECK`。

Movement 的 `amount_delta/count_delta` 固定表示本次 RESERVE、COMMIT 或 RELEASE 转移的非负绝对量；由 `movement_type` 决定 Position 中 reserved/committed 的增减方向，避免 COMMIT 同时减少预占并增加已提交时产生单一符号歧义。

## 10. Due Diligence、Settlement 与 Dispute

### 10.1 Due Diligence

Case 冻结 Program Version 和 Case Origin。只有 Application Case 保存来源 Application ID、Submission
Version/Hash；Continuous Review/Manual 固定 `RISK_COMPLIANCE`。Application Case 以
`source_namespace + source_application_id + role_type + role_id + qualification_type` 唯一定位；命中完整来源身份后比较
Hash，相同即幂等返回、不同即冲突。现有五列唯一索引保持不变，同一数值 Application/Party 由 Typed Namespace
隔离；本次没有新增表、字段、索引、关系或 CHECK 行。

| Namespace | 主 Role / Qualification | 允许的关联人 Role / Qualification |
|---|---|---|
| `CCT_CLIENT_APPLICATION` | `CLIENT + INDIVIDUAL_KYC/ORGANIZATION_KYB` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `CCT_OPERATING_ENTITY_APPLICATION` | `OPERATING_ENTITY + ENTITY_UNDERWRITING` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `CCT_LOCAL_PARTNER_APPLICATION` | `LOCAL_PARTNER + PARTNER_DUE_DILIGENCE` | `RELATED_PERSON + RELATED_PERSON_KYC` |
| `RISK_COMPLIANCE` | Continuous Review / Manual 自有来源 | 按既有 Role/Qualification 矩阵 |

`ck_dd_case_origin` 只新增
`(source_namespace='CCT_LOCAL_PARTNER_APPLICATION' AND role_type IN ('LOCAL_PARTNER','RELATED_PERSON'))`，因此该
Namespace 拒绝 `CLIENT` 和 `OPERATING_ENTITY`；`ck_dd_case_tuple` 保持不变。CCT 只接收 Local Partner 主 Case/Decision，Risk
管理关联人 KYC 子 Case 及其依赖。Continuous Review/Manual 的三个 Submission 字段必须为空，禁止制造虚假来源坐标。Program
Version 仅 ACTIVE 占用 `active_slot=1`，DRAFT 不得有批准信息，ACTIVE/RETIRED 必须保留批准人和时间。Case 仅
DRAFT/SUBMITTED/IN_REVIEW/WAITING_EVIDENCE 占 Slot，终态置空；唯一键按 Role + Qualification 防止同一资格并发建案。Case Type
可由 Qualification 完全推导，因此不重复存列；同一行 CHECK 固定 Namespace/Role 矩阵以及
CLIENT+INDIVIDUAL_KYC/ORGANIZATION_KYB、LOCAL_PARTNER+PARTNER_DUE_DILIGENCE、OPERATING_ENTITY+ENTITY_UNDERWRITING、RELATED_PERSON+RELATED_PERSON_KYC，并与
CCT Application Tuple 精确对齐。

Case Subject 冻结 Party Profile ID/Version No，以及 UBO、Director、Signatory 的 CCT Party Relation Version ID/Version No；持股单位与 CCT 一致为百分比 `(0,100]`。每个 Case 通过 nullable `primary_slot` 唯一键最多保留一个 PRIMARY，关联主体通过准确 Relation Version ID 防重；不再建立独立 Relation Snapshot 表。Organization KYB 必须显式依赖全部 Required UBO、Director、Signatory KYC Case/Decision。Dependency 是可变 CAS 行：`accepted_decision_id` 只能从 NULL 单向写为属于 Child Case 的有效 Decision，父 Case 终结后冻结；完整性和无环由 Processor 验证。

创建 Case 时必须验证 Program、Case 的 Qualification Type 完全一致。Requirement、Check、Screening Run 引用的 Template 必须属于该 Case 冻结的同一 Program Version，Screening Template 的 Subject Role 还必须等于 Case Subject Role；所有 Required Template 必须完整实例化。Parent Organization KYB 只收组织级材料，UBO/Director/Signatory 的个人材料分别落在各自 Child KYC Case 的 PRIMARY Subject Requirement 中。

Requirement 通过 `program_requirement_id` 精确回链模板，不复制 Requirement Code/Type；Evidence 通过本库 `case_subject_id` 指向案件主体，不保存外部 Party ID、Document Type 或 Document Version。Evidence 创建后只有 `status/version/update_time` 可变，Requirement、Case Subject、File ID 和 Content Hash 全部冻结。替换文件时锁定 Requirement 与现有 Evidence，在同一事务将旧 RECEIVED/ACCEPTED 行 CAS 为 SUPERSEDED、插入新 RECEIVED Evidence，并把 Requirement 推进或回退为 RECEIVED；只有新 Evidence 与 Requirement 同事务转为 ACCEPTED 后才能再次满足材料要求。

Check Type 和 Screening Type 分别从精确 Program Template 推导。Channel 成功响应时，先按 `(screening_run_id, external_hit_ref)` 去重写入完整 Hit 集合，再在同一事务以 Version CAS 把 Run 从 STARTED 推进为 SUCCEEDED；FAILED/TIMED_OUT 不得写 Hit，避免部分批次。Screening Hit 只保留 OPEN/RESOLVED 队列状态；最新 Resolution 为 INSUFFICIENT_EVIDENCE 时 Hit 保持或经明确 Reviewer Correction 重新变为 OPEN，为 CONFIRMED_MATCH/FALSE_POSITIVE 时同事务 CAS 为 RESOLVED，具体结论始终读取最大 Resolution Version。`resolved_time` 表示这一版人工 Review 完成时点，不表示 Hit 必然关闭。

Program `content_hash` 使用 `CODE-MOR:RISK:DD_PROGRAM_VERSION:1` Canonical Object：`programId/versionNo/reviewFrequencyCode`，以及按 `requirementCode` 排序的 Requirements、按 `checkCode` 排序的 Checks、按 `subjectRole + screeningType` 排序的 Screenings。数组元素分别只含 `requirementCode/documentType/required`、`checkCode/checkType/required/validityDays`、`subjectRole/screeningType/required`。字符串按 Unicode 原值编码，Boolean 使用 JSON true/false，所有 ID/Version/Day 使用无前导零十进制字符串，可空值显式写 JSON null；对象采用固定字段顺序后按 RFC 8785 JCS 生成 UTF-8 字节并计算 SHA-256。Status、Slot、批准信息和审计时间不进入 Hash。激活前必须重算校验，Case 只引用校验通过的准确 Program Version。Check Template 的 `validity_days` 是 Channel 结果最长有效期；非空时 `valid_until` 取 Channel 有效期与 `performed_time + validity_days` 的较早值，空值只保留 Channel 明确返回的有效期。

Snapshot 在 Protect 前按 `RISK_DD_PARTY_SNAPSHOT_V1` 与 `schema_version` 校验字段白名单、排序和大小，`source_content_hash` 承诺该规范化来源 Payload，`captured_time` 表示来源采集时点而非本库插入时点。三个 Risk-owned Recoverable Token 为 `snapshot_vault_token`、`result_vault_token`、`name_vault_token`，分别只允许已批准的 `RISK_DD_PARTY_SNAPSHOT_V1`、`RISK_DD_CHECK_RESULT_V1`、`RISK_SCREENING_HIT_NAME_V1` Data Type，并由当前 Vault V1 Protect/Reveal 处理；`snapshot_vault_token` 有案件复核/审计 Reveal 消费者，保持 NOT NULL，另外两个按是否保存可恢复结果维持可空。四个 Commerce Stable Correlation Token 是 Checkout/Transaction 输入快照各自的 Email/Phone Token，Risk 不 Protect、不拥有原值，也不因保存它们取得 Reveal 权限。七个 Token 列都保留 `VARCHAR(128)` 协议缓冲，不按当前 43 字符 Wire 收紧；规则配置不调用 Vault，也不保存 Vault 业务 Resource 参数。

自动 Evaluation 只形成 recommendation。`t_qualification_decision` 是正式不可变决定版本，覆盖必须记录原因；Case 内最大 `decision_version` 是局部当前决定。Screening Hit 内最大 `resolution_version` 是当前处置，不保留 Current/Supersedes 双指针。`t_role_qualification.current_decision_id` 必须保留，因为它是跨 Case 的当前生效资格投影。

Program Version 保存可选 `review_frequency_code`；Role Qualification 保存频率、`next_review_time` 和 `review_status`。到期任务以 Qualification Version + Next Review Time 做 CAS，在同一本地事务直接创建 `case_origin=CONTINUOUS_REVIEW` 且带 `scheduled_review_time` 的新 Case 并推进下次时间；Role + Qualification + Scheduled Time 唯一键做最终防重。不建立 Continuous Review Plan/Run 表。

### 10.2 Settlement Eligibility Control 与 RCO-I09

Risk 侧当前实现恰好三张控制表：`t_settlement_control` 是 Client/Local Partner 长期控制根，`t_settlement_control_scope` 保存 OE/币种/`bank_country` 的 1:N OR 作用域，`t_settle_control_state_change` 追加完整状态历史。`MIN_SECURITY_DEPOSIT` 的最终最低金额、币种和释放条件直接属于根，没有独立 Requirement 表；它只允许 Client 且不表示 Finance Balance、资金预占或余额迁移。`SETTLEMENT_BLOCK` 可用于 Client/Local Partner，金额三列必须为空。Local Partner 配置或运行出现金额门槛都 Fail-Closed。

Control 只允许 `NULL→ACTIVE→RELEASED|EXPIRED`；RELEASED/EXPIRED 是不可复活终态。根只保存唯一 `source_action_effect_id`，沿 `Action Effect→Evaluation→Release` 追溯来源；REAL、PRIMARY、SUCCEEDED、LOCAL_SYNC 的已登记 `CREATE_SETTLEMENT_CONTROL` Handler 才能创建，一个 Effect 最多一个 Control，SHADOW/REPLAY 禁止创建。创建必须同时写 1..N Scope、首条状态历史和 `SettlementControlChanged`；释放必须携带准确 `control_id + expected_version + 幂等键`，不能清除同 Role 其他活动 Control。Timer Trigger 固定为 `settlement-control-expiry:v1:<control_id>:<expected_version>:<effective_to>`，重复执行返回原终态而不复活。

PRIMARY Eligibility 在 MySQL InnoDB `REPEATABLE READ` 下通过 `(role_type,role_id,active_slot,control_id)` 锁定 Role Range；零行也取得 Next-Key/Gap Lock。创建、检查、精确释放和 Timer 使用唯一锁序 `Role Range -> control_id 升序 Root -> Scope/Event`，且只有取得所需锁后才采样数据库 UTC。每个命中 Control 都冻结完整 Scope 集；任一 Block 返回 RESTRICT，同币种多个最低保证金取 MAX 而不相加。

Payout Creation、Payout Final Approval、Payout Execution Creation 和每次 Payout External Submission 都创建新的 PRIMARY Evaluation 与 `t_settlement_risk_assessment`；同一幂等命令重放复用原 `assessment_id + evaluation_id`，不同检查点及每个新 Retry Execution 必须使用新坐标。Assessment 用结构化字段证明 `checkpoint_type`、`role_type/role_id`、`operating_entity_id`、`gross_amount/currency`、`bank_country`、`source_fact_type/id/version` 和 `input_hash`，并必须满足 `Assessment.checkpoint_type = Decision Point.point_code` 与 `Assessment.input_hash = Evaluation.input_snapshot_hash`。`bank_country` 只来自 Finance 冻结的单表 `beneficiary_account.bank_country`；契约中没有 `beneficiary_account_ver`。`source_fact_version` 限定为 MySQL signed INT 的 `1..2147483647`：创建类检查点固定为 1，审批/提交类检查点冻结命令开始时的变更前 expected/current Version。`PAYOUT_CREATION/PAYOUT_FINAL_APPROVAL` 只接受 `PAYOUT`，`PAYOUT_EXECUTION_CREATION/PAYOUT_EXTERNAL_SUBMISSION` 只接受 `PAYOUT_EXECUTION`。

Assessment 的 Eligibility 结果与 Evaluation 只允许六组 `(eligibility_decision, technical_status, proposed_decision, business_decision)` 映射：`(ALLOW, SUCCEEDED, ALLOW, ALLOW)`、`(RESTRICT, SUCCEEDED, ALLOW, DENY)`、`(NULL, SUCCEEDED, REVIEW, REVIEW)`、`(NULL, SUCCEEDED, DENY, DENY)`、`(NULL, FAILED, NULL, NULL)` 和 `(NULL, TIMED_OUT, NULL, NULL)`。这是跨行静态契约，不允许 Assessment 与 Evaluation 产生相互矛盾的双重结果；元数据校验本身也拒绝空 Assertion/Selector/Mapping、重复 Mapping Key、未知 Alias/Field/Operator 和不闭合 Mapping，防止真空通过。

`gross_amount=owner_debit_amount=cash_required_amount`，不得使用 requested 或 beneficiary 金额。`source_fact_version` 是评估命令开始时锁定的变更前 expected version：Payout/Execution 创建使用预分配 ID 与版本 1，Final Approval/External Submission 使用状态迁移前的当前版本，不得引用未来版本。Risk 成功而 Finance 本地事务失败时，该 Assessment/Evaluation 仍有效；同一命令重放复用原 pair。新 Retry Execution 只有在前一 Execution 权威 FAILED 后创建，并重新走后两个检查点；UNKNOWN 始终阻断。

`RCO-I09` 返回不可变且可检索的 `assessment_id + evaluation_id`、`decision`、排序后的 `reason_codes[]`、完整 `matched_controls[]`、`required_amount/required_currency` 和 `control_set_hash`。Risk 不建立额外结果聚合或完整响应 Hash；Finance 必须冻结完整响应并按 Finance 本地 Canonical Contract 计算自己的 `eligibility_hash`，但不能用该 Hash 跳过后续检查点。四个检查点在各自业务副作用前都必须独立执行 Finance 本地账户门禁：Client 必须满足 `CLIENT_RECOVERY_RECEIVABLE/NONE = 0` 且 `CLIENT_SECURITY_DEPOSIT/NONE >= 本次 required_amount`，Local Partner 必须满足 `PARTNER_RECOVERY_RECEIVABLE/NONE = 0` 且 `required_amount/required_currency` 均为 NULL。非 ALLOW、Scope/1:1/Hash 不一致、账户门禁不通过或技术失败一律 Fail-Closed，不得复用早先检查点。Execution Creation 通过后 Finance 才创建 CREATED Execution 并锁定准确 BANK_CASH；Local Partner 不得查询或伪造两类 Client Account。Payout 只清偿 Client/Local Partner 已形成的应付余额；Client-to-Beneficiary Disbursement 是独立能力，不得复用这些 Point、输入或 Control。

Risk 三表、Assessment、Policy 和静态校验已实现；Manual Payout 四检查点及 Finance Projection 的 Scope/完整结果/账户门禁契约已于 2026-08-29 同 Finance、requirements 和共享入口对齐。

### 10.3 Dispute

开案冻结 Channel Notice/Version、Channel/MID、外部争议、Payment Attempt、Order、Client、金额币种和 Client/Channel Deadline；Client Deadline 必须早于 Channel Deadline。案件直接进入 EVIDENCE_REQUIRED，并通过既有 Package/Reference/Submission Request 完成按需 RFI。

Dispute 当前保持 7 张表且职责分离，但删除五个重复字段：Package 不复制 Case Deadline，Evidence Ref 不复制 Platform Document Version，Submission Request 不复制 Request Hash，Liability 不再拥有独立责任决定标识，也不复制可沿 Evaluation 取得的规则发布坐标。Liability 以 `outcome_id` 作为共享主键表达逻辑 1:1，并新增非空 `security_deposit_use_allowed`；不创建物理 FK。Outcome 与 Liability 仍不合表，因为外部结果事实和内部责任判断的来源与审计含义不同。

Payment 拥有 Channel Action，Risk Request 必须回绑准确 `channel_dispute_action_id + action_source_version`。`CREATED/SUBMITTED/UNKNOWN` 占用活动 Slot 且无收口时间，`SUCCEEDED/FAILED/CANCELLED` 释放 Slot 并写收口时间；`UNKNOWN` 不允许发起第二 Request。Request 与 Package、Case 当前 Outcome、Outcome 前序三条路径均由 Case Root 锁内的完整 Tuple 查询、唯一索引与 CAS 保证同案，不创建物理复合 FK。

Outcome 来源闭集为 `CHANNEL_ACTION/CLIENT_DISPOSITION/OPERATIONS_DISPOSITION/OPERATIONS_CORRECTION`，由 Type+ID+Version 唯一定位。Channel、Client、Operations 首次来源只允许 Version 1，其中后两者只产生明确授权的 LOST；Version N（N>1）只允许 Operations Correction，必须直接替代同 Case Version N-1，一个旧 Outcome 最多一个后继，Case 当前指针必须指向链尾。首次 WON/LOST State Change 必须等于 V1 Outcome，Trigger Type/ID 必须匹配其来源；每条 CLOSED 自环按 Outcome Version 顺序绑定一条 Operations Correction 且 Trigger ID 等于 `decision_source_id`。Outcome、Shared-PK Liability、Case 指针/Version、State Change 和 compensation_event 在同一 Case Root Lock 本地事务内提交。

Evidence Package `content_hash` 使用 `CODE-MOR:RISK:DISPUTE_EVIDENCE_PACKAGE:1`：固定 JSON 字段顺序，ID 使用正 BIGINT 十进制字符串，Version 使用正 INTEGER，Evidence 按 `evidence_type + file_id` 数值顺序排序，元素只含 Content Hash/Type/File ID，UTF-8 后计算小写 SHA-256。Status、Deadline、Package ID 和审计时间不参与；重复 Evidence Identity、开放字段或非规范数值直接拒绝。

`DisputeOpened` 只启动举证，不触发 Finance 记账或冻结，Client 沉默不得自动形成 Lost。直接 LOST 的 Trigger Type/ID 必须与 V1 来源严格匹配，Timer/Deadline/沉默均拒绝；CLOSED 自环只能与新 Operations Correction Outcome 逐版成对。每条 Liability 要么保存同一 Evaluation 下 `REAL/APPLIED/LOCAL_SYNC/SUCCEEDED` 的 Action Effect，要么让 Evaluation/Effect 同时为空并保存明确 `decided_by`；人工处置和更正必须属于后一类，禁止同时带规则与人工来源。Risk 拥有权威 Outcome 和 Liability 分类；只有 `DisputeLost` 触发 Finance Journal，且 Finance 只能在 `LOST + principal_bearer=CLIENT + security_deposit_use_allowed=1` 时使用 `CLIENT_SECURITY_DEPOSIT`，剩余缺口进入 `CLIENT_RECOVERY_RECEIVABLE`。Finance 不得从承担方或原因码推导许可。Outcome 更正只追加新版本，下游只追加补偿 Journal。

## 11. CHECK Sidecar 逻辑约束与跨行事务边界

PDManer 4.9.3 对所有数据库目标没有统一可移植约束节点。名称、逻辑表达式和绑定表冻结在 `scripts/pdmaner/risk/model-policy.mjs`，当前共 82 条 Sidecar 契约，供模型、Owner 应用校验和测试追踪，不生成物理 `CHECK`，覆盖：

- Version、Active Slot、状态—时间和有效区间；
- DD Program 批准/版本/检查有效期，Role/Qualification、Origin、状态时间和 Slot 矩阵，Subject Profile/Relation 版本与百分比持股，Dependency/Evidence/Run CAS，Screening 时间/分数，状态历史首行/聚合版本，决定来源和复审状态；
- File ID/Content Hash 成对引用、Screening 证据文件对和明确的 Vault Token 引用；
- Rule Node Shape、Operand 来源、类型化 Literal/Action 参数 XOR；
- Terminal Action、Obligation Target、Typed Release Audit、Shadow Run 状态/汇总和审批字段矩阵；
- Evaluation 技术/业务/调整状态、SHADOW 比较字段矩阵、Feature 类型化结果、Rule/Predicate Trace、Intent 来源/Effect 引用 XOR、Effect Mode 与内嵌 Execution 状态；
- Restriction、Velocity 金额币种、Limit 阈值/窗口/非负计数与 Movement；
- Settlement Control 类型/金额、状态/Slot、有效区间、Instance Key，State Change 状态/Trigger，以及 Settlement Assessment 输入/结果矩阵；
- Dispute Deadline、Case Pointer、State Change、Package Version、Request 正序号/Slot/Time/Action、Outcome Version/Source 以及 Liability 推荐/授权来源/保证金许可矩阵；
- Compensation Claim/Lease；次数非负和正锁版本由写入口、CAS、安全默认值与真实 MySQL 反例测试保证，不建立 Counter CHECK。

Risk 物理 DDL 目标明确为零 CHECK、零 FOREIGN KEY。PDManer Correlation 和 `properties.compositeForeignKeys` 继续作为逻辑关系 Sidecar，由 Owner 写入口在真实本地事务中锁定 Root、逐值校验 Tuple，并配合 CAS、唯一索引与测试执行。此处不宣称全局 DB-01 完成；Vault 物理 Schema 仍是全局阻塞项。

以下条件不能由单行表达式完整证明，未来实现必须由 Processor 持 Root/Position 锁、Repository 条件更新和仓库自有 `local-environment` 真实 MySQL 并发反例测试共同证明：

- Rule AST 和 Feature Dependency 无环，NOT 恰有一个子节点，Operator 与 Operand 数量/类型兼容；
- Rule、Rule Set、Feature 和 Action 都属于 Release 冻结的同一 Point Version 白名单；
- 一条 Rule 最多一个 Terminal Action，Candidate Release 组成与 Content Hash 一致；
- 八项 Static Check、Shadow 质量门禁、同 Run 双 Reviewer 审批、状态事件连续性和 Candidate/旧 ACTIVE 原子切换；
- Shadow 复用 PRIMARY 输入和时间锚，逐样本比较引用正确，不产生任何 REAL Effect 或业务写入；
- 默认决定与 Point Version 一致；RULE/OBLIGATION Intent 来源引用正确；Intent/Effect 的 Evaluation、Action、Target、Merge Policy 一致且 Effect 有贡献者；
- Decision Point Domain 与 Checkout/Payment/Settlement 三类输入快照精确一类，DD/Dispute 不建空壳快照；
- `risk_list_entry.active_slot` 仅由 ACTIVE 行占用，历史行置空；替代链和生效区间必须在同一名单与主体内连续；
- `velocity_event_subject.event_code/occurred_time` 等于父 Event；
- Limit 多 Position 稳定锁序、阈值守恒、Group/Reservation/Movement/Position 原子提交和乱序事件不回退；
- Program/Case Qualification、三类实例 Template/Program Version、Screening Template/Subject Role 必须逐值一致，全部 Required Template 必须实例化；
- Organization Case Dependency 完整且无环，Qualification Current Decision 属于同一 Role/Type；RELATED_PERSON role_id 是 CCT Party ID，Dependency Child Case、Parent Case Subject Party 与 Decision 必须一致；
- 每个 DD Case 必须恰有一个 PRIMARY，关联 Party 不得等于该 PRIMARY；Dependency 接受的 Decision 必须属于 Child Case；Evidence 的 Requirement 与 Case Subject 必须属于同一 Case，替换与 Requirement 状态重置必须原子完成；
- Screening 成功 Hit 全批写入与 Run CAS、Hit Resolution 与 OPEN/RESOLVED 投影、周期调度 CAS、Case 创建和 Next Review 推进必须分别原子完成；
- Settlement Control 的唯一 Effect 来源、首条状态/Scope 完整性、连续版本、每版本事件、Client 保证金币种与 Scope 币种一致、Shadow/Replay 禁止创建，以及 Settlement Assessment 与 Point Domain 的 1:1 矩阵；
- Dispute 的 Request/Package、Case Current Outcome 与 Outcome Predecessor 由 Case Root 锁内完整 Tuple 查询、唯一索引和 CAS 保证同案；连续 Outcome Version、链尾、Case/State Version、首次结果与 V1 Outcome、结果/更正 Trigger ID 来源坐标、每个 Outcome 恰一条 Liability，以及 Liability Effect/Evaluation 同链、规则/操作人来源和 `security_deposit_use_allowed` 对应 LOST 的条件由 Root Lock 内聚合写入、设计期纯 JS 行矩阵和仓库自有 `local-environment` 真实 MySQL 反例共同保证。

## 12. 可靠执行与安全边界

- `t_compensation_event` 与业务变化同事务写入，提交后执行；状态只允许 INIT、PROCESSING、ERROR、FAILED，PROCESSING 必须持有成对 Lease，成功后删除记录。XXL-Job 只做丢失唤醒兜底扫描和分片。
- 不建设 Inbox/Outbox、中央补偿库或通用 Recovery Task。消费者幂等落在业务事实、来源版本和唯一键。
- `t_compensation_event` 已迁移为精确 15 列，以 `(event_key,event_type,sub_event_type)` 唯一；`payload_json VARCHAR(4096) NULL` 只保存无法从本库权威事实恢复的最小非敏感 JSON Object，`lock_owner VARCHAR(64)`，`lock_version BIGINT DEFAULT 1`。运行字段绑定 Foundation 安全默认值：`execute_status DEFAULT 'INIT'`、`execute_count DEFAULT 0`、`next_execute_at DEFAULT CURRENT_TIMESTAMP(6)`、`create_time DEFAULT CURRENT_TIMESTAMP(6)`、`update_time DEFAULT CURRENT_TIMESTAMP(6)`；访问索引精确为 `idx_comp_event_ready(execute_status,next_execute_at,id)` 与 `idx_comp_event_lease(execute_status,lock_until,id)`，以 `id` 稳定同时间批次的排序。表中没有旧 `execution_type/source_type/source_id/source_version/schema_version/payload_hash`，禁止 Vault Token、Vault Reveal 原值、Consumer/Network 原值、文件正文、Channel 原文、Secret 或卡数据。
- `t_command_idempotency.idempotency_key` 只接受已批准 Header 字符集中的 ASCII 值，并以字段级 `ascii/ascii_bin` 按原始 Bytes 做大小写敏感比较；服务不得改写 Key 大小写。这样 `uk_command_idem(caller_scope,operation_type,idempotency_key)` 与精确等值锁查询使用同一身份语义，不受表级 `utf8mb4_0900_ai_ci` 折叠。
- Risk 补偿事件重建清单在 `model-policy.mjs` 冻结 Qualification、Compliance Restriction、Checkout/Payment Assessment、Settlement Control、Dispute 与异步 Action Effect 的 `event_type/sub_event_type/event_key` 构造、权威表、信封来源和永久幂等位置。`CR_SETTLEMENT_CONTROL_EVENT` 通过 State Change `change_id` 重算 `risk:settlement-control-change:{changeId}`；`CR_SIMULATED_EFFECT_COMPENSATION_EVENT` 通过 Action Effect 的永久唯一 `idempotency_key` 连接本地任务，不依赖任何已删除 Source 字段。
- DD Snapshot 只保存有授权 Reveal/审计消费者的稳定 Vault Token、来源 Content Hash 和 Schema Version；没有元数据并发或同步消费者，因此不保存 Vault Record Version/Metadata Hash。DD 文件只保存 Platform File ID/Content Hash，替换文件生成新 File ID。没有真实消费者时不增加 Blind Index、Masked Value 或通用 Token Tuple。
- PAN、CVV/CVC、完整磁道、PIN/PIN Block 和卡有效期永不进入 Risk、Vault、事件、文件或日志。
- Consumer Stable Token、IP、User-Agent、代理链和派生网络维度属于受限数据，不进入普通日志、普通页面或 MQ；访问必须最小授权并可审计。

## 13. Review 门禁

自动门禁覆盖 JSON、确定性生成、精确总量、独立结构摘要、中文元数据、闭合枚举/开放目录、32 字符命名、索引前缀、本库关系、真 1:1、关键字段、文件引用对、Vault Token 引用、CHECK Policy、Diagram 覆盖和敏感禁存。

自动验证不能替代以下人工步骤：

1. 使用 PDManer 4.9.3 打开 `risk-compliance.pdma.json`；
2. 检查八个关系图、跨组引用锚点、当前投影指针和连线端口；
3. 执行应用内规范检查；
4. 按八个子域 Review 表、字段、枚举、索引、关系和中文说明；
5. 对照已落盘的完整 Schema SQL、Java 切片和仓库自有 `local-environment` 验证证据检查是否漂移；PDManer 4.9.3 人工 Review 仍是任何共享或生产使用前的独立门禁。继续禁止 Flyway、Liquibase、Testcontainers 或应用启动时 Schema/初始化数据迁移。

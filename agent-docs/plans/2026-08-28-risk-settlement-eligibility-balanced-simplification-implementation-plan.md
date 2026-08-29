# Risk Settlement Eligibility Balanced Simplification Implementation Plan

> 历史记录：本文记录三表 Control 精简的原始实施阶段。三检查点、旧 Finance 来源事实和 `beneficiary_account_ver` 已由 [Manual Payout 四检查点对齐实施记录](./2026-08-29-risk-manual-payout-eligibility-checkpoint-alignment-implementation-plan.md) 取代；不得把下文旧名称恢复为当前契约。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Risk Settlement 图组从 4 表收敛为 3 表，用 Settlement Eligibility Control 统一表达结算阻断和 Client 最低保证金门槛，并让 Finance 无损投影 1..N Scope、在三个检查点实时 Fail-Closed。

**Architecture:** Risk 以 `settlement_control` 为稳定根、`settlement_control_scope` 为不可变 OR 作用域、`settlement_control_state_change` 为追加生命周期；`settlement_risk_assessment` 继续在 Evaluation 图组冻结每个检查点的输入和结果。`CHECK_SETTLEMENT_ELIGIBILITY` 是不可删除的 ALLOW/FAIL_CLOSED 系统义务，真实 Control 只能来自 PRIMARY/REAL 本地同步 Rule Action。Finance 不新增表，仅把 `finance_restriction_proj` 扩展为每个 Risk Scope 一行，保证金仍由同步 RCO-I09 和现有 Requirement Snapshot 冻结。

**Tech Stack:** Node.js ESM、`node:test`、Risk/Finance Manifest DSL、PDManer 4.9.3 确定性生成器、Markdown/TypeScript requirements catalog、MySQL 8.4 物理合同。

**Spec:** `agent-docs/plans/2026-08-28-risk-settlement-eligibility-balanced-simplification-design.md`

## Global Constraints

- Risk 目标总表数必须恰好为 71，Settlement 图组恰好 3 表，Diagram Groups 保持 8；其他统计、CHECK 数和 Digest 必须生成后读取，不得手算冻结。
- Finance 统计净增量只允许 `+4 fields / +1 CHECK`，表、Unique Index、Access Index、Relation、1:1 和 Diagram Group 数不变。该门禁明确允许本计划列出的原位变更：`finance_restriction_proj` 四字段、两个 Unique/一个 Access Index 的列组合调整、Candidate `requirement_set_hash` 从 NN 改为 N、两个已有 Snapshot CHECK 表达式收紧，以及一个新来源/Scope CHECK。最终绝对统计必须叠加在 Finance 线程最新已同步基线上。
- 当前主目录仍显示 Finance 98/1,221，Finance 并行线程的 Provider 对账安全点报告为 97/1,211/166/160/172/28/7；在对方发出明确解冻短语并同步后，才能以当时主目录生成结果为真实起点，不得从任一组旧绝对数字反向覆盖。
- `t_reserve_requirement`、`SETTLEMENT_HOLD`、Risk 内部 `RESERVE_REQUIREMENT` 和百分比公式全部删除；Finance Snapshot 中的 `RESERVE_REQUIREMENT` 只作为 `MIN_SECURITY_DEPOSIT` 的兼容代码保留。
- `MIN_SECURITY_DEPOSIT` 只允许 Client，多条命中取 MAX，不相加；Risk 不读取 Finance Account，Finance 自行检查 Security Deposit 与 Recovery Receivable。
- Settlement 仅表示 Finance 向 Client/Local Partner 支付已形成可结算负债。不设计 Client-to-Beneficiary Disbursement，不给表增加 Beneficiary、代发用途或通用 Payout 字段。
- SHADOW/REPLAY 只保存 SIMULATED Effect，不得创建 Control、Scope、State Change 或业务事件。
- 本计划只授权 PDManer/Manifest/验证器/文档/requirements 设计资产；不创建 Migration、Java 模块、物理库、Redis/MQ 运行环境或银行执行代码。
- 不 commit、不创建分支、不打开或控制 PDManer GUI、不发布。PDManer 4.9.3 应用内视觉复核仅作为交付后的人工门禁，未执行时明确报告为 Pending。

## Concurrent-Task Ownership

| Phase | Exclusive writer | Files |
|---|---|---|
| Risk 专属实施 | 本 Risk 任务 | `scripts/pdmaner/risk/**`、Risk 专项 tests、`risk-compliance.pdma.json`、Risk 三份权威文档 |
| Finance 配套实施 | Finance 所有者解冻后的单一实施者 | Finance manifest/policy/oracle/tests/PDManer/三份权威文档 |
| 共享语义合并 | 最终集成者一人 | requirements、global sequences、6 份共享入口及其 tests |

任何阶段不得整文件复制另一 worktree 的 Oracle、PDManer 或共享文档；必须在当前主目录最新内容上逐段语义合并。

---

### Task 0: 执行前协调与冻结点

**Files:**
- Review only: `git status --short`
- Review only: Finance 并行线程的 final changed files 和验证回执
- Review only: 本计划 `Concurrent-Task Ownership`

**Interfaces:**
- Consumes: Finance 线程明确解冻短语、最终统计和 changed files。
- Produces: 无重叠 writer 的三阶段执行窗口。

- [ ] **Step 1: 重新列出活跃任务并发送文件所有权**

明确告知 Finance 和架构线程：Risk 阶段只写 Risk 专属文件，共享文件继续冻结。

- [ ] **Step 2: 校验 Finance 解冻条件**

在收到对方 final changed files、生成统计、验证结果和明确解冻短语之前，不得开始 Task 7-11 的 Finance/共享写入；Task 1-6 仍只能修改 Risk 专属文件。

- [ ] **Step 3: 记录基线而不修复脏工作树**

Run:

```bash
git status --short
git diff --name-only
```

保留所有现有修改。如出现未声明的 Risk 专属写入，停止对重叠文件的编辑，先读 diff 并语义合并；不 reset/checkout/clean。

### Task 1: 冻结 Risk 三表、Assessment 和 Action Scope 的 RED Oracle

**Files:**
- Create: `scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Test: `scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs`

**Interfaces:**
- Consumes: 当前 `02-decision-engine.mjs`、`03-evaluation.mjs`、`06-settlement.mjs`、`model-policy.mjs` 与已生成 Risk 模型。
- Produces: 新三表清单、删除结构、字段/索引/关系/CHECK 和 71 表目标的独立失败门禁。

- [ ] **Step 1: 写入目标表清单失败测试**

```js
assert.deepEqual(
  TABLE_GROUPS.settlement.map((table) => table.defKey),
  ["settlement_control", "settlement_control_scope", "settlement_control_state_change"],
);
assert.equal(TABLES.some((table) => table.defKey === "reserve_requirement"), false);
assert.equal(TABLES.length, 71);
```

测试还必须断言旧 `settlement_restriction*`、`reserve_rate`、`SETTLEMENT_HOLD`、直接 `decision_evaluation_id/release_id` 均已从 Control 结构消失。

- [ ] **Step 2: 冻结 Assessment 结果矩阵**

断言 `checkpoint_type/gross_amount/bank_country/eligibility_decision/required_amount/required_currency/control_set_hash`，以及 Checkpoint 与 `source_fact_type` 的精确组合。

- [ ] **Step 3: 冻结 Action Scope 参数序列**

断言 `rule_action_parameter.value_sequence` 非空且大于零，Unique Key 为：

```text
(rule_action_id, parameter_definition_id, value_sequence)
```

- [ ] **Step 4: 运行 RED**

Run:

```bash
node --test scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs
```

Expected: FAIL，原因必须是当前 4 表/旧字段/缺少 `value_sequence`，不得是语法或 fixture 错误。

### Task 2: 实现 Settlement Canonicalizers

**Files:**
- Create: `scripts/pdmaner/risk/settlement-control-canonicalizer.mjs`
- Create: `scripts/pdmaner/risk/settlement-eligibility-canonicalizer.mjs`
- Modify: `scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs`

**Interfaces:**
- `canonicalizeSettlementActionParameterIdentity(parameters) -> string`
- `buildSettlementControlInstanceKey({ ruleActionId, businessInstanceKey }) -> string`
- `canonicalizeSettlementControlScope(scope) -> string`
- `hashSettlementControlScope(scope) -> lowercase hex`
- `canonicalizeSettlementControlContent(control, scopes) -> string`
- `hashSettlementControlContent(control, scopes) -> lowercase hex`
- `canonicalizeSettlementControlScopeSet({ controlId, controlVersion, scopes }) -> string`
- `hashSettlementControlScopeSet(input) -> lowercase hex`
- `canonicalizeSettlementEligibilityInput(input) -> string`
- `hashSettlementEligibilityInput(input) -> lowercase hex`
- `canonicalizeSettlementControlSet(result) -> string`
- `hashSettlementControlSet(result) -> lowercase hex`

- [ ] **Step 1: 先写 Canonicalization RED 测试**

测试必须覆盖：BIGINT 十进制字符串、无前导零；金额无指数/无多余零；UTC 六位微秒；Null 不省略；Scope Hash 字典序；Control/Scope ID 数值序；乱序输入同 Hash；输入对象不被修改。

同一测试还必须冻结两条合法 Literal Scope Tuple 的正例：

```text
sequence=1 -> operatingEntity=210001, currency=USD, bankCountry=AE
sequence=2 -> operatingEntity=210002, currency=USD, bankCountry=*
```

交换数组物理顺序不改变规范结果；交换两条 Tuple 的 `value_sequence` 必须改变 Release 参数内容身份。

Run:

```bash
node --test scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs
```

Expected: FAIL with missing module/export or not-yet-implemented canonical bytes.

- [ ] **Step 2: 实现封闭 Schema 的 JCS 投影**

固定五个 Schema Code：

```text
CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE:1
CODE-MOR:RISK:SETTLEMENT_CONTROL_CONTENT:1
CODE-MOR:RISK:SETTLEMENT_CONTROL_SCOPE_SET:1
CODE-MOR:RISK:SETTLEMENT_ELIGIBILITY_INPUT:1
CODE-MOR:RISK:SETTLEMENT_CONTROL_SET:1
```

`canonicalizeSettlementActionParameterIdentity` 不定义第六个独立 Hash Schema；它只输出按 `parameterDefinitionId + valueSequence` 排序的封闭 `actionParameters` JSON 组件。每行显式包含 `ruleActionId/parameterDefinitionId/valueSequence/sourceType/featureVersionId/inputFieldId` 和五个类型值槽（空值也不省略），供现有 Release 内容投影纳入身份。

只接受本设计的封闭标量对象，未知字段、错误类型、Unicode surrogate、非规范 ID/金额/时间/Hash 立即拒绝；不读当前时间、不随机、不依赖数组原顺序。

- [ ] **Step 3: 运行 GREEN**

```bash
node --test scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs
```

Expected: Canonicalizer 用例 PASS；Manifest 目标仍可继续 RED。

### Task 3: 实现 Risk Manifest、CHECK 与跨行合同

**Files:**
- Modify: `scripts/pdmaner/risk/manifests/02-decision-engine.mjs`
- Modify: `scripts/pdmaner/risk/manifests/03-evaluation.mjs`
- Modify: `scripts/pdmaner/risk/manifests/06-settlement.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk/manifest.mjs`
- Modify: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`
- Test: `scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs`

**Interfaces:**
- Consumes: Tasks 1-2 的独立 Oracle 和 Canonical Schema。
- Produces: 71-table Risk production manifest、三表 Control 模型和完整物理合同。

- [ ] **Step 1: 扩展 Decision Engine 参数和义务**

`decision_point_obligation.obligation_type` 改为：

```text
ACTION | RESERVE_LIMIT_GROUP | CHECK_SETTLEMENT_ELIGIBILITY
```

`CHECK_SETTLEMENT_ELIGIBILITY` 必须 `trigger_decision=ALLOW`、`action_version_id=NULL`、`FAIL_CLOSED`、enabled，且不允许 Limit Binding。

给 `rule_action_parameter` 增加 NN `value_sequence`，将 `uk_rule_action_param` 改为三列唯一键，增加 `value_sequence>0` CHECK。

- [ ] **Step 2: 收敛 Settlement 三表**

`settlement_control` 根字段必须精确包含：

```text
control_id, control_no, role_type, role_id, control_type, reason_code,
source_action_effect_id, status, effective_from, effective_to,
required_amount, required_currency, release_condition_code,
control_instance_key, content_hash, active_slot, version
```

索引至少冻结：

```text
uk_settle_control_no(control_no)
uk_settle_control_effect(source_action_effect_id)
uk_settle_control_active(role_type,role_id,control_instance_key,active_slot)
idx_settle_control_role(role_type,role_id,active_slot,control_id)
```

`source_action_effect_id -> action_effect.effect_id` 必须定义为 `1:1` 本库关系，以上 Effect Unique Key 与 Relation 双重冻结“一个 REAL Effect 最多一个 Control”。

`settlement_control_scope` 冻结 `operating_entity_id/currency/bank_country/scope_hash` 和 `uk_settle_control_scope(control_id,scope_hash)`。

`settlement_control_state_change` 的物理名保持 32 字符限制内的 `t_settle_control_state_change`，冻结 `(control_id,aggregate_version)` 唯一约束；Trigger 幂等由类型化 Command/Timer Key、根 CAS 和连续聚合版本共同保证，不额外增加未批准的 Unique Index。

- [ ] **Step 3: 收紧 Risk 单行 CHECK**

至少实现下列稳定合同：

```text
ck_action_parameter_sequence
ck_settle_control_type
ck_settle_control_status_slot
ck_settle_control_range
ck_settle_control_instance_key
ck_settle_control_state
ck_settle_control_trigger
ck_settle_assessment_input
ck_settle_assessment_result
```

`ck_settle_control_type` 必须把 BLOCK/最低保证金金额矩阵和 Local Partner 禁止固化；`ck_settle_assessment_result` 必须将成功结果四列组合与未执行时的全空矩阵固化。

- [ ] **Step 4: 增加不能用单行 CHECK 表达的合同**

`model-policy.mjs` 必须冻结：

- Effect 只能对应一个 Control；
- Control 的 Effect/Evaluation/Intent/Action Version 同源；
- 恰好一个 `source_type=RULE + status=MERGED` Intent；
- 每个 Control 至少一个 Scope，恰好一条 `aggregate_version=1 + NULL->ACTIVE + ACTION_EFFECT` 初始 State Change，且 Trigger ID 等于来源 Effect ID；
- State Change 版本连续、前后状态衔接，根 `status/version/active_slot` 与最后事件一致；
- 每个 Control Version 恰好登记一个 `source_type=SETTLEMENT_CONTROL + event_type=SettlementControlChanged` 的 `compensation_event`；
- `MIN_SECURITY_DEPOSIT` 所有 Scope 币种非空且等于根币种；
- SHADOW/REPLAY 到 `settlement_control` 的 Forbidden Path；
- Settlement Assessment 与 `business_domain=SETTLEMENT` 一对一。

- [ ] **Step 5: 运行 Manifest GREEN 前半程**

```bash
node --test scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs
```

Expected: Manifest/Policy 断言 PASS；生成 PDManer/Digest 仍可因未重生成而 FAIL。

### Task 4: 更新 Release Static Validator 和运行事实 Fixture

**Files:**
- Modify: `scripts/pdmaner/risk/decision-engine-static-validator.mjs`
- Modify: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- Modify: `scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs`

**Interfaces:**
- Consumes: `obligationType`、Settlement Action Version/Parameter Definition/Binding、`crossRowRows.settlement_control*`。
- Produces: 发布前静态门禁与 PRIMARY/REAL 事实闭包校验。

- [ ] **Step 1: 先写 Validator RED 用例**

至少覆盖：

```text
三个 Settlement Point Version 各有恰好一个 CHECK_SETTLEMENT_ELIGIBILITY -> accept
缺失/重复/非 ALLOW/非 FAIL_CLOSED/禁用/带 Action/Limit Binding -> reject
Settlement REVIEW/DENY Rule 缺 reason_code -> reject
CREATE_SETTLEMENT_CONTROL 非 SETTLEMENT 分类、terminal、非 ACTIVE、不支持 Shadow 或 Handler Hash 不匹配 -> reject
Scope sequence 重复/断号/缺维/非法 ID-Currency-Country-* -> reject
非 Settlement 参数或 Control 根参数的 value_sequence!=1 -> reject
同一 Scope Parameter Definition 按 sequence=1/2 重复且两个 Tuple 完整 -> accept
Local Partner MIN_SECURITY_DEPOSIT -> reject
合法 PRIMARY + REAL + APPLIED + SUCCEEDED + 单 RULE MERGED Intent -> accept
Control 无 Scope/无初始 State Change/无 SettlementControlChanged 事件或根与事件版本不一致 -> reject
SIMULATED/异步/多 Intent/OBLIGATION Intent/目标不一致 -> reject
```

- [ ] **Step 2: 运行 RED**

```bash
node --test scripts/pdmaner/risk-decision-engine-contract.test.mjs
```

Expected: FAIL on old `CREATE_RESERVE_REQUIREMENT`、old settlement tables or missing Eligibility Obligation checks.

- [ ] **Step 3: 实现稳定错误码**

使用现有 `ValidationError[]` 结构，至少冻结：

```text
REQUIRED_SETTLEMENT_OBLIGATION_MISSING
REQUIRED_SETTLEMENT_OBLIGATION_AMBIGUOUS
SETTLEMENT_OBLIGATION_CONTRACT_INVALID
SETTLEMENT_RULE_REASON_MISSING
SETTLEMENT_ACTION_CONTRACT_INVALID
SETTLEMENT_SCOPE_SEQUENCE_INVALID
SETTLEMENT_SCOPE_TUPLE_INVALID
LOCAL_PARTNER_SECURITY_DEPOSIT_FORBIDDEN
CR_SETTLEMENT_CONTROL_EFFECT_INVALID
CR_SIMULATED_EFFECT_SETTLEMENT_CONTROL
```

静态 Action 契约固定为 `action_code=CREATE_SETTLEMENT_CONTROL`、`action_category=SETTLEMENT`、`target_type=SETTLEMENT_CONTROL`、`terminal_flag=false`、`status=ACTIVE`、`shadow_supported_flag=true`；运行事实再强制 `execution_type=LOCAL_SYNC`。必须同步把所有现有非重复 Action 参数 Fixture 显式迁移为 `valueSequence=1`，不用 Validator 默认补值掩盖旧数据。

Validator 的参数身份必须从 `(ruleActionId,parameterDefinitionId)` 改为 `(ruleActionId,parameterDefinitionId,valueSequence)`：只有三元组重复才报 `ACTION_PARAMETER_DUPLICATE`。非 Settlement 和 Control 根参数仍要求恰好一条 sequence=1；三个 Scope Definition 按连续 Sequence 分组，每组恰好三维。用于 Release 配置内容冻结的规范参数投影必须按 `parameterDefinitionId + valueSequence` 排序并显式编码 `valueSequence`；合同测试必须证明它不依赖 fixture/SQL 返回顺序。

错误排序和去重必须继续确定；不运行 Handler、不读时钟或数据库。

- [ ] **Step 4: 运行 Validator GREEN**

```bash
node --test scripts/pdmaner/risk-decision-engine-contract.test.mjs
```

Expected: PASS，且既有 AST、Limit、Shadow、Approval、Activation 合同全部保持。

### Task 5: 重生成 Risk PDManer 并冻结真实统计

**Files:**
- Modify by generator: `risk-compliance-service/docs/database/risk-compliance.pdma.json`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`
- Modify: `scripts/pdmaner/risk-limit-balanced-simplification.test.mjs`
- Modify: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`

**Interfaces:**
- Consumes: Tasks 1-4 的完整 Risk production manifest/policy。
- Produces: 71-table PDManer、真实 totals、Settlement=3 与更新后 Digests。

- [ ] **Step 1: 运行 Risk 生成器**

```bash
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs
```

- [ ] **Step 2: 从生成结果读取而非手算统计**

更新 Oracle 的 tables/fields/unique/access/relations/one-to-one/groups/CHECK count，以及 model/structural/check-policy Digest。只允许表数 71 和图组数 8 在生成前作为固定目标。

- [ ] **Step 3: 替换全部非历史的 72-table 断言**

包括 Limit 专项测试中对 Risk 总表数的间接断言；不改写历史实施记录的阶段数字。

- [ ] **Step 4: 运行 Risk 模型 GREEN**

```bash
node --test \
  scripts/pdmaner/risk-settlement-eligibility-balanced-simplification.test.mjs \
  scripts/pdmaner/risk-compliance-model.test.mjs \
  scripts/pdmaner/risk-decision-engine-contract.test.mjs \
  scripts/pdmaner/risk-limit-balanced-simplification.test.mjs
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
```

Expected: PASS，生成器报告 71 tables / 8 groups / Settlement 3。

### Task 6: 同步 Risk 权威文档与现实场景

**Files:**
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/plans/2026-08-28-risk-settlement-eligibility-balanced-simplification-design.md`
- Modify: `agent-docs/plans/2026-08-28-risk-limit-balanced-simplification-implementation-plan.md`
- Test: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: Task 5 的生成结构、真实统计和 Digest。
- Produces: Risk 当前权威关系图、表/字段清单、并发契约和现实案例。

- [ ] **Step 1: 更新关系图和三表说明**

权威文档必须明确：

```text
Decision Release -> Evaluation -> Action Effect -> Settlement Control
Settlement Control -> 1..N Scope
Settlement Control -> 1..N State Change
Evaluation -> 0..1 Settlement Risk Assessment
```

并明确 Security Deposit 只是门槛、Finance Account 仍是资金权威。

- [ ] **Step 2: 更新三检查点场景**

现实场景至少覆盖：

- Candidate 命中 Block + 20,000 USD 最低保证金；
- 精确释放 Block 后 Requirement 继续 ACTIVE；
- Promotion 不复用 Candidate 结果；
- Bank Submission/Retry 重新评估并在 Finance 锁 BANK_CASH；
- Local Partner 不查 Client Security Deposit/Recovery；
- Bank Country 来自 `beneficiary_account_ver.bank_country`；
- Settlement 与 Disbursement 语义分离。

- [ ] **Step 3: 固化并发、重放和状态边**

写明 Role Range Next-Key/Gap Lock、`Role Range -> Root -> Scope/Event` 锁顺序、锁后 DB UTC 时间、精确释放、Timer 幂等和终态不复活。

- [ ] **Step 4: 标注历史阶段而不改写历史**

只在旧 Limit 实施记录追加一句：其 72/866 是 Settlement Eligibility 实施前阶段；不修改已完成任务和当时验证数字。

- [ ] **Step 5: 运行 Risk 文档 GREEN**

```bash
node --test scripts/pdmaner/risk-compliance-scenario-doc.test.mjs
```

Expected: 现实场景覆盖全部 71 表，当前权威无删除表。

### Task 7: 等待 Finance 解冻并冻结 Projection RED Oracle

**Files:**
- Create: `scripts/pdmaner/finance-settlement-eligibility-projection.test.mjs`
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Test: `scripts/pdmaner/finance-settlement-eligibility-projection.test.mjs`

**Interfaces:**
- Consumes: Finance 所有者解冻后的最新 main manifest/policy/oracle/model。
- Produces: 相对最新 Finance 基线的 `+4 fields / +1 CHECK` 失败门禁。

- [ ] **Step 1: 确认解冻和当前基线**

Run:

```bash
git status --short
node scripts/pdmaner/finance/generate-finance-model.mjs --check
```

记录生成器当时输出；不把本计划中的 98 或 97 当成可写目标。

- [ ] **Step 2: 写入投影字段与索引 RED**

断言 `finance_restriction_proj` 增加：

```text
source_scope_key      VARCHAR(64) NN
source_scope_count    INTEGER N
source_scope_set_hash CHAR(64) N
country_scope_key     VARCHAR(4) NN
```

索引必须为：

```text
uk_fin_restrict_source(source_system,source_restriction_id,source_scope_key)
uk_fin_restrict_event(source_system,source_event_key,source_scope_key)
idx_fin_restrict_scope(owner_type,owner_id,operating_entity_id,currency_scope_key,country_scope_key,decision)
```

- [ ] **Step 3: 冻结 Snapshot 矩阵 RED**

断言：

```text
Candidate SETTLEMENT_RESTRICTION -> RESTRICT + amount NULL
Candidate RESERVE_REQUIREMENT    -> RESERVE + amount > 0
Settlement Snapshot             -> 仅 RESERVE_REQUIREMENT + RESERVE + amount > 0
Candidate requirement_set_hash  -> nullable
Settlement requirement_set_hash -> non-null
```

- [ ] **Step 4: 运行 RED**

```bash
node --test scripts/pdmaner/finance-settlement-eligibility-projection.test.mjs
```

Expected: FAIL on missing Scope fields/keys/CHECK or old Snapshot matrix，不得因 Provider 对账基线被回退而失败。

### Task 8: 实现 Finance Scope Projection 与 Snapshot 合同

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/06-settlement.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify by generator: `finance-service/docs/database/finance.pdma.json`
- Test: `scripts/pdmaner/finance-settlement-eligibility-projection.test.mjs`

**Interfaces:**
- Consumes: `SettlementControlChanged` 一个 Control Version 的完整 Scope Set。
- Produces: Risk Scope 每行投影、CCT ROOT 兼容行和两层 Requirement Snapshot。

- [ ] **Step 1: 增加四字段并重建三个索引**

仅修改 `06-settlement.mjs`；不新增 Finance 表或关系。

- [ ] **Step 2: 实现精确来源/Scope CHECK**

```sql
(
  source_system='RISK'
  AND restriction_type='SETTLEMENT_BLOCK'
  AND source_scope_key REGEXP '^[1-9][0-9]*$'
  AND source_scope_count IS NOT NULL
  AND source_scope_count>0
  AND source_scope_set_hash IS NOT NULL
  AND (country_scope_key='*' OR country_scope_key REGEXP '^[A-Z]{2}$')
)
OR
(
  source_system='CCT'
  AND source_scope_key='ROOT'
  AND source_scope_count IS NULL
  AND source_scope_set_hash IS NULL
  AND country_scope_key='*'
)
```

合同名固定为 `ck_fin_restrict_source_scope`。实际行数、同版本 Set Hash 和完整替换是跨行/未来消费事务合同，不伪造成单行 CHECK。

- [ ] **Step 3: 收紧 Snapshot 和 Hash 语义**

- `source_requirement_id=control_id`，`source_version=control_version`；
- `SETTLEMENT_BLOCK -> SETTLEMENT_RESTRICTION`，`MIN_SECURITY_DEPOSIT -> RESERVE_REQUIREMENT`；
- Candidate 保存每个命中 Control，Client 多个保证金行的 MAX 必须等于 RCO 顶层门槛；
- Promotion 只在 ALLOW 时建 Settlement，所以 Settlement Snapshot 不保存虚构的 ALLOW Block 行；
- Candidate/Settlement 根的 `requirement_set_hash` 直接冻结 Risk `control_set_hash`，不另定义排序。

- [ ] **Step 4: 重生成并更新真实 Finance 统计/Digest**

```bash
node scripts/pdmaner/finance/generate-finance-model.mjs
node --test \
  scripts/pdmaner/finance-settlement-eligibility-projection.test.mjs \
  scripts/pdmaner/finance-model.test.mjs
node scripts/pdmaner/finance/generate-finance-model.mjs --check
```

Expected: 相对 Task 7 实际基线恰好 `+4 fields / +1 CHECK`，其余结构统计不变；Provider 对账语义和 Digest 被保留。

### Task 9: 同步 Finance 权威文档与多 Scope 场景

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: `scripts/pdmaner/finance-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: Task 8 的生成结构和事件/投影契约。
- Produces: Finance 当前投影、检查点、回填与资金边界。

- [ ] **Step 1: 替换旧 Risk 结算事件消费语义**

Finance 投影不再消费 `ComplianceRestricted/SettlementRestricted/SettlementReleased`，仅消费聚合事件 `SettlementControlChanged`。CCT 的 Client/Entity 根限制继续以 `ROOT/*` 矩阵投影。

- [ ] **Step 2: 固结聚合事件事务**

一个 Control Version 只有一个 Event Key/Payload Hash，消费者在同一 Finance 本地事务内锁定根范围、验证 Count/Set Hash/版本，完整替换 Scope 行。同版本同 Hash 幂等，同版本异 Hash、版本缺口、部分展开均 Fail-Closed。

Projection 生命周期映射必须固定为：

```text
Control status=ACTIVE           -> 保留完整 Scope 行，decision=RESTRICT
Control status=RELEASED/EXPIRED -> 保留完整 Scope 行，decision=ALLOW
```

终态事件不删除 Projection 行；它以更高 `source_version`、同一完整 Scope Set 和 ALLOW 结果替换旧 RESTRICT 版本。`effective_from/effective_to` 仍逐值冻结，ACTIVE 行只在当前检查时间命中半开区间时阻断。

- [ ] **Step 3: 固结回填和检查点**

- CCT 旧行确定性回填 `ROOT / * / NULL / NULL`；
- Risk 旧单行不得猜测，必须清除并由完整聚合事件重建；
- 重建完成前 Settlement 全路径 Fail-Closed；
- Candidate/Promotion 锁 Risk Projection + Client Accounts，不锁 BANK_CASH；
- First/Retry Bank Submission 额外锁定精确 BANK_CASH。

- [ ] **Step 4: 运行 Finance 场景 GREEN**

```bash
node --test scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: 多 Scope 原子展开、缺 Scope/版本缺口、Projection/RCO 不一致、CCT ROOT、Risk 重建、`ACTIVE->RESTRICT`、`RELEASED/EXPIRED->ALLOW 且保留完整行`，以及 Local Partner 禁止保证金均有明确测试证据。

### Task 10: 单点合并 Requirements 与全局时序

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/components/architecture-diagrams.tsx`
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`

**Interfaces:**
- Consumes: Risk/Finance 已稳定契约和两侧最终统计。
- Produces: 统一的 RCO-I09、FIN-13、E2E-06、事件目录和架构标签。

- [ ] **Step 1: 先改测试冻结新语义**

测试必须断言：

- RCO-I09 是 Settlement Eligibility，返回 `reason_codes/matched_controls/required_amount/control_set_hash`；
- 三检查点每次新 Evaluation，不复用旧结果；
- `SETTLEMENT_BLOCK` 与 `MIN_SECURITY_DEPOSIT` 的差异；
- Local Partner 不访问 Client Accounts；
- `SettlementControlChanged` 替代旧结算限制事件；
- Settlement/Payout Request/Disbursement 语义不混用。

- [ ] **Step 2: 运行 Requirements RED**

```bash
npm --prefix requirements-site test
```

Expected: FAIL on old `Settlement Restriction/RESERVE_REQUIREMENT/SettlementRestricted/SettlementReleased` current semantics.

- [ ] **Step 3: 逐段更新 Catalog 与 E2E-06**

只替换 Risk/Finance Settlement 段落，保留 Provider 对账线程最新的 CASH_IN_TRANSIT/对账语义、保证金 Account 边界、Dispute 边界和全部其他系统内容。

- [ ] **Step 4: 运行 Requirements GREEN**

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: PASS。

### Task 11: 用真实生成统计单点合并共享入口

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `requirements-site/tests/site-content.test.mjs`

**Interfaces:**
- Consumes: Task 5 的 Risk 真实统计、Task 8 的 Finance 真实统计、当前其他四系统基线。
- Produces: 六份共享入口一致数字和当前 Risk 设计/实施路由。

- [ ] **Step 1: 再次确认共享 writer 为单一任务**

发送最终 changed files，等待活跃任务确认共享入口已冻结。

- [ ] **Step 2: 只替换 Risk/Finance 子串与路由**

- Risk 更新为生成器真实数字并追加本 Design/Implementation Plan；
- Finance 更新为 Provider 对账 + Settlement Projection 合并后的真实数字和当前语义；
- CCT、Commerce、Payment、Platform 的数字与语义不改。

- [ ] **Step 3: 检查六文件统计完全一致**

```bash
npm --prefix requirements-site test
```

Expected: 统计/路由断言 PASS，无任何已解决并行任务的 stale 数字。

### Task 12: 全量门禁、残留扫描与独立复核

**Files:**
- Review: 本计划全部 changed files

**Interfaces:**
- Consumes: Tasks 0-11 的完整联合增量。
- Produces: 可交付的 Risk/Finance 基线、门禁证据和独立审查结论。

- [ ] **Step 1: 运行 Risk 全量**

```bash
node --test scripts/pdmaner/risk-*.test.mjs scripts/pdmaner/risk-compliance-*.test.mjs
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
```

- [ ] **Step 2: 运行 Finance 全量**

```bash
node --test scripts/pdmaner/finance-*.test.mjs
node scripts/pdmaner/finance/generate-finance-model.mjs --check
```

- [ ] **Step 3: 运行六模型与六生成器**

```bash
node --test scripts/pdmaner/*.test.mjs
node --test scripts/pdmaner/*-model.test.mjs scripts/pdmaner/*-scenario-doc.test.mjs
node scripts/pdmaner/generate-client-contract-model.mjs --check
node scripts/pdmaner/generate-commerce-model.mjs --check
node scripts/pdmaner/generate-payment-model.mjs --check
node scripts/pdmaner/finance/generate-finance-model.mjs --check
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check
```

- [ ] **Step 4: 运行 Requirements build/test/lint**

```bash
npm --prefix requirements-site run build
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

- [ ] **Step 5: 执行 diff、冲突与 stale 扫描**

```bash
git diff --check
rg -n "^(<<<<<<<|=======|>>>>>>>)" \
  --glob '!requirements-site/node_modules/**' \
  --glob '!.git/**' .
rg -n \
  "t_settlement_restriction|t_reserve_requirement|t_settle_restrict_state_change|SETTLEMENT_HOLD|CREATE_RESERVE_REQUIREMENT" \
  scripts/pdmaner/risk \
  risk-compliance-service/docs/database \
  agent-docs/database/RISK_COMPLIANCE_DATABASE.md \
  agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md
```

Finance Snapshot 兼容码 `RESERVE_REQUIREMENT` 允许保留，因此不做全仓无差别禁词。历史计划中的旧表名允许作为明确历史证据存在。

- [ ] **Step 6: 请求独立代码/设计复核**

使用 `superpowers:requesting-code-review`，要求审查者只读检查：

- 是否完整实现已确认 Design；
- Risk/Finance 投影契约是否对称；
- 是否保留 Provider 对账并行任务的最新语义；
- 是否存在 Critical/Important 问题。

所有 Critical/Important 修复后必须重跑本 Task 全量命令。

- [ ] **Step 7: 记录人工视觉门禁状态**

不启动 PDManer。交付时明确写明：`PDManer 4.9.3 in-app visual review: PENDING`，除非用户后续亲自完成并提供结果。

## Execution Handoff

推荐使用 `superpowers:subagent-driven-development`，但只允许按下列串行交付点并行独立的读取/复核：

```text
Risk exclusive implementation
  -> Risk read-only review
  -> wait for Finance owner explicit unfreeze
  -> Finance projection implementation on latest main
  -> Finance read-only review
  -> one-writer shared semantic merge
  -> full read-only gate and independent review
```

实施中任一共享文件发生意外变动时，放弃当前文件的盲目继续写入，先获取对方 changed files 和语义，再在最新内容上逐段合并。

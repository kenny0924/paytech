# Finance Trial Balance And Close Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收紧 Finance 试算平衡、投影重建、旧水位保护、双账簿往来核对和期间关账事务门禁，使旧 Run、可变异常解决或投影漂移不能产生错误 Final Trial 或覆盖较新期间余额。

**Architecture:** Journal 继续是唯一不可变会计事实；Trial Run 是按 `ledger_book_id + period_id + as_of_sequence` 生成的结果快照，失败 Run 不转成功，修复后必须生成新 Run。`t_ledger_period_balance` 仍是最新期间科目余额投影，只增加自身水位并以单调条件更新防止旧 Run 回写；关账继续复用现有八张表，不新增表。

**Tech Stack:** Node.js ESM、`node:test`、PDManer 4.9.3 JSON、Finance manifest/schema DSL、Markdown 权威文档。

**Spec:** `agent-docs/database/FINANCE_DATABASE.md` 第 4.7 节、`finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`、本会话确认的六项 Trial/Close 收紧决策。

## Global Constraints

- Finance、其他五个核心系统和外部系统数据库保持隔离；本计划不引入跨库读写或跨库外键。
- Journal 是唯一不可变会计事实，Projection Rebuild 不得追加虚假 Journal。
- 不新增通用 Intercompany、Accounting Entity、Trial Resolution 或 Projection Rebuild 表。
- 维持 122 张表、211 个唯一索引、188 个访问索引、211 个关系、41 个 1:1 关系和 8 个 Diagram Group；只增加 `t_ledger_period_balance.as_of_sequence` 一个字段。
- 本计划不设计 Platform Service Charge VAT、Invoice、Credit Note、功能货币、FX 或期初迁移。
- 不创建 Java、Migration、物理数据库或部署环境，不提交 Git。
- 采用测试先行：先让 Finance 专项测试因缺少新契约而失败，再修改 manifest、policy、文档、场景、PDManer 和独立 Oracle。

---

### Task 1: 冻结 Trial Run 结果语义和 Hash 生命周期

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance/manifests/07-close.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`

**Interfaces:**
- Consumes: 现有 `trial_balance_run`、`trial_balance_line` 和 CHECK Policy 生成能力。
- Produces: `content_hash` 在运行态为空、终态非空的物理契约，以及期末余额实际借贷方向合计语义。

- [x] **Step 1: 写失败测试**

在 `Finance close and material reconciliation controls are structurally reviewable` 中断言：

```js
const trialRun = entities.get("t_trial_balance_run");
assert.equal(trialRun.fields.find((field) => field.defKey === "content_hash")?.notNull, false);
assert.match(trialRun.fields.find((field) => field.defKey === "total_debit")?.comment, /期末余额.*实际借方/u);
assert.match(trialRun.fields.find((field) => field.defKey === "total_credit")?.comment, /期末余额.*实际贷方/u);
assert.equal(
  MODEL_CHECK_CONTRACTS.ck_trial_balance_hash,
  "(status IN ('CREATED','RUNNING') AND content_hash IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND content_hash IS NOT NULL)",
);
```

- [x] **Step 2: 运行测试并确认 RED**

Run: `node --test scripts/pdmaner/finance-model.test.mjs`

Expected: FAIL，原因是现有 `content_hash` 非空、借贷合计说明仍为“所有试算行金额合计”，且不存在 `ck_trial_balance_hash`。

- [x] **Step 3: 最小实现**

修改 manifest：

- `content_hash` 改为 nullable；
- `total_debit/total_credit` 明确只汇总可过账叶子科目的期末有符号余额，并按科目正常方向与余额正负拆成实际借方/贷方；
- `period_debit/period_credit` 继续只表示期间发生额；
- Hash 只覆盖 Scope、水位、Trial Line 和异常发现事实，不包含可变解决 Tuple；失败 Run 不转成功。

在 model policy 增加：

```js
ck_trial_balance_hash: "(status IN ('CREATED','RUNNING') AND content_hash IS NULL) OR (status IN ('SUCCEEDED','FAILED') AND content_hash IS NOT NULL)"
```

并绑定到 `trial_balance_run`。

- [x] **Step 4: 更新独立 Oracle 并确认 GREEN**

同步 `EXPECTED_CHECK_CONTRACTS`、`EXPECTED_CHECK_BINDINGS`，先不更新模型 Digest。运行：

`node --test scripts/pdmaner/finance-model.test.mjs`

Expected: 新契约断言不再失败；Digest/模型陈旧失败留到 Task 5 处理。

### Task 2: 增加 Projection Rebuild 和 Waiver 硬错误边界

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance/manifests/07-close.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`

**Interfaces:**
- Consumes: `trial_balance_exception.resolution_type` 与现有解决 Tuple。
- Produces: `PROJECTION_REBUILD` 解决类型；数据库层禁止 `GENERAL_LEDGER_MISMATCH` 使用 `CONTROLLED_WAIVER`。

- [x] **Step 1: 写失败测试**

断言生成模型的 `resolution_type` 允许值含 `PROJECTION_REBUILD`，并断言 CHECK Policy：

```js
assert.match(resolutionType.comment, /PROJECTION_REBUILD/u);
assert.match(MODEL_CHECK_CONTRACTS.ck_trial_exception_resolution, /exception_type='GENERAL_LEDGER_MISMATCH'.*PROJECTION_REBUILD/u);
assert.match(MODEL_CHECK_CONTRACTS.ck_trial_exception_resolution, /status='WAIVED'.*exception_type<>'GENERAL_LEDGER_MISMATCH'.*CONTROLLED_WAIVER/u);
```

- [x] **Step 2: 运行测试并确认 RED**

Run: `node --test scripts/pdmaner/finance-model.test.mjs`

Expected: FAIL，原因是现有 enum 和 CHECK 只有 `RECON_RESOLUTION/ACCOUNTING_INSTRUCTION/CONTROLLED_WAIVER`。

- [x] **Step 3: 最小实现**

- `PROJECTION_REBUILD` 只允许解决 `GENERAL_LEDGER_MISMATCH`，引用受控重建命令结果；
- `CONTROLLED_WAIVER` 至少在数据库层禁止 `GENERAL_LEDGER_MISMATCH`；
- 应用契约进一步禁止 Journal 不平衡、Journal/Line 缺失、跨 Book/Period/Sequence、单边 Platform Service Charge 使用 Waiver；
- Projection Rebuild 固定从 Journal 重算投影，不写 Journal，完成后必须新建 Trial Run。

- [x] **Step 4: 更新 Oracle 并确认新断言 GREEN**

同步独立 CHECK Oracle，运行 Finance model test；Digest 陈旧失败继续留到 Task 5。

### Task 3: 为期间余额增加单调水位并固化双账控制

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance/manifests/04-balance-funding.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`

**Interfaces:**
- Consumes: `trial_balance_run.as_of_sequence`。
- Produces: `ledger_period_balance.as_of_sequence BIGINT NOT NULL` 和 `as_of_sequence>=0` CHECK；应用层仅允许较新水位覆盖、同 Run 同水位幂等。

- [x] **Step 1: 写失败测试**

```js
const periodBalance = entities.get("t_ledger_period_balance");
const sequence = periodBalance.fields.find((field) => field.defKey === "as_of_sequence");
assert.equal(sequence?.type, "BIGINT");
assert.equal(sequence?.notNull, true);
assert.match(sequence?.comment, /旧水位.*不得覆盖/u);
assert.equal(MODEL_CHECK_CONTRACTS.ck_ledger_period_balance_sequence, "as_of_sequence>=0");
```

同时断言 `trial_balance_exception` 或 Trial 控制说明包含 Platform Service Charge 两本账应收/应付匹配且不得单边豁免。

- [x] **Step 2: 运行测试并确认 RED**

Run: `node --test scripts/pdmaner/finance-model.test.mjs`

Expected: FAIL，原因是 `t_ledger_period_balance` 没有水位字段和 CHECK，Trial 控制说明未覆盖服务费双边核对。

- [x] **Step 3: 最小实现**

- 在 `trial_balance_run_id` 前增加 `as_of_sequence`；
- 说明更新事务必须锁定现有行，`new_sequence > old_sequence` 才覆盖；相同 Run/Sequence 幂等，旧水位拒绝；
- Trial 的 `COUNTERPARTY_MISMATCH` 覆盖同一 Economics/Component/Currency 的 `PLATFORM_SERVICE_PAYABLE` 与 `PLATFORM_SERVICE_RECEIVABLE`；
- 不增加新索引、新关系或新异常表。

- [x] **Step 4: 更新 Oracle 并确认新断言 GREEN**

把 EXPECTED_TOTALS.fields 从 1486 更新为 1487，增加 CHECK Oracle；Digest 留到 Task 5。

### Task 4: 固化 Final Trial 和 Close 的跨表事务门禁

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance/manifests/07-close.mjs`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`

**Interfaces:**
- Consumes: `period_close_book`、Final Trial、Closing/Reopening Instruction、Journal、Close Step 和期间余额水位。
- Produces: `FINALIZED` 的同事务跨表门禁契约。

- [x] **Step 1: 写失败测试**

直接检查生成模型中 `t_period_close_book` 的表/状态字段契约，要求明确包含：同 Book、同 Period、同 Sequence、Final Trial SUCCEEDED、无开放异常、对应 Journal、期间余额同水位、七步 SUCCEEDED 和单事务。

- [x] **Step 2: 运行测试并确认 RED**

Run: `node --test scripts/pdmaner/finance-model.test.mjs`

Expected: FAIL，现有注释只说明成功试算和结转证据，没有完整事务门禁。

- [x] **Step 3: 最小实现**

- 收紧 manifest 表/字段说明；
- 在 Finance invariant 中写明 `FINALIZED` 条件必须在一笔事务内重验；
- Final Trial 可以包含已解决或被允许豁免的软异常，但不得有 `OPEN/UNDER_REVIEW`，也不得有硬错误 Waiver；
- 任何修复 Journal 都会推进 Sequence 并要求新 Trial；Projection Rebuild 不推进 Journal Sequence，但仍要求新 Trial；
- 旧 Run 永远不能成为 Final Trial 的替代品。

- [x] **Step 4: 运行模型测试确认结构契约 GREEN**

Digest 陈旧失败继续留到 Task 5。

### Task 5: 更新现实场景、生成 PDManer 并刷新独立 Oracle

**Files:**
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/finance.pdma.json`（由生成器生成）
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/database/README.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: Tasks 1–4 的 manifest、policy 和文档契约。
- Produces: 122 表、1487 字段的确定性 Finance PDManer 模型与同步项目统计。

- [x] **Step 1: 写失败场景测试**

在 `finance-scenario-doc.test.mjs` 断言场景包含：

- 期末余额实际借贷方向合计；
- 运行态 Hash 为空、终态 Hash 非空；
- `PROJECTION_REBUILD` 不产生 Journal 且修复后新建 Run；
- `ledger_period_balance.as_of_sequence=9015`，旧水位回写被拒绝；
- Platform Service Charge 两本账往来核对；
- Finalized 同事务门禁。

- [x] **Step 2: 运行场景测试并确认 RED**

Run: `node --test scripts/pdmaner/finance-scenario-doc.test.mjs`

Expected: FAIL，现有现实场景缺少上述新证据。

- [x] **Step 3: 更新现实场景和项目统计**

- 给 `t_ledger_period_balance` 示例增加准确水位；
- 增加一个运行态 Trial Hash 为空的例子和一个 Projection Rebuild 分支；
- 明确旧 Trial 不覆盖水位 9015；
- 明确双账往来核对及 Final Trial 门禁；
- 将 Finance 字段统计由 1486 更新为 1487，其他统计保持不变。

- [x] **Step 4: 生成模型并读取新摘要**

Run: `node scripts/pdmaner/finance/generate-finance-model.mjs`

Expected: 输出 122 tables、1487 fields、211 unique indexes、188 access indexes、211 relations、41 1:1 relations、8 diagrams。

- [x] **Step 5: 刷新独立 Oracle 摘要**

根据生成器和失败输出更新：

- `EXPECTED_TOTALS.fields=1487`；
- `EXPECTED_MODEL_DIGEST`；
- `EXPECTED_STRUCTURAL_DIGEST`；
- 新增/变更 CHECK contracts 与 bindings。

- [x] **Step 6: 运行 Finance 两组专项测试**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs
node --test scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: 全部 PASS。

### Task 6: 全量验证和实施记录收尾

**Files:**
- Modify: `agent-docs/plans/2026-08-27-finance-trial-balance-close-hardening-implementation-plan.md`

**Interfaces:**
- Consumes: 完整变更集。
- Produces: 可复现的验证证据和完成状态。

- [x] **Step 1: 运行生成器 Check 和双摘要验证**

```bash
node scripts/pdmaner/finance/generate-finance-model.mjs --check
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: Finance model up to date，专项测试中的 model/structural 双摘要与独立 Oracle 一致。生成器只支持 `--check`，不虚构 `--check-hash` 参数。

- [x] **Step 2: 运行全量 PDManer 测试**

Run: `node --test scripts/pdmaner/*.test.mjs`

Expected: 新 Finance 测试通过；若仅存在已知 Commerce stale 失败，单独记录，不能归因于本次 Finance 变更。

- [x] **Step 3: 运行 requirements-site 测试**

Run: `npm --prefix requirements-site test`

Expected: 83/83 或当前套件全部 PASS。

- [x] **Step 4: 检查格式与变更范围**

```bash
git diff --check
git diff -- scripts/pdmaner/finance scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs scripts/pdmaner/finance-test-oracle/schema.oracle.mjs finance-service/docs/database agent-docs AGENTS.md
```

Expected: 无空白错误；没有 Java、Migration、部署、VAT/Invoice、通用 Intercompany 或无关文件变更。

- [x] **Step 5: 更新本计划实算结果**

记录最终模型统计、Finance 专项、全量 PDManer、requirements-site、生成器与 Hash Check 的实际命令和结果；不创建 Git commit。

## 实施结果（2026-08-27）

- 最终模型：122 表、1,487 字段、211 个唯一索引、188 个访问索引、211 条本库关系、41 条 1:1 关系、8 个 Diagram Groups。
- 共享模板 SHA-256：`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- 模型文件 SHA-256：`76667f3230540e5769173a05eb5e408a14f3d78f848837b50f34eeb659599ace`。
- 独立结构 SHA-256：`60edef12f7bd15567f19346fd04f671bf83d6bc86189c30ad014c5b0f360abff`。
- `node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs`：21/21 PASS。
- `node scripts/pdmaner/finance/generate-finance-model.mjs --check`：PASS，模型无漂移。
- `node --test scripts/pdmaner/*.test.mjs`：143/143 PASS。
- `npm --prefix requirements-site test`：构建成功，83/83 PASS。首次执行因本 worktree 缺少已锁定依赖而未进入测试；确认 package.json 与 lockfile 和主工作区逐字节一致后执行 `npm ci`，未修改依赖清单或锁文件。
- `git diff --check`：PASS。
- 未创建 Java、Migration、物理数据库、部署产物或 Git commit；VAT、Invoice、Credit Note、FX、期初迁移与通用 Intercompany 均未进入本次设计。
- PDManer 4.9.3 应用内人工兼容检查仍是未完成门禁。

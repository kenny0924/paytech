# Payment Execution Simplification Implementation Plan

状态：已完成；模型、文档、现实场景、requirements、导航与自动契约已同步，PDManer 4.9.3 应用内检查仍待人工完成。

> 后续已在同一精简权威下删除 Channel Refund 可由 Position 推导的 Intent/Attempt、无消费者 Binding Hash、两个访问索引和两条关系，并新增 Funding Reservation 唯一索引；本文中的目标数量已按当前工件刷新。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已确认的 Payment Instrument 单表、通用 Customer Action、退款/重复支付语义和资金主链状态轨迹落到权威文档、确定性生成器、自动契约与 PDManer 工件。

**Architecture:** 保持 Intent → Attempt → Action 主链和 29 表总量；用 Attempt 的 Payment Method Catalog 代码表达支付方式，用可选 Instrument 单表表达稳定 Provider 工具引用，用 Customer Action 替换 3DS 专表。退款与重复支付不新增表，只强化精确 Transaction Ref 导航和判定不变量。

**Tech Stack:** Node.js test runner、确定性 PDManer JSON 生成器、TypeScript requirements catalog、Markdown 权威文档。

**Spec:** `agent-docs/plans/2026-08-25-payment-execution-simplification-design.md`

## Global Constraints

- 只修改设计资产、生成器、测试和本地 requirements site；不创建 Migration、Java、数据库或环境。
- 表名使用 `t_`，唯一索引使用 `uk_`，访问索引使用 `idx_`。
- 不新增 Instrument JSON、Key-Value、Wallet/Bank/BNPL 预留字段或类型化子表。
- 不保存 PAN、CVV/CVC、卡有效期、临时 Redirect URL、Client Secret、SDK Token、QR Payload 或 bearer-like 参数。
- 不提交、建分支、部署或发布。

---

### Task 1: Freeze the new independent test oracle

**Files:**
- Modify: `scripts/pdmaner/payment-model.test.mjs`
- Modify: `scripts/pdmaner/payment-scenario-doc.test.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`

**Interfaces:**
- Consumes: 当前 29 表模型和已确认设计。
- Produces: 独立的 29 表 / 376 字段 / 63 关系 Oracle，以及 Payment Method、Customer Action、退款、重复支付和状态流转回归契约。

- [x] **Step 1: Replace the old Three DS oracle with Customer Action**

把 `t_three_ds_context` 替换为 `t_payment_customer_action` 的十个精确字段，给 Attempt 增加非空 `payment_method_code`，把 Instrument 的 `provider_token_ref` 改为 `provider_instrument_ref`；关系 Oracle 删除 Customer Action→Attempt，只保留 Customer Action→Action 1:1。

- [x] **Step 2: Freeze closed/open contracts and totals**

断言目标总量 `29 / 376 / 42 / 44 / 63 / 7 / 7`，Customer Action 两组闭合枚举，Attempt Payment Method Catalog 开放来源，以及 State Transition 五类资金聚合白名单。

- [x] **Step 3: Freeze scenario and requirements behavior**

断言现实场景使用 Instrument 单表、Customer Action、精确退款原 Ref、第二个不同成功资金事实判重，并且不出现旧 Three DS 表、Instrument JSON/EAV 或 Provider Callback/Dispute 通用状态流转。

- [x] **Step 4: Run tests and verify RED**

Run: `node --test scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs requirements-site/tests/site-content.test.mjs requirements-site/tests/global-use-case-sequences.test.mjs`

Expected: FAIL because the generator, PDManer, metadata, scenario and requirements still expose the old Three DS and Instrument contracts.

### Task 2: Implement the deterministic model and metadata

**Files:**
- Modify: `scripts/pdmaner/generate-payment-model.mjs`
- Modify: `scripts/pdmaner/payment-field-metadata.mjs`
- Regenerate: `payment-orchestration-service/docs/database/payment-orchestration.pdma.json`

**Interfaces:**
- Consumes: Task 1 independent Oracle.
- Produces: Deterministic 29-table Payment PDManer model matching the approved schema.

- [x] **Step 1: Update table, field, index, relation and diagram manifests**

替换 Customer Action 表；保留 Instrument 单表；增加 Attempt Payment Method；维持 Customer Action 单字段唯一索引和状态/过期时间访问索引；删除冗余 Attempt 关系。

- [x] **Step 2: Update Chinese metadata and code contracts**

为所有新增/改名字段提供中文名、中文说明、闭合枚举或精确开放代码来源，并明确 Customer Action 禁止保存秘密跳转材料。

- [x] **Step 3: Generate the model**

Run: `node scripts/pdmaner/generate-payment-model.mjs`

Expected: `29 tables, 376 fields, 42 unique indexes, 44 access indexes, 63 relations, 7 diagram groups`.

- [x] **Step 4: Run focused tests and verify GREEN except approved digests/docs**

Run: `node --test scripts/pdmaner/payment-model.test.mjs`

Expected: Only deterministic digest or not-yet-synchronized Physical Decisions assertions may remain red.

### Task 3: Synchronize business authorities and requirements

**Files:**
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md`
- Modify: `payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: navigation/context files that contain the Payment physical baseline
- Modify: historical Payment plans only by adding or refreshing supersession notices

**Interfaces:**
- Consumes: Generated model from Task 2.
- Produces: One consistent business meaning across logical, physical, scenario, requirement and agent navigation authorities.

- [x] **Step 1: Update logical and physical decisions**

写入精确新字段、关系、枚举、CHECK、退款链、重复支付判定和状态流转白名单；同步 Manifest 总量与新模型摘要。

- [x] **Step 2: Update the real-world scenario**

把 CARD Attempt、Provider Instrument、Customer Action、3DS Challenge、UNKNOWN Query 恢复、退款和重复成功映射到真实字段；删除旧表行和已移除的 Callback/Dispute Transition 行。

- [x] **Step 3: Update requirements and global sequences**

冻结 Attempt Payment Method、通用 Customer Action、精确退款 Ref、重复成功判定和资金主链 Transition；不把 APM 特有字段或秘密材料引入外部契约。

- [x] **Step 4: Update navigation and supersession notes**

把所有当前 Payment 基线更新为 `29 / 376 / 42 / 44 / 63 / 7`，将本设计加入文档导航，并把旧 29/382 与 Three DS 专表阶段标记为历史。

### Task 4: Final verification

**Files:**
- Verify all files modified above.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: Fresh evidence for completion.

- [x] **Step 1: Verify syntax and generation idempotency**

Run: `node --check scripts/pdmaner/generate-payment-model.mjs && node --check scripts/pdmaner/payment-field-metadata.mjs && node scripts/pdmaner/generate-payment-model.mjs --check`

- [x] **Step 2: Run Payment model and scenario contracts**

Run: `node --test scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs`

- [x] **Step 3: Run requirements regression and lint**

Run: `npm --prefix requirements-site test && npm --prefix requirements-site run lint`

- [x] **Step 4: Check retired artifacts and whitespace**

确认生成 JSON 不含 `t_three_ds_context`、`provider_token_ref` 或被移除的 State Transition 类型，当前权威文档不含旧 29/382/66 基线，并运行 `git diff --check`。

- [x] **Step 5: Report the manual gate**

报告自动测试精确结果，并明确 PDManer 4.9.3 应用内打开、关系图和规范检查仍待人工完成。

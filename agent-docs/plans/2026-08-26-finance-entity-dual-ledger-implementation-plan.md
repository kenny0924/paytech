# Finance Entity、Platform Service Charge 与 Partner OE 双账簿实施计划

> **后续替代说明（2026-08-27）：** 本文中顺带描述的 `t_refund_funding_reservation`、REFUND Commitment 以及所有 generic Funding Position/Commitment/Allocation/Move 已分别由 [Finance 退款 Funding 移除实施记录](./2026-08-27-finance-refund-funding-removal-implementation-plan.md)、[通用 Funding 模块删除设计](./2026-08-27-finance-funding-module-removal-design.md)与[实施记录](./2026-08-27-finance-funding-module-removal-implementation-plan.md)删除。本文中的 121 表等统计均为历史快照，当前 Finance 为 103 表；只保留不受影响的 Finance Entity、Platform Service Charge 与 Partner OE 双账簿设计。

> **执行要求：** 后续实施时使用 `superpowers:executing-plans` 按任务逐项执行并在每个检查点停下来复核。本计划只授权 PDManer 设计、生成器、模型测试、权威文档和 Requirements 同步；不授权 Java、Migration、物理数据库、部署、分支或 Git Commit。

**目标：** 在不引入通用 `t_accounting_entity` 或跨库读写的前提下，让 Partner 来源交易同时保留 Partner OE 的交易账簿和平台 Finance Entity 的独立账簿，并以合同明确的 `PLATFORM_SERVICE_CHARGE` 在两本账分别形成平衡 Journal，使平台收入和利润只归属于平台法律主体。

**架构：** CCT 继续拥有法律 Party、Partner Agreement 和商业条款；Commerce 冻结订单创建时命中的平台服务费 Scope 与平台法律 Party；Finance 用本地人工维护的 `t_finance_entity` 映射该 Party，并将 `t_ledger_book`、法定财务 Root 和必要资金范围泛化为 `entity_type + entity_id`。一个服务费经济组件产生两个不同 `instruction_type`，各自绑定一本账、各自过账并在同一 Finance 本地事务中原子提交。

**技术栈：** Markdown 权威设计文档、Node.js ESM PDManer 生成器、`node:test` 模型/场景契约测试、PDManer 4.9.3 JSON、Requirements Site TypeScript/ESM 内容测试。

---

## 已冻结的实施边界

- 仅 `entity_source_type=PARTNER` 的交易要求 Platform Service Charge 和 Finance Entity；`PLATFORM`、`CLIENT` 来源交易保持 `finance_entity_id=NULL`，除非未来有独立批准的会计政策。
- `t_finance_entity.legal_party_id` 映射 CCT Partner Agreement 中唯一的 `PLATFORM_SIGNATORY` Party，不复制 Party 法定资料，不建立跨库外键。
- `PLATFORM_SERVICE_CHARGE` 是 CCT 第三类 Commercial Term，与 `PARTNER_SHARE` 并列、都归属 Local Partner 并引用同一 Partner Agreement；它不是 Client Fee、Partner Share 或 Provider Cost 的残差。
- V1 复用 `commercial_term_component` 的百分比/固定金额结构；百分比基数必须来自受控 Calculation Base Catalog 并逐值冻结，固定金额按组件币种，允许百分比与固定组件组合。现实场景使用 `GROSS_PAYMENT_AMOUNT`，但本计划不把它写死为唯一合法基数；Finance 负责计算、舍入和逐值冻结。
- Commerce 只冻结 CCT Scope ID 和平台法律 Party ID，不认识 Finance 内部 `finance_entity_id`。
- Finance 不新增 `t_platform_service_charge`、发票、Open Item、Receipt Allocation、通用 Intercompany Transfer、Consolidation 或 Elimination 表。
- Journal 仍是唯一不可变会计事实；Partner OE Book 和 Finance Entity Book 绝不共用一条 Journal Entry。
- 本计划只同步 Trial/Close 的“按单本 Ledger Book”主体语义，不顺带实施尚未单独确认的 Trial Balance Run 生命周期、Hash、Waiver、Projection Rebuild 和 Final Trial 门禁收紧。

## 目标事实流

```text
CCT Partner Agreement
  ├─ exactly one PLATFORM_SIGNATORY Party
  ├─ PARTNER_SHARE Plan / Version / Scope / Components
  └─ PLATFORM_SERVICE_CHARGE Plan / Version / Scope / Components
                         │ CCT-I13 V5
                         ▼
Commerce immutable Order Context
  ├─ platform_service_charge_scope_id
  └─ platform_service_party_id
                         │ COM-I04 + CCT-I14
                         ▼
Finance Transaction Economics
  ├─ operating_entity_id     -> Partner OE Book
  ├─ finance_entity_id       -> Platform Finance Entity Book
  └─ PLATFORM_SERVICE_CHARGE Component
              ┌──────────────┴──────────────┐
              ▼                             ▼
Partner instruction/journal          Platform instruction/journal
Dr Service Fee Expense               Dr Service Receivable
Cr Payable to Finance Entity         Cr Service Revenue
```

### Task 1: 收紧并同步书面决策边界

**Files:**

- Modify: `agent-docs/plans/2026-08-26-finance-entity-dual-ledger-design.md`
- Create: `agent-docs/plans/2026-08-26-finance-entity-dual-ledger-implementation-plan.md`（本文件，实施过程中只更新状态和实算结果）

**Step 1: 先写设计一致性断言清单**

在本实施计划的执行记录中逐项确认：

```text
PARTNER source => Partner OE + Partner Agreement + Platform Service Charge Scope
                + Platform Service Party + active Finance Entity
non-PARTNER source => Platform Service Charge Scope/Party/Finance Entity all NULL
one charge => two instructions => two journals => two individually balanced books
```

**Step 2: 修正文档中过宽的不变量**

把设计文档“每一条交易必须具有 Partner OE 和明确 Finance Entity”改为只约束 Partner 来源交易，并补充非 Partner 来源三字段成组为空。不得把 Finance Entity 变成所有交易的默认利润主体。

**Step 3: 固化本计划新增的合同决定**

在设计文档中补充：

- CCT `PLATFORM_SERVICE_CHARGE` 第三 Term Type；
- Partner Agreement 必须恰有一个 `PLATFORM_SIGNATORY` 才能激活该类条款；
- Commerce 冻结 Scope ID + Party ID；
- Finance 通过 Party ID 精确映射本地 Finance Entity；
- 百分比基数必须来自受控 Calculation Base Catalog 并逐值冻结；`GROSS_PAYMENT_AMOUNT` 只是本计划现实场景使用的示例，Accounting Rule 不重新计算金额。

**Step 4: 文档静态检查**

Run:

```bash
git diff --check -- agent-docs/plans/2026-08-26-finance-entity-dual-ledger-design.md agent-docs/plans/2026-08-26-finance-entity-dual-ledger-implementation-plan.md
```

Expected: exit 0；没有尾随空格或冲突标记。

**Checkpoint:** 只展示两份计划文档的 diff；不修改模型。

### Task 2: CCT 增加 `PLATFORM_SERVICE_CHARGE` 商业条款契约

**Files:**

- Modify: `scripts/pdmaner/client-contract-model.test.mjs`
- Modify: `scripts/pdmaner/client-contract-scenario-doc.test.mjs`
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Regenerate: `client-contract-service/docs/database/client-contract.pdma.json`

**Step 1: 先写失败的模型契约测试**

将关键预期改成：

```js
assert.deepEqual(enumValues("commercial_term_plan.term_type"), [
  "CLIENT_PRICING",
  "PARTNER_SHARE",
  "PLATFORM_SERVICE_CHARGE",
]);
assert.deepEqual(
  expectedUniqueIndexes.commercial_term_plan,
  ["term_type,plan_code", "client_id", "local_partner_id,term_type"],
);
assert.ok(expectedRelations.has(
  "commercial_term_plan.local_partner_id->local_partner.local_partner_id|n:1",
));
```

同时增加注释/契约断言，要求：

- `CLIENT_PRICING` 仅 `client_id` 非空；
- `PARTNER_SHARE`、`PLATFORM_SERVICE_CHARGE` 仅 `local_partner_id` 非空；
- 两类 Partner Term 必须引用同一 Local Partner 的 Partner Agreement；
- `PLATFORM_SERVICE_CHARGE` 的 Partner Agreement 恰有一个 `PLATFORM_SIGNATORY`；
- 百分比组件必须使用批准的 Calculation Base Catalog 值，固定组件沿用现有币种/上下限矩阵；测试至少覆盖 `GROSS_PAYMENT_AMOUNT`，不得接受残差或未登记基数。

**Step 2: 运行测试并确认按预期失败**

Run:

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs
```

Expected: FAIL，失败点只来自第三 Term Type、Partner 复合唯一键、关系基数、V5 契约和缺失现实场景。

**Step 3: 修改 CCT 生成源和字段元数据**

实现以下物理变化：

```text
t_commercial_term_plan.term_type
  + PLATFORM_SERVICE_CHARGE

DROP logical model UK: uk_comm_term_partner(local_partner_id)
ADD  logical model UK: uk_comm_term_partner(local_partner_id, term_type)

commercial_term_plan.local_partner_id -> local_partner.local_partner_id
  relationship cardinality: 1:1 -> n:1
```

字段数和表数不因本任务变化；唯一索引和关系总数由生成器实算，不手填预测值。

**Step 4: 将 CCT-I13 升级为 V5 Bundle**

把当前 Domain 升级为：

```text
CODE-MOR:CCT:COMMERCIAL_CONTEXT:5
```

V5 在 V4 全部字段基础上新增固定位置的：

```json
{
  "platformServicePartyId": "<BIGINT string or null>",
  "platformServiceCharge": "<full Plan/Version/Scope/Components object or null>"
}
```

规则：

- PARTNER 来源必须返回非空 Party 和完整 Service Charge；
- 非 PARTNER 来源两个属性都必须存在并编码为 JSON `null`；
- Component 仍按 `componentCode/termComponentId` 稳定排序；
- 不增加 Version/Scope/Component Hash；
- V4/V5 不得混算。

**Step 5: 收紧 CCT-I13/CCT-I14 Fail-Closed 语义**

- CCT-I13 对 Partner 来源同时解析 Partner Share 与 Platform Service Charge；任一 Plan/Version/Scope/Component 缺失、并列、越过 Agreement 有效期，或 Partner Agreement 的 `PLATFORM_SIGNATORY` 为零/多条时，整个 Bundle Fail Closed。
- CCT-I14 继续使用 `term_scope_id + agreement_operating_entity_id + context_as_of_time`，但当 `term_type=PLATFORM_SERVICE_CHARGE` 时额外返回该 Agreement 唯一的 `platform_service_party_id`；它仍不接收或验证 V5 Hash。
- 不增加专用接口、不在 Scope 表重复保存 Party ID。

**Step 6: 增加现实场景**

在 CCT 场景文档加入一笔 Partner OE 交易，明确：

```text
Gross Payment = USD 100.00
Platform Service Charge = 0.40% + USD 0.00 = USD 0.40
Partner Agreement PLATFORM_SIGNATORY = platform_service_party_id
```

同时覆盖零签约方、多签约方、Scope 并列、非 Partner 交易错误携带 Service Charge 四个失败边界。

**Step 7: 生成 CCT 模型并验证**

Run:

```bash
node scripts/pdmaner/generate-client-contract-model.mjs
node scripts/pdmaner/generate-client-contract-model.mjs --check
node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs
```

Expected: 全部 PASS；生成器 `--check` 不重写文件；把实算表/字段/唯一索引/普通索引/关系/1:1/Diagram Group 数记录到本计划“生成后基线”。

**Checkpoint:** 复核 CCT 只有第三 Term Type、一个索引组成和一条关系基数变化，没有新增表、Hash、Version 或 Party 副本。

### Task 3: Commerce 冻结平台服务费 Scope 和法律 Party

**Files:**

- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Regenerate: `commerce-service/docs/database/commerce.pdma.json`

**Step 1: 先写失败的 Order Context 契约测试**

在 `t_order_context` 的有序字段中，把两列插在现有 Partner Share Scope 后：

```text
platform_service_charge_scope_id BIGINT N
platform_service_party_id BIGINT N
```

增加断言：两列均可空、没有本地关系、没有 Finance Entity ID、字段注释明确其来自同一次 CCT-I13 V5 Bundle。

**Step 2: 运行测试并确认失败**

Run:

```bash
node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
```

Expected: FAIL，原因仅为缺少两列、V5 文案和 Partner 条件场景。

**Step 3: 修改 Commerce 模型生成源**

更新 generator schema 与 field metadata；保持 `t_order_context` 一 Order 一行、不可变。由于条件依赖父表 `t_order.entity_source_type`，不伪造单表 SQL CHECK，由 Order 创建本地事务执行以下跨表不变量：

```text
order.entity_source_type = PARTNER
  => local_partner_id, partner_share_scope_id,
     platform_service_charge_scope_id, platform_service_party_id all NOT NULL

order.entity_source_type <> PARTNER
  => local_partner_id, partner_share_scope_id,
     platform_service_charge_scope_id, platform_service_party_id all NULL
```

**Step 4: 更新 COM-I04 与现实场景**

- COM-I04 返回这两个新冻结值以及 V5 `commercial_context_hash`；
- Risk 的最小 Order Preparation Context 如需审计关联可携带 V5 Hash，但不得消费 Finance Entity ID；
- 场景覆盖 Partner 成功、非 Partner 为 NULL、缺一字段拒绝创建 Order；
- Commerce 不保存 CCT Term Component 或 Finance Entity 映射。

**Step 5: 生成 Commerce 模型并验证**

Run:

```bash
node scripts/pdmaner/generate-commerce-model.mjs
node scripts/pdmaner/generate-commerce-model.mjs --check
node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
```

Expected: 全部 PASS；记录生成后实算基线，不手工推算模型摘要。

**Checkpoint:** 复核 Commerce 只冻结 CCT 外部稳定 ID，不新增 Platform Service Charge 金额、Finance Entity 或会计状态。

### Task 4: Finance 增加本地 Finance Entity 并泛化账簿所有者

**Files:**

- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/finance/manifests/01-accounting-foundation.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance/manifest.mjs`
- Regenerate later: `finance-service/docs/database/finance.pdma.json`

**Step 1: 先写失败的 Foundation 模型测试**

测试要求新增：

```text
t_finance_entity
  id BIGINT PK
  entity_code VARCHAR(64) NN
  legal_party_id BIGINT NN
  display_name VARCHAR(256) NN
  status VARCHAR(64) NN {ACTIVE,CLOSED}
  create_time DATETIME(6) NN
  update_time DATETIME(6) NN
  uk_fin_entity_code(entity_code)
  uk_fin_entity_party(legal_party_id)
```

以及：

```text
t_ledger_book: operating_entity_id -> entity_type + entity_id
uk_ledger_book_entity(entity_type,entity_id,currency)
entity_type {OPERATING_ENTITY,FINANCE_ENTITY}

t_fiscal_calendar: operating_entity_id -> entity_type + entity_id
uk_fiscal_cal(entity_type,entity_id)

t_account.owner_type + FINANCE_ENTITY
ck_account_owner_bucket: FINANCE_ENTITY only allows NONE
```

断言 `t_finance_entity` 不含 Version、Hash、History、Approval、Active Slot，不与 CCT 建物理关系；`t_ledger_book.entity_id` 不建多态外键。

**Step 2: 运行 Finance 模型测试并确认失败**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs
```

Expected: FAIL，原因只来自新表、字段、索引、枚举、CHECK、表清单和计数尚未实现。

**Step 3: 实现 Foundation Manifest 和 Oracle**

- 把 `t_finance_entity` 放入 `ledger_and_close` Diagram Group；
- 在 `APPROVED_TABLES` 中置于 `t_chart_account` 与 `t_ledger_book` 之间；
- 保持 `t_finance_entity` 为受控人工初始化 Root；
- 增加主体解析应用不变量：OPERATING_ENTITY 查 CCT，FINANCE_ENTITY 查本地 ACTIVE 行；
- `entity_type/entity_id` 创建后不可修改，`CLOSED` 不可恢复 ACTIVE；
- 账户的 Book-owning Revenue/Expense/Equity Owner 必须与 Book 主体相同。

**Step 4: 增加四个叶子科目与 Owner 规则文档测试**

模型不把科目代码做成字段枚举，但测试和场景文档必须要求初始化目录包含：

```text
PLATFORM_SERVICE_RECEIVABLE   ASSET     DEBIT
PLATFORM_SERVICE_REVENUE      REVENUE   CREDIT
PLATFORM_SERVICE_FEE_EXPENSE  EXPENSE   DEBIT
PLATFORM_SERVICE_PAYABLE      LIABILITY CREDIT
```

账户 Owner：Partner Expense=Partner OE，Partner Payable=Finance Entity，Platform Receivable=Partner OE，Platform Revenue=Finance Entity；全部 `balance_bucket=NONE`。

**Step 5: 运行局部测试**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs
```

Expected: Foundation 相关断言通过；总计/摘要断言暂时仍可能因模型尚未重新生成而失败，必须明确记录，不能放宽测试。

**Checkpoint:** 复核没有 `t_accounting_entity`、双可空 Owner FK 或伪造 Operating Entity。

### Task 5: 逐表泛化 Finance 法定主体范围，保留纯 OE 交易对象

**Files:**

- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/finance/manifests/02-tax-economics-cost.mjs`
- Modify: `scripts/pdmaner/finance/manifests/04-balance-funding.mjs`
- Modify: `scripts/pdmaner/finance/manifests/05-provider-cash-recon.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`

**Step 1: 先写失败的逐表保留/泛化测试**

必须改为 `entity_type + entity_id` 的 Root：

```text
t_tax_registration
t_cash_account
t_cash_account_binding
t_funding_position
t_funding_commitment
t_recon_exception
```

已经由父链或 Book 唯一决定主体、不重复加列：

```text
t_accounting_period          <- fiscal_calendar
t_tax_period/return/payment  <- tax_registration
t_cash_account_binding_ver   <- cash_account_binding
t_bank_statement/item        <- cash_account
t_trial_balance_*            <- ledger_book
t_period_close*              <- ledger_book/calendar chain
```

必须继续使用 `operating_entity_id` 的纯交易对象至少包括：

```text
t_tax_snapshot / t_fiscal_record
t_provider_account
t_balance_lot / t_reserve_case / t_restricted_fund / t_recovery_case
t_settlement_profile / t_settlement / payout/payment chain
t_finance_restriction_proj
```

`t_provider_open_item` 与 `t_provider_debit_item` 不重复保存 `operating_entity_id`，继续通过 `provider_account_id -> t_provider_account.operating_entity_id` 稳定父链确定交易 OE，并与各自 `transaction_economics_id -> t_transaction_economics.operating_entity_id` 逐值校验；两张子表也不得新增 `entity_type/entity_id`。

`t_refund_funding_reservation` 同样不重复保存主体列；它通过一对一 `funding_commitment_id` 继承主体，而 `ck_funding_commitment_identity` 强制 REFUND Commitment 只能属于 `OPERATING_ENTITY`。

**Step 2: 运行测试并确认失败**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs
```

Expected: FAIL，明确列出仍使用旧 `operating_entity_id` 的六个 Root 或错误泛化的纯 OE 表。

**Step 3: 修改六个 Root 的字段和索引**

统一命名：

```text
entity_type {OPERATING_ENTITY,FINANCE_ENTITY} NN
entity_id BIGINT NN
```

对应唯一键/访问索引把旧 `operating_entity_id` 原位替换为 `entity_type,entity_id`，例如：

```text
uk_tax_registration(entity_type,entity_id,authority_id,tax_type,registration_kind,effective_from)
uk_cash_blind_index(entity_type,entity_id,account_blind_index_key_version,account_blind_index)
uk_cash_binding(entity_type,entity_id,currency,account_purpose)
idx_recon_exception_entity(entity_type,entity_id,currency,status)
```

不为多态字段建立条件外键。

**Step 4: 收紧 Funding CHECK**

- 所有 Refund、Provider、Balance Lot、Restricted Fund、Settlement 位置/承诺必须 `entity_type=OPERATING_ENTITY`；
- `BANK_CASH` 允许两类主体，并要求 `owner_type=entity_type AND owner_id=entity_id`；
- Funding Position Owner enum 增加 `FINANCE_ENTITY` 仅服务上述 BANK_CASH；
- `TAX_PAYABLE` Position 和 `TAX_PAYMENT` Commitment 可属于 OE 或 Finance Entity，但 `owner_type` 仍是 `TAX_AUTHORITY`，法律主体由 `entity_type/entity_id` 单独表达；
- 一个 Allocation 的 Position 主体、币种必须等于 Commitment 主体、币种。

**Step 5: 收紧 Tax/Cash/Recon 父链一致性**

- Ledger Book、Fiscal Calendar、Tax Registration 和税款支付使用的 Cash Binding 必须主体一致；
- Cash Binding Version 引用的 Cash Account 必须主体和币种一致；
- Recon Exception 主体从其 Source/Bank/Cash 父链确定，不允许人工填入另一主体；
- 原交易 Fiscal Record/Snapshot 的 Seller 仍是 Operating Entity，不因平台服务费而改成 Finance Entity；平台服务发票/税务凭证仍属后续用例。

**Step 6: 运行局部测试**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs
```

Expected: 主体字段、索引、CHECK 和保留清单通过。

**Checkpoint:** 展示所有被删除/新增的 `operating_entity_id` 路径，逐项证明没有机械全局替换。

### Task 6: Finance 冻结服务费经济组件并支持双 Instruction/Journal

**Files:**

- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/finance-scenario-doc.test.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/finance/manifests/02-tax-economics-cost.mjs`
- Modify: `scripts/pdmaner/finance/manifests/03-accounting-journal.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Regenerate: `finance-service/docs/database/finance.pdma.json`

**Step 1: 先写失败的 Economics/Journal 模型测试**

目标字段和枚举：

```text
t_transaction_economics.finance_entity_id BIGINT N
t_transaction_economic_component.source_rule_type
  + PLATFORM_SERVICE_CHARGE
t_transaction_economic_component.party_type
  + FINANCE_ENTITY
t_accounting_instruction.ledger_book_id BIGINT NN
  -> t_ledger_book.id n:1
```

增加 CHECK 契约：

```text
local_partner_id, partner_agreement_oe_id, finance_entity_id
  are all NULL or all NOT NULL

source_rule_type=PLATFORM_SERVICE_CHARGE
  => tax_line_id IS NULL
     source_rule_scope_id IS NOT NULL
     source_rule_component_id IS NOT NULL
     source_rule_content_hash IS NULL
     party_type='FINANCE_ENTITY'
     party_id=transaction_economics.finance_entity_id (application invariant)
```

`finance_entity_id` 不建跨聚合重复关系；Component 通过现有 `transaction_economics_id` 父链验证。

**Step 2: 运行测试并确认失败**

Run:

```bash
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: FAIL，原因仅为新字段、枚举、关系、CHECK 和双账场景尚未实现。

**Step 3: 实现 Economics 冻结规则**

处理 Partner 来源 PAYMENT 时：

1. COM-I04 返回冻结的 Platform Service Charge Scope 和 Party；
2. Finance 调 CCT-I14 精确读取该 Scope；
3. 返回 `term_type` 必须为 `PLATFORM_SERVICE_CHARGE`，Owner 必须是交易 Local Partner，Agreement/OE/Scope 维度必须与 Order Context 一致；
4. CCT-I14 返回的 `platform_service_party_id` 必须等于 Commerce 冻结值；
5. 以 `t_finance_entity.legal_party_id` 唯一查到一条 ACTIVE Finance Entity；
6. 将本地 `finance_entity_id` 冻结到 Economics Header，将 Version/Scope/Component 和计算参数冻结到 Component；
7. 缺失、重复、CLOSED 或任一不一致均永久 Fail Closed，不创建 Economics/Instruction/Journal。

退款、更正和 Cost Adjustment 沿 `parent_economics_id + component link` 继承原 `finance_entity_id` 和规则证据，不回读当前 Finance Entity 或当前 CCT 条款。

**Step 4: 实现两个稳定 Instruction Type**

使用：

```text
PLATFORM_SERVICE_CHARGE_PARTNER
PLATFORM_SERVICE_CHARGE_PLATFORM
```

保留现有幂等唯一键：

```text
uk_instr_source(source_system,source_event_key,instruction_type)
```

两类 Instruction 共享同一 Transaction Economics，但 `ledger_book_id` 不同；Instruction Line 全部账户必须属于 Header 的 Ledger Book，Journal Entry 的 `ledger_book_id` 也必须相同。

**Step 5: 固化四条 Accounting Rule Line 场景**

```text
Partner instruction:
  Dr PLATFORM_SERVICE_FEE_EXPENSE owner=PARTNER_OPERATING_ENTITY
  Cr PLATFORM_SERVICE_PAYABLE     owner=PLATFORM_FINANCE_ENTITY

Platform instruction:
  Dr PLATFORM_SERVICE_RECEIVABLE  owner=PARTNER_OPERATING_ENTITY
  Cr PLATFORM_SERVICE_REVENUE     owner=PLATFORM_FINANCE_ENTITY
```

Accounting Rule 只读取已算出的 `PLATFORM_SERVICE_CHARGE` Component Amount，不保存费率、不做残差、不跨 Book 配平。

**Step 6: 固化本地原子提交和锁顺序**

场景文档必须写明：

```text
sort and lock ledger_book.id ASC
lock accounting period and book sequence for each book
sort and lock account.id ASC within each book
validate both instructions independently balanced
insert both instructions, journals, lines, balance projections, compensation events
commit once; any failure rolls back all
```

双 Journal 各自取得本 Book 的严格递增 Sequence；不能用一侧成功、另一侧补偿的最终一致性方案替代同库原子事务。

**Step 7: 增加真实记账与失败场景**

至少覆盖：

- USD 100.00、服务费 USD 0.40 的四条分录；
- 非 Partner 来源不生成服务费；
- Finance Entity 不存在/重复/CLOSED；
- Party 不一致；
- 一侧 Book/Period/Account 不可过账时全部回滚；
- 相同 Event Key + 相同 Payload 幂等返回，相同 Key + 不同金额/主体冲突；
- Refund 反向效果不改原 Economics/Journal；
- 两本账分别 Trial/Close/Profit Carry Forward。

**Step 8: 生成 Finance 模型并验证专项测试**

Run:

```bash
node scripts/pdmaner/finance/generate-finance-model.mjs
node scripts/pdmaner/finance/generate-finance-model.mjs --check
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: Finance 专项全部 PASS；更新 `EXPECTED_TOTALS`、Template/Model/Structural Digest 和 Oracle 为生成后的实算值，不凭手工估算修改测试。

**Checkpoint:** 复核新增表只有 `t_finance_entity`，服务费没有独立生命周期表，两条 Journal 各自平衡且共同原子提交。

### Task 7: 同步跨系统权威文档、Requirements 和全局序列

**Files:**

- Modify: `AGENTS.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`

**Step 1: 更新当前模型基线和权威引用**

从三个生成器的实际输出复制精确数量和摘要；不得预先写 `+1 table/+N fields` 的猜测值。Finance 权威列表加入本设计和实施计划，历史 121 表基线标记为被新生成结果取代。

**Step 2: 更新接口用例**

- CCT-I13：V5、第三 Term、唯一 Platform Signatory Party；
- CCT-I14：Service Charge Scope 的 Party 返回和单 Scope 校验边界；
- COM-03：Order Context 新增两字段及 Partner 条件不变量；
- COM-I04：向 Finance 返回两字段；
- Finance 交易经济计算：精确映射 Finance Entity，生成双 Instruction/Journal；
- 不把 Finance 内部 ID 暴露给 Commerce、Payment、Risk 或外部 API。

**Step 3: 更新全局序列**

在 Checkout 与 PaymentSucceeded→Finance 序列中加入：

```text
CCT-I13 V5 returns Platform Service Scope + Party for PARTNER source
Commerce freezes both external IDs
Finance reads COM-I04 and exact CCT-I14 scope
Finance maps Party -> ACTIVE Finance Entity
Finance calculates one charge component
Finance atomically posts two balanced journals
```

Refund 序列写明沿原 Component Link 继承，不重新解析当前条款。

**Step 4: 全局术语扫描**

Run:

```bash
rg -n "V4|CODE-MOR:CCT:COMMERCIAL_CONTEXT:4|Pricing/Share|Client Pricing 与.*Partner Share|121 tables|121 表" AGENTS.md agent-docs requirements-site scripts/pdmaner client-contract-service commerce-service finance-service
```

Expected: 所有“当前权威”引用已更新到 V5/第三 Term/新 Finance 基线；历史计划中的 V4 和旧计数只在明确标注“历史”的段落保留。

**Step 5: Requirements 验证**

Run:

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
```

Expected: 全部 PASS；若存在与本变更无关的既有失败，保留完整命令、失败测试名和证据，不修改无关逻辑掩盖失败。

**Checkpoint:** 审查一张跨系统字段所有权表，确认无跨库读取和重复事实：

| 事实 | 权威系统 | 下游只冻结 |
|---|---|---|
| Partner Agreement / Platform Signatory / Term | CCT | Commerce Scope ID + Party ID |
| Order 时点商业上下文 | Commerce | Finance 精确读取输入 |
| Finance Entity 映射 | Finance | 不对外暴露 |
| 服务费金额与会计事实 | Finance | Component + 双 Journal |

### Task 8: 全量生成、摘要与人工门禁

**Files:**

- Verify: `client-contract-service/docs/database/client-contract.pdma.json`
- Verify: `commerce-service/docs/database/commerce.pdma.json`
- Verify: `finance-service/docs/database/finance.pdma.json`
- Modify: `agent-docs/plans/2026-08-26-finance-entity-dual-ledger-implementation-plan.md`（仅填写实算基线和验证结果）

**Step 1: 确认三套生成器确定性**

Run:

```bash
node scripts/pdmaner/generate-client-contract-model.mjs --check
node scripts/pdmaner/generate-commerce-model.mjs --check
node scripts/pdmaner/finance/generate-finance-model.mjs --check
```

Expected: 三个命令 exit 0，无模型 stale。

**Step 2: 运行三套专项测试**

Run:

```bash
node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs
node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs
node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs
```

Expected: 全部 PASS。

**Step 3: 运行全量 PDManer 测试**

Run:

```bash
node --test scripts/pdmaner/*.test.mjs
```

Expected: 本次涉及的 CCT、Commerce、Finance 全部 PASS。若仍有既有 Commerce stale 之外的失败，不能宣称完成；先按失败归属诊断。若此前 3 个 Commerce stale 已被本次 Commerce 重生成自然消除，应记录新的全量通过数。

**Step 4: 运行 Requirements 与 diff 检查**

Run:

```bash
npm --prefix requirements-site test
npm --prefix requirements-site run lint
git diff --check
git status --short
```

Expected: tests/lint/diff-check PASS；`git status` 只显示用户原有改动和本计划明确范围内的文件，不出现 Java、Migration、DDL、部署或凭据文件。

**Step 5: 记录生成后基线**

实施完成时在本任务执行记录中原样附上三套生成器的完整统计输出和测试汇总，不提前预测，也不在计划中保留待替换数字：

```text
For each model, record: tables, fields, unique indexes, access indexes,
relations, 1:1 relations, diagram groups, template digest, model digest,
and structural digest.

For each suite, record: command, passed count, failed count, and exit code.
```

**Step 6: PDManer 4.9.3 人工兼容检查**

在应用内逐套打开三份模型并检查：

- 无 JSON 导入错误或丢失实体；
- `t_finance_entity` 位于“账簿、账户与关账”图组；
- 三套新字段、复合唯一键、关系基数和字段注释正确；
- CCT/Commerce/Finance Diagram Group 不出现跨库关系线；
- 保存后再次运行三个 `--check`，确认应用未产生非预期结构漂移。

Expected: 人工门禁完成前，状态只能写“自动验证通过，PDManer 4.9.3 人工检查待完成”，不能宣称物理模型最终兼容。

## 完成定义

- 用户已确认的 Finance Entity、Partner OE、Platform Service Charge 和双账簿语义在 CCT、Commerce、Finance 三套模型与文档中一致；
- CCT 第三 Term 可由合同和唯一 Platform Signatory 精确证明；
- Commerce 冻结准确外部 ID，不拥有会计主体或金额；
- Finance 可唯一映射本地 Finance Entity，并在同库事务写入两条独立平衡 Journal；
- 非 Partner 交易没有被强制分配 Finance Entity；
- 所有生成器、专项测试、Requirements、全量 PDManer 和摘要校验达到上述门禁；
- 没有创建 Java、Migration、DDL、物理数据库、部署资源、分支或 Git Commit。

## 2026-08-26 执行记录

### 生成后物理基线

| 模型 | 表 | 字段 | 唯一索引 | 访问索引 | 关系 | 其中 1:1 | Diagram Group | Template SHA-256 | Model SHA-256 | Structural SHA-256 |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|
| Client & Contract | 36 | 400 | 54 | 43 | 61 | 7 | 6 | `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540` | `791dfe3ba8ea28e5a7e651d971b2f27a0b066adc6b2c676f3b8c189edb4192f3` | `7eabf67bf0670a9873e34a5700e0a5267c8eb0e1fe4fc4abcd62752ac67db0c5` |
| Commerce | 14 | 224 | 21 | 11 | 14 | 8 | 4 | `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540` | `829dfe2c1a7aacbd01339495ebdbaabd55a3fad8146b9416095c772059c9f23c` | `0af9dd92c9f99b783fd043bfd91b1ac19d04e60897f7603a21ad8eeb361b6f78` |
| Finance | 122 | 1,486 | 211 | 188 | 211 | 41 | 8 | `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540` | `decffa403e531af3aafc8ee0c50dc884395e55d18b4ec1ddb793f701b82df09b` | `efc6187564bd14c169a1e4f8cbac05b4c75fc8346e8609b091fe388e08ea9caa` |

### 自动验证结果

| 命令 | 通过 | 失败 | Exit |
|---|---:|---:|---:|
| `node scripts/pdmaner/generate-client-contract-model.mjs --check` | 生成模型无漂移 | 0 | 0 |
| `node scripts/pdmaner/generate-commerce-model.mjs --check` | 生成模型无漂移 | 0 | 0 |
| `node scripts/pdmaner/finance/generate-finance-model.mjs --check` | 生成模型无漂移；输出 122/1486/211/188/211/41/8 | 0 | 0 |
| `node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs` | 83 | 0 | 0 |
| `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs` | 19 | 0 | 0 |
| `node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs` | 20 | 0 | 0 |
| `node --test scripts/pdmaner/*.test.mjs` | 142 | 0 | 0 |
| `npm --prefix requirements-site test` | 83 | 0 | 0 |
| `npm --prefix requirements-site run lint` | lint 通过 | 0 | 0 |
| `git diff --check` | whitespace check 通过 | 0 | 0 |

Requirements 验证在隔离工作树中复用了主工作区已安装的 `requirements-site/node_modules`；临时符号链接在验证后已删除，没有安装依赖或改写 Lockfile。

### 边界复核

- CCT 权威拥有 Partner Agreement、唯一 Platform Signatory Party 与显式 Platform Service Charge；当前 Wire 为 V5。
- Commerce 只冻结 `platform_service_charge_scope_id + platform_service_party_id`，不保存 `finance_entity_id`、服务费金额或会计状态。
- Finance 以手工维护的 `t_finance_entity` 映射平台法律 Party；`t_ledger_book` 使用 `entity_type + entity_id`，不建立多态或跨库外键。
- 同一服务费 Component 原子生成 `PLATFORM_SERVICE_CHARGE_PARTNER` 与 `PLATFORM_SERVICE_CHARGE_PLATFORM` 两条指令，在 Partner OE 与平台 Finance Entity 两本账各写一条独立平衡 Journal；任一失败全部回滚。
- 非 Partner 交易不要求 Finance Entity；纯交易执行根仍保留 `operating_entity_id`，只有法律主体根使用 typed subject。
- 未创建 Java、Migration、DDL、物理数据库、部署资源、分支或 Git Commit。

### 未完成的人工门禁

PDManer 4.9.3 应用内兼容检查仍待人工完成。在三套模型逐一打开、检查并保存，再重新通过三个生成器 `--check` 前，只能认定“自动验证通过”，不能认定应用内最终兼容。

# Finance Account Balance And Contract Maturity Simplification Implementation Plan

> **已被后续设计局部取代（2026-08-27）：** 本文所有依赖 Funding Position、Funding Commitment 或 Funding Allocation 的 Settlement/Tax Payment 描述，已由 [Finance 通用 Funding 模块移除设计](./2026-08-27-finance-funding-module-removal-design.md) 取代。统一 Account、T+N 成熟和退款成功后入账决定仍然有效。

> **后续替代说明（2026-08-27）：** 本文关于活动 `SOURCE_COVER` 阻断成熟、退款释放后补成熟和退款 Funding Position 的部分，已由 [Finance 退款 Funding 移除实施记录](./2026-08-27-finance-refund-funding-removal-implementation-plan.md) 替代。现行语义是未决退款不阻断到期，Finance 只在 `RefundSucceeded` 后按 PENDING → AVAILABLE → Recovery 入账。Account 合并和 T+N 工作日计算决策继续有效。

> **For agentic workers:** Use TDD and verification-before-completion. This plan is design-artifact work only: do not create Java, migrations, physical databases, deployments, branches or commits.

**Goal:** 删除与统一 `t_account` 重复表达余额的 Balance Lot 和通用 Availability 模型，让 Client 合同约定的 T+N 工作日直接控制 `CLIENT_PAYABLE` 从 `PENDING` 到 `AVAILABLE`；Settlement 继续用 Account、Journal 与 Funding 完成可结算余额锁定和付款。

**Architecture:** CCT 在不可变的 CLIENT Agreement–Operating Entity 关系上保存结算工作日数、节假日日历、IANA 时区和本地 Cutoff。Finance 在 PAYMENT Transaction Economics 中冻结 PaymentSucceeded 发生时间和计算后的 Client 可用时间。到期处理通过现有幂等 Accounting Instruction 生成 `PENDING -> AVAILABLE` Journal；Provider 是否已结算不阻断合同到期。Settlement Candidate 冻结唯一 AVAILABLE Account 及其余额版本，Promotion 通过 Journal 转入 LOCKED 并用 Account-backed Funding Position 原子占用。

**Tech Stack:** Node.js ESM、`node:test`、PDManer 4.9.3 JSON、Finance/CCT 生成器与独立 Oracle、Markdown/TypeScript 需求目录。

**Status:** 2026-08-27 设计产物实施完成并通过自动验证；PDManer 4.9.3 应用内人工兼容检查仍是最终门禁。

## Confirmed Boundaries

- `T` 是 Payment 发布的 `PaymentSucceeded` 资金成功时间；仅 SALE 成功或累计全额 CAPTURE 成功产生该事件。
- `T+N` 使用合同指定时区和 Cutoff 确定业务日期，再按合同指定节假日日历增加 `N` 个工作日；周末和日历节假日不计数，结果按 FOLLOWING 规则落在工作日 Cutoff。
- Client 可用时间在 PAYMENT Economics 创建时计算并冻结；后续合同变更、日历变更或事件重投不得重算历史交易。
- Provider T+2 尚未到账不阻止 Client T+1 到期；Provider Receivable 是平台资产，Client Payable 是平台负债，时间差造成的流动性缺口由平台承担。
- Risk 限制、Reserve、最低出款金额和实际付款调度不改变合同成熟时间；它们只能限制或锁定已经 AVAILABLE 的余额。
- 到期前退款按 Component Link 从原 Client 应付组件扣减；退款仍 UNKNOWN 时还要扣除 ACTIVE `SOURCE_COVER` Allocation，相关金额留在 PENDING，FAILED/CANCELLED 释放后若已经到期立即补成熟，SUCCEEDED 则消费。到期任务与退款处理锁定同一 Economics/Component Link/Account/Funding Position 事务范围，防止重复移动。
- 当前只为 CLIENT Agreement 定义合同成熟条款；Local Partner Share 在 V1 入账时直接进入 AVAILABLE，付款周期仍由 Settlement Profile 控制。
- 不新增 Maturity、Availability、Balance Lot 或 Settlement Line 表，不新增通用 Policy/Condition/Hash。

## Target Model Shape

- CCT 保持 36 表；`agreement_operating_entity` 增加 4 个 CLIENT-only 字段，最终为 404 字段、54 个唯一索引、43 个访问索引和 61 条本库关系。
- Finance 删除 10 表：`t_balance_lot`、`t_balance_lot_bucket`、`t_balance_lot_move`、6 张 `t_availability_*`、`t_settlement_line`。
- `t_transaction_economics` 增加 `payment_succeeded_time`、`client_available_time`；仅 PAYMENT 必填，其他类型为空。
- `t_settlement_candidate` 删除 `eligible_lot_count`、`lot_set_hash`，增加 `available_account_id`、`available_balance_version`，并引用 `t_account`。
- Funding Position 用 `position_type=OWNER_PAYABLE + source_type=ACCOUNT` 表达 Client/Local Partner 应付账户容量；应用事务校验 Account 的 Owner、主体、币种、科目和余额桶。
- Finance 最终为 108 表、1,357 字段、186 个唯一索引、177 个访问索引、187 条本库关系、35 条 1:1 关系和 8 个 Diagram Groups。
- Finance 模型 SHA-256 为 `953c970a01145cb802778e82d03fa6291e701c7143d1b3c05c89656382963ead`，独立结构 SHA-256 为 `7f2a259296eae17b6c6b6575d15f0128c5ed0152ed1de43b7143d69599d02334`；共享模板摘要保持 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- `SOURCE_COVER` Allocation 通过 `source_component_id` 精确绑定原 PAYMENT 的 Client Principal Component；同一来源组件上的成功 Reversal、全部 ACTIVE SOURCE_COVER 与新申请金额合计不得超过原始入账金额。
- 首次成熟使用 `CLIENT_BALANCE_MATURED:<economics_id>`；初次成熟后退款失败或取消所释放的正残差使用 `CLIENT_BALANCE_MATURED:<economics_id>:REFUND_RELEASED:<source_cover_allocation_id>` 补成熟，避免复用已消费的幂等键。

---

### Task 1: 先写失败测试

**Files:**
- Modify: `scripts/pdmaner/finance-model.test.mjs`
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`

- [x] 冻结 10 张删除表不得恢复、旧 Lot/Availability 字段与枚举不得出现。
- [x] 冻结 PAYMENT Economics 的两个精确时间字段和类型 CHECK。
- [x] 冻结 Candidate 的 Account/Balance Version 快照与本地 Account 关系。
- [x] 冻结 Funding Position 的 `OWNER_PAYABLE + ACCOUNT` 身份矩阵。
- [x] 冻结 CLIENT Agreement–OE 的四个条款字段、物理类型、可空性和成组约束。
- [x] 运行 Finance/CCT 专项测试并确认因旧模型而 RED，而不是测试语法错误。

### Task 2: 修改 CCT 法律条款并生成模型

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Modify: `client-contract-service/docs/database/client-contract.pdma.json` (generated)

- [x] 在 Agreement–OE 关系冻结工作日数、日历代码、时区与 Cutoff 秒数。
- [x] CLIENT 四字段全部非空且边界合法；PARTNER 四字段全部为空。
- [x] 更新 CCT-I13/I14 与签署冻结语义，不增加 Version、Hash 或新表。
- [x] 生成并验证 CCT 模型。

### Task 3: 精简 Finance 余额、到期与结算模型

**Files:**
- Modify: `scripts/pdmaner/finance/manifests/02-tax-economics-cost.mjs`
- Modify: `scripts/pdmaner/finance/manifests/04-balance-funding.mjs`
- Modify: `scripts/pdmaner/finance/manifests/06-settlement.mjs`
- Modify: `scripts/pdmaner/finance/model-policy.mjs`
- Modify: `scripts/pdmaner/finance-test-oracle/schema.oracle.mjs`
- Modify: `finance-service/docs/database/finance.pdma.json` (generated)

- [x] 删除 Balance Lot、Availability 和 Settlement Line。
- [x] 增加 PAYMENT 成功时间与 Client 可用时间，扩展 Economics 类型 CHECK 和到期扫描索引。
- [x] 用 Account/Balance Version 替换 Candidate 的 Lot 集合信息。
- [x] 用 Account-backed Owner Payable Funding Position 替换 Lot Bucket 来源。
- [x] 更新 Diagram Groups、CHECK/Binding、白名单、统计与双摘要。

### Task 4: 同步现实场景与权威需求

**Files:**
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/database/scenarios/CLIENT_CONTRACT_REAL_WORLD_SCENARIOS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: current project authority/count documents found by repository search

- [x] 用现实例子说明 T+1 Client、T+2 Provider 和平台流动性缺口。
- [x] 说明提前退款并发、Risk/Reserve 与实际银行付款边界。
- [x] 删除所有当前权威文档中的 Lot/Availability/Settlement Line 现行语义。
- [x] 同步精确模型统计与本实施记录链接；历史计划保留为历史，不改写旧快照。

### Task 5: 完整验证

- [x] Finance/CCT 生成器 `--check` 无漂移。
- [x] Finance/CCT 模型与现实场景专项测试全部通过。
- [x] `npm --prefix requirements-site test` 全部通过。
- [x] 全量 `node --test scripts/pdmaner/*.test.mjs` 通过；若仅有既有无关失败，单独报告证据。
- [x] 记录最终统计、双摘要和仍待 PDManer 4.9.3 应用内人工检查的状态。

## Verification Evidence

- Finance 生成器 `--check`：通过，确认 108 表 / 1,357 字段 / 186 唯一索引 / 177 访问索引 / 187 关系 / 35 条 1:1 / 8 个 Diagram Groups。
- CCT 生成器 `--check`：通过，模型无漂移。
- Finance 模型与现实场景专项测试：25/25 通过。
- CCT 模型与现实场景专项测试：84/84 通过。
- requirements-site：构建成功，83/83 测试通过。
- 全量 PDManer：148/148 测试通过；本次验证中没有遗留 Commerce 失败。
- `git diff --check`：通过。
- 未执行 PDManer 4.9.3 应用内人工兼容检查；未创建 Java、Migration、物理数据库、部署环境、分支或 Git 提交。

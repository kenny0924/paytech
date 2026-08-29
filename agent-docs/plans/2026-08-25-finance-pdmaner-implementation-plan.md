# Finance PDManer Implementation Plan

> 历史实施记录：本计划对应 126 表首版模型，已被 [Finance 统一账户实施计划](./2026-08-26-finance-unified-account-model-implementation-plan.md)和后续[交易经济与会计规则精简](./2026-08-26-finance-transaction-economics-accounting-rule-simplification-implementation-plan.md)取代。当前模型为 121 表；后续 Agent 不得继续执行本文件中的旧结构任务或恢复双账户、双余额和旧 Economics/Accounting Rule 表。

> **For agentic workers:** 按任务逐项执行，先建立独立失败测试，再实现生成器和文档；不得从生产 Manifest 导入测试 Oracle。

**Goal:** 把已确认的 Finance 平衡型设计落成一套 126 表、可重复生成、可自动校验并可在 PDManer 4.9.3 中人工评审的第一版物理模型。

**Architecture:** 共享 PDManer 模板保持只读；Finance 生产 Manifest 按八个物理子域拆分，根生成器负责组合、确定性 ID、模板清洁、关系和 Diagram。测试侧维护独立 Oracle，以最终 JSON 和物理决策为被测对象。

**Tech Stack:** Markdown、Node.js ESM、`node:test`、PDManer 4.9.3 JSON、requirements-site TypeScript/MJS 内容目录。

**Spec:** `agent-docs/plans/2026-08-25-finance-balanced-physical-model-design.md`

## Global Constraints

- 最终物理模型为单一 Finance 数据库的 126 张表、8 个 Diagram Group；当前精确统计为 1,522 字段、214 个唯一索引、192 个普通索引、219 条本库关系和 43 条 1:1 关系。
- 全部表名以 `t_` 开头，唯一索引以 `uk_` 开头，普通索引以 `idx_` 开头。
- 表和字段显示名、说明使用直接中文；闭合枚举完整列值，开放代码注明权威来源。
- 只建立 Finance 本库关系；所有跨系统、Platform File、Vault 和 Actor 标识不建外键。
- 不新增通用 History、BCR、Inbox/Outbox、工作流、自动银行付款、跨 OE Cash Pool 或跨库 Projection。
- Journal、Snapshot、Movement、Event、Approval 和来源流水只追加；不物理删除或覆盖历史。
- 本计划不创建 Java、Migration、数据库实例或基础设施，不修改共享模板，不提交 Git Commit。

## Task 1：冻结物理边界与跨系统修正

- [x] 写入 126 表、8 图组和物理不变量设计。
- [x] 使用统一 `provider_debit_item`，不恢复已删除的 `channel_refund_attempt_id`。
- [x] 为 `reserve_case` 补完整来源事件四元组。
- [x] 把所有文件引用统一为 `file_id + file_content_hash`。
- [x] 明确 Finance Vault `CopyForBinding` 与 Fiscal Content Hash/Event Payload Hash 的区别。

## Task 2：建立独立 RED 测试

- [x] 创建 `scripts/pdmaner/finance-test-oracle/`，独立声明 126 表、字段签名、索引、关系、图组、枚举、CHECK 和场景。
- [x] 创建 `scripts/pdmaner/finance-model.test.mjs`，校验模板摘要、精确结构、元数据、安全边界、Diagram 和 `--check`。
- [x] 创建 `scripts/pdmaner/finance-scenario-doc.test.mjs`，校验现实场景覆盖全部表和关键资金链。
- [x] 在生成器和模型不存在时运行测试，记录预期失败。

## Task 3：实现模块化生产 Manifest

- [x] 创建 Finance 专属 Manifest DSL，不从 Markdown 或测试 Oracle 推断结构。
- [x] 分八个模块声明会计基础、税务/经济、Journal、余额/Funding、Provider/Cash/Recon、Settlement、Close、Reliability。
- [x] 每张表显式声明字段类型、空值、中文名、中文说明、枚举/开放目录、索引、本库关系和可变性。
- [x] 对 Version、Hash、Active Slot、Vault Tuple 和文件二元组执行白名单校验。

## Task 4：实现确定性 PDManer Generator

- [x] 校验共享模板字节摘要并清空数据库连接。
- [x] 应用 Money、Decimal、DateTime、Currency 共享 Domain。
- [x] 使用稳定 UUID 构建 Entity、Field、Index、Correlation 和 Diagram。
- [x] 生成 8 个关系图，并保证每表一个主图组、每关系只绘制一次。
- [x] 支持普通生成与 `--check`；输出表、字段、索引、关系和图组数量。

## Task 5：生成物理模型与决策文档

- [x] 生成 `finance-service/docs/database/finance.pdma.json`。
- [x] 创建 `finance-service/docs/database/FINANCE_PHYSICAL_DECISIONS.md`，记录精确总量、结构摘要、CHECK、事务约束、删改原因和手工门禁。
- [x] 首次生成后把模型字节摘要、测试独立结构摘要和元数据摘要固化到 Oracle。

## Task 6：现实场景与导航同步

- [x] 创建 `agent-docs/database/scenarios/FINANCE_REAL_WORLD_SCENARIOS.md`。
- [x] 使用同一组现实 ID/金额演示支付入账、Tax/Fiscal、Provider 到款、退款 UNKNOWN、重复扣款、拒付追偿、结算审批/付款/取消和期间关账。
- [x] 每个场景列出对应表、关键字段和值；整份文档覆盖 126 张表。
- [x] 更新 Finance 逻辑文档、数据库导航、项目上下文和 README 的物理状态。

## Task 7：全量验证

- [x] `node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs`
- [x] `node scripts/pdmaner/finance/generate-finance-model.mjs --check`
- [x] 回归 CCT、Commerce 和 Payment 的模型及场景测试（CCT、Payment 通过；Commerce 暴露 3 个既存模板同步失败，未在 Finance 任务中重写）。
- [x] `npm --prefix requirements-site test`，并执行相关 lint/内容检查。
- [x] 解析最终 JSON，扫描跨库 FK、凭证、PAN/SAD、旧退款尝试字段、未批准 Hash/Version 和文件二元组缺口。
- [x] 审阅限定 Diff；只保留 Finance 和必要跨域一致性改动。
- [x] 标明 PDManer 4.9.3 应用内兼容性检查仍待用户人工完成。

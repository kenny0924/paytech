# Commerce PDManer 物理模型实施计划

> **状态：历史 26 表阶段，已由 [Commerce 引用型订单快照压缩实施计划](./2026-08-23-commerce-reference-snapshot-compression-implementation-plan.md) 取代。本文仅保留实施过程证据，不得作为当前 Commerce 模型或任务入口。**

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to execute this plan task-by-task, `superpowers:test-driven-development` for every executable contract, and `superpowers:verification-before-completion` before reporting completion.

**Goal:** 将已确认的 Commerce 平衡型 26 表逻辑方案同步为权威文档、现实场景、跨系统契约，并从共享 PDManer 4.9.3 模板确定性生成可自动验证的 Commerce 物理模型。

**Architecture:** `agent-docs/database/COMMERCE_DATABASE.md` 继续拥有业务事实与关系；`agent-docs/plans/2026-08-23-commerce-balanced-physical-model-design.md` 固化本次精简决策；Commerce 专属显式 Manifest、中文元数据和生成器负责从共享模板产生唯一 JSON 工件；Node 契约测试验证表、字段、索引、关系、图组、哈希、场景覆盖和重复生成一致性。任何跨系统、Vault、文件、Actor 或 MQ 标识只保存逻辑引用，不创建跨库关系。

**Tech Stack:** Markdown、TypeScript/JavaScript 内容目录、Node.js `node:test`、PDManer 4.9.3 JSON、SHA-256、MySQL 8.0 物理约束语义。

**Approved Spec:** `agent-docs/plans/2026-08-23-commerce-balanced-physical-model-design.md`

## 全局约束

- 当前授权仅覆盖设计文档、现实场景、网站内容契约、生成器、自动测试和 `commerce-service/docs/database/commerce.pdma.json`；不得创建 Java 模块、Migration、数据库实例或外部环境。
- 不创建分支、不提交 Git；当前工作区已有大量用户变更，必须仅修改任务列出的文件。
- 物理表使用 `t_`；唯一索引使用 `uk_`；普通索引使用 `idx_`；索引名为小写 snake_case 且不超过 32 字符。
- 中文表名不带技术前缀；所有表、字段和索引必须有具体中文说明，闭合枚举必须列出完整允许值。
- ID 为非自增 Snowflake `BIGINT`；金额 `DECIMAL(24,8)`；费率 `DECIMAL(18,10)`；UTC 时间 `DATETIME(6)`；币种 `CHAR(3)`；MCC `VARCHAR(4)`；SHA-256 `CHAR(64)`。
- 不可变 Snapshot、Acceptance、Confirmation、Evidence、Reserve Change 和 BCR Version 只保留 `create_time`，不得添加通用 `version`、`active_slot` 或 `update_time`。
- BCR 仅覆盖 `ORDER|CHECKOUT_SESSION|CONSUMER_CONTEXT|FULFILLMENT|REFUND_REQUEST`；专用不可变事实、跨系统 Current Projection 和技术表不得重复接入 BCR。
- `t_compensation_event` 使用 `lock_version`，不得恢复通用 `version`；Claim 索引为 `(execute_status,next_execute_at,lock_until)`。
- 不得恢复四张普通状态历史表、`acceptance_state_change`、`fulfill_callback_snapshot`、Consumer/Acceptance 的 Phone/IP/User-Agent Blind Index，或 Commerce 自有 Webhook Endpoint Snapshot。
- 共享模板 `agent-docs/templates/pdmaner/template.pdma.json` 必须保持字节不变，不得保存真实连接或凭据。

### Task 1: 同步权威模型、现实场景与跨系统内容契约

**Files:**

- Modify: `agent-docs/plans/2026-08-23-commerce-balanced-physical-model-design.md`
- Rewrite: `agent-docs/database/COMMERCE_DATABASE.md`
- Create: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`

**Step 1: 写内容契约的失败测试**

- 先更新或新增最小断言，验证 `consumer_document` 命名、26 表基线、BCR 白名单、Acceptance 完成触发元组、Platform 在收到 `FulfillmentRequested` 后选择并冻结当前 ACTIVE Subscription 与 Verified Endpoint Version。
- 添加负向断言，禁止旧 32 表、已删除表、Acceptance IP/UA Blind Index 和 Commerce Webhook Endpoint/Version 所有权。

Run: `npm --prefix requirements-site test`

Expected: FAIL，且失败原因只应是权威内容尚未同步。

**Step 2: 同步权威 Commerce 文档**

- 将设计稿状态改为“已确认、当前决策”，明确本次用户已授权 PDManer 设计资产，但未授权 Migration、Java 或环境。
- 将 Commerce 权威数据库文档收敛为精确 26 表，更新逐表职责、字段、状态、关系图、关键流程、跨系统所有权、安全边界和待实现约束。
- 使用一个 Order Snapshot 聚合表达 Order Snapshot、Legal、Item、Tax、Commercial Term 及 Component；不再使用多个 1:1 子快照的概念。
- Consumer Context 为可纠正 Current Root；Consumer Contract 为不可变法律快照；Acceptance Summary 只允许 `PENDING -> COMPLETE`，由最终 Acceptance ID 与完成 Version 固化。
- Commerce 只发布履约业务事实；Platform 在事件到达时解析并冻结 Webhook Subscription/Endpoint Version。

**Step 3: 创建逐表现实场景文档**

- 用一条 USD Hosted Checkout 订单贯穿 CCT Bundle、消费者合同、两次支付尝试、履约确认、部分退款、UNKNOWN 预占释放、Receipt/Credit Note。
- 对 26 张表逐表给出真实示例值，明确哪些行创建、更新、追加、冻结或不创建；所有示例 ID、枚举、金额、版本和关联必须能互相对上。
- 明确 BCR 只记录五类 Current Root，专用不可变事实不重复记录。

**Step 4: 同步导航和跨系统序列**

- 更新两个 README 与 Project Context，使 Agent 能路由到 26 表决策、权威模型和现实场景。
- 更新 requirements catalog 和 E2E-02；保持 Payment Readiness 契约、Platform Webhook 所有权和六系统边界不变。

**Step 5: 运行内容验证**

Run: `npm --prefix requirements-site test`

Expected: PASS，且不存在与本任务相关的旧模型残留。

### Task 2: 以契约测试驱动 Commerce PDManer 确定性生成

**Files:**

- Create: `scripts/pdmaner/commerce-field-metadata.mjs`
- Create: `scripts/pdmaner/generate-commerce-model.mjs`
- Create: `scripts/pdmaner/commerce-model.test.mjs`
- Create: `scripts/pdmaner/commerce-scenario-doc.test.mjs`
- Create: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`
- Create: `commerce-service/docs/database/commerce.pdma.json`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`

**Step 1: 写 PDManer 和场景的失败契约测试**

- 固定精确 26 表集合、逐表字段顺序、可空性、类型、长度/精度、审计字段、版本白名单、BCR 白名单和禁用对象。
- 固定每个 `uk_*`/`idx_*` 的字段顺序、本库关系、跨库零关系、图组、中文元数据质量和枚举/开放目录说明。
- 验证 Snowflake、金额/费率、币种、MCC、Hash、Vault Tuple、CCT V4 Context、退款 UNKNOWN/预占、命令幂等和补偿 Claim/Lease 约束。
- 场景测试验证 26 表全部被真实流程覆盖，并禁止旧表名和旧 Endpoint/IP/UA 设计。

Run: `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`

Expected: FAIL，因为生成器、物理决策和模型尚不存在或不满足契约。

**Step 2: 编写显式 Manifest、中文元数据和物理决策**

- 不通过 Markdown 名称猜字段；显式声明每张表的字段、类型、可空性、默认值、索引、关系、图组和注释。
- 物理决策记录所有 MySQL 无法直接表达的状态矩阵、同 Order 归属、退款金额不变量、来源版本单调性、Hash Domain 和跨系统 Fail Closed 规则。
- 字段或表缺少显式中文元数据时生成必须失败，不允许机械英文拆词兜底。

**Step 3: 实现确定性模板派生生成器**

- 克隆共享模板，移除连接信息，禁用自增，按 `SHA-256(model-key:path)` 生成稳定内部 ID。
- 精确生成 26 表及本库 Correlation；每条本库关系的子字段必须有左前缀索引。
- 创建 6 个 Diagram Groups；每张表恰好一个 Primary Group，每条本库关系恰好一个 Relation Cell，跨图参照可重复但 Primary Table Cell 不重叠。
- 导出生成函数、序列化函数和 `--check`；重复运行必须字节相同。

**Step 4: 生成并冻结真实统计量与摘要**

Run: `node scripts/pdmaner/generate-commerce-model.mjs`

- 从第一次合格生成结果读取真实表/字段/索引/关系/图单元数、Structural Projection SHA-256 和 Model SHA-256，写入测试与物理决策文档。
- 不得预设或手改统计量来绕过测试。

**Step 5: 运行 Commerce 专属验证**

Run: `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`

Expected: PASS。

Run: `node scripts/pdmaner/generate-commerce-model.mjs --check`

Expected: PASS，输出模型已同步。

### Task 3: 全量回归与人工兼容性门禁交接

**Files:**

- Verify only; only repair files already listed above when a regression is caused by this plan.

**Step 1: 验证模板和既有 CCT 工件未被破坏**

Run: `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json`

Expected: `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`

Run: `node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs`

Expected: PASS。

Run: `node scripts/pdmaner/generate-client-contract-model.mjs --check`

Expected: PASS。

**Step 2: 运行 Commerce 和需求网站全量验证**

Run: `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`

Expected: PASS。

Run: `node scripts/pdmaner/generate-commerce-model.mjs --check`

Expected: PASS。

Run: `npm --prefix requirements-site test`

Expected: PASS。

**Step 3: 静态残留与工件检查**

- JSON parse 成功；模型无连接；26 张 `t_` 表精确匹配；生成两次字节一致。
- 在 Commerce 权威文档、场景、物理决策、生成器和模型范围内搜索旧 32 表、已删除对象、Acceptance IP/UA Blind Index、Commerce Webhook Endpoint 所有权和通用 `version` 残留。
- 检查只修改计划列出的文件，报告任何无关的既有工作区变化，不覆盖或清理它们。

**Step 4: 记录人工门禁**

- 自动验证完成后，仍明确标记“PDManer 4.9.3 应用内打开并执行 Check”是待用户人工完成的兼容性门禁；不得把自动 JSON 测试表述为已经通过应用内检查。

# Commerce 订单上下文精简实施计划

> **状态：已完成的历史实施阶段。** 订单上下文结构仍然有效，但最终全库统计、Consumer/Network Evidence 与无主动履约边界以 [Commerce 隐私与履约精简实施计划](./2026-08-24-commerce-privacy-fulfillment-simplification-implementation-plan.md) 和当前 Commerce 权威文档为准；不得从本文恢复旧阶段数量或履约结构。

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 Commerce 的订单外部标识统一为 `client_order_id`，移除重复的创建请求哈希与订单快照 Hash/Schema，按“订单主事实 + 一对一外部证据上下文”收敛模型，并固化同一 Operating Entity 内重试及永久失效后的取消规则。

**Architecture:** `t_commerce_order` 保存订单身份、冻结主体、行业/MCC 和订单级金额；不可变 `t_order_context` 只保存 CCT、Risk、Finance 等外部事实的稳定引用与交易时点。`t_order_item` 直接从属于订单，`t_order_legal` 从属于订单上下文。跨系统以 `order_id + order_context_id` 和明确业务字段核对，不再依赖 Commerce 自建 Snapshot Schema/Hash。

**Tech Stack:** Node.js `node:test`、PDManer 4.9.3 JSON 生成器、TypeScript/ESM 需求目录、Markdown 架构文档。

---

### Task 1: 先冻结新的 Commerce 物理契约

**Files:**
- Modify: `scripts/pdmaner/commerce-model.test.mjs`
- Modify: `scripts/pdmaner/commerce-scenario-doc.test.mjs`

1. 将测试期望改为 `client_order_id`、`t_order_context`、订单主表金额/分类字段和新的本库关系。
2. 将 `external_order_id`、`creation_request_hash`、`t_order_snapshot`、`snapshot_schema_version`、`snapshot_hash` 加入禁止恢复清单。
3. 删除 Commerce Order Snapshot V2 固定 Hash 向量测试。
4. 运行 Commerce 测试，确认旧生成物因契约不匹配而失败。

### Task 2: 更新并重新生成 PDManer 模型

**Files:**
- Modify: `scripts/pdmaner/commerce-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-commerce-model.mjs`
- Regenerate: `commerce-service/docs/database/commerce.pdma.json`
- Modify: `commerce-service/docs/database/COMMERCE_PHYSICAL_DECISIONS.md`

1. 调整字段、索引、关系、中文表名/字段说明和 Diagram Group。
2. 保留 Order 聚合根的 `version`，删除只服务旧快照哈希的字段。
3. 运行生成器并记录新的模型总数、模型 SHA-256 与结构投影 SHA-256。
4. 更新物理决策清单并让模型测试恢复通过。

### Task 3: 同步 Commerce 逻辑设计与现实场景

**Files:**
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md`
- Add: `agent-docs/plans/2026-08-24-commerce-order-context-simplification-design.md`
- Modify: `agent-docs/plans/2026-08-23-commerce-reference-snapshot-compression-design.md`
- Modify: `agent-docs/README.md`

1. 说明 `client_order_id` 是 Client 命名空间内的业务引用，不承担请求幂等。
2. 说明命令幂等只由 `t_command_idempotency.request_hash` 管理。
3. 固化临时 Provider/MID 不可用时同 Order、同 OE 新建 Attempt；OE 永久不可用时安全终止 Intent、取消 Order，由 Client 使用新的 `client_order_id` 重新创建，系统不自动替换。
4. 用现实数据展示 Order、Context、Legal、Item 与 Payment Attempt 的映射。

### Task 4: 同步跨系统合同与需求目录

**Files:**
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`
- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`

1. COM-I04 改为按 `order_id` 查询不可变 Order Context；COM-I10 使用 Order/Context 引用而非 Snapshot Hash。
2. Payment Intent 与成功事件冻结 `source_order_context_id` 和明确订单字段，不再保存 Snapshot ID/Schema/Hash。
3. Risk 从 Payment Intent 的冻结订单事实评估，不要求 Commerce Snapshot Hash。
4. Finance 按 `order_id + order_context_id` 及明确字段交叉校验，继续保留 CCT V4、Tax Snapshot 与 Finance 自有计算 Hash。
5. Platform 文档生成以 `commerce_order_context_id` 加三份 Legal 文件/内容 Hash 作为渲染引用。
6. 补齐永久 OE 失效事件、活动/UNKNOWN Attempt 安全门禁和禁止自动替换订单的全局流程。

### Task 5: 完整验证

1. 运行 `node scripts/pdmaner/generate-commerce-model.mjs --check`。
2. 运行 `node --test scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`。
3. 运行 `npm --prefix requirements-site test`。
4. 搜索并确认当前规范中不再使用已删除的 Commerce Snapshot 字段或 `external_order_id`；历史计划只保留明确的“已被后续决策替代”说明。
5. 检查 `git diff/status`，仅报告本次范围内变更，不提交、不建分支、不部署。

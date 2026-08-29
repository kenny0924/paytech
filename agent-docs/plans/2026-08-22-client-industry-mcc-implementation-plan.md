# Client Industry and MCC Classification Implementation Plan

> **Commerce 后续决策：** 本计划中的请求哈希与 Order Snapshot 锚已被 [2026-08-24 Commerce 订单上下文精简设计](./2026-08-24-commerce-order-context-simplification-design.md) 取代；当前实现只继承“一笔订单一组 `industry_code + mcc_code`”结论。

> **历史阶段提示：** 本文保留该阶段 55 表 / 620 字段基线、测试结果、Digest 与决策原文；当前基线已进一步收敛为 [44 表 Commercial Term 设计](./2026-08-23-cct-commercial-term-consolidation-design.md)。不得据此恢复旧 Scope/Hash 或 Agreement/Legal 结构。

> 历史实施记录：本计划中的 55 表 / 620 字段及七张 Scope/Rule 是该次行业/MCC 变更完成时的验收基线，随后已被 `2026-08-22-cct-assignment-simplification-design.md` 的 51 表 / 576 字段四表自动分配模型取代；不得据此恢复 `assignment_policy_binding` 或 Assignment Hash/Version。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的“Client Business Profile 可维护多组 `industry_code + mcc_code`，但一笔 Order 只提交并冻结一组”完整同步到需求目录、六系统逻辑文档，以及已授权的 Client & Contract PDManer 物理模型。

**Architecture:** Client & Contract 负责批准交易分类组合并在 CCT-I13 中返回可验证证明；Commerce 在创建 Order 时验证并冻结唯一一组分类；Payment、Risk 与 Finance 只继承该订单时点事实。Client 不提交 `classification_id`，也不能选择 Operating Entity、Provider 或 MID。只更新 Client & Contract 的物理模型，其他系统仍停留在逻辑设计阶段。

**Tech Stack:** Markdown、TypeScript/MJS 内容目录、Node.js `node:test`、PDManer 4.9.3 JSON、确定性模型生成器、npm/Vinext 内容站点。

**Spec:** `agent-docs/plans/2026-08-22-client-industry-mcc-design.md`

## Global Constraints

- 不创建 Java 模块、Migration、数据库、运行环境或其他五个系统的 PDManer 文件。
- 不手工编辑 `client-contract-service/docs/database/client-contract.pdma.json`；只能由生成器重建。
- 一笔 Order 只能有一组 `industry_code + mcc_code`，所有 Item 共用该组；不设计 Item 级或多组分类。
- 创建 Order 请求只接受两个业务代码，不接受 `classification_id`、Operating Entity、Provider 或 MID 作为路由选择输入。
- `mcc_code` 保留为字符串：业务分类行必须是四位数字，Scope 配置另允许批准的 `*` 通配值。
- 保留工作区既有修改；不提交 Git commit，不创建分支，不发布站点。

---

## Task 1: 先锁定需求级回归契约

**Files:**
- Modify: `requirements-site/tests/domain-decisions.test.mjs`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`

- [x] 在 `domain-decisions.test.mjs` 增加一个独立测试，断言需求目录同时明确：Profile 可有多组已批准组合、一笔 Order 恰好一组、Create Order 传 `industry_code + mcc_code` 而非 `classification_id`、Client 不选择 Operating Entity/Provider/MID。
- [x] 运行 `node --test requirements-site/tests/domain-decisions.test.mjs`，确认新测试因内容尚未同步而失败，并记录失败断言。
- [x] 更新 `requirements.ts` 中 Client & Contract、Commerce、Payment、Risk、Finance 的对应 Use Case / Interface Use Case 与全局 Decisions；把 CCT-I13 定义为按准确组合验证 Profile 并返回 Profile/Classification Proof。
- [x] 在 Commerce 需求中写明 Create Order 两项代码都必填、进入 `creation_request_hash`、一笔订单所有 Item 共用一组，历史订单不得被当前 Profile 改写。
- [x] 在 Payment/Risk/Finance 需求中写明两个维度来自冻结 Order Snapshot，分别参与路由、风险和经济快照；MCC 仍约束 MID 能力，Industry 不替代 MCC。
- [x] 更新 `global-use-case-sequences.mjs` 的 E2E-02：Client API 提交一组代码，Commerce 调 CCT-I13 验证，CCT 返回 `business_profile_id/version_no/content_hash` 与 `classification_id/industry_code/mcc_code`，后续 Payment/Risk 继承同一组。
- [x] 重新运行 `node --test requirements-site/tests/domain-decisions.test.mjs`，确认测试通过。

## Task 2: 先让 Client PDManer 契约测试表达新模型

**Files:**
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`

- [x] 将预期表 `client_business_activity` 改为 `client_business_classification`，中文表名改为“客户经营分类”，主键改为 `classification_id`，关系和 `roles_onboarding` 图组同步改名。
- [x] 将该表字段清单改为 `classification_id,business_profile_id,industry_code,mcc_code,primary_slot,create_time,update_time`。
- [x] 将唯一键预期改为 `(business_profile_id,industry_code,mcc_code)` 与 `(business_profile_id,primary_slot)`。
- [x] 将七张 CCT Scope/Rule 表的单一 `activity_code` 或 `scope_activity_code` 全部改成相邻的 `industry_code,mcc_code`：`client_entity_affiliation`、`client_entity_assignment`、`assignment_policy_binding`、`entity_assignment_rule`、`agreement_scope_binding`、`client_pricing_scope`、`partner_share_scope`。
- [x] 更新字段总数断言为 620；表数 55、唯一索引 83、访问索引 63、本库关系 83、Diagram Group 6 的基线保持不变。
- [x] 更新开放目录白名单、字段长度覆盖和主分类 Slot 规则；断言分类行 `mcc_code` 为 `VARCHAR(4)` 且注释声明四位数字，七张 Scope 的 `mcc_code` 为 `VARCHAR(4)` 且注释声明四位 MCC 或批准的 `*`。
- [x] 把 `client_business_activity`、`activity_id`、`classification_scheme`、`classification_code`、`activity_code`、`scope_activity_code` 加入精确遗留结构拒绝检查；历史说明文本不作为结构误报。
- [x] 运行 `node --test scripts/pdmaner/client-contract-model.test.mjs`，确认测试因 Catalog/Metadata/生成模型仍是旧结构而失败；失败必须命中新旧结构差异，而不是测试语法错误。

## Task 3: 更新 Client & Contract 权威逻辑 Catalog

**Files:**
- Modify: `agent-docs/database/CLIENT_CONTRACT_DATABASE.md`
- Modify: `agent-docs/plans/2026-08-22-client-industry-mcc-design.md`

- [x] 在 `CLIENT_CONTRACT_DATABASE.md` 把分类表更名并替换字段、中文职责、唯一键、1:N 关系说明和 `roles_onboarding` ER 图节点。
- [x] 将七张 Scope/Rule 表字段清单拆为 `industry_code + mcc_code`，明确每个维度独立参与 `scope_hash/content_hash`，只有配置 Scope 可使用批准的 `*`。
- [x] 更新 Business Profile Hash 规则：按 `industry_code,mcc_code` 稳定排序后纳入完整分类集合和 `primary_slot`，新输入形状提升 Hash Schema Version，旧 Profile 不重算。
- [x] 更新 CCT-I13 输入、返回值、失败规则和跨系统引用：输入准确的一组代码，返回 Profile Proof 与命中 Classification Proof，并用同一组合解析 Assignment、Agreement、Pricing、Partner Share、Eligibility 和 Consumer Legal。
- [x] 将文档统计从 55 表 / 613 字段改为 55 表 / 620 字段；唯一索引、访问索引、关系和图组统计保持原批准值。
- [x] 将设计文档状态改为“已确认，实施中”，并把“一笔订单一组”作为不可回退决策，而不是待复核项。

## Task 4: 更新字段元数据与确定性生成器

**Files:**
- Modify: `scripts/pdmaner/client-contract-field-metadata.mjs`
- Modify: `scripts/pdmaner/generate-client-contract-model.mjs`

- [x] 在字段元数据中把表名改为 `client_business_classification: "客户经营分类"`，新增 `classification_id`、`industry_code`、`mcc_code` 的简洁中文名称与不重复表名前缀的中文说明。
- [x] 删除旧表和旧字段的专用描述、开放目录来源与 Slot 规则；将主分类 Slot 路径改为 `client_business_classification.primary_slot`，说明改为“一组主要经营分类”。
- [x] 为分类行配置开放来源：`industry_code` 来自平台行业目录，`mcc_code` 来自平台采用的 MCC 目录且必须为四位数字；不伪造成闭合枚举。
- [x] 为七张 Scope/Rule 表分别配置两个开放来源：Industry 使用平台行业目录加批准通配规则，MCC 使用 MCC 目录加批准通配规则；保持注释格式 `取值来源：…；非固定枚举。`。
- [x] 在生成器的 Nullable 路径、Business Profile 关系、`roles_onboarding` Diagram Group 中完成表名替换。
- [x] 为八个 `mcc_code` 路径设置 `VARCHAR(4)` 长度覆盖；保持 `industry_code` 使用当前标准代码长度。
- [x] 运行 `node --check scripts/pdmaner/client-contract-field-metadata.mjs` 和 `node --check scripts/pdmaner/generate-client-contract-model.mjs`，确认脚本语法通过。

## Task 5: 重建并验证 Client PDManer 模型

**Files:**
- Generate: `client-contract-service/docs/database/client-contract.pdma.json`
- Modify: `scripts/pdmaner/client-contract-model.test.mjs`

- [x] 运行 `node scripts/pdmaner/generate-client-contract-model.mjs`，从模板和更新后的 CCT Catalog 生成模型。
- [x] 运行 `node --test scripts/pdmaner/client-contract-model.test.mjs`；先修复所有结构、字段元数据、关系、索引、图组和物理类型失败，直到只剩批准摘要漂移。
- [x] 对通过结构检查的模型计算新 Structural Projection SHA-256，把测试中的摘要常量更新为该次实际值；不得用跳过断言或 `PENDING` 绕过门禁。
- [x] 再运行 `node --test scripts/pdmaner/client-contract-model.test.mjs`，确认全部测试通过。
- [x] 运行 `node scripts/pdmaner/generate-client-contract-model.mjs --check`，确认生成结果确定且工作树模型没有手工漂移。
- [x] 运行 `node -e 'JSON.parse(require("node:fs").readFileSync("client-contract-service/docs/database/client-contract.pdma.json","utf8")); console.log("client-contract PDManer JSON OK")'`，确认 JSON 可解析。

## Task 6: 同步 Client 物理决策与导航状态

**Files:**
- Modify: `client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/README.md`

- [x] 从已验证模型读取并写回准确统计：55 表、620 字段、83 唯一索引、63 访问索引、83 本库关系、6 Diagram Groups；不凭手工估算修改其他统计。
- [x] 运行 `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json client-contract-service/docs/database/client-contract.pdma.json`，把模板摘要和模型摘要同步到物理决策文档。
- [x] 从通过的模型测试输出或测试常量读取 Structural Projection 摘要，并同步到物理决策文档。
- [x] 在物理决策中记录分类表、七张 Scope 拆分、Business Profile Hash 输入和 CCT-I13 Proof；明确其他五个物理模型仍未授权。
- [x] 在数据库导航、项目上下文与总导航中同步 55/620 状态和“一笔订单一组”的已确认约束；设计文档导航标记为已落模，不再写“待落模”。

## Task 7: 同步 Commerce 的唯一订单分类快照

**Files:**
- Modify: `agent-docs/database/COMMERCE_DATABASE.md`

- [x] 在 Create Order 规则中加入两项必填输入、四位 MCC 校验、组合验证和幂等 Hash 规则；明确请求不接受 `classification_id` 或路由对象选择。
- [x] 在 `order_snapshot` 增加 `business_profile_id`、`business_profile_version_no`、`business_profile_content_hash`、`business_classification_id`、`industry_code`、`mcc_code`，并写明全部进入 `cct_commercial_context_hash/snapshot_hash`。
- [x] 明确一个 Order Snapshot 只有一组分类，所有 Item 共用；`product_type_code` 和 `tax_category_code` 不得替代 Industry/MCC。
- [x] 更新 CCT 跨系统引用和建单主流程，要求同一次 CCT-I13 Proof 验证命中组合；缺失、冲突、超时或 Profile 非有效时 Fail Closed。

## Task 8: 同步 Payment、Risk 与 Finance 下游语义

**Files:**
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/FINANCE_DATABASE.md`

- [x] Payment：在 `payment_intent` 增加冻结的 `industry_code,mcc_code`；将 `routing_policy_binding`、`routing_coverage` 的模糊活动字段拆为两个维度；更新 `routing_decision.input_hash`、MID MCC 能力、Attempt 不可换分类和跨系统引用规则。
- [x] Risk：将 `policy_scope` 与 `business_acceptance_rule` 改为两个维度；在 `checkout_assessment`、`transaction_assessment`、`risk_fact_projection` 加入结构化 Industry/MCC；更新 Feature/Rule 输入 Hash、旁路评估和统计说明。
- [x] Finance：在 `economics_snapshot` 增加两个维度并纳入 `calculation_hash`；明确 PAYMENT、REFUND、DISPUTE、COST_ADJUSTMENT 继承原交易分类；更新 `EconomicsCalculated` 最小事件载荷和收入/成本/分润/利润统计语义。
- [x] 在三份文档中保持税务分类与 Industry/MCC 分离，并保持 Client 无权通过这些字段选择 Operating Entity、Provider 或 MID。

## Task 9: 全量一致性验证与交付审查

**Files:**
- Verify: all files changed in Tasks 1–8

- [x] 运行 `node --test scripts/pdmaner/client-contract-model.test.mjs`，确认 Client 模型契约全部通过。
- [x] 运行 `node scripts/pdmaner/generate-client-contract-model.mjs --check`，确认生成器无漂移。
- [x] 运行 `npm --prefix requirements-site test`，确认站点构建和全部内容测试通过。
- [x] 运行 `npm --prefix requirements-site run lint`；若存在与本次无关的既有失败，单独记录文件和错误，不把它伪装成本次通过。
- [x] 运行定向遗留扫描：`rg -n 'client_business_activity|activity_id|classification_scheme|classification_code|scope_activity_code|\bactivity_code\b' agent-docs/database agent-docs/PROJECT_CONTEXT.md requirements-site/content requirements-site/tests scripts/pdmaner client-contract-service/docs/database/CLIENT_CONTRACT_PHYSICAL_DECISIONS.md`。只允许迁移说明中的明确历史引用；现行 Catalog、模型、元数据、测试白名单和接口语义必须零旧结构命中。
- [x] 运行 `git diff --check`，确认没有空白错误。
- [x] 审查 `git diff --stat` 与 `git status --short`，确认没有 Java、Migration、其他系统 PDManer、部署或无关文件被创建。
- [x] 将设计文档状态改为“已实施并通过自动验证”；保留“PDManer 4.9.3 应用内检查待人工确认”这一人工门禁。
- [x] 最终报告只汇总已完成内容、准确验证结果、人工门禁和未授权范围；不声称已创建数据库或运行时实现。

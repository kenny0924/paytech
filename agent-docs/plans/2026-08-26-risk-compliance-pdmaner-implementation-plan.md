# Risk & Compliance PDManer Implementation Plan

> **Historical baseline:** 本文件记录首版 88 表物理模型的已执行过程。Due Diligence 的 22 表结构及由此产生的总计已被 [2026-08-27 Risk DD 19 表精简计划](./2026-08-27-risk-dd-simplification-implementation-plan.md) 取代；其余子域的历史实施记录继续有效。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的 Risk & Compliance 88 表逻辑模型落成一套可重复生成、可独立自动校验并可在 PDManer 4.9.3 中人工评审的 V1 物理模型。

**Architecture:** 共享 PDManer 模板保持只读；Risk 专属生产 Manifest 按八个逻辑子域拆分，根生成器负责组合、确定性 ID、模板清洁、本库关系和 Diagram。测试侧维护不导入生产 Manifest 的独立 Oracle，以最终 JSON、物理决策和现实场景为被测对象，严格采用 RED → GREEN。

**Tech Stack:** Markdown、Node.js ESM、`node:test`、PDManer 4.9.3 JSON、SHA-256、MySQL 8.0 物理约束语义。

**Spec:** `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`；全局规则见 `agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/database/README.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md` 和 `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`。

**Execution record:** Risk 自动交付项已完成，最终物理基线为 88 表、964 字段、114 个唯一索引、87 个访问索引、140 条本库关系、14 条 1:1 关系和 8 个 Diagram Groups。Risk 专测、CCT/Payment/Finance 回归与需求站测试通过；全量既有模型回归另发现未由本计划修改的 Commerce 已提交模型与其当前 Generator/共享模板不一致，保留给对应并行任务处理。PDManer 4.9.3 应用内人工评审仍是未自动代替的最终门禁。

## Global Constraints

- 当前逻辑模型固定为 88 张表，分组精确为 `22 + 27 + 10 + 6 + 10 + 4 + 7 + 2`；`t_velocity_bucket` 不属于 V1。
- 全部表名使用 `t_` + lowercase `snake_case`；唯一索引使用 `uk_`，普通访问索引使用 `idx_`；表、字段和索引物理名不超过 32 字符。
- 每张表恰好一个服务侧 Snowflake `BIGINT` 主键且 `autoIncrement=false`；金额使用 `DECIMAL(24,8)`，比例使用 `DECIMAL(18,10)`，UTC 时间使用 `DATETIME(6)`，币种使用 `CHAR(3)`，国家使用 `CHAR(2)`。
- 表、字段、索引必须使用直接、可独立理解的中文名称和说明；闭合枚举列出完整允许值，开放目录码注明权威来源。
- 只建立 Risk 本库关系；CCT、Commerce、Payment、Finance、Platform、Vault、文件和 Actor 标识不建物理外键。
- Rule 使用受控 AST、Feature/Context/Literal 操作数和工程注册 Handler；禁止任意 SQL、Script、无版本 JSON、EAV、运行时类名和外部 URL。
- Release 只允许静态校验 → Shadow → 指标检查 → 审核 → ACTIVE；不建设真实流量灰度，Shadow/Replay 不创建真实 Action Execution、Restriction、Limit Reservation 或业务事件。
- Action Intent 全量收集并校验后才合并为 Effect；PRIMARY 真实副作用与 Evaluation/业务状态同事务提交，异步动作通过本地 `t_compensation_event` 执行。
- Payment Attempt 的 PRIMARY ALLOW 与 Limit Group、Reservation、Movement、Position 和可靠事件必须原子提交；UNKNOWN 保留占用，SHADOW/REPLAY 不占用。
- Risk 只表达 Settlement Restriction 和 Client Reserve Requirement，不保存 Finance 余额、实际冻结金额、Ledger 或实际损失计算。
- PAN、CVV/CVC、完整磁道、PIN/PIN Block、卡有效期、Vault 密文、Secrets、数据库连接和 Provider 原始报文不得进入模型。
- 本计划只授权设计文档、现实场景、生产 Manifest、测试 Oracle、生成器、自动测试和 `risk-compliance.pdma.json`；不创建 Migration、Java 模块、物理数据库或基础设施。
- 共享模板 `agent-docs/templates/pdmaner/template.pdma.json` 必须保持字节不变，SHA-256 固定为 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- 当前工作区包含用户未提交变更；只修改本计划列出的 Risk 文件和必要导航，不创建分支、不提交 Git、不发布外部环境。

---

### Task 1: 建立独立 RED 模型契约

**Files:**

- Create: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Create: `scripts/pdmaner/risk-compliance-model.test.mjs`

**Interfaces:**

- Consumes: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md` 的 88 表清单、状态机和不变量。
- Produces: 测试侧独立的 `APPROVED_TABLES`、`APPROVED_DIAGRAMS`、`EXPECTED_TOTALS`、字段/索引/关系与安全约束，以及对真实生成工件的可执行契约。

- [x] **Step 1: 写精确表集合和分组 Oracle**

  在 `schema.oracle.mjs` 中以字面量声明 88 个 `t_*` 表名和八个图组，断言表数、唯一性、前缀及 `t_velocity_bucket` 缺席。测试不得导入生产 Manifest。

- [x] **Step 2: 写工件存在性和模板契约测试**

  `risk-compliance-model.test.mjs` 必须直接读取共享模板、未来模型、物理决策、生产 Manifest、Policy 和 Generator；不存在时给出准确缺失路径。

- [x] **Step 3: 写物理结构与安全契约测试**

  对真实模型断言：每表一个非自增 BIGINT 主键、中文元数据、32 字符命名、索引前缀、本库关系、关系字段左前缀索引、1:1 唯一性、空连接、受控敏感字段以及无跨库 FK。

- [x] **Step 4: 写领域专项契约测试**

  断言 Rule AST/Operand XOR、Literal/Feature Result 类型化值、Release Shadow/Approval、Action REAL/SIMULATED、Limit 原子占用、Role Qualification、Settlement Restriction、Dispute、File 二元组、Vault Tuple、来源事件版本和本地 Compensation Lease 的必要结构。

- [x] **Step 5: 运行并确认 RED**

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs`

  Expected: FAIL，原因明确为 Risk 生产 Manifest、Generator、物理决策或模型尚不存在；88 表 Oracle 自身完整性测试通过。

### Task 2: 实现 Risk 专属生产 Manifest DSL 与 Policy

**Files:**

- Create: `scripts/pdmaner/risk/schema-dsl.mjs`
- Create: `scripts/pdmaner/risk/model-policy.mjs`
- Create: `scripts/pdmaner/risk/manifest.mjs`
- Create: `scripts/pdmaner/risk/manifests/01-due-diligence.mjs`
- Create: `scripts/pdmaner/risk/manifests/02-decision-engine.mjs`
- Create: `scripts/pdmaner/risk/manifests/03-evaluation.mjs`
- Create: `scripts/pdmaner/risk/manifests/04-risk-data.mjs`
- Create: `scripts/pdmaner/risk/manifests/05-limits.mjs`
- Create: `scripts/pdmaner/risk/manifests/06-settlement.mjs`
- Create: `scripts/pdmaner/risk/manifests/07-dispute.mjs`
- Create: `scripts/pdmaner/risk/manifests/08-reliability.mjs`
- Test: `scripts/pdmaner/risk-compliance-model.test.mjs`

**Interfaces:**

- Consumes: Task 1 的独立 Oracle 与逻辑 Spec。
- Produces: `field()`、`primaryKey()`、`uniqueIndex()`、`accessIndex()`、`relation()`、`defineTable()`；`TABLES`、`RELATIONS`；`DIAGRAM_GROUPS`、`CHECK_CONTRACTS`、`CHECK_BINDINGS`、`FILE_REFERENCE_TUPLES` 和安全白名单。

- [x] **Step 1: 为 DSL 写失败行为测试**

  测试导入 `schema-dsl.mjs` 并调用 `defineTable()`：缺少中文名称/说明、重复字段、非 BIGINT 主键、超过 32 字符的表/字段/索引、非法索引前缀时必须抛错。

- [x] **Step 2: 运行 DSL 测试确认 RED**

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs --test-name-pattern='Risk Manifest DSL'`

  Expected: FAIL，原因是 `schema-dsl.mjs` 尚不存在。

- [x] **Step 3: 实现最小 DSL 并确认 GREEN**

  复用 Finance 已确认的接口形状，但代码位于 Risk 专属目录；`defineTable()` 只接受显式字段、索引、关系和可变性，不从 Markdown、字段名或测试 Oracle 推导业务结构。

- [x] **Step 4: 按八个逻辑分组声明 88 张表**

  每张表显式声明逻辑字段展开、物理类型、空值、中文元数据、闭合枚举/开放目录、UK、访问索引、本库关系和不可变性。`manifest.mjs` 只组合八组并导出扁平 `TABLES/RELATIONS`。

- [x] **Step 5: 声明行级 CHECK 与跨表 Policy**

  `model-policy.mjs` 必须至少覆盖：时间区间、状态—时间矩阵、活动占位、类型化值 XOR、来源 XOR、文件/Vault Tuple、分数/比率范围、Deadline 顺序、Limit 非负与窗口、Restriction/Reserve 角色矩阵、Compensation Claim/Lease；跨行不变量保留在物理决策中。

- [x] **Step 6: 运行 Manifest 专项测试**

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs --test-name-pattern='Oracle|Manifest|Policy'`

  Expected: 88 表、八组、Policy 绑定、关键结构测试通过；模型工件相关测试仍因 Generator/模型缺失而失败。

### Task 3: 实现确定性 PDManer Generator

**Files:**

- Create: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`
- Create generated: `risk-compliance-service/docs/database/risk-compliance.pdma.json`
- Test: `scripts/pdmaner/risk-compliance-model.test.mjs`

**Interfaces:**

- Consumes: 共享模板、Task 2 的 `TABLES/RELATIONS/DIAGRAM_GROUPS` 和 Policy。
- Produces: `generateRiskComplianceModel()`、`serializeRiskComplianceModel()`、`modelTotals()`、`structuralProjection()`、`structuralProjectionDigest()`；CLI 支持普通生成和 `--check`。

- [x] **Step 1: 写 Generator 行为失败测试**

  测试真实执行 Generator 并断言：模板摘要错误时拒绝；两次序列化逐字节一致；`--check` 在模型缺失或漂移时失败；模型写入后 `--check` 成功。

- [x] **Step 2: 运行确认 RED**

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs --test-name-pattern='deterministic|generated artifact'`

  Expected: FAIL，原因是 Generator 或模型不存在。

- [x] **Step 3: 实现模板规范化与稳定 ID**

  克隆共享模板，清空 `dbConn/dbConns/profile.default.dbConn`，修正 BIGINT→Long、DECIMAL→BigDecimal、DATETIME(6)→Instant、Currency/Country Domain；以 `SHA-256(code-mor/risk-compliance/database/v1:path)` 生成稳定 UUIDv5 形态 ID。

- [x] **Step 4: 生成实体、索引与本库关系**

  使用 Manifest 原有顺序生成字段和索引；只建立 Risk 内部 Correlation；关系子字段必须具有主键或左前缀索引，1:1 必须由主键或单字段 UK 保证。

- [x] **Step 5: 生成八个关系图**

  图组固定为“尽调与资格”“决策点与规则发布”“评估与 Action”“冷却、名单与风险数据”“限额与占用”“结算限制”“争议与举证”“可靠执行”。每表恰好一个 Primary Group，每条本库关系恰好绘制一次，跨组父表只作为 Reference。

- [x] **Step 6: 生成真实模型并确认 GREEN**

  Run: `node scripts/pdmaner/risk/generate-risk-compliance-model.mjs`

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs`

  Expected: JSON 结构、88 表、字段、索引、关系、Diagram、元数据、安全和确定性测试全部通过，物理决策缺失测试除外。

### Task 4: 冻结物理决策、统计量和独立摘要

**Files:**

- Create: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Test: `scripts/pdmaner/risk-compliance-model.test.mjs`

**Interfaces:**

- Consumes: Task 3 的真实模型总量和摘要。
- Produces: `EXPECTED_TOTALS`、`EXPECTED_MODEL_DIGEST`、`EXPECTED_STRUCTURAL_DIGEST`，以及可供未来 Migration/Testcontainers 使用的精确 CHECK 和事务不变量。

- [x] **Step 1: 从真实模型读取统计量和摘要**

  使用 Generator 的只读输出与测试侧独立结构投影取得表、字段、唯一索引、访问索引、关系、1:1、图组、模型 SHA-256 和结构 SHA-256；不得预猜数量或从生产 Generator 导入预期值。

- [x] **Step 2: 写物理决策文档**

  记录阶段边界、八个图组、模板派生、类型/命名、Version/Hash/Active/immutable 白名单、Rule AST、Release Shadow、Action、Limit、DD、Settlement、Dispute、可靠执行、安全边界和人工门禁。

- [x] **Step 3: 固化精确 CHECK 契约**

  物理决策中的 CHECK 名称、表达式和绑定表必须与 `model-policy.mjs` 一致；说明 PDManer 4.9.3 对 CHECK 的表达限制，未来 Migration 和 Testcontainers 必须逐项落实。

- [x] **Step 4: 固化独立 Oracle 并运行模型测试**

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs`

  Expected: PASS，且测试不通过生产 Manifest 计算期待结构。

### Task 5: 建立逐表现实场景契约

**Files:**

- Create: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Create: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`

**Interfaces:**

- Consumes: 88 表模型、Risk 状态机、接口/事件契约。
- Produces: 使用一致 ID/金额/版本贯穿全部 88 表的现实业务样例，以及校验表名、字段名、枚举和关键链路的独立场景测试。

- [x] **Step 1: 写场景测试并确认 RED**

  测试读取真实模型和场景文档，要求 88 张物理表全部被覆盖，并验证组织 KYB、Checkout Shadow、Payment Limit UNKNOWN、冷却/IP 名单、Settlement Restriction、Dispute RFI 与可靠事件链；场景文档缺失时应明确失败。

- [x] **Step 2: 编写一致的现实场景**

  使用一组固定 Client、Operating Entity、Order、Payment Attempt、Evaluation、Release、Case 和 Dispute ID，逐表说明创建/更新/追加/冻结/不创建行为；禁止占位符、伪 SQL 和与模型不存在的字段。

- [x] **Step 3: 运行场景测试确认 GREEN**

  Run: `node --test scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`

  Expected: PASS，88 表全部覆盖，场景中的 `t_*` 表名均存在于模型。

### Task 6: 同步导航和物理模型状态

**Files:**

- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Test: `requirements-site/tests/*.test.mjs`

**Interfaces:**

- Consumes: Task 4 的精确模型统计和 Task 5 的场景文档。
- Produces: 从项目入口、Agent 路由和 Risk 逻辑文档到物理决策、场景、模型及实施记录的准确导航。

- [x] **Step 1: 复核需求目录与内容测试边界**

  Risk 业务契约已经由 canonical requirements 覆盖，本任务只更新物理设计状态，不改写 `requirements.ts` 或全局序列；保留既有内容测试作为共享文案兼容门禁。

- [x] **Step 2: 同步导航和状态**

  写入实际字段/索引/关系/图组总量，链接逻辑模型、现实场景、物理决策、PDManer 和本实施计划；将 Risk 标记为第五套已生成并自动验证模型，同时明确 Migration、Java、环境和 PDManer 4.9.3 人工检查未完成。

- [x] **Step 3: 扫描并清除过时状态**

  检查根 README、Agent 路由、项目上下文、数据库总览、数据库设计指南与 Risk 专项文档，不再残留“Risk 物理模型待生成”或“四套已生成模型”等旧状态。

- [x] **Step 4: 运行内容测试确认 GREEN**

  Run: `npm --prefix requirements-site test`

  Expected: PASS。

### Task 7: 全量验证和人工门禁交接

**Files:**

- Verify only; verification 发现由本计划引入的缺陷时，只修改 Task 1–6 已列文件。

**Interfaces:**

- Consumes: 全部 Risk 设计产物。
- Produces: 可交给用户在 PDManer 4.9.3 中人工 Review 的确定性第五套物理模型。

- [x] **Step 1: 验证 Risk 生成物**

  Run: `node --test scripts/pdmaner/risk-compliance-model.test.mjs scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`

  Run: `node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check`

  Expected: 全部 PASS，`--check` 报告模型与确定性生成结果一致。

- [x] **Step 2: 回归既有四套模型**

  Run: `node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/finance-scenario-doc.test.mjs`

  Expected: PASS；任何与本任务无关的既存失败单独报告，不改写其他系统模型绕过。

- [x] **Step 3: 验证模板、JSON 和敏感内容**

  Run: `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json`

  Run: `node -e "JSON.parse(require('node:fs').readFileSync('risk-compliance-service/docs/database/risk-compliance.pdma.json','utf8'))"`

  Run: `rg -n -i 'jdbc:|password|secret_key|private_key|\\bpan\\b|\\bcvv\\b|card_exp' risk-compliance-service/docs/database/risk-compliance.pdma.json`

  Expected: 模板摘要固定；JSON 可解析；扫描不含凭证值和禁止的卡数据字段。

- [x] **Step 4: 运行需求回归和工作区检查**

  Run: `npm --prefix requirements-site test`

  Run: `git diff --check`

  Run: `git status --short`

  Expected: 需求测试通过、无空白错误；限定 Diff 只包含 Risk 设计资产和必要导航，不覆盖用户其他未提交修改。

- [x] **Step 5: 交接人工门禁**

  明确自动验证已完成，但仍需用户使用 PDManer 4.9.3 打开模型、检查八个关系图、运行应用内规范检查并确认中文元数据和循环关系可读；人工确认前不得宣称兼容性门禁已通过。

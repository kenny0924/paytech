# Platform Support PDManer Implementation Plan（历史记录）

> **2026-09-01 再次替代：** 本计划记录的 92/97 表结构、八组 Manifest、旧 Gateway、通用配置和持久化 Readiness 已被后续设计取代。[57 表精简设计](../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md) 是未受影响领域的直接前序；[统一公共 API 动态控制面设计](../../docs/superpowers/specs/2026-09-01-gateway-platform-support-dynamic-operations-configuration-design.md) 与其 [PDManer 实施计划](../../docs/superpowers/plans/2026-09-01-platform-public-api-control-plane-pdmaner.md) 定义当前六表后继。当前模型为 63 表 / 822 字段 / 123 个唯一索引 / 99 个访问索引 / 107 条关系 / 5 个 1:1 / 7 图组 / 31 条复合 FK / 85 条 CHECK / 36 个 `_hash` 字段；后文只用于追溯历史 TDD 过程。

> **2026-08-30 已替代：** 本记录继续证明历史 92 表模型的生成过程，但其外部 IdP-only、无本地登录 Session、动态 Gateway Route/Upstream 与旧 Guard 语义已被 [统一人工认证设计](../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md) 和当前 [97 表模型](../../platform-support-service/docs/database/platform-support.pdma.json) 取代。认证/Guard 的物理模型修订已经在设计资产中授权并生成；Migration、Java、数据库和运行基础设施仍需单独授权。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的 Platform Support 92 表平衡模型落成可重复生成、可独立自动校验并可在 PDManer 4.9.3 中人工评审的第六套 V1 物理模型。

**Architecture:** 共享 PDManer 模板保持只读；Platform Support 生产 Manifest 按八个子域拆分，根生成器负责确定性 ID、模板清洁、本库关系和 Diagram。测试侧维护不导入生产 Manifest 的独立 Oracle，并用现实场景逐表验证模型可用性。

**Tech Stack:** Markdown、Node.js ESM、`node:test`、PDManer 4.9.3 JSON、SHA-256、MySQL 8.0 物理约束语义。

**Spec:** `agent-docs/plans/2026-08-27-platform-support-balanced-physical-model-design.md`，逻辑边界见 `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`；全局规则见 `agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/database/README.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md` 和 `agent-docs/VAULT_SERVICE_ARCHITECTURE.md`。

## Global Constraints

- 精确模型为 92 张表，图组分布固定为 `16 + 19 + 14 + 11 + 9 + 13 + 8 + 2`。
- 明确排除 `resource_grant`、通用 File Grant/Access Event、`document_source_ref`、`notification_preference`、`audit_access_event`、五张 Dictionary 表和五张通用 Job 表。
- 表使用 `t_`、唯一索引使用 `uk_`、访问索引使用 `idx_`；表、字段和索引物理名不超过 32 字符。
- 每表恰有一个服务侧 Snowflake `BIGINT` 主键且 `autoIncrement=false`；UTC 时间使用 `DATETIME(6)`。
- 只建立 Platform Support 本库关系；其他系统、Vault、IdP、对象存储、Gateway Runtime 和 Actor 标识不建物理外键。
- Vault 字段按实际 Reveal、等值查询、展示或元数据并发消费者最小化；禁止默认全套 Tuple，禁止本地 Ciphertext/DEK/KMS Key 和卡数据。
- `config_version` 必须支持 INLINE、VAULT_TOKEN、SECRET_REF 三选一；领域配置不得进入 Platform。
- `file_object` 内容不可变但 AVAILABLE 可因显式重扫进入 QUARANTINED；文档更正必须使用显式 Document/Version 自引用。
- `compensation_event` 使用 `lock_version`；`command_idempotency` 不使用机械 Version；不建设 Inbox/Outbox 或通用 Job 平台。
- 共享模板 SHA-256 固定为 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- 本轮只授权设计资产、生成器、测试和 PDManer JSON；不创建 Migration、Java、数据库或基础设施。
- 当前 worktree 源自包含其他任务未提交内容的工作树快照；Platform 专属文件可直接完成，共享文件必须等待 Risk/Finance 给出稳定基线后做语义合并，禁止整体覆盖。

---

### Task 1: 收敛逻辑模型并建立独立 RED Oracle

**Files:**

- Modify: `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`
- Create: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Create: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**

- Consumes: 92 表设计、PLT-I01～PLT-I14、Platform 事件和全局数据库规则。
- Produces: 测试侧独立 `APPROVED_TABLES`、`APPROVED_DIAGRAMS`、关键字段/索引/关系和禁止项；逻辑文档中的精确 92 表清单。

- [ ] **Step 1: 更新逻辑表和不变量**

  删除 16 张表及其关系、状态机、跨系统说明，写入 92 表八组统计；同步 Vault 最小字段、INLINE 配置、File 重扫、Document 更正、`lock_version` 和非空 Scope 决策。

- [ ] **Step 2: 写独立表集合和分组 Oracle**

  Oracle 以字面量声明 92 个 `t_*` 表名及八组，不导入生产 Manifest；同时声明被删除表、敏感字段、关键 Tuple 和精确图组边界。

- [ ] **Step 3: 写模型结构与安全失败测试**

  测试直接读取未来模型、Generator、Manifest、Policy、物理决策和共享模板，覆盖主键、类型、中文元数据、命名、索引、关系、1:1、空连接、Vault/File、CHECK、Diagram 和确定性摘要。

- [ ] **Step 4: 运行确认 RED**

  Run: `node --test scripts/pdmaner/platform-support-model.test.mjs`

  Expected: Oracle 自身完整性通过；生产 Manifest、Generator、物理决策或模型缺失项明确失败。

### Task 2: 实现八组生产 Manifest DSL 与物理 Policy

**Files:**

- Create: `scripts/pdmaner/platform-support/schema-dsl.mjs`
- Create: `scripts/pdmaner/platform-support/model-policy.mjs`
- Create: `scripts/pdmaner/platform-support/manifest.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/01-identity-access.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/02-file-document.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/03-notification.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/04-webhook.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/05-audit-security.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/06-gateway.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/07-technical-config.mjs`
- Create: `scripts/pdmaner/platform-support/manifests/08-reliability.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**

- Produces: `field()`、类型辅助函数、`defineTable()`、`TABLES/RELATIONS`、`DIAGRAM_GROUPS`、`CHECK_CONTRACTS/CHECK_BINDINGS`、Vault/File Tuple 和安全白名单。

- [ ] **Step 1: 为 DSL 写失败行为测试**

  缺中文名称/说明、重复字段、非 BIGINT 主键、非法物理名和错误索引前缀必须抛出准确错误。

- [ ] **Step 2: 实现最小 DSL**

  显式声明字段、类型、空值、闭合枚举/开放目录、索引、关系和可变性；不得从 Markdown 或测试 Oracle 反向生成生产结构。

- [ ] **Step 3: 按八组声明 92 张表**

  每组表数必须精确匹配 `16/19/14/11/9/13/8/2`；关系子列都具有左前缀索引，1:1 都由单字段 UK 或主键证明。

- [ ] **Step 4: 声明 CHECK 与跨行 Policy**

  至少覆盖 Active Slot、有效区间、状态—时间、Vault/Source Tuple、Document 双输入、Credit Note/Version 自引用、Config 三选一、Callback 验签、Gateway Release Slot、Lease/Fencing 和 Compensation Claim。

- [ ] **Step 5: 运行 Manifest 专项测试**

  Run: `node --test scripts/pdmaner/platform-support-model.test.mjs --test-name-pattern='Oracle|Manifest|Policy|DSL'`

  Expected: 92 表和 Policy 通过；模型工件测试仍因 Generator/JSON 缺失失败。

### Task 3: 实现确定性 PDManer Generator

**Files:**

- Create: `scripts/pdmaner/platform-support/generate-platform-support-model.mjs`
- Create generated: `platform-support-service/docs/database/platform-support.pdma.json`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**

- Produces: `generatePlatformSupportModel()`、`serializePlatformSupportModel()`、`modelTotals()`、`structuralProjection()`、`structuralProjectionDigest()`；CLI 普通生成与 `--check`。

- [ ] **Step 1: 写确定性与漂移测试**

  两次序列化必须逐字节一致；模板摘要变化、缺失工件或 `--check` 漂移必须失败。

- [ ] **Step 2: 实现模板规范化与稳定 ID**

  清空连接，修正 BIGINT→Long、DATETIME(6)→Instant 和模板规则；使用 `SHA-256(code-mor/platform-support/database/v1:path)` 派生稳定 UUIDv5 形态 ID。

- [ ] **Step 3: 生成实体、索引、关系与八个 Diagram**

  每表恰属一个主图组，每条关系恰绘制一次；跨组父表只作 Reference。

- [ ] **Step 4: 生成模型并确认结构 GREEN**

  Run: `node scripts/pdmaner/platform-support/generate-platform-support-model.mjs`

  Run: `node --test scripts/pdmaner/platform-support-model.test.mjs`

  Expected: 除尚未冻结的物理摘要外，模型结构、安全和确定性契约通过。

### Task 4: 冻结物理决策、精确统计和独立摘要

**Files:**

- Create: `platform-support-service/docs/database/PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`
- Modify: `scripts/pdmaner/platform-support-test-oracle/schema.oracle.mjs`
- Test: `scripts/pdmaner/platform-support-model.test.mjs`

**Interfaces:**

- Produces: 精确表/字段/索引/关系/1:1/图组总量、模型 SHA-256、结构 SHA-256，以及未来 Migration/Testcontainers 必须落实的 CHECK 和事务不变量。

- [ ] **Step 1: 从真实模型读取统计与双摘要**

  不预猜数量；使用真实 JSON 与测试侧独立投影固化最终值。

- [ ] **Step 2: 编写物理决策**

  覆盖阶段边界、92 表删减、八图、模板派生、类型/审计、Vault 最小化、File/Document、Notification/Webhook、Audit、Gateway、Config、可靠执行和人工门禁。

- [ ] **Step 3: 固化 Oracle 并跑全套模型测试**

  Run: `node --test scripts/pdmaner/platform-support-model.test.mjs`

  Expected: PASS，且测试不通过生产 Manifest 计算期待结构。

### Task 5: 建立 92 表现实场景契约

**Files:**

- Create: `agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md`
- Create: `scripts/pdmaner/platform-support-scenario-doc.test.mjs`

**Interfaces:**

- Produces: 使用一致 Tenant、Client、User、Order、Document、Notification、Webhook 和 Release ID 贯穿全部 92 表的现实场景。

- [ ] **Step 1: 写场景覆盖失败测试**

  场景文档必须覆盖所有 92 张物理表，出现的表/字段/枚举必须存在于真实模型。

- [ ] **Step 2: 编写八组现实场景**

  覆盖 IdP/API/OTP、文件重扫、双输入凭证及更正、通知晚到 Complaint、Webhook Replay、Audit Hash Chain、Gateway LKG/回滚、INLINE/Vault/Secret 配置和 Compensation Claim。

- [ ] **Step 3: 运行确认 GREEN**

  Run: `node --test scripts/pdmaner/platform-support-scenario-doc.test.mjs`

  Expected: PASS，92 表全部出现且无模型外物理表名。

### Task 6: 在稳定共享基线上同步导航与状态

**Files:**

- Modify after coordination: `AGENTS.md`
- Modify after coordination: `README.md`
- Modify after coordination: `agent-docs/README.md`
- Modify after coordination: `agent-docs/PROJECT_CONTEXT.md`
- Modify after coordination: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify after coordination: `agent-docs/database/README.md`
- Modify if required: `requirements-site/content/requirements.ts`
- Modify if required: `requirements-site/tests/domain-decisions.test.mjs`
- Modify if required: `requirements-site/tests/site-content.test.mjs`

**Interfaces:**

- Consumes: Risk/Finance 线程完成后的主工作区共享文件、Task 4 精确统计和 Task 5 场景。
- Produces: 第六套已生成模型的准确导航和快照，不丢失其他任务的新决策。

- [ ] **Step 1: 获取其他任务最终 changed-files/共享基线**

  通过 Codex 任务消息确认 Risk/Finance 已完成；读取主工作区最终共享文件并与 worktree 基线比较。

- [ ] **Step 2: 对共享文件做语义合并**

  只增加 Platform Support 的物理权威、92 表统计和“六套模型均已生成”状态；保留最新 CCT/Commerce/Payment/Finance/Risk 数字与专项决策。

- [ ] **Step 3: 同步 canonical requirements 的必要术语**

  将 `Technical Job` 收敛为“可靠技术执行”，固定 92 表、删除通用 Job/Dictionary/Preference/ACL 的边界；不改写其他系统业务语义。

- [ ] **Step 4: 运行需求回归**

  Run: `npm --prefix requirements-site test`

  Expected: PASS；若共享基线仍有与 Platform 无关的失败，单独报告并由对应任务处理。

### Task 7: 全量验证与冲突感知集成

**Files:**

- Verify all Platform Support files and coordinated shared files only.

**Interfaces:**

- Produces: 可交给用户在 PDManer 4.9.3 中人工 Review 的确定性第六套模型，以及不会覆盖其他 chat 的主工作区增量。

- [ ] **Step 1: 验证 Platform 生成物**

  Run: `node --test scripts/pdmaner/platform-support-model.test.mjs scripts/pdmaner/platform-support-scenario-doc.test.mjs`

  Run: `node scripts/pdmaner/platform-support/generate-platform-support-model.mjs --check`

- [ ] **Step 2: 回归其余五套模型**

  Run: `node --test scripts/pdmaner/*-model.test.mjs scripts/pdmaner/*-scenario-doc.test.mjs`

  Expected: 全部 PASS；外部既存失败单独报告，不改其他系统模型绕过。

- [ ] **Step 3: 验证模板、JSON 和敏感内容**

  Run: `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json`

  Run: `node -e "JSON.parse(require('node:fs').readFileSync('platform-support-service/docs/database/platform-support.pdma.json','utf8'))"`

  Run: `rg -n -i 'jdbc:|password|private_key|\\bpan\\b|\\bcvv\\b|card_exp' platform-support-service/docs/database/platform-support.pdma.json`

- [ ] **Step 4: 对比并集成到主工作区**

  先再次联系活跃 Code-MoR 任务；仅将 Platform 专属文件和已三方核对的共享语义增量应用到主工作区，绝不整体复制共享文件。

- [ ] **Step 5: 最终验证与人工门禁交接**

  Run: `npm --prefix requirements-site test`

  Run: `git diff --check`

  明确自动验证结果，并保留 PDManer 4.9.3 打开模型、检查八图和执行应用内规范检查的人工门禁。

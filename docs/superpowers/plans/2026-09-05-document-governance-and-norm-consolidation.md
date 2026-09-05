# Document Governance and Norm Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans or superpowers:subagent-driven-development, preserve pre-existing dirty work, and do not commit unless the user explicitly asks.

**Goal:** 将 Code-MoR 的规范权威、实现状态和历史记录拆开，传播 2026-09-05 已确认的六项决定，并建立可验证的后续迁移清单。

**Architecture:** 根 `AGENTS.md` 只做稳定护栏与路由；`agent-docs/DOCUMENT_GOVERNANCE.md` 定义主题所有权；`agent-docs/CURRENT_IMPLEMENTATION.md` 是唯一实现状态台账；专项规范只保留本主题规则；仓库 README 只描述本仓库运行和验证事实；历史 specs/plans 通过元数据说明有效范围。

**Tech Stack:** Markdown、Node.js 静态文档门禁、Maven/JUnit 5、仓库 `local-environment`。

**Spec:** [`agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md`](../../../agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md)

**2026-09-05 execution note:** 本轮文档治理已完成 Task 1–3、Task 5–7。最终门禁为 Requirements Site 123/123、六库 Model 183/183、Scenario-doc 154/154，额外包含 Vault 的 PDManer 总套件 348/348；61 个改动 Markdown 的相对链接与结构检查通过，根仓库及有改动的嵌套仓库 `git diff --check` 通过。Task 4 是独立代码迁移，不属于“规范已收敛”的完成声明，继续由 `CURRENT_IMPLEMENTATION.md` 的 TEST-01 与 RUN-01 跟踪。六库模型、DDL 和真实消费者迁移同样未因本文档修改而完成。

## Global Constraints

- 保留当前工作区中用户已有未提交改动，只做逐段补丁。
- 不把文档更新写成代码、模型、物理数据库或部署已完成。
- 不修改六库 PDManer、DDL 或生产代码，除非对应专项任务先建立失败门禁和迁移设计。
- 不创建 Git commit、分支、PR 或部署。

---

## Task 1: 建立治理入口和当前状态台账

**Files:**

- Create: `agent-docs/DOCUMENT_GOVERNANCE.md`
- Create: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Modify: `agent-docs/README.md`
- Modify: `AGENTS.md`

**Steps:**

1. 定义主题权威矩阵、冲突处理、状态元数据和历史归档规则。
2. 汇总当前组件、模型、验证和部署状态，并显式区分 `implemented`、`verified`、`deployed` 与 `planned`。
3. 将根入口和导航页中的易变状态替换为状态台账链接，修正已知的 Token、Gateway、Platform、Operations 与 Vault 漂移。
4. 检查所有新增链接可解析，并扫描仍会误导当前工作的旧摘要。

## Task 2: 落实 Java 模块与 DataObject 边界

**Files:**

- Modify: `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- Modify: `.cursor/rules/java-service-return-boundary.mdc`
- Modify: `.cursor/rules/java-service-boundaries.mdc`

**Steps:**

1. 写清 DataObject 在 Owner JAR 内允许使用，跨模块合同必须使用 Domain/Application Command/Result。
2. 将 Domain Service `interface + impl` 改为按边界必要性决定。
3. 同步当前异常规则和 BFF Client Port 简单转发例外。
4. 用关键词和上下文扫描确认不存在反向表述。

## Task 3: 同步 Vault、数据库、补偿和测试规范

**Files:**

- Modify: `agent-docs/TECHNOLOGY_STACK.md`
- Modify: `agent-docs/APPLICATION_ARCHITECTURE.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/COMPENSATION_EVENT_GUIDE.md`
- Modify: `agent-docs/UNIT_TEST_GUIDE.md`
- Modify: `local-environment/README.md`
- Modify: `vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md`

**Steps:**

1. 确认 Go Server + Java Client 是 Vault V1 正式实现，同时保留未生产就绪边界。
2. 将当前 Vault 契约收敛到 Protect/Reveal 与三表模型，把旧 Binding/Copy/Blind Index 等标记为待设计 V2 消费者缺口。
3. 将通用 Compensation Event 说明收敛到 `event_key/event_type/sub_event_type` 当前模型；六库模型迁移只登记状态，不在本任务静默改模。
4. 取消 Foundation Testcontainers/自动 Schema 的规范例外，并登记代码迁移为未完成。
5. 对物理 CHECK/FK 残留只记录专项迁移，不声称已经从数据库移除。

## Task 4: 建立静态门禁并修复无争议运行偏差

**Files:**

- Modify: `scripts/verify-java-foundation.mjs`
- Modify: Foundation Redis/Compensation Event test configuration and tests
- Modify: Gateway local Nacos configuration and related tests/docs

**Steps:**

1. 先增加会因现有 Testcontainers 和 `spring.sql.init` 而失败的门禁测试，运行并保存 RED 证据。
2. 将 Redis 集成测试迁移到 `mor-local`、唯一命名空间和作用域清理；将 Compensation Event Schema 改为手工准备且缺失快速失败。
3. 运行门禁及受影响 Maven 测试获得 GREEN 证据。
4. 先增加 Gateway Nacos 默认 Namespace 的失败断言，再把 `public` 改为 `mor-local` 并运行相关测试。

## Task 5: 专项模型迁移计划

**Files:**

- Create: `docs/superpowers/plans/2026-09-05-vault-consumer-model-alignment.md`
- Create: `docs/superpowers/plans/2026-09-05-compensation-event-six-model-alignment.md`
- Create: `docs/superpowers/plans/2026-09-05-finance-manual-tax-reconciliation.md`
- Create: `docs/superpowers/plans/2026-09-05-physical-constraint-removal.md`

**Steps:**

1. 对每个迁移列出当前模型证据、目标契约、数据迁移、应用保护、反例测试和人工 SQL 门禁。
2. 不在消费者能力、状态机或数据迁移未闭合时直接删除旧字段/约束。
3. 每项完成后更新 `CURRENT_IMPLEMENTATION.md`，分别记录实现、验证和部署状态。

## Task 6: 全局 fallback 收敛

**Files:**

- Modify outside workspace after explicit filesystem approval: `/Users/kenny/.codex/AGENTS.md`

**Steps:**

1. 先在工作区生成精简候选和原文件备份。
2. 将全局规则缩成项目无本地规范时使用的框架中立 fallback，删除固定公司包、Dubbo、`@Transactional`、固定测试 Profile 和机械测试矩阵。
3. 经受控文件权限写入后，重新读取并验证项目级 `AGENTS.md` 明确优先。

## Task 7: 最终验证

**Steps:**

1. 运行 Markdown 相对链接检查、冲突关键词检查和 `git diff --check`。
2. 审阅差异，确认未覆盖既有修改、未把历史计划当成当前规则、未作虚假的实施/部署声明。
3. 汇报已完成项、已决定但待迁移项、验证证据和仍需用户参与的人工门禁。

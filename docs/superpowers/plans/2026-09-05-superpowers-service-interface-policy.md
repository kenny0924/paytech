# Superpowers Workflow and Service Interface Policy Implementation Plan

> **For agentic workers:** 按根 `AGENTS.md` 中 `Change discipline` 的真实触发条件选择实现 skill；共享规则文件保持 single-writer，不为执行本计划机械启动 worktree、分支收尾或代码评审流程。步骤使用 checkbox（`- [ ]`）跟踪。

**Goal:** 将用户确认的 Superpowers 分阶段使用策略和业务 Service 强制 `interface + Impl` 规则传播到唯一权威、项目入口、Cursor 薄投影与自动门禁。

**Architecture:** 新决策记录负责取代 2026-09-05 前序决策中的两处旧口径；根 `AGENTS.md` 的 `Change discipline` 拥有 Agent/Superpowers 工作流，`DEVELOPMENT_CONVENTIONS.md` 拥有通用 Service 规则，`JAVA_PROJECT_STRUCTURE.md` 拥有 Java 分层映射，其他引用和 `.cursor/rules` 只做薄投影。Requirements Site 文档门禁先失败、再随规则传播恢复为绿色。

**Tech Stack:** Markdown、Cursor MDC、Node.js `node:test`、Requirements Site。

**Spec:** 当前对话中用户于 2026-09-05 确认的四类代码阶段加速能力边界，以及 `agent-docs/decisions/2026-09-05-superpowers-and-service-interface-policy.md`。

## Global Constraints

- 设计阶段默认使用适用的 Superpowers；多步骤设计使用 `writing-plans`。
- 代码阶段按真实触发条件使用 TDD、并行/计划执行、系统化调试和完成前验证，不按清单机械调用全部 skill。
- Worktree、分支收尾和代码评审类 skill 只有任务确有需要或用户明确要求时使用；不得因此自行创建分支、提交或 PR。
- 所有业务 Service 和 Domain Service 必须使用 `interface + *Impl`；Processor、Controller 仍使用具体类，Repository 保持 `interface + *RepositoryImpl`。
- 保留现有用户改动，不提交、不部署、不修改生产代码或数据库。

---

### Task 1: 建立失败的规范门禁

**Files:**

- Modify: `requirements-site/tests/site-content.test.mjs`

**Interfaces:**

- Consumes: 根 `AGENTS.md`、`DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md` 与 Cursor rule 文本。
- Produces: 两个静态测试，分别锁定 Superpowers 分阶段策略和 Service `interface + Impl`。

- [x] **Step 1: 写入 Superpowers 工作流断言**

断言设计阶段默认使用 `brainstorming`/`writing-plans`，代码阶段包含按触发条件使用的 TDD、并行、调试、验证能力，并且不再保留“除非用户明确要求否则完全不用 Superpowers”的旧规则。

- [x] **Step 2: 写入 Service 接口断言**

断言所有业务 Service/Domain Service 使用 `interface + *Impl`，并拒绝“模块内单实现可直接使用具体类”的旧规则。

- [x] **Step 3: 运行测试并确认 RED**

Run: `node --test --test-name-pattern='Superpowers workflow|Service interface' requirements-site/tests/site-content.test.mjs`

Expected: 两项均因旧规范仍存在或新规则尚不存在而失败。

### Task 2: 写入决策和主题 Owner

**Files:**

- Create: `agent-docs/decisions/2026-09-05-superpowers-and-service-interface-policy.md`
- Modify: `agent-docs/DEVELOPMENT_CONVENTIONS.md`
- Modify: `agent-docs/JAVA_PROJECT_STRUCTURE.md`
- Modify: `agent-docs/DOCUMENT_GOVERNANCE.md`

**Interfaces:**

- Consumes: Task 1 的失败断言。
- Produces: 当前业务与工程语义的唯一正式来源。

- [x] **Step 1: 记录窄范围取代关系**

新决策明确取代前序“Superpowers 仅显式请求时使用”和“模块内单实现 Service 可直接使用具体类”两项口径，不改动 DataObject Owner-JAR 边界。

- [x] **Step 2: 更新开发约定**

将设计/实现阶段的 skill 选择写成按触发条件执行的最小清单；将业务 Service 和 Domain Service 统一为接口及 `Impl` 实现。

- [x] **Step 3: 更新 Java 分层规范**

保持 Processor、Controller、Repository 现有边界，只替换 Service 具体类例外及相关示例/检查清单。

- [x] **Step 4: 路由新决策**

在治理文档中登记新决策和取代范围。

### Task 3: 同步入口和薄投影

**Files:**

- Modify: `AGENTS.md`
- Modify: `.cursor/rules/java-service-boundaries.mdc`
- Create: `.cursor/rules/superpowers-workflow.mdc`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`

**Interfaces:**

- Consumes: Task 2 的主题 Owner 规则。
- Produces: 自动注入的短规则与未迁移代码台账。

- [x] **Step 1: 更新根入口**

根入口承载分阶段 Superpowers 工作流，并保留 Service 接口主题 Owner 的薄护栏。

- [x] **Step 2: 更新 Java Cursor rule**

在现有 Java 文件范围内加入 Service/Domain Service `interface + Impl`，不得复制整篇架构规范。

- [x] **Step 3: 新增 Superpowers Cursor rule**

创建少于 50 行、`alwaysApply: true` 的单一关注点规则，说明设计阶段和实现阶段的 skill 选择及禁止机械调用。

- [x] **Step 4: 登记代码迁移状态**

在 `CURRENT_IMPLEMENTATION.md` 新增已确认但需按触及范围审计既有 Service 的待办，不宣称现有代码已经全部迁移。

### Task 4: GREEN 与一致性验证

**Files:**

- Modify: 本计划的执行说明。

**Interfaces:**

- Consumes: Tasks 1–3 的全部变更。
- Produces: 可复现的完成证据。

- [x] **Step 1: 运行聚焦测试**

Run: `node --test --test-name-pattern='Superpowers workflow|Service interface' requirements-site/tests/site-content.test.mjs`

Expected: PASS。

- [ ] **Step 2: 运行完整 Requirements Site 测试**（本轮改动后曾 125/125 通过；最终复跑受并行 Gateway 状态更新导致的既有断言漂移影响，当前为 124/125）

Run: `npm --prefix requirements-site test`

Expected: 全部通过。

- [x] **Step 3: 运行冲突扫描和补丁检查**

扫描现行文档中的旧口径；运行 Markdown 相对链接检查与根仓库 `git diff --check`。

- [x] **Step 4: 更新执行记录**

只记录实际通过的门禁；既有生产 Service 的迁移继续留在当前状态台账。

## 执行记录

- RED：聚焦测试首次运行 2 项均失败，分别命中旧的 Superpowers 使用限制和 Service 单实现例外；终审加固断言首次运行也以 2/2 失败证明 Owner 和不变边界尚未写全。
- GREEN：加固后的聚焦测试 2/2 通过。`npm --prefix requirements-site test` 在本轮规则传播后曾构建成功且 125/125 通过；随后并行任务更新 Gateway Target/Route 当前状态，最终复跑构建成功但为 124/125，唯一失败是旧的 `the current ledger owns the Platform snapshot and governance routes control-plane authority` 仍要求 Target/Route/Console 管理命令全部未实现。
- 一致性：现行规则扫描只命中一条明确标注为“历史口径”的旧决策文本；9 个本次涉及的 Markdown 文件相对链接检查通过；`git diff --check` 通过。
- 迁移边界：本次没有修改生产 Java；既有仓库的 Service 形态全量审计仍登记为 `CURRENT_IMPLEMENTATION.md` 的 `JAVA-02`。

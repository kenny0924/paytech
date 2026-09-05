# Superpowers 工作流与 Service 接口形态决策

- Decision status: Confirmed
- Implementation status: In progress
- Deployment status: Not applicable
- Decision date: 2026-09-05
- Supersedes: `agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md` 第 2 节 D3 最后一段；`agent-docs/DEVELOPMENT_CONVENTIONS.md` 中旧的 Service 单实现例外和“仅用户显式要求才使用 Superpowers”规则；`agent-docs/JAVA_PROJECT_STRUCTURE.md` 中旧的 Service 单实现例外；根 `AGENTS.md` 中旧的 Superpowers 显式请求限制；`.cursor/rules/java-service-boundaries.mdc` 中旧的 Service 单实现例外；2026-09-05 之前历史 Spec/Plan 中与本文冲突的 Agent 工作流指令（仅该指令范围）

## 1. 背景

现有规范把 Superpowers 同时描述为“只有用户明确要求才使用”和设计阶段的标准工具，也允许模块内单实现的 Domain Service 直接使用具体类。这两处口径与用户当前确认的开发方式冲突，需要由一个窄范围决策统一。

本文只决定 Agent 工作流和 Java Service 形态。DataObject 的 Owner-JAR 边界、Processor 必要性门禁、Repository 形态、测试链路、事务规则和业务事实不因本文改变。

## 2. 已确认决定

### D1. Superpowers 按设计阶段和代码阶段选择

设计阶段默认使用与任务匹配的 Superpowers。新增功能、改变行为或架构探索先使用 `brainstorming`；已经形成并获准执行的多步骤设计使用 `writing-plans`。纯问答、只读状态检查和无需设计判断的机械操作不为形式完整而启动设计 skill。

代码阶段只使用真实触发的实现 skill：功能、修复或重构使用 `test-driven-development`；存在两个以上可独立推进的子任务时使用 `dispatching-parallel-agents`；已有批准计划且工作适合拆分时，在 `subagent-driven-development` 与 `executing-plans` 中选择一种；遇到 bug、测试失败或异常行为时使用 `systematic-debugging`；报告完成前使用 `verification-before-completion`。不得为了凑清单机械串行调用全部 skill。

`using-git-worktrees`、`finishing-a-development-branch`、`requesting-code-review` 和 `receiving-code-review` 只在任务确实需要隔离、集成或独立评审，或者用户明确要求时使用。使用 skill 本身不授权创建分支、提交、推送或 PR。

### D2. 业务 Service 固定使用接口与实现分离

所有业务 Service 必须使用 `interface + *Impl`；所有 Domain Service 必须使用 `interface + *Impl`。接口按业务能力命名，实现使用同名接口加 `Impl`，只有实现类标注 Spring `@Service`。Controller 和 Processor 继续使用具体类；Repository 继续使用 `interface + *RepositoryImpl`。

该形态规则不要求创建没有业务职责的空 Service。先通过必要性门禁判断是否真的需要一个 Service；一旦创建业务 Service 或 Domain Service，就不得再以“仅模块内部使用”或“只有一个实现”为由省略接口。调用方依赖接口，不依赖 `*Impl`。

## 3. 不变边界与迁移状态

- Agent/Superpowers 工作流的唯一现行 Owner 是根 `AGENTS.md` 的 `Change discipline`；本文记录决定及取代范围，`DEVELOPMENT_CONVENTIONS.md` 和 Cursor rule 只做开发侧薄投影。
- DataObject 仍可在其 Owner 业务能力 Maven JAR 内使用；跨 Maven 模块和 HTTP API 仍不得暴露 DataObject。
- Processor 仍按用例复杂度决定是否创建，不为简单转发增加空编排层。
- 本决策不要求为单个 Processor 预建抽象基类，也不允许无职责的空接口或一行代理。
- 现行规范、入口、Cursor 薄投影和静态门禁在本次变更中同步；既有 Java 仓库是否全部符合必须另行扫描和验证，不能由文档更新推断。

当前迁移状态见 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。完整主题路由见 [`DOCUMENT_GOVERNANCE.md`](../DOCUMENT_GOVERNANCE.md)。

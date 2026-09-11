# Superpowers 显式授权与 Service 接口形态决策

- Decision status: Confirmed
- Implementation status: In progress
- Deployment status: Not applicable
- Decision date: 2026-09-05
- Last amended: 2026-09-08
- Supersedes: 工作流范围内取代本文 2026-09-05 原 D1 及所有默认或强制调用 Superpowers 的历史规则、Spec 和 Plan 指令；Service 形态范围内继续取代 `agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md` 第 2 节 D3 最后一段、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 与 `agent-docs/JAVA_PROJECT_STRUCTURE.md` 的旧 Service 单实现例外，以及 `.cursor/rules/java-service-boundaries.mdc` 的旧投影

## 1. 背景

本文最初于 2026-09-05 统一了 Superpowers 分阶段工作流和 Service 接口形态。2026-09-08，用户进一步明确要求：本项目默认不再使用任何 Superpowers skill，只有当前请求明确指定时才可使用；Service 接口形态决定保持不变。

本文只决定 Agent 工作流和 Java Service 形态。DataObject 的 Owner-JAR 边界、Processor 必要性门禁、Repository 形态、测试链路、事务规则和业务事实不因本文改变。

## 2. 已确认决定

### D1. Superpowers 仅按当前用户的明确指定使用

默认不得调用任何 Superpowers skill，包括所有 `superpowers:*` 以及由 Superpowers 插件提供的同类 skill。只有当前用户在当前请求中明确指定使用 Superpowers 或点名某个具体 Superpowers skill 时，主 Agent 和子 Agent 才可在该请求授权范围内调用。

授权不得从任务类型、历史回合、计划中的 `REQUIRED SUB-SKILL`、`docs/superpowers/**` 文档路径或其他 Agent 的选择中推断，也不得跨请求沿用。未获明确授权时仍须按照项目规则完成必要设计、测试先行、系统化诊断和完成前验证，但使用普通工作流与可用工具，不调用 Superpowers skill。

非 Superpowers skill 继续按其正常触发规则使用。任何 skill 都不授权创建分支、提交、推送、部署、发布或 PR，这些动作继续服从独立授权规则。

### D2. 业务 Service 固定使用接口与实现分离

所有业务 Service 必须使用 `interface + *Impl`；所有 Domain Service 必须使用 `interface + *Impl`。接口按业务能力命名，实现使用同名接口加 `Impl`，只有实现类标注 Spring `@Service`。Controller 和 Processor 继续使用具体类；Repository 继续使用 `interface + *RepositoryImpl`。

该形态规则不要求创建没有业务职责的空 Service。先通过必要性门禁判断是否真的需要一个 Service；一旦创建业务 Service 或 Domain Service，就不得再以“仅模块内部使用”或“只有一个实现”为由省略接口。调用方依赖接口，不依赖 `*Impl`。

## 3. 不变边界与迁移状态

- Agent/Superpowers 工作流的唯一现行 Owner 是根 `AGENTS.md` 的 `Change discipline`；本文记录 2026-09-08 显式授权修订及取代范围，`DEVELOPMENT_CONVENTIONS.md` 和 Cursor rule 只做开发侧薄投影。
- DataObject 仍可在其 Owner 业务能力 Maven JAR 内使用；跨 Maven 模块和 HTTP API 仍不得暴露 DataObject。
- Processor 仍按用例复杂度决定是否创建，不为简单转发增加空编排层。
- 本决策不要求为单个 Processor 预建抽象基类，也不允许无职责的空接口或一行代理。
- 现行规范、入口、Cursor 薄投影和静态门禁在本次变更中同步；既有 Java 仓库是否全部符合必须另行扫描和验证，不能由文档更新推断。

当前迁移状态见 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。完整主题路由见 [`DOCUMENT_GOVERNANCE.md`](../DOCUMENT_GOVERNANCE.md)。

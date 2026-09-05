# Code-MoR 文档治理

最后更新：2026-09-05

状态：Confirmed。本文定义文档权威、状态和维护方式，不定义业务事实。

## 1. 冲突处理顺序

Code-MoR 不再用“所有文档排成一条绝对优先级”的方式解决冲突。先确定主题，再使用该主题的唯一 Owner：

1. 当前用户对同一问题的明确决定；
2. 项目根 [`AGENTS.md`](../AGENTS.md) 仅对 Agent 行为、项目级安全护栏、变更纪律和任务路由生效；
3. 下表所列主题 Owner 对各自业务、架构和技术语义生效；
4. 组件仓库 README 对该仓库当前实现、运行和验证状态的记录；
5. `.cursor/rules/*.mdc` 等自动注入摘要；
6. 用户级全局 `AGENTS.md`，仅在项目没有本地规则时作为 fallback；
7. 已被取代的设计、实施计划、参考项目和非当前 worktree。

若当前用户决定改变了既有规则，应先写入决策记录，再同步主题 Owner 和自动注入摘要。不得把两个冲突版本拼成没有人批准的第三种方案。

根 `AGENTS.md` 中为防误操作而保留的业务边界是主题 Owner 的薄安全投影，不因此取得业务语义所有权。若投影与主题 Owner 漂移，应遵循主题 Owner 并在同一变更中修复投影；只有当前用户的明确决定可以改变 Owner 规则。

## 2. 主题 Owner

| 主题 | 唯一现行权威 | 其他文档的职责 |
|---|---|---|
| 文档权威、状态与归档 | 本文 | 入口只链接本文 |
| 当前实现、验证、部署和待迁移项 | [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) | README 提供证据，不复制全局状态矩阵 |
| Agent/Superpowers 工作流 | 根 [`AGENTS.md`](../AGENTS.md) 的 `Change discipline` 是唯一现行权威；[`2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md) 记录确认理由与取代范围 | `DEVELOPMENT_CONVENTIONS.md` 和 Cursor rule 只做开发侧薄投影 |
| 项目背景、六系统职责和术语 | [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md) | 专项文档只补充本领域事实 |
| 编码、命名、异常、事务和通用依赖习惯 | [`DEVELOPMENT_CONVENTIONS.md`](./DEVELOPMENT_CONVENTIONS.md) | Cursor rule 仅薄投影 |
| Java 模块、分层、包、HTTP API 和跨模块边界 | [`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) | 系统模块设计只记录本系统映射 |
| 技术产品、版本和允许/禁止能力 | [`TECHNOLOGY_STACK.md`](./TECHNOLOGY_STACK.md) | README 记录实际启用值 |
| 入口应用、Gateway、BFF、前端和部署边界 | [`APPLICATION_ARCHITECTURE.md`](./APPLICATION_ARCHITECTURE.md) | 组件 README 记录运行方式 |
| 本机开发拓扑、端口和依赖准备 | [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) | `local-environment/README.md` 是操作手册 |
| Java 测试策略、auto/manual 和覆盖率 | [`UNIT_TEST_GUIDE.md`](./UNIT_TEST_GUIDE.md) | 各仓库 README 记录已运行证据 |
| 数据库通用物理规则 | [`DATABASE_DESIGN_GUIDE.md`](./DATABASE_DESIGN_GUIDE.md) | 不保存六库实施状态 |
| 六系统业务数据事实和状态机 | [`database/README.md`](./database/README.md) 路由的系统文档、当前专项决定和 PDManer | 通用手册不重定义业务语义 |
| 本地可靠执行 | [`COMPENSATION_EVENT_GUIDE.md`](./COMPENSATION_EVENT_GUIDE.md) | 六库文档只记录本系统事件 |
| Vault V1 | [Vault V1 Spec](../docs/superpowers/specs/2026-09-02-vault-database-design.md) 拥有安全、信任与行为语义；[`OpenAPI`](../vault-service/api/openapi/v1.yaml) 拥有 HTTP Wire Contract；[`VAULT_PHYSICAL_DECISIONS.md`](../vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md)、PDManer 与评审后 Schema SQL 拥有持久化结构 | [`VAULT_SERVICE_ARCHITECTURE.md`](./VAULT_SERVICE_ARCHITECTURE.md) 只保留未被 V1 取代的安全背景；三类权威漂移时不得互相猜测，按各自子主题修复并记录冲突 |
| 业务需求和全局时序 | `requirements-site/content/requirements.ts` 与 `requirements-site/content/global-use-case-sequences.mjs` | 派生站点不得反向成为事实源 |

某项专项决策可以窄范围取代主题 Owner 的一部分，但必须声明 `Superseded sections` 或准确作用域；未命中的其余部分继续有效。

## 3. 文档类型与允许内容

### 3.1 稳定规范

稳定规范只描述长期边界、不变量和选择方法，不复制某天通过了多少测试、部署了多少容器或实现了多少接口。状态变化时通常不应改写稳定规则。

### 3.2 当前状态台账

`CURRENT_IMPLEMENTATION.md` 是跨仓库当前状态的唯一摘要。每条状态必须区分：

- `Designed`：契约已批准；
- `Implemented`：当前主工作区已有实现；
- `Verified`：记录的门禁在对应代码/环境上通过；
- `Deployed`：明确环境已经运行该制品；
- `Pending`：决定已作出但实现、验证或部署尚未完成。

`Implemented` 不能推出 `Verified` 或 `Deployed`，本地验证也不能推出生产就绪。

### 3.3 仓库 README

README 只拥有本仓库的构建、配置、运行、已实现/未实现能力和可复现验证命令。通用规范应链接主题 Owner；不得在 README 中另建一套异常、事务、数据库或测试规范。

### 3.4 决策记录

跨文档决策放在 `agent-docs/decisions/`，至少包含：

```text
Decision status: Proposed | Confirmed | Superseded
Implementation status: Not started | In progress | Complete
Deployment status: Not applicable | Not deployed | Partial | Deployed
Decision date: YYYY-MM-DD
Supersedes / Superseded sections: 精确路径或作用域
```

决策记录写“为什么和决定了什么”，不复制实施步骤。

### 3.5 Spec、Plan 与历史

Spec 冻结目标和契约，Plan 记录可执行步骤，实施记录保存实际证据。它们必须保留当时上下文；被取代时增加状态与窄范围后继链接，不重写历史使其看起来从未发生。

## 4. 更新规则

1. 先确定主题 Owner，再修改内容；同义规则只写一次。
2. 规则改变时同步薄投影和必要的架构门禁；状态改变时只更新状态台账和组件 README。
3. 数据库模型、DDL、代码和部署分别验证。文档更新不得宣称其他层已经迁移。
4. 模型统计应从生成器或单一清单校验；入口只引用状态台账，不在多个文件手工维护。
5. 历史扫描默认排除 `.git/`、`.worktrees/`、构建输出和依赖目录；历史关键词不能被误报为当前规范。
6. 当前规范若必须提到已废弃机制，应使用“禁止/历史/待迁移”明确限定，不能以现在时提供可复制示例。
7. 修改共享文档前检查工作区差异并使用逐段补丁；不得覆盖其他任务的未提交内容。

## 5. 当前已冻结的冲突结论

2026-09-05 的 DataObject、Vault V1、Foundation 测试基础设施、Finance 手工缴税和历史治理决定见 [`decisions/2026-09-05-document-governance-and-conflict-resolution.md`](./decisions/2026-09-05-document-governance-and-conflict-resolution.md)。该记录是这些具体冲突的裁决来源。

同日确认的 Superpowers 分阶段工作流与业务 Service/Domain Service 强制 `interface + *Impl` 见 [`decisions/2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)。该决策只取代旧的 Agent 工作流指令和 Service 单实现例外；DataObject Owner-JAR 边界及其他既有决定继续有效。

同日确认的业务模块默认无直接依赖、稳定能力单向例外、Owner 合同边界与 `web` Processor 编排规则见 [`decisions/2026-09-05-business-module-collaboration-policy.md`](./decisions/2026-09-05-business-module-collaboration-policy.md)。[`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) 仍是这类 Java 模块协作语义的唯一现行权威，系统模块设计只做具体映射。

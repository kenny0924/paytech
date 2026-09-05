# Code-MoR

Code-MoR 是一个本地维护的 Merchant of Record 平台工作区，包含需求目录、架构与数据库设计、共享 Java 基础设施、六个核心系统，以及 Gateway、BFF、前端和 Vault 等独立仓库。

本工作区中的“已设计”“已实现”“已验证”“已部署”是四种不同状态。请从 [`agent-docs/CURRENT_IMPLEMENTATION.md`](./agent-docs/CURRENT_IMPLEMENTATION.md) 查看当前状态，不要从历史 Spec、Plan、模型统计或本页推断实现进度。

## 从这里开始

1. [`AGENTS.md`](./AGENTS.md)：项目级安全护栏和任务路由。
2. [`agent-docs/DOCUMENT_GOVERNANCE.md`](./agent-docs/DOCUMENT_GOVERNANCE.md)：按主题确定唯一权威并解决冲突。
3. [`agent-docs/CURRENT_IMPLEMENTATION.md`](./agent-docs/CURRENT_IMPLEMENTATION.md)：当前实现、验证、部署和待迁移台账。
4. [`agent-docs/README.md`](./agent-docs/README.md)：按任务选择必须阅读的专题文档。
5. [`agent-docs/PROJECT_CONTEXT.md`](./agent-docs/PROJECT_CONTEXT.md)：稳定的项目目标、系统边界和术语。

2026-09-05 已确认的文档冲突结论，包括 Owner-JAR DataObject 边界、Vault V1、测试基础设施和 Finance 手工缴税，见 [`文档治理与规范冲突收敛决策`](./agent-docs/decisions/2026-09-05-document-governance-and-conflict-resolution.md)。

同日确认的 Superpowers 分阶段工作流和业务 Service/Domain Service `interface + *Impl` 规则，见 [`Superpowers 工作流与 Service 接口形态决策`](./agent-docs/decisions/2026-09-05-superpowers-and-service-interface-policy.md)。

## 工作区边界

六个核心系统分别是 Client & Contract、Commerce、Payment Orchestration、Risk & Compliance、Finance 和 Platform Support。API Gateway、入口应用、BFF 和 Vault 不成为第七个核心业务系统。

各应用仓库独立版本化；根仓库维护跨系统需求、架构、联合验证和本地环境。数据库设计从 [`agent-docs/database/README.md`](./agent-docs/database/README.md) 路由，Java 开发从 [`agent-docs/DEVELOPMENT_CONVENTIONS.md`](./agent-docs/DEVELOPMENT_CONVENTIONS.md) 与 [`agent-docs/JAVA_PROJECT_STRUCTURE.md`](./agent-docs/JAVA_PROJECT_STRUCTURE.md) 开始。

## 本地需求站点

前置条件：Node.js `>=22.13.0`。

```bash
npm --prefix requirements-site install
npm --prefix requirements-site run dev
```

本地访问：[http://localhost:3000/](http://localhost:3000/)。未经明确要求，不发布到外部环境。

## 验证

```bash
npm --prefix requirements-site test
```

组件级构建、测试和运行命令由对应仓库 README 维护。本地 MySQL、Redis、RabbitMQ、Nacos 等依赖的准备方式见 [`local-environment/README.md`](./local-environment/README.md)；自动测试只复用已由操作员启动和准备的仓库环境，不创建或迁移业务 Schema。

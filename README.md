# Code-MoR

Code-MoR 是一个本地维护的 Merchant of Record 平台单仓库，包含需求目录、架构与数据库设计、共享 Java 基础设施、六个核心系统，以及 Gateway、BFF、前端和 Vault 等应用目录。

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

所有应用目录随根仓库统一版本化；根目录还维护跨系统需求、架构、联合验证和本地环境。数据库设计从 [`agent-docs/database/README.md`](./agent-docs/database/README.md) 路由，Java 开发从 [`agent-docs/DEVELOPMENT_CONVENTIONS.md`](./agent-docs/DEVELOPMENT_CONVENTIONS.md) 与 [`agent-docs/JAVA_PROJECT_STRUCTURE.md`](./agent-docs/JAVA_PROJECT_STRUCTURE.md) 开始。

## 工程结构

全部 Java 与 Vault 工程位于 [`paytech/`](./paytech/)，由单一 Maven Reactor [`paytech/pom.xml`](./paytech/pom.xml) 聚合，唯一 Wrapper 为 `paytech/mvnw`（构建治理见 [`paytech/build/README.md`](./paytech/build/README.md)）：

| 目录 | 内容 |
|---|---|
| `paytech/build/` | `paytech-dependencies`（BOM）与 `paytech-parent`（父 POM） |
| `paytech/foundation/` | 共享 Foundation 模块（Starter、messaging、internal-auth、补偿事件等） |
| `paytech/<service>/` | 每个服务一个目录，模块为 `paytech-<service>-{api,core,app,test}`；目录与端口见各服务 README |
| `paytech/vault/` | Vault Go 服务与 Java Client（`paytech-vault-client`、`paytech-vault-test`） |
| `paytech/tools/` | 架构规则（`arch-rules`）、实例清单（`instance-manifest`）、迁移执行器（`migration-runner`） |

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
cd paytech
./mvnw -B verify                                          # 全部 Reactor 模块
./mvnw -B verify -pl <service>/paytech-<service>-test -am  # 单个服务及其上游
```

前端工程（`operations-console-web`、`client-console-web`、`checkout-web`、`business-portal-web`）、`business-portal-bff`，以及仓库级目录 `agent-docs/`、`docs/`、`requirements/`、`requirements-site/`、`scripts/`、`local-environment/`、`test-environment/` 不在 Reactor 内。2026-10-17 前的 12 个旧工程根目录已全部并入 `paytech/`，搬迁记录见 [`requirements/20261017/architecture-V2骨架与模块搬迁/`](./requirements/20261017/architecture-V2骨架与模块搬迁/)。

## 本地需求站点

前置条件：Node.js `>=22.13.0`。

```bash
npm --prefix requirements-site install
npm --prefix requirements-site run dev
```

本地访问：[http://localhost:3000/](http://localhost:3000/)。未经明确要求，不发布到外部环境。

## 验证

```bash
node scripts/verify-markdown-links.mjs        # 全仓 Markdown 相对链接可解析，0 broken 才继续
node scripts/rewrite-moved-links.mjs --check   # 不再有指向旧工程根目录的链接或 cd 命令
npm --prefix requirements-site test
```

`verify-markdown-links.mjs` 逐条列出失效链接（`文件:行:链接`），指向 git 忽略文件的链接只标为 `local-only`。`rewrite-moved-links.mjs` 读取映射表 [`scripts/moved-links-map.json`](./scripts/moved-links-map.json)，`--write` 只改写链接目标，不改正文与命令块。

组件级构建、测试和运行命令由对应仓库 README 维护。本地 MySQL、Redis、RabbitMQ、Nacos 等依赖的准备方式见 [`local-environment/README.md`](./local-environment/README.md)；自动测试只复用已由操作员启动和准备的仓库环境，不创建或迁移业务 Schema。

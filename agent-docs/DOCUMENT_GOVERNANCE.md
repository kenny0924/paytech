# Code-MoR 文档治理

> **支付渠道术语：** 全系统支付渠道的现行自有命名以 [2026-09-13 Channel 决定](decisions/2026-09-13-payment-channel-terminology.md)为准。Payment、Finance、Risk 及 Gateway/Platform/Operations 的支付回调合同同步使用 Channel；框架、非支付供应商、外部原协议与历史记录按决定中的边界保留。

> **OE / Client / Pricing 本期路由：** CCT、Commerce、Payment、Finance 的 entityNo、OE Scope、Affiliation、Agreement Party、支付准备与定价事实以 [2026-09-11 四系统决定](decisions/2026-09-11-cct-commerce-oe-pricing-model.md) 为准；Risk 延后接入，旧 Wire/Hash/历史 Reader 继续按迁移边界保留。

最后更新：2026-09-11

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
| 单项需求文件夹、命名及材料索引 | [`requirements/README.md`](../requirements/README.md)，按用户指定的日期/系统-改动内容结构组织 | `requirement.md`、`technique.md` 和 `db/` 归集单次需求材料；已有正式合同、全局需求目录、服务模型/SQL 及当前状态台账仍按各自主题确定权威，不因建目录而复制或迁移 |
| 当前实现、验证、部署和待迁移项 | [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) | README 提供证据，不复制全局状态矩阵 |
| Agent/Superpowers 工作流、阶段确认及 SQL、提交、推送、部署等动作授权 | 根 [`AGENTS.md`](../AGENTS.md) 的 `Change discipline` 是唯一现行权威；[逐阶段确认决策](./decisions/2026-09-11-staged-requirement-delivery.md)与[Superpowers 决策](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)分别记录确认理由和取代范围 | [`REQUIREMENT_DELIVERY_WORKFLOW.md`](./REQUIREMENT_DELIVERY_WORKFLOW.md)提供公共阶段、交付物与评审模板，不独立授权动作；`DEVELOPMENT_CONVENTIONS.md` 和 Cursor rule 只做开发侧薄投影 |
| 项目背景、六系统职责和术语 | [`PROJECT_CONTEXT.md`](./PROJECT_CONTEXT.md) | 专项文档只补充本领域事实 |
| 编码、命名、异常、事务和通用依赖习惯 | [`DEVELOPMENT_CONVENTIONS.md`](./DEVELOPMENT_CONVENTIONS.md) | Cursor rule 仅薄投影 |
| Java 模块、分层、包、HTTP API 和跨模块边界 | [`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) | 系统模块设计只记录本系统映射 |
| 第一方 HTTP 4xx/5xx Problem Details、Request ID、错误时间、字段错误及 Gateway/BFF/内部 Client/Vault 传播 | [`2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md) 是完整合同；[决策记录](./decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)冻结取代范围 | `DEVELOPMENT_CONVENTIONS.md` 与 `JAVA_PROJECT_STRUCTURE.md` 只投影 Java 开发规则；业务系统只拥有自己的 Code/Status；实现和迁移状态只写 `CURRENT_IMPLEMENTATION.md` |
| Foundation RFC 8785 JSON Object Profile、规范 bytes、严格输入域与 Owner Hash/签名边界 | [Object Profile 决策](./decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md)冻结窄范围；[完整设计](../docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md)拥有 API、验证、上限、向量和升级合同 | `TECHNOLOGY_STACK.md` 只投影 Foundation 模块、依赖、版本与允许/禁止能力；业务 Owner 继续拥有 Projection、Hash Schema、存储、重放与签名语义；实现、验证、制品发布和部署状态只写 `CURRENT_IMPLEMENTATION.md` |
| Foundation 服务本地命令幂等 Claim/Lease/CAS 与业务 Owner 边界 | [命令幂等能力决策](./decisions/2026-09-09-foundation-command-idempotency.md) | `TECHNOLOGY_STACK.md` 投影 JAR、依赖和启用边界；各业务 Owner 仍拥有请求 Hash/Snapshot、恢复阶段、错误、事务编排和本地数据库，当前实现、验证与迁移范围只写 `CURRENT_IMPLEMENTATION.md` |
| Commerce 非运行 Client API Owner ingress 契约模块及 Java/OpenAPI 投影规则 | [契约模块决策](./decisions/2026-09-05-commerce-client-api-contract-module.md)、[设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md)与[实施计划](../docs/superpowers/plans/2026-09-05-commerce-client-api-contract-module-implementation.md) | 仅拥有非运行 Java 模块、投影规则与实施顺序；原 Commerce Client API 决策/设计继续拥有其余业务和公共 wire 语义 |
| Commerce Create Checkout 命令恢复检查点 | [恢复简化决策](./decisions/2026-09-09-commerce-checkout-recovery-simplification.md)与 [`COMMERCE_DATABASE.md`](./database/COMMERCE_DATABASE.md) | 只取代 Create Checkout 的旧五阶段表达；公共 Wire、Cancel/Create Refund 阶段与实现状态仍由各自 Owner 管理 |
| 技术产品、版本和允许/禁止能力 | [`TECHNOLOGY_STACK.md`](./TECHNOLOGY_STACK.md) | README 记录实际启用值 |
| 入口应用、Gateway、BFF、前端和部署边界 | [`APPLICATION_ARCHITECTURE.md`](./APPLICATION_ARCHITECTURE.md) | 组件 README 记录运行方式 |
| Gateway 业务 Target/Discovery/Route 的配置权威与动态转发 | [2026-09-11 范围决定](./decisions/2026-09-11-gateway-database-routing.md)取代静态业务名单；[技术修订方案](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-design.md)按阶段评审 | 复用 Platform 数据库和 PLT-I20、固定单次请求 Route/Target、BFF 管理监听隔离；其余 Channel/认证/协议/控制面由原专项设计持有，迁移状态只读当前台账 |
| Client API 最小调用方输入、可信 Channel/身份、Request ID 接纳与最小下游 Header | [Client API Gateway 完整设计](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)拥有入口合同；[合同决策](./decisions/2026-09-05-client-api-gateway-contract.md)记录确认理由与范围 | 仅适用于 `CLIENT_API`，不统一其他 Channel 的认证算法或外部 Request ID 行为；具体业务字段仍由业务 Owner 定义，第一方错误响应继续遵守全系统 Problem Profile |
| 本机开发拓扑、端口和依赖准备 | [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) | `local-environment/README.md` 是操作手册 |
| Java 测试策略、auto/manual 和覆盖率 | [`UNIT_TEST_GUIDE.md`](./UNIT_TEST_GUIDE.md) | 各仓库 README 记录已运行证据 |
| 数据库通用物理规则 | [`DATABASE_DESIGN_GUIDE.md`](./DATABASE_DESIGN_GUIDE.md) | 不保存六库实施状态 |
| 六系统业务数据事实和状态机 | [`database/README.md`](./database/README.md) 路由的系统文档、当前专项决定和 PDManer | 通用手册不重定义业务语义 |
| Risk Operations Rule Configuration Backend 首期（`CHECKOUT_PREPARE` Rule Set、TEST Candidate 与静态检查） | [`2026-09-05-risk-operations-rule-configuration-backend.md`](./decisions/2026-09-05-risk-operations-rule-configuration-backend.md) 冻结已确认的窄范围与取代边界；[完整设计](../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md) 拥有完整合同；[实施计划](../docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md) 拥有执行顺序和验收 | [`database/RISK_COMPLIANCE_DATABASE.md`](./database/RISK_COMPLIANCE_DATABASE.md) 继续拥有未被该窄决定取代的 Risk 业务事实；实现、验证和部署状态只写入 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) |
| Risk Operations Rule Configuration Console 集成（结构化编辑器、BFF、权限、公共 Route 与 TEST Candidate 页面） | [Console 集成决策](./decisions/2026-09-06-risk-operations-rule-configuration-console-integration.md) 与[完整设计](../docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md) | Risk 后端语义仍由上一行 Owner 持有；Gateway/Platform、Operations 基础和 Problem 通用合同仍由各自 Owner 持有；实现、验证和部署状态只写入 [`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) |
| Risk Operations V1 配置增量（动态主体绑定、冷却/主体标签配置、手工拒绝原因与文案） | [2026-09-11 配置决策](./decisions/2026-09-11-risk-rule-v1-configuration.md) 拥有已确认业务范围与语义；[需求与接口合同](./plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md) 细化本期 Wire、持久化和兼容规则 | 窄范围扩展前两行的配置合同；保留历史 Wire/Hash 与执行就绪门禁。实现、验证、数据库和部署状态只读当前台账 |
| Risk Operations 规则集列表与整体管理 | [单项需求](../requirements/20260911/operations-console-风控规则集列表优化/requirement.md) 与[已批准方案第 10–18 节](../requirements/20260911/operations-console-风控规则集列表优化/technique.md) | 取代前述合同中按版本列表、无整体删除与 Candidate 页面入口部分；保留不可变版本和 Candidate 后端历史。当前状态只见台账第 16 节 |
| Risk Operations 历史只读查看、发布引用与原因/主体标签字典 | [范围决定](./decisions/2026-09-11-risk-rule-history-and-dictionaries.md)与[单项需求](../requirements/20260911/operations-console-风控规则集历史与字典管理/requirement.md) | 用户已确认功能范围，接口/模型/兼容细节仍按阶段评审；仅取代历史展示延期、详情 Drawer 和无字典的后续目标，保留一集一行、整集删除、历史内容及配置执行边界；当前状态只见台账 |
| 本地可靠执行 | [`COMPENSATION_EVENT_GUIDE.md`](./COMPENSATION_EVENT_GUIDE.md) | 六库文档只记录本系统事件 |
| Vault V1 | [Vault V1 Spec](../docs/superpowers/specs/2026-09-02-vault-database-design.md) 拥有安全、信任与行为语义；[`OpenAPI`](../vault-service/api/openapi/v1.yaml) 拥有 HTTP Wire Contract；[`VAULT_PHYSICAL_DECISIONS.md`](../vault-service/docs/database/VAULT_PHYSICAL_DECISIONS.md)、PDManer 与评审后 Schema SQL 拥有持久化结构 | [`VAULT_SERVICE_ARCHITECTURE.md`](./VAULT_SERVICE_ARCHITECTURE.md) 只保留未被 V1 取代的安全背景；三类权威漂移时不得互相猜测，按各自子主题修复并记录冲突 |
| Risk Operations 决策流配置与规则动作可选配置 | [RCO-17 范围决定](./decisions/2026-09-12-risk-decision-flow-configuration.md)与[已确认需求](../requirements/20260912/operations-console-决策流配置/requirement.md) | 条件连线、提前结束、配置版本/历史及规则动作可选配置；真实执行下一期。系统影响、接口、模型与 SQL 继续分阶段评审，不把需求确认当成实现或部署 |
| 业务需求和全局时序 | `requirements-site/content/requirements.ts` 与 `requirements-site/content/global-use-case-sequences.mjs` | 派生站点不得反向成为事实源 |

Foundation 资源锁按既有主题分治，不新增重叠 Owner：[`TECHNOLOGY_STACK.md`](./TECHNOLOGY_STACK.md) 拥有公共能力、依赖、配置与失败边界，[`DEVELOPMENT_CONVENTIONS.md` 第 5.1 节](./DEVELOPMENT_CONVENTIONS.md#51-锁与并发控制)拥有是否加锁、实际资源、Redis-first 与数据库最终保护的选择规则，[`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) 拥有公共包、代理入口和 Processor/事务位置，[`UNIT_TEST_GUIDE.md`](./UNIT_TEST_GUIDE.md) 拥有验证门禁，[`CURRENT_IMPLEMENTATION.md`](./CURRENT_IMPLEMENTATION.md) 独占实现、验证、迁移和部署状态。[资源锁决策](./decisions/2026-09-05-redis-first-resource-lock.md)与[完整设计](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)冻结跨 Owner 的准确窄合同；各 Owner 只维护自己子主题的稳定投影。

某项专项决策可以窄范围取代主题 Owner 的一部分，但必须声明 `Superseded sections` 或准确作用域；未命中的其余部分继续有效。

Payment 的交易级工具 Token、统一 pay、旧 Instrument 移除与 Commerce 幂等衔接，按[Execution 修订评审稿](../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)管理后继目标的详细合同。其明确取代范围外仍沿用原 Owner；方向确认、详细设计评审、代码、物理模型及数据库执行分别判断，不用旧 SQL 或测试证据证明新行为。

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

### 3.6 公共合同与系统文档分工

- 跨系统共同遵守的接口、Header、错误和信任边界，由该主题的公共文档 Owner 统一维护。各系统文档链接该权威，只补充本系统的实现映射、配置、运行与验证证据，不复制或自行改写公共合同。
- 系统任务发现公共合同缺口或冲突时，应向主题 Owner 提供差异、影响和建议；公共合同变更由负责该主题的任务收敛后同步，不能在各系统中分别形成不同版本。用户未要求系统任务维护公共文档时，该任务只维护本系统文档并交接公共变更需求。
- 公共文档不等于全项目无差别适用：必须标明全项目、跨系统协议面或单系统范围。例如 Client API 的最小输入与 Request ID 接纳合同不能自动推广到 Operations。
- 某次任务由谁负责、具体系统的交付顺序或时限属于计划/交接记录，不固化为长期公共规范。跨任务通用的阶段与确认模板见[需求交付流程](./REQUIREMENT_DELIVERY_WORKFLOW.md)，具体任务的适用阶段和用户确认记录留在任务计划或会话。当前实现、验证和部署状态仍只由状态台账及组件证据承载。

## 4. 更新规则

1. 先确定主题 Owner，再修改内容；同义规则只写一次。
2. 规则改变时同步薄投影和必要的架构门禁；状态改变时只更新状态台账和组件 README。
3. 数据库模型、DDL、代码和部署分别验证。文档更新不得宣称其他层已经迁移。
4. 模型统计应从生成器或单一清单校验；入口只引用状态台账，不在多个文件手工维护。
5. 历史扫描默认排除 `.git/`、`.worktrees/`、构建输出和依赖目录；历史关键词不能被误报为当前规范。
6. 当前规范若必须提到已废弃机制，应使用“禁止/历史/待迁移”明确限定，不能以现在时提供可复制示例。
7. 修改共享文档前检查工作区差异并使用逐段补丁；不得覆盖其他任务的未提交内容。

## 5. 当前已冻结的冲突结论

2026-09-11 用户明确要求后续需求逐阶段交付并停下等待确认，见[逐阶段确认决策](./decisions/2026-09-11-staged-requirement-delivery.md)。[公共流程](./REQUIREMENT_DELIVERY_WORKFLOW.md)中的八阶段划分暂为评审稿；根 `AGENTS.md` 继续拥有动作授权，Superpowers 显式授权规则保持。

2026-09-05 的 DataObject、Vault V1、Foundation 测试基础设施、Finance 手工缴税和历史治理决定见 [`decisions/2026-09-05-document-governance-and-conflict-resolution.md`](./decisions/2026-09-05-document-governance-and-conflict-resolution.md)。该记录是这些具体冲突的裁决来源。

2026-09-05 建立并于 2026-09-08 修订的 Superpowers 显式授权规则，以及业务 Service/Domain Service 强制 `interface + *Impl`，见 [`decisions/2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)。Superpowers 默认工作流已被“仅当前用户明确指定时使用”取代；Service 形态、DataObject Owner-JAR 边界及其他既有决定继续有效。

同日确认的业务模块默认无直接依赖、稳定能力单向例外、Owner 合同边界与 `web` Processor 编排规则见 [`decisions/2026-09-05-business-module-collaboration-policy.md`](./decisions/2026-09-05-business-module-collaboration-policy.md)。[`JAVA_PROJECT_STRUCTURE.md`](./JAVA_PROJECT_STRUCTURE.md) 仍是这类 Java 模块协作语义的唯一现行权威，系统模块设计只做具体映射。

同日确认的 Redis-first Foundation 资源锁合同见 [`decisions/2026-09-05-redis-first-resource-lock.md`](./decisions/2026-09-05-redis-first-resource-lock.md)和[完整设计](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)。它只在标准同步排他资源协调范围内取代“不得新增通用锁框架”的旧表述，批准的是 Foundation `@ResourceLock` 唯一公共能力，不授权业务服务复制本地注解、Aspect、执行器或客户端；是否需要协调、资源 Key 和数据库最终保护仍由业务不变量决定。Platform 2026-09-04 锁重构文档保留为具体资源选择与历史验证证据，不拥有该公共能力的当前合同。

2026-09-11 确认的 [PDManer 单模型与短文件名决策](./decisions/2026-09-11-pdmaner-single-model-short-filenames.md)收敛各持库服务的模型路径：每个服务只维护一个小写短名 `*.pdma.json`，内部展示名和 PDManer 本地备份不得形成第二个权威模型；完整稳定规则仍由 `DATABASE_DESIGN_GUIDE.md` 拥有。

# Code-MoR 当前实现状态

最后核对：2026-09-06

状态：Current status ledger。本页只汇总实现、验证、部署和已决定待迁移事项；架构与业务规则仍由 [`DOCUMENT_GOVERNANCE.md`](./DOCUMENT_GOVERNANCE.md) 路由的主题 Owner 定义。

## 1. 状态解释

- `Implemented`：当前主工作区存在代码或资产，不代表已经通过全部门禁。
- `Verified`：列出的检查曾针对对应实现和环境通过，不外推到其他工作区或环境。
- `Deployed`：只表示明确写出的环境，不代表生产部署。
- `Pending`：结论已确认，但对应代码、模型、物理库、验证或部署仍未完成。

## 2. 应用与基础设施

| 组件 | Implemented | Verified | Deployed | Pending / 未证明 |
|---|---|---|---|---|
| Requirements Site | 需求目录和全局序列源文件已存在，仍是需求事实入口；全系统 Problem Profile 决策已投影到现行需求目录 | 2026-09-06 内容门禁 134/134 | 仅本地站点，不对外发布 | 需求存在不代表六系统全部实现或已部署 |
| Build / Foundation | Maven 构建治理、框架无关 `paytech-http-problem`、严格/迁移期 HTTP Client Reader 与唯一 MVC Problem Writer 已在当前工作区实现 | 本次 Problem 相关 Reactor `clean verify` 327/327（Common 100、Problem 9、Client 104、MVC 114） | 最新 Snapshot 仅安装到本机 Maven 仓库供下游源码验证，未发布 Nexus、未部署 | Legacy Reader 须在部署与观测完成后删除；Redis 测试仍使用 Testcontainers；Compensation Event 测试仍自动重建 Schema |
| API Gateway | 既有动态 Snapshot 消费已经实现，Target/Route/Console 管理命令仍是独立事项；当前工作区已实现 WebFlux Problem Writer、严格 Owner Problem 校验、无 Body `HEAD`、`UPSTREAM_PROTOCOL_ERROR` 和高于公开 API 限额的 Connector envelope | Task 6/9 聚焦门禁 128/128 与边界脚本通过；完整验证发现 878 项，剩余 10 个失败与 3 个错误和 clean HEAD 768 项基线中的 13 个失败用例完全相同，本次新增的 413 title、截断响应与 15 秒 idle timeout 回归均已聚焦修复并通过 | 既有 Gateway 仍部署于 `mor-test`（1 个 Target / 27 条 Route）；本次 Problem/Connector 源码未部署 | 既有 13 个 Gateway 基线失败及其 JaCoCo aggregate、Legacy Operations Owner Reader 删除、生产 rollout、Console Resource、环境切换与本地 Nacos Namespace 默认值仍待完成 |
| Platform Support | 既有 Platform 切片之外，当前工作区补齐 Operations 401/403、Gateway 配置 404 的 Owner 状态映射，并继续由 Foundation 唯一 Writer 生成 Problem | 本次 Problem 配置/架构门禁 15/15；纯 RFC 暂存快照 JDK 25 `test-compile` 成功（33 API、133 Web、35 Test sources），配置聚焦测试 4/4。注入本地凭据后的当前工作区完整验证发现 284 项，但本机 `mor_platform` 含部署/人工数据，不满足测试要求的仅初始化目录前置，最终为 1 个失败与 127 个错误 | 既有 Target/Route 控制面部署保持不变；本次 Problem 映射未部署 | 在干净、仅初始化目录的仓库本地 Platform 数据库重跑完整门禁；CUSTOMER 认证/JWKS、生产 Vault/KMS、Console Resource、MQ 和生产部署 |
| Operations Console BFF | 独立 `web + test` Maven 仓库；当前工作区除既有页面适配外，已使用 Foundation Writer 输出统一 Problem，并在边界重建自身 503/504，保留显式上游迁移 Reader | 2026-09-06 JDK 25 `clean verify` 315/315；严格 Profile 与真实 `HEAD` 聚焦门禁 272/272 | 既有 Gateway 控制面适配部署保持不变；本次 Problem 迁移未部署 | 无数据库和签名密钥；不拥有核心授权或跨系统事务；Legacy Reader 待部署证据后删除 |
| Operations Console Web | React/TypeScript/Vite/Ant Design 已实现；当前工作区的 API 消费者严格读取 Problem、显示安全 detail/requestId，并为已知目标保留显式旧 Reader | 2026-09-06 测试 216/216，TypeScript 与 Vite 生产构建通过 | 既有控制面页面部署保持不变；本次 Problem Reader 未部署 | Client/Partner 未来会话方案不由此决定；Legacy Reader 待部署证据后删除 |
| Vault V1 | Go Server、OpenAPI 与 Java Client 的当前工作区已切换统一 Problem/`HEAD`/Request ID 合同；三表 Protect/Reveal 业务边界不变 | 纯 RFC staged tree 的 Java Client `clean verify` 34/34，OpenAPI/YAML/source 静态门禁通过；当前主机没有 Go/gofmt，Go 编译与测试未执行 | 既有 `mor-test` 合成 smoke 部署保持不变；本次 Problem 源码未部署 | 补齐 Go 门禁；Platform 仍使用批准的开发 Mock；生产 KMS、强服务身份、真实消费者、合规验证和生产部署未完成；Legacy Java Reader 待删除 |
| CCT | 37 表模型与首个 Java 切片已存在；Client Contract 应用继承统一 MVC Problem Writer | 现有模型门禁已记录；本次真实随机端口 Profile 门禁及仓库 `clean verify` 4/4 | 未声明 | 完整服务、物理数据库、生产部署和 Legacy Reader rollout 未证明 |
| Commerce | 14 表模型、Client API/Web/Test 与非运行 Client API Owner contract module 已存在；公共 OpenAPI 及代表性运行时错误已投影统一 Problem | 2026-09-06 JDK 25 `clean verify` 68/68；OpenAPI/Java drift、purity 与随机端口 Problem 门禁通过 | 未声明 | 无业务 Controller/实现、数据库/SQL 迁移、Gateway 激活、Checkout 页面、端到端验证、部署或生产可用性；15 表目标仍未实现 |
| Payment | 29 表模型已生成 | 自动模型门禁通过 | 未声明 | 完整 Java 服务、物理数据库和生产部署未证明 |
| Finance | 84 表模型与首个余额查询 Java 切片已存在；当前测试消费者统一校验真实 HTTP Problem | 2026-09-06 JDK 25 `clean verify` 25/25，含本地 MySQL 余额路径与 RFC 1123 Date 门禁 | 未声明 | 完整服务与部署未证明；手工缴税异常分支待专项模型闭合 |
| Risk & Compliance | 既有 71 表/九路由切片保持不变；当前工作区的真实 HTTP 测试消费者已统一校验 Problem，Risk 不增加本地 Writer | 模型/Schema 188/188、需求站点 134/134；仅向测试进程注入仓库本地数据库凭据后的 JDK 25 `clean verify` 148/148，包含本次 DB-backed Problem 断言 | 未部署；本机 `mor_risk` 只是仓库自有本地验证库 | PDManer 人工 Review、部署、UI/BFF/Gateway/Auth/PLT-I02 及全局 COMP-01/DB-01/Vault V1 Gate 仍未完成 |
| `mor-local` | 仓库提供 MySQL、Redis、RabbitMQ、Nacos 等本地依赖的手工环境 | 当前本机运行状态应按 [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 的命令即时核对 | 仅本机开发环境 | 自动测试只能复用该环境，不得自行拉起替代容器或连接真实业务环境 |
| `mor-test` | 独立 Docker 测试拓扑含 10 个容器、27 条 Route、15 个 Console Resource、14 个 ACTIVE Permission，浏览器入口为 `http://localhost:28080` | 已记录真实登录/IAM/审计/Refresh/Logout、Gateway 控制面启停/幂等/版本冲突/Snapshot 恢复与 Vault 合成 smoke | 本机独立测试环境；Gateway 控制面 V1 已部署且 10 个容器健康 | 不是 Maven `auto`、覆盖率或生产证据；生产部署仍未声明 |

组件级命令、精确测试数字和运行配置由各仓库 README/实施记录维护，本页不复制会频繁变化的日志。

## 3. 当前生成模型快照

| 系统 | 表 | 字段 | 唯一索引 | 访问索引 | 本库关系 | 1:1 | Diagram Groups |
|---|---:|---:|---:|---:|---:|---:|---:|
| Client & Contract | 37 | 423 | 57 | 44 | 64 | 7 | 6 |
| Commerce | 14 | 223 | 20 | 11 | 14 | — | 4 |
| Payment Orchestration | 29 | 373 | 41 | 44 | 63 | — | 7 |
| Finance | 84 | 1,100 | 145 | 155 | 160 | 22 | 6 |
| Risk & Compliance | 71 | 862 | 105 | 75 | 111 | 9 | 8 |
| Platform Support | 63 | 822 | 123 | 99 | 107 | 5 | 7 |

这些数字表示当前生成资产内部一致；六套模型仍保留 PDManer 4.9.3 应用内人工检查门禁，也不能证明物理数据库已经创建。Platform 的 22 表是当前手工 SQL 投影，不改变 63 表逻辑模型统计。

## 4. 已确认但尚未全部实施

| ID | 已确认目标 | 当前缺口 | 完成判据 |
|---|---|---|---|
| GOV-01 | 主题 Owner + 单一状态台账 + 带元数据的历史 | 现行入口和用户级全局 fallback 已收敛；大量历史 Spec/Plan 的元数据只在后续触及时补齐 | 新入口只路由；当前规范关键词/链接门禁持续通过；历史文件不再被误作现行状态 |
| JAVA-01 | DataObject 在 Owner JAR 内可用；跨模块只用 Owner 的业务接口和 Domain/Application Command/Result | 中央规范与 Cursor 薄投影已传播；各业务模块的架构测试要随实现补齐 | 文档与架构测试不存在跨模块 DataObject/Repository/Mapper 依赖 |
| JAVA-02 | 所有业务 Service 和 Domain Service 使用 `interface + *Impl`；Processor/Controller 保持具体类，Repository 保持 `interface + *RepositoryImpl` | 规范、入口、Cursor 薄投影和静态门禁已传播；Platform Support 已完成本次迁移与架构扫描，其他 Java 仓库尚未完成全量形态审计与迁移 | 架构扫描确认不存在无接口的业务 Service/Domain Service，相关构建与测试通过；不得用空 Service 满足形态检查 |
| JAVA-03 | 业务模块默认无直接依赖；只允许稳定 Owner 能力通过发布接口和 Domain/Application 模型形成单向无环依赖，其他协作由 `web` Processor 编排 | 公共规范、Finance 映射和 Cursor 薄投影已传播；既有仓库尚未完成获准依赖清单、包级越权与依赖环的全量审计 | 每个复杂核心服务的 Maven 图无环，架构测试阻止未获准依赖及跨模块 `*Impl`、DataObject、Repository、Mapper 和 SQL 越权；跨 Owner 写事务由单个本地 `TransactionTemplate` 覆盖 |
| HTTP-PROBLEM-01 | 除 `HEAD` 无 Body 语义及 pre-application 传输/边缘失败外，所有第一方 JSON HTTP 4xx/5xx 统一为 RFC 9457-based Code-MoR Problem Profile：`type=about:blank`，必填 `title/detail/code/requestId/timestamp`，可选字段错误只有 `field/message`，Body 不含 `status` | Tasks 1–12 已完成 Foundation、HTTP Client、Gateway、Platform/BFF/Web、Vault、Commerce OpenAPI 和现有核心服务测试消费者的源码迁移；Nginx/Connector 顺序已实现静态门禁，变更已合入各受影响仓库的本地 `main`。为未部署 Producer 保留的显式 Legacy Readers 尚未删除；Go 工具链、Gateway 既有 13 个基线失败和 Platform 非干净本地库仍是未闭合门禁；未发布或部署 | 补齐 Go、Gateway 基线与干净 Platform 数据库门禁，发布并按消费者优先顺序部署，观察所有第一方 Producer/Consumer 已使用新 Profile，再删除 Legacy Readers 并重跑全门禁；只有实际环境验收后才更新部署状态 |
| VAULT-01 | Go Server + Java Client 是 V1 正式实现；V1 只支持三表 Protect/Reveal | 六系统文档仍依赖 Binding/Copy/Blind Index/Masked/Metadata/Retire 等旧能力 | 每个真实消费者完成 V1 适配或获批窄 V2 设计，API/模型/迁移/安全测试一致 |
| TEST-01 | Foundation 不使用 Testcontainers 或自动 Schema 初始化 | Redis 测试和 Compensation Event 测试仍违规 | 静态门禁先失败后通过；受影响 Maven 测试在手工准备的 `mor-local` 上通过 |
| COMP-01 | 本地可靠表使用 `event_key + event_type + sub_event_type + 可选最小 payload_json`，任何 Vault Token 禁止进入 Payload | 六库文档/PDManer 仍有旧 source/version/schema/hash/64 KiB 契约；Foundation `CompensationPayloadPolicy` 尚未显式拒绝 `vaultToken` / `*_vault_token` 标准字段名，也缺少相应反例测试 | 六套 Manifest、Policy、模型、场景、SQL 和测试一次性迁移并复核统计；Foundation 登记入口拒绝标准 Vault Token 字段并通过反例测试 |
| DB-01 | 新建表不使用物理 CHECK/FK，Sidecar 仅表达应用不变量/逻辑关系 | 多份旧物理决策与 Vault DDL 仍有现在时或实际约束 | 应用保护与反例测试齐备后，审核并手工执行逐库约束迁移；物理核验通过 |
| FIN-01 | Tax Payment 事前承诺门禁与已发生银行扣款的事后事实登记分离 | 专项核对已确认：现有 `Bank Item + Reconciliation Exception` 可保存并追踪已发生扣款，但当前模型不能无损地把“没有前置 Tax Payment 的真实扣款”闭合到 Return/Payable | 正常流、无前置记录真实扣款、重复、金额/归属不匹配和解决流程均有模型与测试 |
| RUN-01 | Gateway 本地 Nacos 默认 Namespace 与 `mor-local` 一致 | Gateway 当前默认/示例仍为 `public` | 失败断言、配置修正和相关 Gateway 门禁通过 |
| RISK-OPERATIONS-RULE-CONFIG-01 | `CHECKOUT_PREPARE` 配置后端的完整工作区实现已存在：71 表 DDL、唯一 DML、九路由 typed API、不可变 DRAFT/Freeze、TEST Candidate、八项静态检查与持久幂等/两写事务恢复 | 授权后的仓库自有本地 `mor_risk` Schema/DML 应用、物理结构核对和真实 MySQL HTTP/持久化/并发，以及 Validate A/读快照/事务外纯校验/B Recovery 已通过；人工 PDManer Review 与部署仍未完成。模型/Schema 188/188 与需求站点 134/134 是独立门禁；COMP-01、DB-01、Vault V1 全局 Gate 继续开放 | 本地切片完成不等于部署或生产可用。共享或生产使用前仍须完成 PDManer 4.9.3 人工 Review、环境配置、Operations UI/BFF/Gateway/Auth/PLT-I02、Shadow/审批/激活/运行期评估及部署验收；不得用本项关闭 COMP-01、DB-01 或 Vault V1 |
| COMMERCE-CLIENT-API-01 | Commerce Hosted Checkout V1 的非运行 Java Owner ingress contract module 已实现：公共 OpenAPI、三接口、十操作、records、九个 known-code catalogs 与 projection/purity tests；它不实现业务 HTTP | [业务决策](./decisions/2026-09-05-commerce-client-api-v1.md)与[设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)仍拥有业务/公共 wire 语义；[契约模块决策](./decisions/2026-09-05-commerce-client-api-contract-module.md)、[设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-contract-module-design.md)和[实施计划](../docs/superpowers/plans/2026-09-05-commerce-client-api-contract-module-implementation.md)只拥有非运行模块及投影规则。Problem OpenAPI 投影与 Foundation Writer 继承已在本地验证；仍是 14 表 / 223 字段，无业务 Controller、SQL、Platform Webhook、CCT/Risk/Payment/Finance typed 合同、Client API Gateway/Nginx 激活或 `checkout-web` | 所有外部 Owner 前置完成；15 表 PDManer/SQL/映射与本地 MySQL auto 一致；十条 OpenAPI/HTTP、幂等恢复、Token 固定向量、Funds 事件、Webhook、日志和跨 Client 反例通过；Gateway/Platform/Nginx 与 CHECKOUT 页面完成端到端激活后才可声明可用 |
| GATEWAY-CLIENT-API-01 | Client API 使用专用域名推导的 Channel、PLT-I03 opaque Credential、Credential/Tenant Guard、exact Route Scope、外部可生成的 Request ID 与最小三 Header 下游合同 | [合同已确认](./decisions/2026-09-05-client-api-gateway-contract.md)，[设计](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)和 [Gateway-only 计划](../docs/superpowers/plans/2026-09-05-client-api-gateway-implementation.md)已形成；Gateway 仍是 Operations-only 运行链，Platform 未发布 PLT-I03 Java/HTTP 契约与 Credential SQL，Platform Route Key 仍跨 Channel 冲突，Nginx/Owner Adapter/生产 Target 均未就绪 | Gateway auto 通过并只标记 integration-ready；Platform、Owner 和 Nginx 前置全部完成后，以 `DISABLED` Target/Route 准备并静态核对。首条 Route 切为 `ACTIVE` 后，逐实例确认刷新成功且 CLIENT_API Target/Route Count `0 -> 1`，再用未分发的测试 Credential smoke；失败先撤销测试 Credential，再禁用 Target/Route 并等待每实例 Count 回到 0。只有成功收敛和 smoke 后才声明可用；后续同 Count 变更须先提供 Snapshot Revision/Fingerprint |
| AUDIT-01 | Platform 既有 Operations HTTP 审计由 Controller 注解声明，提交结果确认后独立事务尽力写入；允许丢失，审计故障不改变业务结果 | [正式设计](../docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md)与[实施计划](../docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md)已实现：29 个方法精确覆盖、业务保护迁移、有界异步隔离、同库独立事务及真实故障回归均通过本地门禁；本次把业务审计接口收敛为 `identify/target/record/suppressReplay`，Processor 由唯一事务执行器自动桥接事务终态并新增递归字节码门禁；首次实现与低侵入收敛已分别提交本地 `main`（`5ce9ad0`、`8b42a64`），均尚未推送或部署，网络未知提交、强制终止和物理基础设施故障不在已验证范围 | 当前实现与验证证据见第 7 节；上线前另行完成环境配置、容量评估和部署回归，不把尽力写入描述为零丢失或物理零影响 |

上述条目为迁移台账，不授权跳过专项设计直接改生产模型或数据库。

## 5. 最近决策与计划

- 决策：[`decisions/2026-09-06-system-wide-rfc-9457-problem-details.md`](./decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)
- 实施计划：[`docs/superpowers/plans/2026-09-06-system-wide-rfc-9457-problem-profile-implementation.md`](../docs/superpowers/plans/2026-09-06-system-wide-rfc-9457-problem-profile-implementation.md)

- 决策：[`decisions/2026-09-05-commerce-client-api-v1.md`](./decisions/2026-09-05-commerce-client-api-v1.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md`](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-commerce-client-api-v1-implementation.md`](../docs/superpowers/plans/2026-09-05-commerce-client-api-v1-implementation.md)

- 决策：[`decisions/2026-09-05-client-api-gateway-contract.md`](./decisions/2026-09-05-client-api-gateway-contract.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-client-api-gateway-design.md`](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-client-api-gateway-implementation.md`](../docs/superpowers/plans/2026-09-05-client-api-gateway-implementation.md)

- 决策：[`decisions/2026-09-05-platform-controller-best-effort-audit.md`](./decisions/2026-09-05-platform-controller-best-effort-audit.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md`](../docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md`](../docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md)

- 决策：[`decisions/2026-09-05-risk-operations-rule-configuration-backend.md`](./decisions/2026-09-05-risk-operations-rule-configuration-backend.md)
- 已确认设计：[`docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`](../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md`](../docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md)

- 决策：[`decisions/2026-09-05-document-governance-and-conflict-resolution.md`](./decisions/2026-09-05-document-governance-and-conflict-resolution.md)
- 决策：[`decisions/2026-09-05-superpowers-and-service-interface-policy.md`](./decisions/2026-09-05-superpowers-and-service-interface-policy.md)
- 决策：[`decisions/2026-09-05-business-module-collaboration-policy.md`](./decisions/2026-09-05-business-module-collaboration-policy.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-document-governance-and-norm-consolidation.md`](../docs/superpowers/plans/2026-09-05-document-governance-and-norm-consolidation.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-superpowers-service-interface-policy.md`](../docs/superpowers/plans/2026-09-05-superpowers-service-interface-policy.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-business-module-collaboration-policy.md`](../docs/superpowers/plans/2026-09-05-business-module-collaboration-policy.md)
- 执行计划：[`docs/superpowers/plans/2026-09-05-platform-support-review-refactoring.md`](../docs/superpowers/plans/2026-09-05-platform-support-review-refactoring.md)

## 6. Platform Support 审查修复验收（2026-09-05）

- 实现遵循本次[已确认设计](../docs/superpowers/specs/2026-09-05-platform-support-review-refactoring-design.md)。原 726 行 Directory 已分为人员生命周期、身份创建、角色、授权导航、审计查询和共享管理规则；HTTP 契约与 Processor 事务/资源锁不变。Owner JAR 内纯镜像删除，HTTP/Domain 转换显式留在边界；Snapshot/JWKS 同样遵守该边界。
- 路由 RED 在 2/64/65/4096 条目录、两种插入顺序均复现；人员 RED 复现非有效管理员被拒绝，以及 10 人/12 人列表分别执行 35/41 次 SQL。最终回归使用真实 200 人页面，验证查询数固定、排序/角色/COUNT 正确及缺失成员/凭据时失败关闭。登录审计另用受控变异恢复旧原因，8 个原因断言均按预期失败，恢复修复后纳入完整 GREEN。
- Platform 执行 `./mvnw -q clean verify`：145 项、0 失败/错误/跳过，JaCoCo 门禁通过。Gateway 执行 `GatewaySnapshotCompilerAutoTest,GatewaySnapshotCodeParserAutoTest,OperationsRouteDefinitionContractAutoTest`：54 项全通过；未宣称整个 Gateway 套件通过。该次验收时需求站点 `npm --prefix requirements-site test` 为 127/127；当前工作区的新结果见第 7 节。主工作区、Platform 和 Gateway 的 `git diff --check` 通过。
- 本地身份、密码、成员、授权、Session、Refresh、幂等、审计、签名 Key 的临时记录均为 0；临时 Catalog Route 为 0，测试同时比较完整 Gateway 目录的运行前后快照。只读 `SCAN` 确认 `platform-lock-test:*` 与 `gateway-management-test:*` 均无残留。专用 MySQL 测试锁已释放，另一 JVM 在持锁时不能取得、释放后可以取得。
- 完成交叉代码评审，未留下已确认的 Critical/Important 问题。验证只访问仓库自有 Loopback MySQL/Redis 和本地 HTTP；用户本次明确授权读取本地凭据，仅注入测试进程且未回显。未启动容器、应用 Schema/DML、提交、推送或部署，未访问 `mor-test`。

## 7. Platform Controller 尽力审计验收（2026-09-05）

- 四个 Operations Controller 的 29 个已批准方法使用 `@Audit` 精确声明，其中 15 个记录成功与拒绝、14 个只记录拒绝；Context、JWKS 和 Gateway Snapshot 入口明确排除。Login/Logout 与 Authorize 保留双资源分类，10 个业务幂等成功重放分支明确丢弃审计，不补发已丢事件。
- 业务侧 `OperationsAuditService` 只保留可信身份、动态目标、类型化事实和重放抑制四个语义入口；19 个事实调用点不再拼装通用属性或暴露 DataObject。三个 Processor 不依赖审计 Service/Context，固定事务画像由 `OperationsTransactionExecutor` 统一执行并在真实回调内观察终态；递归架构门禁确认该编排包只有这一个观察桥接点。
- 审计在调用终态和实际业务事务终态都确定后，才冻结不可变事件并尝试进入单 Worker 有界队列；独立 DataSource、SqlSessionFactory 和 `REQUIRES_NEW` 事务一次原子写入 Event/Attribute/Chain/Security。队列满、链锁、唯一冲突、辅助连接耗尽或认证失败时只记录固定低基数诊断并丢弃该事件，不阻塞、重试或替换原业务结果。密码失败计数和 Refresh 重用撤销均已在真实审计故障下验证提交。
- 从工作区通过凭据脱敏 Runner 执行 Platform `clean verify`：25 个 Suite、213 项、0 失败/错误/跳过；JaCoCo 指令 `20626/21073`（97.879%）、分支 `1389/1533`（90.607%）、行 `3132/3249`（96.399%），既有指令/分支 90% 门槛未降低。独立源码评审最终 PASS；主工作区和 Platform `git diff --check` 通过，本次设计/计划/决策的 17 个相对链接均可解析。
- `npm --prefix requirements-site test` 已完成构建并通过 133/133 内容测试；审计改造没有修改 Requirements Site 需求内容。
- 首次审计实现与低侵入收敛已分别提交本地 `main`（`5ce9ad0`、`8b42a64`）。最新真实 local MySQL/Redis `clean verify` 为 221/221，分支/指令/行覆盖率分别为 90.897%/97.951%/96.437%。根工作区设计、计划和状态文档仍只以根仓库 Git 历史为准；本次未推送或部署。未验证网络层“数据库已提交但响应丢失”、进程 kill/OOM、断电、跨主机全序、物理数据库停机或持久化重试；同 JVM、同 MySQL 的逻辑资源隔离不等于物理零影响，且该能力按设计允许丢失审计事件。

## 8. System-wide RFC 9457 Problem Profile 源码迁移状态（2026-09-06）

- **Implemented：** Tasks 1–12 已完成当前工作区现有第一方路径的源码迁移：Foundation 框架无关 Problem、严格/显式迁移 Reader 和唯一 MVC Writer；Gateway 本地 Writer 与 Owner 响应校验；Platform Owner 状态映射；Operations BFF/Web；Vault Go/OpenAPI/Java Client；Commerce OpenAPI 与 Starter 继承验证；Client Contract、Finance、Risk 的现有 HTTP 测试消费者；以及 Nginx/Connector 限额顺序。未发现新的业务 Controller、第二套 Problem DTO 或服务本地 Advice。
- **Verified：** Build 本地安装通过；Foundation Problem 相关 Reactor 327/327；Operations Web 216/216 且生产构建通过；BFF 315/315；Commerce 68/68；Client Contract 4/4；Finance 25/25；Vault Java Client staged tree 34/34；Risk 全量 148/148；Gateway 聚焦 128/128，完整 878 项只保留与 clean HEAD 768 项完全相同的 13 个失败用例；Platform DB-free 15/15 且源码编译通过；Requirements Site 134/134；Nginx/Gateway 静态边界 4/4，固定 Nginx 镜像语法和 Compose 配置检查通过。各数字是独立门禁，存在共享 Reactor 测试时不得相加。
- **Environment gaps：** 当前主机没有 Go/gofmt，未执行 Vault Go 编译、格式化和测试；Gateway 的 10 个失败与 3 个错误是 clean HEAD 已存在的同一组基线问题，因此未生成 JaCoCo aggregate；Platform 注入本地凭据后的完整验证发现 284 项，但本机 `mor_platform` 含部署/人工数据，与测试要求的仅初始化目录状态冲突，产生 1 个失败与 127 个错误，未获授权重置该数据库。
- **Deployment：** 本次迁移已提交到各受影响仓库的本地 `main`，没有推送、发布或部署。既有 `mor-test` 部署只证明其历史切片，不能作为本次 Problem Profile 已部署的证据。
- **Legacy cleanup：** Foundation HTTP Client、Operations Web/BFF、Gateway 和 Vault 的显式 Legacy Reader/fixture 暂时保留。只有全部已部署第一方 Producer/Consumer 切换并完成环境观测后，才能在独立清理任务中删除旧四字段 DTO、Decoder 和兼容测试，再重跑全量门禁。

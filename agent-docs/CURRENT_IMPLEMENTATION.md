# Code-MoR 当前实现状态

最后核对：2026-09-12

状态：Current status ledger。本页只汇总实现、验证、部署和已决定待迁移事项；架构与业务规则仍由 [`DOCUMENT_GOVERNANCE.md`](./DOCUMENT_GOVERNANCE.md) 路由的主题 Owner 定义。

## 1. 状态解释

- `Implemented`：当前主工作区存在代码或资产，不代表已经通过全部门禁。
- `Verified`：列出的检查曾针对对应实现和环境通过，不外推到其他工作区或环境。
- `Deployed`：只表示明确写出的环境，不代表生产部署。
- `Pending`：结论已确认，但对应代码、模型、物理库、验证或部署仍未完成。

## 2. 应用与基础设施

| 组件 | Implemented | Verified | Deployed | Pending / 未证明 |
|---|---|---|---|---|
| Requirements Site | 需求目录和全局序列源文件已存在，仍是需求事实入口；全系统 Problem Profile 决策已投影到现行需求目录 | 2026-09-11 构建及内容门禁 136/136，lint 通过 | 仅本地站点，不对外发布 | 需求存在不代表六系统全部实现或已部署 |
| Build / Foundation | Maven 构建治理、框架无关 `paytech-http-problem`、严格/迁移期 HTTP Client Reader、唯一 MVC Problem Writer、稳定公共 Header 名称、可复用显式-null/严格时间反序列化器，以及声明式 `@ResourceLock` 已在当前工作区实现 | 2026-09-08 当前 Header/JSON 绑定/MVC Problem 聚焦门禁 29/29，HTTP Problem Profile 5/5；此前 DTO 清理后的 Problem 相关 Reactor 326/326、注入仓库本地 Redis 凭据后的全 Foundation 505/505 是对应历史源码基线 | 最新 Snapshot 仅安装到本机 Maven 仓库供下游源码验证，未发布 Nexus、未部署 | Legacy Reader 须在部署与观测完成后删除；Compensation Event 测试仍自动重建 Schema；Resource Lock 尚未发布或部署 |
| API Gateway | 动态 Snapshot、数据库 Target/Route 绑定与转发、BFF 管理监听隔离补改已实现，见第 17 节；Client Credential 链源码仍保持 | RCO-16 补改全量 1199/1199；指令 96.649021%、分支 90.084034%；Platform 注册时序修正后 386/386，指令 97.784105%、分支 90.058766%，阶段 5 本地门禁通过 | 2026-09-12 本轮动态路由完整制品已部署 mor-test，Snapshot 接受 51 条 Operations Route；含用户已接受的既有 Client 代码 | Client 入口未开放；本轮 test 功能验收按用户要求由用户执行，见第 17 节 |
| Platform Support | 当前工作区已将现有实现拆为 `api + access-control + audit-security + web + test`；Web 仍是唯一应用和组合根，全部模块仍使用一个 Platform 数据库和主 DataSource，审计保留同库专用写池；HTTP/Schema/跨系统合同未改变 | 2026-09-12 RCO-16 在获准独立本机库完成 `clean verify` 386/386，指令 97.784105%、分支 90.058766%；原共享库不满足仅初始化前置的问题通过独立验证库处理，保留原有人工数据；详见第 17 节 | 既有 Platform 应用容器保持；2026-09-12 RCO-16 的 182 行注册及发布状态 SQL、Target 表注释已应用 mor-test；模块拆分和 Problem 映射源码未部署 | 精确制品及环境发布另行核对；CUSTOMER 认证/JWKS、生产 Vault/KMS、Console Resource、MQ 和生产部署；其余资源锁/HMAC 风险见第 8 节 |
| Operations Console BFF | 独立 `web + test` Maven 仓库；当前工作区除既有页面适配外，已使用 Foundation Writer 输出统一 Problem，并在边界重建自身 503/504，保留显式上游迁移 Reader | 2026-09-06 JDK 25 `clean verify` 315/315；严格 Profile 与真实 `HEAD` 聚焦门禁 272/272 | 2026-09-12 RCO-16 BFF 已部署 `mor-test`，19201 回环管理监听健康；原 2026-09-11 跨服务验收保留，本轮功能待用户验收；见第 17 节 | 无数据库和签名密钥；不拥有核心授权或跨系统事务；Legacy Reader 待部署证据后删除 |
| Operations Console Web | React/TypeScript/Vite/Ant Design 已实现；当前工作区的 API 消费者严格读取 Problem、显示安全 detail/requestId，并为已知目标保留显式旧 Reader | 2026-09-06 测试 216/216，TypeScript 与 Vite 生产构建通过 | 2026-09-12 RCO-16 Web 已部署 `mor-test`，公开入口 200 且 index.html Hash 匹配本轮镜像；功能待用户验收；见第 17 节 | Client/Partner 未来会话方案不由此决定；Legacy Reader 待部署证据后删除 |
| Vault V1 | Go Server、OpenAPI 与 Java Client 的当前工作区已切换统一 Problem/`HEAD`/Request ID 合同；三表 Protect/Reveal 业务边界不变 | 纯 RFC staged tree 的 Java Client `clean verify` 34/34，OpenAPI/YAML/source 静态门禁通过；当前主机没有 Go/gofmt，Go 编译与测试未执行 | 既有 `mor-test` 合成 smoke 部署保持不变；本次 Problem 源码未部署 | 补齐 Go 门禁；Platform 仍使用批准的开发 Mock；生产 KMS、强服务身份、真实消费者、合规验证和生产部署未完成；Legacy Java Reader 待删除 |
| CCT | 38 表/428 字段目标模型及完整人工 Schema；本地空库已初始化，条件启用的 Checkout 预检/解析已实现，Risk 资格端口仍关闭，见 CCT-QUERY-01 | 全量 Maven verify 32/32；本地 MySQL 结构与测试数据清理核对通过；四系统模型证据见 OE-CLIENT-MODEL-01 | 应用未部署 | Risk 新资格接入、最终解析完整链路、其他写入口/API/MQ、存量库迁移和部署未完成；旧 V5/Hash Reader 保留，Scope 不得绕过 Risk 激活 |
| Commerce | 15 表/249 字段目标模型；创建只做 CCT 基础预检并建立基础订单，不创建 Payment 单据；已删除 `schemaVersion` 和旧创建分支。本地兼容增量 SQL 与公共 HTTP 收敛见 COM-PRECHECK-01 | `./mvnw -q clean verify` 194/194，0 失败/错误/跳过；真实本地 MySQL、随机端口 HTTP、CCT HTTP Simulator、创建/重放/恢复通过。JaCoCo 90.02%（1254/1393），通过原 90% 门槛 | 应用未部署 | 本地桥接结构为 15 表/250 字段，保留历史列，未等同完整目标 Schema。支付准备、最终合同/Tax/Payment、晚到定价、其他公开接口/事件及真实跨服务联调尚未完成 |
| Payment | 30 表/393 字段目标模型、Payment Pricing Fact 和完整人工 Schema 已生成 | 2026-09-11 四系统自动模型和场景回归通过，详见 OE-CLIENT-MODEL-01 | 未声明 | 尚无完整 Java 业务服务；新模型不代表已有支付执行、Risk 联调或数据库迁移 |
| Finance | 84 表/1,109 字段目标模型和完整人工 Schema 已生成；首个余额查询 Java 仍是旧数字身份切片；当前测试消费者统一校验真实 HTTP Problem | 2026-09-06 JDK 25 `clean verify` 25/25，含本地 MySQL 余额路径与 RFC 1123 Date 门禁 | 未声明 | 本次未执行 SQL，旧余额切片不能直接消费新身份 Schema；完整服务与部署未证明；手工缴税异常分支待专项模型闭合 |
| Risk & Compliance | 71 表/九路由配置切片；本次原 V1 增加可空终态、冷却/主体标签参数、手工拒绝文案、Risk-owned 主体绑定与显式历史格式兼容，见第 15 节 | 2026-09-11 JDK 25 `clean verify` 155/155，含真实本地 MySQL/随机端口 HTTP、旧 Hash Golden 和配置冻结门禁；模型/Schema 189/189 | 2026-09-12 RCO-16 已部署本机 `mor-test`，新增两类字典表及索引，实际 74 表；本轮按用户明确指令跳过备份，旧批次备份事实保留 | Risk 配置的真实 Nginx/Gateway/BFF/Risk HTTP 链已验收；PDManer 人工 Review、其他环境/生产仍待完成；运行期评估和真实动作不在本期范围；全局 COMP-01/DB-01/Vault V1 Gate 保持 |
| `mor-local` | 仓库提供 MySQL、Redis、RabbitMQ、Nacos 等本地依赖的手工环境 | 当前本机运行状态应按 [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 的命令即时核对 | 仅本机开发环境 | 自动测试只能复用该环境，不得自行拉起替代容器或连接真实业务环境 |
| `mor-test` | 独立 Docker 测试拓扑当前为 11 个容器、36 条 Route、21 个 Console Resource、19 个 ACTIVE Permission，浏览器入口为 `http://localhost:28080` | 已记录真实登录/IAM/审计/Refresh/Logout、Gateway 控制面启停/幂等/版本冲突/Snapshot 恢复与 Vault 合成 smoke | 本机独立测试环境；Gateway 控制面与 Risk V1 配置入口已部署，11 个容器健康 | 不是 Maven `auto`、覆盖率或生产证据；生产部署仍未声明 |

组件级命令、精确测试数字和运行配置由各仓库 README/实施记录维护，本页不复制会频繁变化的日志。

- **RCO-14 Implemented / Verified / Deployed（2026-09-11，mor-test）：** 原 V1 的 Risk、Operations BFF/Web 配置增量已实现；[需求与接口合同](./plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)覆盖三条既有接口扩展，新增 API 和 Platform 注册 DML 均为 0。真实 MySQL/HTTP、BFF 转发/授权、前端和模型验证通过；开发库和 test Risk 库分别在用户授权、备份后应用增量 SQL；三个应用已部署，真实入口验收通过。精确证据及范围见第 15 节。

## 3. 当前生成模型快照

| 系统 | 表 | 字段 | 唯一索引 | 访问索引 | 本库关系 | 1:1 | Diagram Groups |
|---|---:|---:|---:|---:|---:|---:|---:|
| Client & Contract | 38 | 428 | 61 | 43 | 64 | 6 | 6 |
| Commerce | 15 | 249 | 24 | 12 | 15 | — | 4 |
| Payment Orchestration | 30 | 393 | 42 | 45 | 65 | 8 | 7 |
| Finance | 84 | 1,109 | 145 | 155 | 160 | 22 | 6 |
| Risk & Compliance | 74 | 898 | 110 | 80 | 113 | 9 | 8 |
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
| TEST-01 | Foundation 不使用 Testcontainers 或自动 Schema 初始化 | Redis Starter 已迁移为复用仓库 `local-environment` 且真实门禁通过；Compensation Event 测试仍自动重建 Schema | Compensation Event 静态门禁先失败后通过，并在手工准备的 `mor-local` 上完成受影响 Maven 测试 |
| COMP-01 | 本地可靠表使用 `event_key + event_type + sub_event_type + 可选最小 payload_json`，任何 Vault Token 禁止进入 Payload | 六库文档/PDManer 仍有旧 source/version/schema/hash/64 KiB 契约；Foundation `CompensationPayloadPolicy` 尚未显式拒绝 `vaultToken` / `*_vault_token` 标准字段名，也缺少相应反例测试 | 六套 Manifest、Policy、模型、场景、SQL 和测试一次性迁移并复核统计；Foundation 登记入口拒绝标准 Vault Token 字段并通过反例测试 |
| DB-01 | 新建表不使用物理 CHECK/FK，Sidecar 仅表达应用不变量/逻辑关系 | 多份旧物理决策与 Vault DDL 仍有现在时或实际约束 | 应用保护与反例测试齐备后，审核并手工执行逐库约束迁移；物理核验通过 |
| CCT-PRICING-01 | CCT 用全局不可复用 `payment_method_profile_code` 描述支付方式特征，并由 Term Version 下准确 OE Price 组合 Percentage、Fixed、Tiered Component/Band | 四系统目标模型及完整人工 Schema 已同步；新 Wire、Java、Payment 晚到事实、真实 Risk 接入和数据库执行仍待完成，详见 OE-CLIENT-MODEL-01 | 完成实际历史盘点及新旧 Reader 隔离；实现各 Owner HTTP/MQ、准备恢复与定价；另行接入 Risk，在授权环境迁移并通过真实数据库与端到端验证后关闭 |
| FIN-01 | Tax Payment 事前承诺门禁与已发生银行扣款的事后事实登记分离 | 专项核对已确认：现有 `Bank Item + Reconciliation Exception` 可保存并追踪已发生扣款，但当前模型不能无损地把“没有前置 Tax Payment 的真实扣款”闭合到 Return/Payable | 正常流、无前置记录真实扣款、重复、金额/归属不匹配和解决流程均有模型与测试 |
| RUN-01 | Gateway 本地 Nacos 默认 Namespace 与 `mor-local` 一致 | Gateway 当前默认/示例仍为 `public` | 失败断言、配置修正和相关 Gateway 门禁通过 |
| RISK-OPERATIONS-RULE-CONFIG-01 | `CHECKOUT_PREPARE` 配置后端、BFF 适配与 V1 页面已实现；本次增加动态主体绑定、可空终态和冷却/主体标签配置、手工拒绝文案，保留不可变版本、历史 Hash、持久幂等与纯终态 TEST Candidate | 本次 Risk `clean verify` 155/155、BFF `verify` 1008/1008、Web 887/887 与生产构建、模型/Schema 189/189 通过；获授权备份后已准备本机 `mor_risk` 增量结构/目录。本次 test 环境原有 Risk 脚本 164/164、新显式验收入口 2/2 离线检查通过；三个应用已部署，`RISK_V1_CONFIGURATION_SMOKE_OK` 与 `FULL_OK` 验收通过，人工 PDManer Review 尚未完成 | 本机 test 已完成协调切换和 Gateway/Auth/PLT-I02 实际 HTTP 链验收；其他环境须单独授权和验证。冷却/标签配置不能冻结或执行，运行评估、Shadow、审批和激活须另行设计；不得用本项关闭 COMP-01、DB-01 或 Vault V1 |
| JSON-CANONICALIZATION-01 | Foundation 独立 opt-in `paytech-json-canonicalization` 已实现严格 RFC 8785 JSON Object/Number Profile；Risk 与 Commerce Web 是当前两个真实直接消费者。Risk 保持七组既有业务 Projection；Commerce 自有 Checkout 请求/恢复快照 Projection、Hash Schema 和 SHA-256 | 原 Foundation/Risk/共享 JS fixture 门禁保持；Commerce 全量 194/194 通过，JCS 请求 Hash/恢复快照已由真实随机端口 HTTP 与本地 MySQL 的创建、重放、恢复及损坏失败关闭场景执行。精确依赖边界仍要求 Client API、Common Core、Starter、Compensation Event、Gateway 和非消费者不得获得 JCS 传递依赖；BOM、Foundation Parent/JAR 仅安装到本机 Maven 仓库，未发布 Nexus、未部署 | 发布前重新执行 Build → Foundation → Consumer 顺序与逐字节向量门禁，获授权后发布明确 artifact/version；Platform、CCT、Finance 及其他未来 Owner 只在真实消费者切片中直接依赖并自有 Projection、Hash Schema、SHA/HMAC、存储和重放语义，部署后再更新本台账 |
| COMMERCE-CLIENT-API-01 | Commerce Hosted Checkout V1 的非运行 Java Owner ingress contract module 仍定义公共 OpenAPI、三接口与十操作；其中 `createCheckout/getCheckout` 已由 Commerce Web 真实同步 MVC 实现，另外八操作保持仅设计。当前结构分离 Creation、Query、Create Idempotency、Idempotency Validator、Snapshot Codec、Owner Converter、Prepared Assembler、Repository 与 `CLAIMED -> ORDER_COMMITTED -> COMPLETED` 三个 Create 恢复检查点；Java 接口复用 Foundation Header 名称，Request ID 留在 MVC 基础设施，可信身份使用正 `long`，请求通过正常 Jackson/Bean Validation 绑定并忽略未知属性 | [业务决策](./decisions/2026-09-05-commerce-client-api-v1.md)与[设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)继续拥有公共 wire 语义；[恢复简化决策](./decisions/2026-09-09-commerce-checkout-recovery-simplification.md)拥有 Create Checkout 的三个真实恢复检查点；[结构简化设计](../docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md)与[边界简化计划](../docs/superpowers/plans/2026-09-08-commerce-client-api-boundary-simplification.md)记录当前重构。当前模型/人工 SQL 为 15 表/246 字段；本地 MySQL 上全量 `clean verify` 194/194、JaCoCo 行覆盖率 90.20%，覆盖真实 HTTP Create/Get、幂等恢复、并发窗口、过期、Owner 失败/畸形合同与合法 PARTNER 来源。合同/漂移 49/49、JavaDoc、模型/Schema 24/24 和 Requirements 136/136 证据保持。四个 `*Client` 是 local/auto/test-only Commerce 端口，不是 Owner wire 合同 | 接入 Owner 发布的 `@HttpExchange` 合同和有状态协议 Simulator 后补真实 wire/时序联调。其余八操作、Funds 事件、Webhook、Gateway/Platform/Nginx 与 Consumer Checkout 页面完成端到端激活后才可声明外部可用 |
| GATEWAY-CLIENT-API-01 | Client API 使用专用域名推导的 Channel、PLT-I03 opaque Credential、Credential/Tenant Guard、exact Route Scope、外部可生成的 Request ID 与最小三 Header 下游合同 | Gateway 已消费 Platform 的 reactive PLT-I03 合同并实现独立 Credential Security Chain、Client Identity、exact Scope、最小下游 Header、响应/错误边界与纵向测试；Client 专项 130/130 通过，完整 1031 项仍有与对照副本相同的 39 个失败项；详细验证与剩余前置见第 13 节。PLT-I03 API artifact 仅构建安装到本机 Maven 缓存，不等于远端发布或部署 | 完整门禁和 Platform、Owner、Nginx 前置完成后，才获授权准备生产 Approved Target 与 `DISABLED` Target/Route。首条 Route 切为 `ACTIVE` 后逐实例确认 CLIENT_API Target/Route Count `0 -> 1` 并 smoke；失败先撤销测试 Credential，再禁用 Target/Route 并等待每实例 Count 回到 0。只有成功收敛和 smoke 后才声明可用；后续同 Count 变更须先提供 Snapshot Revision/Fingerprint |
| AUDIT-01 | Platform 既有 Operations HTTP 审计由 Controller 注解声明，提交结果确认后独立事务尽力写入；允许丢失，审计故障不改变业务结果 | [正式设计](../docs/superpowers/specs/2026-09-05-platform-controller-best-effort-audit-design.md)与[实施计划](../docs/superpowers/plans/2026-09-05-platform-controller-best-effort-audit.md)已实现：29 个方法精确覆盖、业务保护迁移、有界异步隔离、同库独立事务及真实故障回归均通过本地门禁；本次把业务审计接口收敛为 `identify/target/record/suppressReplay`，Processor 由唯一事务执行器自动桥接事务终态并新增递归字节码门禁；首次实现与低侵入收敛已分别提交本地 `main`（`5ce9ad0`、`8b42a64`），均尚未推送或部署，网络未知提交、强制终止和物理基础设施故障不在已验证范围 | 当前实现与验证证据见第 7 节；上线前另行完成环境配置、容量评估和部署回归，不把尽力写入描述为零丢失或物理零影响 |

| COMMAND-IDEMPOTENCY-01 | Foundation 独立 opt-in `paytech-command-idempotency` 已实现服务本地 Claim、Lease、Version CAS、恢复阶段推进和稳定响应冻结；JAR 不创建事务、不计算业务 Hash/Snapshot，也不拥有中央数据库。Commerce Web 是当前唯一批准的迁移消费者 | Build BOM 已管理新 artifact；Foundation 聚焦 `-pl paytech-command-idempotency -am clean verify` 通过，其中新模块 11/11；Commerce 已移除重复的 DataObject、Mapper/XML 和 Repository，并以同一表结构接入公共 API；本地 MySQL 全量 194/194 覆盖 Claim、Lease、CAS、阶段恢复和并发提交窗口。JAR 与 BOM 只安装到本机 Maven 仓库，未发布 Nexus；Risk、Platform Support、Gateway 和其他系统未改动 | 发布前按 Build → Foundation → Commerce 重跑并发布明确版本。其他系统是否迁移必须逐系统批准，不能自动接入 |

上述条目为迁移台账，不授权跳过专项设计直接改生产模型或数据库。

## 5. 最近决策与计划

- CCT 定价模型：[`decisions/2026-09-10-cct-payment-method-profile-pricing.md`](./decisions/2026-09-10-cct-payment-method-profile-pricing.md)
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

- 决策：[`decisions/2026-09-05-redis-first-resource-lock.md`](./decisions/2026-09-05-redis-first-resource-lock.md)
- Foundation 设计：[`docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md`](../docs/superpowers/specs/2026-09-05-foundation-resource-lock-design.md)
- Foundation 执行计划：[`docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md`](../docs/superpowers/plans/2026-09-05-foundation-resource-lock-implementation.md)
- Platform 设计：[`docs/superpowers/specs/2026-09-05-platform-support-resource-lock-optimization-design.md`](../docs/superpowers/specs/2026-09-05-platform-support-resource-lock-optimization-design.md)
- Platform 执行计划：[`docs/superpowers/plans/2026-09-05-platform-support-resource-lock-optimization-implementation.md`](../docs/superpowers/plans/2026-09-05-platform-support-resource-lock-optimization-implementation.md)

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
- 首次审计实现与低侵入收敛已分别提交本地 `main`（`5ce9ad0`、`8b42a64`）。该轮收敛后的真实 local MySQL/Redis `clean verify` 为 221/221，分支/指令/行覆盖率分别为 90.897%/97.951%/96.437%；资源锁改造后的当前门禁见第 8 节。根工作区设计、计划和状态文档仍只以根仓库 Git 历史为准；本次未推送或部署。未验证网络层“数据库已提交但响应丢失”、进程 kill/OOM、断电、跨主机全序、物理数据库停机或持久化重试；同 JVM、同 MySQL 的逻辑资源隔离不等于物理零影响，且该能力按设计允许丢失审计事件。

## 8. Foundation / Platform Resource Lock 验收（2026-09-06）

- Foundation `paytech-redis-starter` 提供公共 repeatable `@ResourceLock(prefix, key)`，不引入 `@LockKey`。动态表达式只接受 `#param` 或一层 `#param.field`；启动门禁扫描实际 Spring Bean 方法并编译参数/属性绑定，同时验证方法可代理、同步返回类型、唯一 Foundation Advisor/Interceptor、事务顺序、唯一 Foundation Redisson Client、Namespace、Wait/Lease 和实际代理链。存在注解但配置、表达式、Client 或代理无效时，应用在 Ready 前失败；没有注解的应用不被强制依赖 Redis。
- Platform 删除服务内 `OperationsResourceLock` / `Locks` 和生产代码中的直接 Redisson 调用，认证 7 处、管理 8 处、Gateway 2 处，共 17 处标准注解。动态入口只接收预先形成的安全 `resourceKey`；固定 Gateway Catalog 省略 `key`。Login、Refresh、密码、创建账号命令等不会把登录标识、邮箱、密码、Token、Vault Token 或原始 Idempotency-Key 写入 Redis Key。
- Redis 负责跨实例协调，数据库只保留 Bootstrap Account X、Session X、Actor Account/Member SHARE、按需 Role SHARE/X 和后台 Audit Chain X 等最终不变量保护。Gateway 只锁一个 Catalog Redis 资源，短 `SERIALIZABLE` 事务、Version CAS 与唯一幂等记录继续承担本地最终仲裁；认证慢工作移到事务外，目标账号写入以条件 DML/CAS 为主。
- 并发 RED/GREEN 另外暴露并修复两条授权竞态：同一事务内用一次数据库 `UTC_TIMESTAMP(6)` 冻结 `effectiveAt`，防止未来角色在多次查询之间生效并获得中途授权；撤销当前或未来 ACTIVE `PLATFORM_ADMIN` 绑定时按需取得管理员 Role X，再以同一 cutoff 重算当前有效管理员，避免并发撤销后变成零管理员。
- JDK 25 全量 `./mvnw -q clean verify` 复用仓库 `local-environment`，29 个 Suite、283 项全部通过，0 失败/错误/跳过。强制 Phase A JaCoCo 为指令 `22733/23231`（97.856%）、分支 `1706/1894`（90.074%）、行 `3383/3520`（96.108%）；同轮 Maven aggregate 为指令 97.756%、分支 90.244%、行 95.901%。Foundation 全量 410/410、其中 Redis Starter 120/120；Foundation 结构门禁、Platform 63 表模型检查和 Requirements Site 134/134 也通过。
- 当前实现只存在于工作区；Foundation Snapshot 仅安装到本机 Maven 仓库供 Platform 验证，没有发布、提交、推送或部署。开发适配器的固定 HMAC Key 不能证明持久化命令幂等跨生产密钥轮换仍连续；接入可轮换 KMS 前仍需批准并验证版本化查找/迁移或独立稳定派生密钥方案。

## 9. System-wide RFC 9457 Problem Profile 源码迁移状态（2026-09-06）

- **Implemented：** Tasks 1–12 已完成当前工作区现有第一方路径的源码迁移：Foundation 框架无关 Problem、严格/显式迁移 Reader 和唯一 MVC Writer；Gateway 本地 Writer 与 Owner 响应校验；Platform Owner 状态映射；Operations BFF/Web；Vault Go/OpenAPI/Java Client；Commerce OpenAPI 与 Starter 继承验证；Client Contract、Finance、Risk 的现有 HTTP 测试消费者；以及 Nginx/Connector 限额顺序。无生产引用的共享旧四字段 `ApiErrorResponse` DTO 已删除，Operations BFF 的迁移 fixture 改为直接生成旧 Wire JSON；Legacy Reader 行为不变。2026-09-08 当前请求体边界已收敛为标准 Jackson/MVC 与 Bean Validation：JSON 语法、结构和类型错误统一为 400，成功绑定后的 Body 约束错误为 422；Common Core 只提供可复用的显式-null 与严格可选时间反序列化器，不再提供业务字段集合、自定义请求体异常层级或有界 Cause 扫描。Commerce Create Checkout 仍复用唯一 MVC Writer。
- **Verified：** Build 本地安装通过；当前 Foundation Header/JSON 绑定/MVC Problem 聚焦门禁 29/29、HTTP Problem Profile 5/5；此前 DTO 清理后的 Problem 相关 Reactor 326/326 和注入仓库本地 Redis 凭据后的全量 505/505 是对应历史源码基线。Commerce 全量 194/194，含真实 MySQL、随机端口请求身份/绑定、Owner 失败投影与 Schema Runtime，JaCoCo 行覆盖率 90.20%。Operations Web 216/216 且生产构建通过；BFF 315/315；Client Contract 4/4；Finance 25/25；Vault Java Client staged tree 34/34；Risk 全量 148/148；Gateway 聚焦 128/128，完整 878 项只保留与 clean HEAD 768 项完全相同的 13 个失败用例；Platform DB-free 15/15 且源码编译通过；Requirements Site 136/136；Nginx/Gateway 静态边界 4/4，固定 Nginx 镜像语法和 Compose 配置检查通过。各数字是独立门禁，存在共享 Reactor 测试时不得相加。
- **Environment gaps：** 当前主机没有 Go/gofmt，未执行 Vault Go 编译、格式化和测试；Gateway 的 10 个失败与 3 个错误是 clean HEAD 已存在的同一组基线问题，因此未生成 JaCoCo aggregate；Platform 注入本地凭据后的完整验证发现 284 项，但本机 `mor_platform` 含部署/人工数据，与测试要求的仅初始化目录状态冲突，产生 1 个失败与 127 个错误，未获授权重置该数据库。
- **Deployment：** 2026-09-06 系统迁移已提交到各受影响仓库的本地 `main`；2026-09-08 Header/请求绑定简化已提交到 Foundation、Gateway 与 Commerce 的本地 `main`。两者都没有推送、发布或部署；既有 `mor-test` 部署只证明其历史切片。
- **Legacy cleanup：** 无生产引用的共享旧四字段 DTO 已独立删除；Foundation HTTP Client、Operations Web/BFF、Gateway 和 Vault 的显式 Legacy Reader/fixture 暂时保留。只有全部已部署第一方 Producer/Consumer 切换并完成环境观测后，才能在独立清理任务中删除旧 Decoder、旧字段解析分支和兼容测试，再重跑全量门禁。

## 10. Platform Support 第一阶段能力模块拆分（2026-09-06）

- **Implemented：** `platform-support-service` Reactor 现包含 `platform-support-api`、`platform-support-access-control`、`platform-support-audit-security`、`platform-support-web` 和 `platform-support-test`。Access Control 与 Audit/Security 是不可部署的能力 JAR；Web 仍是唯一 Spring Boot 应用、Controller/Processor 组合根以及资源锁、事务、请求审计生命周期和命令幂等基础设施 Owner。未预建空的 File/Document 或 Delivery 模块。
- **Boundaries：** Web 单向依赖两个能力模块；Access Control 只通过不可变审计事实和 `OperationsAuditService` 依赖 Audit/Security；Audit/Security 不反向依赖 Access Control。业务 DataObject、Mapper、XML 与 Repository 已迁入对应 Owner，Web 不跨模块访问 DAL。所有模块仍共用一个 Platform 数据库、主 DataSource 和主事务管理器；既有审计专用写池仍连接同一数据库并仅承担独立尽力写事务。
- **Compatibility：** `platform-support-api`、Controller 路由、JSON DTO、Problem 合同、数据库 Schema/初始化 DML、业务 SQL、Redis 资源锁 Key、Nacos 服务身份和部署单元均未改变。现有测试的业务断言保留；改动仅适配包名、Mapper 资源路径并新增 Maven DAG、Owner 和跨模块泄漏门禁。
- **Verified：** 135 项聚焦纯逻辑/结构测试通过；`./mvnw -q -DskipTests package` 通过；Web executable JAR 包含两个能力 JAR；隔离应用启动通过，认证开启的应用上下文也完成启动；Requirements Site 135/135。共享 `mor_platform` 含部署/人工数据，不满足审计 Runtime suite 的仅初始化目录前置，因此 17 项在 fixture setup 被安全阻止；数据库未重置，当前工作区不声称全量 `clean verify` 通过。
- **Deployment：** 本次拆分已提交到 `platform-support-service` 本地 `main`（`79b05fd`），尚未推送、发布或部署；影响范围限定为 Platform Support 构建与运行时类路径，不要求 Gateway、BFF、Vault 或六个核心系统同步修改。
- **Authorities：** [设计](../docs/superpowers/specs/2026-09-06-platform-support-modular-code-design.md)；[执行计划](../docs/superpowers/plans/2026-09-06-platform-support-capability-module-migration.md)。

## 11. Foundation RFC 8785 JSON Object Profile 本地验收（2026-09-07）

- **Implemented：** Build BOM 管理 `io.github.erdtman:java-json-canonicalization:1.1` 和 Foundation artifacts；Foundation Reactor 现有 12 个模块，其中 `paytech-json-canonicalization` 只发布 `canonicalizeObject(byte[])` 与固定安全异常。V1 严格限制为 Root Object、非负安全整数 Number、严格 UTF-8、重复属性/Unicode/大小技术门禁；它不拥有 Projection、NFC、业务 Array 排序、SHA/HMAC、Hash Schema、存储或重放语义。
- **Consumer：** `risk-decision` 与 `paytech-commerce-web` 直接 opt-in 依赖该 JAR。Risk-owned Adapter 继续负责七类 Projection、NFC、Decimal、UTC、ID 与 Array 排序；Commerce-owned Hash/快照组件负责 Checkout 请求、恢复坐标、字符串 Money/ID 和 SHA-256；Foundation 只提供底层 canonical bytes。Common Core、现有 Starter、Compensation Event、Commerce Client API、Gateway 和其他非消费者没有获得传递依赖。
- **Verified：** JDK 25 下 Build `clean verify` 和 `clean install` 通过；Foundation 全 Reactor `clean verify` 为 607/607，JCS 聚焦 `-am clean install` 为 103/103（核心 76、边界 20、合同 7）；根结构/有效 Maven 模型门禁通过；Risk 真实本地 MySQL `clean verify` 为 151/151；两个共享 JavaScript oracle 为 101/101；Requirements Site 构建并通过 135/135。七组 Risk canonical bytes、UTF-8 hex 与 SHA-256 由 Java 和 Node 共享 fixture 逐字节验证，未发生 Hash Schema 迁移。
- **Installed locally：** `com.paytech:paytech-dependencies:1.0.0-SNAPSHOT`、`com.paytech:paytech-parent:1.0.0-SNAPSHOT` 与 `com.paytech:paytech-json-canonicalization:1.0.0-SNAPSHOT` 已安装到本机 `/Users/kenny/.m3/repository`，只用于本次源码验证。实际第三方 JAR SHA-256 为 `ed12a01f28d147898312963a1f704e90290b67a61f34fa3a761f41c134f4e691`，本机解析标记为 `maven-public`，上游 POM 声明 Apache License 2.0。
- **Published / Deployed：** 没有发布 Nexus，没有部署 `mor-test`、生产或任何其他环境；本机 Maven 安装不构成制品发布或部署证据。
- **Remaining：** Commerce Webhook、Platform Webhook、CCT/Finance BCR 等未来消费者只能在各自真实纵向切片中直接依赖已发布 JAR 并增加 Owner 黄金向量；不得因为 Commerce Checkout 已接入而把依赖放进 Client API、Common Core 或 Starter。扩大 Root 或 Number 输入域必须先批准新设计、官方向量和兼容策略。
- **Authorities：** [决策](./decisions/2026-09-07-foundation-rfc-8785-json-object-profile.md)、[设计](../docs/superpowers/specs/2026-09-07-foundation-rfc-8785-json-canonicalization-design.md)、[实施计划](../docs/superpowers/plans/2026-09-07-foundation-rfc-8785-json-canonicalization.md)。

## 12. Commerce Client Checkout 本地纵向切片（2026-09-10）

- **Implemented：** `ClientCheckoutApi#createCheckout/getCheckout` 已由 Commerce Web 的同步 Spring MVC Controller 实现。当前重构把创建、查询、创建幂等、幂等 Validator、快照 Codec、Owner Command 转换和 Prepared Aggregate 组装分成独立职责；通用 Command DataObject、Mapper/XML、Repository、Claim/Lease/CAS 已迁入按需启用的 Foundation `paytech-command-idempotency`。`CreateCheckoutProcessor` 线性编排 claim/resume、authority resolution、prepared persistence、Payment binding 和 replay/expiry，持久化恢复检查点仅为 `CLAIMED -> ORDER_COMMITTED -> COMPLETED`。Commerce 的无副作用 Validator 继续校验当前请求/winner、持久化生命周期、快照坐标和冻结结果；Commerce Domain Service 解释业务阶段并在原有调用方事务中组合 Foundation API。业务 Service、Domain Service 和 Repository 保持 `interface + *Impl`，单实现纯协作者保持具体类；纯依赖赋值构造器使用 Lombok。Create 使用独立的外部语义请求 Hash，以及尚未发布的内部 V1 `{h,p}` JCS 恢复快照；后者以 Payload canonical SHA-256 检测局部损坏（不是认证签名），并 fail closed 校验命令生命周期、命令行/快照坐标和成功回执/实际 Root。`Client + operation + Idempotency-Key` winner、Lease/CAS、应用生成 ID/Order No/三个 Token 候选、正常成功路径三个本地短事务以及 GET 的可信 Client 隔离语义保持不变；CCT、Risk、Finance、Payment 调用仍全部位于本地数据库事务之外。
- **Persistence / model：** Commerce 设计资产已推进为 15 表/246 字段/24 唯一索引/12 访问索引/15 本库关系/4 图，完整人工 DDL 与单一初始化 DML 已生成。应用保持 `spring.sql.init.mode=never`；经用户授权，当前开发机的仓库 `local-environment` 已人工应用 DDL/DML，并固定 Loopback `commerce_app` 测试密码。该本地准备不构成共享环境迁移或部署。
- **Integration boundary：** `CctClient`、`RiskClient`、`FinanceClient`、`PaymentClient` 当前是 Commerce-owned 最小端口。确定性 Local Mock 只允许在 active profile 全属于 `local|auto|test` 且显式 `commerce.integrations.mode=local-mock` 时装配；默认 `disabled` 失败关闭。它们不构成 Owner `@HttpExchange` wire 合同、生产降级或路由启用证据。
- **HTTP / errors / tests：** 两条私有应用路径已实现，OpenAPI 公共路径仍等待 Gateway 映射。Foundation MVC 独占 Request ID 校验/生成/MDC/回写；Commerce API 只接收两个正 `long` 可信身份 Header，并在一个 Web 边界拒绝缺失或重复值。三个 POST 的幂等 Key 仍由单一 Commerce Filter 校验。请求 DTO 使用正常 Jackson 属性绑定，忽略未知属性，拒绝 scalar 到 String 的隐式转换，并以通用反序列化器区分缺失与显式 `null`；结构/类型错误为 400，成功绑定后的 Body 约束错误为 422。Client API 十操作、参数、Request/Response 字段和非显然枚举已同步补齐 JavaDoc/OpenAPI 描述。Test module 现以唯一 `TestApplication` 组合生产应用；随机端口 HTTP 自动测试统一继承 `MockTestBase`，未来人工联调统一继承 `ManualTestBase`，Schema Runtime 保持无 Web 例外。auto/manual 配置固定仓库本地 MySQL、提交且允许环境覆盖的 Loopback 测试密码、禁止 SQL 初始化并显式关闭 Nacos；初始切片未引入 Redis/RabbitMQ/Nacos 消费者；后续 CCT 的 Nacos 接法与验证以 COM-PRECHECK-01 为准。Checkout 业务行为测试全部从 `RestTestClient` 进入真实 Controller → Processor → Commerce Domain Service → Foundation Command Idempotency Mapper → Owner 端口，并联合断言数据库事实；不再直接测试 Commerce Processor、Domain Service 或 Hasher。默认 Owner 行为委托生产确定性 Local Mock，测试 Mocker 只在 HTTP 场景中控制失败、协议畸形和并发窗口。Checkout HTTP 场景覆盖创建/查询/校验、语义幂等、Lease 恢复、同一 `clientOrderId` 并发提交窗口、Token 冲突/回滚、快照与生命周期 fail-closed、过期、Owner 失败投影、合法 PARTNER 来源和已提交 Payment Binding 恢复。
- **Verified：** `./mvnw clean verify` 完整通过，194 项测试，0 失败、0 错误、0 跳过；真实本地 MySQL Repository、Schema Runtime 与所有随机端口 HTTP Suite 均执行。JaCoCo Hosted Checkout aggregate 行覆盖率 90.20%（1831/2030），通过 90% 门槛；没有降低阈值或排除生产代码。此前 Foundation JAR 11/11、Client API contract/drift 49/49、JavaDoc、模型/Schema Node 24/24、生成器 `--check` 和 Requirements Site 136/136/lint 证据保持。
- **Remaining：** 真实 Owner `@HttpExchange` 合同、有状态 Wire Simulator 和跨服务联调仍未实现；当前 Mocker 只验证 Commerce 对端口结果的处理，不证明网络协议或真实 Owner 时序。另八个 Client API 操作、Payment/Funds 事件、Webhook、Gateway/Platform/Nginx、`checkout-web /c/{token}`、发布与部署仍未完成。
- **Delivery state：** 本切片基础实现、恢复简化、Command Idempotency 迁移与测试基础设施重构已提交到各本地 `main`；最新相关提交包括 Build `e914193`、Foundation `b2f146f`、Commerce `6c01860` 和此前根文档 `8326266`。所有相关改动均未推送、发布或部署。
- **Authorities：** [业务决策](./decisions/2026-09-05-commerce-client-api-v1.md)、[原始设计](../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md)、[恢复简化决策](./decisions/2026-09-09-commerce-checkout-recovery-simplification.md)、[Foundation Command Idempotency 决策](./decisions/2026-09-09-foundation-command-idempotency.md)、[结构简化设计](../docs/superpowers/specs/2026-09-08-commerce-client-checkout-structure-simplification-design.md)、[结构简化实施计划](../docs/superpowers/plans/2026-09-08-commerce-client-checkout-structure-simplification.md)、[本地纵向切片计划](../docs/superpowers/plans/2026-09-07-commerce-client-checkout-local-mock-slice.md)。

## 13. Client API Gateway Credential 链本地实现（2026-09-08）

- **Implemented：** Gateway 复用 `platform-support-api` 的 `ApiCredentialValidationApi` / `ValidatedCredentialResponseBody` / `ApiCredentialConstraints`，调用私有 `POST /platform-support/api-credentials/v1/validate`。独立无 Session Security Chain 只接受单值 opaque Bearer Credential，调用时去掉 Bearer 前缀放入内部 `X-Platform-Api-Credential`，成功后建立不可变 `ClientApiIdentity`。认证无缓存、无重试、无 Redirect、无 JWT/匿名降级；请求不要求 HMAC 签名、应用层加密或 Client timestamp。
- **Runtime boundary：** Shared/IP Guard → Credential 验证 → Credential/Tenant Guard → exact Scope → Body budget → 空白 Header 重建及两个可信身份 Header；Request ID 延续已有外部可生成合同。缺失或错误 typed identity 在 Guard、Scope 和下游注入阶段都 Fail-Closed；Owner 只接收三项可信 Header 与 Route allowlist 业务 Header。Client 固定响应 Header、受限 `201 Location`、严格 Owner Problem、无 Operations CORS，以及身份摘要日志已接通。Operations JWT / Cookie / 响应合同保留。
- **Dependency / public docs：** Platform 已提交的 PLT-I03 API 模块执行 `./mvnw -q -pl platform-support-api -am -DskipTests install` 并安装到本机 Maven 缓存；未修改 Platform 业务代码，未向 Nexus 发布。公共 [设计第 8.1 节](../docs/superpowers/specs/2026-09-05-client-api-gateway-design.md)补齐 Method/Path/Header、Owner 类型、字段/集合上限、响应预算与失败映射；Gateway README 补齐调用链和配置说明。
- **Verified：** 当前 `clean verify` 执行 1031 项，Client / PLT-I03 相关 130/130 通过，另两组 Operations 响应/Cookie 回归 41/41 通过。纵向路径使用真实随机端口 Gateway、生产 typed HTTP Client、共享本地 Redis、Platform/Owner HTTP Simulator，覆盖错误凭据、错误/超限/超时/断连响应、Scope、伪造 Header、Body 限制、取消/超时及租约释放。边界静态脚本通过；边界脚本自身 11/11，Requirements Site 构建及 136/136 通过。
- **Full-suite failure comparison：** 完整门禁剩余 36 failures + 3 errors。临时对照源码以 Gateway `996906f` 为底，叠加工作开始前已有的 Risk 测试、Snapshot Fixture、BFF Simulator/Journal、Approved Target 根及 65536 Body ceiling，未包含本次 Client 实现；对照执行 934 项，失败测试名称/参数集合与当前 1031 项完全相同，无新增或消失的失败项。39 项由既有 13 项 transport / health / JWKS / dynamic routing / error contract 与工作区 Risk Suite 的 26 项组成；未在本次越界修复或回滚它们，不能声明完整 Gateway 门禁通过。
- **Coverage：** 完整执行后复跑 Client/Operations 171 项并生成非空 JaCoCo aggregate；专项 Maven 返回 0，不代表完整门禁通过。当前分支覆盖率 78.705212%（1933/2456）低于 90%，报告完整性检查还报缺少编译器生成的 `GatewayGuardProfileCompiler$1`；对照副本同样报告该类缺失，分支覆盖率为 77.919128%（1715/2201）。未放宽门槛、排除生产代码或把报告生成成功视为覆盖率通过。
- **Remaining：** Platform 当前“关联 Tenant 行缺失”按损坏数据返回 503，与公共设计“没有同一 ACTIVE CLIENT Tenant 属于无效认证”的 401 表述仍需 Platform Owner 确认；Gateway 保持依赖失败 503，不猜测错误响应内的业务身份。真实 Provider 部署/服务发现、Credential/Scope SQL 和生命周期、Channel-aware 控制面、真实 Owner Adapter/Approved Target 与 Nginx 域名入口仍需上线前联合验证，本轮 Simulator 成功不代表这些前置已完成。
- **Commit verification：** 仅包含本次 Client 变更的提交树单独导出复验，Client / PLT-I03 与 Operations 响应/Cookie 专项 171/171 通过，边界脚本通过；未夹带工作开始前已有的 Risk 代码、测试、Target 根或 Body ceiling 调整。上述 1031 项完整结果来自包含这些既有未提交变更的工作区，不是纯 Client 提交树的全量结果。
- **Delivery state：** Gateway Client 实现已提交到 `api-gateway` 本地 `main`（`4577ee9`），公共合同和本节台账随配套文档提交沉淀；其他任务的未提交修改保留。未推送、发布或部署；未添加生产 Client Target、未激活 Route、未应用 SQL、未修改 Nginx。完成完整门禁并获准执行跨系统准备后，才进入 Client Route rollout。

## 14. Gateway auto 测试启动与 YAML 配置调整（2026-09-09 更新）

- **Implemented in working tree：** 固定 Redis、Platform 客户端、测试身份、服务端口与发现/调度配置集中到 `application-auto.yml`；Java 不再直接读取 `PAYTECH_REDIS_PASSWORD` 或静态创建默认 Redis 连接。Redis 前置检查和按 Namespace 清理复用 Gateway 应用的 Spring 连接工厂，按测试类管理 Sentinel、不销毁应用连接。隔离故障 Gateway 保留唯一 `auto` Profile，通过 YAML 场景参数和动态关闭端口验证失败关闭；重复的手工连接故障测试收敛到现有真实 HTTP 故障 Suite。
- **Local credential fix：** 已复现样例密码导致的 `WRONGPASS`，随后增加 `application-auto.yml` 对 Git 忽略的 `application-auto.local.yml` 的可选导入；固定配置仍由 Spring 加载，环境变量显式覆盖优先于本机 YAML。经核对现有 `mor-local` Redis 的容器身份及 `127.0.0.1:16379` 绑定，本次修复复用其现有凭据创建权限为 `0600` 的本机文件，不回显凭据、不读取 `.env.local`、不修改 Redis 密码/数据或启停容器。测试自身不查询 Docker 或解析环境文件；隔离故障场景仍只使用唯一 `auto` Profile 和 YAML 中的故障凭据。
- **Verified：** 显式移除 `PAYTECH_REDIS_PASSWORD` 后，以 JDK 25/Maven 执行 20 个 Suite、249/249 项，0 failures/errors/skipped。覆盖 YAML 默认值/本机导入/覆盖优先级/故障隔离，真实 Redis 前置检查、应用连接工厂复用、Sentinel 清理、Token Bucket/Concurrency Lease，以及 Client API/PLT-I03 和 Operations 响应/Cookie 回归。Gateway 边界脚本通过，脚本测试 11/11，Requirements Site 136/136；本机凭据未进入 tracked diff 或本次验证日志。
- **Remaining verification：** 本次解决本机 Redis 凭据阻塞，并完成上述相关回归；未重新执行全量 `clean verify` 或 JaCoCo 门禁。第 13 节的旧工作区全量失败与覆盖率记录仍是历史证据，不据此宣称最新工作区全量通过。IDE 使用同一测试资源导入机制，但本轮实际执行器为 Maven，未通过 IDEA UI 运行。
- **Commit verification：** 仅包含本次测试配置修复的暂存树单独导出，以同一 Git 忽略的本机 YAML 验证，20 个 Suite、249/249 项及边界静态脚本通过；未夹带工作区 Risk 测试、Snapshot Fixture、BFF Simulator、Approved Target 或 Body ceiling 变更。凭据文件及其内容均未进入提交。
- **Delivery / authority：** Gateway 调整已提交到 `api-gateway` 本地 `main`（`de05227`），公共规则和本节台账随配套文档提交沉淀；未推送或部署，未更改 Gateway 生产业务链或其他服务代码。公共规则依 [2026-09-08 决策](./decisions/2026-09-08-middleware-auto-test-configuration.md) 同步到 `UNIT_TEST_GUIDE.md`、本地环境说明及 Java 测试规则投影，其他系统未在本轮批量迁移。

## 15. Operations Risk V1 配置增量（2026-09-11）

- **Implemented：** 按 [RCO-14 决策](./decisions/2026-09-11-risk-rule-v1-configuration.md)与[需求/接口合同](./plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)，修改 Risk、Operations Console BFF 和原 V1 Web。扩展 Authoring Context、Save、Detail 三个已有接口；新增 API、Platform 权限/路由注册 DML 均为 0，九条 Route、五权限、Save 64 KiB 与其他命令 16 KiB 边界保留。BFF 继续先 PLT-I02 后单次 Owner 调用。
- **配置能力：** 支持零或一个终态及最多 16 个附加动作；冷却保存 1..31536000000 毫秒字符串，标签最多 32 项并按代码去重排序；拒绝代码/文案手工输入。主体由 Risk 工程目录提供，Web/BFF 动态消费；本 Point 当前只有 `CURRENT_CLIENT_V1`，没有伪造 Consumer 输入。附加动作标记 CONFIGURATION_ONLY、无 Handler 证据，Freeze 在锁后闭包内拒绝；纯终态继续支持冻结和 TEST Candidate。
- **历史兼容：** 新格式以持久化 `content_format` 选择三个配置 Hash Schema；原七组 Golden 保留。旧消息 null、旧非拒绝原因代码与旧 Hash 不补造、不重写；新 DENY 完整校验在历史成功命令重放之后执行。
- **Verified：** JDK 25 下 Risk `./mvnw -q clean verify` 155/155，BFF `./mvnw -q verify` 1008/1008，均 0 failure/error/skipped。Risk 使用仓库 `local-environment` 的真实 `127.0.0.1:13306/mor_risk` 和随机 HTTP 端口，覆盖保存/回读/重放、14 类错误配置、新纯终态 Freeze/Candidate、附加动作冻结拒绝及运行事实计数不变。BFF 通过协议 Simulator 核对新字段逐项传播、授权拒绝后 Risk 零调用、动态目录和 Handler 字段隔离；这不是跨进程 Gateway 全链路证明。
- **前端/模型：** Web `npm test` 883/883、TypeScript/Vite 生产构建通过；使用生产编辑器与合成目录完成本地浏览器选中/折叠、配置保留、动态主体选择和布局检查，未见控制台 error；临时预览已清理。根 `node --test scripts/pdmaner/risk-*.test.mjs` 189/189 通过，模型/Schema 为 71 表、865 字段，未增加物理 CHECK/FK。
- **本机 SQL：** 用户明确授权“允许备份后应用本机 mor_risk”。先将五张受影响表备份到 `/Users/kenny/.codex/backups/risk-rule-v1-20260911/mor_risk-affected-tables.sql`（目录 0700、文件 0600），再人工执行 [2026-09-11 增量 SQL](../risk-compliance-service/docs/database/sql/2026-09-11-risk-rule-v1-configuration.sql)。增加 3 列、调整动作元数据/注释并插入 2 个动作版本和 4 个参数定义；迁移前后原 Rule Set/Point Hash 及全部命令幂等记录摘要一致。新列位置与生成 Schema 已核对；空库初始化仍由单一初始化 DML 文件拥有。
- **mor-test 部署（后续明确授权）：** 用户授权部署，包含备份、test SQL 更新和合成数据验收。已核对当前 Docker context、Compose/MySQL 身份和部署前检查；预先构建并保留原镜像，备份原 Resource 后隐藏六个风控 Resource，停止旧 Web/BFF/Risk，在 test Risk 零连接时备份完整库并应用增量，再按 Risk → BFF → Web 依次替换并健康验证。最后恢复 `RESOURCES_EXPOSED`；九条 Route 保持原状态和版本，其余八个容器的 ID/image 均与操作前相同，未重建基础设施或删除 Volume。
- **test 备份/迁移证据：** `/Users/kenny/.codex/backups/risk-rule-v1-mor-test-20260911/` 包含完整 Risk SQL 备份、原 Platform Resource 表备份、本次增量副本、列/历史 Hash/幂等摘要核验和应用 image ID 清单；目录 0700、文件 0600。数据库是 `mor-test-mysql-1` 的 `mor_risk`（独立宿主端口 23306），与先前开发库 13306 分开；目录检查返回 `PREPARED`，旧 Rule Set/Point Hash 和所有旧幂等记录在迁移前后保持一致。
- **test 验收：** `risk_rule_v1_configuration_smoke.py --verify` 返回 `RISK_V1_CONFIGURATION_SMOKE_OK`，验证动态主体目录、Handler 隔离、冷却/标签保存、去重与同键重放、新版本保存与旧版退役、Consumer 绑定拒绝、手工拒绝文案及 409、附加动作 Freeze 400 与纯终态冻结；运行事实计数无变化。既有 `risk_rule_configuration_smoke.py --full` 返回 `FULL_OK`，覆盖五项权限分离与恢复、九路读写链、TEST Candidate、64 KiB 成功/超限拒绝和唯一 Nacos 实例。临时合成 IAM 夹具通过显式 cleanup 清理；不可变合成规则保留为验收证据。公开 `/risk/rule-configuration` 的 HTML 和两个 JS/CSS 资源与本次 Web 构建逐字节一致。通用 `smoke.py` 在既有限流窗口恢复后重试通过，覆盖登录、分页、导航/角色、Gateway 控制面查询、错误文案、Refresh 轮换和退出后的撤销；未调整限流配置。
- **V1 样式补齐（用户截图反馈后）：** 首次配置增量交付保留了旧表单布局，未完整落地设计稿。现已在原页面补齐可收起的左侧规则列表、选中规则编辑区、紧凑条件行/嵌套组、决策单选、手工拒绝面板、附加动作卡片和时间单位、基本信息弹窗与保存状态；空规则集同样使用左右分栏。Web 回归 887/887、TypeScript/Vite 构建通过；真实浏览器合成数据验收覆盖切换保留内容、收起/展开、动态主体、拒绝文案、冷却单位、标签、排序与完整保存 Payload，旧 NOT/RIGHT/精确 Decimal 保持，390px 无横向溢出且浏览器 error 为零。仅替换 test Web 容器，另十个容器的 ID/image 保持，13 个静态资源均与构建逐字节一致。通过 test 公开入口实际登录，验证新建空规则集、添加规则、REJECT/冷却编辑与放弃修改后退出；未提交规则命令。Requirements Site 构建及 136/136 通过；未新增 SQL 或后端改动。
- **精确镜像：** Risk `sha256:55e5a50e17a3`、BFF `sha256:ca802c008aa4`、当前 Web `sha256:b9f304f73e71`（此前缀只用于可读摘要）。首次部署完整 image ID 在上述 `application-images.json`，样式修正的前后容器和完整 Web image ID 在同目录 `web-style-deployment.json`；原 Web 镜像另保留 `before-risk-v1-style-20260911` 标签。部署只使用本机 Maven 缓存与本地镜像，未发布 Nexus 或推送；本地提交状态见下文。
- **本地提交：** Risk `d609e04`、Operations BFF `4b64b96`、Operations Web `7e8036c` 已按用户要求提交到各仓库当前分支，未推送；配套文档、模型和验收脚本随根仓库本次提交。提交前模型门禁 189/189、Risk 离线验收脚本测试 166/166 再次通过。
- **范围：** 本次只证明本机 `mor-test` 的规则配置可用，不是生产部署。实际评估、冷却/打标签、标签查询、拒绝字典、Shadow、审批、激活仍不在本期范围；含附加动作的版本禁止冻结。PDManer 应用内人工 Review、其他环境 SQL 和生产验收仍独立保留。


## 16. Operations 规则集列表优化（2026-09-11）

- **范围与阶段：** [本需求](../requirements/20260911/operations-console-风控规则集列表优化/requirement.md)及[已批准技术方案](../requirements/20260911/operations-console-风控规则集列表优化/technique.md)用户明确“发布”后，阶段 7 本机 mor-test 部署与真实入口验收已完成，用户随后明确“先git提交一下”，本地提交与归档见下述阶段 8。[交付记录](../requirements/20260911/operations-console-风控规则集列表优化/delivery.md)关联 AC、代码与证据。
- **Implemented in working tree：** Risk 新增管理根，按根及当前配置查询/分页；整集删除保留编码和全部历史，任一历史版本被当前 ACTIVE Release 使用即阻止删除。保存/冻结/删除/新 Candidate 复用 Redis 资源锁及本地事务、引用校验和 CAS。BFF 新增独立删除权限与一次 Owner 转发。Web 精确编码校验、精简列表、详情/编辑/红色删除菜单、真实 URL 历史及未保存保护，隐藏 Candidate 页面。API 不升级版本，25 个 DTO 类型去协议版本后缀。Platform 单一初始化 DML 同步 16 行注册数据，初始化文件继续保持 Route/按钮 DISABLED 默认值；test 已通过本轮受控 SQL 开放。Gateway 无本轮代码修改。
- **Foundation 必要修复：** 经用户单独同意，将 `paytech-redis-starter` 的 MeterRegistry 获取延迟到首次记录指标，避免 Advisor 提前初始化破坏 JVM 监控。原监控构造器及资源锁合同保留；本机 Maven 安装只用于 Risk/BFF 集成验证，未远程发布。
- **Verified：** JDK 25 下 Risk 与 BFF `./mvnw -q clean verify` 均退出 0，分别 162/162、1024/1024，0 failures/errors/skipped；Risk 后续本地 Redis 前置检查也通过删除 HTTP 聚焦回归。Foundation `-pl paytech-redis-starter -am test` 的三个相关模块合计 235/235（其中 Redis Starter 121），不称全 Foundation 验证。Web 876/876、TypeScript/Vite 生产构建通过。Risk 全模型/Schema/场景/Hash 门禁 190/190、Requirements Site 构建与内容测试 136/136 通过；API clean JAR 包含 25 个新 DTO 名且无旧类型残留。详细命令见[验证摘要](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage4-verification.json)。
- **浏览器及清理：** 生产 Web 组件配合成 HTTP 的 11 个场景通过，覆盖键盘/悬停菜单、删除取消/同键重试、过滤与分页的 Back/Forward、未保存保护及 Candidate 旧 URL，0 JavaScript errors；不是新功能 Gateway 全链路或 test 环境验收。临时预览已关闭；local Root/Version/Release/Command 与 `risk-auto:*` 均无测试残留，三个终态 Action 恢复 ACTIVE。
- **local SQL：** 用户单独确认备份后应用 `127.0.0.1:13306/mor_risk`。2026-09-11 11:08:20 UTC，核对历史为空、无其他业务连接及 Risk 服务停写后备份原 71 表，再应用本需求 Risk DDL/DML，manifest 为 `[]`；新增 `t_rule_set` 12 列、0 行回填。原 71 表 schema/data dump 与执行前逐字节一致。[执行证据](../requirements/20260911/operations-console-风控规则集列表优化/assets/local-sql-applied.json)记录文件与备份 SHA-256；备份在 `~/Code-MoR-backups/risk-rule-set-list-local-20260911T110820Z/mor_risk-before.sql`。
- **边界：** 本轮仅部署本机 mor-test，阶段 8 已获本地提交授权；未推送、远程发布制品或操作生产；第 15 节的镜像为上一需求发布时的历史证据，当前三应用镜像以下述阶段 7 为准。
- **范围限制：** 非空历史回填、真实 Gateway/Platform 删除路由及权限已在阶段 7 核验；实际规则评估、动作执行、Shadow、审批和激活继续不在本期范围。后续阶段段落中“未执行”均指该阶段结束时。

- **阶段 5 独立验收：** 复核业务/接口/并发边界，发现删除确认框缺少名称并修复；本阶段产品代码仅改 Web 确认状态和弹窗。真实 BFF → Risk 独立进程配本地 MySQL/Redis 完成 38 个 HTTP 检查，覆盖当前配置单行列表、权限、实际使用保护、过期删除、同键并发、永久编码及成功历史回放；Platform 为现有 Simulator，并非真实 Gateway/Platform 权限环境验收。
- **阶段 5 页面及监控：** 修复后 Web 876 项与生产构建通过；11 个合成接口浏览器场景、5 个真实业务接口浏览器场景（25 次 API 调用）通过，0 JavaScript errors，390px 页面稳定后无横向溢出。真实打包 Risk 的 JVM 内存及资源锁指标均存在。后端及模型源码未改，相关全量门禁沿用上述阶段 4 结果。详情见[综合验收](../requirements/20260911/operations-console-风控规则集列表优化/acceptance.md)和[阶段 5 验证摘要](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage5-verification.json)。
- **阶段 5 清理及动作边界：** 7 组合成规则及相关版本/历史/幂等数据已按所属身份清理，临时 Web/BFF/Risk 服务已关闭；数据库和 Redis 最终只读核对见验证摘要。本阶段未再应用 SQL，未写 Platform/test，未发布制品、部署、提交或推送；第 15 节部署事实不变。

- **阶段 6 准备：** 只读核对 mor-test 仍为 23 个版本/21 个规则集、零 ACTIVE Release、原 36 Route/21 Resource。已生成 21 条 test 专用 Snowflake manifest，核对 71 个 BIGINT 主键列无冲突，DML 同构只读投影为 21 个有效根；实际 INSERT 未执行。准备精确维护/路由开放/资源开放/失败关闭 SQL，未应用。发布方案和恢复限制见[发布包](../requirements/20260911/operations-console-风控规则集列表优化/release.md)。
- **阶段 6 制品与兼容：** 已构建本地专用 Risk `46aff45920d3`、BFF `59be81991657`、Web `83a5141710c3`；完整 ID、JAR/文件 SHA 及验证边界见[机器可读清单](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage6-release-package.json)。Risk test Compose 补齐现有 Redis 地址/凭据注入及健康依赖，运行配置尚未切换。当前部署的 Platform/Gateway 编译器离线接受新 37 条目录，Gateway 接受维护态 27 条目录；不需替换这两个服务。旧通用 smoke 的 36/21 分类不适用于本次最终 37/22，阶段 7 按发布包做精确实际入口验收。

- **阶段 6 检查与停点：** 部署脚本离线测试 31/31、Requirements Site 构建/内容测试 136/136、跨仓库 diff/链接/SQL 校验通过。最终只读核对两库元数据及 11 个容器完整 ID/启动时间不变，development 镜像标签未切换，Gateway 仍为旧 36 条目录；本阶段没有应用 SQL、部署或提交。待用户按发布包授权阶段 7。

- **阶段 7 Deployed：** 用户明确“发布”，本机 `desktop-linux/mor-test` 完成两库备份及受控 SQL、Risk → BFF → Web 协调升级。当前镜像前缀为 Risk `46aff45920d3`、BFF `59be81991657`、Web `83a5141710c3`，三个容器 healthy，其他八个容器 ID/image 不变。完整记录见[部署验收报告](../requirements/20260911/operations-console-风控规则集列表优化/deployment.md)与[部署证据](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage7-deployment.json)。
- **阶段 7 SQL/入口：** 备份位于 `~/Code-MoR-backups/risk-rule-set-list-test-20260911T131033Z/`，完整 Risk/Platform SQL 已核验大小、结尾和 SHA-256。新增管理根并回填 21 集，迁移前后原 71 表 schema/data dump 字节一致。Gateway 接受精确 37 Route、22 ACTIVE Operations Resource，`SUCCESS/NONE`、无 Last Valid fallback。唯一健康 Risk 实例，JVM 内存/ResourceLock 指标、公开 HTML/JS/CSS 与新制品均通过核验。
- **阶段 7 真实验收/清理：** 23 项 HTTP 核对及浏览器查询、菜单、详情、直接编辑、Back/Forward、未保存保护、整集删除、旧 Candidate URL、390px 布局核验完成，0 JavaScript errors。快速浏览器导航触发现有读取 429 后，等待限流恢复核对已保存版本并继续完成，未重复保存或改限流；不宣称单次连续脚本全绿。原 21 根/23 版本及 9 Candidate 状态不变，Evaluation/Action Effect 仍 0；仅新增两组各两版合成规则并以 API 整体删除，保留历史。只读账号真实 DELETE 403 后退出并用 API 删除（CLOSED）。详见[最终核验](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage7-final-verification.json)。
- **阶段 7 交付停点：** 发布锁已正常释放，临时容器探针已移除；未提交或推送。旧 smoke 的 36/21 分类仍需后续维护，本轮按已批准的精确 37/22 目录和真实入口完成验收。下一步须由用户按[待提交清单](../requirements/20260911/operations-console-风控规则集列表优化/commit-plan.md)确认阶段 8；只确认 test 验收不自动提交。

- **阶段 8 本地提交与归档：** 用户明确“先git提交一下”。paytech-foundation `6f2d692997e2`、risk-compliance-service `d9f0c1c633a8`、operations-console-bff `2e0e8d707db0`、operations-console-web `bf607aaaa961`、platform-support-service `ef6570f71833`，根仓库为[本归档记录](../requirements/20260911/operations-console-风控规则集列表优化/assets/stage8-commits.json)所在提交。均在原 main 分支，本轮未推送、创建 PR/分支或重新部署。四份 Risk 选择性提交源码经 JDK 25 编译，7 个 class 去除调试元数据后与已部署制品一致；其他组件源码与阶段 7 清单一致。详情见[提交归档](../requirements/20260911/operations-console-风控规则集列表优化/completion.md)。
- **阶段 8 保留项：** 原有公共流程/目录治理、其他需求材料以及 Risk 的 import/格式整理保持未提交，未覆盖工作区源码。冻结入口、人员展示、详情文案、限流体验、名称搜索/复制等讨论仅为后续建议，没有混入本轮实现。等待用户确认本需求结束。

## 17. Operations 规则历史与业务字典（2026-09-11）

- **当前状态 / 用户验收确认（2026-09-12）：** 本期功能及 Web 反馈调整已部署 test；用户明确“我验收了，代码提交吧”，确认验收并授权本地提交。以下保留阶段证据，旧“等待/未部署”状态只代表当时检查点；当前提交归档见 [completion.md](../requirements/20260911/operations-console-风控规则集历史与字典管理/completion.md)。不涉及推送、生产或再次部署。

- **Stage 5 verified / Stage 6 paused for Gateway design amendment：** 用户回复“继续”，确认[精简后的设计包](../requirements/20260911/operations-console-风控规则集历史与字典管理/technique.md)并授权研发。保留两张字典主表，移除字典回执与标签主体关系；默认 v1、类命名不加版本。确认过程见[交付记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/delivery.md)。
- **Source implemented / development regression passed：** Risk/BFF 的 14 个新增接口及原规则保存校验、Web 只读详情/历史/引用图/两类字典与受控选择已落到源码。字典按代码和 CAS 维护当前值，未知写入结果由人工查询确认；原命令幂等和回放不变。未新增运行时决策或实际冷却/打标签能力。
- **Model / registration artifacts：** Risk 模型为 74 表/898 字段，新增 2 表/21 列、2 个反查索引和 2 处旧字段注释；完整 Schema 与增量对齐。Platform 初始化 SQL 同步 182 条插入及一次管理员角色更新，新 Route/Resource 均 DISABLED；静态等价检查通过。Risk 本机 DDL 已按本轮授权备份后应用；Platform 注册 SQL 未应用。
- **Verified：** Risk 最终空基线全量 **186/186**（27 类），BFF **1040/1040**，Web **883/883** 及生产构建通过，Platform Catalog **21/21** 通过；均无失败/错误/跳过。浏览器 **26 个场景**（22 个真实 BFF/Risk 场景 / 84 次 API 请求，另 4 个合成只读 HTTP 焦点场景）、0 JavaScript errors，含 1360/1024/390 px、历史/引用导航、字典未知结果、权限及错误态；Platform/登录/导航/Gateway 头为测试夹具，未替代真实入口验收。[阶段 5 报告](../requirements/20260911/operations-console-风控规则集历史与字典管理/acceptance.md)。
- **Local SQL applied / not deployed：** 用户回复“允许”后，本轮 DDL 已应用至本机 127.0.0.1:13306/mor_risk，2 张新表/21 列/2 反查索引及 2 处注释已按模型核对，受影响旧表行数不变；[备份与执行记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/local-sql-application.json)。Platform 数据库、test、部署、制品发布、Git 提交和推送均未操作。Risk API 仅 local Maven install 供本机 BFF 编译。设计画板和本次源码不是 test 页面已更新的证据。

- **Stage 5 corrections / cleanup：** 修复 Web 历史详情 `size=1`、字典确认离开后抽屉残留、历史页返回列表、菜单/抽屉键盘焦点。两类字典四种确定锁顺序、旧空消息和复制规则/动作等兼容已真实验证。临时三服务已停止，本轮 2 个规则集/13 个版本及字典/命令夹具精确清理，相关 9 表空、无其他 Risk 连接；首次受浏览器夹具影响的全量失败在空基线重跑后通过。阶段 6 已获用户确认；阶段 7–8 未开始。

- **Stage 6 historical gate / next preparation：** 原 mor-test 只读基线为 72 表、23 个 DELETED 规则集/27 个版本、无 ACTIVE Release，现有 37 Route/22 Resource；新增字典/182 行目录尚未应用。实际部署 Platform 编译器接受 51 路由，旧 Gateway 拒绝字典下游路径；内存增补路径可通过。随后用户确认完整数据库动态路由和 BFF 管理端口隔离；补改阶段 5 本地门禁现已通过。等待用户确认返回阶段 6 后，重新采集实际角色/绑定与版本、重生成 SQL、核对未部署 Gateway Client 源码、补齐 Gateway/BFF 精确制品；当前未部署，旧发布包不可直接执行。[发布准备草案](../requirements/20260911/operations-console-风控规则集历史与字典管理/release.md)。

- **Stage 6 prepared artifacts / no rollout：** Risk/BFF/Web 三个独立本地镜像已构建，Risk API 内嵌字节一致；BFF 1040 项及最终字典边界 16 项通过，SQL 状态门禁 16 项通过。新镜像未切换 development 标签；11 个 test 容器、原业务版本 Hash 和目录状态最终只读核对不变。[待完善发布包](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage6/release-package.json)明确 Gateway 门禁未通过，尚不可部署。

- **Gateway amendment implemented / functional regression passed / gates remain（2026-09-12）：** 用户确认[修订方案](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-dynamic-routing-design.md)并授权阶段 4。Gateway 取消业务静态 Registry，从同一快照绑定 Target/Route 并经原框架链转发，关闭请求级 Route Filter Cache；未匹配路由 404 后关闭连接，修复复用连接后续请求阻塞。保留已部署 65,536 字节请求体上限。BFF 独立回环管理端口 19201、业务端口 18201 及 test 探针工件已同步；Platform 模型/Schema 仅同步 1 张表注释，0 新 API/Wire 字段/表/业务列/索引/注册 DML。
- **Gateway amendment stage 4 historical verification：** Gateway 全量 **982/982**（60 类）、BFF **1041/1041**、Platform 相关 **23/23**（含真实本地 MySQL/HTTP 目标变更及停用）、模型/Schema/场景 **48/48**，边界脚本通过；37/27/27/51 目录离线编译通过。Gateway 分支覆盖率 **78.445378%（1867/2380）** 未达 90%，仍报已有 `GatewayGuardProfileCompiler$1` 完整性问题，未降低门槛；Platform 旧完整目录套件因 Resource 12/预期 21 在写入前停止，未放宽断言。实际证据见[研发报告](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-development.md)及[验证摘要](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-development/verification.json)。
- **Gateway amendment stage 4 historical authorization / rollout：** 当时未应用 Schema/初始化/注册 SQL；Platform 自有测试夹具清理后五张目录表与运行前一致。未部署、发布镜像、Git 提交或推送。该阶段随后获用户确认，补改阶段 5 当前进展见后；当时两个门禁、实际 Nacos/公开入口和容器探针尚不构成完整验收。旧 BFF 镜像需重建，Gateway main 还有未部署 Client API 工作，阶段 6 仍须核对精确制品源码；原单路径排障及旧发布包只保留历史证据。


- **Gateway amendment stage 5 / passed（2026-09-12）：** Gateway 最终 `clean verify` **1199/1199**（65 类）通过，指令 **96.649021%（18257/18890）**、分支 **90.084034%（2144/2380）**，覆盖率及报告完整性通过；仅按 JaCoCo 本身的 synthetic/module 规则识别编译产物，原 90% 门槛及业务类排除范围未放宽。框架 413 错误码映射已修复，新增安全/协议/配置/真实 HTTP 验证；本轮复核 Gateway 修改文件及 BFF 补改 4 文件 Hash 不变，复用 Gateway 1199 / BFF 1041 项证据。[阶段 5 验证报告](../requirements/20260911/operations-console-风控规则集历史与字典管理/gateway-acceptance.md)。
- **Authorized isolated local SQL / original preserved：** 用户明确允许准备独立本机验证库后，已备份结构及授权元数据，创建 `127.0.0.1:13306/mor_platform_rco16_validation` 并应用现有 24 表 Schema/683 条初始化插入；只授既有应用账号该库数据权限。测试后 8 张目录表逐项恢复初始化状态，原 `mor_platform` 结构 Hash/全部表计数未变；[SQL 证据](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/local-sql-application.json)、[最终复核](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/final-database-check.json)。原库及 test 未应用本期注册或注释 SQL。
- **Platform acceptance / registration corrected：** 用户回复“可以”确认权限注册时序与独立本机补丁。已备份后将 7 条待发布绑定改为 REVOKED/version=2、管理员 version=3；完整初始化、独立 Oracle、182 条增量清单/SQL 同步，新增量的 6 条绑定使用 REVOKED/version=1。原 4 个角色/人员管理 403 已闭合，Java 授权上限未变。最终 `clean verify` **386/386**（42 类），指令 **97.784105%（23697/24234）**、分支 **90.058766%（1839/2042）**，均通过原 90% 门槛。真实 HTTP 完整快照 8 项、动态目标 2 项随全量通过；此前真实 DB 36 条有效快照和 51 条仅内存开放投影证据仍成立。原 377/6 失败记录保留为历史。[修正方案](../requirements/20260911/operations-console-风控规则集历史与字典管理/pending-registration-permissions.md)、[最终证据](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/permission-correction-verification.json)。
- **Stage 5 cleanup / next checkpoint：** 最终独立库 24 表/683 行，8 张目录表逐元组等于修正后的初始化，测试夹具为空、无遗留应用连接；原库结构 Hash/全部表计数未变。发布生成器按实际原 ACTIVE 绑定处理管理员/Viewer/自定义角色，21 项离线状态与 182 元组一致性检查通过；需求站点 136/136。五份旧 test SQL 已加执行前失败门禁，阶段 6 必须重新采集绑定/角色并生成精确发布包。当前等待用户确认返回阶段 6；没有 test 操作、部署、制品发布、提交或推送。[数据库复核](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/gateway-acceptance/permission-correction-database-check.json)。

- **Stage 6 refreshed candidate package（2026-09-12）：** 用户随后确认返回发布准备，已只读刷新实际 mor-test 基线，仍为 11 个容器、Risk 72 表/23 个 DELETED 根/27 个版本、Platform 37 Route/22 Resource。五份发布/恢复 SQL 已按实际 7 条原绑定、管理员 9/Viewer 4 重生成，取代阶段 5 的不可执行旧文件；正常开放角色为 12/6，21 项边界及实际版本离线推演通过。候选镜像为 Risk `c6a4f44a8cba`、BFF `1a6c0110750d`、Web `ef04bdbbd75a`、Gateway `70ed9f3aa481`；Risk/Web 复用精确专用制品，BFF 重新离线打包，24 项双监听/字典边界通过后因 Foundation 依赖 Hash 差异补跑完整 verify 1041/1041；最终测试 JAR 与镜像一致。Gateway 使用阶段 5 干净构建制品。四镜像 JAR/18 个 Web 文件、内嵌 API 字节均核对一致；部署中 Platform/候选 Gateway 接受 37/27/27/51 投影，旧 Gateway 接受 37/27/27 恢复投影。全部为准备和离线验证，无 test SQL/运行实例切换。[完整候选发布包](../requirements/20260911/operations-console-风控规则集历史与字典管理/release-final.md)。
- **Stage 6 explicit source scope / stage 7 authorization pending：** 当前候选 Gateway 包含此前未部署的 Client API 及公共适配代码。实际 Nginx 两 API location 固定 OPERATIONS，所有既有/新增 Route 均为 OPERATIONS，Client Route 为 0；携带代码仍须明确接受，不能用入口未开放替代制品范围批准。已提供源码/制品差异、SQL Hash、备份、四应用顺序、BFF 探针恢复及限定合成验收清单，等待具体部署授权。Platform 应用与其他六个保留容器不替换，development 标签未变；无 Git 提交/推送或远程制品发布。[精确清单](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage6-final/release-package.json)。

- **Stage 7 deployed / user acceptance pending（2026-09-12 06:50 UTC）：** 用户在理解完整 Gateway 制品后明确“部署”，随后要求省去备份和代理功能验收、由用户验证。已按该最新范围应用维护、Risk DDL、Platform 注释/182 行注册及路由/资源开放 SQL，未执行备份或合成业务验收。Risk 74 表，Gateway 当前 Snapshot 为 `SUCCESS/NONE`、51 条 Operations Route、非 Operations Route 为 0；32 个有效 Operations Resource，管理员/Viewer 版本 12/6。Risk `c6a4f44a8cba`、BFF `79b697a757a9`、Gateway `70ed9f3aa481`、Web `ef04bdbbd75a` 四个应用已替换且 healthy；其余七容器身份保持。公开入口 `http://localhost:28080` 返回 200，前端 index Hash 对应本轮制品。业务功能未由代理在 test 验收，等待用户反馈；未提交、推送或远程发布。[部署检查点](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage7/deployment.json)。
- **Stage 7 startup correction：** 首次 BFF 进程已启动，但镜像内 `LocalHttpHealthProbe` 仍限定旧 18201 地址，面对 Compose 的 19201 回环地址退出 64。只替换探针对应地址并重新封装 BFF，业务 JAR 保持 `d66936ac…` 原字节；19 项探针专项检查通过后容器 healthy，从已完成注册的检查点继续，未重放 DDL/DML。最终 BFF 镜像取代阶段 6 的 `1a6c0110750d` 候选，[补正制品记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage7/bff-probe-image.json)。

- **Stage 7 user-requested demo data（2026-09-12）：** 部署后用户另行要求“帮我造几条数据进去”。已通过真实 test Operations HTTP 创建并保留 3 条 DEMO 拒绝原因、3 条 DEMO 标签，以及 CHECKOUT_PREPARE 下 `DEMO_AMOUNT_GUARD`、`DEMO_REGION_GUARD`、`DEMO_USD_ALLOW` 三个规则集，各 2 个版本，当前均 DRAFT。美元示例旧版本冻结并创建 1 条 TEST DRAFT 发布引用供关系图展示，未激活或执行规则。创建后只读确认每个规则集列表一行、版本两条；不构成完整业务验收。原有数据未修改，使用的临时登录会话已退出。[示例数据与服务端 ID](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage7/demo-data.json)。

- **Web-only visual follow-up（2026-09-12）：** 用户附图要求折叠侧栏显示名称/简介并去掉页面返回按钮，明确授权直接修改 Web、部署 test、由用户验收。已部署 Web `8832199077ef`，替代上述 `ef04bdbbd75a`：180px 紧凑规则卡片保留名称、条件数和决策摘要，编辑/只读一致；规则页面统一浏览器返回，未保存离开保护保留。相关 69 项检查与 TypeScript/Vite 构建通过，Web healthy、公开 HTML Hash 匹配；其他十容器身份/镜像不变，无 API/SQL/备份/提交/推送。视觉和业务验收等待用户。[部署记录](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/web-ui-followup/deployment.json)。

- **Stage 8 accepted / local Git archive（2026-09-12）：** 用户明确“我验收了，代码提交吧”。risk-compliance-service `27cf50b6fc48`、operations-console-bff `6822928fc355`、operations-console-web `9165b95e76da`、api-gateway `2f1f70e85e1a`、platform-support-service `d40b268b6440` 已提交到原 main；根仓库为[本期归档清单](../requirements/20260911/operations-console-风控规则集历史与字典管理/assets/stage8-commits.json)所在提交。应用改动与此前验收源码 Hash 一致，Pencil 只新增本期 12 个画板；本次需求站点构建及 136/136 项检查通过。五个应用工作区干净，较早的公共流程草稿和上一需求材料仍保留在根工作区；未推送、创建 PR/分支、重新部署或应用 SQL。

<a id="oe-client-model-20260911"></a>

## OE-CLIENT-MODEL-01：2026-09-11 四系统模型切片

本次按用户授权修改 CCT、Commerce、Payment、Finance 的模型和配套工件；PDManer 包含在范围内，Risk 的其他在途改动不属于本切片。决定见 [四系统模型决定](decisions/2026-09-11-cct-commerce-oe-pricing-model.md)，产物、SQL 路径及迁移前置见 [实施记录](../requirements/20260911/client-contract-OE与Client模型梳理/model-implementation.md)。

- 已完成：四份模型及图组、生成器、独立字段/关系 Oracle、完整人工 Schema、合成场景和文档路由。模型当前数量见本页第 3 节。
- 已验证：四系统模型及专项 Schema/身份检查 **147/147**；现实场景回归 **129/129**；Requirements 内容门禁 **71/71**。只声明这次实际运行范围，不沿用先前的 136 项或 Java 结果作为新模型验收。
- 稳定 ID 对照：CCT 380、Commerce 237、Payment 370、Finance 1090 个同名保留字段及其所属保留表的 PDManer ID 均未漂移；被改名或移出目标的结构按本次差异处理。
- GUI：PDManer 已刷新 CCT。新 OE、Scope、Affiliation、Agreement Party 检查通过；两个原有 JSON 字段的 largeText Base Type 触发建议级提示，物理 JSON 类型和 Base Type 与变更前相同。CCT 不宣称 GUI 全绿，另外三库的应用内检查待完成。
- 模型切片当时未完成：四系统新 Java/API/MQ 行为、正式 Wire 迁移、真实 Risk 新身份/Scope Review 联调、存量数据库迁移、真实端到端验证与部署。该模型切片没有执行 SQL；后续 CCT 查询与本地空库初始化见 CCT-QUERY-01。Finance 旧余额查询 SQL 明确保留为旧运行切片，Commerce 现有 Java/本地库同样未升级。
- 历史与激活门禁：不改历史订单、旧 Scope、签署结果、JSON/Hash 或经济结果；先盘点真实旧身份和 Reader，再安排人工迁移。Risk 新门禁不可用时不激活新范围，不伪造旧数字 ID。


<a id="cct-query-20260911"></a>

## CCT-QUERY-01：2026-09-11 Checkout 预检与最终解析首批

用户确认保留 Source Relation，并授权先实现 CCT 两个查询；[切片与数据库动作清单](../requirements/20260911/client-contract-OE与Client模型梳理/cct-query-implementation.md)记录当前范围。Risk 和其余系统代码没有纳入本批。

- **Source implemented / Runtime gated：** `client-contract-api` 发布私有 GET `/checkout-eligibility/v1/precheck` 和 POST `/checkout-context/v1/resolutions`。Web 条件装配本库 MyBatis 只读快照、Client/Primary/准确四要素/来源校验、按优先级的合同/条款/法律证据检查，以及相关 Root 和准确胜出者的输出前复核。默认 `checkout.enabled=false`；Risk 新身份资格查询尚未存在，真实资格端口明确返回 `503 OWNER_UNAVAILABLE`，没有默认通过或测试替代实现。
- **Verified：** 在本机 Java 25/Maven Wrapper 下执行未筛选测试的 `./mvnw -q verify`，**32/32** 通过，0 失败/错误/跳过；包含 12 个规则、3 个接口契约、4 个 HTTP 边界、9 个真实 MySQL 业务和 4 个原运维基础用例。Web 可执行 JAR 与 API JAR 构建成功。业务用例覆盖预检、合同过期后按优先级继续、配置/Root 变化、未知数据库 Code 拒绝及 Risk 未接入时的明确 503；不把本地候选成功解释为最终解析 HTTP 200。首轮暴露的泛型枚举 Handler 复用问题已修复：XML 按 `javaType` 使用逐枚举注册的 Handler，并补充未知状态不外泄内部信息的用例。完整日志与制品摘要见下方证据目录。
- **Local database applied：** 用户明确确认后，在 **2026-09-11 19:10:43 UTC** 向 `mor-local-mysql-1` / `127.0.0.1:13306/mor_cct` 人工应用 CCT 完整 Schema；执行前该库为 0 表，未执行业务初始化 DML。实际 **38 表、428 字段、142 个主键/唯一/普通索引**的类型、可空、默认值、字符集/排序规则、完整注释及索引列顺序与 PDManer 一致，物理 FK/CHECK 为 0。全量验证后逐表精确计数均为 0，合成 Fixture 无残留。没有对 test 或其他业务库执行写入。
- **Backup / evidence：** 受限目录 [`/Users/kenny/.codex/backups/cct-checkout-20260911T191028Z/`](/Users/kenny/.codex/backups/cct-checkout-20260911T191028Z/)保存空库备份、实际执行 SQL、验证日志和 `verification.json`；目录权限 700，文件 600。空库备份 SHA-256 为 `04534aba2e19340eda8baa066b8be94992e654a43dcc37234b38ae427f00c0d4`，实际执行 SQL SHA-256 为 `26ef700eef63ba8e2d22bbfd1fdae64176a9a489de233f0708f2541e7f21c54a`。执行后四份 Schema 的状态注释统一改为链接本台账，DDL 语句未变；不能将完整建表文件重复应用于本地现有库。
- **Other verification：** 前一源码批次的 `npm --prefix requirements-site test` 构建及 **136/136** 保持为该批证据，本次没有修改需求站源码。23 个 Mapper Query 的 **164 个投影列**已对照模型；本轮 Schema `--check` 和实际库核对通过，文档/模型补充检查见同一证据目录。
- **Still pending：** Risk Owner 新资格 HTTP 接入、最终解析 HTTP 200/依赖超时及真实跨 Owner 竞态验证、写入口的 Root 传播、Commerce 支付准备接入（基础预检见下方 COM-PRECHECK-01）、存量环境迁移及应用部署。当前通过的是 CCT 本地规则、真实数据库查询和入站边界；Price 匹配接口、Scope 激活/签署写操作不在本批。没有提交、推送或部署。

<a id="commerce-precheck-20260911"></a>

## COM-PRECHECK-01：2026-09-11 Commerce 基础创建接入 CCT

用户确认“下一步让 Commerce 接入基础预检”，随后于 2026-09-12 回复“执行”，授权本地备份、增量迁移与综合验证；具体范围见
[实施记录](../requirements/20260911/client-contract-OE与Client模型梳理/commerce-precheck-implementation.md)。

- **Source implemented（2026-09-12 直接替换修正）：** 用户确认旧 Commerce 代码未发布，当前 Create 统一在事务外通过
  CCT Owner `CheckoutEligibilityApi` 预检，仅提交基础 Order/Items、退款仓位、资金投影与 PREPARING Session，
  按 `CLAIMED → ORDER_COMMITTED → COMPLETED` 冻结回执。删除旧 Resolve/Risk/Finance/Payment 创建链、四组 Local Mock、
  无消费者的 Prepared 聚合/Mapper/模型以及历史测试夹具；`CheckoutCreationService` 直接承担现有基础创建。
  请求和回执 JSON 不再有 `schemaVersion`，无 Schema 1/2 分支；不预分配 Context/Legal/Payment ID 或下游幂等键。
  Foundation 的可选快照版本元数据传 null；语义请求 Hash、永久 Client Order ID、三个冻结 Token、Lease/CAS 与恢复检查点保留。
- **Wire：** Create 固定返回 `PENDING/ESTIMATED/PREPARING`，税额、舍入、总额均为 null；GET 投影当前状态，
  成功创建重放保持最初回执。Java DTO 与原 v1 OpenAPI 直接调整，移除旧响应构造器，当前状态和金额字段为必填。
  `context_format` 不再参与 Java 模型、GET 或恢复分发；本轮不执行 DDL，既有非空列插入固定 `TERM_PRICE`，
  旧数据库列的物理清理仍未执行，不能据代码简化宣称完整目标数据库迁移完成。
- **CCT adapter（2026-09-12 服务发现修正）：** 已移除 `commerce.cct.base-url` 和手工构建 RestClient/接口代理。
  Commerce 通过 `@ImportHttpServices(group="client-contract-service")` 自动注册 CCT Owner API，Spring Cloud LoadBalancer
  按服务名选择 Nacos Discovery 提供的实例；CCT Web 已补同名注册依赖与配置。Foundation 既有 HTTP 普通 JAR
  新增 `configureGroup(...)` 挂载原单层异常边界，不增加 Boot/Cloud 依赖或新中间件模块。
  Spring Group 配置 2 秒连接/5 秒读取超时及禁止重定向，LoadBalancer 和底层 Apache HTTP 都禁止自动重试。
  成功 JSON 验证类型、日期、字段闭合与资格结果；发现为空/连接中断为依赖不可用。
  明确无候选才冻结配置失败；配置变动/歧义及技术失败保留命令恢复能力。CCT API JAR 本轮仅安装到本地 Maven 供编译，未发布 Nexus。
- **Previous development evidence（已被下方直接替换验证取代）：** Java 25 下 22 个测试类共 **119/119** 开发检查通过，0 失败/错误/跳过，包括 API/OpenAPI、
  金额与旧规范回执字节向量、出站真实 HTTP Simulator、Spring 启动、请求绑定、身份、错误及架构/配置门禁；
  补充重复字段、未知字段、超长 Body、无效日期和尾随 JSON 后 CCT Wire **6/6** 重验通过。
  `./mvnw -q -DskipTests package` 及 Client API `javadoc:javadoc` 成功；Mapper XML、增量列与目标模型存在性、
  本轮 Markdown 本地链接和 `git diff --check` 通过。开发检查没有执行 Checkout 业务落库。
- **Local database applied（2026-09-12）：** 按明确授权，向 `mor-local-mysql-1` / **127.0.0.1:13306/mor_commerce**
  人工应用 [增量 ddl.sql](../requirements/20260911/client-contract-OE与Client模型梳理/db/commerce/ddl.sql)，执行时间 `2026-09-12T03:40:40.700244+00:00`。
  执行前确认该库没有其他连接，备份全库并校验完整建表内容/摘要。迁移前 **15 表/246 字段**、所有表为 0 行；
  迁移后 **15 表/250 字段**，四个新增列和九个可空调整符合脚本，原字段类型、默认值、字符集/排序规则及 **51 个索引**不变，
  原列数据摘要一致，物理 FK/CHECK 为 0。该桥接结构保留历史列，不等同 **249 字段**完整目标 Schema；没有执行全量 CREATE。
- **Previous full verification（2026-09-12，直接替换前源码）：** 迁移后在 Java 25 下执行未筛选测试的 `./mvnw -q clean verify`，**214/214** 全部通过，
  0 失败/错误/跳过；包含真实随机端口 HTTP → 生产业务链 → 本地 MySQL，以及 CCT 实际出站 HTTP 到 Simulator。
  基础创建、只写五类事实、最终金额为空、无候选稳定失败、超时/不可用与恢复、过期、Token 冲突回滚、永久 Client Order ID、
  缺事实拒绝、损坏回执拒绝及 Schema 1 历史恢复均通过。JaCoCo 行覆盖率 **90.33%（2131/2359）**，通过原 **90%** 门槛。
  Web 可执行 JAR 和 API JAR 构建成功；没有跳过或降低门禁。本轮未发现需要修改代码的失败。
- **Previous full verification（2026-09-12 直接替换后、服务发现修正前）：** Java 25 下未筛选、未跳过的 `./mvnw -q clean verify`
  **195/195** 通过，0 失败/错误/跳过；JaCoCo **90.08%（1326/1472）**，原 **90%** 门槛不变。
  覆盖当前唯一创建链、CCT 实际 HTTP 到 Simulator、缺失/非法 Origin 拒绝、确定拒绝与配置变化/歧义、真实超时、
  并发永久订单号冲突、两个检查点 Lease 接管、Token 冲突回滚、创建回执在后续状态变化后不变、损坏快照及闭合 JSON。
  快照去版本回归先出现预期失败，再由直接替换实现通过；删除旧业务用例，保留并迁移适用的幂等/错误/查询用例，
  不用保留失效功能换取覆盖率。Web/API JAR 构建成功，`git diff --check` 通过；结束后本地 15 张表精确计数全部为 0。
  本轮仅 Commerce 代码与相关合同/记录变化；没有新增 DDL、修改 Risk/CCT/Finance/Payment 源码或执行部署。
- **Previous direct-replacement evidence：** [/Users/kenny/.codex/backups/commerce-single-flow-20260912T040743Z](/Users/kenny/.codex/backups/commerce-single-flow-20260912T040743Z)
  保存修改前源码归档、初始失败证据、最终完整构建日志、Surefire、JaCoCo、源码/制品 SHA-256 和逐表清理结果；
  `verification.json` 为本轮验收记录。CCT 验证仍为真实 HTTP 契约到 Simulator，最终 OE 解析/支付准备及真实跨服务联调未完成。
- **Previous full verification（2026-09-12 服务发现修正后、公共 HTTP 收敛前）：** Java 25 下 Commerce 未筛选的 `./mvnw -q clean verify`
  **192/192** 通过，0 失败/错误/跳过；JaCoCo **90.13%（1315/1459）**，原 **90%** 门槛不变。
  CCT 同样完整 `clean verify` **32/32** 通过；Foundation `-pl paytech-http-client-support -am install` 成功，
  HTTP 模块 **105/105** 通过，兼容既有显式 Factory 与新 Spring Group 注册入口，制品只安装本机 Maven。
  Commerce 使用真实 Spring HTTP 代理、LoadBalancer、SimpleDiscovery 与 HTTP Simulator，覆盖两实例轮询、实例移除、
  空列表、连接中断不重试、503 不重试、超时/重定向/协议拒绝、Request ID 传播及原创建/幂等/恢复链。
  测试入口 RestTestClient 复用禁重试传输，避免测试客户端自动重发 503 掩盖首次响应；Client API 的 Reactor 禁入门禁
  检查 API 自身解析后的依赖，不再误读 Web 运行时类路径，Maven 同时禁止 API 引入 Cloud/Nacos/Reactor。
  `git diff --check` 通过，结束后本地 Commerce **15 表全部 0 行**。自动测试禁用 Nacos，不访问共享注册中心。
- **Previous discovery evidence / delivery boundary：**
  [/Users/kenny/.codex/backups/commerce-cct-discovery-20260912T044028Z](/Users/kenny/.codex/backups/commerce-cct-discovery-20260912T044028Z)
  保存修改前源码、失败证据、最终构建日志、Surefire/JaCoCo、源码/制品摘要和数据库清理结果。
  本轮仅 Foundation HTTP 支持、Commerce 发现调用、CCT 注册配置及相应测试/文档变化；无 DDL、无 Risk/Payment/Finance 修改，
  无 Git 提交/推送、Nexus 发布或部署；**真实 Nacos 注册/发现联调、支付准备与最终 OE/定价链尚未验收**。
- **Current source（2026-09-12 公共 HTTP 收敛）：** `CctPrecheckConfiguration` 从 **134 行缩为 32 行**，
  只声明 Owner API、错误转换和严格响应选项。Foundation Factory / Group 入口统一安装 `HttpServiceExchangeInterceptor`，
  每次同步调用传播合法的 MDC Request ID，不缓存或复制其他 MDC 项；与 MVC Filter 共用 `ApiProblemProfile` 的 MDC Key。
  `withStrictJsonResponses(8192)` 使用标准 Jackson typed 绑定和公共有界响应读取，删除 CCT 手写 JSON Reader 与响应包装。
  应用级禁重试策略移入 `HttpClientConfiguration`。删除仅被 CCT 使用的 `AbstractClientTemplate` 及重复调用日志，
  CCT 适配器只保留资格/日期/原因和明确业务错误解释，不再重复捕获所有参数异常；公共网络/协议错误仍由 Foundation 转换。
  未新增 Starter、Boot/Cloud 依赖或业务 API；HTTP 普通 JAR 增加 SLF4J API，Logback 只用于该模块测试。
- **Current full verified（2026-09-12 公共 HTTP 收敛后）：** Commerce Java 25 `./mvnw -q clean verify`
  **194/194** 通过，0 失败/错误/跳过，JaCoCo **90.02%（1254/1393）**，原 **90%** 门槛不变。
  Foundation `-pl paytech-http-client-support,paytech-mvc-web-starter -am install` 成功，HTTP **108/108**、MVC **124/124** 通过，
  仅安装到本机 Maven。真实 HTTP 验证逐请求 MDC A/B/缺失/非法值、不复制其他 MDC、Factory/Group 两种入口、
  标准 Jackson 闭合/类型约束、UTF-8 正文字节上限（成功及错误）、CCT 负载均衡/无重试/超时/错误映射及原业务恢复。
  新增预检过程中到期的真实 Checkout 回归，确认 CCT 返回可用也不能提交过期订单，重放不再次查询；公开入口到 CCT 的 Request ID 一致。
  完成后本地 Commerce **15 表全部 0 行**；`git diff --check` 通过。
- **Current evidence / boundary：**
  [/Users/kenny/.codex/backups/commerce-http-simplification-20260912T050237Z](/Users/kenny/.codex/backups/commerce-http-simplification-20260912T050237Z)
  保存修改前源码、缺失 MDC 的失败证据、最终日志、Surefire/JaCoCo、源码摘要和逐表计数。
  本次未修改 CCT/Risk/Payment/Finance、API 或数据库结构；没有连接 Nacos、部署、发布 Nexus、提交或推送。
  真实 Nacos 联调和后续支付准备仍保持未验收状态。
- **Previous migration cleanup / boundaries：** 验证结束后逐表精确计数，**15 表全部为 0 行**，测试自有 Fixture 无残留；原索引定义与备份逐表一致。
  本轮没有写 CCT、Risk、Finance、Payment 或 test 数据库，没有修改其他系统源码、部署、提交或推送。
  支付准备、最终 OE/合同冻结、Tax/Payment Intent、晚到定价、Checkout 页面和公共路由尚未接入；
  CCT 使用 HTTP Simulator，不把本地成功称为 Commerce 与真实 CCT 数据的完整业务联调。
- **Backup / acceptance evidence：** [/Users/kenny/.codex/backups/commerce-precheck-20260912T034031Z](/Users/kenny/.codex/backups/commerce-precheck-20260912T034031Z)
  保存执行前全库 SQL、迁移原文及 SHA-256、逐列/逐表前后校验、完整构建日志、Surefire 报告、JaCoCo XML、制品摘要与清理结果；
  总结为该目录 `verification.json`。备份未写入版本库。恢复必须核对备份之后的新数据，不自动删库。

- **Evidence：** 开发检查、最终 CCT Wire、构建/JavaDoc、测试编译日志、Surefire 报告及源码摘要保存于
  [/Users/kenny/.codex/artifacts/commerce-precheck-development-20260911T200120Z](/Users/kenny/.codex/artifacts/commerce-precheck-development-20260911T200120Z)。此目录是研发验证证据，不是数据库备份。

- **Pre-commit verification（2026-09-12）：** 本次待提交源码重新运行 Commerce `clean verify` **194/194**、CCT 安全注入本地测试配置后的 `clean verify` **32/32**；四系统生成器/Schema `--check` 与模型/场景 **276/276** 通过。Foundation 本次暂存源码与前述 HTTP **108/108**、MVC **124/124** 的验证摘要一致。需求站完整工作区构建成功、测试 **134/136**，两项失败来自其他任务的 Gateway 文档；导出本次暂存内容后独立运行完整构建与测试 **136/136** 通过。各仓库暂存差异空白检查通过。证据保存在 [/Users/kenny/.codex/backups/oe-commerce-commit-20260912T052717Z](/Users/kenny/.codex/backups/oe-commerce-commit-20260912T052717Z)；本轮未执行 DDL、推送、部署或制品发布。

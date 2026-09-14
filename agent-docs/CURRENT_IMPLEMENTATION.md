# Code-MoR 当前实现状态

最后核对：2026-09-13

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
| API Gateway | 动态 Snapshot、数据库 Target/Route 绑定与转发、BFF 管理监听隔离补改已实现，见第 17 节；Client Credential 链源码仍保持 | RCO-16 补改全量 1199/1199；指令 96.649021%、分支 90.084034%；Platform 注册时序修正后 386/386，指令 97.784105%、分支 90.058766%，阶段 5 本地门禁通过 | 2026-09-12 本轮动态路由完整制品已部署 mor-test，原制品保持，RCO-17 注册后 Snapshot 接受 61 条 Operations Route；含用户已接受的既有 Client 代码 | Client 入口未开放；本轮 test 功能验收按用户要求由用户执行，见第 17 节 |
| Platform Support | 当前工作区已将现有实现拆为 `api + access-control + audit-security + web + test`；Web 仍是唯一应用和组合根，全部模块仍使用一个 Platform 数据库和主 DataSource，审计保留同库专用写池；HTTP/Schema/跨系统合同未改变 | 2026-09-12 RCO-16 在获准独立本机库完成 `clean verify` 386/386，指令 97.784105%、分支 90.058766%；原共享库不满足仅初始化前置的问题通过独立验证库处理，保留原有人工数据；详见第 17 节 | 既有 Platform 应用容器保持；2026-09-12 RCO-16 的 182 行注册及发布状态 SQL、Target 表注释已应用 mor-test；RCO-17 另应用 118 条注册及入口状态 SQL，见 [RCO-17](#rco17-test-20260912)；模块拆分和 Problem 映射源码未部署 | 精确制品及环境发布另行核对；CUSTOMER 认证/JWKS、生产 Vault/KMS、Console Resource、MQ 和生产部署；其余资源锁/HMAC 风险见第 8 节 |
| Operations Console BFF | 独立 `web + test` Maven 仓库；当前工作区除既有页面适配外，已使用 Foundation Writer 输出统一 Problem，并在边界重建自身 503/504，保留显式上游迁移 Reader | 2026-09-06 JDK 25 `clean verify` 315/315；严格 Profile 与真实 `HEAD` 聚焦门禁 272/272 | 2026-09-12 RCO-17 BFF 已部署 `mor-test`，19201 回环管理监听健康；配置转发及 422 错误投影真实入口通过，待用户验收；见 [RCO-17](#rco17-test-20260912) | 无数据库和签名密钥；不拥有核心授权或跨系统事务；Legacy Reader 待部署证据后删除 |
| Operations Console Web | React/TypeScript/Vite/Ant Design 已实现；当前工作区的 API 消费者严格读取 Problem、显示安全 detail/requestId，并为已知目标保留显式旧 Reader | 2026-09-06 测试 216/216，TypeScript 与 Vite 生产构建通过 | 2026-09-12 RCO-17 Web 已部署 `mor-test`，HTML/全部静态资源与批准镜像匹配，浏览器限定验收通过；待用户验收；见 [RCO-17](#rco17-test-20260912) | Client/Partner 未来会话方案不由此决定；Legacy Reader 待部署证据后删除 |
| Vault V1 | Go Server、OpenAPI 与 Java Client 的当前工作区已切换统一 Problem/`HEAD`/Request ID 合同；三表 Protect/Reveal 业务边界不变 | 纯 RFC staged tree 的 Java Client `clean verify` 34/34，OpenAPI/YAML/source 静态门禁通过；当前主机没有 Go/gofmt，Go 编译与测试未执行 | 既有 `mor-test` 合成 smoke 部署保持不变；本次 Problem 源码未部署 | 补齐 Go 门禁；Platform 仍使用批准的开发 Mock；生产 KMS、强服务身份、真实消费者、合规验证和生产部署未完成；Legacy Java Reader 待删除 |
| CCT | 38 表/428 字段目标模型及完整人工 Schema；本地空库已初始化，条件启用的 Checkout 预检/解析已实现，Risk 资格端口仍关闭，见 CCT-QUERY-01 | 全量 Maven verify 32/32；本地 MySQL 结构与测试数据清理核对通过；四系统模型证据见 OE-CLIENT-MODEL-01 | 应用未部署 | Risk 新资格接入、最终解析完整链路、其他写入口/API/MQ、存量库迁移和部署未完成；旧 V5/Hash Reader 保留，Scope 不得绕过 Risk 激活 |
| Commerce | 15 表/249 字段目标模型；本期新增 Customer 三操作及 Payment 内部四操作，准备时冻结 OE/合同/税事实并创建 Intent，见 [COM-CUSTOMER-01](#commerce-customer-20260912)；基础创建仍只做预检 | JDK 25 `clean verify` 331/331，0 失败/错误/跳过；真实本地 MySQL/HTTP、按需顺序查询与独立 Handler 恢复验证；JaCoCo 行覆盖率 91.78%（3027/3298）；模型沿用上一轮 17/17 | 应用未部署；获授权本机三表增量 SQL 已执行，本地 Mock 启动入口已验证并关闭 | 本机保留旧列的兼容结构不等于目标新库 Schema；新 Owner 端口为 Mock + TODO，真实跨服务、支付成功事实消费、晚到定价、Payment Card、公共路由及前端联调未完成 |
| Payment | 30 表/403 字段目标模型；统一 pay、工具 Token、独立 Processor；移除 Caller 和 Payment→Commerce 出站依赖，Intent 冻结上游国家，见 [本地切片](#payment-execution-implementation) | 本轮限定四类 `clean verify` 24/24，全部测试源码编译、七模块打包；模型/场景 21/21；新 Schema 业务回归未运行 | 未部署；本机仍为旧 Execution Schema，Token 与 Intent 国家迁移待授权人工执行 | Commerce 上游适配、真实 Owner、完整数据库回归与综合验收待完成 |
| Finance | 84 表/1,109 字段目标模型和完整人工 Schema 已生成；首个余额查询 Java 仍是旧数字身份切片；当前测试消费者统一校验真实 HTTP Problem | 2026-09-06 JDK 25 `clean verify` 25/25，含本地 MySQL 余额路径与 RFC 1123 Date 门禁 | 未声明 | 本次未执行 SQL，旧余额切片不能直接消费新身份 Schema；完整服务与部署未证明；手工缴税异常分支待专项模型闭合 |
| Risk & Compliance | 71 表/九路由配置切片；本次原 V1 增加可空终态、冷却/主体标签参数、手工拒绝文案、Risk-owned 主体绑定与显式历史格式兼容，见第 15 节 | 2026-09-11 JDK 25 `clean verify` 155/155，含真实本地 MySQL/随机端口 HTTP、旧 Hash Golden 和配置冻结门禁；模型/Schema 189/189 | 2026-09-12 RCO-17 已部署本机 `mor-test`，实际 78 表；四表及三表精简、历史回填与兼容比对完成，见 [RCO-17](#rco17-test-20260912) | Risk 配置的真实 Nginx/Gateway/BFF/Risk HTTP 链已验收；PDManer 人工 Review、其他环境/生产仍待完成；运行期评估和真实动作不在本期范围；全局 COMP-01/DB-01/Vault V1 Gate 保持 |
| `mor-local` | 仓库提供 MySQL、Redis、RabbitMQ、Nacos 等本地依赖的手工环境 | 当前本机运行状态应按 [`LOCAL_DEVELOPMENT_ENVIRONMENT.md`](./LOCAL_DEVELOPMENT_ENVIRONMENT.md) 的命令即时核对 | 仅本机开发环境 | 自动测试只能复用该环境，不得自行拉起替代容器或连接真实业务环境 |
| `mor-test` | 独立 Docker 测试拓扑当前为 11 个容器、61 条 ACTIVE Operations Route、35 个 ACTIVE Console Resource；RCO-17 证据见下，浏览器入口为 `http://localhost:28080` | 已记录真实登录/IAM/审计/Refresh/Logout、Gateway 控制面启停/幂等/版本冲突/Snapshot 恢复与 Vault 合成 smoke | 本机独立测试环境；Gateway 控制面与 Risk V1 配置入口已部署，11 个容器健康 | 不是 Maven `auto`、覆盖率或生产证据；生产部署仍未声明 |

组件级命令、精确测试数字和运行配置由各仓库 README/实施记录维护，本页不复制会频繁变化的日志。

- **RCO-14 Implemented / Verified / Deployed（2026-09-11，mor-test）：** 原 V1 的 Risk、Operations BFF/Web 配置增量已实现；[需求与接口合同](./plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)覆盖三条既有接口扩展，新增 API 和 Platform 注册 DML 均为 0。真实 MySQL/HTTP、BFF 转发/授权、前端和模型验证通过；开发库和 test Risk 库分别在用户授权、备份后应用增量 SQL；三个应用已部署，真实入口验收通过。精确证据及范围见第 15 节。

## 3. 当前生成模型快照

| 系统 | 表 | 字段 | 唯一索引 | 访问索引 | 本库关系 | 1:1 | Diagram Groups |
|---|---:|---:|---:|---:|---:|---:|---:|
| Client & Contract | 38 | 428 | 61 | 43 | 64 | 6 | 6 |
| Commerce | 15 | 249 | 24 | 12 | 15 | — | 4 |
| Payment Orchestration | 30 | 403 | 45 | 48 | 67 | 8 | 7 |
| Finance | 84 | 1,109 | 145 | 155 | 160 | 22 | 6 |
| Risk & Compliance | 78 | 944 | 115 | 80 | 120 | 9 | 8 |
| Platform Support | 64 | 829 | 124 | 101 | 109 | 5 | 7 |

这些数字表示当前生成资产内部一致；六套模型仍保留 PDManer 4.9.3 应用内人工检查门禁，也不能证明物理数据库已经创建。Platform 当前手工 Schema 投影包含 25 表，完整逻辑模型包含 64 表；本期新增 Scope 表及两索引已应用到本机独立 `mor_platform_config_validation`；test 也已补齐三表及索引，详见 [Client API 接入管理](#platform-client-api-access-20260913)。

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
| COMP-01 | 本地可靠表使用 `event_key + event_type + sub_event_type + 可选最小 payload_json`，任何 Vault Token 禁止进入 Payload | 跨系统前序文档/模型仍有旧 source/version/schema/hash/64 KiB 契约；Payment 本轮已对齐目标模型与代码，并人工创建本机 Execution 相关表；其他环境未迁移，见 [Execution 研发](#payment-execution-implementation)；Foundation `CompensationPayloadPolicy` 尚未显式拒绝 `vaultToken` / `*_vault_token` 标准字段名，也缺少相应反例测试 | 六套 Manifest、Policy、模型、场景、SQL 和测试一次性迁移并复核统计；Foundation 登记入口拒绝标准 Vault Token 字段并通过反例测试 |
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

<a id="commerce-client-gateway-path-20260912"></a>

## COM-CLIENT-PATH-01：2026-09-12 Commerce Client API 入站路径修正

- **Decision / scope：** 用户将已有十个 Client API 服务前缀调整为 `/client-gateway-api/v1`，见[路径修正决定](decisions/2026-09-12-commerce-client-gateway-api-path.md)。Context Path 保持 `/commerce`，公开 OpenAPI 仍是 `/api/v1`；不改变业务行为和数据库。
- **Implemented：** Commerce `ClientApiPaths`、metadata-only 日志配置、已有契约与 HTTP 测试以及组件 README 已同步。请求过滤器继续引用统一常量；旧路径不保留别名。
- **Verified：** 已先更新契约断言，确认旧生产常量导致预期失败；随后 Java 25 `./mvnw -q clean verify` **301/301** 通过，0 失败、0 错误、0 跳过。现有十个接口的真实 HTTP 场景改用新前缀，继续验证可信身份、请求约束、幂等、no-store 和业务行为；配置测试验证 metadata-only 前缀。`npm --prefix requirements-site test` 构建及 **136/136** 通过，6 个新增决定链接/锚点和本次差异空白检查通过；源码、配置、测试及 README 无旧前缀残留。日志见 `/Users/kenny/.codex/verification/commerce-client-gateway-prefix-20260912T111828Z/`。验证使用本地基础设施和测试外部依赖，不代表 Gateway 或真实 Owner 联调。
- **Delivery：** 用户已授权本地提交；Commerce 提交为 `d953d7afa546f4572e3607a3bb3e1324b5ff81f4`，配套路径决定、合同投影与本条状态一同归档。提交前核对 19 个文件与完整验证时的内容一致，本次归档仅补充交付状态。
- **Deployment / remaining：** 本次未修改 Gateway、Platform、Nginx 或其他服务；没有应用 SQL、推送、发布或部署。后续 Gateway 接入需使用新的 `/commerce/client-gateway-api/v1` 下游地址。

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

<a id="rco17-test-20260912"></a>

## RCO-17 决策流配置（2026-09-12，test 已部署，待用户验收）

- **范围与阶段：** [需求目录](../requirements/20260912/operations-console-决策流配置/technique.md)原阶段 1–5 已确认，包含用户批准的[解耦方案修订](../requirements/20260912/operations-console-决策流配置/simplification.md)及阶段 5 必要修复。用户对阶段 6 [test 发布包](../requirements/20260912/operations-console-决策流配置/release.md)回复“对”后，阶段 7 已部署并完成限定环境验收，等待用户完整功能验收。以下阶段 4–6 的“未部署/待授权”保留为当时历史，不代表当前状态。只交付配置管理；无真实执行、发布激活或运行动作。
- **原四表阶段的模型及本机结构：** 唯一生成源、PDManer、完整 Schema 追加 4 表/44 字段/11 索引时，原 74 个实体逐项保持不变；后续三表精简修订见下文；当前模型总数见第 3 节。获用户单独授权后，已备份本机 Schema 并手工应用增量 DDL 到 `127.0.0.1:13306/mor_risk`，核对 4 张新表及全部列/索引/注释一致。备份及执行范围见 [本机应用记录](../requirements/20260912/operations-console-决策流配置/db/risk-compliance-service/local-application.md)。
- **实现与开发检查：** Risk、BFF、Web 已实现 10 新操作/4 原操作调整，包括流程配置、准确历史/引用、整体删除、动作可选及原因统一反查；无 Gateway 生产代码改动。Risk 完整 reactor **205/205**（真实本机 MySQL/Redis/HTTP），BFF 完整 reactor **1,046/1,046**，Web **894/894** 与生产构建、模型/Schema **29/29**、需求站构建与 **136/136** 通过；设计/注册一致性通过。已检查本机合成 API 场景下的代表性页面交互，不能据此声称真实全链路或完整视觉验收完成。证据及本期修复见 [开发交付报告](../requirements/20260912/operations-console-决策流配置/development-checks.md)。
- **注册、环境与剩余阶段：** Platform 唯一初始化 SQL 已合入 **118 行注册/1 行角色更新**，逐值核对且每行仅一份；本机独立验证库的应用与关闭状态见下方阶段 5 记录，test 尚未应用本期注册。risk-api 仅安装本机 Maven 仓库用于 BFF 开发。阶段 5 本机视觉/交互、关键故障矩阵及真实 Gateway/Snapshot/授权联调已完成，见下方结果；无本需求的 test 数据库变更、共享制品发布、部署或 Git 提交/推送。

- **2026-09-12 表关系复核：** 用户要求查看表关系及精简空间；已只读核对当前源码、32 张相关表的本机/test 行数和模型引用。7 张未接入运行表为裁剪候选，4 张空表仍有读取逻辑；旧协议版本/输入字段/顺序绑定仍被规则及新图引用资格检查使用，不能视为已被替换。详见 [表关系评审及证据](../requirements/20260912/operations-console-决策流配置/table-relationship-review.md)。未修改应用、模型、SQL 或数据；未作出删除决定，不代表阶段 5 已完成。
- **精简修订研发完成：** 用户确认后，工作区已将规则版本改为直接保存输入合同，条件/动作参数保存字段代码，并收敛共享输入目录和规则内容还原；新规则保存不再插入三张旧编排表，旧状态及 Release 物理 ID Hash 保留专用兼容读取。BFF/Web 的规则 `pointVersionId` 接受显式 null，Candidate 自身仍要求真实 ID。唯一生成源、模型及最终 Schema 已同步三表的增 4 列/删 2 列及 2 索引，模型/Schema **30/30** 通过。原段落的 205/1,046/894 是精简前证据，不能当作本修订完整回归通过；修订后的完整回归结果见下项。
- **精简修订本机数据库：** 用户在具体三表 SQL 授权说明后回复“可以”。执行前核对 13306/mor_risk 无写入方、历史及三表全行数为 0；备份后应用扩展、同连接回填/后置检查与显式 COMMIT，再收敛删列。三表 44 个字段结构及全部索引、新四列注释与模型吻合；39 个旧列注释差异与备份一致，本轮保留。备份目录和证据见 [本机应用记录](../requirements/20260912/operations-console-决策流配置/db/risk-compliance-service/simplification/local-application.md)。test 历史 462 行仅为先前只读快照，未迁移；无共享制品发布、部署或提交。
- **精简修订开发回归：** Risk 完整 reactor **208/208**（真实本机 HTTP/MySQL/Redis）；最终清理旧冲突分类及无用导入后，受影响测试 **8/8** 再次通过。BFF 完整 reactor **1,051/1,051**，Web **900/900** 和生产构建，模型/Schema **30/30**、原设计/注册及链接检查通过。新规则保存零旧编排新增、新旧规则混合流程引用、准确合同拒绝、原字段 ID/九字段快照与 Hash、旧 Candidate 查询/静态校验/成功回放、并发及 ACTIVE Release 删除阻断通过。历史测试事实与全部流程配置数据已清理。阶段 4 交付证据见 [修订开发检查](../requirements/20260912/operations-console-决策流配置/simplification-development-checks.md)；阶段 5 及真实 test 历史迁移验证仍未完成。

- **RCO-17 阶段 5 完成（2026-09-12）：** 两次本机联调已交付 [综合验证记录](../requirements/20260912/operations-console-决策流配置/acceptance.md)。首轮 CSS 说明区遮挡已修复；用户对两项补充回复“都同意”后，已补开既有规则历史 GET，并完成 Foundation 显式字段校验失败能力、BFF 当前 Body 路径投影及 MVC 422 输出；其他代理默认行为不变。浏览器发现集合字段无法定位，Web 复用静态校验修复，真实保存失败保留草稿、准确定位、修复保存 #3、历史/已删除规则引用只读、整体删除和返回均通过。Foundation 相关 reactor 347/347；BFF 全量 1,052 项中旧工厂数量断言修正后受影响 16/16 通过，最终各 suite 报告均通过；Web 900/900 和构建通过。六张 Pencil 状态已映射检查，实际布局差异见报告，不声称逐像素一致。真实入口使用既有独立 Platform 验证库和 mor_risk、真实四应用/会话/权限，Nacos 由本机 SimpleDiscoveryClient 代替，Vault/签名仍为已有开发适配器边界。最后已关闭 13 Route/3 Resource/2 RolePermission，同一 Gateway 从 49 路由恢复 36、入口 200→404；原配置 version=5、额外历史 Route DISABLED/version=3、角色 version=8。118 条本机注册保留，临时事实/进程/Redis 命名空间及凭据已清理。停在阶段 5 交付确认点；test 历史迁移、制品发布、部署与 Git 提交均未执行。
- **阶段 6 发布准备交付：** 已构建 Risk/BFF/Web 三个本地候选镜像（完整 ID、源码及制品摘要见 [发布包](../requirements/20260912/operations-console-决策流配置/release.md)），没有替换运行标签。test 只读预检为 33 个规则版本、418 个条件 Context 和 11 个动作 Context，异常 0；实际旧 Risk Reader 的 33 个规则/协议 Hash、10 个 Release 详情及创建回放通过。现有 Platform/Gateway 实际部署编译器离线接收 51→27→27→61 路由，不需替换两个应用。118 行注册及入口切换 SQL 已按 test 角色 12/6 准备并通过内存 CAS 检查；新 Reader 已针对候选 JAR 编译，test 回填后的比对仍属阶段 7 门槛。镜像内容检查通过，11 个 test 原容器/镜像/启动时间未变。当前等待本包的 test 备份、SQL、3 应用部署及限定合成验收授权；未执行 test 写 SQL、共享发布或 Git 提交。
- **阶段 6 最终材料检查：** 需求站首次 135/136 的失败来自 RCO-17 精简前统计断言遗漏；逐项核对唯一模型后同步断言，重新构建及 136/136 通过。设计/注册一致性、252 个本地链接及根/6 组件空白检查通过。精确 SQL/制品摘要见需求目录 assets/stage6/release-manifest.json，test 写入及部署仍未执行。

- **阶段 7 Deployed（2026-09-12）：** 备份后完成 test 两库 SQL 和 Risk→BFF→Web 升级，镜像前缀为 `89bd500057a2` / `bfc92deaee5e` / `e6585089b4e4`；11 容器健康，其他 8 个容器保持原 ID/镜像/启动时间。Risk 4 新表和 3 旧表精简已应用，回填 462 行；删列前实际旧/新 Reader 与原 33 规则/协议/输入身份 Hash、10 Release 详情和创建回放一致。Platform 118 行注册已生效，Gateway 61 Route / 35 Resource，角色版本 15/8；公开 HTML 与全部 22 静态文件吻合。精确备份、制品、辅助历史校验修正与执行顺序见[部署记录](../requirements/20260912/operations-console-决策流配置/deployment.md)。
- **阶段 7 验收与停点：** 1 个合成规则集、1 个流程/2 个成功版本完成保存、同键重放、409 旧版本冲突、422 非法图保留草稿、准确历史/规则引用、只读历史及浏览器返回验收；两个管理根已由 API 整体删除，历史保留，无评估/动作事实，验收会话退出。test 只读角色现有账号均停用或关闭，未重跑该身份登录验收，也未改变账号状态；阶段 5 本地证据保留。部署及限定验收已交付，用户功能验收/阶段 8 提交待确认；未 Git 提交/推送、未 Nexus 发布。待提交范围见部署记录，避免混入其他需求。

- **用户验收示例（2026-09-12）：** 用户另行要求创建一条决策流数据，已通过 test API 保留 `DEMO_CHECKOUT_BRANCH_FLOW` / `224578384930377728` 第 1 版，及配套动作留空的金额检查规则集。5 节点/4 连线、静态校验及回读通过；大额拒绝、其余非美元复核、其他放行。与上方已删除的部署验收夹具分开，供用户测试，未启用真实执行；[数据证据](../requirements/20260912/operations-console-决策流配置/assets/stage7/user-demo.json)。

- **Web 样式反馈部署（2026-09-12）：** 用户明确仅 Web、不运行测试，随后授权部署 test。圆角折线、删除说明、无选择时隐藏右侧面板已随 Web 镜像 `2d40894a389f` 部署；前述 `e6585089b4e4` 保留为原阶段 7 制品证据。生产构建通过，容器 healthy，公开 HTML/决策流 JS/CSS 摘要吻合，其他十个容器原 ID/镜像/启动时间不变。未跑测试或业务验收，未改数据库，未提交；[部署证据](../requirements/20260912/operations-console-决策流配置/assets/stage7/web-ui-deployment.json)。

- **下一期待办 RCO-18：** 用户明确保留“是否命中”，另增“本节点处理结果”，允许 REJECT 经连线继续另一个规则集，安排下一期修改；已登记[需求及分析入口](../requirements/20260912/operations-console-决策流节点处理结果/requirement.md)。当前仅记录，未改业务代码、Schema、示例或 test；结果汇总、未命中/空动作及旧终态兼容待方案确认。

<a id="rco19-variables-20260912"></a>

## RCO-19 统一风控变量管理与条件编排（2026-09-12）

- **用户验收与代码提交（2026-09-13）：** 用户反馈“好像没什么问题，提交代码”，确认本轮 test 验收并授权提交。决策流配置、统一变量管理及旧规则详情修复已分别提交：`paytech-foundation` `d2e5603`；`risk-compliance-service` `6d7e684`；`operations-console-bff` `0727168`；`operations-console-web` `feefc36`；`platform-support-service` `74db641`。公共需求、模型生成源、Pencil 与发布证据随本次根仓库提交归档；其他需求的未提交改动保留，未推送。提交前对实际暂存版本检查：需求站点构建及 136/136、Risk 模型/Schema 32/32 通过；应用回归沿用本节已记录的对应发布证据，没有重复全量运行。已执行 SQL 的历史文件保留原始字节。

- **验收缺陷修复（2026-09-13，后于下方初次发布）：** `DEMO_USD_ALLOW` 详情的实际 HTTP 状态为 200，Web 解码器遗漏 `operandFormat=LEGACY` 导致误报服务不可用。已只修改 Web 的格式识别并保留旧语义/未知格式检查；259 项相关检查及生产构建通过，真实响应可生成两条规则的只读编辑模型。test Web 当前镜像为 `rco19-legacy-detail-20260913`，其他 10 容器不变，规则内容/版本/Hash 不变，无 SQL。见[验证证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/rule-detail-compatibility/verification.json)与[Web 发布证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/rule-detail-compatibility/deployment.json)。该缺陷修复随后获用户验收并提交，见上方记录；此前受限的原生浏览器观察不作为视觉验收证据。

- **test 发布记录（2026-09-13，随后已获用户验收）：** 用户明确“发布”，授权发布准备与既有 `desktop-linux/mor-test` 部署。Risk/BFF/Web 最终镜像标记为 `rco19-variables-20260913-body-limit`，其他 8 容器镜像、身份、启动时间不变。Risk 已应用 2 新表及旧表增量、17 变量/9 成员；Platform 已应用 113 行注册并启用 9 路由/3 资源/2 管理员权限，管理员角色版本 17。Gateway 自动加载 70 路由，Snapshot SUCCESS、NONE、无旧配置回退；11 容器均健康。入口 `http://localhost:28080` 的变量菜单、列表、中文搜索、成员读取可用，6 个 Web 公开资源 Hash 与最终构建一致。见[发布材料](../requirements/20260912/operations-console-统一风控变量管理与条件编排/deployment.md)及[最终检查](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/final-checks.json)。
- **发布容量修正：** 原成员路由 256 KiB 超过现有 Platform/Gateway 的 64 KiB 绝对上限，导致 Snapshot 拒绝，旧路由保持服务；已将本需求 Route、Web/Risk/BFF 及管理上下文统一到 65,536 字节，并同步初始化/增量 DML 和合同。集合仍最多 1,000 项，同时受总字节限制。新增失败证据后，Risk 65,536/65,537 字节边界 1/1、BFF 变量边界 7/7、JAR/Web 构建通过；test 超限请求返回 413 且原成员未改。未重跑全量回归，未扩大 Gateway 安全上限或替换其制品。
- **test 示例已迁移：** `DEMO_FLOW_AMOUNT_CHECK` 第 2 版为 `224761038741016576`，Hash 为 `dc520420beff45ebfda24dea7ed3cf776279b988a14d30ccaf8ebb91de67a8e1`；`DEMO_CHECKOUT_BRANCH_FLOW` 第 2 版为 `224761039558905856`，准确引用该规则版本。原第 1 版保持，5 节点/4 连线及初始分支含义不变；流程条件使用 schemaVersion 2 统一变量操作数。共享阈值/标准币种临时改为 10000/EUR 后，无需保存规则/流程即可读到新值且 Hash/版本/图不变；已按本次 CAS 恢复 8000/USD，两个常量管理版本为 3。见[迁移回执](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage7/demo-migration.json)。验证会话已退出。真实执行未启用，等待用户页面验收；未 Git 提交、推送或向 Nexus 发布。

### 此前各阶段记录

下列“未部署/待确认/未应用”是各阶段结束时的历史状态，当前环境与确认点以上方发布记录为准。

- **需求范围已确认：** 用户确认需求范围并同意进入阶段 2，材料为[范围与验收稿](../requirements/20260912/operations-console-统一风控变量管理与条件编排/requirement.md)。拟统一字段/常量/集合/节点输出目录，规则和流程条件的两侧均可选择变量或常量；RCO-18 作为子项纳入，保留 matched，真实执行延期。用户已确认共享常量/集合修改后自动采用最新成功保存值，无需逐条重新保存引用配置；进一步明确前后端业务变量定义均由变量库管理，已支持来源范围内新增变量不应要求应用改代码、发布或重启，test 示例统一引用迁移纳入本期。已更新范围与 13 项功能/6 项视觉验收，直接常量和历史定义不改写；阶段 2 分析已获确认；阶段 3 完整方案已确认，阶段 4 研发成果已确认，阶段 5 综合验证完成待确认。
- **本轮实际变更（阶段 4 已交付，2026-09-13）：** 变量 PDManer 生成源、模型、完整 Schema/初始化目录及需求增量材料已同步，当前模型 80 表 / 990 字段。Risk 已实现 9 个变量操作、类型化持久化、资源锁/CAS、集合整批替换、当前/历史引用扫描，以及规则/流程共享操作数校验、当前值投影、历史 Reader/回放隔离和 3 个执行就绪门禁。BFF 已实现 9 个转发操作，保留独立权限校验，并校验 Owner 响应身份、类型、容量和字段错误。Web 已接入变量列表/定义/成员/引用页面及规则/流程共享控件，候选来自变量接口，支持显式去重、草稿保护、当前值刷新和只读投影。Platform 的 113 条注册及角色版本更新已归并到初始化 SQL，仍为禁用/REVOKED 准备态，未应用。工作区实现不代表部署完成；本机 Risk SQL 状态见下方记录。
- **开发回归停点（阶段 4 完成）：** Risk 完整 auto 回归 **254/254**（38 类，0 失败/错误/跳过）通过，包含 17 个变量真实 HTTP/MySQL/Redis 用例；其中 8 个确定锁顺序用例覆盖规则/流程保存与变量删除/停用的两种先后顺序。共享值更新不改变规则/流程内容 Hash、原成功回放先于现值资格检查、旧冻结/发布/字典/删除/历史快照与并发均随全量通过。修复了幂等层遗漏新业务请求 Hash Schema 的保存问题。BFF 的 734 项 Risk 回归及后续变量/流程边界 13 项通过（有重叠，不相加）；Web 913 项测试及生产构建、模型/Schema 32 项通过，这三个应用/模型检查沿用本阶段未被后续改动影响的证据。本次数据库用例结束后清理验证通过。详见[研发证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage4/verification.json)及[阶段 4 交付材料](../requirements/20260912/operations-console-统一风控变量管理与条件编排/stage4.md)。该阶段成果已获用户确认；阶段 5 增量验证见下文，开发回归不等同于 test 部署或业务验收。
- **综合验证停点（阶段 5 完成）：** 真实 Web/BFF/Risk 连接本机 MySQL/Redis；Platform 登录/权限使用 HTTP Simulator、导航/入口使用本机适配器，未验证实际 Gateway Snapshot 或启用 Platform 注册。已完成动态变量发现、两处条件引用、共享值更新、历史只读和权限拒绝检查，修复中文搜索 503、集合保存后旧版本读取误报、只读详情值/状态仍可操作三项问题；增量 Risk 18/18、Web 13/13 及最终生产构建通过。独占 Fixture 已清理，17 变量/9 成员保持，5 类执行事实为 0。详见[阶段 5 报告](../requirements/20260912/operations-console-统一风控变量管理与条件编排/stage5.md)及[证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage5/verification.json)。等待用户确认进入阶段 6 发布准备；未部署 test、迁移示例或提交代码。
- **本机 SQL 已授权并应用（2026-09-13）：** 用户对精确本机应用清单回复“可以”。在 `desktop-linux/mor-local-mysql-1/127.0.0.1:13306/mor_risk` 备份 Schema/四个受影响表后执行已审 DDL/DML；备份位置及文件 Hash 见[应用证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage4/local-sql-application.json)。新表 41 列、旧表 5 新列及 17 变量/9 成员核对通过。回归后测试变量 0，原四个受影响表仍为 0 行；[清理证据](../requirements/20260912/operations-console-统一风控变量管理与条件编排/assets/stage4/local-regression.json)。本轮不重复应用增量，未修改 test 或应用 Platform 注册。
- **范围与数量校正：** 需求增量为 2 新表 / 41 字段、4 张旧表新增 5 列并修改 4 列注释、11 索引和 Risk 26 条目录；相比阶段 3 摘要补列了原已批准的 `t_rule_set_version.content_format` 注释更新，不增加数据字段或业务功能。Risk API 工件只安装到本机 Maven 缓存供 BFF 编译，未向制品库发布。既有 test 数据、制品与执行能力未改变，未部署、提交、推送；下一步为确认后进入阶段 5。

- **test 示例核对：** 用户明确要求修复 test 示例，已经 `http://localhost:28080` 的既有管理 API 查询 `DEMO_CHECKOUT_BRANCH_FLOW`（流程 ID `224578384930377728`，第 1 版）、配套 `DEMO_FLOW_AMOUNT_CHECK` 和准确版本引用；两者均可配置，引用未丢失，5 节点/4 连线的静态校验返回 `valid=true`、无问题。金额阈值仍是直接常量 `8000`，节点输出目录仅有 `matched`；当时 test 尚无统一变量能力，不能仅改数据获得最新值引用能力；本期代码仍未部署到 test。核对后用户已明确预期为统一变量引用；两个示例的迁移已纳入 RCO-19 范围，须待本期变量配置能力落地后实施。该次只读核对未保存新业务版本或写 SQL，核对会话已退出。上述有限核对不代替 UI 或运行执行验收。

- **阶段 2 分析交付：** [差异与系统影响](../requirements/20260912/operations-console-统一风控变量管理与条件编排/technique.md)已完成，识别固定目录、单来源操作数、节点输出、动作字段、历史/Hash、引用查询及刷新缺口。建议改 Risk/BFF/Web 三应用与 Platform 注册 DML，Gateway/Foundation 暂无必要代码改动；以变量定义+集合成员两表进入方案设计，非已批准 Schema。仅修改需求/目录/状态材料并做静态检查，未改应用或模型、未新运行 test 写入/应用测试，该轮尚未进入阶段 3；用户随后确认后完成本次方案，当时研发/部署/示例迁移尚未执行，现行进展见本节上方记录。

- **阶段 3 方案交付：** [方案入口](../requirements/20260912/operations-console-统一风控变量管理与条件编排/design.md)包含9新增操作、9原合同改动/3门禁、2新表41字段、3旧表新增5字段并改3列注释、11索引、Risk26行/Platform113行及1角色版本更新的评审稿；不是已应用SQL。Pencil在Operations原文件新增7画板及PNG/PDF，原40画板保留，Checkout设计未改。2026-09-12 用户回复“可以，基本没什么问题”，确认方案并授权进入阶段 4；SQL 应用、部署、迁移与提交仍需各自阶段授权。
- **阶段 4 初始失败证据：** 变量模型测试先确认缺表/缺列，同步后模型/Schema 32 项通过；真实 Risk HTTP 新目录入口用例先得到 404，证明原入口缺失。后续编译、纯合同与 BFF/Web 开发检查见本节最新回归停点；本轮随后获得独立本机 SQL 授权，数据库回归已完成，旧需求 SQL 授权未复用。
- **设计验证：** check-design.mjs核对SQL现行列名、DDL/注册行数、变量依赖、10条件样例及7错误结构、包内链接和7个导出图；Pencil检查7画板无越界/未完成标记。需求目录/全局时序语法与本轮差异检查结果见delivery.md。这些设计检查本身不代表应用测试、数据库执行或真实运行能力验证；本期实际研发检查另见本节开发回归停点。

<a id="commerce-customer-20260912"></a>

## COM-CUSTOMER-01 Commerce Customer Checkout（2026-09-12）

- **授权与范围：** 用户要求“先完成 Commerce 的接口及功能，外部接口先 Mock + TODO”，并明确允许备份后执行本机 SQL。实现边界见[需求确认记录](../requirements/20260912/commerce-CustomerCheckout接口设计/delivery.md)、[实施说明](../commerce-service/docs/requirements/20260912-customer-checkout/implementation.md)和[OpenAPI](../commerce-service/docs/api/customer-checkout.openapi.json)。本节承接 Commerce 当前状态；上方 COM-PRECHECK-01 的测试数字和“支付准备未实现”属于该较早切片的证据。
- **Implemented：** 新增轻量 `paytech-commerce-customer-api`；Customer 加载/轮询、资料保存、支付准备三操作，以及 Payment 内部访问校验、法律确认、原确认查询、COM-I10 readiness 四操作均有 Controller、Processor、业务 Service 与持久化实现。所有 Processor 继承 `AbstractGatewayProcessor`；准备补偿由独立 `CheckoutPreparationHandler` 编排。Customer 不接收版本字段，三个法律文档返回链接；Payment 的公共单请求卡支付入口归 Payment，不在 Commerce 中创建。
- **事务与恢复：** 所有 Owner 查询、Protect/Reveal、合同生成与 Payment 操作均在数据库事务外。原准备身份固定 OE 选择，选择先落库再调用 Risk/Tax；完整 Context/法律/税事实提交后记录 Intent 首发标记，再以原键创建。响应丢失、本地绑定回滚均恢复原创建；取消/到期在首发后必须取得 Payment 对原创建身份的终止证明。确认在一个本地事务内保存三份不可变接受、同 Order/Session 的证据及稳定回执；Commerce 返回后不回调 Payment。查询不更新支付事实。
- **外部边界：** Vault、CCT 最终选择、Risk、Finance Tax、Platform 文件和 Payment 的新能力以 Commerce-owned 业务端口表达，生产默认明确报依赖不可用并保留 TODO。状态型 Mock 只在 test 模块，未将合成成功逻辑加入主应用制品。既有创建预检继续走真实 HTTP Client 到测试 CCT Simulator；其余新端口测试不声称远程 Wire 已接通。
- **Verified：** 2026-09-12 20:37:13 +04:00，JDK 25 下完整 `./mvnw clean verify` **323/323**，0 failures/errors/skipped，JaCoCo 行覆盖率 **92.04%（3043/3306）**，原 90% 门槛不变。新增 Customer HTTP 12 项、独立 Handler 9 项、默认端口配置 1 项，覆盖真实本机 MySQL、并发/语义幂等、资料锁定、三份接受及原回执、过期/取消、Tax 失败后保持 OE、Intent 响应丢失与真实事务回滚。Owner Mock 对每次调用检查不存在数据库事务。Commerce 模型 **17/17**、生成器 `--check`、7 操作 OpenAPI JSON/本地引用、差异空白检查通过；未将其他系统或完整需求站测试计入本批验证。
- **本机数据库：** 仅向 `mor-local` 的 `127.0.0.1:13306/mor_commerce` 手工执行[三表增量 SQL](../commerce-service/docs/requirements/20260912-customer-checkout/ddl.sql)，增加 8 列、放宽 5 列可空约束；已用 `information_schema` 核对全部 13 项。DDL 不更新历史数据，保留旧 Scope/Assignment 列，应用负责 TERM_PRICE 新写完整性；兼容旧库不等同完整目标 Schema。PDManer、生成源及完整新库 SQL 同步 `consumer_reference` 可空，目标统计保持 15 表/249 字段。未操作 `mor-test`。
- **备份与证据：** 原三表结构和执行 SQL 保存在本机受限目录 [/Users/kenny/.codex/backups/commerce-customer-20260912](/Users/kenny/.codex/backups/commerce-customer-20260912)，SQL SHA-256 为 `f47bc4f38e81622a3facc01eeeca7582509cc986494f2799b6875ad5546b4f5d`。完整构建、JaCoCo、模型及启动日志保存在 [/Users/kenny/.codex/verification/commerce-customer-20260912](/Users/kenny/.codex/verification/commerce-customer-20260912)，不写入仓库。
- **本地联调与待接入：** `tools/run-customer-checkout-mock.sh` 已实际启动；健康检查返回 UP，Customer 未知 Token 返回标准 Problem 404，验证进程已关闭。该入口使用本机库与内存合成 Owner，文件链接并非真实文件，远端 Mock 状态不会跨进程保留。真实 Owner HTTP/MQ、PaymentSucceeded 消费、COM-I13 restricted risk wire、Payment Card/NI、Gateway 和前端联调仍待后续切片，不能据本批测试宣称真实支付闭环。未修改 Payment/Risk/Gateway/前端应用代码，没有 Git 提交、推送、制品发布或部署。

- **提交前复核（2026-09-12）：** 用户授权将 Commerce 全部代码与其格式化一同提交。格式化后 JDK 25 完整 `clean verify` **323/323**，0 失败/错误/跳过；JaCoCo 行覆盖率 **91.90%（2971/3233）**，行数变化来自格式调整，原 90% 门槛保持。需求站完整构建与 **136/136**、Commerce 模型 **17/17** 通过。按任务边界提交 Commerce、相关需求/规范及模型脚本，Risk 决策流的独立改动留在工作区；本轮未推送或部署。

- **GET 分层重构（2026-09-12）：** （前序记录：读事务与全量加载策略已由后续按需查询重构取代。） `CustomerCheckoutApi#getCheckout` 的查询输入独立为 `GetCheckoutQuery`，`CheckoutQueryContext` 复用本库对象与外部查询结果，HTTP 嵌套对象统一为 `Info`，消费者响应不复用保存请求体。`CustomerCheckoutService.getCheckout` 自身使用 REQUIRED 读事务；独立调用创建只读 REPEATABLE_READ 事务，已有事务则继承其设置。Processor 在两次本地事务之间补齐外部展示数据，并保留两轮版本重读与无副作用语义。原四表订单查询拆为三表查询加单表创建事实查询，共享 Orders Repository 同步装配原有返回对象；不改数据库模型。JDK 25 完整 `clean verify` **328/328**，0 失败/错误/跳过，JaCoCo 行覆盖率 **91.95%（2980/3241）**；新增 5 项独立 GET/Service 验证覆盖 SQL 表数、Info 合同、并发快照、加入已有事务与重试上限。7 操作 OpenAPI 解引用后与原合同形状一致，需求站完整构建与 **136/136** 通过，用户原 Converter import 排序已保留。本轮不执行 DDL、提交、推送或部署；[本机验证证据](/Users/kenny/.codex/verification/commerce-get-checkout-refactor-20260912)。

- **Customer 按需查询重构（2026-09-12）：** 按用户要求移除 Customer 普通读取的显式事务与 Repository 全量 `load`。GET 在 Processor 中顺序读取必要事实，末尾仅重验 Token/Order/Session/Consumer/Payment 版本，保持最多两轮重读；冻结后不再读取创建快照。保存、支付访问、确认回查及 Readiness 使用各自的状态/绑定查询；准备响应不读商品、消费者详情或 Payment 展示概要，Handler 按阶段恢复且写入仍保留短事务与锁内重证。真实 SQL 观察断言已准备 GET 不超过 **8 条 SELECT**、商品与冻结详情各一次（前序全量重读为 14 条），所有单条查询最多三表，普通查询不启事务，无关查询不发生。JDK 25 完整 `clean verify` **331/331**，0 失败/错误/跳过，JaCoCo 行覆盖率 **91.81%（3026/3296）**，90% 门槛保持；Customer HTTP、独立准备 Handler 及既有 Client Orders/Refund 回归通过。变更限 Commerce 查询链和实现记录，不改数据库模型或公开字段，不执行 DDL、提交、推送或部署；[本机验证证据](/Users/kenny/.codex/verification/commerce-demand-query-refactor-20260912)。


- **按需查询提交复核（2026-09-12）：** 用户授权提交，Commerce 的 46 个相关文件（含格式调整）已提交为 `dedebb2`。提交前 JDK 25 完整 `clean verify` **331/331**，0 失败/错误/跳过，JaCoCo 行覆盖率 **91.78%（3027/3298）**；最新 import 整理后 `test-compile` 通过，需求站完整构建与 **136/136** 通过。根状态文档只提交 Commerce 片段，Risk 等独立任务改动保留在工作区；未推送、发布或部署。新增日志位于同一本机验证目录的 `precommit-verify.log`、`precommit-compile.log` 和 `precommit-requirements-site.log`。


<a id="payment-application-foundation"></a>

## Payment 应用基础工程（2026-09-12）

- **Implemented：** `payment-orchestration-service` 新增标准 `paytech-payment-api / web / test` 三模块、Maven Wrapper 3.9.16 与统一 Parent/BOM、Java 25 启动入口。Web 复用公共 MVC Request ID/Problem/元信息日志，只开放 Health/Info/Prometheus，默认监听 `127.0.0.1:18103/payment`。详见[运行说明](../payment-orchestration-service/README.md)与[本次需求](../requirements/20260912/payment-应用初始化/requirement.md)。
- **Verified：** 完整 `clean verify` 14/14，0 失败/错误/跳过；真实 main/HTTP 验证健康探针、管理端点白名单、统一错误与日志边界。可执行 JAR 默认端口 8 路径 smoke 通过，测试进程已停止；制品无测试及数据库/中间件依赖，Test 不打包。需求站 136/136；JaCoCo 只覆盖启动类 3/3 行，不能外推到尚未实现的业务。详细证据见[交付记录](../requirements/20260912/payment-应用初始化/delivery.md)。
- **Deployed：** 无。仅本机短时验证，未提交、推送或发布制品。
- **Pending：** Payment 业务、Card、渠道适配、外部 Owner 集成与持久化随后续用例实施。本次没有修改 Commerce、Payment PDManer/Schema，也没有执行 SQL 或添加 Gateway 配置。

<a id="payment-channel-terminology"></a>

## 支付渠道命名统一（2026-09-13）

- **Implemented：** 按[Channel 决定](./decisions/2026-09-13-payment-channel-terminology.md)同步现行支付渠道术语、Payment/Finance/Risk 生成源、PDManer、完整人工 Schema、模型/场景测试及 Owner 文档；CCT 模型只改支付相关注释，Commerce 模型不变。Platform、Gateway、Operations BFF/Web 的 Route Channel、Security Profile、Authorization Mode 使用 `CHANNEL_CALLBACK`。Payment 四业务模块 Owner 映射与 Card 接口/字段草稿同步命名，没有创建空业务 JAR 或实现 Card 交易。
- **Verified：** 对改名前六份工作区模型逐项比较，内部 ID、图形连线和所有非命名结构保持；批准的 23 张表名、79 处列名、51 个索引名逐项匹配且无碰撞，新物理名称均不超过 32 字符。模型字节与独立结构摘要按比较后的结果更新，外部业务 ID、金额、状态机和幂等身份不变。
- **Verified：** JDK 25 Gateway `verify` 1,199/1,199、Operations BFF `verify` 1,052/1,052；Platform 编译器与枚举归属测试 22/22；Operations Web 900/900 与生产构建；需求站 136/136 与构建通过。Java 交叉仓库验证使用临时的第一方 Maven 缓存，未覆盖开发者共享制品。
- **Known verification gaps：** 全量 PDManer/场景测试 533 项中 518 通过、15 失败；在隔离副本恢复改名前生成源、模型与文档后，同样复现这 15 项，没有新增失败。它们属于 Finance 旧 `operating_entity_id` 断言、Risk 旧 72/74 表清单、旧 Operand 字段及初始化 DML 清单。Platform `GatewaySnapshotHttpAutoTest` 因未注入 `PLATFORM_SUPPORT_TEST_DB_PASSWORD` 而无法取得本地数据库保护，未完成该 HTTP/数据库验证；未读取或改写 `.env.local`，未修改数据库结构或环境配置。
- **Not deployed / Pending：** 本次没有执行任何数据库迁移、配置真实渠道、部署、发布、提交或推送。运行环境仍需分别核对旧表/列/索引、Finance 目录及既有 Journal/Hash、Risk 争议来源、Gateway Snapshot/回调枚举的切换；历史原报文与外部固定字段保留。Payment 业务执行与 Card 仍按各专项推进，不能从命名完成推断支付能力上线。

命令、基线对照与保留项说明见[交付记录](../requirements/20260913/payment-支付渠道命名统一/delivery.md)。


<a id="payment-business-modules"></a>

## Payment 四业务模块工程拆分（2026-09-13）

- **Implemented：** 在原 api/web/test 基础上加入 `paytech-payment-execution / routing / channel / card` 四个普通 JAR，形成七子模块 reactor；父 POM 管理内部版本，Web 作为唯一应用组合根直接依赖 API 和四业务模块，Test 只直接依赖 Web。各业务 JAR 有根包说明与 Maven Enforcer 依赖隔离规则。范围依据见[本次决定](./decisions/2026-09-13-payment-module-scaffolding.md)；[模块方案](../requirements/20260913/payment-业务模块划分/technique.md)维护业务职责与表 Owner。
- **Verified：** JDK 25、Maven Wrapper `clean verify` 19/19，0 失败/错误/跳过：原 14 项应用真实 HTTP 回归及 5 项模块边界测试。拆分前的新门禁因仅有三模块而失败；拆分后通过。隔离副本加入 `Execution → Channel` 依赖后，Enforcer 明确报告该 JAR 被禁止，未改主工作区或共享制品。
- **Verified：** Web `-exec.jar` 含 API 与四个普通业务 JAR，不含测试制品；业务 JAR 不含独立启动入口。实际打包应用以随机 Loopback 端口完成 Health/Liveness/Readiness、Info、Prometheus 和未实现 `/payments` 的六路径检查，Request ID 保持，测试进程已停止。30 张当前模型表与架构测试的唯一 Owner 清单完全对应，Payment 数据库工件与本轮开始时逐字节一致。
- **拆分阶段的边界：** 当时四业务模块为工程骨架，没有可搬迁的既有业务类；后继 Execution 研发另见下一节。路由、资金 Action、跨模块事务、回调重放、Card 加密/权限与 NI 集成仍按各业务专项实现并验证；模块目录与同进程 JAR 不是运行时卡数据隔离证明。
- **Not deployed：** 本轮未执行 SQL、配置基础设施、部署、发布、提交或推送。业务数据库和跨系统合同未改变。

可复现构建命令与原始证据见[服务 README](../payment-orchestration-service/README.md)和[交付记录](../requirements/20260913/payment-业务模块划分/delivery.md)。

<a id="payment-execution-implementation"></a>

## Payment Execution 本地切片研发（2026-09-13，Token 修订中，待完整回归）

- **当前修订授权与范围：** 用户确认[交易级工具 Token 合同](../requirements/20260913/payment-Execution设计/instrument-token-amendment.md)并进入 Payment 研发。已新增 PI-I00/PI-I01，EX-I05 改为统一 `pay`，各接口独立 Processor；方式共同命令只携带内部 Token ID，Card Flow 通过受控 Owner 完成首次认证或资金动作。其余 Owner 仍为测试 Mock + TODO；本次未适配 Commerce 实际代码、未实现真实 Card 材料存储或 Wallet/Crypto。
- **数据与恢复实现：** Attempt 直接关联 `t_payment_instrument_token`，移除旧 Instrument DAL 与 CardReference。原来源作用域、固定期限、唯一绑定、撤销、T3 发送门禁和现有补偿任务清理已编码；Owner 材料准备、领取、清理及发送在事务外。SUBMITTED/UNKNOWN 只查询原动作，工具到期不会阻断原 Capture/Void/Refund。源码与场景导航见[修订研发检查](../requirements/20260913/payment-Execution设计/instrument-token-development-checks.md)。
- **本次已运行验证：** JDK 25 限制五个测试类的 `verify` **36/36**，涵盖应用启动、实际本地 HTTP、合同、Flow Registry、资金纯规则及架构，所有测试源码可编译，七模块完成打包；模型/场景 **21/21**。修复了资源锁不支持两层属性表达式导致的启动失败，改为显式 Intent ID 并在 Processor 内重验。新空库工件的 13 条 CREATE 逐字对应正式 Schema，迁移 Token CREATE 同样一致。[验证证据](../requirements/20260913/payment-Execution设计/assets/instrument-token-development-verification.json)，日志 `/tmp/payment-token-review-verify.log`、`/tmp/payment-token-model-final2.log`。这不是完整 `clean verify`、新覆盖率或数据库业务通过的证据。
- **实际数据库状态：** 2026-09-13 13:28 UTC 只读核对本机 `mor_payment` 仍为旧 **13 表/192 列/58 个索引（含主键）**，逐表真实 COUNT 均为 0，新 Token 表不存在。[只读证据](../requirements/20260913/payment-Execution设计/assets/instrument-token-local-preflight.json)。[空旧表迁移](../requirements/20260913/payment-Execution设计/db/instrument-token-empty-migration.sql)已准备，ALTER/DROP 与临时本地恢复检查的独立授权尚待回复；未执行该迁移、未做备份恢复演练。生成的正式模型已改为本节上表所示目标，不能据此推断数据库已变更。
- **尚待完成：** 21 项 Token 业务测试及已适配的并发、幂等、崩溃恢复等完整数据库回归尚未执行。获 SQL 授权后需重新确认空表、备份并验证恢复、人工迁移、核对物理结构，再运行完整回归和覆盖率检查；若已出现数据则停止空表方案。综合验收、真实 Owner/渠道、Broker 与调度仍未完成，未推送或部署。

- **本地版本检查点（用户要求“先帮我提交一版本吧”）：** 保存当前 Payment 实现及此前全系统 Channel 命名工件；其他任务的未提交改动排除。当前源码重新运行限制范围 `verify` **36/36**，Commerce 公开合同 **13/13**，需求站构建及 **136/136**、修改文件 ESLint 通过。同步修正需求站中旧 Payment 模型统计断言。全量模型/场景 **518/533**，15 项失败名称与已保留的改名前基线完全一致，无新增失败；不把全量模型检查记为通过。[提交前验证证据](../requirements/20260913/payment-Execution设计/assets/checkpoint-verification.json)。本版本保留 Processor 间调用和通用 Execution 内的 Card 动作选择，刚讨论的职责调整尚未实施；Token SQL 和完整数据库回归继续待办。本次授权仅为本地提交，不包含推送、部署或 SQL 应用。

- **应用仓库本地提交已完成：** payment-orchestration-service `d35bd4bb`；client-contract-service `a25a11a7`；finance-service `c5476030`；risk-compliance-service `c989f0c7`；platform-support-service `e69cff4a`；commerce-service `e69cacba`。其中 Payment 保存当前 Execution/Token 版本，其他五库仅保存此前批准的 Channel 命名关联改动；没有推送、创建分支、部署或执行 SQL。具体父提交与树摘要见[提交证据](../requirements/20260913/payment-Execution设计/assets/checkpoint-commits.json)。

- **PI-I01 局部重构（2026-09-14 已本地提交 `dbc882e3`）：** 按用户指定整理 `PaymentInstrumentController#prepareCardInstrument` 及对应单个 Processor。Controller 依次转换请求、调用用例、转换响应；Processor 明确区分首次创建和原绑定重放，重放直接读原 Token，首次仍在 T1 内重查命令并原子创建 Token、命令和清理任务。Card 身份核对与材料准备位于事务外；接口字段、幂等 Hash 字段及顺序、Intent 锁键保持一致。生产修改前入口基线 **19/19**；修改后 JDK 25 限定五类 `verify` **38/38**，全部测试源码编译、七模块打包通过，格式与方法文档检查通过，需求站构建及 **136/136** 通过。新增只读请求不得创建绑定的数据库场景，Token 专项现为 21 项；本轮只读确认 Token 表仍不存在，专项和完整数据库回归未运行，不能据此宣称创建/重放数据库链路通过。2026-09-14 提交前按当前源码重新运行限定 `verify` **38/38** 与需求站 **136/136**，将重构、相关测试及 Payment import 整理保存为上述提交；Caller 接口保持原样。未执行 SQL、推送或部署；[本次证据及提交前复验](../requirements/20260913/payment-Execution设计/assets/prepare-card-instrument-refactor-verification.json)。

- **可信内部调用、移除 Caller（2026-09-14，已本地提交 `fbe247f4`）：** 根据用户明确要求与[内部信任修订](../requirements/20260913/payment-Execution设计/trusted-internal-amendment.md)，删除 Caller 接口、默认授权 Bean、14 个 Processor 的授权调用和测试 Owner 授权实现。8 类命令统一由现有 `CommandIdentity.forClient` 生成 `client:<clientId>` 命名空间，原 Hash 投影、操作类型与锁表达式保持；Client/Intent/来源/工具业务归属和状态门禁、其他 Owner 校验继续执行。变更前 7 个 HTTP 反例均被旧 Caller 阻断为 503；变更后限定五类 `clean verify` **48/48**、全部测试源码编译及七模块打包通过，清理构建产物不再含 Caller 类，需求站构建及 **136/136** 通过。真实 HTTP/MySQL/Redis 验证了来源关闭的重放、参数冲突、跨 Client 同键隔离、来源归属冲突与命令回滚，测试后 Binding/Intent/命令记录均为 0。新 Token 表仍不存在，21 项 Token 专项及完整支付回归未运行；Caller 移除阶段没有 Schema 修改或历史数据迁移；后续国家字段修订与本地提交见下项，未推送或部署。旧命名空间的非空环境需另行兼容评审；[验证证据](../requirements/20260913/payment-Execution设计/assets/caller-removal-verification.json)。

- **Payment 禁止调用 Commerce（2026-09-14，已本地提交 `fbe247f4`）：** 按[调用方向修订](../requirements/20260913/payment-Execution设计/payment-commerce-direction-amendment.md)移除 `ExecutionPorts.Commerce`、默认 Bean、测试 Owner 实现及创建/支付/定价三个调用点。创建增加必填 `consumerCountryCode`，持久化到 Intent 的 `consumer_country_code`；首次和恢复定价使用本地冻结值，渠道不能覆盖。创建 Hash 追加该字段并使用 `CREATE_INTENT_V2`，其他命令不变。两项修改前反例准确失败；本轮限定四类 `clean verify` **24/24**、全部测试源码编译及七模块打包、模型/场景 **21/21**、需求站构建及 **136/136** 通过，124 个修改文档链接有效。本机只读确认 Binding/Intent/命令仍为 0、Token 表和 Intent 国家列均不存在；无 Commerce 装配的应用启动成功，但创建用例在只读 Schema 前置明确失败（缺少 consumer_country_code），未执行业务请求。新 SQL 尚未执行，真实数据库创建/支付/恢复不能据此声明通过；国家必填、改值冲突及渠道覆盖反例已编码。历史 Caller 48/48 对应此前 Schema/源码，不作为本轮数据库通过证据。当前模型与完整/空库 Schema 已同步；非空旧环境需另行兼容设计，未执行 SQL、推送或部署。用户随后要求“提交”，按相同源码完成提交前专项 `verify` **24/24**、模型/场景 **21/21** 与需求站构建及 **136/136** 复验，将 Caller 和 Commerce 出站依赖移除、模型/SQL/测试保存为上述本地提交；数据库完整回归仍待迁移。[本轮验证证据](../requirements/20260913/payment-Execution设计/assets/payment-no-commerce-verification.json)。

### 旧 SubmitCard 切片及 Processor 重构历史证据

以下记录属于 Token 修订前的源码与 Schema，保留其验证范围；其中 102 项完整回归及覆盖率不能验证当前工具 Token 行为。


- **授权与 SQL：** 用户在研发确认后，再次允许读取 Payment/Redis 的本机连接配置，并在 13 张目标表均不存在时应用空库 SQL。只读确认 `127.0.0.1:13306/mor_payment` 为零表后，人工执行[13 表创建工件](../requirements/20260913/payment-Execution设计/db/execution-create-empty-schema.sql)。实际 **13 表/192 列/58 个索引（含主键）**的列序、类型、默认值、空值、注释及索引定义逐项等于正式模型；未执行旧表 ALTER/DROP 或其他系统 SQL。[执行与前置证据](../requirements/20260913/payment-Execution设计/assets/local-sql-application.json)。此处只表示本机 Execution 相关 13 表已创建，完整 Payment 30 表未应用。
- **实现范围：** Execution 的来源 Binding/单向关闭、Intent、原 submission/Attempt、AUTHENTICATE/资金/QUERY Action、退款预占、原授权 Capture/Void、Reversal 决策绑定、Pricing Fact、首笔成功唯一性和重复扣款补偿。Web 提供 12 个内部 HTTP 接口、短事务命令定位、Redis 资源锁、Card 首次流程及四类可靠 Handler；业务与任务同事务，外部 Owner 调用在事务外。没有消费者交互表或数据库流程编排。
- **方式抽象：** `PaymentMethodFlow<C>` / `PaymentFlowRegistry` 按精确方式与用例匹配；Card 使用专属输入，非 Card 测试替身不携带 3DS 字段。生产 Owner 默认不可用；稳定 Card 引用受理前校验，瞬时材料不进入库、Hash、任务。其余 Owner 为测试 Mock + TODO，未实现 Wallet/Crypto。
- **本轮修复：** 补齐 Redis 显式超时；可信结果核对原支付方式并拒绝非二进制挑战事实；统一 Problem Writer 清空响应后恢复 `no-store`；恢复 Handler 不使用无业务含义的占位子类型分流。test 显式依赖其直接消费的 api/execution，覆盖率不再只统计 Web；未改变生产模块依赖。
- **接口评审重构：** 按用户要求移除两个集中 Processor，12 个接口各自拥有用例入口；七个写用例的短事务受理分别使用独立锁代理。Controller/Converter 位于 `ext/rest/execution`，Mapping 只由 API 声明；Converter 无数据库/外部依赖，Processor 使用应用模型。命令绑定、业务查询与原渠道请求分别采用 Service + Impl；Card 首次流程拆为明确阶段，闭集枚举按稳定 Code 转换。接口路径、请求 Hash 字段序列、锁 Key、金额和状态合同不变。[具体映射与边界](../requirements/20260913/payment-Execution设计/design.md#11-接口与-processor-一一对应)。
- **开发检查：** 独立 Processor 重构后，JDK 25 七模块 `clean verify` **102/102**，0 失败/错误/跳过。包括 15 项生产装配/HTTP、模块边界、Card 与后续资金操作、权限/严格输入、四类 Handler 独立入口、真实 Scanner/Dispatcher 的并发 Claim/过期 Lease/次数耗尽、实际 Redis 锁过期、事务回滚，以及两种真实 JVM 退出后重启恢复。外部调用 Probe 检查无活动数据库事务；发送前退出保持 UNKNOWN，发送后退出查询补记成功，两者资金发送均仅一次。[本轮逐类结果、源码摘要与覆盖率](../requirements/20260913/payment-Execution设计/assets/processor-refactor-verification.json)，原日志 `/tmp/payment-processor-final-verify.log`；[此前 98 项基线](../requirements/20260913/payment-Execution设计/assets/development-verification.json)保留为历史证据。新增检查覆盖接口/Processor 一一对应、协议与查询分层、稳定 Code 解析及原提交 Hash/只读重放。
- **打包与数据清理：** 可执行 `-exec.jar` 在随机本地端口启动，健康 UP；正式 Owner 未配置的 Execution 返回 503 且带 `no-store`，检查后进程关闭。[本轮制品证据](../requirements/20260913/payment-Execution设计/assets/processor-refactor-packaged-smoke.json)。每个用例清理其自身 Client 数据；本轮早期遗留的 46 个合成 Client 精确清理后，13 张表均为零行，无业务事实遗留。[清理证据](../requirements/20260913/payment-Execution设计/assets/local-fixture-cleanup.json)。
- **覆盖率与后续门禁：** API/Execution/Web 完整聚合指令 **88.955351%（12492/14043）**、分支 **66.463415%（872/1312）**；行覆盖率 **91.644909%（1755/1915）**。指令与分支仍未达到项目 90% 目标；`clean verify` 成功不代表该目标已满足。[21 组验收映射](../requirements/20260913/payment-Execution设计/development-checks.md)区分已运行代表场景与待综合核对项。阶段 5 尚未进入；真实 XXL Admin 调度、Broker Confirm/正式 Wire、真实 Owner/渠道、非空旧库迁移均未验收。同 key 保证幂等的重发优化未启用，当前 SUBMITTED/UNKNOWN 统一只查询原动作。
- **模型与文档基线：** 前一轮交付的 Payment 模型/场景 **21/21**、需求站点 **136/136** 和 105 个本地链接检查保留为历史证据；本次 Processor 重构未改模型、SQL 或需求站点源码。[静态检查摘要](../requirements/20260913/payment-Execution设计/assets/document-checks.json)，日志 `/tmp/payment-delivery-model-final.log`、`/tmp/payment-delivery-site-final.log`。
- **本轮文档检查：** 接口/Processor 映射、交付记录、README 与本节的 72 个本地链接/锚点通过，37 个本轮 Web 源码文件无行尾空白，工作区及 Payment `git diff --check` 通过。[静态检查](../requirements/20260913/payment-Execution设计/assets/processor-refactor-document-checks.json)。最终回归及制品检查后只读确认 13 张本机表仍为零行。
- **状态边界：** 这是已完成本轮研发及开发回归的本地模拟切片，综合验收未完成。没有提交、推送、test/生产部署、真实卡支付或其他 Owner 接入。


<a id="platform-config-management-20260913"></a>

## Platform 菜单权限与 API 配置管理（2026-09-13）

- **范围已确认：** 用户在澄清模型后回复“那就一起覆盖呗”，确认 Target/Route 新增编辑覆盖六类已有 Channel；Operations 为管理入口，菜单/按钮仍只维护 OPERATIONS。两项能力合并，风控新增功能暂缓。见[需求范围](../requirements/20260913/platform-support-菜单权限与API配置管理/requirement.md)，包含 14 项功能、6 项视觉/交互验收。
- **阶段 2 已确认：** [差异与系统影响](../requirements/20260913/platform-support-菜单权限与API配置管理/technique.md)列出 14 项差异、3 个应用预计改动；复用七张配置表，暂未发现新增表/业务字段的必要，基础新增操作估算 15 个（两侧约 30 个端点），精确接口/索引/SQL 数量在阶段 3 核定。Gateway/Foundation 暂无必要生产代码改动的证据；六类配置管理不扩展为尚未接通的业务认证研发。
- **关键缺口：** 现有可分配权限只含已持有且由启用 Operations 资源/路由引用的权限，需要设计新增权限的首次显式分配；组件/路径映射必须符合已部署页面；没有独立 Scope 字典，不新增 Scope 管理。阶段 3 已确认上述规则及接口/SQL/Pencil 和研发方案；阶段 4 结果及下一确认点见本节开发状态。
- **阶段 2 操作：** 已同步 PLT-16/PLT-17、PLT-I19 及 E2E-16 目标时序，更新原动态路由决定的后继范围入口；其他需求改动保留。未改应用、模型或 Pencil，未执行 SQL、部署、提交或推送。阶段确认见[交付记录](../requirements/20260913/platform-support-菜单权限与API配置管理/delivery.md)。
- **阶段 1 文档证据：** 当批三份材料 23 个本地链接/显式锚点有效，14/6 验收编号唯一，需求站点构建及 136/136 通过；阶段 2 材料另行核验，不能将此数字当作本轮新增应用验证。
- **阶段 2 文档验证：** 三份材料的 42 个本地链接/显式锚点有效，14 项差异与 14/6 验收编号核对通过。E2E-16 登记后首次检查发现旧全局用例数量 15 和已被动态路由决定取代的静态 Registry 断言；按现行范围同步为 16 个用例及数据库/完整快照规则后，需求站点生产构建与 136/136 检查通过。没有新运行应用、数据库或 test 环境验证；日志位于本机 `/tmp/platform-config-stage2-final-tests.log`。

- **阶段 3 已确认：** [详细设计](../requirements/20260913/platform-support-菜单权限与API配置管理/design.md)定稿评审范围为 3 个应用、18 项能力/36 个新端点、15/14 个既有 Platform/BFF 端点合同或授权调整；复用七配置表，新增表/字段/索引为 0。准备 227 行 INSERT（7 张表，禁用注册）和零 DDL 声明，未执行。Pencil 在原 47 张画板后追加 7 张并导出 PNG/PDF，未改应用实现、未发布/部署/提交/推送。用户随后回复“可以，下一步”，确认本阶段并授权研发；[交付记录](../requirements/20260913/platform-support-菜单权限与API配置管理/delivery.md)保留边界。

- **阶段 3 文档/设计验证：** 本需求 83 个本地链接存在；18 项新增 API 的 Method/Path/Route Code 清单与 SQL 对应，227 行 INSERT 的列名与当前 Schema 对齐，种子 ID/自然路由代码与当前完整初始化 DML 无冲突。Pencil 54 张画板（原 47 + 本期 7），新画板最终无裁剪告警，PNG/PDF 已导出并通过编辑器保存 design.pen。需求站点生产构建及 136/136 检查通过，日志 `/tmp/platform-config-stage3-tests.log`。以上为静态设计/文档证据，没有执行 SQL 或应用业务测试。

- **阶段 3 菜单树视觉修订：** 按用户追加意见优化原 d6tuF 一张画板：树图标/缩进/按钮分组引导线、停用项文字、选中菜单摘要及分组编辑；画板总量仍为 7。已通过 Pencil 最终裁剪检查并审阅树/编辑区截图，保存设计文件、重导菜单页 PNG 和整套 PDF，更新 ui.md/delivery.md 并核对链接。仅页面方案调整，未进入研发；本次未新增 API、结构或注册 SQL，也未运行应用测试。


- **阶段 7 已部署，等待用户验收：** Platform、Operations BFF、Web 已实现 18 项新增操作（Platform/BFF 各 18 个端点）、菜单树、权限目录/引用/选择器、六类 Channel 的 Target/Route 完整编辑与只读详情。保留 v1 接口和无版本后缀类名，包含显式首次授权、INACTIVE 历史关联、内置管理对象保护、CAS/幂等/审计、严格 JSON/Query 校验与浏览器草稿保护。新增表/字段/索引为 0，增量注册为 227 行；原有 Channel 等其他任务修改继续保留。用户明确确认四应用和 SQL 发布清单后已完成 test 部署，包括 Gateway 必要命名兼容；当前等待用户业务/页面验收；用户同阶段反馈的两处 Web 样式已修复并更新 test，见下方后续记录。
- **Foundation 必要修复：** 用户明确同意纳入后，`paytech-http-client-support` 补充合法 404/NOT_FOUND 分类及回归，108/108 通过并完成本地 Maven install。Platform/BFF 显式映射 NOT_FOUND→404；未知或畸形上游错误仍按既有策略处理。没有对外发布制品。日志 `/tmp/plt-config-foundation-test.log`。
- **真实 Platform 开发回归：** 最终代码相关 HTTP/基础设施用例共 97/97 通过：新增配置 19、既有人事/角色/登录 39、目录重构 8、配置启停/幂等 19、Redis 不可用 1、租约失效竞争 1、快照 8、数据库 Target 2；架构 7、本机库边界 13、审计投影 6 另通过。证据来自 `/tmp/plt-config-platform-regression2.log`、`/tmp/plt-config-platform-final-regression.log`、`/tmp/plt-config-platform-eligibility-regression.log`，同类用例采用最后一次通过结果，不重复累计。覆盖六类 Channel 创建/编辑、权限与树生命周期、原回执重放、CAS、树互挂竞争、权限停用与绑定竞争、同 Method/Path 竞争、子项唯一键失败时根/全部子项/命令回执一起回滚，以及读取可分配权限后引用停用导致并发授权 403 且不写入角色。
- **开发中修复的实际问题：** 新测试库精确加入 localhost:13306 允许范围，仍拒绝远端、test 端口及相似库名；菜单锁前缀统一为 `console-resource:operations`，业务 Channel 仍为 OPERATIONS。新审计注解只引用真实字符串参数，架构用例固定该规则；认证关闭时不加载仅供管理写入使用的 Gateway Repository，快照独立启动恢复正常。曾将全部人员事务提升为 SERIALIZABLE，真实旧回归暴露管理员交接超时和冲突，现已恢复 READ_COMMITTED；按 Permission ID 顺序锁定权限与 Operations 资源/Route/Target 引用行并重查资格，树与 Gateway 候选写入单独保留 SERIALIZABLE。旧人员 39 项及配置引用竞争已通过，无此阻塞遗留。
- **规则与注册静态证据：** 原 48/48 项规则、编译器、目录和架构聚焦检查通过（`/tmp/plt-config-platform-pure-tests3.log`）；管理端点最小传输合同补齐导航查询，43 个合同与独立固定目录逐项核对，作者规则/架构 18/18 通过（`/tmp/plt-config-platform-authoring-final3.log`）。权限 `resource_type + action_code` 唯一冲突按业务 400 处理，主键碰撞保留系统错误，并通过真实 HTTP 用例。Web/Platform 11 个页面能力逐项一致。
- **BFF 与 Web 开发证据：** 真实 BFF 314/314 通过（新增配置边界 46、既有浏览器边界 254、角色树 5、HTTP Body 命名 9），日志 `/tmp/plt-config-bff-regression2.log`。Web 全量 920/920 与后续配置聚焦 5/5 通过，最新 TypeScript/Vite 生产构建通过，仅既有大 Chunk 提示；日志 `/tmp/plt-config-web-tests2.log`、`/tmp/plt-config-web-authoring-final.log`、`/tmp/plt-config-web-build4.log`。浏览器使用本机临时 API 样例确认树布局、编辑草稿、取消返回保留内容、确认返回上一页及只读详情无保存；这是页面开发证据，尚不是真实后端整链路验收。
- **Gateway 复用验证：** 既有动态转发与快照用例 13/13 通过，使用真实 Gateway/Redis 和独立 HTTP 下游 Simulator，覆盖新逻辑 Target、发现名称/路径刷新、请求固定旧快照、无效刷新保留上一配置、停用与无实例处理。没有因本需求修改 Gateway 生产代码，其他任务已有改动保留。日志 `/tmp/plt-config-gateway-regression.log`；不将 Simulator 证据表述为真实 Platform→Gateway 环境验收。
- **本机数据库操作与清理：** 用户单独回复“可以”后，按[准备材料](../requirements/20260913/platform-support-菜单权限与API配置管理/db/platform-support-service/local-validation/README.md)新建 `127.0.0.1:13306/mor_platform_config_validation`，应用现有 24 表及完整 1141 行初始化，并给既有本机应用账号该库数据读写权限。执行前备份两个原 Platform 库的结构及账号授权元数据，原库未修改；证据目录 `/Users/kenny/Code-MoR-backups/20260913-platform-config-validation-132337/`。最终回归后仍为 24 表/1141 行（Target 1、Route 88、Header 310、Query 99、Guard 524、Tenant 1、Role 2、Permission 34、Role-Permission 40、Resource 42），测试账号、会话、命令和审计 Fixture 均已清理。新增入口保持禁用、管理员新增授权保持 REVOKED；应用/测试启动不建表或播种，不删除验证库。
- **文档和下一阶段：** 需求站点构建及 136/136 检查通过（`/tmp/plt-config-requirements-tests.log`），本轮收尾 93 个本需求本地链接存在，四个改动仓库差异格式及新增源码空白检查通过。实现计划仍为[已确认计划](../requirements/20260913/platform-support-菜单权限与API配置管理/implementation.md)，[交付记录](../requirements/20260913/platform-support-菜单权限与API配置管理/delivery.md)保存阶段 4 审阅入口。该条保留阶段 4 开发证据；阶段 5 最终证据及下一确认点见下。没有执行 test 部署、外部制品发布、Git 提交或推送。

- **阶段 5 最终完整验证：** Platform `clean verify` 433/433、BFF `verify` 1105/1105，均无失败、错误或跳过。Platform 指令 97.597%、分支 90.086%，保留原 90% 门禁。同步旧目录 / 路由 / 日志清单断言并补足配置保护边界用例；没有下调阈值或添加排除。最终日志 `/tmp/plt-config-stage5-platform-verified.log`、`/tmp/plt-config-stage5-bff-final-verify.log`；Web / Foundation / Gateway 未在此后修改本期实现，复用上述有效证据。
- **阶段 5 实际修复：** 真实 Gateway → BFF → Platform 调用 A18 时，PLT-I02 旧允许清单遗漏新权限选择器，错误返回 403。新增真实 HTTP 用例先得到 allowed=false，随后仅补齐该已批准 Route 的普通会话接入；usage 对应 READ 权限、强制改密及 Bootstrap 前置限制保持。该修复已包含于最终 433 项完整验证。
- **阶段 5 真实链路与页面：** 本机实际 Web 构建、Gateway、BFF、Platform、MySQL/Redis 完成 50 项公开 HTTP 检查，A01–A18 全部覆盖；新 Target / Route 经真实快照自动加载转发，停用后 404；真实显式授权、人员新会话导航可见、Permission 停用隐藏和恢复通过。浏览器验证真实资源保存 / 回读、只读详情无保存、草稿回退、权限筛选 / 引用、六 Channel 选项、管理 / 只读 / 无权限账号、1280 / 1024 / 768 布局；未捕获 error / warn。服务发现为本机 SimpleDiscovery，开发签名 / Email Vault 为批准的 auto 适配器，动态 Target 下游为合成 HTTP 回显；不宣称六类真实业务认证已接通。
- **阶段 5 清理与交付：** 自有进程 / 浏览器已关闭，最终验证库恢复 24 表 / 1141 行；合成账号、会话、命令、审计与 Key 均归零，18 新 Route / 4 Resource 恢复 DISABLED，4 条管理员授权保持 REVOKED。验收启动器退出清理先于连接池关闭，首次退出遗留也已按精确合成 ID 清理。材料：[阶段 5 验收映射](../requirements/20260913/platform-support-菜单权限与API配置管理/acceptance.md)、[机器证据及 SHA-256](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage5/verification.json)、[浏览器记录](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage5/browser.md)。阶段 5 结果已获用户确认，后续阶段 6 结果见下；尚未远程发布制品、部署 test 或提交 / 推送。

- **阶段 6 发布准备：** 已只读核对 `desktop-linux / mor-test / 127.0.0.1:23306/mor_platform`：原 70 条 Route 全为 Operations，本期 227 个主键/自然编码/路径均无冲突，8 张受影响表的结构及索引与验收库一致；管理员角色 version=17。准备 227 行禁用注册与 27 行启用 SQL、一次 8 表配置备份和受限恢复方案，未执行 test 数据写入。
- **阶段 6 制品与兼容证据：** 已构建独立标签 `plt16-17-config-20260913` 的 Platform / BFF / Web / Gateway 四个候选镜像，完整 ID 见[发布清单](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage6/release-manifest.json)。已验收的 258 个源码证据与 45 个制品文件 Hash 未变；三个 Java 应用内部 Platform API 的 117 个类完全一致，BFF 内 Foundation HTTP 404 修复与本地已测试类一致。Gateway 候选额外构建并通过 64/64 快照策略及监控定向用例，日志 `/tmp/plt-config-stage6-gateway-package.log`；未重复完整 Java 回归。
- **阶段 6 待确认依赖：** test 实际旧 JAR 使用 PROVIDER_CALLBACK，新 Platform/BFF/Web 使用 CHANNEL_CALLBACK；因此建议同时切换只包含既有命名补丁的 Gateway。test 当前旧/新回调 Target/Route 均为 0，无需回调数据迁移。该第四应用补充待与阶段 7 一并确认，不代表整个 Channel 专项获准部署。具体顺序、精确 SQL、旧镜像恢复和轻量入口验收见[release.md](../requirements/20260913/platform-support-菜单权限与API配置管理/release.md)。当前容器和默认 development 标签未改；没有 Nexus 发布、Git 提交或推送。

- **阶段 6 收尾检查：** 候选/原镜像两份 Compose 覆盖 `config --quiet` 均通过；需求站 136/136、本需求 119 个本地 Markdown 链接、SQL 静态结构、JSON 与四个镜像/制品 Hash 核对通过。11 个 test 容器仍全部 healthy，运行基线未变。收尾证据见[verification.json](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage6/verification.json)；静态 SQL 核对不表示已经执行数据库变更。

- **阶段 7 执行授权与部署：** 用户对四应用、227 行注册/27 行启用及相关表备份的明确确认问题回复“可以”。按已审阅 release.md 在 `desktop-linux/mor-test` 执行：8 表一致性备份至 `/Users/kenny/Code-MoR-backups/20260913-154927-plt16-17-test/`（136434 字节）；确认 INSERT `4/4/4/18/76/13/108` 后提交，依次切换 Gateway / Platform / BFF / Web，四服务 healthy 后确认 UPDATE `18/4/4/1` 再提交。管理员角色 version=18，新 Route/Resource/授权 ACTIVE，无 DDL 或回调数据迁移。完整镜像 ID 与阶段 6 候选一致，其余七个容器 ID/镜像未变，11 个均 healthy。证据见[部署及验收记录](../requirements/20260913/platform-support-菜单权限与API配置管理/test-acceptance.md)。
- **阶段 7 真实入口证据：** 12 项公开 API 检查通过，覆盖新菜单、资源/权限目录、两个能力目录、A18 选择器、Target/Route 列表和原风控变量/规则/决策流；六类 Channel 正确，不存在权限返回 HTTP 404 Problem。Gateway 有效快照 88 Route，刷新 SUCCESS、连续失败 0、未使用旧快照回退。实际公开 HTML 与 39 个静态文件 Hash 全部匹配候选。浏览器真实渲染菜单资源树、权限目录、API 配置及只读详情；权限编辑有保存按钮，浏览器返回回到无保存的只读详情，未保存配置。最终 API/页面会话明确退出；没有新增账号/角色/Target/Route 等业务 Fixture。
- **阶段 7 首次验收观察：** 当时发现权限目录较长编码挤入相邻资源类型列并保存截图；用户随后提出明确样式修复要求，后续修复和 test 更新见下。最初只读首页的临时浏览器会话已关闭并按既有 TTL 结束，后续验收均明确退出；没有归档凭据/Token。候选镜像仍按完整 ID 固定，无 Nexus 发布、Git 提交或推送。后续本地提交范围已整理为[commit-review.md](../requirements/20260913/platform-support-菜单权限与API配置管理/commit-review.md)，须另获用户明确确认。

- **阶段 7 Web 样式反馈已修复并更新 test：** 用户要求资源树按钮数改为名称右侧的小数字和按钮图标，并让权限列表过长内容省略。仅修改 Web 的 ConsoleResources.tsx、Permissions.tsx、configuration.css：计数保持原直接子 BUTTON 口径，同行 13px 图标/数字；权限表格固定列宽、链接和长字段省略并保留完整 title/详情跳转。`npm run build` 通过（仍只有既有 chunk 大小提示），无后端或 SQL 改动。
- **样式更新运行证据：** test Web 使用 `mor-test/operations-console-web:plt16-17-style-20260913`，镜像 `sha256:7aff290486eecefe527f845c78fd5f83d8de774e60e873099b86dba0d49a6586`。实际浏览器 1440/1024 核对按钮数和名称同行、表格链接不越界、完整提示、详情点击及无页面溢出；40 个公开静态文件与新 dist 一致，无 pageerror，验收会话已退出。其他十个容器 ID/镜像未变，11 个均 healthy。材料见[样式修复清单](../requirements/20260913/platform-support-菜单权限与API配置管理/assets/stage7/web-style-fix/manifest.json)和[检查/截图入口](../requirements/20260913/platform-support-菜单权限与API配置管理/test-acceptance.md#web-样式反馈修复)。首次长编码挤列观察已解决，等待用户继续验收；未提交或推送代码。

- **阶段 8 本地提交归档：** 用户明确要求“代码提交”，据此完成本需求五个应用仓库本地提交：paytech-foundation `6fc398d`；platform-support-service `1148936`；api-gateway `a2111df`；operations-console-bff `a3071a7`；operations-console-web `fba7a24`。包含最终 Web 样式修复及获准的 Foundation 404 / Gateway 回调枚举兼容。源码共 986 项发布 Hash 核对通过（3 项 Web 样式按最后部署清单核对），沿用对应的有效应用回归证据；精确暂存的根文档快照重新构建并通过 136/136 检查，本需求 148 个本地 Markdown 链接存在。共享目录仅暂存本需求条目及对应的三处用例断言，Payment/Finance/Risk 和其他 Channel 模型/Schema/工程修改保留。未推送、未创建 PR、未再次部署或执行 SQL；该提交要求不额外推定用户已完成全部业务验收。


<a id="platform-client-api-access-20260913"></a>

### Client API 接入管理：已部署 test，本地提交归档（2026-09-14）

- **最新授权与停点：** 用户已确认阶段 3 并授权研发；本机 SQL 修订申请经新表用途/取值说明后，用户回复“可以，没问题了”。用户随后回复“继续”，确认进入阶段 5；综合验证及本地验收完成后，用户明确要求“部署test”，据此合并阶段 6/7 发布准备与执行，已完成 test 部署，等待用户业务验收。2026-09-14 用户明确要求“git提交”，授权归档本次本地代码与文档；该要求不额外推定全部业务验收完成，没有远程制品发布或推送。
- **实现范围：** Platform 与 BFF 各新增 9 个端点、各调整 4 个旧端点；Operations 仅查询凭证及授权、独立权限紧急撤销，Scope 查/建/改和 Route 绑定，Client 日常自助生命周期另期。BFF 只聚合当前选中的 Scope 引用页；Web 新增凭证/Scope 页面，复用分页、保存、草稿与错误处理。旧未登记 Scope 绑定读取与同值更新兼容，新的绑定必须存在目录。既有 Route 维护合同同步 requiredScopeCode，Gateway 转发代码未改。源码及 AC 对照见 [development.md](../requirements/20260913/platform-support-ClientAPI接入管理/development.md)。
- **数据工件：** Manifest/Oracle/PDManer/完整 Schema/单一初始化与增量同步；当前完整模型 64 表/829 字段/124 唯一索引/101 普通索引/109 逻辑关系/5 一对一/7 组，手工 Schema 投影 25 表。模型 SHA-256 为 `56991d1ede96b2bed6b916862235047f75cacc4dce36a52e87f169510c9c2d4a`。历史两表建表文件未改；本期模型只新增一张目录表，test 所缺另外两表为旧设计。
- **本机数据库：** 仅在 `desktop-linux/mor-local-mysql-1` 的 `127.0.0.1:13306/mor_platform_config_validation`，备份两旧表 Schema 后，应用新 Scope 表/7 字段/两旧表查询索引，以及 127 条本期注册基线。四权限 ACTIVE、资源/Route DISABLED、角色关联 REVOKED，无 Client/凭证/Scope 业务种子；本机基线不执行部署 DML 的两旧 Route version 更新。精确文件 Hash、备份与事务证据见 [本机执行记录](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage4/local-database-application.json)。完整目录门禁保留，回归后业务夹具无遗留。
- **已验证边界：** Platform 首轮全量 447 项，修订旧精确清单/维护合同后复验并补充边界场景，当前报告 480 项无失败/错误/跳过；最终 Maven verify 返回 0，指令覆盖率 97.68%、分支 90.03%（按仓库 append 配置累计）。33 项本期真实管理 HTTP 场景覆盖 PLT-I03 前 200/撤销后 401、其他凭证仍 200、权限收回、重放、真实 Lease/行锁/唯一索引、历史异常和审计。BFF 1,138 项当前报告通过，Web 923 项及生产构建通过，模型/Schema 38 项、需求站 136 项及构建通过。API 仅安装到本机 Maven 缓存，未发布远程。详细运行顺序与限制见 [开发检查记录](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage4/verification.json)。
- **查询计划与后续：** 用 64 Scope/256 凭证/512 授权/256 Route 的本机合成数据确认 Scope 唯一索引及两反向查询索引，完整事务回滚，无遗留；[EXPLAIN](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage4/local-explain.json)只证明本样本访问路径，不代表压力性能。上述阶段 4 BFF 使用 Owner Simulator；阶段 5 真实链验证见下，test 发布和提交继续按阶段确认。

- **阶段 5 修复及回归：** 真实浏览器发现凭证查询输入被模式下拉挤成 0px，已修复布局；Scope 中文名称搜索触发 ASCII/Unicode LIKE 字符集冲突，新增真实 HTTP 中文用例先失败后修复，不改 Schema。最终本期 34 项真实管理 HTTP 全通过，聚焦 Maven verify 返回 0、原累计覆盖率门禁通过；Web 923/923 与生产构建通过。BFF/Gateway 本阶段生产代码未改，复用已有开发检查。
- **阶段 5 完整链及页面：** 本机 Web 生产构建 → 入口代理 → 真实 Gateway/BFF/Platform/MySQL/Redis 完成 36 项代表性检查；修后另完成 11 项搜索复验与 8 项路由复跑。浏览器新增/只读/编辑/分页/引用/审计、权限、草稿冲突、浏览器返回与 1440/1024/390 布局通过；撤销提交后真实 PLT-I03 200→401。中文 Scope 选择保存最终复验通过，浏览器无捕获 error/warn。开发 Vault/签名和本机服务发现边界保留，不宣称真实 Client 业务 API 或 Risk 链已接入。
- **阶段 5 清理和证据：** 自有进程及浏览器已退出，独立本机库恢复 25 表/1,268 行；临时业务、账号/会话/命令/审计/签名记录均为 0，27 条临时 Route 和 8 个 Resource 恢复 DISABLED。材料见 [acceptance.md](../requirements/20260913/platform-support-ClientAPI接入管理/acceptance.md)、[验证记录](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage5/verification.json)、[清理计数](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage5/cleanup.json)。阶段 5 无未关闭验收缺陷；其发布停点已由后续“部署test”授权接续。

- **test 发布结果：** 已在 `desktop-linux/mor-test` 替换 Platform、Operations BFF、Operations Web，最终镜像前缀分别为 `9bc5b9165b49`、`b3e5fa00976c`、`fb434d52204e`；11 个容器健康，Gateway/Risk 等其余 8 个容器 ID 和镜像均未改变。首轮打包复用的旧探针缺少 BFF 19201 端口，已从现行源码重新编译并替换镜像，业务 JAR 未改。精确镜像、JAR、内嵌 API、Web SHA 与恢复边界见 [release.md](../requirements/20260913/platform-support-ClientAPI接入管理/release.md)。
- **test 数据与启用：** 在 `127.0.0.1:23306/mor_platform` 应用已批准的两旧 Credential/Grant 表及 Scope 新表，共 3 表/28 字段、两反向索引；备份仅涉及八张配置表。注册 127 条配置、两旧 Route 各递增一版；启用 9 Route/4 Resource/4 RolePermission，既有 PLATFORM_ADMIN 18→19。Gateway 自动加载 97 条 Route，当前 Snapshot 成功且不使用失败回退。未创建 Client/凭证/Scope 业务数据，未执行 test 紧急撤销。
- **test 部署检查：** 15 项轻量检查通过，包括真实管理员登录、新菜单/按钮、凭证与 Scope 查询、中文查询、Scope 筛选与审计过滤、合法 404、旧规则集/决策流查询；公开入口 44 个静态资源 SHA 与本期产物一致。仅检查部署可用性，未在 test 重跑阶段 5 的写入/撤销和浏览器完整验收；用户业务验收待完成。见 [test-acceptance.md](../requirements/20260913/platform-support-ClientAPI接入管理/test-acceptance.md)。

- **阶段 8 本地提交归档（2026-09-14）：** 用户明确要求“git提交”，已提交 platform-support-service `3cb5e31f`；operations-console-bff `85927a7a`；operations-console-web `4ebeed0e`。根目录归档本期需求/API/SQL/Pencil/模型与发布证据，只暂存本期共享台账段落；Payment 及前序 Gateway 文档的其他未提交改动保留。当前 Web 923/923、模型/Schema 38/38、需求站构建及 136/136 复查通过，阶段 5 修复源码 Hash 一致，未改业务代码，Java 沿用阶段 4/5 与 test 部署有效证据。本次只修正文档状态与链接；未推送、未再次部署、未执行 SQL。具体应用提交见 [提交清单](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage8/application-commits.json)。

以下阶段 1–3 条目保留各次交付历史；其中“尚未研发/待确认”已由上述最新状态接续。

## Platform Client API 接入管理（2026-09-13）

- **阶段 1 范围评审：** 用户同意下一期以 API 凭证生命周期、Scope 目录/授权关联和真实接口接入验证为方向，管理入口先在 Operations。已建立[需求范围](../requirements/20260913/platform-support-ClientAPI接入管理/requirement.md)、[分析入口](../requirements/20260913/platform-support-ClientAPI接入管理/technique.md)和[交付记录](../requirements/20260913/platform-support-ClientAPI接入管理/delivery.md)。仅登记评审稿，11 项功能、11 项功能验收、6 项 UI 验收待确认。旧凭证轮换失效时机 Q1 已询问、待答复；Scope 目录拟先提供新增、名称/说明编辑与引用查询，访问权仍通过凭证授权/撤销控制。
- **证据及边界：** 已有 PLT-I03 认证代码与凭证/授权结构；当前 Route 的 API Scope 仍为手动输入，缺少本期拟建设的统一目录和生命周期管理入口。真实 Client API 的业务 Owner、有效 Tenant 来源及 test 环境前置在阶段 2 核定，不能沿用旧 Simulator 证据宣称接通。本轮没有改应用/模型、执行 SQL、部署、提交或推送；API/表字段/注册 DML 数量尚未定稿，下一步等待阶段 1 确认。
- **阶段 1 文档检查：** 本需求 3 个 Markdown 文件、23 个本地链接、功能/验收编号及行尾空白检查通过。`npm --prefix requirements-site test` 构建通过，136 项中 135 通过、1 项失败；失败来自其他 Payment 模型工作中的台账统计 30 表/402 字段/45 唯一索引与旧断言 30/399/43 不一致，本次只追加需求段落，未修改该统计或断言。日志 `/tmp/plt-client-api-stage1-requirements-tests.log`；不把整站检查表述为全部通过，不修改其他需求来消除该失败。
- **阶段 1 职责修订讨论：** 用户指出日常凭证管理应属于 Client Console，Operations 应支持查看，并询问管理必要性。已在[需求稿首部](../requirements/20260913/platform-support-ClientAPI接入管理/requirement.md)提出修订：客户自助创建/轮换/授权调整/日常撤销，Operations 查看非秘密信息、维护 Scope/Route，紧急撤销仅为待确认建议；Platform 仍统一拥有数据和业务规则。原 Operations 全生命周期稿及其 F/AC/UI 清单暂停，不继续作为默认实现范围；轮换 Q1 暂缓至 Client 自助需求。没有扩大到 Client Console 研发，当前等待修订范围确认。
- **职责修订检查：** 本轮 24 个本地文档链接及行尾空白检查通过；需求站重新构建和 136/136 检查通过，日志 `/tmp/plt-client-api-responsibility-review-tests.log`。前次 Payment 统计断言失败不再复现，本任务未修改其统计或测试。仅完成职责讨论稿修订，不代表范围获批或功能实现。
- **后继范围已确认：** 用户回复“可以”，确认 Operations 凭证查看、Scope 目录与 Route 关联、独立授权紧急撤销；Client Console 自助生命周期和首次真实业务 API 开通退出本期。上方原阶段 1 全生命周期清单及 Q1 仅为历史，不再等待本期答复。[需求](../requirements/20260913/platform-support-ClientAPI接入管理/requirement.md) 已整理为 9 项功能、10 项功能验收和 6 项 UI 验收。
- **阶段 2 已交付待审阅：** [差异与影响](../requirements/20260913/platform-support-ClientAPI接入管理/technique.md) 已核对真实源码与表结构，预计 3 个应用、约 9 项新 API 能力及 4 项旧 API 调整；模型只拟新增一个 Scope 目录，Credential/Grant 复用旧设计，旧业务字段预计不增加，具体端点/字段/索引/DML 在阶段 3 冻结。Gateway 已有逐次 PLT-I03 认证与动态路由，预计无需修改生产逻辑；不扩展 CCT 或 Client Console。
- **test 只读观察：** `desktop-linux` / `mor-test` / `mor_platform`（宿主端口 23306）当前没有 t_api_credential、t_api_credential_scope，CLIENT Tenant 与 API_SCOPE Route 均为 0；旧两表正式 Schema 共 21 字段。预计后续 test 需应用 3 表建表 SQL，其中 2 表为既有模型补齐。证据见[目标表与计数](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage2/test-database-observation.json)，仅查询存在性及数量，执行前必须重新核对，不能推断其他环境为空。
- **阶段边界：** 已同步 PLT-02/PLT-16/PLT-I06/PLT-I19 和 E2E-16 已确认范围。尚未进入精确 API/数据/Pencil 方案、研发或综合验证；没有改应用/模型、执行 SQL、部署或提交。下一步等待用户确认阶段 2 后进入阶段 3。
- **阶段 2 文档验证：** 本需求 3 份 Markdown 的 33 个本地链接（含 4 个锚点）、9/10/6 功能与验收编号、只读观察字段/目标表关系及空白检查通过；`npm --prefix requirements-site test` 生产构建和 136/136 检查通过，日志 `/tmp/plt-client-api-stage2-requirements-tests.log`。本次未运行 Java 或真实 HTTP 功能回归，文档检查不代表拟议接口或撤销功能已实现。

- **阶段 2 已确认，阶段 3 已交付待审阅：** 用户回复“OK”，据此完成 [实施方案](../requirements/20260913/platform-support-ClientAPI接入管理/design.md)、[API](../requirements/20260913/platform-support-ClientAPI接入管理/api.md)、[数据](../requirements/20260913/platform-support-ClientAPI接入管理/data-design.md)、[SQL](../requirements/20260913/platform-support-ClientAPI接入管理/db/README.md) 和 [Pencil 页面](../requirements/20260913/platform-support-ClientAPI接入管理/ui.md)。3 个应用新增 9 项/修改 4 项 API 能力，对应 Platform/BFF 合计 18 个新端点、8 个修改端点；模型新增 1 表/7 字段/1 主键/1 唯一索引、旧业务字段 0、两旧表各加 1 普通索引。test 另补两张原设计表，共需建 3 表/28 字段。注册 SQL 为 127 INSERT + 两旧 Route 更新，初始新路由/菜单/角色授权不生效；不含业务种子或启用 SQL。
- **阶段 3 页面与边界：** Web docs/design.pen 已保存新增 8 个页面状态和 1 张字段关系图，保留原 54 画板；9 张 PNG 和 8 页 PDF 已导出至本需求 assets/stage3。A09 复用 Route READ，无 PLT-I02 白名单或 Gateway 业务代码扩展。当前仅文档和 Pencil 设计变更，未生成正式模型、修改应用功能、执行 SQL、创建示例、部署、提交或推送。上方阶段 2“尚未进入精确方案”已由本记录接续，当前等待阶段 3 整体确认后进入研发。
- **阶段 3 验证：** [静态检查证据](../requirements/20260913/platform-support-ClientAPI接入管理/assets/stage3/design-verification.json) 核对端点/注册子项/127 INSERT/两旧 Route 目标、旧两表与正式 Schema 一致、10/11/7 字段、验收编号和本地链接；Pencil 布局无裁剪，9 张 PNG 与 8 页 PDF 有效。需求站生产构建及 136/136 检查通过，日志 `/tmp/plt-client-api-stage3-requirements-tests.log`；没有数据库 SQL/EXPLAIN 或应用功能验证。

<a id="platform-tenant-access-20260914"></a>

### 租户访问管理与客户/合作伙伴成员权限查询：用户验收通过，代码已提交（2026-09-14）

- **阶段 8 用户确认与提交：** 用户回复“我做完了，提交吧。”，确认本期验收并授权提交。Platform `cb80a2c7c7ab641b7d0646807d34d7fcc05d037a`、BFF `4685f620ad0f93ec9b78c687813a69100caf1666`、Web `ff0b7003b5581eaafd926d7049ab23f3a8dc6462`；需求归档随本条记录所在根提交完成。67个源文件与已验收基线一致，复用Platform512/BFF1163/Web938及阶段5/7有效证据；提交前需求站136/136和范围/敏感值/空白检查通过。Web仅纳入本期8张Pencil画板，其他任务19张仍留工作区；根共享文件仅暂存本需求段落，Payment等修改保留。未推送、重新部署或修改test数据。见[交付记录](../requirements/20260914/platform-support-租户访问与成员权限查询/delivery.md)、[提交验证](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage8/verification.json)。Client/Partner Console菜单控制按用户决定延期。

以下记录保留此前部署、示例创建和验收时的观察，由上述用户确认接续。

- **test 示例（部署后追加授权）：** 用户明确要求“帮我创建一条数据”，已创建 CLIENT 租户 `900000000000065001` / “DEMO 客户租户（验收）”，配1个CUSTOMER成员、1个租户只读角色、2项示例权限，共9INSERT，既有行0UPDATE。合成来源不代表真实CCT入驻，没有登录凭据/API Credential；初始双状态ACTIVE。真实公开接口8项查询均通过，成员两项权限各有1个有效来源。当前test有1个CLIENT租户及1个成员；下方阶段7的0数据为部署时观察。见[示例说明](../requirements/20260914/platform-support-租户访问与成员权限查询/db/test-demo/README.md)、[回执](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/test-demo/verification.json)。暂停/恢复留给用户验收，没有提交或重新部署。

- **阶段 7 授权与执行：** 用户对阶段 6 精确发布清单回复“可以”。复核 11 容器、67 源文件及 SQL/制品无漂移后，一次备份八张受影响配置表，按 Platform→BFF→Web 替换三个固定候选镜像；三个应用 healthy，其他八个容器身份/镜像未变。`127.0.0.1:23306/mor_platform` 人工单连接核对后分别提交 105 INSERT、15 启用 UPDATE，0 DDL；管理员角色 version 19→20。见 [部署与验收报告](../requirements/20260914/platform-support-租户访问与成员权限查询/test-acceptance.md)、[阶段 7 证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage7/verification.json)。
- **部署修正：** 初次交互 MySQL 客户端默认 latin1，将三条新 Resource 中文名称写成乱码；页面检查发现后，以精确 ID/旧字节/version 修正为已批准名称，3 UPDATE，Resource version 2→3。实际合计 105 INSERT、18 UPDATE。公开导航及浏览器均确认中文正确；对应 [修正 SQL](../requirements/20260914/platform-support-租户访问与成员权限查询/db/repair-test-display-names.sql) 和 [修正后恢复候选](../requirements/20260914/platform-support-租户访问与成员权限查询/db/deactivate-test-after-label-repair.sql) 已归档，未执行恢复。
- **部署检查与限制：** Gateway 快照正常加载 105 条有效 Route（新增 8）；真实入口登录、三个新导航资源、客户/合作伙伴空页、不存在/INTERNAL 租户 404、既有人員/角色/API Route/审计/风控规则查询通过。46 个公开 Web 文件与候选摘要一致；浏览器新菜单、筛选、刷新和返回保持条件、退出通过。test 的 CLIENT/PARTNER Tenant/Member 仍为 0，本次没有造数据或重复暂停/恢复/成员明细业务验收；完整业务证据沿用阶段 5。入口 `http://localhost:28080/tenant-access`。当前停点为用户验收；本需求未 Git 提交、推送或发布远程制品。

以下阶段 6 及更早条目保留当时的准备、实施和授权记录，由上述阶段 7 接续。

- **阶段 6 发布准备：** 用户确认阶段 5 后同意准备发布。只读核对 `desktop-linux/mor-test/127.0.0.1:23306/mor_platform`：11 容器健康，IAM 7 表/75 字段齐全，新登记无冲突，管理员角色 version=19、有效 Route 97、CLIENT/PARTNER Tenant/Member 为 0。已构建三个本机候选镜像并固定 image ID，Platform/BFF 内嵌 API 字节一致，Web 46 个文件固定到阶段 5 构建，67 个应用源文件无漂移。已准备 105 INSERT + 15 启用 UPDATE、旧镜像及精确版本停用恢复方案；尚未备份/修改 test 数据、替换运行容器或发布远程制品。候选/恢复 Compose 校验与需求站 136/136 通过。[发布清单](../requirements/20260914/platform-support-租户访问与成员权限查询/release.md)、[制品清单](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage6/release-manifest.json)、[准备证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage6/verification.json)。当时停点是等待 test 部署清单授权；该授权及实际执行见阶段 7，不包含 Git 提交或新增 test 业务示例。

- **阶段 5 真实链路与页面：** 用户在具体本机临时启用申请后回复“可以”。生产 Web → 本机入口 → 真实 Gateway/BFF/Platform/MySQL/Redis 完成 42 项检查和 19 项页面核对：暂停后的 PLT-I03 200→401→恢复后200、权限隔离、分页、幂等、审计、来源限制、损坏关联失败关闭；页面冲突保留原因、权限来源、浏览器返回及 1280/390 布局通过。67 个源文件与阶段 4 一致，未新增业务修复；手工脚本纠正了公开入口应按既有 BFF 503 检查而非 Owner 500 的断言。见 [验收报告](../requirements/20260914/platform-support-租户访问与成员权限查询/acceptance.md)、[验证记录](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage5/verification.json)。开发 Vault/签名、本机发现和合成业务数据边界保留，未验收 CUSTOMER 登录或外部 Client 业务执行。
- **阶段 5 恢复记录：** 自有进程、浏览器和账号/租户/成员授权/凭证/命令/审计/签名夹具已清理，11 行临时配置恢复 DISABLED/version=1，原管理员 3 条关联仍 REVOKED，105 行正式登记保留。见 [SQL 回执](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage5/local-activation.json)、[清理核对](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage5/local-postcheck.json)。没有未关闭验收缺陷；用户已确认接续阶段 6，当前停点见上方。以下保留阶段 4 有效开发证据。

- **实际改动：** Platform/BFF/Web 三应用完成租户列表/详情、安全暂停与恢复、成员/角色/权限及来源查询。8项新业务 API（7读1写，Owner/BFF各8端点）、3页面、0表结构/字段/索引变更、105行登记。只修改目标Tenant安全状态及版本/时间；成员授权只读。Gateway/其他核心应用未改；阶段 4 当时尚未提交、推送、发布或部署。
- **本机 SQL：** 用户针对具体申请回复“继续”后，已备份并在 `mor-local-mysql-1/127.0.0.1:13306/mor_platform_config_validation` 应用105 INSERT，0DDL/0旧行更新。8新路由、3新资源仍DISABLED，3管理员授权仍REVOKED；安装执行元数据ID1仅为已批准的独立本机例外，没有创建账号。[应用与备份](../requirements/20260914/platform-support-租户访问与成员权限查询/db/local-application.md)。阶段 4 当时 test 尚未应用，阶段 7 执行见上方。
- **开发检查：** Platform最终 `clean verify` 512/512（其中本期31），指令覆盖率97.82%、分支90.06%，通过原90%门槛；BFF `verify` 1163/1163（本期25），Web938/938及TypeScript/Vite生产构建通过。保留既有Web chunk大小提示。完整记录及当前源文件摘要见 [验证证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage4/verification.json)。
- **阶段 4 回归修正与边界：** 修复已写入的租户安全操作被原审计前缀查询排除；补齐损坏关联、回执与权限停用用例，保留单资源锁、DB行锁/CAS、幂等重放前重新授权。代表性EXPLAIN使用现有索引，非负载测试。当时的浏览器证据仅为生产页面组件与合成响应交互；阶段 5 完整链结果见上方。详见 [验收映射](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage4/acceptance-mapping.md)。
- **阶段 4 清理记录：** 当时测试合成数据及临时 Web 预览已清理，登记计数/禁用状态保持预期，见 [本机核对](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage4/local-postcheck.json)。后续阶段 5 已执行并恢复，当前停点见上方。

以下阶段 3 条目保留当时的交付及授权证据，当前状态由上方记录接续。

- **设计规模：** Platform/BFF/Web 3 应用，新增 8 项能力（7 读1写，Owner/BFF 各8端点），既有 API 路径/请求变更0；0表结构/字段/索引变更，105 INSERT/0初次旧行UPDATE，后续启用预计15 UPDATE。3功能页面、8张Pencil状态画板；旧审计/页面能力接口有内容扩展，Gateway无需改代码。
- **本轮工件与观察：** Web docs/design.pen 保存新增8画板，保留原63画板；导出8PNG及8页PDF。只读检查本机验证库和test的新ID/权限/路径占用为0，Active Operations Target/管理员角色各1；未应用SQL、创建示例、修改功能代码、部署、发布或提交。其他任务修改保留。证据入口为 [阶段3材料](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage3/design-verification.json)。

- **阶段 3 检查：** 88 个本地文档链接/锚点、8 项 API 与 105 行 SQL 的列名/值数量/唯一 ID/路径/20 项 Query 对齐、6 个 JSON 示例、7/10/6 验收编号通过；8 张 PNG/8 页 PDF 有效，Pencil 无裁剪且已保存。需求站生产构建及 136/136 检查通过，日志 `/tmp/plt-tenant-access-stage3-requirements-tests.log`。未运行本期应用功能、实际 SQL 或性能测试；[完整证据](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage3/design-verification.json)。

以下阶段 2/1 记录为历史观察及交付口径，已由上方当前阶段接续。


- **最新授权与停点：** 用户回复“可以”，确认阶段 1 范围/验收及逐阶段安排；已交付 [阶段 2 差异与影响](../requirements/20260914/platform-support-租户访问与成员权限查询/technique.md)。预计 Platform/BFF/Web 三个应用、约 8 项新 API 能力、0 新业务表/字段，Gateway 无需改代码；精确接口/索引/DML/Pencil 数量在阶段 3 冻结。等待用户确认分析及成员标识采用“脱敏邮箱＋账号 ID”的细化；现有模型没有姓名字段，尚未据此修改阶段 1 F-04。
- **阶段 2 只读证据：** 已对 desktop-linux 的 test `mor-test-mysql-1/127.0.0.1:23306/mor_platform` 和本机验证库 `mor-local-mysql-1/127.0.0.1:13306/mor_platform_config_validation` 读取七表字段、索引及聚合计数。七表共 75 个既有字段，列名/数量与当前 Schema 一致；PDManer 同步。两库均无 CLIENT/PARTNER Tenant、CUSTOMER 账号/成员及 CLIENT 凭证；test 既有 Operations 数据保留。证据见 [database-observation.json](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage2/database-observation.json)，不是完整库或性能验收。
- **阶段 2 边界：** 同步 PLT-15/E2E-16 已批准范围，读取现有 PLT-I03/管理测试但未运行功能回归；无应用/模型/SQL/部署/制品发布/提交变更。后续合成数据、CUSTOMER 运行链缺口与安全命令并发在技术稿中明确，未提前创建示例或扩大到 CCT 投影实现。以下阶段 1 记录保留为历史。
- **阶段 2 文档检查：** 三份 Markdown、44 个本地链接/所引用显式锚点、7/10/6 功能验收编号、8 项分析 API 编号和七表观察与模型/Schema 列名一致性检查通过；四个应用仓库保持干净。`npm --prefix requirements-site test` 生产构建及 136/136 检查通过，差异空白检查通过；证据见 [verification.json](../requirements/20260914/platform-support-租户访问与成员权限查询/assets/stage2/verification.json)。未运行本期应用功能验证，阶段 2 仍待用户确认。

- **需求授权与停点：** 用户选择两项能力一起做；已登记 [需求评审稿](../requirements/20260914/platform-support-租户访问与成员权限查询/requirement.md)、[技术证据入口](../requirements/20260914/platform-support-租户访问与成员权限查询/technique.md) 和 [交付记录](../requirements/20260914/platform-support-租户访问与成员权限查询/delivery.md)。拟覆盖 CLIENT/PARTNER 租户查询、安全暂停/恢复、成员角色和权限只读，7 项功能、10 项功能验收、6 项 UI 验收。等待用户确认整体范围后进入阶段 2；尚未定稿 API/表字段/注册 DML/Pencil 数量。
- **已核对边界：** 现有 Tenant 模型区分 CCT 来源业务状态与 Platform 安全状态；当前人工授权 API/SQL 限 Operations/INTERNAL，不能推定已支持 CUSTOMER 成员查询。现有 PLT-I03 认证已检查 CLIENT Tenant 的来源 ACTIVE 与安全 ACTIVE。本期不以管理页面替代未建设的 CUSTOMER 登录/会话链，不修改客户主档或提供成员授权写入。
- **本轮实际动作：** 只读核对文档、源码及工作区，创建阶段 1 材料并追加检索/状态入口。应用代码、模型、数据库和部署均未变更，未提交或推送；未查询本机/test 数据库。其他任务工作区修改保留，已确认范围的全局用例/时序投影待本阶段确认后处理。
- **阶段 1 文档检查：** 3 个需求 Markdown、19 个本地链接及所引用显式锚点、7/10/6 功能和验收编号、行尾空白检查通过；共享文档差异空白检查通过。`npm --prefix requirements-site test` 完成生产构建及 136/136 检查，日志 `/tmp/plt-tenant-access-stage1-requirements-tests.log`。三个应用仓库保持干净；本轮未运行 Java、HTTP 或页面功能验证，文档检查不代表本期能力已经实现。

# Risk 工程

Client 入驻材料要求查询的合同见[通用化差异稿](../requirements/20260914/client-console-注册与客户入驻/generic-material-form-proposal.md#4-材料目录risk)。入口 `/internal/due-diligence/material-requirements` 只接受 qualificationType 一个参数，由 `onboarding.materials.enabled=true` 显式启用，默认关闭；`onboarding.materials.programs-json` 是受控映射数组，每项严格包含 qualificationType、programCode，每类资格最多一项、总长不超过 65,536 字符；材料不按国家区分，充分性由人工审核判断。仅支持 INDIVIDUAL_KYC、ORGANIZATION_KYB、RELATED_PERSON_KYC；程序代码为最多 64 位大写字母/数字/下划线且字母开头。未配置、无精确匹配、程序/生效修订/材料要求不完整时返回不可用，不使用首条 ACTIVE 或空清单默认通过。正式映射和材料规则须由业务另行确认，应用不初始化它们。查询复用本库 DD Program/Version/Requirement，不创建 Case 或客户资料版本。实现与验证范围只见[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

> 本轮规则集整体管理的现行合同见[需求技术方案](../requirements/20260911/operations-console-风控规则集列表优化/technique.md)：沿用原接口版本，一集一行、整体删除及实际生效使用保护；Web 隐藏 Candidate 页面，后端历史合同保留。阶段确认与证据见[交付记录](../requirements/20260911/operations-console-风控规则集列表优化/delivery.md)，实现与环境状态只见[当前台账第 16 节](../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)。


V1 配置增量的接口、SQL 和兼容规则见[接口合同](../agent-docs/plans/2026-09-11-risk-rule-v1-configuration-contract-draft.md)，最新验证和数据库状态只见[当前实现台账](../agent-docs/CURRENT_IMPLEMENTATION.md)。以下先前验证数字保留为当时证据。

Maven 根项目为 `risk-service`。为保留现有 PDManer 生成路径、独立仓库与文档链接，物理目录暂沿用 `risk-compliance-service`，本轮不迁移或改名数据库资产。

## 模块与边界

| 模块 | 职责 |
|---|---|
| risk-api | Risk 私网 `@HttpExchange` 合同：规则集、Candidate、执行发布、流程、字典、配置查询、统一变量、名单、支付评估、出款资格与材料目录 |
| risk-decision | 规则、Candidate、执行发布与影子运行、流程、字典、统一变量、静态校验、Action 目录及支付评估运行期（决策引擎、实时累计与名单命中 Feature）的 Processor、领域与持久化实现 |
| risk-control | 名单、冷却、限额与出款资格控制；当前有运营名单维护、名单命中查询与 RCO-I09 出款资格 Processor（Fail-Closed） |
| risk-compliance | 尽职调查、资格与争议业务；当前提供通用材料目录读取 |
| risk-web | 唯一 Spring Boot 入口，只承载 Controller、协议转换、异常转换与应用装配配置 |
| risk-test | DB-free 合同/纯测试、真实 Loopback HTTP/MySQL、并发恢复与模块边界验证 |

业务 JAR 之间只有 `risk-decision → risk-control` 一条单向依赖（名单命中查询 `RiskListMembershipService` 与主体键规则），其余不相互依赖；写接口与复杂查询的 Processor 位于各自 Owner 模块的 `service/processor`，继承 Foundation `AbstractProcessor` / `AbstractLockProcessor`，本地事务使用 `TransactionTemplate`。API 和业务模块已建立 `src/main/java` 根包与 `src/main/resources`，通过 `package-info.java` 说明模块职责，不创建空 Service、DTO、Mapper 或未来配置。API/业务模块保持普通 JAR；Web 同时生成普通 JAR 和可执行 `-exec.jar`；Test 不发布。

测试集中在 `risk-test/src/test/java`，测试资源位于 `risk-test/src/test/resources`；不为每个业务 JAR 重复建立测试工程。DB-free 测试不会连接业务库；真实 HTTP/Repository/MySQL 测试只可复用手工准备的仓库 `local-environment`，不得建表、迁移或 Seed。

## 构建与启动

使用 JDK 25 和 Maven Wrapper 3.9.16，继承 `com.paytech:paytech-parent:1.0.0-SNAPSHOT` 与统一 BOM。Maven Settings 需配置项目 Nexus，凭据不进入源码；不要求同级 Foundation 源码。Risk artifactId 按用户决定统一使用 `risk-*`，已有公共依赖仍使用其原有 `paytech-*` 名称。

```sh
./mvnw clean verify
java -jar risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar
```

默认监听 `127.0.0.1:18104`，应用名 `risk-service`；可通过 `SERVER_ADDRESS` / `SERVER_PORT` 覆盖。只开放同端口 `/actuator/health`（含 liveness/readiness）、`/actuator/info`、`/actuator/prometheus`。关闭端点发现、JMX 和健康详情；未配置公网路由或认证，不应直接暴露公网。

应用可通过 Nacos Discovery 在部署私网内注册为 `risk-service`，注册地址、Namespace 和实例 IP 分别由
`NACOS_SERVER_ADDR`、`NACOS_NAMESPACE` 和 `SPRING_CLOUD_NACOS_DISCOVERY_IP` 注入。本轮没有增加公网暴露或
Nacos 配置中心依赖；`auto` 测试显式关闭通用 Discovery、Nacos Discovery 及两层自动注册，不会访问 Nacos。

健康状态 UP 只表示进程可启动，不代表本切片已部署或任何运行期风控决策可用。DB-free 合同、结构和纯领域测试不连接数据库、Nacos、Redis、MQ 或其他服务；Web 装配与真实业务 HTTP `auto` 测试均在随机 Loopback 端口启动 Spring，后者通过真实 Controller→Processor→Service→Repository/Mapper→本地 MySQL 链路验证九条路由、严格输入、持久化、并发和恢复，并在上下文启动前强制检查人工准备的仓库本地 Risk 数据库。复用 Foundation MVC 日志和错误处理，不创建本地 Filter/Advice。

## Risk Operations Rule Configuration Backend（工作区实现状态）

当前工作区已包含 Risk-only 的九条私有管理路由、领域/持久化代码、完整 71 表 Schema DDL、唯一初始化 DML 与 PDManer 模型资产。经用户授权，完整 SQL 已应用到应用前为空的仓库自有本地 `mor_risk`，并完成真实 Loopback HTTP/MySQL、并发和恢复验证；这些本地证据不表示已经部署。权威状态台账见 [`../agent-docs/CURRENT_IMPLEMENTATION.md`](../agent-docs/CURRENT_IMPLEMENTATION.md)。

2026-09-05 的 pre-application 历史门禁为：Risk 模型/Schema 188/188、需求站点 134/134、生产模块打包和 DB-free Java 111/111 通过；当时完整 `./mvnw clean verify` 按设计在人工数据库前置检查停止。2026-09-06 应用本地 SQL 后，模型/Schema 188/188、需求站点 134/134 与 JDK 25 `./mvnw clean verify` 148/148 全部通过；Maven 门禁包含真实 MySQL 持久化、HTTP、并发幂等、五条 GET 的只读一致性快照、历史 Action 退役后的详情/重放、变更原因敏感形态拒绝，以及 Validate A/读快照/事务外纯校验/B Recovery。

两个内部 Base Path 为：

- `/decision-rule-sets/v1`：Authoring Context、Rule Set 列表/详情、Create Version、Freeze；
- `/decision-releases/v1`：TEST Candidate 列表/详情、Create、Validate。

每个调用都要求 `X-Gateway-Actor-Subject` 与 `X-Gateway-Session-Id`。HTTP `Idempotency-Key` 按
[2026-09-17 决定](../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)归 Gateway，Risk 不再解析或重放；
业务防重依靠唯一编码、根版本 CAS 与数据库约束。当前切片只严格解析操作者与会话并把它们作为 correlation 输入：**它不认证 Actor、验证
Session 或执行 PLT-I02 授权**。格式不合规、未知代码、损坏闭包或不允许状态均 fail-closed；该边界不能被
称为 Gateway、BFF、平台权限或真实人员身份。

管理路由及 Foundation 日志仍是 metadata-only：不得记录规则 Body、AST、Literal、原始 Idempotency Key、
Session、Vault/Consumer/网络原值、Secret、Credential 或完整 Payload。Validate 审计 trigger 由 Candidate
身份派生，不保存任何请求 Key。服务级命令幂等表 `t_command_idempotency` 已无代码使用，删除需另行评审 DDL。

### 本地数据库状态与测试边界

完整人工工件位于：

- `docs/database/sql/risk-compliance-schema.sql`
- `docs/database/sql/risk-compliance-initial-data.sql`

2026-09-06 经用户明确授权并核对目标为仓库自有且应用前为空的本地 `mor_risk` 后，已先备份，再手工应用
完整 Schema 与唯一 DML。物理核对为 71 表、862 字段、71 主键、105 唯一二级索引、75 访问索引、零
`CHECK`、零 `FOREIGN KEY`、零自增和零空表/字段注释；固定目录恰为 1 个 `CHECKOUT_PREPARE` Point 与
3 个 ACTIVE Action Version。全量测试清理后仅保留这 4 行初始化目录事实。

自动测试配置只允许 Loopback `127.0.0.1:13306/mor_risk` 与 `risk_app`；密码从
`RISK_TEST_DB_PASSWORD` 或 `MOR_RISK_DB_PASSWORD` 读取。PDManer 4.9.3 应用内人工 Review 仍待执行，
本地物理应用和自动测试不构成共享环境或生产部署。

本期明确未实现或未接入：Operations UI/BFF、Gateway Target/Route、PLT-I02/Auth、Shadow、审批、激活、
运行期 Decision Engine/Checkout 评估、后台恢复、MQ、部署。不得把工作区工件或 DB-free 测试外推为上述能力。

## 支付反欺诈一期（2026-10-14）

按[需求与设计](../requirements/20261014/risk-支付反欺诈一期/design.md)新增三组内部路由，均不经 Gateway、一期无页面：

- `/payment-risk-assessment/v1/assessments`：Payment 每个支付尝试同步评估一次，返回 `ALLOW/REVIEW/DENY/FAILED/TIMED_OUT` 与认证要求；同一尝试同内容重放返回原结果，内容不同 409，本环境无生效发布 503 `PAYMENT_RISK_NOT_CONFIGURED`。请求体含消费者 IP 与卡指纹，日志为 metadata-only。
- `/risk-lists/v1`：名单与成员维护（主体键规范化、响应脱敏）。
- `/decision-releases/v1/executions`：执行发布创建、静态校验、回放式影子运行、TEST 一人激活与查询。

运行环境由 `decision.runtime.environment`（环境变量 `RISK_DECISION_ENVIRONMENT`）决定，一期只允许激活 TEST 发布。影子运行经本地 `compensation_event`（`DECISION_SHADOW_RUN`）在事务外执行。

## RCO-I09 最小内部接口

`POST /manual-payout-eligibility/v1/evaluations` 已发布四个 Manual Payout 检查点的内部契约，由 `risk-control` 的 `ManualPayoutEligibilityProcessor` 处理。接口是否属于内部调用由私有网络、服务发现和 Gateway Catalog 边界决定，不编码进能力路径。

入口先严格校验检查点/来源矩阵、创建类版本、角色、ID 范围与 `inputHash`，输入错误返回 `400`；合法请求一律返回 `503 SERVICE_UNAVAILABLE`，因为 RCO-I09 所需的 PRODUCTION/ACTIVE 正式运行 Release 与 Assessment/Evaluation 执行、持久化链尚未实现，不能产生 ALLOW。原 `local-evaluation-bootstrap` 内存放行实现违反生产代码不得含模拟行为的规则，已删除；调用方联调需使用测试侧 DTO Mock。

## 本轮范围

本切片只实现私有 Rule Set/Candidate 配置后端及其静态校验编排；不扩大到正式 Release、Action 运行、冷却、
限额、尽调、争议、远程调用、消息、调度、补偿或部署。完整设计见
[`../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`](../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)。

管理写入使用 Foundation `AbstractLockProcessor` 的显式 Redis 资源锁（`decision:*` Key，等待 500ms、租约 30s），本机 auto 验证除 MySQL 13306 外还需 local-environment 的 Redis 16379。通过 `RISK_TEST_REDIS_PASSWORD` 或 `MOR_REDIS_PASSWORD` 注入本机 Redis 凭据，测试键使用 `risk-auto` Namespace；不启动容器或自动迁移数据库。已有库必须先人工审核本需求 Risk DDL/DML，不能重跑全量初始化 SQL。

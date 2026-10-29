# risk-decision

- spring.application.name：`risk-decision-service`（原 `risk-service`，第 06 步不保留别名注册）
- 端口：18104；管理端口 19104（Actuator 不再与业务端口共用）
- 数据库：`mor_risk_decision`（原 `mor_risk` 中的决策、评估、名单与限额表；`mor_risk` 本地保留至合入后删除）
- 来源现有工程：`risk-compliance-service`（第 06 步拆出 risk-decision 与 [compliance](../compliance/README.md)；争议 7 表只有 Schema，归 [dispute](../dispute/README.md) 的 `mor_dispute`）

## 模块与边界

| 模块 | 职责 |
|---|---|
| `paytech-risk-decision-api` | 私网 `@HttpExchange` 合同（包 `com.paytech.risk.decision.api`）：规则集、Candidate、执行发布、流程、字典、配置查询、统一变量；`evaluation` 子包为统一评估、评估查询与 Manual Payout 资格；`control` 子包为名单维护与 Client 承保（`ClientUnderwritingApi`，`/risk-underwriting/v1`，REQ-03）；`enums`、`validation` 为错误码与字典文本校验 |
| `paytech-risk-decision-core` | 原 `risk-decision` 与并入的 `risk-control`（design/07 §1）：决策引擎、规则 / 发布 / 影子运行 / 流程 / 变量 / 字典 Processor、结账与支付运行期评估、Velocity、名单命中与维护、Client 承保（草稿 / 提交 / 四眼复核 / 自动承保 / 修订 Processor 与 `ClientUnderwritingQueryService` 同进程读取）、RCO-I09 出款资格 Processor（Fail-Closed）、`DECISION_SHADOW_RUN` 补偿 Handler |
| `paytech-risk-decision-app` | 唯一可执行：`com.paytech.risk.RiskDecisionApplication`、Controller、DataSource / `@MapperScan`（decision、control 两个 Mapper 包）/ 40 余个闭合枚举 TypeHandler、Snowflake（`decision.identifier.*`）、Clock、补偿与 Redis 协调配置 |
| `paytech-risk-decision-test` | 全部 `auto` 测试（原 risk-test 中 decision / evaluation / control / logging / domain 用例整类搬入）、`RiskDecisionArchitectureTest` 与 R1–R12 `ArchitectureAutoTest` |

- 入站调用方：Operations BFF（规则配置、名单、评估查询、承保）、acquiring（`RiskDecisionApi` CHECKOUT_PREPARE）、payment（`RiskDecisionApi` PAYMENT_ATTEMPT_RISK）。本服务没有出站 HTTP 客户端，不发布也不消费 RabbitMQ。
- 运营请求上下文使用 Foundation `paytech-mvc-web-starter` 的 `OperationRequestContextParser`（原 `RiskOperationRequestContextParser` 已删除）。
- `internal-auth.mode` 默认 `"OFF"`。
- 结算资格控制（`t_settlement_control` 三表）归 compliance（C3、D38）；将来出款检查点评估经 `paytech-compliance-api` 的 `SettlementControlApi` 在评估事务前读取 ACTIVE 控制，compliance 不可用时失败关闭。`CODE-MOR:RISK:*` 哈希 Schema 标识与 `SETTLEMENT_ELIGIBILITY_INPUT:1` 不变。

## 构建与测试

在 `paytech/` 下执行（JDK 25）：

```sh
./mvnw -B verify -pl risk-decision/paytech-risk-decision-test -am -Dpaytech.local-environment.dir=<仓库>/local-environment
java -jar risk-decision/paytech-risk-decision-app/target/paytech-risk-decision-app-1.0.0-SNAPSHOT-exec.jar
```

测试只连接仓库 `local-environment` 的 `127.0.0.1:13306/mor_risk_decision`（账号 `risk_decision_app`）与 Redis 16379，Key 前缀 `risk-decision-test`。空库由开发者用 MySQL root 人工执行一次 `paytech-risk-decision-test/src/test/resources/init/ddl.sql`（逐字切自 [`docs/database/sql/risk-decision-schema.sql`](docs/database/sql/risk-decision-schema.sql)，加 `t_consumed_event` 与 [`risk-decision-initial-data.sql`](docs/database/sql/risk-decision-initial-data.sql) 的目录数据）；测试不建库、不迁移。PDManer 规范模型为 [`docs/database/risk-decision.pdma.json`](docs/database/risk-decision.pdma.json)。

> 以下各节为拆分前 Risk 工程的历史说明，保留为当时证据；其中的 `risk-web`、`risk-test`、`risk-control`、`mor_risk`、`risk_app` 与 `./mvnw` 指拆分前的旧结构。

## Risk Operations Rule Configuration Backend（工作区实现状态）

当前工作区已包含 Risk-only 的九条私有管理路由、领域/持久化代码、完整 71 表 Schema DDL、唯一初始化 DML 与 PDManer 模型资产。经用户授权，完整 SQL 已应用到应用前为空的仓库自有本地 `mor_risk`，并完成真实 Loopback HTTP/MySQL、并发和恢复验证；这些本地证据不表示已经部署。权威状态台账见 [`../../agent-docs/CURRENT_IMPLEMENTATION.md`](../../agent-docs/CURRENT_IMPLEMENTATION.md)。

2026-09-05 的 pre-application 历史门禁为：Risk 模型/Schema 188/188、需求站点 134/134、生产模块打包和 DB-free Java 111/111 通过；当时完整 `./mvnw clean verify` 按设计在人工数据库前置检查停止。2026-09-06 应用本地 SQL 后，模型/Schema 188/188、需求站点 134/134 与 JDK 25 `./mvnw clean verify` 148/148 全部通过；Maven 门禁包含真实 MySQL 持久化、HTTP、并发幂等、五条 GET 的只读一致性快照、历史 Action 退役后的详情/重放、变更原因敏感形态拒绝，以及 Validate A/读快照/事务外纯校验/B Recovery。

两个内部 Base Path 为：

- `/decision-rule-sets/v1`：Authoring Context、Rule Set 列表/详情、Create Version、Freeze；
- `/decision-releases/v1`：TEST Candidate 列表/详情、Create、Validate。

每个调用都要求 `X-Gateway-Actor-Subject` 与 `X-Gateway-Session-Id`。HTTP `Idempotency-Key` 按
[2026-09-17 决定](../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)归 Gateway，Risk 不再解析或重放；
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

按[需求与设计](../../requirements/20261014/risk-支付反欺诈一期/design.md)新增三组内部路由，均不经 Gateway、一期无页面：

- `/risk-decisions/v1/evaluations`：同步风控点统一评估接口，按 `pointCode` 与输入合同版本区分（当前只登记支付尝试 `PAYMENT_ATTEMPT_RISK` v1，调用方用 `PaymentAttemptRiskInput` 构造请求）。同一业务身份同内容重放返回原结论，内容不同 409；输入不符合合同 422 并指出字段；本环境无生效发布 503 `RISK_DECISION_NOT_CONFIGURED`。认证要求等附加输出由放行结束节点配置。请求含消费者 IP 与卡指纹，日志为 metadata-only。
- `/risk-lists/v1`：名单与成员维护（主体键规范化、响应脱敏）。
- `/risk-underwriting/v1`：Client 承保记录（REQ-03，12 个接口；输入快照由 Operations BFF 从 Owner 读取后随命令带入，本服务冻结；复核人 ≠ 提交人在本服务校验）。
- `/decision-releases/v1/executions`：执行发布创建、静态校验、回放式影子运行、TEST 一人激活与查询。

运行环境由 `decision.runtime.environment`（环境变量 `RISK_DECISION_ENVIRONMENT`）决定，一期只允许激活 TEST 发布。影子运行经本地 `compensation_event`（`DECISION_SHADOW_RUN`）在事务外执行。

## RCO-I09 最小内部接口

`POST /manual-payout-eligibility/v1/evaluations` 已发布四个 Manual Payout 检查点的内部契约，由 `risk-control` 的 `ManualPayoutEligibilityProcessor` 处理。接口是否属于内部调用由私有网络、服务发现和 Gateway Catalog 边界决定，不编码进能力路径。

入口先严格校验检查点/来源矩阵、创建类版本、角色、ID 范围与 `inputHash`，输入错误返回 `400`；合法请求一律返回 `503 SERVICE_UNAVAILABLE`，因为 RCO-I09 所需的 PRODUCTION/ACTIVE 正式运行 Release 与 Assessment/Evaluation 执行、持久化链尚未实现，不能产生 ALLOW。原 `local-evaluation-bootstrap` 内存放行实现违反生产代码不得含模拟行为的规则，已删除；调用方联调需使用测试侧 DTO Mock。

## 本轮范围

本切片只实现私有 Rule Set/Candidate 配置后端及其静态校验编排；不扩大到正式 Release、Action 运行、冷却、
限额、尽调、争议、远程调用、消息、调度、补偿或部署。完整设计见
[`../../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`](../../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)。

管理写入使用 Foundation `AbstractLockProcessor` 的显式 Redis 资源锁（`decision:*` Key，等待 500ms、租约 30s），本机 auto 验证除 MySQL 13306 外还需 local-environment 的 Redis 16379。通过 `RISK_TEST_REDIS_PASSWORD` 或 `MOR_REDIS_PASSWORD` 注入本机 Redis 凭据，测试键使用 `risk-auto` Namespace；不启动容器或自动迁移数据库。已有库必须先人工审核本需求 Risk DDL/DML，不能重跑全量初始化 SQL。

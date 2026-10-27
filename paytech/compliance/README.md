# compliance

- spring.application.name：`compliance-service`
- 端口：18112；管理端口 19112
- 数据库：`mor_compliance`（尽调、筛查与资格 24 表、结算资格控制 3 表、`t_compensation_event`）
- 来源现有工程：`risk-compliance-service` 的 `risk-compliance` 模块、`risk-web` 中的合规 Controller 与 `ClientReviewResultRabbitPublisher`（第 06 步拆出，design/07）

## 模块与边界

| 模块 | 职责 |
|---|---|
| `paytech-compliance-api` | 私网 `@HttpExchange` 合同（包 `com.paytech.compliance.api`）：程序解析（`ProgramResolutionApi`）、入驻审核登记 / 分类 / 撤回（`ClientReviewRegistrationApi`）、运营审核工作台与只读程序目录（`ClientReviewAdminApi`，`GET /client-onboarding-admin/v1/programs`）、客户可见结果（`ClientReviewResultApi`）、角色资格（`QualificationEvaluationApi`、`QualificationAdminApi`）、结算资格控制（`SettlementControlApi`，阶段 0 仅合同）；`event` 包为 `ClientReviewDecided` v2、`QualificationChanged` v1、`SettlementRestrictionChanged` 的 Payload |
| `paytech-compliance-core` | 程序解析、I01—I03、运营工作台命令、退回超期扫描、补偿 Handler `CLIENT_REVIEW_RESULT` / `QUALIFICATION_CHANGED` / `CASE_SCREENING`、出站端口（包 `com.paytech.compliance`） |
| `paytech-compliance-app` | 唯一可执行：`com.paytech.compliance.ComplianceApplication`、Controller、XXL 任务 `clientReviewReturnExpiryScan`、事件发布（`ClientReviewDecidedEventPublisher`、`QualificationChangedEventPublisher`）、筛查出站适配（channel-connector 服务渠道、client-master CM-13）、DataSource / `@MapperScan`、Snowflake（`compliance.identifier.*`）、Clock、补偿与 messaging 配置 |
| `paytech-compliance-test` | 原 risk-test 中 compliance 8 类整类搬入、`ComplianceArchitectureTest` 与 R1–R12 `ArchitectureAutoTest` |

- 调用方：client-master（程序解析、审核登记 / 分类 / 撤回）、Operations BFF（运营工作台）、Client BFF（审核结果）。审核入口与退回超期扫描由 `onboarding.review.enabled` 显式开启（默认关闭）；程序解析始终开启。不使用 Redis。
- 出站（2026-10-27）：`CASE_SCREENING` 补偿 Handler 在事务外经 client-master `GET /client-master/v1/party-profiles` 读取主体名称、经 channel-connector `POST /channel-connector/v1/service-executions`（`SCREENING_SEARCH`，渠道 `compliance.screening.channel-code`，缺省 `S_SCREENING_MOCK` 只在 local / mor-test 部署）执行筛查，结果另开短事务落库；名称只在内存使用。生产经服务发现，`internal-auth` 开启时按目标服务附加 Token。
- XXL-Job：`clientReviewReturnExpiryScan`（建议每 10 分钟、单实例）把补件期限已过的退回案件置 EXPIRED 并发布结果。
- 审核结论发布：补偿 Handler 调用 `ClientReviewDecidedEventPublisher`，经 `paytech-messaging` 的 `DomainEventPublisher` 向 `compliance.events` 发布 `ClientReviewDecided` Schema v2 信封（RETURNED / APPROVED / REJECTED / EXPIRED）（aggregateType `DD_CASE`、aggregateVersion `caseVersion`），client-master 经 `client-master.compliance.p0..p3` 消费。旧 `topic_risk_client_review` 双写于第 08 步删除；`messaging.publisher.unroutable-policy` 为 `FAIL`，`messaging.producer.sequencer.enabled` 使发布任务按审核主任务串行。
- 结算资格控制：`SettlementControlApi`（`POST /compliance/v1/settlement-controls`，幂等键 `sourceActionEffectId`；`GET /compliance/v1/settlement-controls?ownerType=&ownerId=&status=ACTIVE`）与三表 Schema 已归本服务，Controller、Processor 与 `SettlementRestrictionChanged` 发布尚未实现（D38）。
- `t_qualification_decision.decision_evaluation_id` 是对 risk-decision 的跨库 ID 引用，只存不联表（design/07 §3）。
- `internal-auth.mode` 默认 `"OFF"`。

## 构建与测试

在 `paytech/` 下执行（JDK 25）：

```sh
./mvnw -B verify -pl compliance/paytech-compliance-test -am -Dpaytech.local-environment.dir=<仓库>/local-environment
java -jar compliance/paytech-compliance-app/target/paytech-compliance-app-1.0.0-SNAPSHOT-exec.jar
```

存量库升级顺序：停服务并备份 → [`changes/2026-10-27-onboarding-global.sql`](docs/database/sql/changes/2026-10-27-onboarding-global.sql)（保留行，旧程序置 RETIRED）→ 初始化 DML（AE / SA / EG 程序，[`compliance-initial-data.sql`](docs/database/sql/compliance-initial-data.sql)，执行前设置 `@approved_by`）→ [`changes/2026-10-27-onboarding-global-cleanup.sql`](docs/database/sql/changes/2026-10-27-onboarding-global-cleanup.sql)（删除非 APPROVED 案件族，预检 0.4—0.6 非 0 时停止）→ 新镜像。测试只连接仓库 `local-environment` 的 `127.0.0.1:13306/mor_compliance`（账号 `compliance_app`）与本地 RabbitMQ `/mor-local`。空库由开发者用 MySQL root 人工执行一次 `paytech-compliance-test/src/test/resources/init/ddl.sql`（逐字切自 [`docs/database/sql/compliance-schema.sql`](docs/database/sql/compliance-schema.sql)，加 `t_consumed_event`）。PDManer 规范模型为 [`docs/database/compliance.pdma.json`](docs/database/compliance.pdma.json)。

## 材料要求

# Risk 工程

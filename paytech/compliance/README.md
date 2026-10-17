# compliance

- spring.application.name：`compliance-service`
- 端口：18112；管理端口 19112
- 数据库：`mor_compliance`（尽调与资格 20 表、结算资格控制 3 表、`t_compensation_event`）
- 来源现有工程：`risk-compliance-service` 的 `risk-compliance` 模块、`risk-web` 中的合规 Controller 与 `ClientReviewResultRabbitPublisher`（第 06 步拆出，design/07）

## 模块与边界

| 模块 | 职责 |
|---|---|
| `paytech-compliance-api` | 私网 `@HttpExchange` 合同（包 `com.paytech.compliance.api`）：入驻审核登记与分类（`ClientReviewRegistrationApi`）、运营审核（`ClientReviewAdminApi`）、客户可见结果（`ClientReviewResultApi`）、材料要求（`MaterialRequirementsApi`）、结算资格控制（`SettlementControlApi`，阶段 0 仅合同）；`event` 包为 `ClientReviewDecided`、`SettlementRestrictionChanged` 的 Payload；错误码 `ClientReviewErrorCode`、`MaterialQualificationType` |
| `paytech-compliance-core` | I01/I02 审核登记、运营审核命令、`CLIENT_REVIEW_RESULT` 补偿 Handler、材料要求目录（包 `com.paytech.compliance`） |
| `paytech-compliance-app` | 唯一可执行：`com.paytech.compliance.ComplianceApplication`、Controller、`ClientReviewResultRabbitPublisher`（双写）、DataSource / `@MapperScan`、Snowflake（`compliance.identifier.*`）、Clock、补偿与 messaging 配置 |
| `paytech-compliance-test` | 原 risk-test 中 compliance 8 类整类搬入、`ComplianceArchitectureTest` 与 R1–R12 `ArchitectureAutoTest` |

- 调用方：client-master（`compliance-service`：审核登记 / 分类、材料要求）、Operations BFF（运营审核）、Client BFF（审核结果、材料要求）。入口由 `onboarding.review.enabled`、`onboarding.materials.enabled` 显式开启，默认关闭。本服务没有出站 HTTP 客户端，不使用 Redis。
- 审核结论双写：补偿 Handler 调用 `ClientReviewResultRabbitPublisher`，先向旧 `topic_risk_client_review` / `client_review.decided.v1` 发布原报文（Exchange、路由键与报文不变，client-master 消费者第 08 步切换），再经 `paytech-messaging` 的 `DomainEventPublisher` 向 `compliance.events` 发布 `ClientReviewDecided` Schema v1 信封（aggregateType `DD_CASE`、aggregateVersion `caseVersion`）。`messaging.publisher.unroutable-policy` 暂为 `ACK_AND_WARN`，第 08 步接入订阅者后改回 `FAIL`。
- 结算资格控制：`SettlementControlApi`（`POST /compliance/v1/settlement-controls`，幂等键 `sourceActionEffectId`；`GET /compliance/v1/settlement-controls?ownerType=&ownerId=&status=ACTIVE`）与三表 Schema 已归本服务，Controller、Processor 与 `SettlementRestrictionChanged` 发布尚未实现（D38）。
- `t_qualification_decision.decision_evaluation_id` 是对 risk-decision 的跨库 ID 引用，只存不联表（design/07 §3）。
- `internal-auth.mode` 默认 `"OFF"`。

## 构建与测试

在 `paytech/` 下执行（JDK 25）：

```sh
./mvnw -B verify -pl compliance/paytech-compliance-test -am -Dpaytech.local-environment.dir=<仓库>/local-environment
java -jar compliance/paytech-compliance-app/target/paytech-compliance-app-1.0.0-SNAPSHOT-exec.jar
```

测试只连接仓库 `local-environment` 的 `127.0.0.1:13306/mor_compliance`（账号 `compliance_app`）与本地 RabbitMQ `/mor-local`。空库由开发者用 MySQL root 人工执行一次 `paytech-compliance-test/src/test/resources/init/ddl.sql`（逐字切自 [`docs/database/sql/compliance-schema.sql`](docs/database/sql/compliance-schema.sql)，加 `t_consumed_event`）。PDManer 规范模型为 [`docs/database/compliance.pdma.json`](docs/database/compliance.pdma.json)。

## 材料要求

# Risk 工程

# 第 06 步报告 · Risk → risk-decision + compliance（两库两 app；risk-api 一次性拆分，五个消费方同分支改）

- 分支：`refactor/step-06-risk`（起点 `refactor-main@3b6b7ac2`）。提交：`507e7e05` 纯 `git mv` 搬迁（内容未改，单独不可编译）；`7a77868b` 内容改写、测试、PDManer 与脚本；本报告单独提交。
- 结论：第 5 节 5 项均已真实执行，结果见第 2 节。risk-decision 1726 例、compliance 374 例，0 失败 0 跳过；五个消费方各自测试数与上一步报告相同且全部通过；全 Reactor `clean verify` BUILD SUCCESS（8111 例，0 失败）。

## 1. 改动摘要

1. **搬迁**：`risk-api` → `paytech-risk-decision-api`（decision 包平铺为 `com.paytech.risk.decision.api`，evaluation / control / enums / validation 为其子包）；compliance 包与 `ClientReviewErrorCode`、`MaterialQualificationType` → `paytech-compliance-api`（`com.paytech.compliance.api`）。`risk-decision` + `risk-control` → `paytech-risk-decision-core`（core 包名保留 `com.paytech.risk.decision` / `com.paytech.risk.control`，01 §8）；`risk-compliance` → `paytech-compliance-core`（包改为 `com.paytech.compliance`）。`risk-web` 的决策部分 → `paytech-risk-decision-app`（启动类改名 `RiskDecisionApplication`），合规 Controller、三个请求拦截配置、`ClientReviewResultRabbitPublisher` → `paytech-compliance-app`。`risk-test` 按 10 §2.4 拆为两个 test 模块。旧根 `risk-compliance-service/`（含 `mvnw`、`.mvn`）与 `risk-api` 坐标已删除，无聚合 POM。
2. **Reactor**：`paytech/pom.xml` 只取消 `risk-decision`、`compliance` 两行注释。两个服务聚合 POM 复制原 Risk Enforcer 禁令并追加 `com.paytech:*-core` / `*-app` 通配禁令（放行自身 core / app 与 `paytech-common-core`）。
3. **装配拆分（07 §5）**：两个 app 各自 DataSource、`@MapperScan`（decision+control / compliance）、TypeHandler（decision 43 个闭合枚举；compliance 只需 `InstantTypeHandler`）、Snowflake（`decision.identifier.*` / `compliance.identifier.*`）、`Clock`、补偿配置、`web.logging.metadata-only-path-prefixes`（7 / 3 条）、`internal-auth.mode: "OFF"`、管理端口 19104 / 19112。`onboarding.review|materials.enabled` 随 compliance。`RiskApiErrorConfiguration` 的审核分支原样移到 `ComplianceApiErrorConfiguration`。
4. **请求上下文**：删除 `RiskOperationRequestContextParser(Impl)` 与 `RiskOperationRequestContext`，9 个 Controller 改用 Foundation `OperationRequestContextParser`（访问器 `actorAccountId()` → `getActorAccountId()`）。
5. **MQ 双写**：`ClientReviewResultRabbitPublisher` 先按原样向 `topic_risk_client_review` / `client_review.decided.v1` 发布（Exchange、路由键、报文不变），再经 `DomainEventPublisher` 向 `compliance.events` 发布 `ClientReviewDecided` v1 信封；两份 Payload 由同一方法生成。`messaging.publisher.unroutable-policy: ACK_AND_WARN`，注释写明"第 08 步改回 FAIL"。
6. **事件目录**：`ClientReviewDecided` 的 aggregateType 由 `CLIENT_REVIEW_CASE` 改为 `DD_CASE`（与实现及旧报文 sourceType 一致），versionField `caseVersion` 核对无误；`SettlementRestrictionChanged` 的 versionField 由 `controlVersion` 改为 `aggregateVersion`（= `t_settle_control_state_change.aggregate_version`，满足 strictOrder）。
7. **compliance-api 新合同**：`SettlementControlApi` 与 5 个 DTO、`event` 包两个 Payload 类（第 3 节）；本步无 Controller / Processor。
8. **测试**：`RiskDecisionMockTestBase` / `ComplianceMockTestBase` 只导入本服务组件，补全局 `@BeforeEach` 空基线断言与 `@AfterEach` 补偿处理停止等待；`RiskDecisionDatabasePrecondition`（55 表，保留 t_rule_set 列、索引与 Action 目录校验，删除 t_command_idempotency 校验）与 `ComplianceDatabasePrecondition`（25 表）；两个全表清理器（表集合 = `init/ddl.sql`，decision 保留初始化目录行）；两份 `init/ddl.sql`（逐字切片 + `t_consumed_event`，decision 含初始化目录数据）；`RiskDecisionArchitectureTest`（原 11 例改写 + A7 表集合 1 例）、`ComplianceArchitectureTest` 12 例、两个 R1–R12 `ArchitectureAutoTest`、两份 `WebLoggingBoundaryAutoTest`；compliance 新增"新信封已发布到 compliance.events 且 Payload 与旧报文一致"断言。
9. **调用方名**：compliance-api 的 `ClientReviewRegistrationApi` JavaDoc 与 4 个测试类常量 `client-contract-service` → `client-master-service`（第 04 报告第 5 节第 1 项）。
10. **五个消费方**：只改 Maven 坐标、import 包名、发现名（及描述发现名的 README 行）。详见 4.1。
11. **PDManer / 脚本 / 环境**：模型与 Schema 按归属拆为 risk-decision（54 表）、compliance（24 表）、dispute（8 表，`paytech/dispute/docs/database/`）；生成器拆为三入口；9 个 pdmaner 测试按新路径改写（4 个改名）；`initialize.py`、`test_risk_database_initialization.py` 改为两库；local-environment 初始化脚本、`.env.example`、README、`verify-local-environment.mjs` 增加两库两账号（可选创建写法，compose 未改）。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并从主工作区 `local-environment/.env.local` 导出 `MOR_RABBITMQ_USER/PASSWORD/VHOST`、`MOR_REDIS_PASSWORD`、`PAYTECH_REDIS_PASSWORD`（取 `MOR_REDIS_PASSWORD`），全部经 source 脚本注入，未打印；没有 up / down / restart 任何容器。

### 2.1 测试数与全 Reactor

**搬迁前基线**：`git archive 3b6b7ac2 risk-compliance-service` 导出到 scratchpad，执行 `./mvnw -B verify`（库 `mor_risk`）。Surefire 执行 **2083** 例、0 跳过，源码方法数 **738**（与 design/10 一致）。其中 **55 例失败**，全部是 `FlowSaveApiAutoTest` 48、`FlowListApiAutoTest` 5、`FlowValidateApiAutoTest` 2 的"流程总数 expected 0 but was 1"：共享 `mor_risk` 中残留一行 `t_decision_flow`（`CHECKOUT_BASELINE_ALLOW`，2026-10-17 07:01:52 UTC 创建，来源不明）。这是环境残留而非代码失败；本步未清理该库。

**本步（新库 `mor_risk_decision` / `mor_compliance`）**：

```bash
cd paytech && ./mvnw -B -o verify -pl risk-decision/paytech-risk-decision-test -am \
  '-Dtest=com/paytech/risk/auto/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=<主工作区>/local-environment
./mvnw -B -o verify -pl compliance/paytech-compliance-test -am \
  '-Dtest=com/paytech/compliance/auto/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…
```

两次均 BUILD SUCCESS。

| 模块 | `<testcase>` | 失败 / 跳过 | 测试类 | 源码方法 | 方法下限（10 §1.2） |
|---|---:|---:|---:|---:|---:|
| paytech-risk-decision-test | 1726 | 0 / 0 | 56 | 643 | 641 |
| paytech-compliance-test | 374 | 0 / 0 | 11 | 113 | 97 |
| 合计 | **2100**（基线 2083） | 0 / 0 | 67 | **756**（基线 738） | 738 |

类映射：其余 61 个整类搬入的类执行数与基线逐类相同（脚本按类名比对 Surefire 报告）。差异只有服务级类：

| 旧类 | 新类（服务） | 旧 → 新 |
|---|---|---|
| `RiskModuleBoundaryAutoTest` | `RiskDecisionArchitectureTest`（risk-decision） | 11 → 12（11 个同名方法按新结构改写，加 A7 表集合 / Mapper 表名 1 例） |
| — | `ComplianceArchitectureTest`（compliance） | 新增 12 |
| `WebLoggingBoundaryAutoTest` | 两份同名（risk-decision / compliance） | 12 → 9 + 5（各 1+N+1，N = 本服务前缀数 7 / 3） |
| — | 两个 `ArchitectureAutoTest` | 新增 1 + 1（R1–R12，含 R4，0 违规，无 known-deviations） |
| `ClientReviewDecisionApiAutoTest` | 同名（compliance） | 64 → 64（成功路径增加 compliance.events 信封断言，方法数不变） |

**全 Reactor**：`cd paytech && ./mvnw -B clean verify -Dpaytech.local-environment.dir=…`（另导出 Vault 端到端变量 `VAULT_TEST_SERVER_BINARY`、`VAULT_TEST_JDBC_URL/USER/PASSWORD`、`VAULT_TEST_DB_DSN`，二进制用 `go1.27.1 build ./cmd/vault-server` 构建在 scratchpad）。结果：**BUILD SUCCESS**（18:05 min，117 个 Reactor 项目）。按 Surefire 报告计数，40 个测试模块共 **8111** 例，0 失败、0 错误、0 跳过；其中 risk-decision 1726、compliance 374、paytech-messaging 55（事件目录修改后）、arch-rules 57、arch-rules-reactor-test 3。运行前 `ps` 确认没有其他 Maven 测试 JVM；验证后 Vault 二进制留在 scratchpad。

### 2.2 五个消费方

命令：`./mvnw -B -o verify -pl client-master/paytech-client-master-test,acquiring/paytech-acquiring-test,payment/paytech-payment-test,operations-console-bff/paytech-operations-console-bff-test,client-console-bff/paytech-client-console-bff-test -am '-Dtest=<五个服务的 **/auto/**/*Test>' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…`，BUILD SUCCESS（5:34 min）。

| 消费方 | 本步 | 上一步报告 | 失败 / 跳过 |
|---|---:|---:|---:|
| paytech-client-master-test | 57 | 57（第 04 步） | 0 / 0 |
| paytech-acquiring-test | 801 | 801（第 03 步） | 0 / 0 |
| paytech-payment-test | 72 | 72（第 05 步） | 0 / 0 |
| paytech-operations-console-bff-test | 1390 | 1390（第 02c / 04 步） | 0 / 0 |
| paytech-client-console-bff-test | 80 | 80（第 02c / 04 步） | 0 / 0 |

client-master `ClientReviewResultListenerTest` 仍从旧 Topic 收报文，断言未改且通过。

### 2.3 两个 app 以 mock Profile 启动

```bash
java -jar paytech-risk-decision-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:<risk-decision-test>/application-mock.yml --server.port=28104 --management.server.port=29104
java -jar paytech-compliance-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:<compliance-test>/application-mock.yml --server.port=28112 --management.server.port=29112
```

`Started RiskDecisionApplication in 3.246 s`、`Started ComplianceApplication in 2.327 s`，日志 ERROR 0 行。两个管理端口 `/actuator/health` 均为 `{"groups":["liveness","readiness"],"status":"UP"}`，liveness / readiness 均 200；业务端口 `/actuator/health` 为 404（Actuator 只在管理端口）。验证后进程已停止，复制的 jar 在 scratchpad。

### 2.4 Node 与 Python

`node --test scripts/pdmaner/risk-*.test.mjs`（改名后）：189 项，166 通过，23 失败；起点同口径 188 项，163 通过，25 失败。起点通过的测试没有一个变为失败。

| 文件（改名后） | 起点 | 现在 | 仍失败的测试（与起点同名） |
|---|---|---|---|
| risk-service-model（原 risk-compliance-model） | 18/24 | 19/25 | is current…、totals and structural digest、exact approved table set…、identifiers, metadata…、critical … fields、token policy… |
| risk-service-schema（原 risk-compliance-schema） | 4/6 | 6/6 | — |
| risk-service-scenario-doc | 12/14 | 12/14 | cover all 74 physical tables…、route current statistics… |
| risk-decision-initial-data | 0/1 | 0/1 | golden-vector 文件在起点即不存在 |
| risk-decision-engine-contract | 86/96 | 86/96 | 同起点 10 项 |
| risk-limit-balanced-simplification | 3/5 | 3/5 | 同起点 2 项 |
| risk-settlement-eligibility-balanced-simplification | 15/17 | 15/17 | 同起点 2 项 |
| action-effect、dispute ×3、variable | 全通过 | 全通过 | — |
| client-onboarding-model / pdmaner-canonical-paths | 3/3、2/2 | 3/3、2/2 | — |

为新结构改写的断言：模型 "diagrams draw each local relation once" 主表数 84 → 83（t_command_idempotency 删除）；"is current" 改跑三个入口 `--check`；新增每表恰属一个服务、结算控制归 compliance、4 条跨库关系不再建模而 ID 列保留；Schema 测试按服务执行，"command idempotency key preserves case-sensitive identity" 改为"command idempotency table deleted (S10)"。

其他检查：`generate-compliance-model.mjs --check`、`generate-dispute-model.mjs --check` up to date；`generate-risk-decision-model.mjs --check` 报 stale（起点生成器即过期：manifest 03 缺 `t_evaluation_flow_trace`；已提交模型按 step 05 先例从已提交模型分区）；`generate-risk-compliance-schema.mjs --check` 起点失败，现在三份 Schema 均 up to date。三份切片共 86 个 CREATE TABLE（84 张不同表，`t_compensation_event` 每库一份）与原 Schema 逐字节一致。

`cd test-environment/scripts && python3 -m unittest test_risk_database_initialization`：8 例，起点（`git archive` 导出）3 errors，现在同样 3 errors，原因相同（`load_risk_catalog` 仍断言 71 表 / 4 条 DML，起点即过期）。`node scripts/verify-local-environment.mjs` 前后输出逐字相同，只有 4 行既有 Gateway 文档引用失败。

### 2.5 历史、旧根与包名

- `git log --follow paytech/risk-decision/paytech-risk-decision-core/…/domain/runtime/DecisionEngine.java`：`507e7e05`、`991e9324`、`a55b3b17`、`4f50b8d1`、`e944be32` …
- `git log --follow paytech/compliance/paytech-compliance-core/…/domainservice/service/impl/ClientReviewServiceImpl.java`：`507e7e05`、`9b0ef160`。
- 并入的 control：`ManualPayoutEligibilityProcessor.java` 追溯到 `29b588bc`。
- `ls risk-compliance-service` 不存在，`git ls-files risk-compliance-service` 0 行；`paytech/` 下无 `risk-api` 目录；`grep -r "com.paytech.risk.api" paytech/ --exclude-dir=target` 为 0。

### 2.6 R4

`node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check`：`table-ownership.yaml is current (265 physical tables + 56 retained).`（白名单早已是 risk-decision 54 / compliance 24，本步未改）。两个服务 `ArchitectureAutoTest` R1–R12 0 违规；两个架构测试另断言 core Mapper XML 引用的表 ⊆ 本服务 `init/ddl.sql`。

### 2.7 本机测试库（root，运行中的 `mor-local-mysql-1`）

2026-10-17T21:43:31Z 执行（root 密码取自 `.env.local` 的 `MOR_MYSQL_ROOT_PASSWORD`，经 `docker exec -e MYSQL_PWD` 传入，未出现在命令文本或输出中）。执行前确认 `mor_risk_decision`、`mor_compliance` 与两个账号都不存在。

```bash
MYSQL_PWD="$(grep -E '^MOR_MYSQL_ROOT_PASSWORD=' …/local-environment/.env.local | cut -d= -f2- | tr -d '"')" \
  docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql --default-character-set=utf8mb4 -uroot \
  < paytech/risk-decision/paytech-risk-decision-test/src/test/resources/init/ddl.sql
# compliance 同一写法，输入 paytech/compliance/paytech-compliance-test/src/test/resources/init/ddl.sql
```

- 内容：`CREATE DATABASE IF NOT EXISTS`、`CREATE USER IF NOT EXISTS`（仓库默认密码 `local-risk-decision-change-me-2026` / `local-compliance-change-me-2026`）、`GRANT SELECT, INSERT, UPDATE, DELETE`、全部 `CREATE TABLE`；decision 另含初始化目录 INSERT。
- 核对：`mor_risk_decision` 55 表（`t_action_version` 8 行、`t_risk_variable` 33 行），`mor_compliance` 25 表；`SHOW GRANTS` 两个账号都只有本库四项权限。
- `mor_risk` 未改动、未删除（也未清理上述残留行）。没有执行任何业务库 SQL。

## 3. 两个 api 的合同清单

### 3.1 `paytech-risk-decision-api`（发现名 `risk-decision-service`，无 context path）

| 接口（包） | 路径 | 调用方 |
|---|---|---|
| `DecisionRuleSetManagementApi`、`DecisionConfigurationQueryApi`（`api`） | `/decision-rule-sets/v1`：authoring-context、rule-sets 列表 / 详情 / 创建 / freeze / 删除、versions、release-references | Operations BFF |
| `DecisionReleaseManagementApi`、`ExecutionReleaseManagementApi` | `/decision-releases/v1`：candidates（列表、详情、创建、validate）、executions（创建、列表、详情、validate、shadow-runs、activate） | Operations BFF |
| `DecisionFlowQueryApi`、`DecisionFlowManagementApi` | `/decision-flows/v1`：authoring-context、flows、versions、rule-set-reference、legacy-point-versions；保存、validate、删除 | Operations BFF |
| `DecisionDictionaryApi` | `/decision-dictionaries/v1`：rejection-reasons、subject-tags（查询、写入、状态、references） | Operations BFF |
| `RiskVariableApi` | `/decision-variables/v1`：authoring-context、variables 增删改查、members、references | Operations BFF |
| `RiskDecisionApi`（`api.evaluation`） | `POST /risk-decisions/v1/evaluations` | acquiring（CHECKOUT_PREPARE）、payment（PAYMENT_ATTEMPT_RISK） |
| `RiskEvaluationQueryApi`（`api.evaluation`） | `GET /risk-decisions/v1/evaluations`、`/{id}`、`/{id}/explanation` | Operations BFF |
| `ManualPayoutEligibilityApi`（`api.evaluation`） | `POST /manual-payout-eligibility/v1/evaluations`（校验后恒 503 Fail-Closed） | 将来 payout |
| `RiskListManagementApi`（`api.control`） | `/risk-lists/v1/lists`、entries、deactivate | Operations BFF |
| 枚举 `RiskDecisionConfigurationErrorCode`、`RiskDecisionEvaluationErrorCode`、`RiskVariableErrorCode`（`api.enums`）；`DictionaryText(Validator)`（`api.validation`） | — | — |

路径、DTO 与错误码 Code 值均未变，只改了包名与坐标。

### 3.2 `paytech-compliance-api`（发现名 `compliance-service`，无 context path）

| 接口 | 路径 | 调用方 |
|---|---|---|
| `ClientReviewRegistrationApi` | `POST /client-onboarding-review/v1/submissions`、`POST …/cases/{caseId}/classifications`（`X-Caller-Service` 固定 `client-master-service`，运行期仍不校验，与原实现一致） | client-master |
| `ClientReviewResultApi` | `GET /client-onboarding-review/v1/applications/{applicationId}/result` | Client BFF |
| `ClientReviewAdminApi` | `/client-onboarding-admin/v1`：cases 列表 / 详情、evidence review、return、approval、rejection | Operations BFF |
| `MaterialRequirementsApi` | `GET /internal/due-diligence/material-requirements` | client-master、Client BFF |
| **新增** `SettlementControlApi` | `POST /compliance/v1/settlement-controls`（Body `CreateSettlementControlRequestBody`：sourceActionEffectId〔幂等键〕、roleType、roleId、controlType、reasonCode、effectiveFrom、effectiveTo、requiredAmount、requiredCurrency、releaseConditionCode、controlInstanceKey、contentHash、scopes[operatingEntityId、currency、bankCountry、scopeHash]）→ `SettlementControlResponseBody`（t_settlement_control 全列 + scopes）；`GET /compliance/v1/settlement-controls?ownerType=&ownerId=&status=` → `SettlementControlListResponseBody{items}` | 将来 risk-decision（本步无实现） |
| `event.ClientReviewDecidedPayload` | Schema v1，FIELDS 与旧报文 payload 同序；aggregateType `DD_CASE` | client-master（第 08 步） |
| `event.SettlementRestrictionChangedPayload` | Schema v1：t_settlement_control 关键列 + 一条 t_settle_control_state_change；aggregateType `SETTLEMENT_CONTROL`，aggregateVersion = state_change.aggregate_version | payout、account（将来） |
| 枚举 `ClientReviewErrorCode`、`MaterialQualificationType` | — | — |

## 4. 消费方改动与 compose / build.sh 待办

### 4.1 五个消费方的改动范围

| 消费方 | 坐标 | 发现名 | 其他 |
|---|---|---|---|
| client-master | core：`paytech-compliance-api` | `ClientSubmissionConfiguration`、`ClientDocumentConfiguration` 与测试 simple discovery：`risk-service` → `compliance-service` | — |
| acquiring | 聚合 POM dependencyManagement、core、app、test：`paytech-risk-decision-api` | `RiskClientConfiguration.SERVICE_ID`、`CheckoutRiskPortImpl.RISK_SERVICE_ID`、`application.yml` 与测试 yml 的 serviceclient 组和 simple discovery → `risk-decision-service` | README 一行 |
| payment | 聚合 POM、app：`paytech-risk-decision-api` | `PaymentOutboundConfiguration.RISK_SERVICE`、Properties JavaDoc、测试 yml → `risk-decision-service` | `docs/development/configuration.md` 一行 |
| operations-console-bff | 聚合 POM、app、test：`paytech-risk-decision-api` + `paytech-compliance-api` | `RiskClientConfiguration`、`RiskDiscoveryInterceptor` → `risk-decision-service`；`ClientReviewClientConfiguration` → `compliance-service`；两个测试类的发现名常量 | 见第 5 节第 3 项；README 三行 |
| client-console-bff | 聚合 POM、app：`paytech-compliance-api` | `ClientOnboardingConfiguration` → `compliance-service` | README 一行 |

### 4.2 compose / build.sh 待办（本步未改，交主会话）

1. `test-environment/compose.applications.yaml:68–74`：`risk-service` 拆为 `risk-decision-service`（18104 / 19104，`RISK_DB_*` 指向 `mor_risk_decision`、账号 `risk_decision_app`，不再需要 RabbitMQ 变量）与 `compliance-service`（18112 / 19112，`COMPLIANCE_DB_*` 指向 `mor_compliance`、`COMPLIANCE_RABBITMQ_*`、`COMPLIANCE_SNOWFLAKE_REGION_ID/WORKER_ID`）；第 314 行的 `depends_on` 同步；健康检查改到管理端口 `/actuator/health`。
2. `test-environment/scripts/build.sh:56–58`：仍执行 `cd risk-compliance-service && ./mvnw -pl risk-web …` 并复制 `risk-web-…-exec.jar`，**本步合入后会失败**。按 09 §3.1 改为单 Reactor 构建 `risk-decision/paytech-risk-decision-app,compliance/paytech-compliance-app`，产出 `risk-decision.jar` / `compliance.jar`；第 72 行 `compose.sh build` 的服务名同步；`test_deploy_script.py:768,830,844` 的断言、`deploy.sh:29,42` 镜像清单同步。
3. `test-environment/scripts/risk_rule_v1_configuration_smoke.py:27` 仍查询 `mor_risk`；`risk_rule_configuration_smoke.py:2760` 探测 Nacos `risk-service`。
4. `local-environment/compose.yaml` 与 test-environment compose 的 `mysql.environment` 需透传 `MOR_RISK_DECISION_DB_PASSWORD`、`MOR_COMPLIANCE_DB_PASSWORD`；在此之前全新初始化的容器会跳过这两个库（可选创建），本机已手工建好。
5. Nacos 配置 / 注册：新增 `risk-decision-service.yaml`、`compliance-service.yaml`；旧 `risk-service` 不保留别名。
6. Gateway / Platform：Operations 的 Risk 管理路由若经 Platform Target 登记 `risk-service`，需改为 `risk-decision-service` / `compliance-service`（本步未核对 Platform 数据）。

## 5. 与 07 文档及任务卡的偏差

1. **decision 包平铺**：按 07 §4 字面 `com.paytech.risk.api.decision.*` → `com.paytech.risk.decision.api.*`，原 evaluation / control / enums / validation 成为其子包。Operations BFF 有两处按包名前缀判断 Owner 类型的代码（`RiskVariableResponseShape.ownerContract` 与 `RiskClientConfigurationAutoTest` / `RiskDecisionConfigurationClientAutoTest` 的包扫描），平铺后前缀含义变宽或失效；已改为与原语义逐项等价的判断（排除同根的四个子包；扫描同时覆盖 decision 与 compliance 两个 Owner 包）。这超出"只改 import"的字面范围，但不改变行为。
2. **risk-decision 不再依赖 AMQP**：审核结果发布随 compliance 拆走后 decision 不发布也不消费 MQ，`spring-boot-starter-amqp` 与 rabbitmq 配置已删除，架构测试断言 `RabbitTemplate` 不在 classpath（A10）。10 §1.2 写的"RabbitMQ（补偿 DECISION_SHADOW_RUN）"不成立：补偿事件不经 MQ。
3. **compliance 不使用 Redis**：compliance 代码无锁与缓存，未引入 `paytech-redis-starter`，也未复制 `RiskRedisCleaner`（10 §1.2 / §3.1 写有副本）；前置检查不再校验 Redis。
4. **双写放在 app 发布实现中**：第 05 步把新信封放在 core 补偿 Handler；本步放在 `ClientReviewResultRabbitPublisher`（core 端口的 app 实现）中，core 的 `ClientReviewResultHandler` 未改，失败重试语义相同。
5. **表数**：decision 54 张（53 + `t_compensation_event`）、compliance 24 张，按 03 §4.2 与任务卡；10 §3.1 / §4.3 写的 decision 61 / 64（含争议 7 表）与 compliance 21 已被任务卡（争议归 `mor_dispute`、结算控制归 compliance）取代。测试库再各加 `t_consumed_event`，故前置检查为 55 / 25。
6. **`t_command_idempotency`** 不进入任何模型、Schema 切片、DDL 或清理器（03 §2.2 "删除，待 Owner 确认"），三份 PHYSICAL_DECISIONS 第 0 节记录。
7. **全表清理器**：除各 Mocker 定向清理外，新增 `RiskDecisionDataCleaner` / `ComplianceDataCleaner` 在每例结束删除本库全部测试数据（decision 只删除目录表中非初始化主键的行），与第 04 步同口径；全局 BeforeEach 以此断言空基线。
8. **Snowflake / 端口 / 前缀**：compliance 读 `compliance.identifier.*`（`COMPLIANCE_SNOWFLAKE_*`）；decision 保留 `decision.identifier.*` 与 `RISK_*` 环境变量名，以免影响现有部署描述。两个 app 启用独立管理端口 19104 / 19112（原 Risk 与业务端口共用）。测试 Redis 前缀用固定 `risk-decision-test`，未用 `${random.uuid}`（清理器与 Starter 各自解析会得到不同值）。
9. **Manifest 06 归 compliance**：任务卡写法，与 07 §7 / 09 §5.2"06 → risk-decision"不同，以任务卡为准。PDManer 由已提交模型分区（生成器起点即过期，同第 05 步）。
10. **`platform-support-api` 旧坐标**：team-lead 消息要求把"Risk 依赖的 `platform-support-api` 旧坐标"改为 `paytech-platform-support-api`。核对后 Risk 从未依赖它；仍使用旧坐标的是 Gateway 与两个 BFF（非 Risk 坐标，不在本步允许的改动内），未改。

## 6. 待确认

1. 第 08 步必须把 compliance 的 `messaging.publisher.unroutable-policy` 改回 `FAIL`，并把 client-master 消费者切到 `compliance.events`；旧 Topic 停发时机由第 08 步定。
2. 事件目录两处修正（`ClientReviewDecided` aggregateType `DD_CASE`；`SettlementRestrictionChanged` versionField `aggregateVersion`）需业务 Owner 确认。
3. `SettlementControlApi` 的查询参数沿用任务卡的 `ownerType` / `ownerId`，DTO 字段沿用表列 `roleType` / `roleId`；`status` 定为必填。Controller、Processor、事件发布与 risk-decision 调用方由后续切片实现。
4. 共享 `mor_risk` 残留的 `t_decision_flow` 行导致旧 Risk 基线 55 例失败；是否清理、以及 `mor_risk` / `risk_app` 的删除时机，由用户决定。
5. 生产 / 部署需分配 compliance 独立的 Snowflake worker、Nacos 注册与新健康检查地址（管理端口）。
6. compliance 未接 Redis、decision 未接 AMQP（第 5 节第 2、3 项）是否认可。
7. 未覆盖：`agent-docs/**` 中指向旧 Risk 路径的入向链接（第 13 步）；`test_risk_database_initialization` 的 3 个既有错误；`generate-risk-decision-model.mjs --check` 的既有过期；golden-vector 夹具缺失。

# Risk & Compliance 服务边界盘点（risk-decision / compliance / dispute）

- 范围：`/Users/kenny/Desktop/Workspace/Code-MoR-refactor/risk-compliance-service`，只读盘点，未修改任何文件。
- 日期：2026-10-17。
- 结论先行：代码层已按 decision / control / compliance 三个 Maven 模块划分，三者之间除 `decision → control` 外零 import；Mapper SQL 零跨模块；事务零跨核心。真正的耦合在 **共享数据库与 `t_compensation_event`**、**`risk-web` 共享装配**、**单一 `risk-api` JAR（5 个外部服务消费）**，以及 **4 条从 compliance/dispute 指向 decision 表的模型关系**。Dispute 与 Settlement Control 只有 Schema，没有任何 Java/Mapper 实现。
- 核心标签说明：`D` = risk-decision，`C` = compliance，`X` = dispute，`S` = shared/unclear。settlement control、`t_risk_restriction`、`t_settlement_risk_assessment` 的归属是本盘点提出的建议，不是代码事实。

## 1. Maven 模块布局

| 模块 | 职责 | 内部依赖 / 关键外部依赖 | 可执行 |
|---|---|---|---|
| `risk-api` | HTTP 合同（`@HttpExchange` 接口 + Request/Response）。包：`api.compliance`、`api.control`、`api.decision`、`api.evaluation`、`api.enums`、`api.validation` | paytech-common-core、spring-web、jakarta.validation-api | 否 |
| `risk-decision` | 规则集、决策流、发布/影子、变量、字典、结账/支付运行期评估、Velocity、评估查询 | **risk-control**、paytech-json-canonicalization、paytech-compensation-event、paytech-mybatis-support、paytech-redis-starter、mybatis、spring-tx | 否 |
| `risk-control` | 风险名单（黑名单）维护与命中；Manual Payout 资格评估 Processor（桩） | paytech-redis-starter、mybatis、spring-tx | 否 |
| `risk-compliance` | I01 Client 入驻审核登记、审核命令、结果发布；材料要求目录 | paytech-compensation-event、mybatis、spring-tx、jackson | 否 |
| `risk-web` | 唯一可部署：`com.paytech.risk.RiskApplication`（`risk-web/src/main/java/com/paytech/risk/RiskApplication.java`），端口 18104，spring-boot-maven-plugin classifier `exec`。Controllers、MQ Publisher、全部共享配置 | risk-api、risk-decision、risk-control、risk-compliance、paytech-mvc-web-starter、paytech-snowflake-id、paytech-redis-starter、paytech-compensation-event、amqp、mybatis-spring-boot-starter、mysql、nacos-discovery | 是 |
| `risk-test` | 全部测试（`maven.install.skip`/`deploy.skip`=true） | risk-web、paytech-test-support、spring-jdbc(test) | 否 |

源码规模（main，不含测试）：decision 约 430 个 Java 文件（48 DataObject、23 Mapper、39 Repository、54 Processor），compliance 约 85，control 约 35，risk-web 48，risk-api 约 120。

已有护栏：`risk-test/src/test/java/com/paytech/risk/auto/RiskModuleBoundaryAutoTest.java` 固定 reactor 模块集合、每个业务模块的精确依赖集合、`@MapperScan` 三个包、enforcer 禁用清单、只有 web 可执行。任何拆分都必须同步修改该测试。

数据库模型：`docs/database/risk-compliance.pdma.json`，由 `../scripts/pdmaner/risk/generate-risk-compliance-model.mjs` 从 10 个 Manifest 生成：`01-due-diligence`、`02-decision-engine`、`03-evaluation`、`04-risk-data`、`05-limits`、`06-settlement`、`07-dispute`、`08-reliability`、`09-decision-flow`、`10-variables`。Schema：`docs/database/sql/risk-compliance-schema.sql`（85 表）；初始化：`risk-compliance-initial-data.sql`（只含 `t_decision_point`、`t_decision_point_version`、`t_action_version`、`t_action_parameter_definition`，即 CHECKOUT_PREPARE / PAYMENT_ATTEMPT_RISK）；`2026-09-11-risk-rule-v1-configuration.sql`（action 目录）。Compliance 材料程序种子在服务外：`requirements/20260914/client-console-注册与客户入驻/db/implementation/risk-generic-materials-init.sql`。

PDManer 图组（与拆分高度对应）：尽调与资格(19 主表)、决策点与规则发布、评估与 Action(8)、冷却/名单与风险数据(6)、限额与占用(7)、结算资格控制(3)、争议与举证(7)、可靠执行(2)。

## 2. 表清单与建议归属（85 表）

`*` = 没有任何生产 Mapper 引用（仅 Schema）。

### 2.1 compliance（20 + 3 待定）

| 表 | 实现 | 说明 |
|---|---|---|
| `t_dd_program` / `t_dd_program_version` / `t_dd_program_requirement` | 有 | 材料程序目录（MaterialRequirementsMapper） |
| `t_dd_program_check`* / `t_dd_program_screening`* | 无 | 程序检查/筛查模板 |
| `t_due_diligence_case` / `t_dd_case_subject` / `t_dd_case_classification` / `t_dd_case_dependency` / `t_dd_requirement` / `t_dd_evidence_ref` / `t_dd_case_state_change` | 有 | I01 审核案件聚合 |
| `t_dd_party_snapshot`* / `t_dd_check`* | 无 | Party 快照、检查结果 |
| `t_screening_run`* / `t_screening_hit`* / `t_screening_resolution`* | 无 | 制裁/PEP 筛查 |
| `t_qualification_decision` | 有 | 资格决定；含 `decision_evaluation_id`（指向 decision） |
| `t_role_qualification`* / `t_qualification_state_change`* | 无 | 资格投影 |
| **待定** `t_settlement_control`* / `t_settlement_control_scope`* / `t_settle_control_state_change`* | 无 | 控制类型 `SETTLEMENT_BLOCK`、`MIN_SECURITY_DEPOSIT`，符合"结算限制/最低保证金"→compliance；但 `source_action_effect_id` 1:1 来自 decision 的 `t_action_effect`，且 payout 检查点需读取它。见热点 #1 |

### 2.2 risk-decision（53，含原 risk-control 的名单）

规则配置（31，全部有实现）：`t_rule_reject_reason`、`t_rule_subject_tag`、`t_decision_point`、`t_decision_point_version`、`t_decision_point_input_field`、`t_decision_point_feature`、`t_decision_point_rule_set`、`t_decision_point_obligation`、`t_feature_version`、`t_feature_dependency`、`t_velocity_feature_definition`、`t_list_feature_definition`、`t_rule_set`、`t_rule_set_version`、`t_risk_rule`、`t_rule_node`、`t_rule_operand`、`t_action_version`、`t_action_parameter_definition`、`t_rule_action`、`t_rule_action_parameter`、`t_decision_release`、`t_release_audit_event`、`t_release_shadow_run`、`t_decision_flow`、`t_decision_flow_version`、`t_decision_flow_node`、`t_decision_flow_edge`、`t_decision_flow_end_output`、`t_risk_variable`、`t_risk_variable_member`。

评估与运行期（22）：

| 表 | 实现 | 说明 |
|---|---|---|
| `t_decision_evaluation`、`t_evaluation_feature_result`、`t_evaluation_rule_trace`、`t_evaluation_flow_trace` | 有 | 评估事实与解释 |
| `t_evaluation_action_intent`*、`t_action_effect`* | 无 | Action 合并与副作用；被 compliance/dispute 引用 |
| `t_checkout_input_snapshot`、`t_transaction_risk_assessment` | 有 | 结账点 / Payment Attempt 评估快照 |
| `t_settlement_risk_assessment`* | 无（测试有 TestMapper） | Manual Payout 四检查点快照，挂 `evaluation_id`；建议 decision（payout checkpoint evaluation） |
| `t_risk_restriction`* | 无 | 消费者/IP/Email/Phone 冷却与阻断，来源 `source_effect_id`；建议 decision（反欺诈） |
| `t_risk_list`、`t_risk_list_entry` | 有（risk-control） | 名单；建议并入 decision |
| `t_payment_attempt_fact`* | 无 | Payment Attempt 投影 |
| `t_velocity_event`、`t_velocity_event_subject` | 有 | 实时累计 |
| `t_limit_version`*、`t_decision_point_limit_binding`*、`t_limit_position`*、`t_limit_reservation_group`*、`t_limit_reservation`*、`t_limit_group_state_change`*、`t_limit_movement`* | 无 | 限额与占用 |

### 2.3 dispute（7，全部仅 Schema）

`t_dispute_case`、`t_dispute_state_change`、`t_dispute_evidence_package`、`t_dispute_evidence_ref`、`t_dispute_submission_request`、`t_dispute_outcome`、`t_dispute_liability_decision`（含 `decision_evaluation_id` + `action_effect_id`）。

### 2.4 shared / unclear（2）

| 表 | 说明 |
|---|---|
| `t_compensation_event` | 两核心都写（`CLIENT_REVIEW_RESULT`、`DECISION_SHADOW_RUN`），拆库后每库各一份 |
| `t_command_idempotency` | 代码零使用；按 2026-09-17 Gateway 请求幂等决定，大概率可删除（需 Owner 确认） |

## 3. 跨核心 SQL

- **生产 Mapper XML：零。** 34 个 XML（`risk-decision/src/main/resources/mapper/decision/*`、`risk-control/.../mapper/control/*`、`risk-compliance/.../mapper/compliance/*`）只引用本模块表；无注解 SQL（`@Select` 等为 0）。
  - 例：`DecisionRuleSetMapper.xml`、`DecisionConfigurationQueryMapper.xml`、`RiskVariableReferenceMapper.xml`、`ExecutionReleaseQueryMapper.xml` 是 decision 内部大 JOIN，但全部是 decision 表。
  - Compliance 最大的是 `CaseRequirementMapper.xml`（`t_dd_program_requirement` + `t_dd_requirement`）、`MaterialRequirementsMapper.xml`、`EvidenceRefMapper.xml`。
- **测试 SQL：一处跨"共享表"。** `risk-test/.../test/mapper/compliance/ClientReviewTestMapper.java` 第 256 行：`t_compensation_event e JOIN t_due_diligence_case c ON e.event_key=CAST(c.case_id AS CHAR)`。
- **PDManer 模型中的跨核心关系（逻辑关系，无物理 FK；拆库后变跨库引用）**，共 135 条关系中 4 条：

| 来源 | 目标 | 基数 |
|---|---|---|
| C `t_qualification_decision.decision_evaluation_id` | D `t_decision_evaluation.evaluation_id` | n:1 |
| C(待定) `t_settlement_control.source_action_effect_id` | D `t_action_effect.effect_id` | 1:1 |
| X `t_dispute_liability_decision.decision_evaluation_id` | D `t_decision_evaluation.evaluation_id` | n:1 |
| X `t_dispute_liability_decision.action_effect_id` | D `t_action_effect.effect_id` | n:1 |

- **无类型约束的触发引用：** `t_settle_control_state_change.trigger_type` 允许 `ACTION_EFFECT`（`trigger_id` 即 decision effect id）；`t_dd_case_state_change.trigger_type` 为受控目录值，可能同样引用 decision。

## 4. 跨核心事务

全仓无 `@Transactional`；全部为 `new TransactionTemplate(transactionManager)`，共享同一个按类型注入的 `PlatformTransactionManager`。**没有任何事务写入两个核心的业务表**；唯一共写对象是 `t_compensation_event`。

| 类#方法 | 写入 | 核心 |
|---|---|---|
| `risk-compliance/.../domainservice/service/impl/ClientReviewServiceImpl#register` | case、subject、requirement、evidence、state_change、dependency | C |
| `ClientReviewServiceImpl#reviewEvidence` / `#saveClassifications` | case touch、evidence、requirement / classification | C |
| `ClientReviewServiceImpl#decide` | case close、qualification_decision、state_change + `CompensationEventRegistrar.register(CLIENT_REVIEW_RESULT)` | C + S |
| `risk-compliance/.../service/processor/MaterialRequirementsProcessor` | 程序目录读取事务 | C |
| `risk-decision/.../impl/CheckoutRiskAssessmentServiceImpl#save` | evaluation、checkout snapshot、feature result、rule/flow trace、velocity event/subject | D |
| `risk-decision/.../impl/PaymentRiskAssessmentServiceImpl#save` | evaluation、transaction assessment、feature result、traces、velocity | D |
| `risk-decision/.../impl/ExecutionReleaseServiceImpl` | release、audit event；`startShadowRun` 写 shadow_run + 注册 `DECISION_SHADOW_RUN` | D + S |
| `risk-decision/.../impl/ShadowRunServiceImpl` | shadow run | D |
| `risk-control/.../service/impl/RiskListServiceImpl` | list / list entry | D（control） |
| 约 40 个 decision Processor（如 `CreateRuleSetVersionProcessor`、`SaveFlowProcessor`、`ReplaceVariableMembersProcessor`、`CreateRejectionReasonProcessor`、`DeleteFlowProcessor` 等） | 各自 decision 表 | D |

拆分影响：两数据源后，每个 `TransactionTemplate` 的 `PlatformTransactionManager` 注入需要 `@Qualifier`；`ClientReviewServiceImpl` 用 READ_COMMITTED + 5s 超时。

## 5. 跨核心 Java 调用

- **compliance ↔ decision / control：0 个 import（双向）。** compliance 中 grep `rule|variable|release|evaluation|risk list|velocity|reject reason|subject tag` 无命中。
- **decision → control：17 个文件。**
  - `com.paytech.risk.control.domain.list.RiskSubjectType`：14 个文件。
  - `com.paytech.risk.control.service.RiskListMembershipService`：`decision/service/query/FeatureValueReader.java`、`decision/service/processor/CreateVariableProcessor.java`。
  - `RiskSubjectKeys`、`RiskListView`：各 2 个文件。
- **规则引擎 / 变量 / 主体绑定**：全部在 risk-decision，compliance 不使用。
  - 引擎：`decision/domain/runtime/DecisionEngine`、`PredicateEvaluator`、`ExecutionSnapshot`、`RuntimeHashes`。
  - 评估器：`decision/service/evaluation/CheckoutPrepareEvaluator`、`PaymentAttemptEvaluator`、`DecisionPointEvaluator`、`RuntimeEvaluationExecutor`。
  - 变量：`decision/domain/variable/*`（18 类）；流程：`decision/domain/flow/*`；哈希：`decision/domain/hash/RiskDecisionCanonicalizer(Impl)`。
  - 主体绑定的类型 `RiskSubjectType` 在 risk-control。
- **risk-web 跨核心装配（`risk-web/src/main/java/com/paytech/risk/`）：**
  - `configuration/PersistenceConfiguration`：一个 `@MapperScan`（decision/control/compliance 三个 mapper 包）、一个 `sqlSessionTemplate`、一个 TypeHandler 注册表（登记 40+ decision/control 枚举）。
  - `configuration/RiskRuntimeConfiguration`：一个 Snowflake `IdGenerator`（`decision.identifier.region-id/worker-id`）同时提供 `DecisionIdentifierGenerator`、`ComplianceIdentifierGenerator`、`ControlIdentifierGenerator`；另有 `DecisionActionRegistry`、`RuleAstValidator`、`RiskDecisionCanonicalizer`、`Clock`。
  - `service/request/RiskOperationRequestContextParser(Impl)`：被 `ClientReviewAdminController`（C）与全部 decision 管理 Controller、`RiskListManagementController` 共用。
  - `configuration/RiskApiErrorConfiguration`、`RiskConfigurationResponseAdvice`、`service/convert/*ExceptionConverter`：decision 专用。
  - `resources/application.yml`：`web.logging.metadata-only-path-prefixes` 混列两核心路径；`compensation.enabled`、`redis.coordination` 单份。

## 6. 跨核心共享的 DataObject / 枚举 / 值类型

- **DataObject：无共享。** 每个模块有自己的 `core/dal/dataobject`。
- `decision/domain/enums/DecisionBusinessDomain`：`DUE_DILIGENCE`、`CHECKOUT`、`PAYMENT`、`SETTLEMENT`、`DISPUTE`。引擎在模型上服务 compliance/settlement/dispute；目前只种子了 CHECKOUT_PREPARE、PAYMENT_ATTEMPT_RISK。
- `control/domain/list/RiskSubjectType`：decision 使用；Schema 中 subject_type（CLIENT、OPERATING_ENTITY、CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CARD_FINGERPRINT、CARD_BIN）与 compliance 的 `role_type`（CLIENT、LOCAL_PARTNER、OPERATING_ENTITY、RELATED_PERSON）概念重叠但独立。
- `risk-api/.../api/enums` 混合：`ClientReviewErrorCode`、`MaterialQualificationType`（C）；`RiskDecisionConfigurationErrorCode`、`RiskDecisionEvaluationErrorCode`、`RiskVariableErrorCode`（D）。
- `risk-api/.../api/evaluation` 混合：`RiskDecisionApi`、`RiskEvaluationQueryApi`（decision 实现）与 `ManualPayoutEligibilityApi`（control 实现）。
- `risk-api/.../api/validation/DictionaryText(Validator)`：decision 字典使用。
- 哈希 Schema 标识共用 `CODE-MOR:RISK:` 命名空间：`DD_PROGRAM_VERSION:1`（C，`t_dd_program_version.content_hash`）、`LIMIT_VERSION:1`（D）、`SETTLEMENT_ELIGIBILITY_INPUT:1`（`control/service/processor/ManualPayoutEligibilityProcessor.INPUT_SCHEMA`，调用方需按同一规则计算 `inputHash`）。
- 时间、ID：统一 UTC `Clock`、统一 `InstantTypeHandler`、统一 Snowflake。

## 7. 外部合同

### 7.1 入站 HTTP（全部由 risk-web 提供，接口定义在 risk-api）

| 归属 | 接口类 | 路径 |
|---|---|---|
| C | `api/compliance/ClientReviewRegistrationApi` | `POST /client-onboarding-review/v1/submissions`；`POST /client-onboarding-review/v1/cases/{caseId}/classifications` |
| C | `api/compliance/ClientReviewResultApi` | `GET /client-onboarding-review/v1/applications/{applicationId}/result` |
| C | `api/compliance/ClientReviewAdminApi` | `/client-onboarding-admin/v1`：`GET /cases`、`GET /cases/{caseId}`、`POST /cases/{caseId}/evidence/{evidenceId}/review`、`POST /cases/{caseId}/return`、`/approval`、`/rejection` |
| C | `api/compliance/MaterialRequirementsApi` | `GET /internal/due-diligence/material-requirements` |
| D | `api/evaluation/RiskDecisionApi` | `POST /risk-decisions/v1/evaluations` |
| D | `api/evaluation/RiskEvaluationQueryApi` | `GET /risk-decisions/v1/evaluations`、`/{evaluationId}`、`/{evaluationId}/explanation` |
| D（现 control） | `api/evaluation/ManualPayoutEligibilityApi` | `POST /manual-payout-eligibility/v1/evaluations`（入参校验后恒返回 503 Fail-Closed） |
| D | `api/decision/DecisionDictionaryApi` | `/decision-dictionaries/v1/rejection-reasons…`、`/subject-tags…` |
| D | `api/decision/DecisionRuleSetManagementApi`、`DecisionConfigurationQueryApi` | `/decision-rule-sets/v1/…` |
| D | `api/decision/DecisionReleaseManagementApi`、`ExecutionReleaseManagementApi` | `/decision-releases/v1/candidates…`、`/executions…`（validate、shadow-runs、activate） |
| D | `api/decision/DecisionFlowQueryApi`、`DecisionFlowManagementApi` | `/decision-flows/v1/…` |
| D | `api/decision/RiskVariableApi` | `/decision-variables/v1/…` |
| D（现 control） | `api/control/RiskListManagementApi` | `/risk-lists/v1/lists…` |

Compliance 入口全部受开关控制，默认关闭：`onboarding.review.enabled`（`ClientReviewRegistrationController`、`ClientReviewAdminController`、`ClientReviewResultController`、`ClientReviewRequestConfiguration`），`onboarding.materials.enabled`（`MaterialRequirementsController`）。

### 7.2 risk-api 的外部消费者（`Code-MoR-refactor` 内 pom 依赖 + import 统计）

| 服务 | 使用的包 |
|---|---|
| client-contract-service | compliance(28)、enums(5) |
| client-console-bff | compliance(7) |
| commerce-service | evaluation(13)、enums(1) |
| payment-orchestration-service | evaluation(12)、enums(1) |
| operations-console-bff | decision(57)、evaluation(18)、compliance(12)、control(5)、enums(4) |

### 7.3 出站 HTTP

无 `@HttpExchange` 客户端、无 RestClient/WebClient 调用。

### 7.4 MQ

- 生产者（唯一）：`risk-web/.../ext/mq/ClientReviewResultRabbitPublisher`，exchange `topic_risk_client_review`（Topic，`ClientReviewMessagingConfiguration`），routing key `client_review.decided.v1`，publisher confirm + mandatory。归属 C。消费者：CCT `client-contract-service/.../ext/mq/ClientReviewResultListener`。
- compliance 侧端口：`risk-compliance/.../integration/ClientReviewResultPublisher`。
- 消费者：Risk 内无 `@RabbitListener`。

### 7.5 XXL-Job

Risk 未声明任何 `@XxlJob` Handler。`paytech-xxl-job-starter` 随 `paytech-compensation-event` 传递引入，供补偿兜底扫描；测试中 executor 关闭。生产 `application.yml` 无 `xxl` 段，运行期配置来源**未核实**。

### 7.6 Compensation Event Handler

| Handler | event_type | event_key | 归属 |
|---|---|---|---|
| `risk-compliance/.../service/handler/ClientReviewResultHandler` | `CLIENT_REVIEW_RESULT` | caseId | C |
| `risk-decision/.../service/handler/ShadowRunHandler` | `DECISION_SHADOW_RUN` | shadowRunId | D |

## 8. 测试

- 总量：160 个测试源文件，596 个 `@Test`。

| 目录（`risk-test/src/test/java/com/paytech/risk/auto/`） | 类 | `@Test` |
|---|---:|---:|
| `ext/http/decision` | 41 | 438 |
| `ext/http/compliance` | 8 | 57 |
| `ext/http/evaluation` | 10 | 48 |
| `ext/http/control` | 1 | 6 |
| `ext/http/logging` | 1 | 2 |
| `RiskModuleBoundaryAutoTest` | 1 | 11 |
| `domain/DecisionFlowValidatorNullInputAutoTest` | 1 | 0 |

- 基类：`test/base/MockTestBase` extends Foundation `TestBase`；`@SpringBootTest(classes = RiskApplication.class, RANDOM_PORT)`、`@ActiveProfiles("mock")`、`@ContextConfiguration(initializers = RiskDatabasePrecondition.class)`；`@Import` 全部核心的 Mocker/TestMapper/Checker（Settlement、MaterialProgram、RuleSet、Flow、Release、Dictionary、Variable、ClientReview、PaymentRisk、CheckoutRisk），共享一个应用上下文。子基类：`AbstractCheckoutRiskApiTest`、`AbstractPaymentRiskApiTest`、`AbstractRuntimeRiskApiTest`、`AbstractVariableApiTest`、`AbstractDecisionDictionaryApiTest`。
- 基础设施：`risk-test/src/test/resources/application-mock.yml`。本地 MySQL `127.0.0.1:13306/mor_risk`（risk_app）、Redis 16379（key-prefix `risk-mock`）、RabbitMQ 5672 `/mor-local`；compensation `instance-id: risk-mock`；`onboarding.review/materials.enabled=true`。`test/infrastructure/RiskDatabasePrecondition` 要求单库恰好 85 张表 + Action 目录行，并校验 Redis。
- 清理：Mocker 实现 Foundation `Clear`（`MaterialProgramMocker`、`ClientReviewMocker`、`RuleSetMocker`、`FlowMocker`、`CandidateMocker`、`DictionaryMocker`、`DictionaryReferenceMocker`、`VariableMocker`、`PaymentRiskMocker`、`CheckoutRiskMocker`）+ `test/clear/RiskRedisCleaner`，由全局 AfterEach 执行。`t_compensation_event` 按 event_type 定向删除：`ClientReviewTestMapper`（`CLIENT_REVIEW_RESULT` + case id）、`PaymentRiskTestMapper` / `CheckoutRiskTestMapper`（`DECISION_SHADOW_RUN`）。
- 探针：`test/probe/ClientReviewResultProbe`（RabbitMQ 结果）、`HttpLogProbe`。
- 跨核心测试（按实际 import 统计）：compliance-only 8 类；decision-only 37；decision+evaluation 6；control+decision+evaluation 9；control-only 1；evaluation-only 1（`ManualPayoutEligibilityApiAutoTest`，使用 `SettlementRiskAssessmentTestMapper` 读 `t_decision_evaluation` + `t_settlement_risk_assessment`）。**没有任何测试同时涉及 compliance 与 decision/control。** 全服务级：`RiskModuleBoundaryAutoTest`、`WebLoggingBoundaryAutoTest`。

## 9. 逻辑热点（按风险排序）

1. **Settlement Control 归属冲突（最高）。** `t_settlement_control` 由 decision `t_action_effect` 1:1 创建（`source_action_effect_id`，状态历史 `trigger_type=ACTION_EFFECT`）；decision 侧 payout 检查点 `t_settlement_risk_assessment` 需要读取 ACTIVE 控制以产出 `required_amount`/`control_set_hash`。若放 compliance，每次 payout 检查都成为跨库同步读、每个 Action Effect 都成为跨库写。代码尚未实现，应在实现前决定归属（倾向 decision 或明确同步/事件合同）。
2. **单一 `risk-api` JAR 被 5 个服务消费。** 包与枚举混合核心；`api.evaluation` 内含 control 实现的 payout API。拆 api 模块会改变 CCT、Commerce、Payment、两个 BFF 的 Maven 坐标或包名，需要兼容迁移。
3. **Dispute 责任决定依赖决策引擎。** `t_dispute_liability_decision` 规则路径冻结 `decision_evaluation_id + action_effect_id`（与 `decided_by` 互斥）；`DecisionBusinessDomain.DISPUTE` 预设 dispute 决策点。独立 dispute 模块需要同步评估合同，不能再依赖本地 ID 关联。
4. **Compliance 自动资格决定依赖决策引擎（潜在）。** `t_qualification_decision.decision_evaluation_id`、`decision_source=AUTOMATED`、`DecisionBusinessDomain.DUE_DILIGENCE`。当前 `DecideClientReviewProcessor` 第 120 行固定写 `REVIEWER`，评估 ID 为空，所以现在风险低，未来会上升。
5. **单数据源装配。** 一个 DataSource / SqlSessionTemplate / `@MapperScan` / TypeHandler 注册表；`mybatis.mapper-locations: classpath*:mapper/**/*.xml`；所有 TransactionTemplate 按类型注入事务管理器；`RiskDatabasePrecondition` 固定 85 表。拆库需双数据源、限定事务管理器、分 mapper 路径、按库前置检查。
6. **共享 `t_compensation_event`。** 两个 Handler 共用一张表、一套 compensation 配置（instance-id、scanner、XXL）；测试清理按 event_type 区分。拆库后每库一张表、各自独立的补偿实例。
7. **共享进程基础设施。** 一个 Snowflake worker 供三个 ID 端口，compliance 读取 `decision.identifier.*` 属性；`RiskOperationRequestContextParser` 共用；日志元数据路径单列表。如果拆成独立可部署单元，必须分配不同 worker id 并改属性命名空间。
8. **`risk-control` 定位。** 名单属于 decision 反欺诈且 decision 已依赖它（17 文件）；payout Processor 在 control，而其表属于 decision 评估组。建议并入 risk-decision。
9. **哈希 / Golden / 模型产物。** `CODE-MOR:RISK:*` 哈希 Schema 标识；`scripts/pdmaner/risk/` 的 canonicalizer（`action-effect-canonicalizer.mjs`、`limit-version-canonicalizer.mjs`、`dispute-evidence-package-canonicalizer.mjs`、`dispute-aggregate-validator.mjs`、`decision-engine-static-validator.mjs`）与单一生成器；"每项目一个 canonical PDManer 模型"规则要求拆分模型与 Manifest。`SETTLEMENT_ELIGIBILITY_INPUT:1` 是对 Finance 的线上合同，改名会破坏调用方。
10. **测试上下文与 Web 胶水（低）。** `MockTestBase` 一次导入全部核心夹具，单上下文同时开启 onboarding 开关；测试本身已按核心纯净，只需拆基类与前置检查。

## 10. 不确定项

- settlement control→compliance、`t_risk_restriction` / `t_settlement_risk_assessment`→decision 为建议归属。
- 生产 XXL-Job 配置来源未核实。
- 列分析只基于完整 Schema；`requirements/2026091x–20261016/.../db` 下已批准增量可能存在未反映的列。
- `t_command_idempotency` 可删除性需 Owner 确认。

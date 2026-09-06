# Risk Operations Rule Configuration Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `risk-compliance-service` 内交付 Operations 可调用的 Risk 规则配置后端首期：`CHECKOUT_PREPARE` Authoring Context、不可变 DRAFT Rule Set 保存与冻结、TEST Candidate 创建、可恢复的八项静态校验、完整查询、服务级幂等、真实 MySQL 持久化，以及与当前 Risk PDManer 一致的完整 Schema 和唯一初始化 DML。

**Architecture:** 保持 Risk 六模块边界；`risk-api` 只发布 typed HTTP 合同，`risk-decision` 拥有规则/发布领域、18 张 Decision 表的 DataObject/Mapper/Repository 和纯校验能力，`risk-web` 拥有 Controller、Processor、事务编排、服务级 `command_idempotency` 及运行配置，`risk-test` 通过随机 Loopback HTTP 和仓库自有 MySQL 验证真实生产链。每次保存物化一套不可变 Rule Set + Companion Point Version；Validate 严格执行事务 A、短 `read-only REPEATABLE_READ` 闭包装载、事务外纯检查、事务 B，不使用远程 I/O 或额外恢复设施。

**Tech Stack:** 项目批准的 Java 25、Spring Boot 4.0.8、Spring MVC、Spring `@HttpExchange`、MyBatis XML、MySQL 8、Foundation Paging/Error/MyBatis TypeHandler/Snowflake 能力、显式 `TransactionTemplate`、JUnit 5，以及 Node.js 的 PDManer/契约门禁；所有版本从现有 Parent/BOM 解析。

**Spec:** [`docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`](../specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)

**Error-contract supersession:** 本计划中原 Foundation 四字段错误响应步骤由
[`2026-09-06-system-wide-rfc-9457-problem-details-design.md`](../specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md) 取代；Risk 业务 Code/Status 与其余历史实施证据不变。

## Global Constraints

- 用户已经明确授权开始实现；本计划是执行入口，不是部署、建分支、提交、推送或修改共享数据库的授权。开始、每个跨仓库阶段和结束时分别检查根工作区及 `risk-compliance-service` 的 `git status --short`。
- 当前 Risk 子仓库的 `docs/database/risk-compliance.pdma.json` 与 `RISK_COMPLIANCE_PHYSICAL_DECISIONS.md` 已包含用户的 Local Partner Typed Namespace 改动；所有模型生成和文档编辑必须在该基线上增量完成，禁止覆盖、回退或清理。
- 先闭合会被完整 DDL 固化的 Risk 模型前置，再实现依赖这些表的 Java。COMP-01 是六库横切迁移，DB-01 的全局关闭仍受 Vault 约束；本计划只声明有证据的 Risk 侧完成状态，不把单库成功升级为全局完成。
- Operations 后端范围只包含规则配置、Freeze、TEST Candidate 和 Static Validation。不得增加 UI、Operations BFF、Gateway Route、Platform 权限、Shadow、人工审批、激活、运行期评估或部署。
- `CHECKOUT_PREPARE_AUTHORING_V1` 恰好暴露九个 Field、十一种 Operator、三个零参数终态 Action，Feature、Parameter、Obligation、Limit Binding 四个集合恰好为空。不得因此删除或缩窄 RCO-I03 现行运行输入。
- 所有业务 Service 和 Domain Service 使用 `interface + *Impl`；Processor/Controller 保持具体类；Repository 使用 `interface + *RepositoryImpl`。DataObject 保留在 Owner JAR，不能穿越 Maven 或 HTTP 边界。
- 生产 SQL 只放 MyBatis XML；事务只用显式 `TransactionTemplate`，不使用注解 SQL 或 `@Transactional`。不引入 Redis/JVM/Tenant 锁、OpenFeign、Dubbo、MQ 或后台恢复任务。
- 不使用 Mockito、`@MockBean`、Spy、Testcontainers、Flyway、Liquibase 或应用启动建表/Seed。真实数据库测试只复用手工准备的 `127.0.0.1:13306/mor_risk`；测试缺依赖时必须快速失败并给出准确准备指引。
- Java/JUnit 不执行 DDL/DML。本计划可以生成并验证 SQL 文件，但只有用户另行明确授权后才能人工应用到本地数据库；在此之前，数据库行为测试的未运行状态必须如实记录。
- 两个管理 Base Path 必须配置 metadata-only 日志。规则 AST、Literal、Change Reason、请求/响应 Body 不得进入普通日志；错误摘要不含 Literal、个人数据、Token 或 Payload。
- 所有行为修改按 TDD：先创建并实际运行最小 RED，再写最小生产实现，再运行聚焦回归。任何已有失败和本次引入失败分开记录。
- API 成功固定 HTTP 200 和 `application/json`；错误使用系统级 Code-MoR Problem Profile，只新增一个 Risk 404 Code。所有外部 BIGINT ID 用正十进制 String，所有时间用 UTC 微秒文本。
- 规则、Point Version、Release 和四种 Command Request Hash 使用各自版本化 Schema、RFC 8785/JCS、NFC、SHA-256 小写十六进制。不存在批准的共享 JCS 库时，只能先实现一个受共享黄金向量约束的 Risk 内部纯实现，并把未来上移 Foundation 记录为显式后续事项，不能用 Jackson Map 排序或手拼 JSON 冒充 JCS。

---

## Task 0: 冻结批准记录、现状证据与可执行边界

**Files:**

- Create: `agent-docs/decisions/2026-09-05-risk-operations-rule-configuration-backend.md`
- Modify: `docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`
- Modify: `agent-docs/DOCUMENT_GOVERNANCE.md`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`

- [x] 记录根仓库和 Risk 子仓库的 Branch、HEAD、`git status --short`，并确认上述两个已有 Risk 数据库文件的差异仍是任务开始前的用户改动。
- [x] 创建窄范围 Confirmed Decision，记录用户已经确认的首期范围、接口、幂等恢复、八项检查、零 Feature/Parameter/Obligation/Limit、完整 DDL 与禁止项；只引用 Spec 作为完整设计，不复制第二套业务合同。
- [x] 把 Spec 的书面评审状态改为 Confirmed，把实施状态改为 In progress；不得提前标记 Java、SQL、数据库或部署完成。
- [x] 在治理路由中登记 Spec/Decision/Plan 的 Owner 关系；在 `CURRENT_IMPLEMENTATION.md` 只记录“实施已启动”和准确 Gate，避免把设计或计划描述为现有实现。
- [x] 运行文档链接/占位扫描和 `npm --prefix requirements-site test`，把现有结果作为后续基线。

## Gate 1: 闭合完整 Schema 的 Risk 模型前置

### Task 1.1: 对齐 `t_compensation_event`，但不虚报六库 COMP-01 完成

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/08-reliability.mjs`
- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk/generate-risk-compliance-model.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `scripts/pdmaner/risk-compliance-scenario-doc.test.mjs`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify generated: `risk-compliance-service/docs/database/risk-compliance.pdma.json`

- [x] 先把 Risk Oracle 改为 Foundation 统一的 15 列 `event_key/event_type/sub_event_type/payload_json/...` 合同，并新增反例断言：无旧 `execution_type/source_type/source_id/source_version/schema_version/payload_hash`，无旧 Counter Check，唯一索引和 ready/lease 索引准确；运行并观察当前 Manifest RED。
- [x] 为 Risk 当前会产生补偿事件的每一种事实建立重建清单，冻结 `event_type/sub_event_type/event_key` 构造、权威事实表、MQ 信封来源和永久幂等位置。用该清单改写 `CR_SETTLEMENT_CONTROL_EVENT` 与 `CR_SIMULATED_EFFECT_COMPENSATION_EVENT`，不能在删除 Source 列后留下不可证明的规则。
- [x] 更新 Manifest：目标字段恰好 15 个；`payload_json VARCHAR(4096) NULL`；`lock_owner VARCHAR(64)`；`lock_version BIGINT DEFAULT 1`；唯一索引 `uk_comp_event_identity(event_key,event_type,sub_event_type)`；访问索引 `idx_comp_event_ready` 和 `idx_comp_event_lease`。
- [x] 使用既有生成器重新生成 PDManer，在生成前后对比并证明 Local Partner Typed Namespace 改动仍保留；禁止手改 JSON 掩盖 Manifest 漂移。
- [x] 同步数据库权威和场景文档，只声明 Risk 模型已对齐；`CURRENT_IMPLEMENTATION.md` 的六库 COMP-01 仍按全局证据维护。
- [x] 运行 `generate-risk-compliance-model.mjs --check` 与全部 Risk model/scenario tests，记录目标统计并复核关系 Sidecar 仍为逻辑关系。

### Task 1.2: 对齐 Vault V1 消费者边界与 DB-01 Risk 投影

**Files:**

- Modify: `scripts/pdmaner/risk/model-policy.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `risk-compliance-service/docs/database/RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify generated if required by a confirmed nullability decision: `risk-compliance-service/docs/database/risk-compliance.pdma.json`

- [x] 先写 RED，区分三个 Risk-owned Recoverable Token 与四个 Commerce 透传 Stable Correlation Token；禁止把后四者登记为 Risk Reveal 授权。
- [x] 删除 Risk 文档中的 Vault Resource Binding、Retire、Metadata 或业务 Resource 参数假设，改成当前 Protect/Reveal V1；规则配置模块明确零 Vault 依赖。
- [x] 以现行尽调场景证明 `dd_party_snapshot.snapshot_vault_token` 是有 Reveal 消费者的必需受保护快照后保持 `NOT NULL`；若证据不成立则停止该字段改动并请求新业务决定，不能在 DDL 阶段自行猜测 Nullable。
- [x] 为 Token Wire `VARCHAR(128)` 记录“协议缓冲”或另行确认收紧；不因当前 43 字符 V1 Wire 擅自改字段。
- [x] 新增 Risk Schema 的零物理 `CHECK/FOREIGN KEY` 门禁，同时保留模型关系 Sidecar 为应用不变量；明确全局 DB-01 仍受 Vault 物理 Schema 阻塞。

### Task 1.3: 增加规则版本审计字段与 RCO-I12 需求闭环

**Files:**

- Modify: `scripts/pdmaner/risk/manifests/02-decision-engine.mjs`
- Modify: `scripts/pdmaner/risk-compliance-test-oracle/schema.oracle.mjs`
- Modify: `scripts/pdmaner/risk-compliance-model.test.mjs`
- Modify: `agent-docs/database/RISK_COMPLIANCE_DATABASE.md`
- Modify: `agent-docs/database/scenarios/RISK_COMPLIANCE_REAL_WORLD_SCENARIOS.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Modify: `requirements-site/tests/site-content.test.mjs`
- Modify generated: `risk-compliance-service/docs/database/risk-compliance.pdma.json`

- [x] 先写模型 RED，要求 `rule_set_version.created_by BIGINT NOT NULL` 与 `change_reason VARCHAR(500) NOT NULL`，并验证字段注释、顺序和非空性。
- [x] 更新 Decision Engine Manifest、Oracle、权威文档及场景；重新生成模型并证明现有用户改动未丢失。
- [x] 新增 RCO-I12，覆盖 Authoring Context、DRAFT 保存、Freeze、TEST Candidate、八项静态校验、同键恢复和 Fail-Closed；只把外部交互加入真正受影响的全局序列，不创建运行期评估或 Shadow 假步骤。
- [x] 运行全部 Risk Node tests 和 requirements-site tests。

## Task 2: 生成完整 71 表 Schema 与唯一初始化 DML

**Files:**

- Create: `scripts/pdmaner/risk/generate-risk-compliance-schema.mjs`
- Create: `scripts/pdmaner/risk-compliance-schema.test.mjs`
- Create: `scripts/pdmaner/risk-compliance-initial-data.test.mjs`
- Create: `risk-compliance-service/docs/database/sql/risk-compliance-schema.sql`
- Create: `risk-compliance-service/docs/database/sql/risk-compliance-initial-data.sql`
- Create: `risk-compliance-service/risk-test/src/test/resources/fixtures/risk-decision-v1-golden-vectors.json`

- [x] 先写 Schema RED：SQL 必须恰好创建最终 PDManer 的全部表，字段顺序/类型/长度/精度/Null/Default/表列中文注释/PK/UK/IDX 精确匹配；禁止 `DROP/TRUNCATE/INSERT/AUTO_INCREMENT/CHECK/FOREIGN KEY`，保留特殊物理名 `t_settle_control_state_change`。
- [x] 实现确定性 Schema 生成器，读取 PDManer 而不是维护第二份字段清单；支持默认生成和 `--check`。SQL 顶部明确“只适用于人工审核后的空 Risk 数据库”，不得包含应用期迁移逻辑。
- [x] 先在共享 Golden Vector 中冻结三个 Handler Descriptor 的规范投影与真实 SHA-256；Java/Node 后续都消费同一份向量。禁止动态时间、占位 Hash 或 `sha256:*` 文本。
- [x] 冻结稳定目录事实（Decision Point ID、三个 Action Version ID、名称、Action Version No、固定初始化时间），先写 DML RED，再创建唯一初始化文件，且只插入一行 `t_decision_point` 与三行 `t_action_version`。
- [x] DML 不得插入 Point Version、九个 Input Field、Rule Set Binding、Feature、Parameter、Obligation 或模板 Version，不得使用 `INSERT IGNORE`/Upsert 静默覆盖。
- [x] 运行 Schema/DML tests、生成器 `--check`、全 Risk 模型门禁；没有用户授权时不应用 SQL，并把真实 MySQL 测试标记为等待人工 Schema/DML，而不是声称失败或完成。

## Task 3: 建立 Maven、持久化配置与真实 MySQL 测试基座

**Files:**

- Modify: `risk-compliance-service/pom.xml`
- Modify: `risk-compliance-service/risk-api/pom.xml`
- Modify: `risk-compliance-service/risk-decision/pom.xml`
- Modify: `risk-compliance-service/risk-web/pom.xml`
- Modify: `risk-compliance-service/risk-test/pom.xml`
- Modify: `risk-compliance-service/risk-web/src/main/resources/application.yml`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/configuration/PersistenceConfiguration.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/configuration/RiskRuntimeConfiguration.java`
- Create: `risk-compliance-service/risk-test/src/test/resources/application-auto.yml`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/infrastructure/RiskDatabasePrecondition.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/base/RiskServiceAutoTestBase.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/base/RiskRepositoryAutoTestBase.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/decision/RiskDecisionTestMapper.java`
- Modify: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/RiskModuleBoundaryAutoTest.java`
- Modify: existing Risk Spring tests only where required for the `auto` profile/database precondition.

- [x] 先改模块边界与配置测试，期望 `risk-decision` 只增加 MyBatis Support/API、Spring Context/Tx，`risk-web` 增加 JDBC/MyBatis Starter、Runtime MySQL 和 Foundation Snowflake，`risk-api` 增加 Common Core；确认旧 POM RED。
- [x] 根 Enforcer 只移除 `spring-jdbc`、`org.mybatis:*`、`org.mybatis.spring.boot:*`、`com.mysql:*`、`HikariCP` 五组已批准禁令；保留 Migration、Testcontainers、Mockito、Nacos、Redis、RabbitMQ、XXL-Job、OpenFeign、Dubbo 等所有仍有效禁令。
- [x] 配置两个 Mapper 包、`classpath*:mapper/**/*.xml`、`useGeneratedKeys=false`、Foundation `CodeEnumTypeHandler` 与 UTC `InstantTypeHandler`；业务 ID 通过窄 `DecisionIdentifierGenerator` Port 适配 Foundation Snowflake，不能让领域依赖 Web 实现。
- [x] 全局 Jackson 输入启用未知属性和缺失 Creator 属性失败；API DTO 再以 required metadata 和 Bean Validation 固定显式 Null 语义。增加两个 metadata-only Path Prefix。
- [x] `application-auto.yml` 只指向 `127.0.0.1:13306/mor_risk` 和 `risk_app`，密码来自 `RISK_TEST_DB_PASSWORD`/`MOR_RISK_DB_PASSWORD`；前置检查在 Spring 启动前验证 Loopback、71 表 Schema 和四条目录 DML，失败时输出人工准备命令但不执行 SQL。
- [x] TestMapper 集中 Fixture 准备、观察和精确清理；每个测试使用独立业务 Code/ID 命名空间，不 `TRUNCATE`、不重建 Schema、不删除非本测试行。
- [x] 运行纯模块测试；只有数据库已由用户准备时才运行 Spring/MySQL 测试，并区分“环境未准备”与代码失败。

## Task 4: 发布精确 `risk-api` 合同与唯一 404 Code

**Files:**

- Create: `risk-compliance-service/risk-api/src/main/java/com/paytech/risk/api/decision/DecisionRuleSetManagementApi.java`
- Create: `risk-compliance-service/risk-api/src/main/java/com/paytech/risk/api/decision/DecisionRuleSetManagementModels.java`
- Create: `risk-compliance-service/risk-api/src/main/java/com/paytech/risk/api/decision/DecisionReleaseManagementApi.java`
- Create: `risk-compliance-service/risk-api/src/main/java/com/paytech/risk/api/decision/DecisionReleaseManagementModels.java`
- Create: `risk-compliance-service/risk-api/src/main/java/com/paytech/risk/api/enums/RiskDecisionConfigurationErrorCode.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/configuration/RiskApiErrorConfiguration.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/contract/RiskDecisionConfigurationApiContractAutoTest.java`

- [x] 先写反射/JSON RED，精确断言两个 Base Path、九个 Method+Path、Header 规则、四个写请求、四个命令响应、五个 GET Schema、Foundation Paging 和唯一新增 404 Code；不得只做“字段至少存在”检查。
- [x] API 接口使用 `@HttpExchange`；Controller 将直接实现接口，不重复声明路由。所有 ID 暴露为 String，普通 Version/Sequence 为 Integer，时间先暴露为严格 UTC 微秒 String。
- [x] DTO 使用 `public final *Models + public record`，集合防御性复制。Create Rule Set 的所有列出属性都必须出现；可空字段用 Wrapper/显式 required metadata，未知属性拒绝。
- [x] `literalValue` 仅接受 JSON String 或 Boolean；NUMBER/DATETIME/DURATION 保持 String，禁止 JSON Number 在反序列化阶段被宽松转型。
- [x] 只发布 `RISK_DECISION_CONFIGURATION_NOT_FOUND` 并通过一个 `ApiErrorStatusResolver` 映射 404；400/409/500/503 复用 Foundation，不新增 Advice 或新 Error Body。
- [x] 运行合同测试和既有 API/启动测试，确认 RCO-I09 的默认 503/显式 local bootstrap 未改变。

## Task 5: 实现 Authoring Descriptor、Handler Registry 与纯校验/Canonical Hash

**Files:**

- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/authoring/CheckoutPrepareAuthoringDescriptor.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/authoring/DecisionActionRegistry.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizer.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/rule/RuleAstValidator.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/rule/CanonicalUtcMicrosecond.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/rule/ChangeReasonPolicy.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseStaticValidator.java`
- Create closed enums under: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/enums/`
- Modify: `scripts/pdmaner/risk/decision-engine-static-validator.mjs`
- Modify: `scripts/pdmaner/risk-decision-engine-contract.test.mjs`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/domain/RuleAstValidatorAutoTest.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/domain/RiskDecisionCanonicalizerAutoTest.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/domain/DecisionReleaseStaticValidatorAutoTest.java`

- [x] 先用共享 JSON Golden Vector 写 Java/JavaScript RED：NFC、无符号 UTF-8 排序、Node ID `2/10`、Null 排序、点号/下划线路径、UTC 微秒、ID String、Decimal 八位定标、负零/指数/前导零拒绝、四种 Request Hash 与三种 Content Hash 命名空间。
- [x] 实现最小 RFC 8785/JCS 编码器，仅接受已验证的投影类型；Object Key 按 JCS 规则、String 转义和数字编码符合 RFC 8785，不能依赖 Locale、默认 Map 顺序或通用 Jackson 序列化输出。
- [x] Descriptor 精确固定 Point、九 Field、十一 Operator 和三 Action；Registry 的 Handler Descriptor Hash 必须与共享向量及 DML 一致，ACTIVE 数据库目录漂移时失败关闭。
- [x] AST 校验覆盖一 Root、可达无环、Sibling Sequence 连续、GROUP/NOT/Predicate 形状、Operator Operand 矩阵、类型一致、字段值域、200/2000/10000/12/100 上限，且在写事务前完成。
- [x] Static Validator 始终计算八项，每项将全部违规按规范五元组去重排序后选首项；摘要固定 `first=<code>@<safe-path>; violation_count=<n>`，不含 Literal，最长 1000。
- [x] JavaScript Validator 迁移到 `RISK_DECISION_RELEASE_V1` 与同一排序规则；Java/JS 对全部 Golden Vector 的 Hash 和首违规逐字节一致。
- [x] 运行三个纯 Java 测试、Node 决策引擎测试和 DML Hash 测试。

## Task 6: 实现 Decision 聚合的 DataObject、Mapper 与 Repository

**Files:**

- Create 18 immutable DataObjects under: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/core/dal/dataobject/`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/core/dal/mapper/DecisionRuleSetMapper.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/core/dal/mapper/DecisionReleaseMapper.java`
- Create: `risk-compliance-service/risk-decision/src/main/resources/mapper/decision/DecisionRuleSetMapper.xml`
- Create: `risk-compliance-service/risk-decision/src/main/resources/mapper/decision/DecisionReleaseMapper.xml`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/repository/DecisionRuleSetRepository.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/repository/impl/DecisionRuleSetRepositoryImpl.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/repository/DecisionReleaseRepository.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/repository/impl/DecisionReleaseRepositoryImpl.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/repository/RiskDecisionPersistenceAutoTest.java`

- [x] 先写 XML 契约 RED 和真实 MySQL RED，覆盖 list/count 同过滤、稳定排序、完整聚合重建、未知数据库 Code、UTC 微秒、Decimal、不完整/重复闭包、Root/Range/Version/Release 锁与 CAS 行数。
- [x] 18 张表只包括 Spec 的 12 张写表和 6 张只读闭包表；不映射 `decision_point_limit_binding`、Shadow 或评估/执行表。
- [x] Java Mapper 只声明签名/参数，SQL 全在 XML；枚举和 Instant 显式绑定 Foundation TypeHandler，`FOR UPDATE` 查询禁用缓存。
- [x] Repository 按聚合重建 Root/Children，检测重复、孤儿、未知 Check/Binding 并失败关闭；不得通过任意挑第一行恢复损坏数据。
- [x] list/count 使用相同 SQL Filter，数据库 `LIMIT/OFFSET`，并固定 Rule Set 与 Candidate 的双字段倒序。
- [x] 运行 XML 静态契约测试；授权并人工准备仓库本地数据库后运行真实 MySQL 测试。

## Task 7: 实现服务级命令幂等与显式事务能力

**Files:**

- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/core/dal/dataobject/CommandIdempotency.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/core/dal/mapper/CommandIdempotencyMapper.java`
- Create: `risk-compliance-service/risk-web/src/main/resources/mapper/idempotency/CommandIdempotencyMapper.xml`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/domainservice/repository/CommandIdempotencyRepository.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/domainservice/repository/impl/CommandIdempotencyRepositoryImpl.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/domainservice/service/CommandIdempotencyService.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/domainservice/service/impl/CommandIdempotencyServiceImpl.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/service/transaction/RiskTransactionExecutor.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/domainservice/CommandIdempotencyAutoTest.java`

- [x] 先写 RED 覆盖 `OPERATIONS_ACCOUNT:<actorId> + operationType + key`、Key 格式、四种 Request Hash、同键同/异载荷、PROCESSING/SUCCEEDED/FAILED/过期、稳定 `response_ref`、原子 Claim 和并发串行化。
- [x] Service 隐藏 Mapper/Row 细节；Processor 只能使用 typed claim/replay/complete/recovery API。首次记录时间决定 24 小时 `expire_time`，过期行仍参与冲突与重放。
- [x] 非 Validate 命令不能提交可见 PROCESSING；只有 Validate 可在事务 A 后保留 `PROCESSING + release:<id>`。FAILED 和任何非法状态矩阵统一抛内部 Fail-Closed，不猜测修复。
- [x] Response Ref 只保存操作类型和稳定业务 ID；重放由不可变事实重建首次响应，不能返回 Root 当前状态。
- [x] `RiskTransactionExecutor` 封装短写事务和无读锁的 `read-only REPEATABLE_READ` 快照；Authoring Context、Rule Set/Release List/Detail 五条多 SQL GET 使用 read，Validate Processor 显式执行 write(A) → read(闭包) → 事务外纯校验 → write(B)。
- [x] 运行幂等纯/数据库测试和并发测试。

## Task 8: 实现 Rule Set Authoring、查询、创建新 DRAFT 与 Freeze

**Files:**

- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/rule/DecisionRuleSetAggregate.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/service/DecisionRuleSetService.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/service/impl/DecisionRuleSetServiceImpl.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/service/processor/DecisionRuleSetManagementProcessor.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/ext/rest/decision/DecisionRuleSetManagementController.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/ext/rest/decision/RiskDecisionWireMapper.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/builder/DecisionRuleSetRequestBuilder.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/checker/DecisionRuleSetChecker.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionRuleSetManagementHttpAutoTest.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionConfigurationConcurrencyAutoTest.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionConfigurationReadSnapshotAutoTest.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/infrastructure/RiskSqlBarrier.java`

- [x] 先写 Authoring Context RED：准确 Point、九 Field、Operator 固定顺序、三 Action 与四个空集合；DB Action 缺失、重复、Hash 漂移或非 ACTIVE 时 500 Fail-Closed。
- [x] 写 Create HTTP/MySQL RED：严格 JSON/Header/Key、首次 Base 双 Null、后续 Base 双非 Null且为最新、数组重排/Node Key 重命名同 Hash、审计字段、每次全新 ID、Point Version 序列、完整子图和幂等稳定重放。
- [x] 写保存并发 RED：锁顺序固定为 idempotency→Point Root→Code Range/当前 Version→Companion；不同 Code 仍安全分配 Point Version No；同 Code 只有一个 winner；唯一冲突只在准确业务索引时映射 409。
- [x] Service 在事务前完成 DTO 结构/类型/上限验证和语义投影；事务中物化 Descriptor 的 Point Version、九 Input Field、Binding、Rule/Node/Operand/Action，并保存三个 Hash/审计事实。
- [x] `changeReason` 在 Wire、领域校验和持久化入口执行同一窄策略，拒绝不可展示字符、完整 JSON、Credential/Bearer 与 Luhn PAN 候选且不回显；DATETIME 由三处消费者共享严格 UTC 微秒解析器和黄金向量，拒绝 `24:00`、闰秒与非法日历日期。
- [x] 后续保存若最新是 DRAFT，同事务 RETIRE Rule Set 与 Companion；若最新 FROZEN 保持不变；任何非最新 Base、ID/Hash/Status 不匹配返回 409，历史子图永不修改。
- [x] 写 Freeze RED：重载完整闭包、重算两个 Hash、目录 ACTIVE/Handler 精确匹配、条件 CAS，并在同一事务同时 FROZEN 两个 Root；任一失败零部分状态。
- [x] GET Detail 确定性生成 Node ID Key、树前序和 Operand 顺序；List/Detail 显式输出 Null；跨 Point/不存在使用唯一 404。历史详情和成功重放只校验实际引用 Action 的不可变身份/Handler 内容，允许其后来 `RETIRED`，且不依赖无关目录项；新增/Freeze 路径仍要求完整 ACTIVE 目录。
- [x] Controller 只做协议转换与 Processor 调用，不访问 Mapper。
- [x] 运行 Rule Set HTTP、持久化、并发和日志脱敏测试。

## Task 9: 实现 TEST Candidate 创建、查询与冻结组合

**Files:**

- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseAggregate.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/service/DecisionReleaseService.java`
- Create: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/service/impl/DecisionReleaseServiceImpl.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/service/processor/DecisionReleaseManagementProcessor.java`
- Create: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/ext/rest/decision/DecisionReleaseManagementController.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/test/checker/DecisionReleaseChecker.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionReleaseManagementHttpAutoTest.java`

- [x] 先写 Candidate Create RED：只接受 FROZEN Rule Set/Companion、Hash 精确、Point=`CHECKOUT_PREPARE`、唯一 Binding、零 Feature/Parameter/Obligation 和结构性空 Limit Binding，环境服务端固定 TEST。
- [x] 同一事务创建 immutable Release Root `DRAFT/version=1/active_slot=NULL` 和准确初始 State Change；Release No/Hash、Point、Rule Set、Environment 从此不变。
- [x] 同 Key 重放固定返回 Version 1 和初始 State Change，即使 Root 后来 VALIDATING/REJECTED；同键异载荷 409。
- [x] GET List/Detail 从本地冻结事实构造五个已冻结组合坐标；State Change/Static Check 排序准确，0 Checks 时 `allStaticChecksPassed=null/waitingForShadow=false`。
- [x] 详情遇到重复/未知 Static Check 或损坏冻结引用时 500 Fail-Closed，不展示猜测结果。
- [x] 运行 Candidate HTTP/MySQL、分页和并发测试。

## Task 10: 实现两事务 Validate、同键恢复与损坏矩阵

**Files:**

- Modify: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/service/DecisionReleaseService.java`
- Modify: `risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domainservice/service/impl/DecisionReleaseServiceImpl.java`
- Modify: `risk-compliance-service/risk-web/src/main/java/com/paytech/risk/service/processor/DecisionReleaseManagementProcessor.java`
- Create: `risk-compliance-service/risk-test/src/test/java/com/paytech/risk/auto/ext/http/DecisionReleaseValidationRecoveryAutoTest.java`

- [x] 先写事务 A RED：锁幂等行再锁 `DRAFT/version=1` Release，保存 `PROCESSING + release:<id>`，CAS 到 `VALIDATING/version=2`，追加 `DRAFT→VALIDATING/STATIC_VALIDATION_STARTED` 后提交。
- [x] 先写一致性装载与事务外检查 RED：在短 `read-only REPEATABLE_READ`、无读锁事务内装载 frozen closure，结束快照后在无事务、无远程 I/O 下只计算一次并返回严格八项结果，不首错停止。
- [x] 先写事务 B RED：再次按幂等→Release 顺序锁；只允许 `PROCESSING + VALIDATING/v2 + zero checks` 一次插入八行。全过保持 VALIDATING/v2；任一失败追加 `VALIDATING→REJECTED/v3` 并更新 Root；最后 SUCCEEDED。
- [x] 写重放/恢复 RED：A/B 间故障后只有原 Key+同 Hash 可继续；两个同键并发恢复由原子幂等 Claim 串行，后到者重建首次结果。
- [x] 写不可达损坏 RED：1–7 Checks、8 Checks 但仍 PROCESSING、错误 Root 状态/Version、FAILED、损坏 Response Ref、重复/未知 Check 均返回 500，不补写、不删行、不猜测结果。
- [x] Validate 响应严格八项顺序；Passed=`INFO + null/null`，Failed=`ERROR + nonblank/nonblank`。全通过详情派生 `waitingForShadow=true`，不新增 SHADOWING 或指针字段。
- [x] 运行纯 Validator、Recovery、同 Key 并发、数据库不变量和日志脱敏测试。

## Task 11: 文档、运维与最终验证

**2026-09-05 pre-application 状态（历史证据）：** Risk-only 的代码、71 表 Schema DDL、唯一初始化 DML 与
PDManer 模型已在当前工作区生成；当时 `mor_risk` 仍为 0 表，真实 MySQL HTTP、并发和 Validate recovery、
人工 PDManer Review 及部署均 Pending。全局 COMP-01、DB-01 与 Vault V1 同样保持未完成。

最终工作区证据：Risk 模型/Schema 门禁 188/188、需求站点 134/134、生产模块打包及 DB-free Java 111/111
通过；完整 `./mvnw clean verify` 按设计在 `RiskDatabasePrecondition` 停止，只读核对确认本地 `mor_risk`
为 0 表。该前置失败不是代码通过或真实 MySQL 通过的替代证据。

**2026-09-06 后续状态：** 经用户授权并核对目标为应用前为空的仓库自有本地 `mor_risk`，已先备份，再
手工应用完整 Schema 和唯一 DML。物理核对为 71 表 / 862 字段 / 71 主键 / 105 唯一二级索引 / 75 访问
索引 / 0 FK / 0 CHECK / 0 自增 / 0 空表或字段注释，固定目录为 1 个 Decision Point 与 3 个 Action Version。
真实 Loopback HTTP/MySQL、持久化、并发幂等、五条 GET 一致性快照、历史 Action 退役重放、敏感输入拒绝
和 Validate A/读快照/事务外纯校验/B Recovery 已随 JDK 25 `clean verify` 148/148 通过；测试清理后只剩
固定目录行。PDManer 4.9.3 应用内人工 Review 与部署仍 Pending。

**Files:**

- Modify: `risk-compliance-service/README.md`
- Modify: `agent-docs/CURRENT_IMPLEMENTATION.md`
- Modify: `docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md`
- Modify: `docs/superpowers/plans/2026-09-05-risk-operations-rule-configuration-backend-implementation.md`
- Modify only if the canonical database facts changed: Risk database/physical/scenario documents listed above.

- [x] README 记录两个内部 Base Path、所需 Headers、默认 Fail-Closed 身份边界、metadata-only、手工 Schema/DML 准备、测试环境变量和本期明确未实现项；不得给出生产部署暗示。
- [x] 运行 `rg` 安全扫描，证明规则 Body/Literal、Vault/Consumer/网络原值、Secret、Credential、完整 Payload 不会被日志模板或异常消息输出。
- [x] 运行 Maven 格式/编译/聚焦测试与完整 `./mvnw clean verify`；如 MySQL 未获准准备，完整命令应快速失败于明确前置，另行运行所有不依赖数据库的测试并准确报告覆盖范围。
- [x] 运行：

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR
node scripts/pdmaner/risk/generate-risk-compliance-model.mjs --check
node scripts/pdmaner/risk/generate-risk-compliance-schema.mjs --check
node --test scripts/pdmaner/risk*.test.mjs scripts/pdmaner/risk-compliance-*.test.mjs
npm --prefix requirements-site test

cd /Users/kenny/Desktop/Workspace/Code-MoR/risk-compliance-service
./mvnw clean verify
```

- [x] 用户明确授权后，先备份并核对目标仅为仓库自有且应用前为空的 `mor_risk`，应用完整 Schema 和唯一 DML，再用 `SHOW CREATE TABLE` 与 `information_schema.columns/statistics` 验证 71 表、字段、索引、零 CHECK/FK，并重跑全部 MySQL auto tests；未对共享或生产库执行。
- [x] 使用 `superpowers:verification-before-completion` 核对实际输出后，更新 Spec/Decision/`CURRENT_IMPLEMENTATION.md`：分别记录代码、SQL 文件、自动模型验证、真实 MySQL 验证、人工 PDManer Review 和部署状态，未完成项保持明确 Pending。
- [x] 最后分别检查两个 Git 工作区差异，确认只修改计划列出的文件且没有覆盖用户已有改动；不提交、不建分支、不推送。

## Completion Definition

- 两个 typed API 可构建，严格遵守九个 Method+Path、Headers、JSON/分页/错误/ID/时间合同。
- Authoring Context 与工程目录/DML/Handler Registry 精确一致；任一漂移失败关闭。
- Rule Set 每次保存产生完整不可变 DRAFT Snapshot，Base CAS、退役、Freeze 和重放/并发行为均由真实 MySQL 测试证明。
- TEST Candidate 只引用一个准确冻结组合；Validate 的事务 A/只读一致性闭包装载/事务外纯检查/事务 B、八行检查、拒绝路径、同键恢复和损坏矩阵由自动测试证明。
- 完整 Risk Schema 与唯一 DML 可确定性生成并通过 PDManer Oracle；SQL 零 `CHECK/FOREIGN KEY` 且不包含自动迁移/Seed 机制。
- 日志保持 metadata-only，无规则 AST/Literal 或敏感内容泄漏；RCO-I09 现有默认 503 行为不回退。
- 文档只声明实际实现和验证状态；授权后的仓库本地数据库应用不等于部署，PDManer 人工 Review、UI/BFF/Gateway/权限/Shadow/激活/运行评估/部署仍明确未完成或不在本期范围。

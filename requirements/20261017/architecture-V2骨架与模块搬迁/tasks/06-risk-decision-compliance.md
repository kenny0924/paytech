# 任务卡 06 · Risk → risk-decision + compliance（两库两 app；`risk-api` 一次性拆分，五个消费方同分支改）

- 分支：`refactor/step-06-risk`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step06`；起点：`refactor-main@b5826639`（00–05、07、09、10 已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 06 步；设计 [07-risk-split.md](../design/07-risk-split.md)；决定 D38（结算控制归合规）、D40（`risk-api` 一次性拆分，本步是唯一允许同时改多个系统的步骤）、D42（争议 7 表归 dispute 库）。
- **串行执行**，不与其他搬迁步并行。

## 1. 输入文档（先完整读取）

- [design/07-risk-split.md](../design/07-risk-split.md) 全文。
- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md)、[design/02-archunit-rules.md](../design/02-archunit-rules.md)、[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节 risk-decision / compliance / dispute 行、第 2 节 Risk 85 表（结算控制 3 表归 compliance，争议 7 表仅 Schema 归 `mor_dispute`）。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4（Risk 行）、2.8、3.6（第 21–23 行）、3.8、4.3（risk 生成器与 9 个测试）；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 1 节 Risk 行、2.4、3.1（risk-decision / compliance 行）、3.2、4.1–4.3、5.1、6。
- [analysis/risk.md](../analysis/risk.md) 全文（第 5 节 Java 调用、第 6 节共享类型、第 7.2 节 `risk-api` 五个消费者的 import 统计、第 9 节热点）。
- 第 00、01、02c、03、04、05 步报告（五个消费方的新模块位置：`paytech/client-master`、`paytech/acquiring`、`paytech/payment`、`paytech/operations-console-bff`、`paytech/client-console-bff`）。
- 现有 `risk-compliance-service/`：`RiskModuleBoundaryAutoTest`、`RiskDatabasePrecondition`、`MockTestBase`、`risk-web` 的全部配置类、`ClientReviewResultRabbitPublisher`、`RiskOperationRequestContextParser(Impl)`、`docs/database/*`、`scripts/pdmaner/risk/*`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`。

## 2. 模块映射

| 现 | 目标 |
|---|---|
| `risk-api` 的 `api.decision`、`api.evaluation`（含 `ManualPayoutEligibilityApi`）、`api.control`、相关枚举与 `api.validation` | `paytech/risk-decision/paytech-risk-decision-api`，包 `com.paytech.risk.decision.api.*` |
| `risk-api` 的 `api.compliance`、`ClientReviewErrorCode`、`MaterialQualificationType` | `paytech/compliance/paytech-compliance-api`，包 `com.paytech.compliance.api.*`；另新增结算控制合同（第 3 节第 2 条） |
| `risk-decision` + `risk-control` + `risk-web` 中 decision 侧 Processor / 装配以外代码 | `paytech-risk-decision-core`；Controller、启动类、配置 → `paytech-risk-decision-app` |
| `risk-compliance` + `risk-web` 中 compliance Controller、`ClientReviewResultRabbitPublisher` | `paytech-compliance-core` / `paytech-compliance-app` |
| `risk-test` | 按 10 §2.4 拆：compliance 8 类 97 例、risk-decision 55 类 641 例（源码核实总数 738） |
| `docs/database`、`RISK_COMPLIANCE_PHYSICAL_DECISIONS.md`、10 个 Manifest | 按 07 §7 拆为 `risk-decision.pdma.json`（Manifest 02、03、04、05、09、10 + 08 补偿部分）、`compliance.pdma.json`（01、06 结算控制、08 补偿部分）、`paytech/dispute/docs/database/dispute.pdma.json`（07）；Schema SQL 三份切片（DDL 逐字复制）；`2026-09-11-risk-rule-v1-configuration.sql` 归 risk-decision |
| `risk-api` 旧坐标 | **删除**（D40），不保留聚合 POM |

删除 `risk-compliance-service/mvnw*`；旧根消失。`paytech/pom.xml` 取消 `risk-decision`、`compliance` 两行注释（`dispute` 只有 docs，不加模块）。应用名 `risk-decision-service`（18104 / 19104，现 `risk-service`）、`compliance-service`（18112 / 19112）；库 `mor_risk_decision`（现 `mor_risk` 改名）、`mor_compliance`、`mor_dispute`（仅 Schema 归属，本步只生成切片 SQL 与模型，不建库）。

## 3. 代码与逻辑改动清单（逐条引用 07 文档）

1. **`risk-control` 并入 risk-decision**（07 §1）：名单与 Manual Payout 资格 Processor 进入 risk-decision-core；`t_settlement_risk_assessment` 归 decision。
2. **结算控制归 compliance**（07 §2，D38；代码尚未实现，本步只定合同与 Schema 归属）：`paytech-compliance-api` 新增 `SettlementControlApi`：`POST /compliance/v1/settlement-controls`（幂等键 `sourceActionEffectId`）、`GET /compliance/v1/settlement-controls?ownerType=&ownerId=&status=ACTIVE`；DTO 字段与三表列一致；本步**不实现 Controller 与 Processor**，只提供合同与 compliance 库 Schema 中的三表；事件 `SettlementRestrictionChanged` 的 Payload 类放 compliance-api 并在 `catalog/events.yaml` 核对登记（producer compliance，consumers payout、account，strictOrder=true）。
3. **跨库逻辑引用只存 ID**（07 §3）：`t_qualification_decision.decision_evaluation_id` 保留列不联表。
4. **`risk-api` 一次性拆分**（07 §4，D40）：删除 `risk-api`；同分支修改五个消费方的 Maven 坐标与 import：`paytech/client-master`（入驻审核登记 / 结果消费）、`paytech/acquiring`（`RiskDecisionApi` CHECKOUT_PREPARE）、`paytech/payment`（`RiskDecisionApi` PAYMENT_ATTEMPT_RISK）、`paytech/operations-console-bff`、`paytech/client-console-bff`。每个消费方只改依赖坐标、import 包名与服务发现名（`risk-service` → `risk-decision-service` / `compliance-service`），**不改任何业务代码**；五个消费方的 `-test` 全部重跑通过是合并门禁。
5. **装配拆分**（07 §5）：两个 app 各自 DataSource / 事务管理器 / `@MapperScan`（`mapper/decision/**` 与 `mapper/compliance/**`）/ TypeHandler（decision 40 余个枚举）/ Snowflake worker / `Clock` / 补偿配置（`instance-id` 各自）/ `web.logging.metadata-only-path-prefixes` 各列各的；`RiskOperationRequestContextParser` 改用第 01 步下沉到 `paytech-mvc-web-starter` 的 `OperationRequestContextParser`，删除 Risk 副本；`onboarding.review.enabled`、`onboarding.materials.enabled` 随 compliance；compliance 读取 `decision.identifier.*` 的属性改为 `compliance.identifier.*`。
6. **MQ**：`ClientReviewResultRabbitPublisher` 随 compliance，改用 `paytech-messaging` 的 `DomainEventPublisher` 发布 `ClientReviewDecided` 新信封到 `compliance.events`，**同时继续向旧 Topic 发布**（client-master 消费者第 08 步切换）；事件目录中 `ClientReviewDecided` 的 aggregateType / versionField 按实现核对修正。
7. **Enforcer**（09 §1.4）：Risk 禁令复制到两个聚合 POM，模块 parent = 服务聚合 POM。
8. **路径修复**（09 §3.6、3.8、4.3）：`test-environment/scripts/initialize.py` 第 21–23 行与 `test_risk_database_initialization.py` 第 124–125、132–133 行改为两库各自路径（`mor_risk` 单库初始化改为两库属部署面变化，本步把脚本改成"两库各自 Schema + 初始化"，并在报告列出 compose 服务 `risk-service` 拆为两个服务的待办——compose 与 `build.sh` 本步不改，由主会话统一处理）；`scripts/pdmaner/risk/*` 生成器与 9 个 pdmaner 测试按新路径与三个模型拆分；`CODE-MOR:RISK:*` 哈希 Schema 标识与 `SETTLEMENT_ELIGIBILITY_INPUT:1` 不变（07 §7）。

## 4. 测试要求（10 §2.4、07 §6）

- 基线：搬迁前 `cd risk-compliance-service && ./mvnw -B verify` 实际数（源码核实 738）。
- compliance 8 类 97 例、risk-decision 55 类 641 例整类搬入；`RiskModuleBoundaryAutoTest` 改名 `RiskDecisionArchitectureTest` 并按新结构重写，compliance 建 `ComplianceArchitectureTest`；两侧各一份 `WebLoggingBoundaryAutoTest`；`RiskDatabasePrecondition` 拆为 `RiskDecisionDatabasePrecondition`（`mor_risk_decision` / `risk_decision_app`，表数 = decision DDL 表数，保留 `t_rule_set` 列、索引、`t_action_version` 行数校验）与 `ComplianceDatabasePrecondition`（`mor_compliance` / `compliance_app`，表数 = compliance DDL 表数含结算控制 3 表）；删除 `t_command_idempotency` 列校验；`MockTestBase` 拆两份并补全局 `@BeforeEach` 空基线与 `@AfterEach` 异步停止 / 意外调用检查。
- 新增：`ClientReviewResultListener`（client-master 侧）仍收到旧 Topic 报文的断言保持；compliance 侧新增"新信封已发布到 `compliance.events`"断言；两侧各一个 `ArchitectureAutoTest`。
- 清理器：按 10 §3.1 两份；`init/ddl.sql` 两份按 10 §4.2（Risk 今天无测试 DDL，本步补齐：切片自 `risk-compliance-schema.sql`，加 `t_compensation_event`、`t_consumed_event`，含 `t_action_version` / `t_action_parameter_definition` 目录种子）。
- 本地库：`mor_risk` → `mor_risk_decision`（新建并应用 DDL，不删旧库）、新增 `mor_compliance` 与两个账号（init 脚本、`.env.example`、手工执行，命令写进报告）。
- 五个消费方：各自 `-test` 全部通过（client-master、acquiring、payment、两个 BFF），数量不低于各自上一步报告的数字。
- 禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单

1. 前后测试数；`cd paytech && ./mvnw -B verify` **全 Reactor** 全绿（本步改了五个消费方，必须全量）。
2. 两个 app 以 `mock` Profile 启动 health UP。
3. `node --test scripts/pdmaner/risk-*.test.mjs`（改名后）通过；`python3 -m unittest test_risk_database_initialization` 可运行的部分记录结果。
4. `git log --follow` 抽查 decision 与 compliance 各一个 Java 文件；旧根与 `risk-api` 目录不存在；全仓 `grep -r "com.paytech.risk.api" paytech/` 为零。
5. R4 零违规。

## 6. 禁止事项

- 不改规则引擎、评估、审核、名单任何业务行为；不实现结算控制与争议代码；不改五个消费方除坐标 / import / 发现名之外的任何代码。
- 不执行业务库 SQL（测试库除外，须记录）；不改 `test-environment` compose 与 `build.sh`。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-06): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/06-risk-decision-compliance-report.md`：改动摘要 ≤ 30 行、第 5 节逐项（含五个消费方各自测试数）、两个 api 的合同清单、compose / build.sh 待办、与 07 文档偏差、待确认项。
3. 最终回复两行。

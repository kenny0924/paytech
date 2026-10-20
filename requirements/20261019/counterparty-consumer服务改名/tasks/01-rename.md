# 任务卡 01 · consumer → counterparty 改名（阶段 3 + 4 + 5）

- 需求：[requirement.md](../requirement.md)（S1–S7、AC-01—06）；决定：`agent-docs/decisions/2026-10-19-counterparty-repositioning.md`。
- **工作位置**：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-counterparty`，分支 `feature/counterparty-rename`，起点 `e174c79e`。所有代码改动只在这个 worktree 里做；可在该分支上提交（worktree 有独立 index，可用 `git mv`）。**不要**改主工作树 `/Users/kenny/Desktop/Workspace/Code-MoR` 里除本需求目录以外的任何文件；不推送；不碰 mor-test（`test-environment` 的配置文件可以在 worktree 里改，但不执行任何 docker / compose 命令）。
- 本需求目录（报告、SQL）写在**主工作树**：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261019/counterparty-consumer服务改名/`。
- 只改名、不改行为。

## 1. 改名规则

| 对象 | 旧 | 新 |
|---|---|---|
| 模块目录 | `paytech/consumer` | `paytech/counterparty` |
| Maven artifactId | `paytech-consumer`、`paytech-consumer-{api,core,app,test}` | `paytech-counterparty`、`paytech-counterparty-{api,core,app,test}`；`paytech/pom.xml` modules 同步 |
| Java 包 | `com.paytech.consumer` | `com.paytech.counterparty` |
| 服务身份类 | `ConsumerApplication`、`ConsumerTestApplication`、`ConsumerCustomerApi`、`ConsumerCustomerController`、`ConsumerErrorCode`、`ConsumerApiErrorConfiguration`、`ConsumerIdentifierProperties`、`ConsumerJsonConfiguration`、`ConsumerPersistenceConfiguration`、`ConsumerRuntimeConfiguration`、`ConsumerTransactionExecutor`、`ConsumerUpstreamProtocolException`、`ConsumerMockTestBase`、`AbstractConsumerInternalApiTest` 等 | 前缀改 `Counterparty`（如 `CounterpartyApplication`、`CounterpartyCustomerApi`、`CounterpartyErrorCode`） |
| 业务概念名（**不改**） | `Customer*`、`consumerReference`、`ConsumerReferencePolicy`、`CustomerStatus`、`CardSavingIneligibleReason`、错误码字符串值（`CUSTOMER_NOT_FOUND` 等）、Client API 路径 `/client-gateway-api/v1/customers…` | 不变 |
| 已存卡 client | `integration/payment/PaymentStoredCardClient(Impl)` | `integration/card/StoredCardClient(Impl)`（它调的是 card-service） |
| 应用名 / 发现名 | `consumer-service` | `counterparty-service`（端口 18109 / 19109 不变）|
| 内部合同前缀 | `/consumer/v1` | `/counterparty/v1` |
| 配置前缀 / 环境变量 | `consumer.*`、`MOR_CONSUMER_DB_*` 等 | `counterparty.*`、`MOR_COUNTERPARTY_DB_*` |
| 库 / 账号 | `mor_consumer` / `consumer_app` | `mor_counterparty` / `counterparty_app` |
| PDManer | `docs/database/consumer.pdma.json`、`sql/consumer-schema.sql`、`sql/consumer-initial-data.sql`、`CONSUMER_PHYSICAL_DECISIONS.md`、`scripts/pdmaner/generate-consumer-model.mjs`、`consumer-model.test.mjs`、`consumer-scenario-doc.test.mjs`（若引用） | `counterparty.*` 同名替换；`commerce-model-core.mjs` 中 consumer 家族条目同步；`pdmaner-canonical-paths.test.mjs` 的 consumer 行改为 counterparty。模型内部 name 用服务名 `counterparty`；表结构、字段、注释不变（注释里"Commerce Customer"之类的历史措辞可保留） |
| 补偿事件 | `STORED_CARD_INVALIDATION` 类型不变；`compensation-event-catalog.yaml` 随目录移动 |

调用方：
- acquiring：`ConsumerClientConfiguration`、`integration/consumer/ConsumerCustomerClient(Impl)` 改为 counterparty 命名（包 `integration/counterparty`）；application.yml 中的发现名 / base-url / 配置键；POM 依赖坐标；`CreateCheckoutProcessor`、`CardSavingEligibility` 的引用；测试 `MockConsumerApi`、`ConsumerMocker`、`application-mock.yml`；README。acquiring 内部表与类里表示"按订单的消费者"（`t_consumer_context`、`ConsumerContext`、`SaveConsumer*`、`ConsumerProtectionPort`）**不改**。
- card：`CardApiConstants.CONSUMER_SERVICE` → `COUNTERPARTY_SERVICE = "counterparty-service"`（调用方白名单），`StoredCardApi` 注释与引用。
- 其他：arch-rules（`table-ownership.yaml`、`known-deviations.yaml`、生成脚本中的 consumer 条目；`TableOwnership` 若有硬编码）、instance-manifest `catalog/services.yaml`、`local-environment`（`mysql/init/01-create-databases-and-users.sh`、`.env.example`、README）、`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`、`test-environment`（`compose.applications.yaml` 服务名 / 镜像名 / JAR / 环境变量 / `COMPENSATION_INSTANCEID` / `XXL_JOB_EXECUTOR_APPNAME` / 健康探针 URL；`scripts/build.sh`、`initialize.py`、`test_deploy_script.py`、`test_java_image_health_probe.py`、`java/LocalHttpHealthProbe.java` 允许列表、README）、`scripts/moved-links-map.json`（如有 consumer 路径映射）、`paytech/payment/docs/development/command-idempotency-differences.md` 中的现行描述（如只是历史叙述则不改）。
- 服务 README 重写为 counterparty，并修正"已存卡在 payment-service"为 card-service。
- **不改**：`AGENTS.md`、`agent-docs/CURRENT_IMPLEMENTATION.md`、`requirements/README.md`、`agent-docs/decisions/*`、历史需求目录 `requirements/20261017/**`、`requirements/20261018/**`、`design-discuss/**`（这些由主会话处理或属于历史记录）。

## 2. 本机数据库（local-environment，已授权）

在 `requirements/.../db/local/` 写三份 SQL 并执行（root 连接 127.0.0.1:13306，凭据在主工作树 `local-environment/.env.local`，只读取所需键，不打印密码）：
1. `01-create.sql`：建 `mor_counterparty`（utf8mb4 / utf8mb4_0900_ai_ci）、建账号 `counterparty_app` 并授权（权限与现 `consumer_app` 对 `mor_consumer` 一致；密码规则参照 init 脚本与 `.env.example` 的做法，新增 `MOR_COUNTERPARTY_DB_PASSWORD` 键时写入主工作树 `local-environment/.env.local`——该文件 gitignored，只追加新键，不改其他行）。
2. `02-schema-and-copy.sql`：在 `mor_counterparty` 按新 Schema 建表，再 `INSERT … SELECT` 从 `mor_consumer` 逐表复制（`t_customer`、`t_compensation_event`）。
3. `03-verify.sql`：逐表行数对比。另写 `99-rollback.sql`（DROP 新库与账号；旧库未动）。
旧库 `mor_consumer` 与 `consumer_app` **保留不删**。

## 3. mor-test SQL（只写不执行）

在 `requirements/.../db/mor-test/` 写同样结构的四份 SQL（建库建账号、建表复制、核对、回滚），容器内库名与账号按 `test-environment` 现有约定；mor-test 的账号密码来源照 `test-environment` 现有做法说明（不要写入真实密码）。迁移时 counterparty-service 未启动、consumer-service 已停，避免双写。

## 4. 验证（阶段 5）

- `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven 本地库 `~/.m3/repository`；测试需要的 DB / Redis 密码从主工作树 `local-environment/.env.local` 读取对应键（不打印）。
- 先记录改名前基线：`cd paytech && ./mvnw -B -o verify -pl consumer/paytech-consumer-test,acquiring/paytech-acquiring-test,card/paytech-card-test -am` 的测试数（如 card 测试模块名不同按实际）。
- 改名后：同范围测试全绿、测试数不减；**全 Reactor** `./mvnw -B -o verify` 全绿（或列出与本改动无关、改动前就失败的项并给出证据）。
- `node --test scripts/pdmaner/*.test.mjs` 中与 counterparty / commerce / canonical-paths 相关的测试通过；`npm --prefix requirements-site test` 跑一遍，报告结果，改动前就失败的项单列。
- AC-01 检查：`grep -rIl --exclude-dir={node_modules,target,.git} -E 'consumer-service|mor_consumer|consumer_app|paytech-consumer|com\.paytech\.consumer|/consumer/v1|MOR_CONSUMER_DB' .`，结果只剩第 1 节"不改"列出的路径；把命令与剩余清单写进报告。
- 本机运行冒烟：用 `paytech-counterparty-app` 的可执行 JAR 以本机配置启动（端口 18109），确认健康检查 UP，`POST /counterparty/v1/customers/resolve` 对 `mor_counterparty` 建或取成功，然后停掉。不要启动或停止其他会话的进程。

## 5. 交付

1. 在分支 `feature/counterparty-rename` 上提交（可多个提交），提交信息中文，末尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 把阶段 3 设计（改名映射表、每个文件的处理归类、SQL 说明与执行顺序、恢复方式）追加到主工作树 `requirements/.../technique.md`。
3. 报告写入主工作树 `requirements/.../tasks/01-rename-report.md`（用 Bash heredoc 写文件）：改动摘要 ≤ 30 行、提交号、基线与改名后测试数、全 Reactor 结果、AC-01 grep 结果、本机迁库行数、冒烟结果、与本卡的偏差、待主会话处理的项。最终回复只需一句话指向报告路径。

# Payment Orchestration Service

开发从 [写接口开发规范](docs/development/processor-conventions.md)和 [pay 阅读说明](docs/pay-interface-walkthrough.md)开始。写接口规范以当前 Pay 为基线，包含 API 到 Mapper 的分层职责、代码示例、开发顺序和评审清单；Payment 的明确局部约定在文档中列明，其余规则继续遵循工作区的[开发规范](../agent-docs/DEVELOPMENT_CONVENTIONS.md)与[Java 工程规范](../agent-docs/JAVA_PROJECT_STRUCTURE.md)。实现、验证与数据库迁移状态统一见[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 模块边界

| 模块 | 职责 |
|---|---|
| `paytech-payment-api` | 私有 HTTP RequestBody、ResponseBody 与 HttpExchange 合同 |
| `paytech-payment-execution` | 支付意图、支付受理、Action Flow、Context、本地事务和持久化 |
| `paytech-payment-web` | 唯一启动入口、REST Controller、协议转换和基础配置 |
| `paytech-payment-routing` | RouteService 接口与当前 Mock 选路 |
| `paytech-payment-channel` | Channel/Account/MID/Capability/Detail 五表所有权与两个公开查询；渠道操作和恢复执行另行接入 |
| `paytech-payment-card` | CardService 与显式启用的合成卡 Mock，真实受控卡管理待接入 |
| `paytech-payment-test` | 独立测试模块；正式应用入口、真实本地数据库、外部 HTTP DTO Mock 与架构门禁 |

Web 依赖 API、Execution 和 Card 的协议转换类型；Execution 通过公开的 RouteService、CardService 单向依赖 Routing、Card，Web 同时装配 Channel；Channel 不依赖其他 Payment 模块。模块共用一个应用进程、Payment 数据库和事务管理器，Routing/Card 不反向依赖 Execution，也不接收其 DataObject。卡输入仅经过专用申请请求交给 Card，通用 pay、Execution Context 和仓储不接收卡原值。数据库设计文件不参与应用启动，不自动建表或初始化。pay 使用 Attempt 的 `source_submission_id` 唯一索引防重，重复请求核对原提交内容后返回当前状态，规则见 [pay 阅读说明](docs/pay-interface-walkthrough.md)。Payment 不再依赖 Foundation 命令幂等组件。

## 构建

使用 JDK 25、仓库 Maven Wrapper，以及可解析的 PayTech Parent/BOM/Foundation 制品。生产构建（跳过测试源码和测试执行）：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean package -Dmaven.test.skip=true
```

Windows 使用 `mvnw.cmd` 并设置对应 `JAVA_HOME`。测试实现及验证范围以当前台账为准；清空前的测试记录不代表重建代码已通过业务验收。

## 启动前置

所有配置按模块统一维护，字段、Nacos 加载方式和生效范围见[配置清单](docs/development/configuration.md)，服务配置模板见 [payment-orchestration-service.yaml](config/nacos/payment-orchestration-service.yaml)。默认沿用本地配置，设置 `SPRING_PROFILES_ACTIVE=nacos` 后导入 Nacos；模板需要单独维护到对应环境。

准备符合[当前 PDManer](docs/database/payment-orchestration.pdma.json)的 Payment 业务表。DDL 只能按照[人工迁移说明](../requirements/20260913/payment-Execution设计/db/execution-simplified-migration.md)核对目标后手工执行。pay 不再读取或写入 `t_command_idempotency`，旧命令表的列迁移不再是运行前置；本轮保留既有模型和 Schema，未执行删表。应用不迁移旧库，也不提供 Intent 或工具准备接口的默认替身。

配置本服务的数据库、Redis 和独立 Snowflake 节点：

- `MOR_PAYMENT_DB_URL`、`MOR_PAYMENT_DB_USERNAME`、`MOR_PAYMENT_DB_PASSWORD`
- `MOR_REDIS_HOST`、`MOR_REDIS_PORT`、`MOR_REDIS_PASSWORD`
- `PAYMENT_SNOWFLAKE_REGION_ID`、`PAYMENT_SNOWFLAKE_WORKER_ID`

```bash
"$JAVA_HOME/bin/java" -jar paytech-payment-web/target/paytech-payment-web-1.0.0-SNAPSHOT-exec.jar
```

默认监听 `127.0.0.1:18103`，Context Path 为 `/payment`。pay 的应用路径为 `POST /payment/payment-execution/v1/intents/{intentId}/pay`，不通过公共 Gateway 暴露。

查询 Intent 使用 `GET /payment/payment-execution/v1/intents/{intentId}?clientId=101`，返回 `intentId/sourceType/sourceId/status/activeAttemptId/successfulAttemptId`。只读 Intent 并校验 Client 归属，成功返回 200，不存在或跨 Client 返回 404；来源字段取自创建时冻结的业务身份。完整语义见[查询接口合同](../requirements/20260913/payment-Execution设计/get-intent-amendment.md)。

创建 Intent 使用 `POST /payment/payment-execution/v1/intents`，请求字段及重放语义见[创建接口合同](../requirements/20260913/payment-Execution设计/create-intent-amendment.md)。按来源类型与来源 ID 防重，无额外幂等 Header；仅写 Intent，返回 `intentId/status`。入口沿用公共锁模板、完整 Context 与 Service 本地事务，未接入自动到期推进或 Commerce 真实调用。

终止 Intent 使用 `POST /payment/payment-execution/v1/intents/{intentId}/terminate`，Body 为 `clientId/reason`，无需幂等 Header，返回 `intentId/status/decision`。与 pay 共用 Intent 资源锁；安全终止只更新 Intent，已取消/过期直接返回，活动尝试返回 PENDING，授权等待返回 REVERSAL_REQUIRED，已收款返回 ALREADY_PAID；正常业务结果统一使用 HTTP 200，由上游判断 decision。完整边界见[终止接口合同](../requirements/20260913/payment-Execution设计/terminate-intent-amendment.md)；不自动撤销授权、退款或后台重试。

`applyCardInstrument` 使用 `POST /payment/payment-instrument/v1/card-tokens`，完整请求和重放规则见[工具申请合同](../requirements/20260913/payment-Execution设计/apply-card-instrument-amendment.md)。来源提交范围一致时返回原 Token 和到期时间；新申请由 Processor 开启一个事务，依次调用 CardService 的本地申请和 Execution Service 的工具绑定保存，不创建 Attempt 或 Action。两个 Service 不互相调用，Execution Service 的该方法直接参与外层事务。Card 仍是无外部访问的本地 Mock；未来外部准备由 Card Processor 在事务外完成。

本地合成流程需显式设置 `PAYMENT_CARD_MOCK_ENABLED=true`、合成测试值 `PAYMENT_CARD_MOCK_FINGERPRINT_KEY`（风控卡指纹的 HMAC 密钥，缺失则启动失败）和正的 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW`（ISO 8601 Duration，例如 `PT5M`）。默认关闭 Mock，窗口默认 `PT0S` 不接受新申请。Mock 仅接受 `CARD_TEST`、公开测试卡号 `4111111111111111`、有效期 `12/2030`、姓名 `MOCK CARD`；安全码按接口格式传入合成值。它不保存 PAN/CVV、不准备真实用卡材料，真实 Card 尚未接入；不要使用真实卡输入。

新 Attempt 在 Context 构建后调用 RouteService，再把渠道三元组随受理事务保存，状态仍为 PROCESSING。按本期用户要求，RouteServiceImpl 暂时固定返回合成 Channel/Account/MID `9001/9002/9003`，没有校验真实渠道配置或可用性；接通真实资金前必须替换该 Mock。保存上下文后单独创建 PROCESSING 状态的 PAY_RISK 并绑定 Attempt 的执行位，再通过 ActionFactory 取得实现并直接执行。PAY_RISK 调用 Risk 统一评估（[需求](../requirements/20261015/payment-接入Risk统一评估/technique.md)）：放行按认证要求进入 AUTHENTICATE、AUTHORIZE 或 PAY，其他结论或未取得结论时 Attempt 失败并释放 Intent；认证与授权能力目前明确返回未接入，原 Action 保持 UNKNOWN。真实选路、选择证据与 Owner 调用仍为 TODO。调用需要预先存在有效且匹配的 Intent 和交易级工具 Token。

ActionExecutionService 通过 ActionFlowFactory 选择 CARD:PAY；ActionFlow 调用 Actor，统一保存结果并推进下一步。Actor 异常转为 UNKNOWN，保存成功后 pay 返回 202 / PROCESSING。动作定义和事务顺序见 [Action Flow 修订](../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)。动作执行不登记补偿事件；创建和后继切换使用 active_action_id 与版本条件更新，等待或未知结果保留原执行位。回调和恢复入口仍待后续接入。旧动作名称切换见[迁移约束](../requirements/20260913/payment-Execution设计/db/card-pay-action-migration.md)。

`/payment/actuator/health` 及其 `/liveness`、`/readiness` 子路径提供进程与依赖健康信息，不能证明支付链路已经完成验收。统一 Request ID、Problem 错误体与元信息访问日志由 Foundation 提供；请求 Body、Token、查询参数和交互地址不记录到普通日志。

## Channel 配置查询

[实施合同](../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)定义 `QueryMidCandidatesProcessor` 和 `GetChannelExecutionConfigProcessor` 的内部 `process` 入口。前者输出指定 OE 的候选三元组与能力；后者在核验冻结身份后，通过事务外 Vault Reveal 返回受控账号配置。无 HTTP 路由、运营 CRUD 或交易流水。现有 Routing 占位逻辑仍需单独接入。

测试依赖仓库 mor-local MySQL/Redis。先人工审查 [隔离库 DDL](paytech-payment-test/src/test/resources/init/ddl.sql)，仅初始化 `mor_payment_channel_test` 及受限账号；应用和测试不会自行创建或迁移表。测试读取本机 local-environment/.env.local 的 Redis 凭据，只有空基线才接管并清理本例 Fixture；发现非空数据会拒绝运行。

使用上述 JDK 25 环境执行：

```bash
./mvnw -pl paytech-payment-test -am clean verify
```

外部 Vault 由测试模块中的独立 HTTP Server 提供合成 DTO，Payment 内使用正式 VaultClient；不连接真实 Vault 或渠道。聚合覆盖率在 `paytech-payment-test/target/site/jacoco-aggregate/index.html`，实际结果与数据库范围见[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。

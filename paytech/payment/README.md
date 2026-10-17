# Payment Service（payment）

- spring.application.name：`payment-service`（原 `payment-orchestration-service`）
- 端口：18103；管理端口 19103
- 数据库：`mor_payment`（第 12 步前含卡域 4 表）
- 来源现有工程：`payment-orchestration-service`（重构第 05 步拆出 [channel-connector](../channel-connector/README.md)）

开发从 [写接口开发规范](docs/development/processor-conventions.md)和 [pay 阅读说明](docs/pay-interface-walkthrough.md)开始。写接口规范以当前 Pay 为基线，包含 API 到 Mapper 的分层职责、代码示例、开发顺序和评审清单；Payment 的明确局部约定在文档中列明，其余规则继续遵循工作区的[开发规范](../../agent-docs/DEVELOPMENT_CONVENTIONS.md)与[Java 工程规范](../../agent-docs/JAVA_PROJECT_STRUCTURE.md)。实现、验证与数据库迁移状态统一见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 模块边界

| 模块 | 职责 |
|---|---|
| `paytech-payment-api` | 私有 HTTP RequestBody、ResponseBody 与 HttpExchange 合同（含 card 合同，第 12 步迁到 `paytech-card-api`） |
| `paytech-payment-core` | 支付意图、支付受理、Action Flow、Context、本地事务和持久化（包 `execution`）；路由选择（包 `routing`）；channel-connector 与 Risk 端口；`PaymentSucceeded` 补偿 Handler |
| `paytech-payment-card` | 卡加密存储（卡按卡号唯一、卡凭证、卡交易工具三层；每张卡一把 DEK，由 Vault 托管）、按明文卡号定位、CVV 的 Redis 一次性暂存、读卡与风控卡属性；第 12 步前仍是本服务内模块，只允许 core 与 app 依赖 |
| `paytech-payment-app` | 唯一启动入口、REST Controller、协议转换、数据源 / MyBatis 扫描、出站 HTTP 客户端与 RabbitMQ 发布 |
| `paytech-payment-test` | 独立测试模块；正式应用入口、真实本地数据库、外部 HTTP DTO Mock 与架构门禁（R1–R12） |

App 依赖 API、Core 和 Card 的协议转换类型；Core 通过公开的 CardService 及 Card 公开 Processor 单向依赖 Card。渠道候选与渠道操作经 `paytech-channel-connector-api` 的 HTTP 合同调用 channel-connector（服务发现名 `channel-connector-service`）：能力代码、Platform 输出键与 NOT_SENT 判定都由 channel-connector 解释，Payment 只消费已映射的结论（D06）。Payment 不再访问渠道五表，也不再直接调用 Platform 标准 API。Card 不反向依赖 Core，也不接收其 DataObject。卡输入仅经过专用申请请求交给 Card，通用 pay、Execution Context 和仓储不接收卡原值。数据库设计文件不参与应用启动，不自动建表或初始化。pay 使用 Attempt 的 `source_submission_id` 唯一索引防重，重复请求核对原提交内容后返回当前状态，规则见 [pay 阅读说明](docs/pay-interface-walkthrough.md)。Payment 不再依赖 Foundation 命令幂等组件。

## 构建

使用 JDK 25 与单一 Reactor 的 Wrapper（`paytech/mvnw`）。生产构建（跳过测试源码和测试执行），在 `paytech/` 下执行：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean package -Dmaven.test.skip=true -pl payment/paytech-payment-app -am
```

Windows 使用 `paytech/mvnw.cmd` 并设置对应 `JAVA_HOME`。测试实现及验证范围以当前台账为准；清空前的测试记录不代表重建代码已通过业务验收。

## 启动前置

所有配置按模块统一维护，字段、Nacos 加载方式和生效范围见[配置清单](docs/development/configuration.md)，服务配置模板见 [payment-service.yaml](config/nacos/payment-service.yaml)。默认沿用本地配置，设置 `SPRING_PROFILES_ACTIVE=nacos` 后导入 Nacos；模板需要单独维护到对应环境。

准备符合[当前 PDManer](docs/database/payment.pdma.json)的 Payment 业务表。DDL 只能按照[人工迁移说明](../../requirements/20260913/payment-Execution设计/db/execution-simplified-migration.md)核对目标后手工执行。pay 不再读取或写入 `t_command_idempotency`，旧命令表的列迁移不再是运行前置；本轮保留既有模型和 Schema，未执行删表。应用不迁移旧库，也不提供 Intent 或工具准备接口的默认替身。

配置本服务的数据库、Redis 和独立 Snowflake 节点：

- `MOR_PAYMENT_DB_URL`、`MOR_PAYMENT_DB_USERNAME`、`MOR_PAYMENT_DB_PASSWORD`
- `MOR_REDIS_HOST`、`MOR_REDIS_PORT`、`MOR_REDIS_PASSWORD`
- `PAYMENT_SNOWFLAKE_REGION_ID`、`PAYMENT_SNOWFLAKE_WORKER_ID`

```bash
"$JAVA_HOME/bin/java" -jar paytech-payment-app/target/paytech-payment-app-1.0.0-SNAPSHOT-exec.jar
```

默认监听 `127.0.0.1:18103`，Context Path 为 `/payment`。pay 的应用路径为 `POST /payment/payment-execution/v1/intents/{intentId}/pay`，不通过公共 Gateway 暴露。

查询 Intent 使用 `GET /payment/payment-execution/v1/intents/{intentId}?clientId=101`，返回 `intentId/sourceType/sourceId/status/activeAttemptId/successfulAttemptId`。只读 Intent 并校验 Client 归属，成功返回 200，不存在或跨 Client 返回 404；来源字段取自创建时冻结的业务身份。完整语义见[查询接口合同](../../requirements/20260913/payment-Execution设计/get-intent-amendment.md)。

创建 Intent 使用 `POST /payment/payment-execution/v1/intents`，请求字段及重放语义见[创建接口合同](../../requirements/20260913/payment-Execution设计/create-intent-amendment.md)。按来源类型与来源 ID 防重，无额外幂等 Header；仅写 Intent，返回 `intentId/status`。入口沿用公共锁模板、完整 Context 与 Service 本地事务，未接入自动到期推进或 Commerce 真实调用。

按来源提交查询原支付结果（EX-I06）使用 `GET /payment/payment-execution/v1/intents/{intentId}/submissions/{sourceSubmissionId}?clientId=101`，返回尝试 ID、支付方式、尝试状态、按币种精度的已捕获金额与币种；仅处理中且交互未过期时返回交互类型与截止时间（不返回 3DS 跳转地址），仅失败时返回最后动作的渠道失败码。Intent 不存在或跨 Client、该提交没有支付尝试（含只申请了工具）或属于其他 Intent 返回 404。见[技术方案](../../requirements/20261015/payment-支付结果查询与回传/technique.md) §2.1。

卡支付成功时，PAY 动作的成功事务内登记 `PAYMENT_SUCCEEDED` 补偿任务（`t_compensation_event`，事件键为尝试 ID），提交后由 `PaymentSucceededHandler` 从尝试、意图、成功 PAY 动作与渠道交易引用重建事件，经 RabbitMQ 双写：现有报文发布到 `topic_payment_result` / `payment.succeeded.v1`（Commerce 尚未切换），同一 Payload 以 `paytech-messaging` Schema v1 信封发布到 `payment.events`（`aggregateType=PAYMENT_ATTEMPT`、`aggregateId`=尝试 ID、`aggregateVersion`=尝试版本，design/08 §3），两者都收到 Broker 确认才删除任务。运行需 RabbitMQ（`PAYMENT_RABBITMQ_*`，见[配置清单](docs/development/configuration.md)）及 Payment 库中的 `t_compensation_event`。支付失败、处理中与授权不发布。

终止 Intent 使用 `POST /payment/payment-execution/v1/intents/{intentId}/terminate`，Body 为 `clientId/reason`，无需幂等 Header，返回 `intentId/status/decision`。与 pay 共用 Intent 资源锁；安全终止只更新 Intent，已取消/过期直接返回，活动尝试返回 PENDING，授权等待返回 REVERSAL_REQUIRED，已收款返回 ALREADY_PAID；正常业务结果统一使用 HTTP 200，由上游判断 decision。完整边界见[终止接口合同](../../requirements/20260913/payment-Execution设计/terminate-intent-amendment.md)；不自动撤销授权、退款或后台重试。

`applyCardInstrument` 使用 `POST /payment/payment-instrument/v1/card-tokens`，完整请求和重放规则见[工具申请合同](../../requirements/20260913/payment-Execution设计/apply-card-instrument-amendment.md)。来源提交范围一致时返回原 Token 和到期时间；新申请由 Processor 开启一个事务，依次调用 CardService 的本地申请和 Execution Service 的工具绑定保存，不创建 Attempt 或 Action。两个 Service 不互相调用，Execution Service 的该方法直接参与外层事务。新申请在事务前由 Card 的 `PrepareCardInstrumentProcessor` 准备：按卡指纹查卡，已有则经 Vault 取回 DEK，没有则生成 DEK 交 Vault 并保存卡；按（卡、凭证摘要）查或建卡凭证；加密 CVV 存 Redis。卡与凭证单条插入、自动提交，事务内只插入卡交易工具。原提交重放由 `CheckCardInstrumentProcessor` 比较卡指纹与凭证摘要，不访问 Vault。详见[Card 加密存储方案](../../requirements/20261015/payment-Card加密存储/technique.md)。

本地运行需设置 `PAYMENT_CARD_FINGERPRINT_KEY`（卡指纹 HMAC 密钥，Base64、解码后至少 32 字节，缺失则启动失败）和正的 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW`（ISO 8601 Duration，例如 `PT5M`；默认 `PT0S` 不接受新申请），并在所连 Vault 注册数据类型 `PAYMENT_CARD_DEK` 及 Payment 的 `CARD_STORAGE` Protect、`CARD_USE` Reveal 访问规则。卡号须通过 Luhn 校验；卡号以本卡 DEK 加密存入 `t_payment_card`（按卡指纹唯一，BIN 按卡号长度存 8 或 6 位），有效期与持卡人加密存入 `t_payment_card_credential`，每笔交易一条 `t_payment_card_instrument`；CVV 加密后按交易工具只在 Redis 暂存到工具期限、领取一次即删除。

已存卡（[Customer 与存卡支付](../../requirements/20261015/commerce-Customer与存卡支付/technique.md) §2.4）：申请工具可带 `customerId` 与 `saveCard`，卡支付成功时在同一事务内标记凭证已验证并按同意生成已存卡（`t_payment_stored_card`，对外 `sc_` Token）；也可用 `storedCard{storedCardToken, cvv}` 代替手输卡。Commerce 通过 `GET /payment/payment-card/v1/stored-cards`、`DELETE /payment/payment-card/v1/stored-cards/{token}` 与 `POST /payment/payment-card/v1/customers/{customerId}/stored-cards/invalidation` 管理已存卡，响应只含 Token、前 6 后 4 与有效期。

新 Attempt 在 Context 构建后调用 RouteService，再把渠道三元组随受理事务保存，状态仍为 PROCESSING。按本期用户要求，RouteServiceImpl 暂时固定返回合成 Channel/Account/MID `9001/9002/9003`，没有校验真实渠道配置或可用性；接通真实资金前必须替换该 Mock。保存上下文后单独创建 PROCESSING 状态的 PAY_RISK 并绑定 Attempt 的执行位，再通过 ActionFactory 取得实现并直接执行。PAY_RISK 调用 Risk 统一评估（[需求](../../requirements/20261015/payment-接入Risk统一评估/technique.md)）：放行按认证要求进入 AUTHENTICATE、AUTHORIZE 或 PAY，其他结论或未取得结论时 Attempt 失败并释放 Intent；认证与授权能力目前明确返回未接入，原 Action 保持 UNKNOWN。真实选路、选择证据与 Owner 调用仍为 TODO。调用需要预先存在有效且匹配的 Intent 和交易级工具 Token。

ActionExecutionService 通过 ActionFlowFactory 选择 CARD:PAY；ActionFlow 调用 Actor，统一保存结果并推进下一步。Actor 异常转为 UNKNOWN，保存成功后 pay 返回 202 / PROCESSING。动作定义和事务顺序见 [Action Flow 修订](../../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)。除收款成功登记 PaymentSucceeded 发布任务外，动作执行不登记补偿事件；创建和后继切换使用 active_action_id 与版本条件更新，等待或未知结果保留原执行位。回调和恢复入口仍待后续接入。旧动作名称切换见[迁移约束](../../requirements/20260913/payment-Execution设计/db/card-pay-action-migration.md)。

管理端口 19103 的 `/payment/actuator/health` 及其 `/liveness`、`/readiness` 子路径提供进程与依赖健康信息，不能证明支付链路已经完成验收。统一 Request ID、Problem 错误体与元信息访问日志由 Foundation 提供；请求 Body、Token、查询参数和交互地址不记录到普通日志。

## 渠道配置与执行

渠道、账号、MID 与能力配置及其查询 / 执行入口已迁到 [channel-connector](../channel-connector/README.md)（[实施合同](../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)）。Payment 经 `ChannelCandidateQueryApi`（`POST /channel-connector/v1/mid-candidates`）取得候选并自行选择、冻结三元组，经 `ChannelOperationApi`（`POST /channel-connector/v1/operations`）执行渠道操作；两个调用都在数据库事务外、Redis Intent 锁内进行。

## 测试

测试依赖仓库 mor-local MySQL/Redis/RabbitMQ（凭据读取 `paytech.local-environment.dir` 下的 `.env.local`，默认仓库根 `local-environment/`）。先人工审查 [测试 DDL](paytech-payment-test/src/test/resources/init/ddl.sql)，把 15 张表应用到空的 `mor_payment`（`payment_app`）；应用和测试不会自行创建或迁移表。只有空基线才接管并清理本例数据；发现非空数据会拒绝运行。

在 `paytech/` 下使用上述 JDK 25 环境执行：

```bash
./mvnw -pl payment/paytech-payment-test -am clean verify
```

Vault（卡 DEK）、Risk 与 channel-connector 由测试模块中的独立 HTTP Server 以服务发现名承载合成 DTO，Payment 内使用正式客户端；不连接真实 Vault、channel-connector 或渠道。渠道操作含支付工具 Token 时，测试服务像真实 Platform 一样回调 Payment 读卡接口。聚合覆盖率在 `paytech-payment-test/target/site/jacoco-aggregate/index.html`，实际结果与数据库范围见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。

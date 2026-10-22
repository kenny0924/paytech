# Payment Service（payment）

- spring.application.name：`payment-service`（原 `payment-orchestration-service`）
- 端口：18103；管理端口 19103
- 数据库：`mor_payment`（第 12 步前含卡域 4 表）
- 来源现有工程：`payment-orchestration-service`（重构第 05 步拆出 [channel-connector](../channel-connector/README.md)）

开发从 [写接口开发规范](docs/development/processor-conventions.md)和 [pay 阅读说明](docs/pay-interface-walkthrough.md)开始。写接口规范以当前 Pay 为基线，包含 API 到 Mapper 的分层职责、代码示例、开发顺序和评审清单；Payment 的明确局部约定在文档中列明，其余规则继续遵循工作区的[开发规范](../../agent-docs/DEVELOPMENT_CONVENTIONS.md)与[Java 工程规范](../../agent-docs/JAVA_PROJECT_STRUCTURE.md)。实现、验证与数据库迁移状态统一见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 模块边界

| 模块 | 职责 |
|---|---|
| `paytech-payment-api` | 私有 HTTP RequestBody、ResponseBody 与 HttpExchange 合同（card 合同已于第 12 步迁到 `paytech-card-api`） |
| `paytech-payment-core` | 支付意图、支付受理、Action Flow、Context、本地事务和持久化（包 `execution`）；路由选择（包 `routing`）；channel-connector 与 Risk 端口；`PaymentSucceeded` 补偿 Handler |
| `paytech-payment-app` | 唯一启动入口、REST Controller、协议转换、数据源 / MyBatis 扫描、出站 HTTP 客户端与 RabbitMQ 发布 |
| `paytech-payment-test` | 独立测试模块；正式应用入口、真实本地数据库、外部 HTTP DTO Mock 与架构门禁（R1–R12） |

App 依赖 API 与 Core；卡域是独立的 card-service（design/05）：Core 经 `paytech-card-api` 的 HTTP 合同创建卡工具与申请一次性读卡票据（服务发现名 `card-service`），不触碰卡表、Vault 或 CVV。渠道候选与渠道操作经 `paytech-channel-connector-api` 的 HTTP 合同调用 channel-connector（服务发现名 `channel-connector-service`）：能力代码、Platform 输出键与 NOT_SENT 判定都由 channel-connector 解释，Payment 只消费已映射的结论（D06）。Payment 不再访问渠道五表，也不再直接调用 Platform 标准 API。卡输入仅经过专用申请请求原样转交 card，通用 pay、Execution Context 和仓储不接收卡原值。数据库设计文件不参与应用启动，不自动建表或初始化。pay 使用 Attempt 的 `source_submission_id` 唯一索引防重，重复请求核对原提交内容后返回当前状态，规则见 [pay 阅读说明](docs/pay-interface-walkthrough.md)。Payment 不再依赖 Foundation 命令幂等组件。

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

刷新支付结果使用 `POST /payment/payment-execution/v1/intents/{intentId}/submissions/{sourceSubmissionId}/refresh`（Body `clientId`），返回体与 EX-I06 相同：同一提交 5 秒内（Redis 限频）最多触发一次“确认支付结果”，其余请求直接返回当前状态。acquiring 收银台查询提交（`GET payment-submissions/{id}`）改为调用该接口。见 [Execution 3DS 修订](../../requirements/20261022/payment-3DS全链路/execution-3ds-amendment.md)。

卡支付成功时，PAY 动作的成功事务内登记 `PAYMENT_SUCCEEDED` 补偿任务（`t_compensation_event`，事件键为尝试 ID），提交后由 `PaymentSucceededHandler` 从尝试、意图、成功 PAY 动作与渠道交易引用重建事件，以 `paytech-messaging` Schema v1 信封发布到 `payment.events`（`aggregateType=PAYMENT_ATTEMPT`、`aggregateId`=尝试 ID、`aggregateVersion`=尝试版本，design/08 §3）；收到 Broker 确认且路由到订阅队列（acquiring 的 `acquiring.payment.p0..p3`）才删除任务，未路由按 FAIL 由补偿重试。发布任务经 `AggregateKeySequencer` 按尝试串行。旧 Topic 已于第 08 步停发。运行需 RabbitMQ（`PAYMENT_RABBITMQ_*`，见[配置清单](docs/development/configuration.md)）及 Payment 库中的 `t_compensation_event`。支付失败、处理中与授权不发布。

终止 Intent 使用 `POST /payment/payment-execution/v1/intents/{intentId}/terminate`，Body 为 `clientId/reason`，无需幂等 Header，返回 `intentId/status/decision`。与 pay 共用 Intent 资源锁；安全终止只更新 Intent，已取消/过期直接返回，活动尝试返回 PENDING，授权等待返回 REVERSAL_REQUIRED，已收款返回 ALREADY_PAID；正常业务结果统一使用 HTTP 200，由上游判断 decision。完整边界见[终止接口合同](../../requirements/20260913/payment-Execution设计/terminate-intent-amendment.md)；不自动撤销授权、退款或后台重试。

`applyCardInstrument` 使用 `POST /payment/payment-instrument/v1/card-tokens`，完整请求和重放规则见[工具申请合同](../../requirements/20260913/payment-Execution设计/apply-card-instrument-amendment.md)。第 12 步起为两段创建（design/05 §2.2）：事务外先调 card `POST /card/v1/instruments`（card 在自己的库内保存卡、凭证与工具，托管 DEK、暂存 CVV，按 Client 与来源提交幂等），再在本地事务按 Intent 状态插入 `t_payment_instrument_token`，保存 card 返回的工具引用、期限、风控属性（BIN、指纹）以及 Customer 与保存意愿；原提交重放以同一来源提交再调 card 核对同卡。第二段失败时 card 侧留下的孤立工具由 card 的回收任务清理。

pay 的 PAY 动作在发起渠道调用前（事务外、Intent 锁内）先做准入校验，再向 card 申请本次动作的一次性读卡票据（`POST /card/v1/material-tickets`），把票据作为标准 API 的卡字段句柄；card 拒签（CVV 已领取、工具过期）或不可用时动作以 `CARD_MATERIAL_UNAVAILABLE` 失败、不调用渠道。支付风控读取 Token 行中的 BIN 与指纹。支付成功事务只写本服务的表；凭证验证与存卡由 card 消费 `PaymentSucceeded` v2（新增 `instrumentRef`、`consentToSave`、`customerId`）完成，已存卡的列出、删除与失效改由 card-service 提供（`/card/v1/stored-cards…`）。

本地运行需设置正的 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW`（ISO 8601 Duration，例如 `PT5M`；默认 `PT0S` 不接受新申请）并能经服务发现访问 `card-service`；卡指纹密钥（`CARD_FINGERPRINT_KEY`）与 Vault `PAYMENT_CARD_DEK` 访问规则随卡域属于 card-service。


新 Attempt 在 Context 构建后调用 RouteService，再把渠道三元组随受理事务保存，状态仍为 PROCESSING。按本期用户要求，RouteServiceImpl 暂时固定返回合成 Channel/Account/MID `9001/9002/9003`，没有校验真实渠道配置或可用性；接通真实资金前必须替换该 Mock。保存上下文后单独创建 PROCESSING 状态的 PAY_RISK 并绑定 Attempt 的执行位，再通过 ActionFactory 取得实现并直接执行。PAY_RISK 调用 Risk 统一评估（[需求](../../requirements/20261015/payment-接入Risk统一评估/technique.md)）：放行后 AUTOMATIC 直接进入 PAY（MANUAL 进入 AUTHORIZE，仍为未接入的桩），卡流程不再使用 AUTHENTICATE；Risk 要求 `THREE_DS_REQUIRED` / `CHALLENGE_REQUIRED` 时 PAY 的渠道输入带 `payment.threeDsRequired=true`，由渠道在 PAY 内完成挑战，交互类型按渠道返回记 THREE_DS_CHALLENGE 或 REDIRECT。选路按 MID 的 `SECURITY.3DS_MODE` 依次优先 SUPPORTED、REQUIRED、未声明、UNSUPPORTED；要求 3DS 而冻结 MID 不是 SUPPORTED / REQUIRED 时，PAY 不调渠道、以 `THREE_DS_CHANNEL_UNAVAILABLE` 失败。其他结论或未取得结论时 Attempt 失败并释放 Intent（[Execution 3DS 修订](../../requirements/20261022/payment-3DS全链路/execution-3ds-amendment.md)）。真实选路、选择证据与 Owner 调用仍为 TODO。调用需要预先存在有效且匹配的 Intent 和交易级工具 Token。

ActionExecutionService 通过 ActionFlowFactory 选择 CARD:PAY；ActionFlow 调用 Actor，统一保存结果并推进下一步。Actor 异常转为 UNKNOWN，保存成功后 pay 返回 202 / PROCESSING。动作定义和事务顺序见 [Action Flow 修订](../../requirements/20260913/payment-Execution设计/card-pay-action-flow-amendment.md)。创建和后继切换使用 active_action_id 与版本条件更新，等待或未知结果保留原执行位。除收款成功登记 PaymentSucceeded 发布任务外，PAY 保存为 PROCESSING / UNKNOWN 时同事务登记 `PAY_RESULT_QUERY` 补偿任务（事件键为尝试 ID）。

“确认支付结果”（`ConfirmPayResultProcessor`）在 Intent 锁内（TRY_LOCK 500 ms）、事务外经 channel-connector `QUERY` + `ORIGINAL_TRANSACTION`（标准 API `PAY_QUERY`）查单，再以短事务经 ActionFlow 推进原 PAY：成功且金额币种一致 → SUCCEEDED；FAILED / CLOSED → FAILED（CLOSED 记 `CHANNEL_ORDER_CLOSED`）；无此交易且动作已超过 10 分钟 → FAILED（`CHANNEL_TRANSACTION_NOT_FOUND`）；处理中时 UNKNOWN 改回 PROCESSING；金额币种不一致不推进、转人工。只采信查单结果，不采信回跳参数与通知内容。入口有三个：`PayResultQueryHandler`（首次立即执行，之后 10 秒起 ×2 退避、上限 30 分钟、最多 56 次；动作创建 24 小时仍无结论则以 `PAY_RESULT_UNRESOLVED` 置 FAILED 转人工）、上述刷新接口，以及消费 channel-connector 的 `ChannelPaymentNotified`（`ChannelPaymentNotifiedListener`，`HANDLER_MANAGED`，队列 `payment.channel-connector.p0..p3`，DLQ `payment.channel-connector.dlq`；核对 Attempt 冻结的渠道与账号一致后才确认，消费登记写 `t_consumed_event` / `t_consumed_aggregate_version`）。渠道通知地址由 `payment.execution.channel-callback-base-url`（`PAYMENT_CHANNEL_CALLBACK_BASE_URL`）拼成 `{base}/api/channel-callbacks/v1/{channelCode}/{channelAccountId}` 作为 notifyUrl，为空时不传、只靠查单。旧动作名称切换见[迁移约束](../../requirements/20260913/payment-Execution设计/db/card-pay-action-migration.md)。

管理端口 19103 的 `/actuator/health` 及其 `/liveness`、`/readiness` 子路径提供进程与依赖健康信息（独立管理端口不带 `/payment` Context Path），不能证明支付链路已经完成验收。统一 Request ID、Problem 错误体与元信息访问日志由 Foundation 提供；请求 Body、Token、查询参数和交互地址不记录到普通日志。

## 渠道配置与执行

渠道、账号、MID 与能力配置及其查询 / 执行入口已迁到 [channel-connector](../channel-connector/README.md)（[实施合同](../../requirements/20260921/payment-渠道账号与MID能力查询/implementation-contract.md)）。Payment 经 `ChannelCandidateQueryApi`（`POST /channel-connector/v1/mid-candidates`）取得候选并自行选择、冻结三元组，经 `ChannelOperationApi`（`POST /channel-connector/v1/operations`）执行渠道操作；两个调用都在数据库事务外、Redis Intent 锁内进行。

## 测试

测试依赖仓库 mor-local MySQL/Redis/RabbitMQ（凭据读取 `paytech.local-environment.dir` 下的 `.env.local`，默认仓库根 `local-environment/`）。先人工审查 [测试 DDL](paytech-payment-test/src/test/resources/init/ddl.sql)，把 13 张表应用到空的 `mor_payment`（`payment_app`）；应用和测试不会自行创建或迁移表。只有空基线才接管并清理本例数据；发现非空数据会拒绝运行。

在 `paytech/` 下使用上述 JDK 25 环境执行：

```bash
./mvnw -pl payment/paytech-payment-test -am clean verify
```

card、Risk 与 channel-connector 由测试模块中的独立 HTTP Server 以服务发现名承载合成 DTO，Payment 内使用正式客户端；不连接真实 card、channel-connector 或渠道。渠道操作含读卡票据时，测试服务像真实 Platform 一样凭票据向 card 测试服务读卡，并断言票据只能使用一次。聚合覆盖率在 `paytech-payment-test/target/site/jacoco-aggregate/index.html`，实际结果与数据库范围见[当前台账](../../agent-docs/CURRENT_IMPLEMENTATION.md#channel-account-mid-query-requirement-20260921)。

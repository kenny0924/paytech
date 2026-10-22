# acquiring 订单与会话过期收尾 · mor-test 发布包

阶段 6 于 2026-10-22 20:15—20:40Z（+04 次日 00:15—00:40）做了以下工作：

- 只读核对 mor-test：`docker inspect / ps`，容器内 `printenv` 只取键名，容器内 `sha256sum`，MySQL 与 `xxl_job` 只读 SELECT，`rabbitmqctl list_*`，`compose.sh ... config` 只比较键名。
- 在本机构建两个候选镜像，只打本地 tag。
- 编写发布包。

**没有停启或替换任何容器，没有写任何库、XXL、Vault、RabbitMQ 或 Nacos，没有 docker cp，没有改 compose 文件本体，没有推送镜像，也没有提交 git。** 阶段 7 须用户另行授权，由主会话按第 6 节执行，结果写第 11 节。

## 0. 需要决定

| 编号 | 事实（阶段 6 核对） | 影响 | 建议 |
|---|---|---|---|
| D1 | **存量到期订单 16 笔**：均为 OPEN，于 10-17 06:31Z 至 10-20 03:52Z 到期。来源是历次验收脚本：`wh-query-` 7 笔、`idem-repl` 4 笔、`idem-para` 4 笔、`cp-smoke-` 1 笔，分属 7 个验收 Client。16 笔都没有支付投影、没有接受证据、没有 `termination_requested_at`，Payment 也没有按来源对应的 Intent。会话均为 OPEN | 任务启用后的第一轮扫描会走本地收尾分支，不调用 Payment。16 笔订单和会话将变为 EXPIRED，原因码 `EXPIRY_REACHED`。同时登记 16 个 `order.expired`，Platform 受理 16 行事件。这 7 个 Client 的 ACTIVE 端点都没有订阅 `order.expired`，**所以没有任何投递**。该操作不可逆，系统没有“取消过期”的操作 | 接受。这正是本需求要修复的状态，验收计划第 2 节专门核对这一轮。如果不希望这批订单被处理，没有可配置的排除方式，只能推迟启用任务 |
| D2 | **候选镜像的构建方式**：没有在干净 worktree 用 `test-environment/java/Dockerfile` 重新构建，而是以现网镜像 ID 为底，只覆盖 `/app/application.jar`。原因有三：本机没有 Dockerfile 所需的基础镜像 tag；BuildKit 无法引用本地镜像，联网拉取超时；`test-environment/artifacts/health-probe` 与运行容器内的 health-probe 不同（目录摘要 `21251080…` 对 `1b237ccd…`） | 镜像多一层，现网 JAR 留在下层，大小约多 100 MB。基础层、health-probe、Entrypoint、User、WorkingDir、Env 与现网完全相同。JAR 与阶段 5 验收的 JAR 逐字节相同 | 接受（仅 mor-test）。阶段 8 提交后，按第 2.4.3 节重建并比较 |
| D3 | **验收会写库与 MQ**：`flow_expired` 按技术方案 D5，用 SQL 回拨本次新建订单的 `expires_at`（`mor_acquiring`，只改一行一列）。同一脚本原有的 `order.paid` 夹具会插入投影，并向 `acquiring.payment.hash` 发 PaymentSucceeded v1，与 10-18 以来的历次运行相同。脚本还会新建一个入驻 Client、凭证与端点 | 留下合成数据（验收计划第 8 节） | 阶段 7 授权时一并确认 |
| D4 | **`flow_expired` 不会调用 Payment 新接口**：mor-test 没有 Hosted Checkout 站点，新订单没有投影和接受证据，只走本地分支 | 如果只跑脚本，Payment 的 `ORDER_EXPIRED` 原因和按来源查询在 mor-test 上没有直接证据 | 建议增加验收计划第 3 节的 P-1 探针场景：直连 Payment，用合成 Intent 验证终止原因、重复终止和按来源查询。会留下 2 笔合成 Intent。若不做，沿用阶段 5 S2 |
| D5 | XXL 任务在 Admin 侧的超时设置（任务卡只给了 Cron、路由和阻塞策略） | 单次扫描最多 50 × 200 = 10,000 单。mor-test 现在只有 16 单 | 超时 300 秒、失败重试 0、misfire `DO_NOTHING`，与 account / channel-connector 的任务一致（第 7 节） |

**观察项（不需要决定）：**

- **3DS 候选镜像的 tag 是错的。** 实际标签为 `mor-test/acquiring-serviceard3ds-20261022b:latest`、`mor-test/payment-serviceard3ds-20261022:latest`，构建时 `:c` 被 zsh 修饰符吞掉了。3DS release.md 记录的 `…:card3ds-20261022b` 并不存在。本包全部按 sha256 引用，不受影响。
- **XXL Admin 容器重启过。** 它于 19:56:01Z 重启，累计重启 12 次，不是本卡所为。重启后两个执行器的心跳和任务 4、9 的调度都正常。
- **acquiring 有 8 条旧的 FAILED 补偿任务。** 它们为 `SUBMISSION_EVENT_TYPE / INTERNAL_ERROR`，执行 5 次，产生于 10-17、10-18，与本需求无关，处于终止状态，不会被重试。
- **3DS 留下的 DLQ 消息已不存在。** 3DS 记录中残留的 `acquiring.payment.dlq` 1 条消息，现为 0 条。

## 1. 环境身份

| 项 | 值 |
|---|---|
| 环境 | `mor-test`（本机 Docker，Compose 项目 `mor-test`，入口 `test-environment/compose.sh`；它已经带上 `compose.yaml`、`compose.applications.yaml`、`compose.scheduler.yaml`） |
| 替换服务 | `payment-service`、`acquiring-service`。其余容器都不动，Platform 也不动 |
| 数据库 | `mor-test-mysql-1`，库 `mor_acquiring`、`mor_payment`、`mor_platform`、`xxl_job`，全部只读。**本需求无 DDL / DML** |
| XXL | `mor-test-xxl-job-admin-1`，Admin `http://127.0.0.1:28090`。阶段 7 新登记 1 个任务（第 7 节） |
| MQ | `mor-test-rabbitmq-1`，vhost `/mor-test`，不改 |
| 代码 | 候选工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-expiry`（detached，无分支）：`HEAD 22ede435e34c4a78beda42f0ff2c30c13ddf3210` 加本需求未提交改动，指纹见第 2.4.1 节。主工作区 main 当前为 `32dbdd18`，在 `22ede435` 之后只改了 PDManer 模型、card 测试和文档，不影响这两个 JAR |

## 2. 现网快照（只读，2026-10-22 20:19—20:32Z）

明细见 [snapshot/](./snapshot/)：`containers.txt`、`images.txt`、`jar-sha.txt`、`env-keys-*.txt`（只有键名）、`env-compare-*.txt`、`platform-webhook-caller.txt`、`xxl.txt`、`rabbitmq-queues.txt`、`precheck.out`。快照由 [scripts/snapshot.sh](./scripts/snapshot.sh) 生成，可重跑。

### 2.1 当前镜像 = 回滚目标

| 服务 | 运行镜像 | 来源 | 启动（UTC） | 重启 | 运行 JAR SHA-256 前 16 位 |
|---|---|---|---|---|---|
| payment-service | `sha256:4cf16f15d7840f4027f79c1a743f87d2325e57d4d37df558606085ef9c5e8356` | 3DS `card3ds-20261022`（S7-01 补键后由主会话重建） | 10-22 18:14:42 | 0 | `c452c35e0b9e060f` |
| acquiring-service | `sha256:3958983e2fa397ccca4415b9b373f24dd3b248fffb96752ca0e723d3cedacb21` | 3DS `card3ds-20261022b`（rel2） | 10-22 18:00:43 | 0 | `68a30b6cbd287210` |

**它们就是 3DS 发布后的镜像。** 镜像 ID 和 JAR 指纹与 3DS release.md 第 2.4.5 节的候选完全一致。两个容器的 compose 标签都显示，当前生效的覆盖文件是 `requirements/20261022/payment-3DS全链路/release/compose-candidate.yaml`（3DS 候选），之后没有其他会话替换过。两个镜像都在本机镜像库中，回滚可用。

### 2.2 环境变量（只比较键名与值是否相等，不输出值）

| 渲染来源 | acquiring（35 个应用键） | payment |
|---|---|---|
| 只用主 compose | 35 / 35 相同 | 33 / 33 相同，**容器多一个 `PAYMENT_CHANNEL_CALLBACK_BASE_URL`** |
| 主 compose + 3DS 候选（当前生效链） | 35 / 35 相同 | 34 / 34 相同 |
| 主 compose + **本包候选** | 35 / 35 相同 | 34 / 34 相同 |
| 主 compose + **本包回滚** | 35 / 35 相同 | 34 / 34 相同 |

容器另有 8 个镜像自带键（PATH、JAVA_HOME 等），不属于 compose。

- **需要继承的键只有一个：** 3DS 候选给 payment 加了 `PAYMENT_CHANNEL_CALLBACK_BASE_URL=""`（空值）。另一个键 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW=PT5M`（3DS 缺陷 S7-01）已由 `de4f7ba4` 写入主 compose。本包候选和回滚文件都显式带上前者，后者不重复写。
- **历次覆盖文件已全部检查：** 搜索了 `requirements/*/*/release*/compose*.yaml`，除 3DS 这两份以外，没有任何覆盖文件给这两个服务加过环境变量。
- **acquiring 执行器早已启用：** XXL 执行器（`XXL_JOB_EXECUTOR_ENABLED=true`、APPNAME `acquiring-service`、IP `172.30.73.30:9999`）由主 compose 提供，现网已启用。本需求的 `acquiring.order-expiry.*` 写在 `application.yml`，有默认值，不需要环境变量。

### 2.3 Platform、RabbitMQ 与 XXL

| 项 | 结果 |
|---|---|
| Platform Webhook 调用方（运行容器的 `SPRING_APPLICATION_JSON`） | `acquiring-service` 列表**包含 `order.expired`**，共 8 种事件。主 compose 第 232 行与它一致 |
| Platform 代码 | `WebhookEventType.ORDER_EXPIRED("order.expired", true)` 已存在于 `paytech-platform-support-api`。Platform 不替换 |
| RabbitMQ | `acquiring.payment.p0—p3`、`card.payment.p0—p3`、`payment.channel-connector.p0—p3` 各 1 个消费者，0 条消息。三个 DLQ 都是 0 条 |
| XXL 执行器分组 | `acquiring-service`（id 4）与 `payment-service`（id 9）均为自动注册，地址分别为 `172.30.73.30:9999`、`172.30.73.35:9999`，心跳 30 秒内 |
| `orderExpiryScan` | **不存在**（`xxl_job_info` 中 0 行）。当前任务 20 个，最大 id 22 |
| payment `compensationEventScan`（F1 兜底前提） | 任务 9，`*/30 * * * * ?`，分片广播，**已启用**。最近 30 分钟调度 60 次，handle 200 共 60 次，失败 0 |
| acquiring `compensationEventScan` | 任务 4，已启用，最近 30 分钟 60 / 60 成功。`order.expired` 的提交由它兜底 |

### 2.4 候选镜像与差异

#### 2.4.1 构建源与方法

| 项 | 内容 |
|---|---|
| 源码指纹 | `git -C /Users/kenny/Desktop/Workspace/Code-MoR-expiry rev-parse HEAD` = `22ede435e34c4a78beda42f0ff2c30c13ddf3210`。暂存差异 `git diff --cached --stat HEAD` 为 `73 files changed, 2851 insertions(+), 166 deletions(-)`，其内容 SHA-256 前 16 位为 `04efb204d7249afb`。另有 1 个未暂存文件 `TerminateIntentTest.java`（只改测试，内容指纹 `ccad399cb3ed8ec0`），没有未跟踪文件 |
| JAR | `paytech-acquiring-app-1.0.0-SNAPSHOT-exec.jar` 的 SHA-256 前 16 位为 `de02f36bd0b0c128`，`paytech-payment-app-1.0.0-SNAPSHOT-exec.jar` 为 `621ff256dfc74c5c`。两者都构建于 10-22 19:46:53Z / 19:46:55Z，与阶段 5 本机 e2e 运行的 JAR 相同。构建之后，`paytech/{acquiring,payment,foundation}` 下没有任何 `src/main` 文件被修改 |
| 镜像 | 用 classic builder（`DOCKER_BUILDKIT=0`）构建，`FROM <现网镜像 ID>` 后只执行 `COPY --chown=10001:10001 <jar> /app/application.jar`（D2）。构建上下文在会话 scratchpad，不在仓库内 |
| 核对 | 镜像内 `/app/application.jar` 与构建产物指纹相同。`/app/health-probe` 目录摘要 `1b237ccd…` 与运行容器相同。Entrypoint、User `10001:10001`、WorkingDir `/app`、Env 6 项与现网镜像相同 |

#### 2.4.2 候选相对现网的 JAR 差异（[scripts/jar_diff.py](./scripts/jar_diff.py)，按条目 CRC 与长度比较，含 `BOOT-INF/lib` 内嵌 JAR）

现网 JAR 从本机镜像库读出（`docker run --rm --network none --entrypoint cat <现网镜像 ID>`），没有读写运行中的容器。

| 服务 | 条目数（现网 → 候选） | 差异 | 归属 |
|---|---|---|---|
| acquiring | 62,684 → 62,698 | +15、−1、~27 | **全部属于本需求暂存改动的源文件**：新增 Processor / Service / Context / 终止客户端 / Handler / Properties / `Locks`，删除 `FixedPaymentTerminationClient`，修改 3 个 Mapper 及其 XML、仓储、`ClientWebhookEventType` / `ClientWebhookEvent`、`CancelOrderProcessor`、`SubmitCustomerPaymentProcessor`（锁键改用 `Locks`，取值仍为 `"order:" + id`）、`CustomerCheckoutModels`、内嵌 `paytech-payment-api` 2 个类、`application.yml` |
| payment | 62,314 → 62,317 | +3、−0、~15 | **全部属于本需求**：`TerminationReason`、`GetIntentBySourceProcessor` / `Request`、Controller 与两个 Converter、`PaymentIntentApi`、`TerminateIntentRequestBody`、`PaymentIntentMapper` 及其 XML、`TerminateIntentContext`、仓储、`PaymentExecutionServiceImpl`、`GetIntentProcessor`、`TerminateIntentProcessor`、Request / Response |

现网 JAR 由 3DS rel2（`42879401` + 3DS 未提交改动）构建，候选由 `22ede435`（3DS 提交）+ 本需求构建。差异里没有任何 3DS 或第三方条目，说明对这两个服务而言，`22ede435` 与 rel2 内容一致，本次不会顺带上线其他改动。

| 服务 | 候选镜像 | 本地 tag | JAR 前 16 位 |
|---|---|---|---|
| payment-service | `sha256:f3c48d893729ad9d133a4dda40a20fe5beac0a2991cbe90dd4c322eca32b8a1b` | `mor-test/payment-service:expiry-20261023` | `621ff256dfc74c5c` |
| acquiring-service | `sha256:55a644fd4618606d0ff6bbd37a1cc8e414ea8a5e8dc22adba57583305a21278a` | `mor-test/acquiring-service:expiry-20261023` | `de02f36bd0b0c128` |

`compose-candidate.yaml` 与 `compose-rollback.yaml` 已填入上述 ID。两份文件 `./test-environment/compose.sh -f <文件> config --quiet acquiring-service payment-service` 都通过。

#### 2.4.3 阶段 8 提交后的重建核对（必做）

1. 提交基于当时的 main，当前为 `32dbdd18`。如果 main 在此期间又有改动 acquiring、payment 或 Foundation 的提交，需先评估。
2. 在干净的 detached worktree 中检出提交，执行 `./mvnw -B -o -DskipTests -pl paytech/acquiring/paytech-acquiring-app,paytech/payment/paytech-payment-app -am clean package`（JDK 25，`~/.m3`）。
3. 运行 `python3 -I scripts/jar_diff.py <候选 JAR> <提交构建 JAR>`。期望 `+0 −0 ~0`。CRC 只比较内容，不受时间戳影响。
4. 结果为 0 差异时，在第 11 节记“提交与 mor-test 运行内容一致”，不需要重新部署。不为 0 时，把差异逐项列出，交主会话决定是否以提交构建重新发布。
5. 如果阶段 7 之前有其他会话替换了 acquiring 或 payment，候选镜像的底层就不再等于现网。此时 S1 停止，重做第 2 节核对，并以新的现网 ID 重建候选镜像。

## 3. 数据库只读预检结论

[scripts/00-precheck.sql](./scripts/00-precheck.sql) 由 [scripts/precheck.sh](./scripts/precheck.sh) 执行，于 20:30Z 与 20:32Z 各跑一次，结果相同，见 [snapshot/precheck.out](./snapshot/precheck.out)。

| 核对项 | 结果 |
|---|---|
| P1 订单列与索引 | `status_reason_code` 为 varchar(64) 可空，`termination_requested_at` 为 datetime(6) 可空，`expires_at` 为 datetime(6) NOT NULL，`paid_at`、`cancelled_at`、`version` 都在。`idx_order_expiry` 为非唯一索引，列为 `(order_status, expires_at, order_id)`。**无需 DDL** |
| P2 状态允许值（列注释） | 订单、会话、投影与 `mor_payment.t_payment_intent.intent_status` 都已包含 `EXPIRED`。Intent 的允许值为 OPEN、SUCCEEDED、CANCELLED、EXPIRED |
| P3、P4、P6、P7 到期存量 | **OPEN 16 笔，CREATED 0 笔**，最早于 10-17 06:31Z 到期，最晚于 10-20 03:52Z 到期，到期时长都在 1—7 天。分布：无投影 16、无接受证据 16、未写终止请求 16，Payment 中按来源对应的 Intent 为 0。7 个 Client 的笔数为 4 / 3 / 3 / 3 / 1 / 1 / 1。会话 16 个，都是 OPEN（满足 `CheckoutSessionMapper.expire` 的状态守卫）。合同 16 个，接受证据 0。**结论：16 笔全部走本地收尾，不调用 Payment**（D1） |
| P8 | 未来 24 小时内没有将要到期的订单。全表状态：OPEN 16、PAID 8、CANCELLED 9，没有 EXPIRED |
| P9 | 已有 `EXPIRED` 0 行。CANCELLED 9 行，原因码都是 `CLIENT_REQUESTED` |
| 端点订阅（另查 `mor_platform`） | 16 笔订单所属的 7 个 Client，按 `t_access_tenant.owner_ref_id` 映射到各自 Tenant，每个 Tenant 有 1 个 ACTIVE 端点（指向 webhook-receiver），订阅 `order.paid`、`order.cancelled`、`refund.created`，**都不含 `order.expired`**。全库唯一订阅了 `order.expired` 的端点为 DISABLED |
| P10 acquiring 补偿 | 8 行 `SUBMISSION_EVENT_TYPE / FAILED`（见观察项），没有待执行任务 |
| P11 Payment | Intent 为 OPEN 7、SUCCEEDED 1，都是 3DS 探针的合成数据，sourceId 不对应任何订单。补偿任务 0 行，说明没有 `PAY_RESULT_QUERY` 等未完成任务 |
| P12 扫描语句执行计划 | `range` 走 `idx_order_expiry`，`Using index; Using filesort`，估算 17 行。mor-test 规模下没有问题。EXP-C 遗留的“生产数据量下 filesort”仍然存在：排序键 `order_id` 不是该索引在两个状态范围上的连续前缀。登记为后续项，不阻塞 |

## 4. 替换顺序与兼容性

**顺序：payment → 等待 healthy 并核对 → acquiring → 登记并启用 XXL 任务。**

| 约束 | 原因（代码核对） |
|---|---|
| **payment 必须先于 acquiring** | 新 acquiring 的终止客户端会调用 Payment 新接口 `GET /intents/by-source/...`，终止时还会带 `reason=ORDER_EXPIRED`。旧 Payment 对不存在的路由返回 404 `NOT_FOUND`（Foundation `MvcProblemExceptionMapper` 对 `NoHandlerFound` / `NoResourceFound` 的映射），而新 acquiring 把 NOT_FOUND 解码为“Intent 不存在”。**这样一来，新 acquiring 配旧 Payment 时，有接受证据但无投影的订单会被误判为无 Intent，进而本地取消或过期，即使 Payment 实际有 Intent。** 带 `ORDER_EXPIRED` 的终止请求会被旧 Payment 按非法值拒绝（4xx），按失败重试，不会误写 |
| 旧 acquiring 配新 Payment：兼容 | 旧 acquiring 用固定 Mock 终止，不调用 Payment 的终止接口，也不调用按来源查询。新 Payment 的其他接口没有变化，相对现网的差异中不含 PAY、查单、刷新、回调与 PaymentSucceeded 的类。`GetIntentResponseBody` 不变 |
| acquiring 内的锁 | 提交支付改用 `Locks.getOrderLock`，值仍为 `"order:" + id`。替换期间新旧实例不会同时运行，这一改动也不影响 Redis 中的锁 |
| XXL 任务必须在新 acquiring 之后登记 | 旧镜像没有 `orderExpiryScan` Handler，调度会失败。执行器约 30 秒后重新注册，地址列表约 30 秒后填充，在此之前手动触发会报 “registry address is empty” |
| MQ | 拓扑不变：没有新交换机、队列或消费者 |

**不可用窗口：**

- payment 替换约 30—60 秒：acquiring 的提交支付与轮询返回 503，3DS 探针调用失败。mor-test 当前没有进行中的支付，也没有 Payment 补偿任务。
- acquiring 替换约 30—60 秒：Client API、Customer API 和 `acquiring.payment.*` 的消费都会中断。消息在队列中缓冲，替换后继续消费。

**数据兼容（回滚时）：**

- **订单、会话、投影：** 发布后写为 `EXPIRED` 的数据，旧 acquiring 读侧都能处理。GET 订单不返回 `checkoutUrl`，收银台返回 `CHECKOUT_EXPIRED`，Cancel 返回 409 `ORDER_NOT_CANCELLABLE`，PaymentSucceeded 到达时只记录投影并告警。这些分支在需求第 1 节中已有记录。
- **Payment Intent：** 写为 `EXPIRED` 的 Intent，旧 Payment 按已终止处理。`TerminateIntentContext` 对 CANCELLED 或 EXPIRED 都返回 `ALREADY_TERMINATED`，`PayProcessor` 拒绝非 OPEN 的 Intent，与 `22ede435` 的代码一致。
- **补偿任务：** 未完成的 `order.expired` 任务，旧 acquiring 的 `ClientWebhookSubmissionHandler` 仍会提交。它只核对冻结载荷 Hash 后原样提交，不按事件类型枚举解析。

## 5. 配置差异

| 服务 | 变化 | 说明 |
|---|---|---|
| payment | 只换镜像 | 继承 `PAYMENT_CHANNEL_CALLBACK_BASE_URL=""`（3DS N4）。不新增键 |
| acquiring | 只换镜像 | `acquiring.order-expiry.batch-size=200`、`max-batches=50`、`pending-alert-after=24h` 写在 `application.yml` |
| Platform、Nginx、Gateway、Nacos、Vault、RabbitMQ | 不变 | Platform 调用方已允许 `order.expired` |
| XXL | 新增任务 `orderExpiryScan` | 第 7 节 |

本包没有主 compose 补丁。3DS 遗留的 `PAYMENT_CHANNEL_CALLBACK_BASE_URL` 尚未写入主 compose，属于 3DS 的后续项。本包只负责继承这个键，不负责把它写进主 compose。

## 6. 执行步骤（阶段 7，另需用户授权；主会话执行）

记号：`R=requirements/20261022/acquiring-订单过期收尾/release`，`O=$PWD/$R/compose-candidate.yaml`，`RB=$PWD/$R/compose-rollback.yaml`。`up <svc>` 指：

```
./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <svc>
```

每次替换后都要核对：

- 容器 healthy、重启 0、`.Image` 等于候选；
- `docker exec mor-test-<svc>-1 sha256sum /app/application.jar` 等于第 2.4 节的值；
- `docker logs --since <本步开始> mor-test-<svc>-1 2>&1 | grep -c ' ERROR '` 为 0；
- `python3 -I $R/scripts/env_compare.py --override $R/compose-candidate.yaml <svc>` 的结论全部为 SAME，镜像自带键除外。

任一步失败就停止，并按第 8 节回退已替换的服务。

| 步骤 | 动作 | 核对 |
|---|---|---|
| S0 协调 | 通知 3DS 会话（这两个容器的现网维护方）与其他活跃会话：本次只替换 payment、acquiring，并新登记一个 XXL 任务。确认 D1—D5 已有结论 | 候选镜像 `f3c48d89…`、`55a644fd…` 都存在 |
| S1 复核基线 | `$R/scripts/snapshot.sh <备份目录>/before`。两个容器的 `.Image` 与 JAR 须等于第 2.1 节，`precheck.out` 的 P3—P11 须与 [snapshot/precheck.out](./snapshot/precheck.out) 相同，到期笔数允许因新验收而增加，但须重新列出。`xxl.txt` 中 `orderExpiryScan` 须为 0 行，任务 9 须启用 | 任一项不符就停止，回到第 2.4.3 节第 5 条 |
| S2 备份 | 备份目录建在 `~/Code-MoR-backups/<UTC>-expiry-mor-test/`（0700）。内容：S1 快照、`mysqldump --single-transaction mor_acquiring`（整库，用于对比）、`xxl_job_info` / `xxl_job_group` 中 group 4、9 的行。root 口令只经 `MYSQL_PWD` 传入。最后写 `SHA256SUMS` | dump 非空且以 `Dump completed` 结尾 |
| S3 payment | `up payment-service` | `payment.channel-connector.p0—p3` 各 1 个消费者；XXL `payment-service` 心跳在 30 秒内刷新；任务 9 下一次调度 handle 200 |
| S4 acquiring | `up acquiring-service` | `acquiring.payment.p0—p3` 各 1 个消费者；XXL `acquiring-service` 心跳刷新，`xxl_job_group` id 4 的地址列表已填充；任务 4 handle 200 |
| S5 XXL 登记 | 按第 7 节，用 Admin API 执行 `insert` → `start` | 新任务在 `xxl_job_info` 中 `trigger_status=1` |
| S6 第一轮扫描 | 等第一次整分钟调度，或 `/jobinfo/trigger` 手动触发一次（地址为空时 30 秒后重试） | 按验收计划第 2 节核对：16 笔本地收尾、16 行 Platform 事件、0 投递、第二轮 `scanned 0` |
| S7 验收 | 按 [acceptance-plan.md](./acceptance-plan.md) 第 3—7 节执行 | 结果写入 `release/acceptance/` 与第 11 节 |
| S8 记录 | 填写第 11 节；在 `delivery.md` 补环境操作记录，在 `CURRENT_IMPLEMENTATION.md` 记台账（阶段 8）；通知各会话：今后 acquiring、payment 的候选须基于本次镜像与源码 | — |

## 7. XXL 登记（S5）

登记方式照 `requirements/20261019/billing-计费事实模型重构与渠道成本迁出/release/xxl-job-channel-cost-activation.md` 与 account 任务 14：经 Admin API 登录后调用，不直接写 `xxl_job` 库。

- 登录：`POST http://127.0.0.1:28090/auth/doLogin`，用户 `admin`，口令取 `.env.test` 中的 `MOR_XXL_JOB_ADMIN_PASSWORD`（键已存在），不回显。
- **时机：** S4 之后，`xxl_job_registry` 中 `acquiring-service` 的心跳更新时间晚于新容器启动时间，且 `xxl_job_group` id 4 的 `address_list` 非空。

`POST /jobinfo/insert`，然后 `POST /jobinfo/start`（`ids[]` = 新 id，预计为 23）：

| 字段 | 值 | 说明 |
|---|---|---|
| `jobGroup` | `4` | `acquiring-service` 执行器分组 |
| `jobDesc` | `Acquiring 订单到期收尾扫描（orderExpiryScan，到期 CREATED/OPEN → EXPIRED / order.expired）` | |
| `author` | `code-mor` | 同其他任务 |
| `alarmEmail` | 空 | |
| `scheduleType` / `scheduleConf` | `CRON` / `0 * * * * ?` | 每分钟（需求 Q1） |
| `glueType` / `glueRemark` / `glueSource` | `BEAN` / `BEAN` / 空 | |
| `executorHandler` | `orderExpiryScan` | `OrderExpiryJobHandler.HANDLER_NAME` |
| `executorParam` | 空 | Handler 不读参数 |
| `executorRouteStrategy` | `SHARDING_BROADCAST` | 分片取自 XXL 上下文，按 `MOD(order_id, total)` 取模（S2） |
| `executorBlockStrategy` | `DISCARD_LATER` | 上一轮未结束时丢弃本轮 |
| `executorTimeout` | `300` | D5 |
| `executorFailRetryCount` | `0` | 失败的订单保持原状态，下一轮会再次被选中 |
| `misfireStrategy` | `DO_NOTHING` | |
| `childJobId` | 空 | |

**失败判定：** 本轮有订单收尾失败，或扫描本身抛异常时，任务结果为失败。执行日志的汇总行只含条数：`order expiry scan: scanned …, expired …, pending …, already paid …, reversal required …, skipped …, retry …, failed …`。

**不登记：** 不需要新的执行器分组（id 4 已存在）。`compensationEventScan` 任务 4、9 已存在并启用。

## 8. 恢复方案

### 8.1 触发条件

出现以下任一情况即触发回退：

- 替换后 healthy 失败，或启动日志有 ERROR；
- 第一轮扫描 `failed > 0` 且不能当天前修；
- `acquiring.payment.*` 进入 DLQ；
- Client API 回归（验收计划第 4 节）出现新失败；
- 3DS 回归（第 5 节）失败。

### 8.2 应用回退（不动数据）

1. **先停任务：** `POST /jobinfo/stop`（新任务 id）。旧 acquiring 没有该 Handler，不停任务的话每分钟都会调度失败。确需撤销登记时，再执行 `/jobinfo/remove`。
2. **查未完成任务：** 用只读 SELECT 查 `mor_acquiring.t_compensation_event` 中 `sub_event_type='order.expired'` 的待执行条数。有待执行任务也可以回退，因为旧 Handler 会原样提交（第 4 节），但要在第 11 节记录数量。
3. **按逆序回退镜像：** 先 **acquiring**，后 **payment**：`./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <svc>`。可以只回退 acquiring 并保留新 payment，两者兼容。**不得只回退 payment 而保留新 acquiring**，原因见第 4 节的 404 误判。
4. **核对：** `.Image`、JAR 指纹等于第 2.1 节，`env_compare.py --override $R/compose-rollback.yaml` 结论全部为 SAME。

### 8.3 数据与不可逆项

| 对象 | 回退方式 | 说明 |
|---|---|---|
| 已写为 EXPIRED 的订单、会话、投影（含存量 16 笔） | **保留** | 旧代码读侧兼容。没有“取消过期”的操作，也不写反向 SQL |
| Payment 中已写为 EXPIRED 的 Intent（P-1 合成，或真实过期） | 保留 | 旧 Payment 按已终止处理 |
| Platform `order.expired` 事件行与已完成的投递 | 无法回退 | 已送达的 Webhook 无法撤回。存量 16 笔没有投递 |
| 写了 `termination_requested_at` 但 PENDING 的订单 | 回退后由人工处理 | 旧 acquiring 不会再处理过期，这些订单会一直禁止提交。阶段 6 没有这类订单；回退前用 `SELECT … WHERE termination_requested_at IS NOT NULL AND order_status='OPEN'` 列出 |
| `flow_expired` 回拨过 `expires_at` 的订单 | 保留 | 已是 EXPIRED 终态 |
| XXL 任务 | 停用或删除 | 第 8.2 节第 1 步 |

## 9. 验收

见 [acceptance-plan.md](./acceptance-plan.md)。主要包括四部分：

- 第一轮扫描的影响面核对；
- P-1 Payment 探针（D4）；
- `client_api_flow.py` 全量回归，含 AC-12 `flow_expired`；
- 3DS 回归要点：AC-02 的 DEMO_NI 支付、消费者与执行器、健康，对照 3DS 阶段 7 记录。

AC-03、AC-04、AC-05、AC-09（多实例）、AC-10 沿用阶段 4 与阶段 5 的证据。

## 10. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) | 候选覆盖文件，钉 sha256，继承 3DS 候选中唯一的非主 compose 键 |
| [compose-rollback.yaml](./compose-rollback.yaml) | 回滚覆盖文件，即当前现网 = 3DS 发布后镜像 |
| [acceptance-plan.md](./acceptance-plan.md) | 验收计划 |
| [scripts/snapshot.sh](./scripts/snapshot.sh) | 只读快照（S1 / S2 复用） |
| [scripts/env_compare.py](./scripts/env_compare.py) | 渲染环境与运行容器逐键比较，只输出键名与结论 |
| [scripts/jar_diff.py](./scripts/jar_diff.py) | JAR 逐条目比较（第 2.4.2、2.4.3 节） |
| [scripts/00-precheck.sql](./scripts/00-precheck.sql)、[scripts/precheck.sh](./scripts/precheck.sh) | 只读预检 P1—P12 |
| [snapshot/](./snapshot/) | 阶段 6 快照，不含任何密钥值 |

## 11. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 20:36 | S0 协调 | 通知 code-mor-6b（3DS，两容器现网维护方）与 code-mor-78（billing）。6b 确认未占用 mor-test，并提醒保留 `PAYMENT_INSTRUMENT_PREPARATION_WINDOW=PT5M`、`PAYMENT_CHANNEL_CALLBACK_BASE_URL=""`：前者在主 compose，后者在本包候选文件，渲染比对一致。候选镜像 `f3c48d89…`、`55a644fd…` 存在 |
| 20:38 | S1 复核基线 | `scripts/snapshot.sh <备份>/before`：payment JAR `c452c35e`、acquiring JAR `68a30b6c` 与第 2.1 节一致；`precheck.out` P3—P11 与阶段 6 相同；`orderExpiryScan` 0 行；任务 9 启用 |
| 20:38—20:39 | S2 备份 | `~/Code-MoR-backups/<UTC>-expiry-mor-test/`（0700）：`before/` 快照、`mor_acquiring.sql`（整库，`Dump completed`）、`xxl_job_info_group.sql`、`SHA256SUMS` |
| 20:39:03—20:39:30 | S3 payment | `up payment-service` → `f3c48d89…`：healthy、重启 0、JAR `621ff256`、` ERROR ` 0；`payment.channel-connector.p0—p3` 各 1 个消费者 |
| 20:40:12—20:40:50 | S4 acquiring | `up acquiring-service` → `55a644fd…`：healthy、重启 0、JAR `de02f36b`、` ERROR ` 0；`env_compare` 两服务除镜像自带键外全部 SAME；`acquiring.payment.p0—p3` 启动约 15 秒后各 1 个消费者；20:41:00 执行器心跳、分组 4 地址 `http://172.30.73.30:9999/` |
| 20:41—20:42:23 | S5 XXL 登记 | Admin API `/jobinfo/insert` → 任务 **23**（字段与第 7 节一致，库内核对）；首次 `/jobinfo/start` 用了 `id` 参数得 400，紧接着 XXL Admin 于 20:41:31 自行重启（累计第 13 次，阶段 6 已观察到反复重启）；恢复后以 `ids[]=23` 启用成功，`trigger_status=1`，下次 20:43:00 |
| 20:43:01—20:44:02 | S6 第一轮扫描 | 执行器日志：`scanned 16, expired 16, pending 0, already paid 0, reversal required 0, skipped 0, retry 0, failed 0`；16 笔订单 EXPIRED / `EXPIRY_REACHED` / `termination_requested_at` 已写 / `cancelled_at` 空，16 个会话 EXPIRED；补偿表只剩原有 8 条 FAILED；Platform `order.expired` 16 行、`t_webhook_delivery` 0 行；payment 日志无 terminate / by-source 请求。20:44:01 第二轮 `scanned 0` |

| 2026-10-23 | S8 提交与重建核对 | 本地提交 `623916ac`（父 `32dbdd18`，未推送）。在 detached 工作目录检出该提交，`./mvnw -B -o -DskipTests -pl acquiring/paytech-acquiring-app,payment/paytech-payment-app -am clean package` 后 `jar_diff.py`：acquiring 62698 条目、payment 62317 条目均 `+0 −0 ~0`，**提交与 mor-test 运行内容一致**，不需要重新部署 |

### 11.1 S7 验收（2026-10-22 20:47Z—21:03Z，执行代理 EXP-S7）

证据目录 `release/acceptance/`：`accept_terminate.py`（P-1、3DS 回归、只读核对、泄漏检索，import 复用 3DS 的 `mt.py`、`accept_3ds.py`、`accept_regression.py`、`accept_leak_scan.py`，结果写本目录，3DS 目录下的文件未改动）、`accept-terminate.json`、`client-api-flow-run1.txt`（第一轮）、`client-api-flow.txt`（第二轮）、`client-api-flow-run2-supplement.json`、`acceptance_flow-upload-contract.patch`、`accept-3ds-regression.json`、`leak-scan.json`。前提：`mor-test-webhook-receiver-1` 已运行（healthy），未启停任何容器。

| 时间（UTC） | 项 | 命令 | 结论 | 证据 |
|---|---|---|---|---|
| 20:47:10—20:47:31 | 第 3 节 P-1 | `python3 -I release/acceptance/accept_terminate.py p1` | **PASS**（11/11）。Intent A `239127482396135424` → `TERMINATED / EXPIRED`，版本 1→2；重复终止 `ALREADY_TERMINATED`，版本仍为 2。Intent B `239127533369511936` → `TERMINATED / CANCELLED`。按来源查询 200；换 Client 与不存在的 sourceId 均为 404 `NOT_FOUND`；小写 sourceType 400 `INVALID_REQUEST`；换 Client 终止 400 `BUSINESS_CHECK_FAILED`（Intent 不变）；`reason=EXPIRED` 422 `VALIDATION_ERROR`（Intent 不变）。观察：建 Intent 返回 200 而非计划写的 201，与 3DS 阶段 7 相同，属现有行为 | `accept-terminate.json` |
| 20:47:55—20:48:10 | 第 4 节第一轮 | 候选工作目录 `E2E_TARGET=test python3 -I client_api_flow.py` | **中止**。12/14 后停在入驻 `F01 上传授权 IDENTITY`：HTTP 400 `INVALID_REQUEST`，requestId `623d2f0f-4b5d-4b0d-8650-f25abcf121e4`。`mor-test-client-console-bff-1` 随 `73a2460d` 于 10-22 15:23Z 重建，记录 `HttpMessageNotReadableException`。共用脚本 `acceptance_flow.py` 落后于该提交的上传合同（决定 `2026-10-22-file-upload-address-principles` P1、P2）。与 acquiring、payment 无关。残留 1 个入驻账号与 1 个 DRAFT 申请；`t_commerce_order` 无新增，接收端无新文件 | `client-api-flow-run1.txt` |
| 20:55—20:58 | 脚本副本修正 | 把候选工作目录的 local-e2e 复制到 scratchpad `local-e2e-s7/`，只改 `acceptance_flow.py` 的 `upload_material`。仓库内两份未改 | 字段变化：① F01 `POST /onboarding-files/v1/uploads` 去掉 `applicationId`、`subjectPartyId`，只发 `filename / mimeType / sizeBytes`，依据 BFF `OnboardingUploadRequestBody`，BFF 开启 `fail-on-unknown-properties`；② F02 完成回执同时读取 `fileId` 与 `fileAddress`，并核对 `fileAddress = /files/v1/{fileId}`；③ C12 `POST /onboarding/v1/applications/{id}/documents` 把 `fileId` 换为 `fileAddress`，依据 CCT `DocumentReferenceRequestBody(subjectPartyId, requirementCode, documentType, fileAddress)`。F03 按 `fileId` 读文件、O10 运营下载按 `fileId`（compliance 审核登记仍发文件 ID）不变。没有新增页面流程或人工审批 | `acceptance_flow-upload-contract.patch` |
| 20:58:58—21:00:14 | 第 4 节第二轮 | `cd <scratchpad>/local-e2e-s7 && E2E_TARGET=test python3 -I client_api_flow.py`（只跑一次） | **AC-12 PASS；脚本在退款段崩溃**。91 项 PASS、0 项 FAIL：入驻（F01—F03、C12、提交、运营核验与批准）、凭证、端点（订阅 4 种事件，`whsec_`）、查询、拒绝、Gateway 幂等全套、`order.cancelled`、AC-12 全部子项、`order.paid` 夹具、AC-04 创建退款。AC-12：回拨后等待 16 秒，GET 为 `EXPIRED`，`checkout.url=null`，会话 `EXPIRED`，`paymentStatus=NOT_STARTED`；库内为 `EXPIRY_REACHED`，version 3。`order.expired` 只送达 1 次，签名校验通过，Platform 事件唯一且投递 SUCCEEDED，接收内容等于冻结 Envelope，`sourceVersion = object.version = 3`；`data.object` 与 GET 一致，`expiredAt = expiresAt = 20:58:46.534444Z`。过期后取消返回 409 `ORDER_NOT_CANCELLABLE`，版本 3→3，不产生 `order.cancelled`。崩溃发生在等待 `refund.created` 时：`InboxReceiver.requests` 用 `*.json` 列出接收端目录，读到接收端写入中的临时文件 `.1792702814468413011-1.json`，按文件名解析时间戳抛 `ValueError`。这是脚本读取端的竞态，与被测服务无关。按“只跑一次”未重跑。输出不含 `whsec_`、`ck-` 凭证或 `chk_` 原值 | `client-api-flow.txt` |
| 21:03 | 第 4 节只读补核 | 接收端目录与 Platform、acquiring 只读 SELECT | **PASS（可核部分）**。本轮接收端恰好 4 个文件：order.cancelled、order.expired、order.paid、refund.created，即“接收端总数 = 4”。Platform 本轮 4 种事件各 1 行。`refund.created` 来源 acquiring-service，投递 SUCCEEDED（1 次），acquiring 无待执行任务，退款行为 PROCESSING、version 2，与历次相同。**未核**：refund.created 签名（端点密钥只在脚本内存中）、与 GET Refund 逐字比对、同一 `clientRefundId` 重放、退款结果 GAP、`refundable-amount` 与 `refunds` 查询 | `client-api-flow-run2-supplement.json` |
| 20:49:13—20:49:22 | 第 5 节 3DS AC-02 | `accept_terminate.py ac02`（调用 `accept_3ds.ac02`） | **PASS**。DEMO_NI 600.00 USD：PAY_RISK `THREE_DS_REQUIRED` → PAY FAILED `THREE_DS_CHANNEL_UNAVAILABLE`；`t_channel_interaction` 前后均为 0 行；Intent 仍为 OPEN。Risk 无冷启动超时 | `accept-3ds-regression.json` `AC-02` |
| 同上 | 第 5 节 BIN 冻结 | 同一笔卡工具快照 + `t_card_bin_library_version` 只读查询 | **PASS**。生效版本为 1（ACTIVE，active_slot=1）；Token 冻结 VISA / DEBIT / CLASSIC / GB，`bin_library_version_no=1`，`bin_match_length=8`；Risk 评估取得相同卡属性 | 同上 `AC-02.facts.token`、`checks-*.binActive` |
| 20:50:08 | 第 5 节消费者与执行器 | `accept_terminate.py checks`（只读：RabbitMQ 管理接口、`xxl_job` 库 SELECT） | **PASS**。`payment.channel-connector.p0—p3`、`acquiring.payment.p0—p3` 各 1 个消费者；两个 DLQ 都是 0 条消息。XXL 心跳距今 3 秒（payment）和 2 秒（acquiring），分组 4、9 地址已填。任务 4、9、23 都启用，近 30 分钟 handle 失败 0。任务 4、9 各有 2 次 trigger 500，时间为 20:39:30 / 20:40:01（任务 9）和 20:40:30 / 20:41:00（任务 4），对应 payment 20:39:24Z、acquiring 20:40:31Z 的替换窗口，可以解释 | `checks-*` |
| 20:50:35 | 第 5 节健康 | `accept_regression.health('2026-10-22T20:39:03Z')`（结果写本目录） | **PASS**。十个服务 healthy，重启 0；acquiring `55a644fd`、payment `f3c48d89`，` ERROR ` 0 行。Platform 有 1 行 ERROR（20:49:44Z `TransactionTimedOutException`，requestId `c0ebb07c-…`），来自 Gateway Snapshot 刷新（Gateway 同时刻 WARN `CONTROL_PLANE_UNAVAILABLE`）。20:49:33Z Platform 向 XXL Admin 注册也读超时，判断为数据库或主机的瞬时停顿，与本次替换无关 | `health-*` |
| 20:52:33 | 第 7 节泄漏检索 | `accept_terminate.py leak 2026-10-22T20:39:03Z` | **PASS**。acquiring（244 行）、payment（262 行）：`chk_` 令牌形串 0、`chk_` 前缀 0、收银台主机 `localhost:28443` 0、测试卡号 0、Luhn 卡号形串 0、`whsec_` 0、`ck-` 凭证形串 0、ERROR 0。过期相关 WARN 0 条。其余 WARN 为启动期 BeanPostProcessorChecker 与 LoadBalancer 提示；payment 另有 2 条 `MvcProblemExceptionMapper`（P-1 的 400 / 422 探针请求）和 1 条 `PayActionActor`（AC-02 的 3DS 渠道不可用） | `leak-scan.json` |

**AC 对照（第 6 节实际结果）**

| AC | mor-test 结果 | 证据或沿用 |
|---|---|---|
| AC-01 | 第 2 节存量 16 笔由主会话 S6 核对（S7 前 `t_commerce_order` 为 16 笔 EXPIRED，到期未收尾 0 笔）。第 4 节 `flow_expired` 无 Intent 本地收尾 PASS | S6 记录；`client-api-flow.txt` |
| AC-02 | Payment 侧 PASS（P-1：终止 → Intent EXPIRED）。acquiring 侧有投影的收尾沿用阶段 5 S2 | `accept-terminate.json`；阶段 5 S2 |
| AC-03、AC-04、AC-05、AC-10 | 不做（计划内） | 阶段 4 `ExpireOrderTest`、阶段 5 S2 / S3 |
| AC-06 | PASS：`order.expired` 签名送达、Event ID `evt_<orderNo>_expired`、冻结载荷一致、只送达 1 次 | `client-api-flow.txt` |
| AC-07 | PASS：`ORDER_CANCELLED` 终止（P-1）；Client API Cancel 本地分支 200 `CANCELLED`，`order.cancelled` 送达 1 次；三种事件载荷都没有内部 ID、个人信息或风控字段 | P-1；`client-api-flow.txt`；202 / 409 分支见阶段 4 `CancelOrderTest` |
| AC-08 | PASS：过期后取消返回 409 `ORDER_NOT_CANCELLABLE`，不改写订单。收银台 `CHECKOUT_EXPIRED` 沿用 | `client-api-flow.txt`；阶段 5 S1 |
| AC-09 | 第二轮 `scanned 0` 由主会话 S6 核对；多实例沿用 | S6；阶段 4 自动测试 |
| AC-11 | PASS（P-1：TERMINATED、ALREADY_TERMINATED 版本不变、换 Client 400、非法原因 422） | `accept-terminate.json` |
| AC-12 | PASS（第二轮全部子项） | `client-api-flow.txt` |

**遗留：**
- 第 4 节退款段未核项见上表“只读补核”一行。这些与本次改动无关：refund.created 由 acquiring 现有代码产生，本次没有改动退款。若要补核，先修正副本 `InboxReceiver.requests`，跳过以 `.` 开头的临时文件，再单独跑退款段或整轮；重跑须间隔 35 分钟以上（见验收计划第 4 节）。是否补核由主会话决定。
- 入驻脚本修正 `acceptance_flow-upload-contract.patch` 供脚本维护方合入两份仓库脚本；`InboxReceiver` 的临时文件竞态需要同时修正。
- mor-test 残留：第一轮中止留下 1 个入驻账号与 1 个 DRAFT 申请；第二轮按计划第 8 节保留新 Client、凭证、端点和订单，其中回拨过 `expires_at` 的订单 1 笔，已是 EXPIRED。

# acquiring 到期后支付结果确认 · mor-test 发布包

阶段 6 于 2026-10-23 09:30—09:45Z（+04 13:30—13:45）做了以下工作：

- 只读核对 mor-test：`docker inspect / ps / images`，容器内 `printenv` 只取键名与值是否相等，容器内 `sha256sum`，MySQL（`mor_acquiring`、`mor_payment`、`mor_channel_connector`、`xxl_job`）只读 SELECT，`rabbitmqctl list_queues`，`compose.sh ... config` 渲染比较。
- 从现网镜像读出 JAR（`docker run --rm --network none --entrypoint cat`），与候选 JAR 逐条目比较。
- 在本机构建一个候选镜像，只打本地 tag。
- 编写发布包。

**没有停启或替换任何容器，没有写任何库、XXL、Vault、RabbitMQ 或 Nacos，没有 docker cp，没有改 compose 文件本体，没有推送镜像，也没有提交 git。** 阶段 7 须用户另行授权，由主会话按第 6 节执行，结果写第 11 节。

**本次只换 acquiring-service 一个容器。** 无 DDL / DML，无新 XXL 任务，无新环境变量，Platform、payment 及其他容器不动。

## 0. 需要决定

| 编号 | 事实（阶段 6 核对） | 影响 | 建议 |
|---|---|---|---|
| D1 | **现网 acquiring JAR 不完全等于 `98210e0b` 的构建。** 现网 `ec8487bd…` 是 code-mor-78 在工作树 `Code-MoR-billing-b` 上以 `d3d39e40` + billing B 未提交改动构建的。之后 billing B 以 `98210e0b` 提交到 main，而 main 在 `d3d39e40..98210e0b` 之间还先合入了 `f707f2da`（工作流体验）和 `be688db7`（文件公开桶）。这两个提交改了 acquiring 依赖的两个库：`paytech-platform-support-api`（+19 / ~15 个类）和 `paytech-http-client-support`（~2 个类）。候选 JAR 从 main 构建，所以带上了这些改动（第 2.4.3 节） | **对 acquiring 无行为影响。** acquiring 不引用文件、文件存储、工作流的任何 API（全仓检索为 0）。`http-client-support` 的改动是增量的：`HttpRequestValidationException` 新增一个构造器和 `remoteFieldErrors()`，原构造器和 `fieldPaths()` 不变；拦截器在 422 时多传 `decoded.errors()`（解码器保证非 null）。acquiring 只在 `ClientWebhookSubmissionHandler` 按类型捕获该异常，不读新字段 | **接受。** 候选与 main 一致，阶段 8 提交后的重建核对才能得到 0 差异。若改为在 `d3d39e40` 基础上重建，会得到一个与任何提交都不对应的 JAR |
| D2 | **候选镜像的构建方式**与订单过期收尾 D2 相同：classic builder，`FROM` 现网镜像 ID，只覆盖 `/app/application.jar` | 镜像多一层，大小约多 96 MB（198 MB → 295 MB）。Entrypoint、User、WorkingDir、Env、health-probe 与现网完全相同；JAR 与阶段 5 验收的 JAR 逐字节相同 | 接受（仅 mor-test）。阶段 8 提交后按第 2.4.4 节重建并比较 |
| D3 | **mor-test 上没有能直接证明改动的现成订单。** 10 笔有支付记录的订单都是 `order.paid` 夹具：都是 PAID，但 Payment 中没有对应的 Intent 或 Attempt（10 / 10 为 0）。mor-test 唯一 ACTIVE 的渠道是 `DEMO_NI`，它没有任何标准 API 实现，也没有 3DS 能力，所以成功支付和 3DS 挑战都做不出来 | 只用现成订单，只能验证 410 的分支（第 9 节 P-A），新旧版本结果相同，区分不了 | 建议授权第 9 节的 **P-B**：新建一笔 5 分钟期限的结账，用 600.00 USD 卡支付得到确定的 FAILED（不发渠道请求），等到期后查询。新版本返回 200 FAILED，旧版本返回 410。会留下合成数据（第 9.4 节）。不做 P-B 的话，AC-01—AC-03 只有阶段 5 的本机证据 |

**观察项（不需要决定）：**

- **订单到期任务（id 23）在运行。** 最近 30 分钟调度 30 次，全部成功。到期订单已处理完：EXPIRED 24、CANCELLED 11、PAID 10，没有 OPEN 或 CREATED 的到期订单，也没有“已请求终止但仍 OPEN”的订单。
- **acquiring 有 10 条旧的 FAILED 补偿任务。** 类型都是 `SUBMISSION_EVENT_TYPE`，与 billing 发布时的基线相同（9 条加 10-23 05:37Z 的 1 条），与本需求无关。
- **XXL Admin 重启过。** 它于 09:04:03Z 重启，累计重启 27 次，不是本卡所为。重启后 acquiring 执行器心跳正常（20 秒内），任务 4 和 23 调度正常。
- **3DS 的两个错误 tag 仍在**（`mor-test/acquiring-serviceard3ds-20261022b:latest` 等）。本包全部按 sha256 引用，不受影响；本包的 tag 已加引号，构建结果正确。

## 1. 环境身份

| 项 | 值 |
|---|---|
| 环境 | `mor-test`（本机 Docker，Compose 项目 `mor-test`，入口 `test-environment/compose.sh`；它已经带上 `compose.yaml`、`compose.applications.yaml`、`compose.scheduler.yaml`） |
| 替换服务 | 只有 `acquiring-service`。payment（`cf9097ea…`）、billing 和其余容器不动 |
| 数据库 | `mor-test-mysql-1`，全部只读。**本需求无 DDL / DML** |
| XXL | 不登记、不修改任务。任务 4 `compensationEventScan`、任务 23 `orderExpiryScan` 已启用 |
| MQ | 拓扑不变 |
| 代码 | 主工作区 main `5763b8ab`。`git diff --stat 98210e0b HEAD -- paytech` 为空：`a816bd14`、`37173818`、`5763b8ab` 只改了文档和 ops-web。本需求未提交改动共 3 个文件（第 2.4.1 节） |

## 2. 现网快照（只读，2026-10-23 09:38—09:43Z）

明细见 [snapshot/](./snapshot/)：

- 容器与镜像：`containers.txt`、`images.txt`、`jar-sha.txt`、`image-compare.txt`；
- 环境变量：`env-keys-acquiring-service.txt`（只有键名）、`env-compare-*.txt`；
- 运行状态：`xxl.txt`、`db-precheck.txt`、`rabbitmq-queues.txt`、`acquiring-error-count.txt`；
- JAR 比较：`jar-diff-live-vs-candidate.txt`、`class-checkexpiry-callsites.txt`。

快照由 [scripts/snapshot.sh](./scripts/snapshot.sh) 生成，可重跑。

### 2.1 当前镜像 = 回滚目标

| 服务 | 运行镜像 | 来源 | 启动（UTC） | 重启 | 运行 JAR SHA-256 前 16 位 |
|---|---|---|---|---|---|
| acquiring-service | `sha256:27c36f4e3a16e10fd72c14c7667786dd5cbc795d4b2b400b52388c72c22f0437` | billing B `billing-b-20261023`（code-mor-78） | 10-23 07:48:55 | 0 | `ec8487bd07a722f6` |
| payment-service（不替换，仅记录） | `sha256:cf9097ea8685fbb2231fd97f49a1993b1aa4b6f30ba628e76e929dfa155a11c7` | 同上 | 10-23 07:48:25 | 0 | `96a1c9d83176f64d` |

镜像 ID 和 JAR 指纹与 billing release.md 第 2.1、8 节一致。容器的 compose 标签显示，当前生效的覆盖文件是 `requirements/20261022/billing-支付事实自动登记/release/compose-candidate.yaml`，之后没有其他会话替换过。启动以来 ` ERROR ` 日志 0 行。回滚镜像在本机镜像库中。

### 2.2 环境变量（只比较键名与值是否相等，不输出值）

| 渲染来源 | acquiring（应用键） |
|---|---|
| 只用主 compose | 36 / 36 相同 |
| 主 compose + billing 候选（当前生效链） | 36 / 36 相同 |
| 主 compose + **本包候选** | 36 / 36 相同 |
| 主 compose + **本包回滚** | 36 / 36 相同 |

容器另有 8 个镜像自带键（PATH、JAVA_HOME 等），不属于 compose。

- **需要继承的键只有一个：** billing 候选给 acquiring 加了 `PAYTECH_INSTANCE_ID=acquiring-test`。主 compose 自 `98210e0b` 起已经写入这个键，取值相同。本包的候选和回滚文件都显式带上它，作为兜底。
- **历次覆盖文件已全部检查：** 检索了 `requirements/*/*/release*/compose*.yaml` 中的 `acquiring-service` 段。除 billing 候选外，没有任何覆盖文件给 acquiring 加过环境变量。
- 本需求不新增键，也不新增配置项（需求 B5）。

### 2.3 运行状态与数据（只读）

| 项 | 结果 |
|---|---|
| XXL 执行器分组 4 | `acquiring-service`，地址 `172.30.73.30:9999`，心跳 20 秒内 |
| 任务 4 / 23 | 都已启用。最近 30 分钟：任务 4 成功 60 / 60 次，任务 23 成功 30 / 30 次 |
| RabbitMQ | `acquiring.payment.p0—p3`、`billing.acquiring.p0—p3` 各 1 个消费者，0 条消息；`acquiring.payment.dlq`、`billing.acquiring.dlq` 都是 0 |
| 已到期订单 | EXPIRED 24、CANCELLED 11，都没有支付记录；PAID 10，都有支付记录（夹具）。没有到期但仍 OPEN 或 CREATED 的订单 |
| 未到期订单 | 0 |
| 支付记录与 Payment | 10 条支付记录都没有对应的 Payment Intent 或 Attempt。Payment 现有 Intent 11 笔（OPEN 8、SUCCEEDED 1、CANCELLED 1、EXPIRED 1），Attempt 5 笔（FAILED 4、SUCCEEDED 1），都是 3DS 等探针的合成数据，不对应任何 acquiring 订单 |
| 渠道 | `mor_channel_connector.t_channel_mid` 中 ACTIVE 的 MID 只有 2 个，都属于渠道 `238508277900615680`（DEMO_NI），能力为 `CARD / * / USD|*`，都没有 3DS。PAYERMAX 的 MID 是 SUSPENDED / DISABLED |
| acquiring 补偿任务 | FAILED 10（见观察项），没有待执行任务 |
| 终止请求 | OPEN 且 `termination_requested_at` 非空的订单 0 笔 |

### 2.4 候选镜像与差异

#### 2.4.1 构建源与方法

| 项 | 内容 |
|---|---|
| 源码指纹 | main `a816bd14`，即 `98210e0b` 加文档，与当前 HEAD `5763b8ab` 的 `paytech/` 完全相同。另加本需求 3 个未提交文件：`GetCustomerPaymentProcessor.java`（生产代码）、`GetCustomerPaymentTest.java`、`PaymentResultTestMapper.java`（测试，不进 JAR）。`git diff HEAD -- paytech/acquiring` 的 SHA-256 前 16 位为 `f44490623ae25863`，其中 core 模块部分为 `b6141337b27075f3` |
| JAR | `local-environment/e2e/jars/paytech-acquiring-app-exec.jar`，SHA-256 前 16 位为 `dca404ec4d301876`，构建于 10-23 08:23:58Z，就是阶段 5 本机 e2e 运行的 JAR。最后一次修改 `GetCustomerPaymentProcessor.java` 是 08:08:48Z，早于构建。构建之后，`paytech/acquiring` 和 `paytech/foundation` 下没有任何 `src/main` 文件被修改 |
| 镜像 | 用 classic builder（`DOCKER_BUILDKIT=0`）构建，`FROM sha256:27c36f4e…` 后只执行 `COPY --chown=10001:10001 app.jar /app/application.jar`（D2）。构建上下文在会话 scratchpad 的 `f1-rel/ctx/`，不在仓库内 |
| 核对 | 详见 `snapshot/image-compare.txt`：<br>- 镜像内 `/app/application.jar` 与构建产物指纹相同，属主 `10001:10001`，权限 644；<br>- `/app/health-probe` 目录摘要 `1b237ccd…` 与现网相同；<br>- Entrypoint、Cmd、User `10001:10001`、WorkingDir `/app` 与现网相同；<br>- Env 6 项与现网相同，JSON 摘要都是 `8a01239f…` |

| 服务 | 候选镜像 | 本地 tag | JAR 前 16 位 |
|---|---|---|---|
| acquiring-service | `sha256:f6d55ade25d1cfdadcc499f487e291be15910883db16ec4ff60ce8088ebbbd33` | `mor-test/acquiring-service:f1-result-20261023` | `dca404ec4d301876` |

`compose-candidate.yaml` 与 `compose-rollback.yaml` 已填入 ID。两份文件 `./test-environment/compose.sh -f <文件> config --quiet acquiring-service` 都通过。

#### 2.4.2 候选相对现网的 JAR 差异（[scripts/jar_diff.py](./scripts/jar_diff.py)，按条目 CRC 与长度比较，含 `BOOT-INF/lib` 内嵌 JAR）

现网 JAR 从本机镜像库读出，没有读写运行中的容器。条目数 62,706 → 62,725，差异为 **+19、−0、~17**。逐条清单见 `snapshot/jar-diff-live-vs-candidate.txt`。

| 内嵌 JAR | 差异 | 归属 |
|---|---|---|
| `paytech-acquiring-core` | **~1：`GetCustomerPaymentProcessor.class`** | **本需求。** 用 `javap` 核对，`checkExpiry` 的调用位置已从 `getAndCheckOrder`、`processResponse` 移到 `getAndCheckPayment`、`queryPayment`，与 technique 第 3.1 节一致（`snapshot/class-checkexpiry-callsites.txt`） |
| `paytech-platform-support-api` | +19、~15 | main `be688db7`（文件公开桶与检查策略：`filestorage/*`、`files/*`、`FileStorageCheckResult`）和 `f707f2da`（工作流：`WorkflowInstanceApi`、`WorkflowActionTemplateResponseBody`、`WorkflowActivityBody`、`WorkflowSubmissionResponseBody`）。见 D1 |
| `paytech-http-client-support` | ~2：`HttpRequestValidationException`、`HttpServiceClientInterceptor` | main `f707f2da`：新增 `remoteFieldErrors()`，原行为不变。见 D1 |
| 其余 | 0 | `BOOT-INF/classes`、`application.yml`、`META-INF`、`pom.properties`、`paytech-messaging`（含 `catalog/events.yaml`）及第三方库全部相同。CRC 比较不受时间戳影响，这些条目也没有构建元数据差异 |

#### 2.4.3 现网 JAR 与 `98210e0b` 的关系

以下根据上面的差异和 `git log` 推出，没有另外构建 `98210e0b`：

- **acquiring 自身模块、`paytech-payment-api`、`paytech-messaging`：现网与 `98210e0b` 一致。** 差异中除本需求的 1 个类外没有其他 acquiring 条目；`98210e0b` 提交的 `events.yaml` 与现网 JAR 内的相同（billing R3 所说的工作树版本已原样提交）；`98210e0b..HEAD` 没有改动 `paytech/`。
- **`paytech-platform-support-api`、`paytech-http-client-support`：现网落后于 `98210e0b`。** `git log d3d39e40..98210e0b` 中，只有 `f707f2da` 和 `be688db7` 改了这两个模块的 `src/main`，与差异逐类对应。现网 JAR 以 `d3d39e40` 为基线，所以不含这两个提交。
- **所以，候选就是 `98210e0b` 加本需求，没有夹带其他未提交的改动。** 相对现网多出的内容只有本需求的 1 个类，以及 main 上已提交、对 acquiring 无行为影响的两个库改动（D1）。

#### 2.4.4 阶段 8 提交后的重建核对（必做）

1. 提交基于当时的 main（当前 `5763b8ab`）。如果 main 在此期间又有提交改动 `paytech/acquiring`、`paytech/foundation`、`paytech/payment/paytech-payment-api`、`paytech/platform-support/paytech-platform-support-api`，需先评估。
2. 在干净的 detached worktree 中检出提交，执行 `./mvnw -B -o -DskipTests -pl paytech/acquiring/paytech-acquiring-app -am clean package`（JDK 25，`~/.m3`，必须带 `-am`）。
3. 运行 `python3 -I scripts/jar_diff.py <候选 JAR dca404ec…> <提交构建 JAR>`，期望 `+0 −0 ~0`。
4. 结果为 0 差异时，在第 11 节记“提交与 mor-test 运行内容一致”，不需要重新部署。不为 0 时，逐项列出差异，交主会话决定是否以提交构建重新发布。
5. 如果阶段 7 之前有其他会话替换了 acquiring，候选镜像的底层就不再等于现网。此时 S1 停止，重做第 2 节核对，并以新的现网 ID 和新的源码基线重建候选。

## 4. 替换顺序与兼容性

**只换 acquiring。** 不需要先换其他服务。

| 约束 | 原因（代码核对） |
|---|---|
| 与现网 payment `cf9097ea…` 兼容 | 候选只改了 Consumer 查询提交结果的入口顺序。调用的 Payment 接口没有变化，仍是刷新 / 提交查询，也仍受 5 秒限频。`CheckoutCardPaymentPortImpl` 对 Payment `NOT_FOUND` 的解码不变。Payment 对 Intent 或提交不存在返回 `NOT_FOUND`（`GetSubmissionProcessor`），新代码会在到期时把它改写为 410（D1，阶段 2 已确认） |
| 与 billing / account / ledger 无关 | `OrderPaymentSucceeded` 的发布代码与现网相同（差异中没有相关类） |
| 与任务 23 无关 | `orderExpiryScan` Handler 与现网相同，任务不需停用或重新登记。新实例启动后执行器约 30 秒重新注册，期间的调度可能失败一次，下一分钟恢复 |
| MQ | 拓扑不变 |
| Platform / Gateway | 路由与字段不变。只有已提交支付的订单在到期后，状态码由 410 变为 200 |

**不可用窗口：** 替换约 30—60 秒，期间 Client API、Customer API、`acquiring.payment.*` 消费、补偿任务和过期扫描都会中断。消息在队列中缓冲，替换后继续消费。

**新增的 Payment 调用：** 到期且有支付记录的订单被查询时，候选会调用一次 Payment 刷新，现网不会。按第 2.3 节，mor-test 现在只有 10 笔夹具 PAID 订单符合条件。它们在 Payment 中没有 Intent，所以 Payment 返回 404，acquiring 再返回 410，与现网的结果相同。这些订单只有被主动查询时才会产生这次调用。

**回滚时的数据兼容：** 本需求不写库，也不改变任何持久化状态，回滚无数据处理。

## 5. 配置差异

| 服务 | 变化 | 说明 |
|---|---|---|
| acquiring | 只换镜像 | `PAYTECH_INSTANCE_ID` 原样继承（第 2.2 节） |
| 其他所有服务、Platform、Nginx、Gateway、Nacos、Vault、RabbitMQ、XXL | 不变 | — |

本包没有主 compose 补丁。

## 6. 执行步骤（阶段 7，另需用户授权；主会话执行）

记号如下：

- `R=requirements/20261022/acquiring-到期后支付结果确认/release`
- `O=$PWD/$R/compose-candidate.yaml`
- `RB=$PWD/$R/compose-rollback.yaml`

`up` 指：

```
./test-environment/compose.sh -f "$O" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 acquiring-service
```

| 步骤 | 动作 | 核对 |
|---|---|---|
| S0 协调 | 用 SendMessage 通知 code-mor-78（billing，现网 acquiring / payment 的维护方）和其他活跃会话：本次只替换 acquiring，请确认期间不发布它。确认 D1—D3 已有结论 | 候选镜像 `f6d55ade…` 存在（`docker image inspect`） |
| S1 复核基线 | 运行 `$R/scripts/snapshot.sh <备份目录>/before`，核对以下各项：<br>- acquiring `.Image` = `27c36f4e…`，JAR = `ec8487bd…`；<br>- `env-compare-billing-candidate.txt` 为 36 / 36；<br>- `xxl.txt` 中任务 4、23 启用；<br>- `db-precheck.txt` 与 [snapshot/db-precheck.txt](./snapshot/db-precheck.txt) 一致（订单数允许因其他会话验收而增加，但须列出）；<br>- 两个 DLQ 为 0 | 任一项不符就停止，回到第 2.4.4 节第 5 条 |
| S2 备份 | 只保存快照：`~/Code-MoR-backups/<UTC>-f1-result-mor-test/`（0700），内容为 S1 的 `before/` 和 `SHA256SUMS`。本需求不写库，不需要 dump | — |
| S3 替换 | 执行 `up` | 以下各项都要满足：<br>- healthy，重启 0；<br>- `docker inspect mor-test-acquiring-service-1 --format '{{.Image}}'` = `sha256:f6d55ade…`；<br>- `docker exec mor-test-acquiring-service-1 sha256sum /app/application.jar` 前 16 位为 `dca404ec4d301876`；<br>- `docker logs --since <本步开始> mor-test-acquiring-service-1 2>&1 \| grep -c ' ERROR '` 为 0；<br>- `python3 -I $R/scripts/env_compare.py --override $R/compose-candidate.yaml acquiring-service` 为 SAME 36 / 36，只有镜像自带键属于 ONLY_CONTAINER；<br>- `acquiring.payment.p0—p3`、`billing.acquiring.*` 的消费者数不变；<br>- XXL 组 4 心跳晚于新容器启动时间；<br>- 任务 4、23 在替换后的下一轮调度 handle 200（允许替换后 30 秒内失败一次）；<br>- 两个 DLQ 仍为 0 |
| S4 验收 | 按第 9 节执行 P-A；P-B 须已获授权（D3） | 结果写入 `release/acceptance/` 与第 11 节 |
| S5 记录 | 填写第 11 节；在 `delivery.md` 补环境操作记录；阶段 8 在 `CURRENT_IMPLEMENTATION.md` 记台账；通知各会话：今后 acquiring 的候选须基于本次镜像与源码（main `98210e0b` + 本需求） | — |

任一步失败就停止，并按第 8 节回退。

## 7. XXL

不登记、不修改。任务 23 `orderExpiryScan` 的 Handler 名与参数不变。

## 8. 恢复方案

### 8.1 触发条件

出现以下任一情况即触发回退：

- 替换后 healthy 失败，或启动日志有 ERROR；
- 任务 4、23 替换后持续失败（超过 2 轮）；
- `acquiring.payment.dlq` 或 `billing.acquiring.dlq` 新增消息；
- 第 9 节验收出现与期望不符的结果，且当天不能修复。

### 8.2 应用回退（不动数据）

1. `./test-environment/compose.sh -f "$RB" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 acquiring-service`。
2. 核对 `.Image` = `27c36f4e…`，JAR = `ec8487bd…`，`env_compare.py --override $R/compose-rollback.yaml acquiring-service` 为 SAME 36 / 36，ERROR 为 0，任务 4、23 恢复成功调度。
3. 回退后，到期且有支付记录的订单在查询提交结果时重新返回 410（恢复现行设计）。不需要处理任何数据。

### 8.3 数据与不可逆项

| 对象 | 回退方式 | 说明 |
|---|---|---|
| acquiring / Payment 数据 | 无 | 本需求的代码不写库 |
| P-B 合成数据（若执行） | 保留 | 回退不影响；清单见第 9.4 节 |
| XXL、MQ | 无 | 未改 |

## 9. 验收（mor-test）

### 9.1 能做与不能做

| 项 | mor-test 是否可做 | 原因 / 替代 |
|---|---|---|
| AC-01 到期后 3DS 挑战成功 | **不能** | DEMO_NI 没有 3DS 能力，也没有标准 API 实现；PAYERMAX 的 MID 已 SUSPENDED；mor-test 没有公网入口，也没有 Hosted Checkout 站点。以阶段 5 S1 为证 |
| AC-02 到期后失败，扫描后仍可查询 | **可以（P-B）** | 600.00 USD 卡支付命中 Risk R-11（金额超过 500 须做 3DS），DEMO_NI 没有 3DS 能力，确定得到 FAILED `THREE_DS_CHANNEL_UNAVAILABLE`，不发渠道请求（3DS 阶段 7 AC-02 已在 mor-test 验证过这一点） |
| AC-03 到期后处理中，限频 | **不能** | 需要一笔停在 PROCESSING 的尝试，mor-test 做不出来。以阶段 5 S3 为证 |
| AC-04 未提交支付到期返回 410 | **可以（P-A1）** | 用现成的 EXPIRED 订单 |
| AC-05 到期后写操作与查看收银台返回 410 | **可以（P-A1 / P-B）** | 只读查看收银台；P-B 中提交支付也返回 410 |
| AC-06 Client GET 无 `checkoutUrl` | **可以（P-B）** | P-B 订单到期后由 Client API 查询 |
| D1 有支付记录但 Payment 未找到，到期返回 410 | **可以（P-A2）** | 夹具 PAID 订单在 Payment 中没有 Intent |
| B3 Payment 不可用返回处理中 | 不做 | 需要停 payment。以 `test_expiredPaymentUnavailable` 为证 |

### 9.2 P-A 只读探针（不写库，S3 之后执行）

调用方式与阶段 5 相同：Consumer 接口 `GET /commerce/customer-gateway-api/v1/checkouts/{token}/payment-submissions/{id}`，用私网一次性探针容器直连 `acquiring-service:18102`，即 `e2e_http.private_call`，`E2E_TARGET=test`，`E2E_PROBE_IMAGE` 取任一现网 JDK 镜像。Checkout Public Token 用只读 SELECT 从 `mor_acquiring.t_checkout_session` 读出，只在内存中使用，不写入输出。

| 编号 | 对象（执行时用只读 SELECT 选取） | 请求 | 期望（候选） | 现网对照 |
|---|---|---|---|---|
| P-A1 | 任一 EXPIRED 且无支付记录的订单（现有 24 笔） | 查询提交 `1`；查看收银台 `GET /checkouts/{token}` | 都返回 410 `CHECKOUT_EXPIRED`；Payment 请求日志中没有该订单的请求 | 相同 |
| P-A2 | 任一 PAID 夹具订单（有支付记录，Payment 中没有 Intent） | 查询提交 = 该投影的 `last_attempt_id` | 410 `CHECKOUT_EXPIRED`（D1 分支）；payment 日志中有 1 次刷新请求，返回 404 | 410，但不调用 Payment |
| P-A3 | — | 替换前后各执行一次 P-A1、P-A2 | 结果码相同；只有候选会在 P-A2 中调用 Payment | — |

### 9.3 P-B 正向探针（须授权，D3）

以 `verification/stage5_result_after_expiry.py` 的 S2 和 `client_api_flow.py` 的 `flow_expired` 为蓝本，改写为 `release/acceptance/accept_result_after_expiry.py`（阶段 7 编写），`E2E_TARGET=test`。**不回拨 `expires_at`，靠等待到期，因此不写库。**

1. 用 Client API（`https://localhost:28444`，复用 `client_api_flow` 的入驻与凭证步骤）建结账：600.00 USD，`expiresAt` = 当前时刻 + 5 分钟（允许的最短期限）。
2. 用 Consumer 接口完成支付：保存消费者资料，确认合同，提交卡支付（公开测试卡 `4012888888881881`）。期望提交结果为 FAILED，失败码 `THREE_DS_CHANNEL_UNAVAILABLE`；`t_channel_interaction` 没有新增行。
3. 到期前查询提交结果，期望 200 FAILED。这是回归项。
4. 在 `expires_at` 之后、任务 23 下一轮扫描之前，再查一次，期望 **200 FAILED**（AC-02 前半；现网会返回 410）。同时检查：查看收银台和提交支付返回 410（AC-05）；Client GET 没有 `checkoutUrl`（AC-06）。
5. 等任务 23 扫描：订单变为 EXPIRED（`EXPIRY_REACHED`），会话、投影和 Payment Intent 都变为 EXPIRED，Platform `order.expired` 事件增加 1 行。之后再查一次，期望 **200 FAILED**（AC-02 后半、B4）。
6. 检查日志泄漏：本次窗口内的 acquiring、payment 日志中，卡号、`cvv`、Checkout Token 原值都是 0 次（同阶段 5 S5）。

第 4 步的时间窗不到 1 分钟（任务 23 每分钟执行一次），漏掉也不影响结论：第 5 步同样能区分新旧版本。**风险：** 在 mor-test 上通过 Consumer 接口完成整套 Hosted Checkout 支付，此前还没有人做过。保存资料要走 counterparty / Vault，合同要经过准备；Risk 服务刚重建时，第一笔支付可能因冷启动而失败关闭（3DS 遗留 S7-03）。任何一步卡住都记为环境问题，不重试超过 2 次，然后退回 P-A 加阶段 5 的证据。

### 9.4 P-B 留下的合成数据（保留）

- acquiring：订单、会话、投影各 1，最终都是 EXPIRED；接受证据，网络证据。
- Payment：Intent 1（EXPIRED）、卡工具 1、Attempt 1（FAILED）。
- Risk：评估快照。
- Vault / counterparty：消费者 Token。
- Platform：`order.expired` 事件行 1。
- 若 `client_api_flow` 入驻步骤新建了 Client，还会留下 Client、凭证和端点。

以上都不发送真实渠道请求，不产生支付成功，也不触发 billing（`OrderPaymentSucceeded` 只在成功时发布）。

## 10. 发布物

| 文件 | 用途 |
|---|---|
| [compose-candidate.yaml](./compose-candidate.yaml) | 候选覆盖文件，钉 sha256，继承 billing 候选中 acquiring 的全部键（`PAYTECH_INSTANCE_ID`） |
| [compose-rollback.yaml](./compose-rollback.yaml) | 回滚覆盖文件，即当前现网 = billing B 发布的镜像 |
| [scripts/snapshot.sh](./scripts/snapshot.sh) | 只读快照（S1 复用） |
| [scripts/env_compare.py](./scripts/env_compare.py) | 渲染环境与运行容器逐键比较，只输出键名与结论（复制自订单过期收尾） |
| [scripts/jar_diff.py](./scripts/jar_diff.py) | JAR 逐条目比较（第 2.4.2、2.4.4 节，复制自订单过期收尾） |
| [snapshot/](./snapshot/) | 阶段 6 快照，不含任何密钥值 |

## 11. 执行记录（阶段 7 填写）

S0—S3 由主会话执行（10-23 10:03:23Z 替换为候选 `f6d55ade…`）。S4 验收由验收代理执行。脚本是 [acceptance/accept_result_after_expiry.py](./acceptance/accept_result_after_expiry.py)（`python3 -I … pa|pb|leak <since>`），结果在 [accept-result-after-expiry.json](./acceptance/accept-result-after-expiry.json) 和 [.txt](./acceptance/accept-result-after-expiry.txt)，两者都不含 Token 或卡号（已检索，0 次）。

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 10:05:43 | 验收前核对 | acquiring `.Image` = `f6d55ade…`，JAR `dca404ec4d301876`，healthy，重启 0；payment 仍是 `cf9097ea…` |
| — | P-A3（替换前执行 P-A1 / P-A2） | **未执行**：替换前没有运行。现网对照以第 9.2 节记载的现行行为为准（两项都返回 410，P-A2 不调用 Payment） |
| 10:09:40 | P-A1：`ord_3L5AsWBReR5fhapDgfcFmzJP69J4j7`（EXPIRED，无支付记录） | **通过**。查询提交 `1` 返回 410 `CHECKOUT_EXPIRED`，查看收银台返回 410 `CHECKOUT_EXPIRED`；payment `http_server_requests` 无新增（payment 的 DispatcherServlet 直到 P-A2 才初始化，说明此前一次请求都没有收到） |
| 10:09:46 | P-A2：`ord_3L5B19KdF51LBU8SlDrMMltfy1VEJB`（PAID 夹具，Intent `9410000032110446` 在 Payment 中不存在） | **通过**。查询提交 `9420000033848214` 返回 410 `CHECKOUT_EXPIRED`（D1 分支）。payment 新增恰好 1 次请求：`POST …/submissions/{sourceSubmissionId}/refresh`，状态 404，与 acquiring 出站 `client_name=payment-service` 的 404 计数 1 对应。payment 访问日志只记元数据（uri 为 `/**`），这段时间内按本次 requestId 只有 1 行，`status="404"` |
| 10:09:57 | P-B 1：建结账（600.00 USD，`expiresAt` 10:16:08Z） | 201，订单 **`ord_3L5iBmKka8K2co3YqrB8Ni5DWZU9yk`**（order_id `239329486158544896`），Client `239260386824658945`。**偏差**：Client 侧私网直连 acquiring `client-gateway-api/v1`，带 Gateway 可信 Header，没有经过 28444。原因是 mor-test 上没有可用凭证，入驻新 Client 需要写 Platform 库（`mint_signup_link`），不在授权内 |
| 10:09:59—10:10:06 | P-B 2：保存资料、合同就绪、提交卡 | 保存资料 200；准备 READY，`allowedActions` 为 SAVE_CONSUMER、PAY；提交 200，结果 **FAILED `THREE_DS_CHANNEL_UNAVAILABLE`**；Attempt `239329512368721920` 为 FAILED；`t_channel_interaction` 0 → 0；payment WARN “风控要求 3DS 而冻结 MID 不支持，渠道请求不发出”（预期） |
| 10:10:08 | P-B 3：到期前查询 | 200 FAILED（回归通过） |
| 10:16:10—10:16:13 | P-B 4：到期后、扫描前（订单仍 OPEN） | 查询提交 **200 FAILED** `THREE_DS_CHANNEL_UNAVAILABLE`（AC-02 前半；现网会返回 410）。查看收银台 410、再次提交 410（AC-05）。Client GET 200，没有 `checkout.url`（AC-06） |
| 10:17:00 | P-B 5：任务 23 扫描（handle 200） | 订单 EXPIRED v6，`EXPIRY_REACHED`；会话、投影、Payment Intent 都是 EXPIRED；Platform `order.expired` 1 行（source_version 6）；Client GET 为 EXPIRED / EXPIRED，没有 URL |
| 10:17:06 | P-B 5：EXPIRED 之后查询 | **200 FAILED**（AC-02 后半、B4） |
| 10:17:06 | P-B 6：日志泄漏检索（acquiring、payment，从 10:09:55Z 起） | **通过**。测试卡号、`cvv` 键、Luhn 卡号形串、Checkout Token 原值、`chk_` 形都是 0；ERROR 0 |
| 10:18 | 运行状态 | 替换以来：任务 23 成功 14 / 14，任务 4 成功 27 / 28（1 条在执行中，无失败）；acquiring 执行器心跳 10:17:00；`acquiring.payment.p0—p3`、`billing.acquiring.p0—p3` 各有 1 个消费者，0 条消息；两个 DLQ 都是 0；替换以来 acquiring、payment 的 ERROR 都是 0 |

**AC 证据归属**

| 项 | mor-test（本次） | 阶段 5 / auto 测试 |
|---|---|---|
| AC-01 3DS 挑战中到期，之后成功 | — | 阶段 5 S1；`test_expiredResultQueried` |
| AC-02 到期后失败，扫描后仍能查询 | **P-B 4、5** | 阶段 5 S2 |
| AC-03 处理中，限频 | — | 阶段 5 S3；`test_expiredProcessing` |
| AC-04 未提交支付，到期返回 410 | **P-A1** | 阶段 5 S4；`test_expiredWithoutPayment` |
| AC-05 写操作与查看收银台返回 410 | **P-A1（查看）、P-B 4（查看、提交）**；保存资料未在 mor-test 上做 | 阶段 5 S1；`ExpireOrderTest`、`SaveConsumerTest` |
| AC-06 Client GET 没有 `checkoutUrl` | **P-B 4、5** | 阶段 5 S1、S2 |
| D1 有支付记录、Payment 未找到，到期返回 410 | **P-A2** | `test_expiredSubmissionNotFound` |
| B3 Payment 不可用返回处理中 | — | `test_expiredPaymentUnavailable` |
| B4 EXPIRED 之后返回最终结果 | **P-B 5** | 阶段 5 S2 |

**P-B 留下的合成数据**（与第 9.4 节一致，保留）：acquiring 订单、会话、投影各 1，都是 EXPIRED；Payment Intent `239329506266009600`（EXPIRED）、卡工具 1、Attempt `239329512368721920`（FAILED）；Risk 评估、counterparty / Vault 消费者 Token；Platform `order.expired` 事件 1 行。这个 Client 在订单过期收尾验收中订阅过 `order.expired`，Platform 会向验收接收端投递这一事件，投递结果不在本需求范围内。没有新建 Client。

**结论：** P-A1、P-A2、P-B 1—6 全部通过，不触发回退。

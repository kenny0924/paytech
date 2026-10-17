# 第 05 步报告 · Payment → payment + channel-connector（卡域暂留 payment 进程与库）

- 分支：`refactor/step-05-payment-channel`（起点 `refactor-main@7e0b70d`）。
- 提交：`b3c8805` 纯改名搬迁（内容未改，该提交单独不可编译）；`c1fb7c6` 内容改写；其后为验证修正与本报告。
- 结论：第 5 节各项都已真实执行。
  - 任务卡命令 `-pl payment/…-test,channel-connector/…-test -am`（另加 `foundation/paytech-messaging`）在真实 `mor_payment` 与 `mor_channel_connector` 上 BUILD SUCCESS：payment 72 例、channel-connector 75 例、messaging 55 例、arch-rules 57 例，全部 0 失败。
  - 两个 app 以 mock Profile 启动，health 为 UP。
  - Node 与 PDManer 脚本的失败与起点逐项一致，属既有问题。
  - 全 Reactor 按 team-lead 安排由主会话合入后统一运行。
  - platform-support-test 结果：待运行。

## 1. 改动摘要

1. 搬迁：`payment-orchestration-service` 的 api / card / test 原样搬入 `paytech/payment/`；execution 与 routing 合为 `paytech-payment-core`，web 改名 `paytech-payment-app`；`paytech-payment-channel` 搬入 `paytech/channel-connector/paytech-channel-connector-core`，包根改为 `com.paytech.channel.connector`。删除旧根的 `mvnw*`、`.mvn`、`.gitignore`，旧根已不存在。`paytech/pom.xml` 只取消 `payment`、`channel-connector` 两行注释。
2. channel-connector-api（新）：两个 `@HttpExchange` 合同、DTO、四个稳定代码枚举与能力代码常量，合同见第 3 节。`ChannelOperation`、`ChannelOperationOutcome`、`ExecutionPurpose` 由 core 移入 api。
3. 渠道结果映射迁入 channel-connector（D06）：`PayOutputs` 改名 `PlatformPayOutputs` 搬入 core，Platform 输出键与 NOT_SENT Problem 代码只在 channel-connector 解释，结果以已映射的 `ChannelOperationResult` 返回。能力代码只在候选查询处解析一次，得到支持操作与金额上下限。OE 一致性校验原样留在 channel-connector。
4. Payment 端口：`PaymentChannelClient` 的生产实现改为 core 内的 HTTP 客户端，发现名 `channel-connector-service`；进程内桥接已删除。`PayActionActor` 只消费已映射结论，状态映射逐分支等价，见第 5 节。`RouteServiceImpl` 改用 `CaptureMode` 枚举，动作到渠道操作的映射集中在 `ActionChannelOperations` 一处。
5. `PaymentSucceeded` 双写：补偿 Handler 先向旧 `topic_payment_result` 发布现有报文，再经 `DomainEventPublisher` 向 `payment.events` 发布 Schema v1 信封。两份报文的 Payload 由同一个 `PaymentSucceededPayload` 生成；任一发布失败都按可重试处理。
6. 装配拆分：两个 app 各自持有 DataSource、`@MapperScan`、出站客户端与配置。payment 扫描 execution 与 card 的 Mapper，使用 Redis、RabbitMQ、补偿事件与 messaging；channel-connector 只扫描渠道 Mapper，不引入 Redis 与 RabbitMQ。两者都引入 internal-auth，`mode: "OFF"`。
7. 应用身份：Payment 改名 `payment-service`，端口 18103 / 19103，Vault caller 为 `payment-service`。channel-connector 为 `channel-connector-service`，端口 18110 / 19110，Vault caller 为 `channel-connector-service`。
8. Enforcer：两个聚合 POM 各复制一份 Payment 禁令，并增加 `*-core` / `*-app` 通配禁令。payment 另有一条只查直接依赖的执行，禁止依赖 `paytech-payment-card`，`-core` 与 `-app` 以 skip 豁免（R11 过渡）。
9. arch-rules：`table-ownership.yaml` 新增 `transitionalPlacements`，登记第 12 步前位于 `mor_payment` 的卡表。生成器参数化写出该段；`TableOwnership` 把卡表并入 payment 的 R4 白名单；R11 豁免 `paytech-payment-card`。两项各补一个规则库测试。
10. 事件目录：`PaymentSucceeded` 改为 `schemaVersion: 1`、`aggregateVersionField: version`、`payloadSchemaRef …/v1`，与实现一致；同步修改 `YamlEventCatalogAutoTest`。
11. 测试：channel-connector-test 整类搬入 4 类，新增执行映射 HTTP 测试 10 个方法（15 例）、候选 HTTP 测试 3 例和 `ArchitectureAutoTest`。payment-test 的 6 类 48 例保持入口不变，渠道改由 `ChannelConnectorMocker` 提供 DTO；卡域纯 Java 2 类 7 例随 payment；另加 `ArchitectureAutoTest`。
12. 清理与 DDL：`PaymentDataMocker` 清 `mor_payment` 15 表与卡工具的 Redis CVV；`ChannelDataMocker` 清 `mor_channel_connector` 13 表。两份 `init/ddl.sql` 逐字取自拆分后的 Schema SQL，脚本核对无差异。
13. 本地环境：初始化脚本、`.env.example`、README 与 `verify-local-environment.mjs` 增加 `mor_channel_connector` / `channel_connector_app`。compose 文件按要求未改。
14. PDManer：模型按服务分区为 `payment.pdma.json`（19 表）与 `channel-connector.pdma.json`（13 表），Schema SQL 同步拆分。生成器拆成两个入口，共用分区模块 `payment-service-partition.mjs`。四个 pdmaner 测试与 OE Schema 清单改为新路径。
15. 路径与文档：09 §2.9、2.14、2.16 已完成。processor-conventions 中的 `PaymentExecutionApi` 已改为 `PaymentIntentApi`。Physical Decisions 新增 §2.1 按服务标注表归属。搬入文档的出向链接按新深度重写 108 处，剩余 2 处目标在起点提交中本就不存在。
16. Platform 发现名（team-lead 授权的任务卡第 6 节例外）：只改坐标、发现名、调用方名三类，不碰业务逻辑。11 个文件中的 `payment-orchestration-service` 改为 `payment-service`，包括读卡回调的发现名、Token 签发白名单、通知调用方样本、Javadoc 与 Platform 测试。`paytech-payment-api` 坐标不变，现在解析自 Reactor。
17. `paytech-messaging` 新增配置 `messaging.publisher.unroutable-policy = FAIL | ACK_AND_WARN`，默认 FAIL，语义不变。`messaging.published` 增加 `outcome` 标签（`confirmed` / `unroutable`），并补一个两种策略的 auto 用例。payment-app 与 Nacos 模板配置 `ACK_AND_WARN`，注释写明“第 08 步接入订阅者后改回 FAIL”。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并带 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`；凭据只经环境变量传递，没有打印。没有 up / down / restart 任何容器。

### 5.1 测试数与 Reactor

- **搬迁前基线**：在旧根执行 `cd payment-orchestration-service && ./mvnw -B verify`，BUILD SUCCESS，Surefire 执行 **127** 例，0 失败。源码方法数为 **96**，与 10 §1.1 一致。各类执行数如下：
  - 渠道 4 类共 56 例：QueryMid 14、GetConfig 30、MidCap 9、ChannelModuleBoundary 3。
  - 卡域纯 Java 2 类共 11 例。
  - 跨边界 6 类共 60 例。
- **channel-connector**：命令为 `./mvnw -B verify -pl channel-connector/paytech-channel-connector-test -am '-Dtest=com/paytech/channel/connector/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false`，BUILD SUCCESS，**75 例 0 失败 0 跳过**，源码方法 56 个。上游模块的测试被 `-Dtest` 过滤掉，只编译不执行。

  | 类 | 执行数 | 方法数 | 说明 |
  |---|---:|---:|---|
  | QueryMidCandidatesTest | 14 | 12 | 整类搬入，与基线相同 |
  | GetChannelExecutionConfigTest | 30 | 17 | 整类搬入，与基线相同 |
  | MidCapabilityResolutionTest | 9 | 9 | 整类搬入 |
  | ChannelConnectorArchitectureTest | 3 | 3 | 改名，方法名不变 |
  | ExecuteChannelOperationTest（新） | 15 | 10 | 映射 SUCCEEDED / PENDING / 受理后 FAILED / FAIL / 无法识别；6 个 Problem → NOT_SENT；其他错误 → ERROR；OE 不一致 → NOT_SENT；账号未配置 → NOT_SENT；请求校验 |
  | ChannelCandidateQueryHttpTest（新） | 3 | 3 | 能力投影、关闭的动作、配置损坏 500 |
  | ArchitectureAutoTest（新） | 1 | 1 | R1–R12 零违规 |

- **payment 与 channel-connector（任务卡命令）**：`./mvnw -B verify -pl payment/paytech-payment-test,channel-connector/paytech-channel-connector-test,foundation/paytech-messaging -am`，在真实 `mor_payment` 与 `mor_channel_connector` 上 BUILD SUCCESS。
  - 结果：payment-test **72 例**、channel-connector-test **75 例**、paytech-messaging **55 例**（54 + 新增未路由策略 1 例）、arch-rules 57 例，上游 Foundation 各模块全部 0 失败 0 跳过。
  - 在此之前，payment-test 曾在临时库 `mor_payment_step05_tmp` 上跑过一次，同为 72/72；该临时库已删除。

  | 旧类 → 新类（payment） | 旧方法 / 执行 | 新方法 / 执行 |
  |---|---|---|
  | PayCardChannelTest | 7 / 7 | 7 / 7 |
  | PayRiskEvaluationTest | 9 / 20 | 9 / 20 |
  | PaymentSucceededPublishTest | 4 / 4 | 4 / 4，新增“payment.events 收到 v1 信封”与“旧 Topic 仍收到”两条断言，均用真实本地 Broker 与 Probe |
  | GetSubmissionTest | 7 / 7 | 7 / 7 |
  | CardStorageTest | 13 / 14 | 13 / 14 |
  | StoredCardTest | 8 / 8 | 8 / 8 |
  | CardCipherTest、PaymentCardPropertiesTest | 7 / 11 | 7 / 11，纯 Java，随 payment |
  | ArchitectureAutoTest（新） | — | 1 / 1 |

- **合计**：执行数 75 + 72 = **147**，不少于基线 127；源码方法数 56 + 56 = **112**，不少于 96。两个服务的 `<skipped>` 均为 0。
- **arch-rules**：`-pl tools/arch-rules` 共 57 例 0 失败，含新增的过渡归属与 R11 豁免两例。`tools/arch-rules-reactor-test` 共 3 例 0 失败。
- **全 Reactor**：第一次运行（`./mvnw -B -fae clean verify`）中途主机 Data 卷写满（100%），MySQL 与 Docker 失去响应，多个依赖数据库的模块同时出现 `Communications link failure`，结果作废。用户清理并重启环境后，按 team-lead 安排，全 Reactor 由主会话合入后统一运行，本步不再重跑。
- **platform-support-test**：本步改了 Platform 的发现名与调用方名，按 team-lead 安排在 step04 跑完后运行：待 team-lead 通知后运行（尚未运行）。

### 5.2 两个 app 以 mock Profile 启动

- 两个 app 都用 `java -jar <exec.jar> --spring.profiles.active=mock --spring.config.additional-location=file:<test>/application-mock.yml --test.mock-http.base-url=http://127.0.0.1:9 --paytech.local-environment.dir=…` 启动；channel-connector 用 28110 / 29110，payment 用 28103 / 29103。
- 结果：`Started ChannelConnectorApplication in 1.8 s`，`Started PaymentApplication in 3.2 s`，日志中 0 条 ERROR。
- 三个探针都返回 `200 {"status":"UP"}`：
  - channel-connector：`29110/actuator/health`、`/liveness`、`/readiness`。
  - payment：`29103/actuator/health`、`/liveness`、`/readiness`。
- 两个进程验证后均已停止。

### 5.3 Node

- `npm --prefix requirements-site ci` 后运行 `npm --prefix requirements-site test`：137 项，127 通过，10 失败。
  - 在起点提交的 `git archive` 上用同一份 `node_modules` 运行，结果同为 137 / 127 / 10，失败测试名逐个相同（diff 为空）。
  - 读取 `paytech/payment` 两个文件的断言单独核对通过。
  - 那 10 个失败都在与 Payment 路径无关的断言上。
- PDManer 相关测试 `node --test scripts/pdmaner/payment-model.test.mjs payment-scenario-doc.test.mjs payment-channel-query.test.mjs payment-execution-simplification.test.mjs pdmaner-canonical-paths.test.mjs oe-client-model.test.mjs`：37 项，31 通过，6 失败；起点为 30 通过、7 失败。
  - `Channel target has scoped accounts…` 由失败转为通过。
  - 其余 6 个失败与起点相同，失败信息一致：卡域 4 表和 5 条卡关系不在测试 Oracle 中；`encrypted_pan` 命中字段名禁令；模型摘要过期；场景文档引用 `adapter_code`；Commerce Schema 漂移。“本地关系”测试的差异行与起点逐行相同。
- `node scripts/pdmaner/generate-channel-connector-model.mjs --check`：up to date（13 表）。`generate-payment-model.mjs --check` 报 stale，起点相同，原因见第 6 节第 8 项。
- `node scripts/verify-local-environment.mjs`：输出与起点一致，只剩 4 行既有的 Gateway 文档引用失败。初次修改后新增的 2 项（库数、变量清单）已在脚本清单中修正。

### 5.4 历史与旧根

- `git log --follow` 对 `paytech/payment/paytech-payment-core/.../PayActionActor.java` 追溯到 `35f0554`、`cbb3de5`、`81d546e`、`fff35d2`。
- 对 `paytech/channel-connector/paytech-channel-connector-core/.../ExecuteChannelOperationProcessor.java` 与 `domain/PlatformPayOutputs.java`，经 `b3c8805` 追溯到 `81d546e`。
- `ls payment-orchestration-service` 返回 No such file or directory，`git ls-files payment-orchestration-service` 为 0。

### 5.5 R4 零违规与卡表过渡

- `node tools/arch-rules/scripts/generate-table-ownership.mjs --check` 输出 `table-ownership.yaml is current (265 physical tables + 56 retained).`。新增的 `transitionalPlacements` 段在生成器中参数化，物理表计数不变。
- 两个服务的 `ArchitectureAutoTest` 都零违规；`ChannelConnectorArchitectureTest.keepsChannelTablesInsideOwnerModule` 分别对两个服务执行 R4，结果为空。
- 卡表经过渡登记计入 payment 的 R4 白名单，R11 只豁免 `paytech-payment-card`。
- Enforcer 负例：临时给 `paytech-payment-test` 直接加上 `paytech-payment-card` 依赖后，`validate` 以 `Only paytech-payment-core and paytech-payment-app may depend on paytech-payment-card` 失败，core 与 app 显示 `Skipping Rule Enforcement`。负例改动已复原。

### 5.6 本机数据库操作（root，`docker exec -i mor-local-mysql-1 mysql -uroot`，密码取自 `.env.local` 且未打印）

1. 2026-10-17T18:04:31Z：执行 `mysql -uroot --default-character-set=utf8mb4 < paytech/channel-connector/paytech-channel-connector-test/src/test/resources/init/ddl.sql`。
   - 新建 `mor_channel_connector`、账号 `channel_connector_app`（仓库默认密码），授 `SELECT, INSERT, UPDATE, DELETE`，建 13 张表。
   - 核对结果：13 张表；`SHOW GRANTS` 与 DDL 一致。
2. 对 `mor_payment` 的 13 张旧表执行 DROP 时被权限分类器拒绝，**未执行**。
3. 2026-10-17T18:27:08Z：新建临时库 `mor_payment_step05_tmp`，给 `payment_app` 授 `SELECT, INSERT, UPDATE, DELETE`，应用把库名替换后的 payment DDL，用于一次验证。
4. 2026-10-17T19:40:58Z，选项 (2)，team-lead 决定，不 DROP：
   - 先执行 `mysqldump -uroot --no-data mor_payment`，备份写到 scratchpad：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/d1bc4b55-586b-45ae-abbc-d503d0fde214/scratchpad/step05/mor_payment-legacy-schema-20261017.sql`，共 13 个 CREATE TABLE。该目录是会话临时目录，需要长期保留请自行复制。
   - 逐表 `COUNT(*)` 核对 13 张表均为 0 行。
   - 执行：

     ```sql
     CREATE DATABASE IF NOT EXISTS mor_payment_legacy_20261017 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
     RENAME TABLE mor_payment.`t_channel_refund` TO mor_payment_legacy_20261017.`t_channel_refund`, …（13 张表同一条语句）;
     ```

     13 张表为 `t_channel_refund`、`t_channel_transaction_ref`、`t_command_idempotency`、`t_compensation_event`、`t_duplicate_payment_case`、`t_payment_action`、`t_payment_attempt`、`t_payment_instrument`、`t_payment_intent`、`t_payment_intent_binding`、`t_payment_pricing_fact`、`t_payment_refund_position`、`t_payment_state_transition`。完整语句另存于同目录的 `rename.sql`。
   - 第一次尝试用 `GROUP_CONCAT` 生成语句被截断，导致语法错误；`RENAME TABLE` 是原子的，那次没有移动任何表。
   - 核对结果：`mor_payment_legacy_20261017` 有 13 张表，`mor_payment` 为 0 张。
   - **用户可以随时 `DROP DATABASE mor_payment_legacy_20261017`；也可以先清空 `mor_payment`，再执行反向的 `RENAME TABLE mor_payment_legacy_20261017.<t> TO mor_payment.<t>` 迁回。**
5. 2026-10-17T19:41:05Z：执行 `mysql -uroot … < paytech/payment/paytech-payment-test/src/test/resources/init/ddl.sql`。
   - `mor_payment` 建出 15 张表。脚本中的 `GRANT SELECT, INSERT, UPDATE, DELETE` 对 `payment_app` 原有的 `ALL` 授权不产生收缩。
6. 2026-10-17T19:41:06Z：先执行 `REVOKE SELECT, INSERT, UPDATE, DELETE ON mor_payment_step05_tmp.* FROM 'payment_app'@'%'`，再执行 `DROP DATABASE mor_payment_step05_tmp`，删除我自己建的临时库。
7. 安全事件：19:40 一条 zsh 命令写法错误（把含密码的命令放进变量执行），报错信息把本机 MySQL root 密码打印到了会话输出。该密码只用于本地开发，没有写入仓库或本报告；已告知 team-lead，建议用户轮换。

## 3. channel-connector-api 合同清单

服务发现名 `channel-connector-service`，无 context path。ID 与金额在 JSON 中一律为十进制字符串。

| 合同 | 方法与路径 | 请求 | 响应 |
|---|---|---|---|
| `ChannelCandidateQueryApi` | `queryMidCandidates`，`POST /channel-connector/v1/mid-candidates` | `QueryMidCandidatesRequestBody{entityNo, criteria: MidCriteriaBody{paymentMethodCode, schemeCode?, countryCode, currency, mccCode}}` | `QueryMidCandidatesResponseBody{candidates: [MidCandidateBody{channelId, channelAccountId, midId, supportedOperations[], minAmount?, maxAmount?}]}`；空候选为 200 空列表；配置损坏 500；校验失败 422 |
| `ChannelOperationApi` | `execute`，`POST /channel-connector/v1/operations` | `ExecuteChannelOperationRequestBody{entityNo, channelId, channelAccountId, midId, operation, purpose, criteria?, amount?, input}`；`input` 为标准 API 业务输入，含支付工具句柄、尝试 ID、金额、回跳地址等，不含卡值与账号材料 | `ChannelOperationResponseBody{outcome: SUCCESS\|FAIL\|ERROR\|NOT_SENT, paymentStatus: SUCCEEDED\|PENDING\|FAILED\|null, redirectUrl, channelTransactionRef, channelStatus, failureCode, failureMessage, rawOutputKeys[]}` |

- 稳定代码枚举：`ChannelOperation`（PAY、AUTHORIZE、AUTHENTICATE、CAPTURE、VOID、REFUND、QUERY）、`ChannelOperationOutcome`、`ExecutionPurpose`（NEW_PAYMENT、ORIGINAL_TRANSACTION、RECOVERY）、`ChannelPaymentStatus`。能力代码常量为 `ChannelCapabilityCodes`。
- NOT_SENT 的几种情形：冻结坐标不存在或跨 Channel / OE，`failureCode=CHANNEL_TARGET_UNAVAILABLE`；账号缺少 Vault 引用，为 `CHANNEL_ACCOUNT_NOT_CONFIGURED`；Platform 合同保证未发出的 Problem，为原 Problem code。
- Platform 的其他失败，包括超时、网络错误、未知 5xx 与无法识别的 executeStatus，一律为 ERROR。
- 请求校验失败返回 422，用途与操作不符返回 400；两种情况都不调用 Platform。
- 标准 API 编码取渠道操作代码，即 PAY → `PAY`。

## 4. 调用方需要改的发现名（本步未改，禁止改 Commerce / Platform 目录）

| 调用方 | 位置 | 现值 | 应改为 |
|---|---|---|---|
| Commerce（acquiring） | `commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/PaymentClientConfiguration.java`、`…/application.yml`；测试 `paytech-commerce-test/src/test/resources/application-mock.yml`、`PaymentTransactionProbe.java` | `payment-orchestration-service` | `payment-service` |
| Platform 读卡回调（**本步已改**） | `paytech/platform-support/paytech-platform-support-app/src/main/java/com/paytech/platform/support/configuration/PaymentCardMaterialConfiguration.java`、`…/application.yml` | `payment-orchestration-service` | `payment-service`（第 12 步再改为 `card-service`） |
| Platform 内部 Token 签发白名单与调用方配置（**本步已改**，Payment 侧名字；channel-connector 待登记） | `paytech-platform-support-integration/.../ExternalApiExecutionProperties.java`、`paytech-platform-support-delivery/.../SubmitNotificationRequest.java`、Platform 测试（`InternalAuth*Test`、`NotificationHttpApiTest`、`NotificationCallerPropertiesTest`、`application-mock.yml`） | `payment-orchestration-service` | Payment 侧调用方改为 `payment-service`；channel-connector 调用 Platform 标准 API 时以 `channel-connector-service` 身份出现，需加入 Platform 的调用方 / 签发白名单 |
| Operations BFF 测试样本 | `paytech/operations-console-bff/.../notification-management-boundary.json` | `payment-orchestration-service` | 随 Platform 调用方名同步 |
| Gateway / Platform Target 服务码 | `scripts/pdmaner/platform-support-scenario-doc.test.mjs` 场景表（09 §4.3 已注明随应用名改名另行处理） | `payment-orchestration-service` | `payment-service` |

## 5. Vault Caller 名变更清单（Vault 侧访问规则需主会话登记）

| 用途 | 旧 caller | 新 caller | 数据类型 / Purpose |
|---|---|---|---|
| 卡 DEK 托管与读取 | `payment-orchestration-service` | `payment-service` | `PAYMENT_CARD_DEK`：Protect `CARD_STORAGE`、Reveal `CARD_USE`（第 12 步再改为 `card-service`） |
| 渠道账号配置读取（`GetChannelExecutionConfigProcessor`，无生产调用方） | `payment-orchestration-service` | `channel-connector-service` | 渠道账号 REFERENCE Token：Reveal `CHANNEL_EXECUTION` |

渠道操作执行时由 Platform 自己 Reveal 账号与卡，Platform 的 caller 不变。

## 6. 与设计 / 任务卡的偏差

1. **`ChannelExecutionConfigApi` 没有发布 HTTP 合同。** `GetChannelExecutionConfigProcessor` 会返回渠道账号的明文参数与凭据，且没有生产调用方。按 2026-10-13 账号读取决定，Reveal 只应在 Platform 执行时发生，开放这个入口会扩大凭据外泄面。Processor 与 17 个方法（30 例）的测试原样保留在 channel-connector 中。
2. **结论 DTO 多了两个字段。** `ChannelOperationResponseBody` 比任务卡多出 `channelStatus` 与 `failureMessage`。原 `PayActionActor` 把渠道原始状态写入 `channel_response_code`，把失败说明写入 `channel_response_msg`；少了这两项，逻辑就不再等价。
3. **候选查询入参沿用现有 Processor 请求**，即 `entityNo` 加 `criteria`，不含任务卡举例的 amount、requiredActions、captureMode。按动作与金额筛选仍在 Payment 的 `RouteServiceImpl`，channel-connector 只返回已解析的 `supportedOperations` 与金额上下限，不再返回逐代码的能力明细，Payment 因此无法再解析能力代码。
4. **渠道操作请求不带标准 API 编码**，由 channel-connector 按操作代码确定（PAY → `PAY`），Payment 不再知道 Platform 的编码。原 `ExecuteChannelOperationRequest.standardApiCode` 字段已删除。
5. **channel-connector 不设 context path**，路径即 `/channel-connector/v1/...`，与 Risk 的做法一致。
6. **Payment 启用独立管理端口 19103。** 健康检查从 `18103/payment/actuator/health` 改为 `19103/actuator/health`，部署探针需要同步，01 §3 规定管理端口 = 业务端口 + 1000。
7. **channel-connector 的 DDL 与清理器覆盖 13 张表**，即 10 §3.1 的 6 张加上“S7 若判入”的 7 张，因为 S7 已定。Payment 为 15 张表：10 张 payment 表、卡 4 表与补偿表。争议 3 表（归 dispute）与 `t_command_idempotency`（S10 删除）不进入测试 DDL，但仍留在 payment 的 PDManer 模型与 Schema SQL 中。
8. **PDManer 是按已提交模型分区得到的，没有从生成器重新生成。** 起点提交的生成器只有 27 表清单，卡域 4 表从未写入生成器，因此 `generate-payment-model.mjs --check` 在起点就失败，本步后仍然失败，原因相同。channel-connector 分区与生成器输出逐字节相同，`--check` 通过。
9. **补偿 `event_key` 仍为尝试 ID**，没有改成 `AggregateKey` 的四段式，以免改变发布任务的幂等身份。新信封的 `eventId` 使用四段式 `PAYMENT_ATTEMPT:<id>:PaymentSucceeded:<version>`。
10. **Payment 测试为每例绑定双写观察队列**（全局 BeforeEach / AfterEach），原来只在 `PaymentSucceededPublishTest` 中绑定。原因见待确认第 2 项。
11. **`PlatformMocker` 留在 payment-test**，只承担读卡回调，由 `ChannelConnectorMocker` 在渠道操作含 card.token 时调用。原 `MockPlatformController` 搬到 channel-connector-test，由那里的 `PlatformMocker` 提供标准 API DTO。
12. **两个 app 都引入 internal-auth**，`mode: "OFF"`。非 OFF 时，channel-connector、Risk 与 Platform 三个出站客户端附加内部 Token，缺少 Token 来源时拒绝启动。
13. **候选查询失败的状态码可能改变。** 原来配置损坏时进程内抛 `ErrorException`，pay 返回 500；现在 channel-connector 返回 500，Payment 客户端把 5xx 映射为依赖不可用，pay 可能返回 503。两种情况下都不保存 Attempt，与原来一致。
14. **`ChannelConnectorArchitectureTest` 改写了断言口径**，3 个方法名不变。原“Channel 公开类型白名单”改为三条：Payment 只能引用 `com.paytech.channel.connector.api`；channel-connector 不引用 Payment；api 不引用本服务实现。表归属改为执行 R4，同时保留对 Payment 源码的渠道表名扫描。原 `paytech-payment-channel` 模块级 Enforcer（不依赖其他 Payment 模块）由聚合 POM 的 `*-core` / `*-app` 禁令与 R1 取代。
15. **Foundation 改动（任务卡例外，team-lead 决定）。** `paytech-messaging` 增加未路由策略 `messaging.publisher.unroutable-policy`。payment 本步配置为 `ACK_AND_WARN`：在第 08 步接入订阅者之前，`payment.events` 的信封被退回时记 WARN 与 `messaging.published{outcome=unroutable}`，然后视为成功，旧 Topic 仍严格要求路由。**第 08 步任务卡必须把 payment 改回 `FAIL`。**
    - `messaging.published` 原来只有 `eventType` 一个标签，现在增加了 `outcome`。原因是同一个指标名的标签键必须一致，Prometheus 注册表才能接受。已有看板若按指标名统计不受影响。
16. **Platform 目录改动（任务卡第 6 节例外，team-lead 决定，D42 同口径）。** 只改发现名与调用方名，见第 1 节第 16 项。Commerce（第 03 步）与 Risk（第 06 步）仍在旧根，本步没有改。

## 7. 待确认项

1. **`mor_payment` 的 13 张旧表已按选项 (2) 迁走**，见 5.6 第 4 项。旧结构保留在 `mor_payment_legacy_20261017`，是否 DROP 由用户决定。
2. **第 08 步必须把 payment 的 `messaging.publisher.unroutable-policy` 改回 `FAIL`。** 本步按 team-lead 决定配置为 `ACK_AND_WARN`，见第 6 节第 15 项；第 08 步任务卡需要写入这一项。
3. **补偿 `event_key` 的格式。** 现在的 `event_key` 只是尝试 ID，不满足 `AggregateKey.eventKey` 的四段式（任务卡 §3.5 要求核对）。第 08 步的 `AggregateSerialExecutor` 若按 `fromEventKey` 解析，需要兼容这种格式；也可以改为四段式，但要处理存量任务与幂等身份。
4. **调用方发现名。** Platform 已在本步改为 `payment-service`。Commerce 在第 03 步改，Risk 在第 06 步改；在此之前，refactor-main 上 Commerce 到 Payment 的链路不通。channel-connector 以 `channel-connector-service` 身份调用 Platform 标准 API；该接口目前不按调用方限制，将来若启用白名单，需要登记这个名字。Operations BFF 测试样本 `notification-management-boundary.json` 中的 `sourceSystem` 仍是旧名，只是测试数据，留给后续步骤。
5. **Vault 访问规则。** 第 5 节两个新 caller 需要在 Vault 侧登记。
6. **compose 文件没有改。** 初始化脚本已把 `MOR_CHANNEL_CONNECTOR_DB_PASSWORD` 列为必填，但 `local-environment/compose.yaml` 的 `mysql.environment` 还没有透传这个变量；新建容器时初始化会因缺变量失败，需要主会话补一行。另外，本机 `.env.local` 没有这个键，所以测试库账号使用 `application-mock.yml` 的仓库默认值 `local-channel-connector-change-me-2026` 创建；日后若在 `.env.local` 设置了别的值，需要同步修改账号密码。
7. **实例 ID 的默认值。** `paytech.instance-id` 在 Payment 中默认 `main`，即实例清单示例，供信封的 `instanceId` 使用。Platform 的默认值为空，生产环境应由部署注入。
8. **channel-connector 没有 Snowflake 生成器。** 本服务目前没有写入，生产配置中不含 Snowflake；测试 Fixture 自建生成器（区域 31、节点 29）。将来实现回调落库时，再按实例清单分配 worker id。
9. **共享 Broker 上的新交换机。** 本地共享 Broker 上已经出现 `payment.events`（Durable Fanout，无绑定），是 app 启动与测试声明的。第 08 步会复用它，它不影响其他服务。

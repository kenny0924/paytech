# 第 05 步报告 · Payment → payment + channel-connector（卡域暂留 payment 进程与库）

- 分支：`refactor/step-05-payment-channel`（起点 `refactor-main@7e0b70d`）。
- 提交：`b3c8805` 纯改名搬迁（内容未改，该提交单独不可编译）；`c1fb7c6` 内容改写；其后为验证修正与本报告。
- 结论：代码、配置、文档、PDManer 与本地环境改动均已完成并提交。单独执行的验证全部通过：channel-connector-test 75/75，payment-test 72/72，arch-rules 57/57，Reactor 架构测试 3/3；两个 app 以 mock Profile 启动后 health 为 UP。**还有两项没有完成：**
  - payment-test 只在临时库 `mor_payment_step05_tmp` 上跑通。`mor_payment` 里有 13 张空的旧表，DROP 被权限分类器拒绝，等待决定（待确认第 1 项）。
  - 全 Reactor `clean verify` 跑到中途时主机磁盘写满（Data 卷 100%），MySQL 与 Docker 失去响应，多个依赖数据库的模块因此失败（见 5.1）。Docker 至今无响应，没能复跑。

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

- **payment**：命令为 `./mvnw -B verify -pl payment/paytech-payment-test -am '-Dtest=com/paytech/payment/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false`，BUILD SUCCESS，**72 例 0 失败 0 跳过**，源码方法 56 个。
  - 这次运行不在 `mor_payment` 上，原因见待确认第 1 项。数据库是 root 新建的临时库 `mor_payment_step05_tmp`（命令见 5.6），经环境变量 `SPRING_DATASOURCE_URL` 指过去。
  - 为这次运行，我在本地把 `PaymentDataMocker` 的库名守卫临时改为该库，跑完立即复原，`git diff` 为空，没有提交。

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
- **全 Reactor**：命令为 `./mvnw -B -fae clean verify`，62 个模块，BUILD FAILURE。
  - **结论不可用。** 运行中主机 Data 卷写满（`df` 显示 100%，可用 2.0 GiB）。约 22:40 起，vault、platform-support、api-gateway、payment、channel-connector、migration-runner 的测试同时出现 `Communications link failure / connection unexpectedly lost`，之后 `docker ps` 与 `docker exec` 都无响应。
  - payment-test 那 60 例失败是 `mor_payment` 前置检查不通过，原因见待确认第 1 项。
  - 不依赖数据库的模块全部通过：Foundation 各模块、arch-rules 57、instance-manifest 16、data-service 16、reactor-test 3 等。
  - 我已删除自己产生的 scratch 归档与 `requirements-site/node_modules`，约 650 MB，没有动容器，并已通知 team-lead。随后可用空间回升到 10 GiB（不是我释放的），但 Docker 仍无响应，没能复跑。
  - 待环境恢复、`mor_payment` 处理完成后，需要复跑 `./mvnw -B verify -pl payment/paytech-payment-test,channel-connector/paytech-channel-connector-test -am` 与全 Reactor。

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
2. 对 `mor_payment` 的 13 张旧表（逐表 `COUNT(*)` 均为 0）执行 DROP，被权限分类器拒绝，**未执行**。`mor_payment` 保持原状。
3. 2026-10-17T18:27:08Z：执行 `mysql -uroot … < <payment ddl.sql，把库名替换为 mor_payment_step05_tmp>`。
   - 新建临时库 `mor_payment_step05_tmp`，给 `payment_app` 授 `SELECT, INSERT, UPDATE, DELETE`，建 15 张表。
   - 该库与授权留待清理决定，见待确认第 1 项。

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
| Platform 读卡回调 | `paytech/platform-support/paytech-platform-support-app/src/main/java/com/paytech/platform/support/configuration/PaymentCardMaterialConfiguration.java`、`…/application.yml` | `payment-orchestration-service` | `payment-service`（第 12 步再改为 `card-service`） |
| Platform 内部 Token 签发白名单与调用方配置 | `paytech-platform-support-integration/.../ExternalApiExecutionProperties.java`、`paytech-platform-support-delivery/.../SubmitNotificationRequest.java`、Platform 测试（`InternalAuth*Test`、`NotificationHttpApiTest`、`NotificationCallerPropertiesTest`、`application-mock.yml`） | `payment-orchestration-service` | Payment 侧调用方改为 `payment-service`；channel-connector 调用 Platform 标准 API 时以 `channel-connector-service` 身份出现，需加入 Platform 的调用方 / 签发白名单 |
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

## 7. 待确认项

1. **`mor_payment` 的 13 张旧表。** 本机 `mor_payment` 保留着旧模型的 13 张表，全部为空，与当前测试 DDL 同名冲突。我对它们执行 DROP 时被权限分类器拒绝。payment-test 因此在临时库 `mor_payment_step05_tmp` 上验证（见第 2 节），`mor_payment` 未被改动。需要决定三种做法之一：用户批准 DROP；RENAME 到 `mor_payment_legacy_20261017`；由用户自行处理。处理后在 `mor_payment` 上复跑 `-pl payment/paytech-payment-test -am`。临时库与授权留待一并清理。
2. **第 08 步前 `payment.events` 没有订阅队列。** 发布端按设计使用 mandatory 并检查退回，所以新信封会被退回。补偿任务先成功发布旧 Topic，之后因信封未路由而重试，12 次后进入 FAILED，旧报文也会重复发布，Commerce 按事件键去重。refactor-main 应在第 08 步声明订阅方之后再部署。另一种做法是决定第 08 步前容忍未路由的信封，这需要改动 Foundation，因为发布端异常不区分未路由与 Nack。
3. **补偿 `event_key` 的格式。** 现在的 `event_key` 只是尝试 ID，不满足 `AggregateKey.eventKey` 的四段式（任务卡 §3.5 要求核对）。第 08 步的 `AggregateSerialExecutor` 若按 `fromEventKey` 解析，需要兼容这种格式；也可以改为四段式，但要处理存量任务与幂等身份。
4. **调用方发现名本步未改。** Commerce 与 Platform 仍然调用 `payment-orchestration-service`，清单见第 4 节。在各自步骤修改之前，refactor-main 上 Commerce 到 Payment、Platform 读卡回调到 Payment 的链路都不通。channel-connector 以 `channel-connector-service` 身份调用 Platform 标准 API，Platform 的调用方白名单若按 caller 限制，需要加入这个名字。
5. **Vault 访问规则。** 第 5 节两个新 caller 需要在 Vault 侧登记。
6. **compose 文件没有改。** 初始化脚本已把 `MOR_CHANNEL_CONNECTOR_DB_PASSWORD` 列为必填，但 `local-environment/compose.yaml` 的 `mysql.environment` 还没有透传这个变量；新建容器时初始化会因缺变量失败，需要主会话补一行。另外，本机 `.env.local` 没有这个键，所以测试库账号使用 `application-mock.yml` 的仓库默认值 `local-channel-connector-change-me-2026` 创建；日后若在 `.env.local` 设置了别的值，需要同步修改账号密码。
7. **实例 ID 的默认值。** `paytech.instance-id` 在 Payment 中默认 `main`，即实例清单示例，供信封的 `instanceId` 使用。Platform 的默认值为空，生产环境应由部署注入。
8. **channel-connector 没有 Snowflake 生成器。** 本服务目前没有写入，生产配置中不含 Snowflake；测试 Fixture 自建生成器（区域 31、节点 29）。将来实现回调落库时，再按实例清单分配 worker id。
9. **共享 Broker 上的新交换机。** 本地共享 Broker 上已经出现 `payment.events`（Durable Fanout，无绑定），是 app 启动与测试声明的。第 08 步会复用它，它不影响其他服务。

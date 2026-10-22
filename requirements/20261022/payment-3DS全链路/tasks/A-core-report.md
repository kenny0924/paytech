# 卡 3DS-A 执行报告：channel-connector、payment、acquiring、messaging 目录

- 日期：2026-10-22；执行代理：3DS-A
- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-3ds`（detached `4163f9f9`）。注意：卡片写的是 `/Users/kenny/Desktop/Workspace/Code-MoR-3ds`，该路径不存在；`git worktree list` 显示本卡工作区实际建在主工作区目录内，本卡全部改动都在该目录，未改主工作区（报告文件除外）。
- 未提交、未推送、未建分支；未调用 Superpowers。

## 1. 开工自查

**已读：** `AGENTS.md`；`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`（含第 11 节）、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（第 5、13—15 节）、`COMPENSATION_EVENT_GUIDE.md`；需求 `requirement.md`、`technique.md`（第 2、3 节）、`execution-3ds-amendment.md`、`db/` 三份草案、`tasks/00-common.md`、`tasks/A-core.md`；`paytech/foundation/README.md` Messaging / Internal Auth 节。

**所属 Processor / Context、原子写入与事务外步骤：**

| 入口 | Processor / Context | 本地原子写入 | 事务外步骤 |
|---|---|---|---|
| Payment 确认支付结果（三入口共用） | `ConfirmPayResultProcessor`（Intent 锁 TRY_LOCK 500 ms）/ `PayContext` | `ActionFlow.complete` → `advanceAction` 一个事务：Action CAS（版本 + 原状态）、渠道交易引用（同号跳过）、失败释放 Intent 或成功完成 Attempt / Intent 并登记 `PAYMENT_SUCCEEDED`；仍在运行时登记 `PAY_RESULT_QUERY`（已存在按重复） | channel-connector `QUERY`（事务前、锁内） |
| Payment 刷新 | `RefreshSubmissionProcessor`（无锁）→ `GetSubmissionProcessor` + `ConfirmPayResultProcessor` | 同上 | Redis `SET NX` 5 秒限频、查单 |
| Payment `PAY_RESULT_QUERY` | `PayResultQueryHandler` → `ConfirmPayResultProcessor` | 同上 | 同上 |
| Payment MQ 消费 | `ChannelPaymentNotifiedListener`（HANDLER_MANAGED）→ `ConfirmPayResultProcessor` | 同上；消费登记由 messaging 另开短事务 | 同上 |
| Payment PAY | `PayActionActor`（既有流程） | 结果事务：PROCESSING / UNKNOWN 同事务登记 `PAY_RESULT_QUERY` | 3DS 能力检查（纯内存）、读卡票据、渠道 PAY（含 `threeDsRequired`、`notifyUrl`） |
| channel-connector 通知入站 | `ReceiveChannelCallbackProcessor` / `ChannelCallbackContext` | Processor 开一个事务：`ChannelCallbackService.saveCallback`（信封 + 回调项）+ 登记 `CHANNEL_PAYMENT_NOTIFIED` | 事务前读渠道 / 账号、Vault Reveal 取公钥、验签 |
| channel-connector 发布 | `ChannelPaymentNotifiedHandler` | 发布确认后短事务 RECEIVED → PROCESSED（版本 CAS） | `DomainEventPublisher.publish`（Publisher Confirm） |

## 2. 改动清单（对应 technique §3.6 顺序 1—6、9）

**1 channel-connector：** `ChannelOperation` 增加 `standardApiCode`（QUERY → `PAY_QUERY`）；`ChannelPaymentStatus` 增加 `CLOSED`、`NOT_FOUND`；`ChannelOperationResponseBody` 增加 `nextActionType`、`amount`、`currency`；新 `PayQueryOutputs`，`PayOutputs` 只承认 SUCCEEDED / PENDING / FAILED 并读出 `nextAction.type`（有跳转但无类型或类型不在合同内 → REDIRECT）；`ChannelOperationResult.queried`；`ExecuteChannelOperationProcessor` 按操作选标准 API 与输出合同；`MidCandidateBody` / `MidCandidateView` 增加 `threeDsMode`（及 `channelCode`，见偏离 1），`ChannelCapabilityCodes` 增加 `SECURITY_3DS_MODE` 与三个取值常量。
**2 payment：** 卡流程 `CardPayFlow` 去掉 AUTHENTICATE 转移（删除不再使用的 `AuthenticationRequiredCondition`；`AuthenticateActionActor` 保留）；`PayInput.threeDsRequired / notifyUrl`；`PayActionActor`：读前一 PAY_RISK 的冻结认证要求（THREE_DS_REQUIRED / CHALLENGE_REQUIRED → 要求），冻结 MID 非 SUPPORTED / REQUIRED 时不申请票据、不调渠道以 `THREE_DS_CHANNEL_UNAVAILABLE` 失败，按渠道类型记交互，`onRunningInTransaction` 登记 `PAY_RESULT_QUERY`；`ActionActor.onRunningInTransaction`；`ActionFlow.complete` 与“同动作同外部编号跳过、不同编号系统错误”；`RouteServiceImpl` 按 3DS 稳定排序；`ConfirmPayResultProcessor`、`PayResultConfirmation`、请求 / 响应；`PayResultQueryHandler`（20 s / 56 次 / 10 s ×2 / 30 min / 0.2；24 小时 `PAY_RESULT_UNRESOLVED`；金额不一致 `PAY_RESULT_AMOUNT_MISMATCH`）与 `compensation-event-catalog.yaml`；刷新 API（`PaymentIntentApi.refreshSubmission`、`RefreshSubmissionRequestBody`、Controller、`RefreshSubmissionProcessor`）；`PaymentExecutionProperties.channelCallbackBaseUrl`；`PaymentExecutionService.findAttempt`；`PaymentActionRepository.findByAttemptAndNo`；application.yml / Nacos 模板 / `docs/development/configuration.md`。
**DDL / 模型：** `scripts/pdmaner/payment/payment-model-additions.mjs`（新，追加两张消费表）+ `generate-payment-model.mjs` 写 payment 模型时应用；`payment-execution-field-overrides.mjs`、`payment-field-metadata.mjs`（interaction_type 注释）；`payment.pdma.json` 重新生成；`payment-schema.sql` 由 `renderSchema` 渲染（与草案逐字一致）；测试 `init/ddl.sql`；`PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`；`tools/arch-rules` 生成器 + `table-ownership.yaml` + `TableOwnershipAutoTest` 计数。
**3 acquiring：** `CheckoutCardPaymentPort.refresh`、实现调用 Payment 刷新；`GetCustomerPaymentProcessor` 改用刷新；两处交互类型校验接受 THREE_DS_CHALLENGE / REDIRECT；`CustomerPaymentApi` 文档改述；测试 `MockPaymentIntentApi.refreshSubmission`、`PaymentSubmissionMocker` 记录操作。
**4 channel-connector 入站：** `ChannelCallbackApi` + `ChannelCallbackController`（已验证内部调用方须为 api-gateway）；`ReceiveChannelCallbackProcessor`；`ChannelCallbackDefinition`（解析 / 校验 / RSA_SHA256 验签 / 字段读取）、`ChannelPaymentNotice`、`ChannelCallbackContext`；`ChannelCallback(Item)` DataObject、Mapper / XML、Repository；`ChannelCallbackService(Impl)`；四个闭合枚举；`ChannelCallbackErrorCode`（401 / 403 映射）；`ChannelPaymentNotifiedHandler` + 目录登记；`ChannelPaymentNotifiedPayload`（api/event）；`ChannelEventProperties`；protocol `Channel.callbackDefinitionJson`、`ChannelMapper.xml` 读取列、`ChannelIdentity`；pom（messaging-api、compensation-event、amqp、messaging）；application.yml / Nacos 模板（RabbitMQ、producer、event 身份）；`channel-model.mjs` 新字段与两处注释 → 模型重新生成，`channel-connector-schema.sql` 由 `renderSchema` 渲染（与草案一致），测试 `init/ddl.sql` 同步三张表块，`channel-connector-protocol-model.test.mjs` 字段清单。
**5 payment 消费：** `ChannelPaymentNotifiedListener`（`execution/ext/mq`）；messaging consumer 配置；两张消费表。
**6 messaging：** `catalog/events.yaml` 新条目 `ChannelPaymentNotified`；`YamlEventCatalogAutoTest`。
**9 文档：** execution-3ds-amendment.md 第 3 节所列 11 份需求文档加指向行（`acquiring-sequence.md` 核对一致未改）、`PAYMENT_ORCHESTRATION_DATABASE.md` 与 `PAYMENT_REAL_WORLD_SCENARIOS.md` 改述、`paytech/payment/README.md`、`paytech/channel-connector/README.md`。未改 CURRENT_IMPLEMENTATION。
**部署配置：** `test-environment/compose.applications.yaml` 为 channel-connector 增加 `CHANNEL_CONNECTOR_RABBITMQ_*` 与 rabbitmq 依赖（新版 channel-connector 启动即连 Broker，缺少会启动失败；未部署）。

规模：106 个已跟踪文件改动（+2000 / −206），新增文件约 3600 行（含测试与夹具）。

## 3. AC 对应

| AC | 实现 | 自动验证 |
|---|---|---|
| AC-01 | 卡流程 ALLOW → PAY；`payment.threeDsRequired=true` | `PayCardChannelTest.test_riskRequiresThreeDs`、`PayRiskEvaluationTest.test_allowWithThreeDs`（无 AUTHENTICATE、不卡 UNKNOWN） |
| AC-02 | 路由排序 + PAY 前能力检查 | `test_riskRequiresThreeDs`（选中 SUPPORTED）、`test_threeDsChannelUnavailable`；`QueryMidCandidatesTest.projectsThreeDsModeAndChannelCode` |
| AC-03 | nextActionType 透传、按渠道记交互、acquiring 接受 REDIRECT | `ExecuteChannelOperationTest.test_pendingWithRedirect / test_pendingWithChallengeType`、`PayCardChannelTest.test_redirectInteraction`、`GetCustomerPaymentTest.test_requiresRedirectAction` |
| AC-04 | `PAY_RESULT_QUERY` 退避查单推进、PaymentSucceeded | `PayResultQueryHandlerTest.test_backoffThenSucceeded`（经生产 Scanner）、`RefreshSubmissionTest.test_succeeded` |
| AC-05 | FAILED / CLOSED / NOT_FOUND（10 分钟后）失败并释放 Intent | `RefreshSubmissionTest.test_failed / test_closed / test_notFoundAfterGrace / test_notFoundWithinGrace` |
| AC-06 | 刷新 5 秒限频；acquiring 轮询调用刷新 | `RefreshSubmissionTest.test_rateLimited`、`GetCustomerPaymentTest`（调用记录为 REFRESH） |
| AC-07 | 入站验签、去重入库、发布、Payment 消费确认 | `ReceiveChannelCallbackTest.test_verifiedNoticePublished / test_signatureInvalidRejected`、`ChannelPaymentNotifiedHandlerTest`、`ChannelPaymentNotifiedListenerTest.test_noticeConfirmsPayment`（Gateway 段属卡 3DS-B） |
| AC-08 | 终态 CAS 只写一次、引用同号跳过、eventId 防重 | `ReceiveChannelCallbackTest.test_duplicateNoticeRecordedOnce`、`ChannelPaymentNotifiedListenerTest.test_duplicateNoticesWriteOnce / test_noticeAndRefreshConcurrent` |
| AC-09 | 24 小时转人工，不自动判失败 | `PayResultQueryHandlerTest.test_unresolvedAfter24Hours` |
| AC-10 | 刷新返回验证中交互；过期仍处理中显示确认中 | `RefreshSubmissionTest.test_stillPending`、`GetCustomerPaymentTest.test_processing` |
| AC-11 | 回调路径只记元数据、Payload 无原文 / 签名 / 金额、跳转地址不落库 | `ReceiveChannelCallbackTest`（CapturedOutput 检索交易号与签名）、`PayCardChannelTest.test_threeDsChallenge`（跳转地址不落库） |
| AC-12 | — | 人工 UAT，阶段 5 |

## 4. 本机 SQL（已授权）与备份

| 库 | 备份 | 执行文件 | 结果 |
|---|---|---|---|
| `mor_payment` | `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/backup/mor_payment-20261022163500.sql` | `requirements/20261022/payment-3DS全链路/db/payment/ddl.sql` | 两张消费表已建，`interaction_type` 注释已改 |
| `mor_channel_connector` | `…/backup/mor_channel_connector-20261022163500.sql` | `requirements/20261022/payment-3DS全链路/db/channel-connector/ddl.sql` | `t_channel.callback_definition_json` 已加，两处注释已改 |

执行后用 `information_schema.columns` 核对列类型、可空与注释与 Schema 一致。`db/channel-connector/dml.sql` 未执行（本机测试库 `t_channel` 为空，测试自行写入定义）。只连接 13306，未连接 mor-test。

## 5. 测试（均为本机前台执行；`-Dpaytech.local-environment.dir` 指向主工作区 `local-environment`，因工作区内没有 `.env.local`；acquiring 另按需导出 `MOR_RABBITMQ_*` 等键，未打印）

| 模块 | 命令范围 | 结果 |
|---|---|---|
| channel-connector-test | `-Dtest='com.paytech.channel.connector.**'`（整模块） | 390 通过，0 失败 |
| payment-test | 整模块 | 85 通过，0 失败（含新增 `RefreshSubmissionTest` 11、`PayResultQueryHandlerTest` 4、`ChannelPaymentNotifiedListenerTest` 4，`PayCardChannelTest` 新增 3、架构门禁） |
| acquiring-test | `-Dtest='com.paytech.acquiring.**'`（整模块） | 805 通过，0 失败 |
| foundation/paytech-messaging | `YamlEventCatalogAutoTest` | 12 通过 |
| tools/arch-rules | 整模块 | 68 通过（`TableOwnershipAutoTest` 计数 +2） |
| 全 reactor | `./mvnw -o test-compile` | 成功 |
| PDManer（node --test） | payment-model / execution-simplification / channel-query / scenario-doc / canonical-paths / channel-connector-protocol-model | 35 通过、2 失败：均为基线已有失败（见遗留 2），本卡未新增失败；`Payment canonical schema exactly matches the maintained model` 由本卡渲染后通过 |

## 6. 偏离与裁决

1. **候选增加 `channelCode`：** technique 3.3.4 让 Payment 按 `{base}/api/channel-callbacks/v1/{channelCode}/{channelAccountId}` 拼 notifyUrl，但 Payment 只有 channelId。裁决：`MidCandidateBody` / `RouteCandidate` / `RouteResult` 增加 `channelCode`，只放进本次 `PayContext`，不落库（与 threeDsMode 同处理）。
2. **`payment.threeDsRequired` 只在要求时传 true，不要求时省略；`notifyUrl` 只在配置根地址时传。** 标准 API 输入严格拒绝未声明字段，省略可避免 harness 未更新标准 API 前所有 PAY 被 NOT_SENT；代价：harness 更新前，风控要求 3DS 的 PAY 会 NOT_SENT 失败（部署顺序见 technique 3.4）。
3. **PAY 的 3DS 要求从 DB 读取前一动作（action_no − 1 的成功 PAY_RISK），不经 Context 传递**；前一动作不符按数据不一致抛出 → UNKNOWN。
4. **验签失败的通知不写 `payload_fingerprint`、不写回调项**：避免伪造通知抢占真实通知的去重键，也不映射未经验证的内容；无法映射的已验签通知信封直接 PROCESSED。
5. **新增 `CHANNEL_CALLBACK_CALLER_FORBIDDEN`（403）**：落实“只接受 Gateway 转发”：已验证内部 Token 的调用方不是 `api-gateway` 时拒绝；internal-auth OFF 时依赖私网，自动测试未覆盖该分支。
6. **Web 日志未增加 `/channel-callbacks/` 前缀**：channel-connector 现有 `metadata-only-path-prefixes` 已是 `/`（全部路径只记元数据），再加前缀无作用；`ReceiveChannelCallbackTest` 检索日志验证。
7. **验签公钥经既有 `ChannelAccountConfigReader`（Vault Reveal 用途 CHANNEL_EXECUTION）读取**，未新建读取口。
8. **`PayResultQueryHandler` 以 `ObjectProvider<ConfirmPayResultProcessor>` 延迟取得确认入口**：直接注入形成循环（确认 → 动作流程 → PAY → 补偿登记器 → 全部 Handler）。
9. **Risk 客户端只接受 NOT_REQUIRED / THREE_DS_REQUIRED**，`CHALLENGE_REQUIRED` 仍按要求映射但当前不会出现；测试用 THREE_DS_REQUIRED。
10. **事件 Payload 字段顺序**：信封按 RFC 8785 规范化，线上字段按名称排序；`FIELDS` 只作合同清单，测试按集合比对。目录 `aggregateVersionField` 写 `aggregateVersion`（固定 1）。
11. **测试方式**：channel-connector 的 Handler 入口测试沿用 `IntegrationVerifyRunHandlerTest` 的直接 `handle()` 写法（避免提交后唤醒并发）；payment 的 `PayResultQueryHandlerTest` 经生产 Scanner；共享 Broker 上 payment 分区拓扑可能已绑定 `channel-connector.events`，“未路由可重试”分支未测（由 messaging 组件测试覆盖）。Payment `ChannelConnectorMocker` 的 `pending()` / `unknown()` 同时配置查单“仍处理中 / 结果未知”，使提交后立即执行的查单恢复任务首轮不推进，已写入类注释。
12. **PDManer**：payment 生成器此前已与提交的模型不一致（`transaction_assessment_id` 注释与 namingRules）；本卡把生成器注释改为与已提交模型一致后再生成，namingRules 随生成器更新；消费表作为 `payment-model-additions.mjs` 追加，不进入 payment 字段 oracle（`payment-model.test.mjs` 以 `paymentAdditionTables` 比对，模型摘要与 PHYSICAL_DECISIONS 的 `modelSha256` 同步更新）。

## 7. 遗留

1. **`events.yaml` 合并**：主工作区未提交改动在同一文件末尾追加了 `WorkflowCallbackRaised` 并改了 `YamlEventCatalog.SERVICE_NAMES` 与目录测试的事件列表；合并时两条都要保留，测试列表需同时包含两者。
2. **PDManer 基线失败（非本卡引入）**：`Payment model is deterministic and preserves nine diagram themes`（拆分后该测试仍按 9 个图组断言 payment 单模型，摘要更新后停在图组断言）、`Payment scenario real table field=value rows…`（场景引用已不存在的 `t_payment_channel.adapter_code`）；`generate-card-model.mjs --check` 在基线即 stale。
3. 未部署、未执行 channel-connector DML 到任何非本机库；mor-test 需执行两份 DDL、部署三服务、执行 DML、harness 修改标准 API（`payment.threeDsRequired`）与 PayerMax 流程（卡 3DS-B / 主会话）。
4. PayerMax 通知签名原文、`dynamic3DS` 行为待 UAT 实测（technique D6）；本卡按“sign 头 + 原始 Body + payermaxPublicKey（X.509 Base64）”实现。
5. CURRENT_IMPLEMENTATION 未改（主会话统一）。

## 8. WRITE_API_EXAMPLE 第 11 节核对

- **API 与 Body：** `RefreshSubmissionRequestBody`（class、中文字段注释、Validator）；`ChannelCallbackApi` 路径变量用 `@Pattern`/`@DecimalMax`，Body 为原始字节；`ChannelOperationResponseBody`、`MidCandidateBody` 新字段有注释；`ReceiveChannelCallbackRequest` / `ConfirmPayResultRequest` 由 `validateBasic` 触发约束。
- **Controller / Converter：** `PaymentIntentController.refreshSubmission`、`ChannelCallbackController` 只做协议适配（后者含调用方检查与请求头小写化）；写入口 Processor 位于所属模块 `service/processor`。
- **Processor 查询与校验：** 确认流程查本模块 Repository（尝试归属 Intent、最新动作、工具），不访问 Mapper、不逐表写；入站 Processor 经 `ChannelService`（protocol 发布的能力）与本模块 `ChannelAccountRepository` 校验，写入委托 Service。
- **Context：** `PayContext` 新增 `channelCode`、`midThreeDsMode`（本次请求、不落库）；`ChannelCallbackContext` 持有渠道、账号、定义与待保存 DataObject，`notifyPayment()` / `verified()` 为纯判断。
- **Service：** `ChannelCallbackService.saveCallback` 参与 Processor 事务，不调用其他 Service；`markPublished` 自有短事务；Payment 新增 `findAttempt` 只读。
- **事务位置：** 查单、Vault、Redis、MQ 均在事务外；确认的结果事务、入站保存事务只写本库（见第 1 节表）。
- **Repository：** 新 Repository 一表一个；`updateStatus` 条件 SQL + 行数检查后同步原对象；`saveResult` 版本 CAS 不变。
- **锁：** 确认流程锁 `intent:{id}`，与 pay、终止同一资源 Key；刷新自身无锁；入站无锁（指纹唯一索引保护）。
- **原请求与重放：** 刷新不是新命令，按当前事实返回；终态写入由状态 + 版本 CAS 保证一次；入站以指纹唯一键防重，重复通知应答成功；MQ 以 eventId 防重。
- **Gateway 请求幂等：** 未新增 Idempotency-Key 或命令幂等组件。
- **View：** 未新增 View；刷新复用 EX-I06 的响应与 Processor。
- **公共包与配置：** 复用 Foundation AbstractProcessor / AbstractLockProcessor、compensation-event、messaging；配置集中到 `PaymentExecutionProperties`、`ChannelEventProperties`；无生产 Mock、无伪造成功。
- **验证：** 见第 5 节；未覆盖：internal-auth 下 403 分支、未路由可重试分支、真实 PayerMax 通知、mor-test 部署。

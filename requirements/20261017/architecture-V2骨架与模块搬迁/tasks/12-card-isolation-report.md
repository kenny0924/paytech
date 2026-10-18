# 第 12 步报告 · 卡域独立：card 服务与库、一次性读卡票据、两段创建、支付成功事件化

- 分支：`refactor/step-12-card-isolation`（起点 `refactor-main@c56700bd`）。
- 提交：`14b8db1c` 纯搬迁（单独不可编译）→ `a0f04f5f` 主体实现 → `58055403` 测试、PDManer 与 local-environment → `753a59e5` 文档与表归属计数 → `8d7b486e` consumer 测试样本修正；本报告单独提交。
- 结论：第 5 节 4 项全部真实执行并通过（全 Reactor 按安排由主会话合入后运行）。
  - 任务卡命令 BUILD SUCCESS，1919 例 0 失败；Platform 798 例、channel-connector 75 例 0 失败。
  - card-app、payment-app 以 mock Profile 启动，health / liveness / readiness 均为 UP。
  - R11 与 CVV 键的 grep 检查符合要求。
- **需要先看**：`mor_payment` 中卡 4 表按指示未 DROP（用户待办，见第 9 节）；`t_payment_instrument_token` 新增 4 列，已在本机测试库 ALTER（见第 8 节）；与 05 文档的偏差见第 6 节。

## 1. 改动摘要

1. `paytech/payment/paytech-payment-card` 经 `git mv` 成为 `paytech/card/paytech-card-core`，包根改为 `com.paytech.card`；卡加密的两类纯 Java 测试随 core（R11 只豁免 core / app）。
2. 新建 `paytech-card-api`、`paytech-card-app`（`card-service`，18111 / 19111，无 context path）、`paytech-card-test`；`paytech/pom.xml` 取消 `card` 注释；payment 聚合 POM 删除 card 模块与 R11 过渡 Enforcer。
3. card 聚合 POM 复制 Payment 禁令（含 `*-core` / `*-app`），并加 R11 Enforcer：只有 card-app 可直接依赖 card-core。
4. 一次性票据（05 §2.1）：新增 `t_card_material_ticket` 与签票、凭票读卡两个 Processor；payment 的读卡 Processor、Controller、Converter 删除；`ChannelCardMaterialApi` 迁入 card-api，请求体为 `{ticket}`。
5. PAY 动作在渠道调用前（事务外、Intent 锁内）先做准入校验，再向 card 签票；票据作为标准 API 卡字段句柄。拒签或 card 不可用时，动作以 `CARD_MATERIAL_UNAVAILABLE` 失败，不调用渠道。
6. 两段创建（05 §2.2）：card `POST /card/v1/instruments` 合并原准备与 `applyInstrument`，按（Client、来源提交）幂等。payment 先调 card，再在本地事务插入 Token，并保存工具引用、期限、BIN、指纹、Customer 与保存意愿。
7. 孤立工具回收：新增 `PurgeOrphanCardInstrumentsProcessor` 与 XXL-Job Handler `cardOrphanInstrumentPurge`，回收过期超过宽限期（默认 1 天）、从未签票、也不是已存卡来源的工具。
8. 支付成功事件化（05 §2.3）：`PayActionActor.doInTransaction` 不再写卡表；payment.events 信封升 Schema v2，追加 `instrumentRef`、`consentToSave`、`customerId`。旧 Topic 报文保持 v1 字段，双写逻辑与发布器结构未改。
9. card 新增 `@DomainEventHandler(eventType="PaymentSucceeded", strictOrder=false)`（`card.payment.p0..p3`），在消费事务内标记凭证已验证并 upsert 已存卡；事件目录升 v2。
10. 风控属性（05 §2.4）：`PayRiskCheckActionActor` 改读 Token 行的 `card_bin` / `card_fingerprint`，不再调 card。
11. 已存卡（05 §3）：`StoredCardApi` 迁入 card-api（`/card/v1/stored-cards…`）；acquiring 列表与 consumer 列表 / 删除 / 失效改调 `card-service`；consumer 不再依赖 payment-api。
12. 调用方身份（05 §4）：card 入口按调用方白名单校验（见第 3 节）；`internal-auth.mode` 默认 `"OFF"`，REQUIRED 下的错调用方用例单独覆盖。
13. 装配：card-app 自有 DataSource、Redis（CVV）、Vault Client（Caller `card-service`）、paytech-messaging 消费、补偿与 XXL-Job。payment-app 去掉卡表 Mapper 扫描、Vault Client 与 `payment.card.*`；`grep card:cvv paytech/payment` 为 0。
14. Platform：`PaymentCardMaterialConfiguration` 改指 `card-service`（`ps.integration.execution.card-context-path`，默认空串）；`PaymentCardFunction` 只发 `{ticket}`；测试签发白名单补 card-service。
15. arch-rules：删除 transitionalPlacements 机制（生成器、`TableOwnership`、R11 豁免、对应测试与 fixture）。`t_card_material_ticket` 以 stepAddedTables 登记；`t_consumed_aggregate_version` 并入每库基础表。consumer 的 27 条 R3 过渡偏差删除。
16. 库与模型：`card-schema.sql`、`card.pdma.json`（8 表，生成器 `generate-card-model.mjs --check` 通过）；payment Schema / 模型去掉卡 4 表并给 Token 加 4 列；两份测试 DDL 同步。
17. local-environment：初始化脚本、`.env.example`、README、`verify-local-environment.mjs` 增加 `mor_card` / `card_app`（与第 03、06、07 步相同的“变量存在才创建”规则）。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令都先 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并带 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`。`MOR_RABBITMQ_USER/PASSWORD/VHOST` 与 `PAYTECH_REDIS_PASSWORD` 由 scratchpad 脚本从 `.env.local` 导出，没有打印。没有 up / down / restart 任何容器。

### 5.1 测试

任务卡命令（`-fae` 只为一次跑完全部模块）：

```bash
cd paytech && ./mvnw -B -o -fae verify -pl card/paytech-card-test,payment/paytech-payment-test,acquiring/paytech-acquiring-test,consumer/paytech-consumer-test -am
```

结果为 BUILD SUCCESS，用时 5:00，**1919 例 0 失败 0 错误 0 跳过**：

| 模块 | 执行数 | 说明 |
|---|---:|---|
| Foundation 16 个模块与 vault-client | 930 | messaging 55 例，含事件目录 v2 断言 |
| paytech-arch-rules | 56 | 第 05 步 57 例，删除过渡豁免 1 例；过渡归属用例改为卡表归 card |
| paytech-acquiring-test | 801 | |
| paytech-consumer-test | 21 | |
| paytech-payment-test | 62 | 起点 72 例，卡加密纯 Java 11 例移到 card-core，另新增 1 例 |
| paytech-card-core | 11 | `CardCipherTest` 5、`PaymentCardPropertiesTest` 6 |
| paytech-card-test | 38 | 37 个方法，见下表 |

card-test 明细（新增 37 个方法、38 例，要求不少于 20 例）：

| 类 | 执行数 | 覆盖 |
|---|---:|---|
| `CardInstrumentHttpTest` | 12 | 创建与加密存储、短卡号 BIN、同卡复用、凭证按有效期与持卡人、并发首用、同一 `sourceSubmissionId` 幂等且 CVV 不变、同提交换卡 / 换 Customer / 改意愿 409、Luhn 与过期 400（参数化 2 例）、Vault 不可用 503、错调用方 404、卡值不进日志 |
| `CardMaterialTicketTest` | 5 | 签发（只存摘要、≤90 秒、不领取 CVV）、CVV 已领取拒签、工具过期拒签、工具不存在 404 与 TTL 越界 422、错调用方 404 |
| `ChannelCardMaterialRevealTest` | 6 | 成功一次（CVV 删除、Vault 在事务外）、二次使用拒绝、过期拒绝、purpose 不符拒绝、错调用方拒绝且无卡数据且票据不被消耗、票据不存在 404 |
| `StoredCardHttpTest` | 6 | 列出、删除（跨 Customer 404、重复 204）、失效（1 / 0）、调用方规则、已存卡加新 CVV 支付、已存卡不可用 400 |
| `PaymentSucceededConsumeTest` | 4 | 重复投递只生成一张已存卡、未同意只验证凭证、同卡新凭证更新已存卡、v1 与非卡事件跳过 |
| `OrphanInstrumentPurgeTest` | 2 | 回收孤立工具并保留签过票、作为已存卡来源、仍在宽限期内的工具；无可回收工具 |
| `CardInternalAuthRequiredTest` | 2 | REQUIRED：只有 Platform Token 能读卡；缺 Token 401、Header 与 Token 不一致 400、非 GENERAL Purpose 404、被拒请求不消耗票据 |
| `ArchitectureAutoTest` | 1 | R1–R12 零违规（无登记偏差） |

`VaultTransactionObserver.assertOutsideTransactions` 两侧各一份：card 在每例 AfterEach 断言 Vault 调用不在事务内；payment 不再访问 Vault，同一观察点覆盖 card、channel-connector 与 Risk 的全部出站调用。

payment 6 类保持入口不变（`/payment-instrument/v1/card-tokens`、`/payment-execution/v1/intents/{id}/pay` 等）：

| 类 | 方法 / 执行（起点 → 现在） | 改写要点 |
|---|---|---|
| `PayCardChannelTest` | 7/7 → 8/8 | 渠道输入的 card.token 为 card 签发的票据，而不是支付工具 Token；票据绑定当前 PAY 动作。`test_revealRules` 改为票据二次使用被拒；新增 `test_ticketRefusedNotSent`：拒签时 PAY 失败，渠道未被调用 |
| `PayRiskEvaluationTest` | 9/20 → 9/20 | 未改；BIN 与指纹来自 Token 行 |
| `PaymentSucceededPublishTest` | 4/4 → 4/4 | 断言 v2 字段（`instrumentRef`、`consentToSave`、`customerId`），两份报文都不含票据 |
| `GetSubmissionTest` | 7/7 → 7/7 | 未改 |
| `CardStorageTest` | 13/14 → 13/14 | 改为断言 card-api 调用记录与 `t_payment_instrument_token` |
| `StoredCardTest` | 8/8 → 8/8 | 改为断言 Customer 与保存意愿的转交、v2 事件，以及旧已存卡入口在 payment 返回 404 |

`CardApiMocker` 以 MockHttpServer 的 `card-service` 承载工具创建、票据与读卡；`PlatformMocker.revealCard` 回调该服务，票据只能用一次。

其他验证：

- **acquiring 偶发失败**：任务卡命令第一次运行时，`GetRefundableAmountTest`、`ListOrderRefundsTest` 各有 1 例因测试固件 Snowflake 主键冲突（`DuplicateKeyException`）失败。单独重跑这两类 131 例全部通过；之后完整命令也全部通过。这两类不涉及卡域，属于既有的偶发问题。
- **Platform**：运行前确认没有其他 surefire JVM。命令为 `./mvnw -B -o verify -pl platform-support/paytech-platform-support-test -am`，BUILD SUCCESS，用时 7:09，**798 例 0 失败**，与第 02a、05 步的 798 例相同。
- **channel-connector**：本步修改了它的架构测试。`-pl channel-connector/paytech-channel-connector-test -am` 结果为 75 例 0 失败。
- **arch-rules reactor**：`-pl tools/arch-rules,tools/arch-rules-reactor-test,foundation/paytech-messaging` 结果为 56 / 3 / 55 例，0 失败。`node tools/arch-rules/scripts/generate-table-ownership.mjs --check` 输出 `current (265 physical tables + 56 retained + 1 step-added)`。
- **全 Reactor**：按安排由主会话合入后运行。

### 5.2 两个 app 以 mock Profile 启动

- 启动方式：`java -jar <exec.jar> --spring.profiles.active=mock --spring.config.additional-location=file:<test>/application-mock.yml --test.mock-http.base-url=http://127.0.0.1:9`，再加端口参数。card 使用 28111 / 29111，payment 使用 28103 / 29103。
- 结果：`Started CardApplication in 6.6 s`，`Started PaymentApplication in 7.3 s`，两份日志中 ERROR 均为 0 条。
- `29111` 与 `29103` 的 `/actuator/health`、`/liveness`、`/readiness` 都返回 `200 {"status":"UP"}`。两个进程验证后均已停止。

### 5.3 R11 零违规

- `grep -rl "t_payment_card\|t_payment_stored_card" paytech/ --include='*.xml'`：命中 `card/paytech-card-core` 的 4 个 Mapper，以及 arch-rules 自身的 3 个 R11 测试 fixture（有意构造的违规样本）。
- `grep -rl "com.paytech.card.core.crypto" paytech/`：命中 card 模块源码与 README / POM 注释，以及 arch-rules 的规则常量 `CardDataIsolationRule`。
- 所有服务的 `ArchitectureAutoTest` 都执行了 R11；card、payment、consumer 均为零违规。

### 5.4 CVV 键

`grep -r "card:cvv" paytech/payment` 的结果为 **0 行**。

### Node 与脚本（与起点对照）

- `npm --prefix requirements-site test`：137 项，127 通过，10 失败。起点 `c56700bd` 的 `git archive` 用同一份 node_modules 运行，结果相同，失败名逐个一致。
- PDManer：`node --test` 运行 payment-model、scenario-doc、channel-query、execution-simplification、canonical-paths、oe-client-model 与新增的 card-model，共 39 项，36 通过，3 失败。这 3 个失败都在起点的失败集合中（起点为 37 项，7 失败），没有新增失败。
- `generate-card-model.mjs --check` 与 `generate-channel-connector-model.mjs --check` 均为 up to date。`generate-payment-model.mjs --check` 报 stale，与起点相同（第 05 步偏差第 8 项）。
- `node scripts/verify-local-environment.mjs`：输出与改动前逐行相同，只剩 4 行既有的 Gateway 文档引用失败。

## 3. card-api 合同清单

服务发现名为 `card-service`，无 context path。所有入口都带 `X-Caller-Service` Header；携带已验证内部 Token 时，以 Token 的调用方为准。调用方不符一律返回 404，不返回卡数据，也不暴露资源是否存在。ID 在 JSON 中一律为十进制字符串。

| 合同 | 方法与路径 | 允许调用方 | 请求 → 响应 / 错误 |
|---|---|---|---|
| `CardInstrumentApi` | `createInstrument`，`POST /card/v1/instruments` | payment-service | `{clientId, customerId?, sourceSubmissionId, card{pan,expiryMonth,expiryYear,cardholderName,cvv} \| storedCard{storedCardToken,cvv}, consentToSave, expiresAt(上限)}` → `{instrumentRef, maskedPan, expiresAt, riskAttributes{bin, fingerprint}}`。同 Client 同提交重放返回原工具；换卡、换 Customer 或改意愿 409；卡号校验失败、卡过期、已存卡不可用、卡来源不是恰好一种 400；Vault 或 Redis 不可用 503 |
| `CardMaterialTicketApi` | `issueTicket`，`POST /card/v1/material-tickets` | payment-service | `{instrumentRef, paymentAttemptId, paymentActionId, purpose: CHANNEL_EXECUTION, ttlSeconds 1–90}` → `{ticket(43 位 Base64URL), expiresAt}`。工具不存在 404；工具过期或 CVV 已领取 400；越界 422 |
| `ChannelCardMaterialApi` | `reveal`，`POST /card/v1/channel-card-materials/reveal` | platform-support-service | `{ticket}` → `{pan, expiryMonth, expiryYear, cardholderName, cvv}`。票据不存在 404；已用、过期、purpose 不符、CVV 已领取 400 |
| `StoredCardApi` | `GET /card/v1/stored-cards?clientId&customerId`；`DELETE /card/v1/stored-cards/{token}?clientId&customerId`；`POST /card/v1/customers/{customerId}/stored-cards/invalidation` | 列出：acquiring-service、consumer-service；删除与失效：consumer-service | DTO 与原 Payment 合同相同，只在每个方法上增加调用方 Header |

- 稳定代码：`CardMaterialPurpose`（`CHANNEL_EXECUTION`）。常量类 `CardApiConstants` 定义服务名、Header 名与 4 个调用方名。
- api 模块只依赖 common-core、spring-web、jakarta-validation 与 provided 范围的 Lombok，没有 Jackson 注解，R2 零偏差。

## 4. 三处事务改造前后对照

| 位置 | 改造前 | 改造后 |
|---|---|---|
| 读卡（05 §2.1） | pay 持 Intent 锁调 Platform；Platform 回调 payment `/payment-card/v1/channel-card-materials/reveal`；payment 校验 Token / Attempt / Action 后在进程内调 card 领取材料（同步重入） | pay 在 Intent 锁内、事务外先做准入校验（Attempt 处理中、当前动作为执行中的 PAY 且就是本动作、Token 未撤销），再调 card 签票，然后调渠道。Platform 凭票据直达 card；card 以一条条件 UPDATE 标记 `used_at`（并发只有一方成功），之后在事务外经 Vault 取回 DEK 并 GETDEL CVV。payment 不再提供读卡接口 |
| 申请卡工具（05 §2.2） | 事务外准备卡与凭证（自动提交）、Vault 与 Redis；一个事务内同时插入 `t_payment_card_instrument`（card）与 `t_payment_instrument_token`（payment） | 第一段：card 一个命令完成准备并单条插入工具，`uk(client_id, source_submission_id)` 幂等。第二段：payment 本地事务按 Intent 状态条件插入 Token。第二段失败留下的孤立工具从未签票，由回收任务处理 |
| 支付成功（05 §2.3） | `PayActionActor.doInTransaction` 在成功事务内调 `CardService.paymentSucceeded`（凭证 `verified_time`、已存卡 upsert），同事务登记补偿事件 | 成功事务只写 payment 自己的表并登记补偿事件。Handler 重建事件时读取 Token 行，向 payment.events 发布 v2；card 在消费事务内（`t_consumed_event` 与业务表同提交）标记凭证并 upsert 已存卡。已存卡变为最终一致 |

## 5. Vault Caller 与 Platform 白名单变更

**Vault（Vault 侧访问规则须由主会话 / 用户登记，本步未改 Vault 配置）**

| 用途 | 旧 caller | 新 caller | 数据类型 / Purpose |
|---|---|---|---|
| 卡 DEK 托管与读取 | `payment-service` | `card-service` | `PAYMENT_CARD_DEK`：Protect `CARD_STORAGE`、Reveal `CARD_USE` |

payment 不再访问 Vault，可以撤销 `payment-service` 在 `PAYMENT_CARD_DEK` 上的规则。

**Platform 测试配置（`platform-support-test/.../application-mock.yml`）**

- `internal-token.issuer.callers.platform-support-service.targets` 增加 `card-service`，用于凭票据读卡。
- 新增 `internal-token.issuer.callers.payment-service: {targets: [card-service], purposes: [GENERAL]}`，用于 payment 访问 card。
- `issuer-test.targets` 增加 `card-service`。
- simple discovery 增加 `card-service`；`ps.integration.execution.payment-context-path` 改名为 `card-context-path`。

Platform 主 `application.yml` 中的 `callers: {}` 没有改。生产或测试环境启用签发端时，需要同样登记上面两条，以及 acquiring-service 与 consumer-service 访问 card-service 的条目。

## 6. 与 05 文档 / 任务卡的偏差

1. **创建响应不重复 BIN 与指纹。** 任务卡写的是 `{instrumentRef, bin, fingerprint, maskedPan, expiresAt, riskAttributes{bin, fingerprint}}`。实现只在 `riskAttributes` 中返回一次，避免同值两处。
2. **没有提供 `GET /card/v1/instruments/{ref}/risk-attributes`。** 05 §3 把它列入合同，但 §2.4 推荐、任务卡也要求在创建时返回并存入 Token 行。这样该接口没有调用方，因此未开放。
3. **`t_payment_card_instrument` 增加两列。** `source_submission_id`（与 `client_id` 组成唯一键，实现“同提交幂等”）与 `expires_at`（实现“工具存在未过期”与回收），另加 `idx_card_instrument_expires`。这不是逐字复制的表结构，`mor_card` 是新库，不需要迁移。
4. **`t_payment_instrument_token` 增加 4 列。** `customer_id`、`consent_to_save`、`card_bin`、`card_fingerprint`。前两列供 v2 事件，后两列供风控。卡指纹从此作为受限值落在 `mor_payment`。
5. **v2 只用于 payment.events。** 旧 `topic_payment_result` 报文保持 v1 字段与 `schemaVersion: 1`，以遵守“不改旧 Topic 双写逻辑与发布器结构”，也避免现有严格消费者遇到未知字段。`PaymentSucceededHandler` 为重建 v2 字段增加了 Token 仓储依赖。
6. **Purpose 校验。** api 模块受 R2 限制不能依赖 internal-auth，typed 合同无法在方法上声明 `@InternalPurpose`，出站只会发 `GENERAL`。因此 card 对已验证 Token 只接受 `GENERAL`，其他 Purpose 视为调用方不符（404）。专用 Purpose 需要 Foundation 支持按客户端配置 Purpose。
7. **调用方 Header。** `StoredCardApi` 原来没有调用方参数，本步在各方法上增加 `@RequestHeader("X-Caller-Service")`，acquiring 与 consumer 客户端以常量传入。
8. **Platform 函数签名不变。** `PAYMENT_CARD` 初始化函数仍声明 `paymentInstrumentToken` 与 `paymentAttemptId` 两个参数，已有流程定义无需修改：前者现在承载票据，后者不再发送。
9. **“现有到期清理任务”并不存在。** 新增回收 Processor 与 XXL-Job Handler `cardOrphanInstrumentPurge`，需要在 XXL Admin 登记任务。判定只用 card 自己的数据：过期超过宽限期、从未签票、不是已存卡来源。卡与凭证不删除。
10. **只在 PAY 签票。** AUTHORIZE Actor 仍是“尚未接入”的占位，没有渠道调用，因此没有接入票据。
11. **拒签与 card 不可用一并按未发出处理。** card 拒签、5xx 或超时时，PAY 都以 `CARD_MATERIAL_UNAVAILABLE` 明确失败（NOT_SENT 语义）。此时渠道请求尚未发出，资金状态确定；签票本身不消耗 CVV。
12. **标记已用不再另开事务。** 05 写的是“本地事务标记 used_at”。实现为单条条件 UPDATE（`used_at IS NULL AND expires_at > now`），没有另开 TransactionTemplate；之后的 Vault / Redis 访问都在事务外。
13. **过渡登记机制整体删除。** 包括生成器段、`TableOwnership` 字段与方法、R11 的过渡豁免和相应测试。之后若再有暂居需求需要重新设计。新增 `stepAddedTables`，在 design/03 §4.2 计数之外登记 `t_card_material_ticket`。
14. **配置前缀与环境变量改名。** `payment.card.fingerprint-key` 改为 `card.fingerprint-key`，环境变量改为 `CARD_FINGERPRINT_KEY`，取值必须原样迁移（见第 9 节）。
15. **测试位置与数量。** 卡加密纯 Java 测试放在 card-core 而不是 card-test，因为 R11 不豁免 card-test 引用加密包。payment 6 类从 48 个方法变为 49 个，新增 1 个。
16. **acquiring 与 consumer 的端口类名保留 `PaymentStoredCardClient`，** 以减少与第 08 步的冲突；实现已指向 card-service。

## 7. 待确认项

1. **v2 字段的取值来源。** `customerId` 与 `consentToSave` 由 payment 从申请请求保存。card 消费时会与自己的工具记录比对，不一致就抛错（重投后进入 DLQ），不按任一方猜测存卡。是否改为只以 card 记录为准，需要确认。
2. **第 08 步合并时的冲突点。**
   - `PaymentSucceededHandler` 在本步增加了 Token 仓储依赖与 v2 字段。
   - acquiring 的 payment.events 消费者必须接受 `schemaVersion=2` 并忽略新增字段（目录已注明）。
   - card 的 `card.payment.hash` 已绑定 `payment.events`；本地共享 Broker 上该交换机从此有路由。第 08 步把 payment 改回 `unroutable-policy: FAIL` 的前提至此已满足一部分。
3. **共享 Broker 上的消息堆积。** card-test 运行后，`card.payment.p0..p3` 是 durable 队列；payment-test 运行时发布的事件会堆积在其中，直到下一次 card-test 在每例前清空，或部署的 card-service 消费掉。无法识别的工具引用会抛错，最终进入 `card.payment.dlq`。
4. **在 payment 库保存卡指纹是否可接受。** 卡指纹是受限值，依据是 05 §2.4 的推荐写法；它只进入内部风控请求，不进日志或 MQ：日志测试已断言不含指纹，v2 的固定字段集合中也没有指纹。
5. **回收参数与调度。** 宽限期默认 1 天、单批 200 条，需要确认；XXL-Job 的任务登记与调度周期也需要确定。
6. **R11 的 Maven 层规则是否加严。** card-test 经 card-app 传递获得 card-core 类，Enforcer 只检查直接依赖，源码与 Mapper 的引用由 ArchitectureAutoTest 检查。是否需要更严格的 Maven 层规则，需要确认。

## 8. 本机数据库操作（root 经 `docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql -uroot`，密码取自 `.env.local` 且未打印）

1. **2026-10-18T02:16:31Z**：执行 `mysql -uroot --default-character-set=utf8mb4 < paytech/card/paytech-card-test/src/test/resources/init/ddl.sql`。
   - 新建 `mor_card` 与 `card_app`。本机 `.env.local` 没有 `MOR_CARD_DB_PASSWORD`，因此账号使用仓库默认密码 `local-card-change-me-2026`，与 `application-mock.yml` 的默认值一致。
   - 授权 `SELECT, INSERT, UPDATE, DELETE`，建 8 张表。
   - 核对结果：`information_schema` 中有 8 张表；`SHOW GRANTS` 与 DDL 一致。
2. **2026-10-18T02:16:50Z**：对 `mor_payment.t_payment_instrument_token` 执行加列 ALTER（执行前该表 0 行），语句保存在 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/d1bc4b55-586b-45ae-abbc-d503d0fde214/scratchpad/step12/alter-payment-instrument-token.sql`：

   ```sql
   ALTER TABLE `t_payment_instrument_token`
     ADD COLUMN `customer_id` BIGINT NULL COMMENT '…' AFTER `instrument_ref`,
     ADD COLUMN `consent_to_save` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '…' AFTER `customer_id`,
     ADD COLUMN `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '…' AFTER `consent_to_save`,
     ADD COLUMN `card_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '…' AFTER `card_bin`;
   ```

   - 注释全文与 `payment-schema.sql` 相同。
   - 回滚：`ALTER TABLE t_payment_instrument_token DROP COLUMN card_fingerprint, DROP COLUMN card_bin, DROP COLUMN consent_to_save, DROP COLUMN customer_id;`。
3. **没有执行**任何 DROP，也没有修改 `mor_payment` 的卡 4 表。权限系统没有拦截上述语句。

## 9. 用户待办

1. **`mor_payment` 的卡 4 表。** `t_payment_card`、`t_payment_card_credential`、`t_payment_card_instrument`、`t_payment_stored_card` 仍留在本机 `mor_payment`。payment 不再扫描它们，测试清理器也不触碰。确认无保留价值后可以 DROP；若其中有需要保留的数据，迁移到 `mor_card` 需要另行评审 SQL：`t_payment_card_instrument` 新增的 `source_submission_id` 与 `expires_at` 都是 NOT NULL，需要先确定回填规则。
2. **`compose.yaml` 透传变量。** `mysql.environment` 需要透传 `MOR_CARD_DB_PASSWORD`，新建容器时才会创建 `mor_card`；本步按惯例没有改 compose。若在 `.env.local` 设置了别的密码，需要同步修改 `card_app` 的密码。
3. **Vault 访问规则。** 为 `card-service` 登记 `PAYMENT_CARD_DEK` 的 Protect `CARD_STORAGE` 与 Reveal `CARD_USE`，并可撤销 `payment-service` 的同类规则。
4. **卡指纹密钥迁移。** 部署时把 `PAYMENT_CARD_FINGERPRINT_KEY` 的值原样作为 card-service 的 `CARD_FINGERPRINT_KEY`；更换会使已存的卡与凭证无法再匹配。
5. **其他部署项。**
   - 实例清单与部署为 card-service 分配 Snowflake 坐标（`CARD_SNOWFLAKE_REGION_ID` / `WORKER_ID`）与 RabbitMQ 账号（`CARD_RABBITMQ_*`）。
   - 在 XXL Admin 登记 `cardOrphanInstrumentPurge` 任务。
   - Nacos 上传 `paytech/card/config/nacos/card-service.yaml`。
6. **Platform 生产签发白名单**：见第 5 节。
7. **仓库级文档的旧链接**（`agent-docs/**`、需求文档中的 `paytech-payment-card` 与 `/payment-card/v1`）按 09 §6 留到第 13 步处理。

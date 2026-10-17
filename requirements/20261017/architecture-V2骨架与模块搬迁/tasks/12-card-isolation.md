# 任务卡 12 · 卡域独立：`card` 服务与库、一次性读卡票据、两段创建、支付成功事件化

- 分支：`refactor/step-12-card-isolation`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step12`；起点：`refactor-main`（05、08 合入后，主会话填写；可与 11a / 11b 并行）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 12 步；设计 [05-payment-card-isolation.md](../design/05-payment-card-isolation.md)；决定 D10、D26、D36（修订）、C2。
- **这是阶段 0 两处业务逻辑重做之一**：读卡回调重入改为一次性票据、申请卡工具改两段、支付成功写卡表改事件。逐条按 05 文档实现，任何偏差先在报告写明再做。CVV 不落库不哈希；原始卡号只经 `card-tokens` 入口到 payment 再到 card。

## 1. 输入文档（先完整读取）

- [design/05-payment-card-isolation.md](../design/05-payment-card-isolation.md) 全文。
- [design/08-messaging-module.md](../design/08-messaging-module.md)、[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) §2.3（card 新增 ≥ 20 例）、§3.1 payment / card 行；[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) card 行与 Payment 表归属。
- [analysis/payment.md](../analysis/payment.md) §4、§5、§9（两处事务、回调重入、风控属性读取）。
- 第 05 步报告（payment-core / channel-connector-api 现状、`PlatformMocker.revealCard` 回调目标、Vault Caller 名）、第 02a 步报告（Platform `PaymentCardMaterialConfiguration` 与读卡客户端位置、签发白名单含 `payment-service → card-service` 等目标需补）、第 02d 步报告（Vault Client 在存在 Token 来源时附加 Token）、第 08 步报告（消费者装配方式）。
- 现有 `paytech/payment/paytech-payment-card`、`ApplyCardInstrumentProcessor`、`PrepareCardInstrumentProcessor`、`RevealChannelCardMaterialProcessor`、`ClaimChannelCardMaterialProcessor`、`PayActionActor.doInTransaction`、`PayRiskCheckActionActor`、`CardService`、`StoredCardController`、`ChannelCardMaterialController`、Platform 的读卡客户端与 `PaymentCardMaterialConfiguration`。
- 仓库规范：`AGENTS.md`（Card 边界、CVV 规则）、`agent-docs/VAULT_SERVICE_ARCHITECTURE.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`requirements/20260912/payment-自建CardVault/requirement.md`、`requirements/20260913/payment-Execution设计/instrument-token-amendment.md`。

## 2. 模块与库

- `paytech/payment/paytech-payment-card` → `paytech/card/paytech-card-core`；新建 `paytech-card-api`、`paytech-card-app`（`card-service`，18111 / 19111，`mor_card`，账号 `card_app`）、`paytech-card-test`；payment 聚合 POM 删除 card 模块与对它的放行；`paytech/pom.xml` 取消 `card` 注释。
- 卡表 `t_payment_card`、`t_payment_card_credential`、`t_payment_card_instrument`、`t_payment_stored_card` 的 Schema / PDManer / 测试 DDL 从 payment 切到 card（`card.pdma.json`、`card-schema.sql`）；card 库新增 `t_card_material_ticket`、`t_consumed_event`、`t_consumed_aggregate_version`、`t_compensation_event`；`table-ownership.yaml` 重新生成（card 表归 `mor_card`，去掉第 05 步的过渡注记）。
- Redis：CVV 键 `card:cvv:<instrumentId>` 随 card；Vault：card 的 Caller 为 `card-service`（`PAYMENT_CARD_DEK` 数据类型；Vault 访问规则需同步的 Caller 变更写进报告，Vault 侧配置不在本步改）。

## 3. 逻辑改动清单（逐条引用 05 文档）

1. **一次性票据（05 §2.1）**：`paytech-card-api` 新增 `CardMaterialTicketApi` `POST /card/v1/material-tickets` `{instrumentRef, paymentAttemptId, paymentActionId, purpose: CHANNEL_EXECUTION, ttlSeconds ≤ 90}` → `{ticket, expiresAt}`；card 校验工具存在未过期、CVV 仍可领取，签发随机 256 位票据，存 `t_card_material_ticket`（`ticket_hash`、`instrument_ref`、`attempt_id`、`action_id`、`purpose`、`expires_at`、`used_at`、`caller`）。payment 在发起渠道调用前（事务外、Intent 锁内）先做原有准入校验（Attempt PROCESSING、当前动作 PAY / AUTHORIZE），再调 card 签票，把 `ticket` 作为卡字段句柄交给 Platform 标准 API（替代支付工具 Token）。`ChannelCardMaterialApi` 从 `paytech-payment-api` 移到 `paytech-card-api`，`POST /card/v1/channel-card-materials/reveal` `{ticket}`，card 本地事务标记 `used_at`（已用 / 过期 / purpose 不符 → 4xx 且无卡数据），再经 Vault 取 DEK、Redis `GETDEL` CVV 返回材料；票据只能用一次，CVV 已消耗时拒绝签新票，payment 以 NOT_SENT 结束动作。payment 的 `RevealChannelCardMaterialProcessor` 与 `ChannelCardMaterialController` 删除；Platform 的 `PaymentCardMaterialConfiguration` 改指 `card-service` 并改请求体；Platform 测试签发白名单补 `platform-support-service → card-service`、`payment-service → card-service`。
2. **两段创建（05 §2.2）**：`paytech-card-api` 新增 `CardInstrumentApi` `POST /card/v1/instruments` `{clientId, customerId, cardInput..., consentToSave, sourceSubmissionId}` → `{instrumentRef, bin, fingerprint, maskedPan, expiresAt, riskAttributes{bin, fingerprint}}`（现 `PrepareCardInstrumentProcessor` 事务外准备 + `applyInstrument` 合并为 card 一个命令；同 `sourceSubmissionId` 幂等返回同工具）；payment 的 `ApplyCardInstrumentProcessor` 改为先调 card，再本地事务按 Intent 状态插入 `t_payment_instrument_token`（引用 `instrumentRef`，并把 `riskAttributes` 存入 token 行，05 §2.4 推荐）；第 2 步失败留下的孤立工具由 card 现有到期清理任务回收。
3. **支付成功事件化（05 §2.3）**：`PayActionActor.doInTransaction` 不再调 `CardService.paymentSucceeded`；`PaymentSucceeded` Payload 升 `schemaVersion=2`，增加 `instrumentRef`、`consentToSave`、`customerId`；card 新增 `@DomainEventHandler(eventType="PaymentSucceeded", strictOrder=false)` 消费者（`card.payment.p0..p3`），按 `paymentAttemptId` 幂等地标记凭证 `verified_time` 并 upsert `t_payment_stored_card`；acquiring 消费者忽略新增字段（核对）。事件目录更新。
4. **风控属性（05 §2.4）**：`PayRiskCheckActionActor` 改读 `t_payment_instrument_token` 中保存的 `riskAttributes`，不再调 card。
5. **已存卡（05 §3）**：`StoredCardApi` 从 `paytech-payment-api` 迁到 `paytech-card-api`（`/card/v1/stored-cards…`）；acquiring 的 `PaymentStoredCardClient` 改调 `card-service`（consumer 的已存卡列出 / 删除 / 失效也改调 card）；`PaymentInstrumentApi.card-tokens` 保留为 acquiring 入口。
6. **调用方身份（05 §4）**：card 三类入口按 D26 `caller` / `purpose` 校验（`material-tickets`、`instruments` 来自 `payment-service`；`channel-card-materials/reveal` 来自 `platform-support-service`；`stored-cards` 来自 `acquiring-service` / `consumer-service`）；`internal-auth.mode` 默认 `"OFF"`，`mock` 测试在 `REQUIRED` 下覆盖错 caller 被拒。
7. **装配**：card-app 自己的 DataSource、Redis、Vault Client（Caller `card-service`）、`paytech-messaging`（消费）、补偿；payment-app 去掉卡表 Mapper 扫描与 Redis CVV 键；Enforcer：card 聚合 POM 复制 Payment 禁令并加 R11 约束（只有 card-core / card-app 可触卡表与 `com.paytech.card.core.crypto`）。
8. **本地库**：新建 `mor_card` / `card_app`（init 脚本、`.env.example`、手工建库建账号并应用测试 DDL，命令记报告）；payment 测试 DDL 去掉卡表。

## 4. 测试要求（05 §5、10 §2.3）

- payment 6 类 48 例保持入口不变：`applyCard` 改为 `CardApiMocker`（card-api DTO：工具创建、票据签发），`PlatformMocker.revealCard` 回调目标改为 MockHttpServer 的 `card-service`，并断言票据只用一次；`PaymentSucceededPublishTest` 断言 v2 字段。
- card 新增 ≥ 20 例：`CardInstrumentHttpTest`（创建、同 `sourceSubmissionId` 幂等、CVV 暂存）、`CardMaterialTicketTest`（签发、CVV 已领取拒签、过期）、`ChannelCardMaterialRevealTest`（成功一次、二次使用拒绝、过期拒绝、purpose 不符拒绝、错 caller 拒绝且无卡数据）、`StoredCardHttpTest`（列出 / 删除 / 失效）、`PaymentSucceededConsumeTest`（重复投递只生成一张已存卡）、孤立工具清理、`VaultTransactionObserver.assertOutsideTransactions` 两侧各一份、`ArchitectureAutoTest`。
- acquiring / consumer 的已存卡测试改 Mock 目标为 `card-service`。
- 全 Reactor `verify` 全绿。禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单

1. `cd paytech && ./mvnw -B verify -pl card/paytech-card-test,payment/paytech-payment-test,acquiring/paytech-acquiring-test,consumer/paytech-consumer-test -am` 全绿；全 Reactor 全绿。
2. card-app、payment-app 以 `mock` Profile 启动 health UP。
3. R11 零违规：`grep -rl "t_payment_card\|t_payment_stored_card" paytech/ --include=*.xml` 只在 card；`grep -rl "com.paytech.card.core.crypto" paytech/` 只在 card。
4. `grep -r "card:cvv" paytech/payment` 为零。

## 6. 禁止事项

- CVV 不落库、不哈希、不进日志 / MQ / 补偿 Payload；原始卡号不进 channel-connector 合同、非卡 Vault、MQ、日志。
- 不改支付状态机；不改 Vault 数据模型；不执行业务库 SQL（测试库除外）。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-12): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/12-card-isolation-report.md`：改动摘要 ≤ 30 行、第 5 节逐项、card-api 合同清单、三处事务改造前后对照、Vault Caller 变更、Platform 白名单变更、与 05 文档偏差、待确认项。
3. 最终回复两行。

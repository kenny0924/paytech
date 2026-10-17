# 05 · 卡域独立方案（S3）

依据 D10（独立进程、独立库、独立密钥三条件在本方案中全部成立）、D26、D36（修订）。现状见 [analysis/payment.md](../analysis/payment.md) 第 4、5、9 节。

## 1. 目标

`card` 成为独立服务与库（`mor_card`），只有它持有 `t_payment_card*`、`t_payment_stored_card`、DEK Vault Token 与 CVV Redis。`payment` 不再直接调用 `CardService`，不再提供读卡接口；Platform 凭 card 签发的一次性票据直接向 card 读卡。

## 2. 三处耦合的改法

### 2.1 读卡回调重入 → 一次性票据

现状：pay 持 Intent 锁调 Platform，Platform 回调 `POST /payment/payment-card/v1/channel-card-materials/reveal`，payment 校验 Token / Attempt / Action 后调 card 领取材料。

方案：

1. payment 在发起渠道调用**之前**（已在事务外）调用 `card-api`：`POST /card/v1/material-tickets` `{instrumentRef, paymentAttemptId, paymentActionId, purpose: CHANNEL_EXECUTION, ttlSeconds: 90}` → card 校验工具存在且未过期、CVV 仍可领取，签发票据 `ticket`（随机 256 位，存 `t_card_material_ticket`：`ticket_hash`、`instrument_ref`、`attempt_id`、`action_id`、`purpose`、`expires_at`、`used_at`、`caller`），返回 `ticket` 与 `expiresAt`。
2. payment 把 `ticket` 作为标准 API 的卡字段句柄（替代现在的支付工具 Token）交给 Platform。
3. Platform 执行支付类标准 API 时调用 **card** 的 `POST /card/v1/channel-card-materials/reveal` `{ticket}`（服务发现名 `card-service`，不再是 payment），携带 D26 内部签名 Token。card 校验票据未用、未过期、`purpose` 匹配，本地事务标记 `used_at`，再经 Vault 取 DEK、Redis `GETDEL` CVV，返回材料。票据只能用一次；失败后 payment 若重试渠道调用必须重新申请票据，而 CVV 已被消耗时 card 拒绝签发，payment 以 NOT_SENT 结束动作（与现状"CVV 不可重取"一致）。
4. payment 原来的准入校验（Attempt PROCESSING、当前动作为 PAY / AUTHORIZE）移到**签发票据时**由 payment 自己在调用 card 前完成；card 只校验票据。

接口变化：`ChannelCardMaterialApi` 从 `paytech-payment-api` 移到 `paytech-card-api`，请求体由 `{paymentInstrumentToken, paymentAttemptId, purpose}` 改为 `{ticket}`；Platform 的 `PaymentCardMaterialConfiguration` 改为指向 `card` 服务。

### 2.2 申请卡工具两段创建

现状：`ApplyCardInstrumentProcessor.saveContext` 在一个事务内插入 `t_payment_card_instrument`（card）与 `t_payment_instrument_token`（payment）。

方案：

1. payment 先调 `card-api` `POST /card/v1/instruments` `{clientId, customerId, cardInput..., consentToSave}` → card 在自己的事务内保存卡、凭证、工具（现 `PrepareCardInstrumentProcessor` 的事务外准备与 `applyInstrument` 合并为 card 的一个命令），Protect DEK、暂存 CVV，返回 `instrumentRef`、`bin`、`fingerprint`、`maskedPan`、`expiresAt`。
2. payment 本地事务按 Intent 状态插入 `t_payment_instrument_token`，引用 `instrumentRef`。
3. 若第 2 步失败，card 侧多出一个未被引用的工具；card 已有的工具到期清理任务回收（现状对孤立 DEK 的处理相同）。两段之间以 `instrumentRef` 幂等：payment 重试时携带同一 `sourceSubmissionId`，card 对同一提交返回同一工具。

### 2.3 支付成功写卡表 → 事件

现状：`PayActionActor.doInTransaction` 内调用 `CardService.paymentSucceeded`（凭证 `verified_time`、已存卡 upsert）。

方案：payment 成功事务只写自己的表并登记补偿事件（已有）；`PaymentSucceeded` 事件 Payload 增加 `instrumentRef`、`consentToSave`、`customerId`；card 订阅 `PaymentSucceeded`，按 `paymentAttemptId` 幂等地标记凭证已验证并生成或更新已存卡。已存卡列表在支付成功后数秒内可见，GET checkout 读已存卡本来就经 Payment 查询并可降级为空，语义可接受。

### 2.4 风控属性读取

`PayRiskCheckActionActor` 现在进程内读 `CardService.riskAttributes`。改为 `card-api` `GET /card/v1/instruments/{instrumentRef}/risk-attributes`（BIN、指纹），在风控调用前同步读取；或在 2.2 的创建响应里直接返回并由 payment 存入 `t_payment_instrument_token`（推荐，省一次调用）。

## 3. 表与接口

- card 库新增：`t_card_material_ticket`、`t_consumed_event`、`t_compensation_event`；现有四张卡表迁入。
- `paytech-card-api`：`CardInstrumentApi`（创建工具、读风控属性）、`CardMaterialTicketApi`（签发票据）、`ChannelCardMaterialApi`（Platform 凭票据读卡）、`StoredCardApi`（从 payment-api 迁入，Commerce 的已存卡列出 / 删除 / 失效改调 card）。
- `paytech-payment-api`：删除 `ChannelCardMaterialApi` 与 `StoredCardApi`；`PaymentInstrumentApi.card-tokens` 保留为 Commerce 入口，内部改为两段创建。
- 事件：`PaymentSucceeded` Payload 扩字段（Schema 版本 2，消费者 Commerce 忽略新增字段）。

## 4. 调用方身份

- card 的三个入口分别只接受：`material-tickets` 与 `instruments` 来自 `payment`；`channel-card-materials/reveal` 来自 `platform-support`；`stored-cards` 来自 `acquiring`。以 D26 内部签名 Token 的 `caller` 与 `purpose` 声明校验，替代 `X-Caller-Service`。

## 5. 测试门禁

- 现有 6 条卡支付链路测试通过（payment 的 `-test` 以 card、channel-connector 为 DTO Mock；card 的 `-test` 单独覆盖票据、读卡、工具创建、事件消费）。
- 新增：票据二次使用被拒；票据过期被拒；CVV 已领取后拒绝签发新票据；工具创建后 payment 事务失败的孤立工具被清理任务回收；`PaymentSucceeded` 重复投递只生成一张已存卡；Platform 读卡用错票据或错 purpose 返回 4xx 且无卡数据。
- `VaultTransactionObserver` 迁入 card 的测试，继续断言 Vault 调用不在事务内。

## 6. PCI 边界说明

独立进程 + 独立库 + 独立 Vault 数据类型与 KMS 用途（`PAYMENT_CARD_DEK`）满足 D10 的三条件；原始卡号只经 Commerce 的 `card-tokens` 入口到 payment 再到 card。阶段 0 完成后，持卡人数据环境为 `card` 与接收原值的 `payment` 入口两处；是否把 `card-tokens` 入口直接改为 Checkout 前端经网关直达 card，留业务切片决定。

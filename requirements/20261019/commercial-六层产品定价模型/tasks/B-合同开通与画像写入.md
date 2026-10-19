# 任务卡 B · 合同、订阅、开通项与定价画像写入

> 依赖 A 合入。执行者 Opus 5.5 agent。范围只在 `paytech/commercial`。接口合同：[technique.md §2.2](../technique.md) 合同 / 订阅 / 开通项 / 画像 13 条；审批机制 §2.1；事件 §2.8（仅 `AgreementStatusChanged`）。

## 交付

1. API（`paytech-commercial-api`）：`AgreementApi`（`/agreements/v1`）、`PricingProfileApi`（`/pricing-profiles/v1`）与各 `*RequestBody` / `*ResponseBody`，中文 JavaDoc + Hibernate Validator；稳定 Code 用 A 线枚举的 `getCode()` 字符串。
2. Processor（`paytech-commercial-core/service/processor`）：每条写入一个 Processor，继承 Foundation `AbstractLockProcessor`（LOCK，租约 -1），锁 Key 按 §2.2；主流程"原请求提前返回 → 新请求完整阶段"；Context：`AgreementContext`、`PricingProfileContext`。
3. Domain Service（`interface + *Impl`）：`AgreementService`（创建、签署登记、状态推进、订阅集合替换、开通项增改）、`PricingProfileService`（创建、修改、激活、退役）。本地事务内只写本库 + BCR（AGREEMENT / AGREEMENT_PRODUCT / AGREEMENT_CAPABILITY / PAYMENT_METHOD_PROFILE 记录类型，Root 版本 CAS，`expectedChangeVersion` 入参）+ `compensation_event`。
4. 业务规则：合同类型与 Owner XOR；`business_mode` / `fund_mode` / `pricing_authority` CLIENT 非空、PARTNER 空；MOR 只允许 SETTLE_THROUGH_PLATFORM；本期 `fund_mode` 只接受 SETTLE_THROUGH_PLATFORM、`pricing_authority` 只接受 PLATFORM（PARTNER 时 `referral_partner_id` 非空，仍拒绝）；订阅产品 ACTIVE 且 `business_mode_scope` 含合同模式、基座产品不写；开通项的产品已订阅、`capability_code` ACTIVE、`entity_no` 被合同覆盖、保证金两字段成组、约束行同码 OR 异码 AND；签署按同 Party 全部角色原子登记、`acceptance_reference` 唯一、全部 Party 签完转 SIGNED；画像 `profile_kind=PAYMENT_METHOD` 时 `payment_method_code` 非空、条件维度与种类相容、ACTIVE 后不可改。
5. 事件：`AgreementStatusChanged` 经 `compensation_event` 在提交后发布（`paytech-messaging` 发布方式按 acquiring / billing 现有发布 Handler 的写法）；`compensation-event-catalog.yaml` 登记 `AGREEMENT_STATUS_CHANGED`；`foundation/paytech-messaging/catalog/events.yaml` 登记事件（producer commercial、consumers [acquiring]、strictOrder false、payloadSchemaRef `paytech-commercial-api/event/AgreementStatusChanged/v1`）；载荷类放 `paytech-commercial-api/.../api/event`。
6. Controller（`paytech-commercial-app/ext/rest`）+ Converter 只做协议转换。
7. 测试（auto，真实本地 MySQL + 本地 RabbitMQ）：按 §2.11 写入矩阵：首次、同身份重放、归属错误、状态不符、CAS 冲突、回滚、事件发布与补偿恢复；每个 API 至少一条 HTTP 测试走真实入口。

## 不做

条款 / 价格 / 命中（C）、解析扩展（D）、BFF 与页面（E）。不改 client-master。

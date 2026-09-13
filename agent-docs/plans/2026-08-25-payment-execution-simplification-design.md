# Payment Execution 支付工具与消费者动作精简设计

> 2026-09-13 命名后继：[支付渠道 Channel 决定](../decisions/2026-09-13-payment-channel-terminology.md)取代本文支付领域 Provider 命名；业务边界与历史证据保持。现行实体、字段及自有回调代码使用 Channel；外部固定协议与原报文不改写。

最后更新：2026-08-25  
状态：已确认并完成同步；自动契约已通过，PDManer 4.9.3 应用内检查仍待人工完成

> **后续替代说明（2026-08-27）：** 本文关于 Channel Refund 保存或回绑 Finance Funding Reservation/Version 的部分，已由 [Finance 退款 Funding 移除实施记录](./2026-08-27-finance-refund-funding-removal-implementation-plan.md) 替代。Payment 现仅以本库 Refund Position CAS 控制 Channel Refund；`UNKNOWN` 留在本库，`SUCCEEDED` 通知 Commerce 与 Finance，`FAILED/CANCELLED` 只通知 Commerce。本文其余 Instrument、Customer Action 与 Provider Transaction Ref 决策继续有效。

## 1. 目标与范围

本设计在不改变 Intent → Attempt → Action 主链、退款资金导航、重复成功处置和 Provider 交互边界的前提下，使 Payment Execution 兼容 Wallet/APM，并删除对 3DS 专属字段的过早假设。

本次只授权文档、生成器、自动契约测试和 PDManer 设计资产，不创建 Migration、Java 模块、物理数据库或运行环境。

## 2. 已确认决策

1. `t_payment_attempt` 新增非空 `payment_method_code`，从 Payment Method Catalog 取值。支付方式属于一次 Attempt，即使某种 APM 没有可持久化工具引用，也必须能完成路由与执行。
2. `t_payment_instrument` 继续采用单表方案，不增加类型化子表、Key-Value 或 JSON。`provider_token_ref` 改名为 `provider_instrument_ref`；现有 `scheme_code`、`bin_prefix`、`last4`、`issuer_country_code` 和 `provider_fingerprint` 保持可空。没有稳定 Provider 工具引用时不创建 Instrument。
3. `t_three_ds_context` 删除，由 `t_payment_customer_action` 替代。该表表达 3DS、Redirect、SDK、App Switch 和 QR Code 等消费者后续动作的最小当前状态，不保存临时 URL、Client Secret、SDK Token、QR Payload 或 bearer-like 参数。
4. `t_provider_transaction_ref` 和退款导航保持不变：Payment Refund Position 冻结原成功 Transaction Ref，退款 Action 以 `target_provider_transaction_ref_id` 精确指向原交易，退款成功 Ref 再以 `original_provider_transaction_ref_id` 关联原交易。
5. `t_duplicate_payment_case` 保留。重复支付必须由同一 Intent 下第二个不同的、经验证的 SALE/资金完成 CAPTURE 成功事实触发；重复 Callback、同一外部交易号重放、QUERY 确认原 Action 或 AUTHORIZE→CAPTURE 正常链路均不得误判。
6. `t_payment_state_transition` 保留，但 `aggregate_type` 与 `root_type` 只允许 `PAYMENT_INTENT`、`PAYMENT_ATTEMPT`、`PAYMENT_ACTION`、`CHANNEL_REFUND`、`DUPLICATE_PAYMENT_CASE`。Provider Callback 使用 Callback/Item/Exception 事实，Provider Dispute Notice 使用自己的 Event 表，不再复制通用状态流转。

## 3. 目标字段

### 3.1 t_payment_attempt 增量

```text
payment_method_code VARCHAR(64) NOT NULL
```

### 3.2 t_payment_instrument

```text
payment_instrument_id BIGINT NOT NULL
payment_attempt_id BIGINT NOT NULL
provider_instrument_ref VARCHAR(512) NOT NULL
scheme_code VARCHAR(64) NULL
bin_prefix VARCHAR(8) NULL
last4 CHAR(4) NULL
issuer_country_code CHAR(2) NULL
provider_fingerprint VARCHAR(255) NULL
create_time DATETIME(6) NOT NULL
```

`payment_attempt_id` 保持唯一，因此 Attempt 与 Instrument 是 1:0..1。CARD 或 Apple Pay 等卡轨支付可保存获批卡片描述；PayPal 等方法只有稳定工具引用时才创建 Instrument；一次性 Bank Redirect 没有工具引用时不创建。

### 3.3 t_payment_customer_action

```text
payment_customer_action_id BIGINT NOT NULL
payment_action_id BIGINT NOT NULL
customer_action_type VARCHAR(64) NOT NULL
customer_action_status VARCHAR(64) NOT NULL
provider_reference VARCHAR(255) NULL
expires_at DATETIME(6) NULL
completed_at DATETIME(6) NULL
version BIGINT NOT NULL
create_time DATETIME(6) NOT NULL
update_time DATETIME(6) NOT NULL
```

闭合枚举：

- `customer_action_type`：`REDIRECT`、`THREE_DS_CHALLENGE`、`SDK`、`APP_SWITCH`、`QR_CODE`。
- `customer_action_status`：`PENDING`、`COMPLETED`、`FAILED`、`EXPIRED`。

约束：

- `payment_action_id` 唯一，一个 Payment Action 最多一个当前消费者动作。
- `COMPLETED` 必须有 `completed_at`；其余状态必须没有 `completed_at`。
- `version > 0`，用于浏览器回跳、Provider Callback 与超时处理的 CAS 竞争。
- `provider_reference` 只能保存非敏感稳定引用，不能保存可直接继续会话的秘密值。

## 4. 退款精确导航

```text
Channel Refund
  → Payment Refund Position
  → original_provider_transaction_ref_id
  → Provider Transaction Ref
  → payment_action_id
  → 原 Provider / Provider Account / MID
```

新 REFUND Action 冻结相同 Provider、Provider Account 和 MID，并以 `target_provider_transaction_ref_id` 指向原 SALE/CAPTURE Ref。Adapter 使用原 Ref 的 `external_transaction_id` 发起退款；成功后创建 `reference_type=REFUND` 的新 Ref。

Channel Refund 只保存 `payment_refund_position_id`，Intent 与成功 Attempt 通过 Position 唯一追溯，不再重复保存。`refund_funding_reservation_id` 使用单字段唯一索引保证 Finance 资金预留只能绑定一个 Channel Refund；创建与回绑所需的两个 Finance Version 保留，没有 Canonical 复算和比较消费者的 Binding Hash 删除。

## 5. 重复支付判定

验证后的成功资金事实到达时必须锁定 Intent：

1. `successful_attempt_id` 为空：把当前 Attempt 设为唯一成功 Attempt。
2. 已指向同一 Attempt/Action/Transaction Ref：按幂等重放处理。
3. 已指向另一 Attempt，且新事实拥有不同的成功资金 Action 与外部 Transaction Ref：创建唯一 Duplicate Payment Case，保留原成功指针，把后到 Attempt 收敛为 `CONFLICTED`，并对重复 Ref 发起补偿退款。
4. 未 Capture 的重复 Authorization 不属于重复支付退款；它是多余授权冻结，应对精确 Authorization 执行 VOID。

## 6. 目标物理基线

替换表不改变表数；在 Payment Method/Customer Action 精简基础上，Channel Refund 又删除两个可经 Position 推导的字段、一个无消费者 Hash、两条冗余关系与两个访问索引，并增加一个 Funding Reservation 唯一索引。目标基线为：

- 29 张表
- 376 个字段
- 42 个唯一索引
- 44 个访问索引
- 63 条本库关系
- 7 条强制一对一关系
- 7 个 Diagram Groups

自动契约必须证明上述精确基线、消费者动作敏感数据边界、Payment Method Catalog 来源、退款导航、重复支付判定与精简后的状态流转白名单。PDManer 4.9.3 应用内检查仍是人工门禁。

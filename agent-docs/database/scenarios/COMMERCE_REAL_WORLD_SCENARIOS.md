# Commerce 现实场景与逐表字段映射

最后更新：2026-08-26

状态：与 [Commerce 14 表权威模型](../COMMERCE_DATABASE.md) 和生成 PDManer 模型同步；它是字段、关系、状态和场景文档测试的现实门禁。

## 1. 场景范围

场景 ID：`NOVA_COMMERCE_UAE_2026`

Nova Learning（PARTNER 来源 Operating Entity）创建一笔 Hosted Checkout：一个软件许可 Item，税前 USD 100.00，UAE VAT USD 5.00，Consumer 实付 USD 105.00。Consumer 接受三份法律内容；第一次 Payment Attempt 明确失败，第二次成功。之后 Client 发起一笔 USD 20.00 部分退款，Payment Refund 先 UNKNOWN 后成功并触发 Credit Note；Operations 发起第二笔 USD 10.00 部分退款，Payment Refund 先 UNKNOWN 后明确失败。Commerce 本地预占成功后直接调用 PAY-I04；Payment 成功同时通知 Commerce 与 Finance，失败/取消只通知 Commerce，UNKNOWN 只保留在 Payment 本地。Receipt 与 Credit Note 的状态、文件和更正关系由 Platform Support 保存，Finance 保存不可变 Fiscal Fact，Commerce 不再维护消费者文档投影。

Commerce 不建设主动履约。Chargeback 后由 Risk 将 Dispute Case 置为 `EVIDENCE_REQUIRED`，向 Client 发出 `DisputeEvidenceRequested`，并通过 Risk 自有 Evidence Package/Reference 收集材料；Commerce 不创建履约表或履约事件，也不伪造履约文件行。

本场景用于证明：

- Order 主表冻结主体、Industry/MCC 和金额；完整 Tax Line 由 Finance、完整 Commercial Term Component 由 CCT 保存；
- `consumer_reference` 是 Client 命名空间内的非 PII、不透明业务引用，不得复用 Email、Phone、证件号等个人原值；Email/Phone 使用稳定 Token，Profile Token 只保护姓名和完整账单地址，不重复包含 Email/Phone；
- Checkout 入口直接保存完整客户端 IP、完整 User-Agent 与可信代理链，以及不可逆 Geo/Network/UA 解析维度；不保存 Vault Token、Blind Index、Key Version 或设备标识；
- 三个 Acceptance 与各自 `LEGAL_ACCEPTANCE` Network Evidence 同事务插入；Payment Readiness 返回三个显式 Acceptance ID，不维护 Acceptance Summary；
- Payment Intent 与 Attempt 使用不同来源版本命名空间；Payment Refund UNKNOWN 在 Commerce 映射为 `PROCESSING`，本地退款额度不释放；
- CLIENT 发起时 client_refund_id 必填；OPERATIONS 发起时为空，改用命令幂等键恢复重试；
- Payment 权威成功同时驱动 Commerce 本地额度消费和 Finance 独立记账；失败/取消只驱动 Commerce 释放，UNKNOWN 只保留在 Payment 本地；
- Commerce V1 不保存通用 BCR；需要保留的法律接受、网络证据和订单冻结上下文继续使用专项结构化事实。

## 2. 稳定身份、Hash 与时间

| 事实 | 示例值 |
|---|---|
| Client | `client_id=710000000000000101`，`client_order_id=NOVA-ORDER-9001` |
| Operating Entity | `operating_entity_id=710000000000000201`，`entity_source_type=PARTNER` |
| CCT Assignment | `assignment_id=720000000000000201` |
| Client Agreement | `client_agreement_operating_entity_id=720000000000000302` |
| Client/Partner Scope | `720000000000000403` / `720000000000000406` |
| Platform Service Charge Scope | `platform_service_charge_scope_id=720000000000000409`，Term Type=`PLATFORM_SERVICE_CHARGE` |
| Platform Service Party | `platform_service_party_id=730100000000000004`，来自 Partner Agreement 唯一 `PLATFORM_SIGNATORY` |
| Risk | `risk_checkout_assessment_id=730000000000000101`，Outcome `ALLOW` |
| Finance Tax | `finance_tax_snapshot_id=740000000000000101`，`calculation_hash=H_FINANCE_TAX` |
| Payment | Intent `750000000000000101`；Attempt 1 `750000000000000111`；Attempt 2 `750000000000000112` |

| 别名 | 值 |
|---|---|
| `H_CREATE_REQUEST` | `1111111111111111111111111111111111111111111111111111111111111111` |
| `H_CCT_V5` | `3333333333333333333333333333333333333333333333333333333333333333` |
| `H_FINANCE_TAX` | `1212121212121212121212121212121212121212121212121212121212121212` |
| `H_RENDERED_CONTRACT` | `9999999999999999999999999999999999999999999999999999999999999999` |

| 时间（UTC） | 事实 |
|---|---|
| `2026-08-23T08:00:00.000000Z` | Create Order 首次冻结命令时点 |
| `2026-08-23T08:00:02.000000Z` | Order、Context、Legal、Item 与 Refund Position `0/0/0` 同事务提交，Session=PREPARING |
| `2026-08-23T08:00:05.000000Z` | Payment Intent Projection 提交，Order/Session=OPEN |
| `2026-08-23T08:02:00.000000Z` | Consumer 修正账单地址，Consumer Context Version 2 生效 |
| `2026-08-23T08:03:00.000000Z` | Consumer Contract 创建 |
| `2026-08-23T08:04:00.000000Z` | Consumer Terms Network Evidence + Acceptance 同事务插入 |
| `2026-08-23T08:05:00.000000Z` | Refund Policy Network Evidence + Acceptance 同事务插入 |
| `2026-08-23T08:06:00.000000Z` | Privacy Notice Acceptance 与 LEGAL_ACCEPTANCE 网络证据同事务插入，三类齐全 |
| `2026-08-23T08:07:00.000000Z` | Attempt 1 开始并明确失败 |
| `2026-08-23T08:08:00.000000Z` | Attempt 2 成功，Order=PAID，Session=COMPLETED，Refund Base 从 0 建立为 105.00 |
| `2026-08-23T09:00:00.000000Z` | Client 创建 USD 20 Refund Request=`PENDING`，本地 Position=`105/20/0` |
| `2026-08-23T09:01:00.000000Z` | Channel Refund=UNKNOWN，Commerce Request=`PROCESSING`，Position 保持 `105/20/0` |
| `2026-08-23T09:03:00.000000Z` | Payment Refund=SUCCEEDED，Commerce 同事务得到 Request=`SUCCEEDED`、Position=`105/0/20` |
| `2026-08-23T10:00:00.000000Z` | Operations 创建 USD 10 Refund Request=`PENDING`，`client_refund_id=NULL`，Position=`105/10/20` |
| `2026-08-23T10:01:00.000000Z` | Channel Refund=UNKNOWN，Commerce Request=`PROCESSING`，Position 保持 `105/10/20` |
| `2026-08-23T10:02:00.000000Z` | Payment Refund=FAILED，Commerce 同事务释放本地额度，Request=`FAILED`、Position=`105/0/20` |
| `2026-08-23T10:06:00.000000Z` | Finance 没有失败退款资金动作；Commerce 状态与 Position 已由 Payment 终态收口 |
| `2026-08-24T09:00:00.000000Z` | Chargeback 后由 Risk 发起 RFI/举证收集 |
| `2026-08-24T10:00:00.000000Z` | 未发起渠道支付的 Order A 被 Client Cancel，Payment 确认 Intent 终止后 Order/Session=CANCELLED |
| `2026-08-24T10:10:00.000000Z` | Order B 授权成功但未 Capture，Operations Reversal 由 Payment 原子解析为 Void |
| `2026-08-24T10:20:00.000000Z` | Order C 已全额 Capture，Operations Reversal 由 Payment 返回 REFUND_REQUIRED，Commerce 创建全额 Refund Request |

## 3. CCT Bundle、Consumer 与网络证据

CreateOrder 把 `command_idempotency.operation_context_time=2026-08-23T08:00:00.000000Z` 冻结为 CCT-I13 `context_as_of_time`，并原样写入 `order_context.commercial_context_time`；恢复重放不得重新取 `now()`。Commerce 保存 CCT V5 Bundle 的 `H_CCT_V5`、Assignment、Scope、法律与 Seller 引用。PARTNER 来源四项成组非空：`local_partner_id`、`partner_share_scope_id`、`platform_service_charge_scope_id`、`platform_service_party_id`；非 PARTNER 来源四项成组为空。示例中的 Platform Service Charge Scope 是 `PLATFORM_SERVICE_CHARGE` 条款，Platform Service Party 来自 Partner Agreement 唯一 `PLATFORM_SIGNATORY`。Finance 后续调用 `GetImmutableCommercialTermScope(720000000000000403,720000000000000302,2026-08-23T08:00:00.000000Z)`；`H_CCT_V5` 是 Commerce 审计关联摘要，不是单 Scope 校验参数，也不复制到 Finance Transaction Economics Header。Finance Tax 使用 `finance_tax_snapshot_hash=H_FINANCE_TAX`，完整税务证据通过 `GetImmutableTaxSnapshot(740000000000000101, H_FINANCE_TAX)` 获取。

边界样例：非 Partner 成功场景（`PLATFORM` 或 `CLIENT` 来源）要求上述四项全部为 `NULL`；PARTNER 来源只要其中任一项为空，或者非 PARTNER 来源错误携带任一项，均按“缺一字段拒绝创建 Order”失败关闭。COM-I04 只回传已经冻结的 Scope、Party 和 V5 Hash；Commerce 不保存 CCT Term Component 或 Finance Entity 映射，也不计算平台服务费金额。

### 3.1 Consumer Context 完整行

08:02 纠正后的 `t_consumer_context` Version 2：

| 字段 | 示例值 |
|---|---|
| `consumer_context_id` / `order_id` | `810000000000000012` / `810000000000000001` |
| `consumer_reference` | `nova-consumer-opaque-1001` |
| `email_vault_token` / `phone_vault_token` | `vlt_email_learner_01` / `vlt_phone_971_01` |
| `consumer_profile_vault_token` | `vlt_consumer_profile_02` |
| `email_domain` / `phone_country_code` | `learner.example` / `971` |
| `billing_country_code` / `billing_region_code` / `billing_city` / `billing_postal_prefix` | `AE` / `DU` / `Dubai` / `NULL` |
| `version` | `2` |

`consumer_reference` 是 Client 命名空间内的非 PII、不透明业务引用；不得复用 Email、Phone、证件号等个人原值。同一规范化 Email/Phone 在平台内部同字段作用域返回稳定 Token，分别兼具受控关联与授权 Reveal；consumer_profile_vault_token 只保护姓名和完整账单地址，不重复包含 Email/Phone。命令恢复重放同一 Token/Binding；Commerce 不把 Token 或个人原值复制到通用审计 JSON。

### 3.2 Network Evidence 完整行

Consumer Terms 接受时的 `t_checkout_network_evidence`：

| 字段 | 示例值 |
|---|---|
| `network_evidence_id` / `order_id` / `checkout_session_id` | `810000000000000025` / `...001` / `...011` |
| `evidence_scene` / `request_id` | `LEGAL_ACCEPTANCE` / `req-legal-terms-0001` |
| `client_ip_address` / `user_agent` | `198.51.100.24` / `Mozilla/5.0 (Linux; Android 15) Chrome/128.0` |
| `trusted_proxy_chain` | `203.0.113.10, 198.51.100.24` |
| Geo/ASN/Network | `AE / DU / Dubai / 64520 / MOBILE` |
| Proxy/VPN/Tor/Hosting | `0 / 0 / 0 / 0` |
| Browser/OS/device_type | `Chrome 128 / Android 15 / MOBILE` |
| `occurred_at` | `2026-08-23T08:04:00.000000Z` |

Commerce 直接保存完整客户端 IP、完整 User-Agent 与可信代理链；不保存 Vault Token、Blind Index 或 Key Version，不采集 `device_identifier`。证据插入后不可更新；上述原值与派生维度均是受限取证/风险数据，禁止普通页面展示、复制到通用审计 JSON 或任意导出。

### 3.3 Acceptance 完整行与 Readiness

| `consumer_acceptance_id` | `consumer_contract_id` | `network_evidence_id` | `acceptance_type` | `accepted_at` |
|---|---|---|---|---|
| `810000000000000015` | `810000000000000013` | `810000000000000025` | `CONSUMER_TERMS` | `08:04:00` |
| `810000000000000016` | `810000000000000013` | `810000000000000026` | `REFUND_POLICY` | `08:05:00` |
| `810000000000000017` | `810000000000000013` | `810000000000000027` | `PRIVACY_NOTICE` | `08:06:00` |

三条 Network Evidence 与 Acceptance 各自在同一本地事务插入。COM-I10 校验 Contract/Order/Session 归属、未到期可支付状态、三类型恰好齐全和每条同 Order/同 Session 的 `LEGAL_ACCEPTANCE` Evidence，返回三个显式 ID 与 `acceptance_completed_at=2026-08-23T08:06:00.000000Z`；不维护 Acceptance Summary。

## 4. 14 表逐表映射

| 逻辑表 | 本场景示例 | 写入/更新/冻结语义 |
|---|---|---|
| `commerce_order` | `order_id=...001`，`client_order_id=NOVA-ORDER-9001`，金额 `100/0/100/5/0/105 USD`，最终 `PAID`，`status_reason_code=NULL` | CREATED、OPEN、PAID 条件更新；Client 标识、OE、分类、金额币种和到期冻结；取消/过期时结构化保存当前终态原因 |
| `order_context` | `order_context_id=...002`，`commercial_context_time=08:00:00`，`cct_commercial_context_hash=H_CCT_V5`，`partner_share_scope_id=...406`，`platform_service_charge_scope_id=...409`，`platform_service_party_id=...004`，Tax Hash=`H_FINANCE_TAX` | 创建一次后不可变；PARTNER 条件元组随订单冻结，不复制 Tax Line 或 Term Component |
| `order_legal` | `order_legal_id=...003`，两条 Association、三份 Legal File/Hash 与 Seller Disclosure | 与 Context 同事务插入并冻结 |
| `order_item` | `order_item_id=...004`，`LIC-ANNUAL-01`，Total `105.00 USD` | 直接归属 Order，不可变 |
| `order_payment_projection` | Intent `...101=SUCCEEDED`，成功 Attempt `...112`，Captured `105.00 USD` | Attempt 1 失败后 Attempt 2 成功；迟到事件不回退 |
| `order_refund_position` | Order 创建时 Base/Reserved/Refunded=`0/0/0 USD`；PaymentSucceeded 建立 Base=`105`；最终=`105/0/20` | 与 Order 同事务初始化；成功支付只建立 Base 一次；退款创建、成功、失败/取消分别以 CAS 预占、消费、释放 |
| `checkout_session` | `checkout_session_id=...011`，最终 `COMPLETED` | PREPARING、OPEN、PAYMENT_PROCESSING、COMPLETED 条件推进 |
| `consumer_context` | `consumer_reference=nova-consumer-opaque-1001`，Email/Phone/Profile 三 Token，Billing=`AE/DU/Dubai/NULL`，v2 | Token/派生维度 CAS 更新；Profile 不复制 Email/Phone；不存 Blind Index/Masked/Composite Token |
| `consumer_contract` | Contract `...013`，Rendered Hash=`H_RENDERED_CONTRACT`，`en-AE` | 插入即冻结 |
| `checkout_network_evidence` | `...025`，`LEGAL_ACCEPTANCE`，直接保存 IP/UA/Proxy Chain 与解析维度 | 每个可信请求一行，不存 Vault Token/Blind Index/Key Version/device identifier；插入后不更新 |
| `consumer_acceptance` | `...015/016/017` 对应三条合同类型和 Evidence | 与 Legal Evidence 同事务插入，Contract+Type 唯一 |
| `refund_request` | Client `...030`=`SUCCEEDED` USD 20，`client_refund_id=NOVA-REFUND-20-001`；Operations `...040`=`FAILED` USD 10，`client_refund_id=NULL` | 唯一 Commerce 退款状态；UNKNOWN 映射 PROCESSING；成功同时通知 Commerce 与 Finance，失败/取消只通知 Commerce |
| `command_idempotency` | Create Order、三条 Acceptance、Client 退款、Cancel 和 Operations Reversal 命令；退款键 `ops-refund-810000000000000001-10-v1` | 同 Caller/Operation/Key 同 Hash 重放；新 Key 重用 client_order_id 或 Client 重用 client_refund_id 均不能创建重复业务事实 |
| `compensation_event` | `OrderCancelled`、退款状态和消费者文档生成请求等可靠 MQ/本地恢复任务，采用 `lock_version` | 业务事务登记、afterCommit 执行；不保存消费者文档 Current Projection，也无履约事件 |

Finance 保留完整 Tax Snapshot；Commerce 只校验 `H_FINANCE_TAX`、总额和逐 Item 税额。暂时网络失败不 ACK `PaymentSucceeded` 并等待重投；永久关系错误进入 DLQ。

### 4.1 两笔 Refund Request 完整行

| 字段 | Client 发起的 USD 20 退款 | Operations 发起的 USD 10 退款 |
|---|---|---|
| `refund_request_id` | `810000000000000030` | `810000000000000040` |
| `refund_request_no` | `RFD-20260823-0001` | `RFD-20260823-0002` |
| `order_id` | `810000000000000001` | `810000000000000001` |
| `client_id` | `710000000000000101` | `710000000000000101` |
| `request_origin` | `CLIENT` | `OPERATIONS` |
| `client_refund_id` | `NOVA-REFUND-20-001` | `NULL` |
| `refund_amount` | `20.00000000` | `10.00000000` |
| `currency` | `USD` | `USD` |
| `reason_code` | `CUSTOMER_REQUEST` | `OPERATIONS_ADJUSTMENT` |
| `reason_summary` | `Consumer requested partial refund` | `Operations correction after case review` |
| `refund_status` | `SUCCEEDED` | `FAILED` |
| `status_reason_code` | `NULL` | `PROVIDER_DECLINED` |
| `channel_refund_id` | `750000000000000201` | `750000000000000202` |
| `completed_at` | `2026-08-23T09:03:00.000000Z` | `2026-08-23T10:02:00.000000Z` |
| `version` | `4` | `3` |
| `create_time` | `2026-08-23T09:00:00.000000Z` | `2026-08-23T10:00:00.000000Z` |
| `update_time` | `2026-08-23T09:03:00.000000Z` | `2026-08-23T10:02:00.000000Z` |

CLIENT 发起时 client_refund_id 必填；OPERATIONS 发起时为空。`NOVA-REFUND-20-001` 是 Client 业务退款标识，不代替传输命令幂等键；Operations 使用 `t_command_idempotency.idempotency_key=ops-refund-810000000000000001-10-v1`。`channel_refund_id` 是可空唯一稳定下游 ID：Payment 尚未创建 Channel Refund 时允许为空，填入后只用于关联，不复制下游状态。Commerce 本地预占成功后直接调用 PAY-I04；Payment 明确拒绝时 Request 进入 `FAILED` 且 `channel_refund_id` 仍可为 `NULL`。

## 5. 关键行的版本与状态对账

### 5.1 两次 Payment Attempt

| Attempt | 终态 | 对 Commerce 的影响 |
|---|---|---|
| `750000000000000111` | FAILED | Active 清空，Session 回 OPEN，不终止 Intent |
| `750000000000000112` | SUCCEEDED | 冻结唯一成功 Attempt；Order PAID；Refund Base=105.00 |

两个 Attempt 均属同一 Operating Entity。临时短缺可在同一 OE 下重试；OE CLOSED 时没有自动替换订单，Client 必须使用新的 client_order_id，且不自动创建替换订单。

### 5.2 UNKNOWN 后退款状态

| 步骤 | Commerce Refund 状态 | Base/Reserved/Refunded | Finance 独立动作 |
|---|---|---|---|
| Order 创建 | 尚无 Request | `0/0/0` | 无 |
| PaymentSucceeded | 尚无 Request | `105/0/0` | 独立确认入账事实 |
| Client 创建 USD 20 退款 | `PENDING` | `105/20/0` | Commerce 本地预占后直接调用 PAY-I04 |
| Payment 接受渠道退款 | `PROCESSING` | `105/20/0` | Payment 本地 Refund Position 同步预占 |
| Payment Refund=UNKNOWN | `PROCESSING` | `105/20/0` | UNKNOWN 只保留在 Payment 本地 |
| Payment Refund=SUCCEEDED | `SUCCEEDED` | `105/0/20` | 同时通知 Commerce 与 Finance |
| Operations 创建 USD 10 退款 | `PENDING` | `105/10/20` | Commerce 本地预占后直接调用 PAY-I04 |
| Payment Refund=UNKNOWN | `PROCESSING` | `105/10/20` | UNKNOWN 只保留在 Payment 本地 |
| Payment Refund=FAILED | `FAILED` | `105/0/20` | 只通知 Commerce 释放本地额度 |

Payment Refund=UNKNOWN，Commerce Request 保持 PROCESSING，本地退款额度继续预占；UNKNOWN 只保留在 Payment 本地，不通知 Finance。Payment Refund=SUCCEEDED，Commerce 同事务消费本地退款额度并将 Request 置为 SUCCEEDED；Payment Refund=SUCCEEDED 同时通知 Commerce 与 Finance，Finance 独立记账。Payment Refund=FAILED/CANCELLED，Commerce 同事务释放本地退款额度并进入对应终态；失败/取消只通知 Commerce。

Credit Note 不覆盖 Receipt。Commerce 只可靠发布 Refund 成功与文档生成来源事实；Receipt、Credit Note、版本、文件和更正引用都在 Platform Support 中保存，Finance 保留对应不可变 Fiscal Fact。

### 5.3 Chargeback RFI

Chargeback 建案后，Risk 将 Dispute Case 置为 `EVIDENCE_REQUIRED` 并发布 `DisputeEvidenceRequested`。Client 按 Risk 的 Evidence Package/Reference 和 Submission Request 在截止时间前提供材料；Commerce 只保留自己的 Order、Acceptance、Network Evidence 和 Refund 事实，不创建履约 ID、时间线、表行或事件来模拟举证。

## 6. Cancel、Void 与 Reversal 现实映射

### 6.1 未支付 Cancel

Order A：`order_id=810000000000000060`，`client_order_id=NOVA-CANCEL-9002`，Order=`OPEN`，Session=`OPEN`，Intent=`REQUIRES_PAYMENT`，尚无成功 Authorization/Capture/Sale，也没有已提交或 `UNKNOWN` Provider Action。

Client 使用 `cancel-order-810000000000000060-v1` 调用 `COM-I05`。Commerce 调用 `PAY-I12`，Payment 在 Expected Version 下把 Intent 终止为 `CANCELLED`。Commerce 收到确定结果后在同一本地事务更新：

| 表 | 结果 |
|---|---|
| `t_commerce_order` | `order_status=CANCELLED`，`status_reason_code=CLIENT_CANCELLED` |
| `t_checkout_session` | `session_status=CANCELLED`，`cancelled_at=2026-08-24T10:00:00.000000Z` |
| `t_order_payment_projection` | `intent_status=CANCELLED`，来源版本单调推进 |
| `t_compensation_event` | 登记 `OrderCancelled`，提交后发布 |

如果 Provider Action 已提交或为 `UNKNOWN`，`PAY-I12` 返回 `PENDING` 并先 Query；若结果证明已授权或已捕获，则返回 `REVERSAL_REQUIRED`。Cancel 不会静默升级成 Reversal，也不会创建 Refund Request 或 Finance 事实。

### 6.2 授权成功但未 Capture 的 Reversal

Order B：`order_id=810000000000000061`，`AUTHORIZE Action=SUCCEEDED`，`captured_amount=0`。Operations 使用 `reverse-payment-810000000000000061-v1` 调用 `COM-I08`；Payment 在权威锁内通过 `PAY-I13` 冻结原 Authorization Ref 并创建 `VOID` Action。

Void 成功后：`VOID Action=SUCCEEDED`，Attempt=`FAILED`、`reason_code=AUTHORIZATION_VOIDED`，Intent=`CANCELLED`；Commerce 把 Order/Session 置为 `CANCELLED`，订单 `status_reason_code=AUTHORIZATION_VOIDED`。该路径没有 Refund Request、Channel Refund、Finance Journal、Fiscal Fact 或 Credit Note。

### 6.3 已 Capture 的 Reversal

Order C：`order_id=810000000000000062`，总额 USD 50.00，`SALE` 或全额 Capture 已成功。`PAY-I13` 在权威锁内返回 `REFUND_REQUIRED`；Commerce 以 Operations Command Idempotency 创建 `refund_amount=50.00000000` 的标准 Refund Request，本地预占后直接调用 PAY-I04 进入 Payment Channel Refund 主链路。

Captured Reversal 完成后，Order 仍保持 `PAID`，Refund Position 变为 `50/0/50`，经济返还由 Refund Request 表达，不增加 `REVERSED` Order 状态或 Reversal 表。若出现 `0 < captured_amount < total_amount`，V1 返回 `PARTIAL_CAPTURE_REVERSAL_NOT_SUPPORTED`，不得只 Void 剩余授权或只退款已捕获部分后留下另一半未处理。

## 7. 文档、审计与敏感数据边界

Commerce 14 表不保存 Webhook 配置 ID、URL 或 Secret，也不保存 Consumer Document Current Projection 或通用 BCR。Receipt 与 Credit Note 的状态、文件和更正关系由 Platform Support 保存，Finance 保存不可变 Fiscal Fact；Commerce 只通过 `t_compensation_event` 可靠发布最小来源事实。Consumer Token、网络原值和派生维度不得进入普通日志、MQ 文档事件或普通业务页面。场景中没有任何 PAN、CVV/CVC、完整磁道、PIN/PIN Block、卡有效期、主动履约表或主动履约事件。

## 8. 最终可观察结果

- Order `ORD-20260823-0001` 为 PAID，总额 USD 105.00，唯一成功 Attempt 为 `750000000000000112`。
- Consumer Contract 与三条 Acceptance 永久定位原法律内容；Readiness 显式返回 `...015/016/017`。
- Consumer Context 使用非 PII 引用和三枚职责清晰的稳定 Token；Network Evidence 直接保存完整 IP/UA/可信代理链，不使用 Vault/Blind Index/Key Version。
- Refund Position 最终为 `105.00/0.00/20.00`；成功退款触发 Platform Support 生成 Credit Note，失败退款由 Payment 权威失败事件立即释放 Commerce 本地额度，Finance 独立收口。
- Order A 通过 Cancel 关单；Order B 的授权通过 Void 释放；Order C 的 Captured Reversal 复用全额 Refund，三条路径没有新增业务表。
- Chargeback RFI/举证由 Risk 承接，Commerce 没有主动履约域。

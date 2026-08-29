# Commerce 消费者分析与网络证据精简设计

最后更新：2026-08-24  
状态：历史 23 表阶段，已被 [Commerce 隐私与履约精简设计](./2026-08-24-commerce-privacy-fulfillment-simplification-design.md) 取代

> 本文仅保留旧方案的决策过程。当前任务不得据此恢复 Consumer Blind Index、Network Vault Token、Key Version、设备标识、Acceptance Summary 或主动履约结构；现行规范以 20 表方案及 Commerce 权威数据库文档为准。

## 1. 目标

本次调整解决三个已经出现的实际问题：

1. Consumer Email、Phone、账单地址和网络证据全部收进一个 Vault Token 后，Commerce 与 Risk 无法执行重复消费者、地域不一致、IP/设备频次等分析；
2. `consumer_acceptance_summary` 保存的是三条不可变 Acceptance 可以实时计算出的重复状态；
3. `vault_record_version`、`vault_metadata_hash` 和 `*_masked_value` 在 Commerce 当前流程没有独立查询、展示或并发消费者，却被机械地作为每个 Token 的固定套餐。

目标不是把敏感原值重新放回 Commerce，而是把原值保护、假名化关联和业务分析三种职责拆开。

## 2. 固定边界

- Vault 是通用敏感值存储与密码学服务，不创建 Consumer、Customer、Order 或 Network Evidence 业务表，也不拥有这些字段的业务含义。
- Commerce 定义并校验原始 Payload Schema，Vault 只按通用 Data Type、Purpose、Classification、大小和安全策略保护一个不透明 Payload。
- Email、Phone、姓名、完整账单地址、完整 IP、完整 User-Agent 和可信代理链原值只进入 Vault，不进入 Commerce、Risk、事件、日志或 BCR。
- Commerce 保存真实查询需要的用途隔离 HMAC Blind Index，以及不能还原原值的分析维度；Blind Index 必须与自己的 Key Version 成组保存。当前字段使用一个明确获批、同时授权 Commerce 在线分析与 Risk Assessment 的共享分析 Purpose；仅供另一 Purpose 使用的 Index 不得直接转发或复用。
- Risk 不读取 Commerce 数据库。Commerce/Payment 在调用 Risk 时显式传入批准的假名化关联键和结构化维度，Risk 再保存自己的不可变输入与类型化 Fact。
- Blind Index、Email Domain、账单/IP 地域、ASN、Browser/OS/Device 等仍是可关联的假名化或准标识数据，不等于完全匿名；Commerce、Risk 与未来数仓必须按受限分析数据执行最小权限、访问审计、Purpose 限制和留存/删除治理，禁止普通业务页面展示或任意导出。
- Masked Value 只有存在普通页面展示需求时才落业务库；Commerce 当前消费者分析链路没有这一消费者，因此本轮不保存。
- Vault Record Version 与 Metadata Hash 只有存在元数据并发、跨系统校验、轮换同步或业务内容承诺消费者时才落业务库；Commerce 当前 Token 只承担受控 Reveal/Retire 引用，因此本轮不保存。

## 3. Consumer Context

`t_consumer_context` 仍是每个 Order 至多一行、可以受控纠正的 Current Root，字段固定为：

```text
consumer_context_id
order_id
consumer_pii_vault_token
ref_blind_index
ref_blind_index_key_version
email_blind_index
email_blind_index_key_version
phone_blind_index
phone_blind_index_key_version
email_domain
phone_country_code
billing_country_code
billing_region_code
billing_city
billing_postal_prefix
version
create_time
update_time
```

约束：

- `consumer_pii_vault_token` 指向 Commerce 定义的消费者原始 Payload；Vault 不解析其业务字段并且没有业务专表。
- Consumer Ref 与 Email 必填；Phone 可空，`phone_blind_index` 与其 Key Version 必须同时为空或同时非空。
- `email_domain` 由规范化 Email 派生并转为小写，不保存 Local Part；`phone_country_code` 只保存 E.164 国家呼叫代码。
- `billing_country_code` 必填；Region、City 和 Postal Prefix 可空。Postal Prefix 必须使用国家化规则截断，禁止保存完整邮编。
- Order Context 已冻结 `consumer_country_code + locale`，Consumer Context 不重复保存同义的 `country_code/locale`。
- 修改时先保护并 Bind 新 Payload，再以 Expected `version` 原子切换 Token、全部 Blind Index 和结构化维度；提交后 Retire 旧 Token。
- Protect/Bind 必须使用受 `command_idempotency` 管理的稳定请求键。Bind 成功后在本地提交前崩溃时，命令恢复必须重放同一 Token/Binding 并继续原 CAS；明确终止时 Retire 未被业务行采用的 Binding，不能留下永久孤儿。
- BCR 只保存消费者引用、联系方式、账单资料“发生变化”的受控标记和业务版本，不复制 Token、Blind Index 或可重新识别的原始值。

## 4. Checkout Network Evidence

新增不可变 `t_checkout_network_evidence`，一行代表一次 Hosted Checkout 交互在可信入口采集的网络事实：

```text
network_evidence_id
order_id
checkout_session_id
evidence_scene
request_id
raw_evidence_vault_token
ip_blind_index
ip_blind_index_key_version
ip_network_blind_index
ip_network_blind_key_ver
device_blind_index
device_blind_index_key_version
ip_country_code
ip_region_code
ip_city
ip_asn
network_type
proxy_flag
vpn_flag
tor_flag
hosting_flag
browser_family
browser_major_version
os_family
os_major_version
device_type
occurred_at
create_time
```

约束：

- `evidence_scene` 固定为 `CHECKOUT_OPEN`、`CONSUMER_CONTEXT_SUBMIT`、`LEGAL_ACCEPTANCE`、`PAYMENT_START`。
- `uk_network_evidence_request(checkout_session_id,request_id,evidence_scene)` 保证同一次入口请求不重复写证据。
- 完整 IP、User-Agent 和可信代理链只保存到 `raw_evidence_vault_token` 指向的 Vault Payload。
- IP 与 IP Network Blind Index 必填；IP Network 使用版本化的 IPv4/IPv6 网络归一化规则。Device Blind Index 只有在获准的稳定设备标识存在时才保存，不能用不透明浏览器指纹算法强行补值。
- Geo、ASN、Network Type、代理/VPN/Tor/Hosting 以及 UA 解析结果是采集时已知的不可变派生事实；无法可靠判断时允许为空，四个 Flag 的 `1/0/NULL` 分别表示命中、明确未命中、没有判断结果。
- `device_type` 固定为 `DESKTOP`、`MOBILE`、`TABLET`、`BOT`、`OTHER`、`UNKNOWN`。
- Evidence 插入后不更新；更晚的供应商再判定属于 Risk 自己的新 Fact，不回写 Commerce 历史证据。
- 本表自身就是不可变领域证据，不进入 BCR。

主要访问路径：

- Order 时间线：`idx_network_order_time(order_id,occurred_at)`；
- 相同 IP：`idx_network_ip_blind(ip_blind_index_key_version,ip_blind_index,occurred_at)`；
- 相同网段：`idx_network_range_blind(ip_network_blind_key_ver,ip_network_blind_index,occurred_at)`；
- 相同设备：`idx_network_device_blind(device_blind_index_key_version,device_blind_index,occurred_at)`。

## 5. Acceptance 与 Payment Readiness

删除 `t_consumer_acceptance_summary`。三种必需类型固定且 `uk_consumer_acceptance(consumer_contract_id,acceptance_type)` 已阻止重复，因此 Readiness 可以对不可变事实做一次受索引的精确查询，不需要维护第二份状态机。

`t_consumer_acceptance` 精简为：

```text
consumer_acceptance_id
consumer_contract_id
network_evidence_id
acceptance_type
accepted_at
command_idempotency_id
create_time
```

约束：

- `acceptance_type` 固定为 `CONSUMER_TERMS`、`REFUND_POLICY`、`PRIVACY_NOTICE`。
- Network Evidence 必须属于该 Contract 所属 Order 的 Checkout Session，且 `evidence_scene=LEGAL_ACCEPTANCE`。
- `content_hash` 不再重复保存；通过 `consumer_contract.order_legal_id + acceptance_type` 精确定位三份不可变 Legal Content Hash。
- `checkout_session_id` 不再重复保存；通过 `network_evidence_id` 精确定位 Session。
- `request_hash` 不再重复保存；同一命令的请求完整性由 `t_command_idempotency` 独占。
- Acceptance 与已 Bind 的 Network Evidence 在同一本地事务插入，创建后不可修改，也不写 BCR。
- Network Payload Protect/Bind 复用本次 Acceptance 的稳定命令幂等键；Bind 后在本地事务前崩溃时由命令恢复重放同一 Token/Binding 并补齐 Evidence + Acceptance，明确终止时 Retire 未引用 Binding。

`COM-I10 GetPaymentReadiness` 的服务端判定固定为：

1. Order/Order Context/Checkout Session/Consumer Contract 属于同一 Order；
2. Order 与 Session 当前允许创建 Attempt，且尚未过期；
3. 同一 Contract 恰好存在三种必需 Acceptance；
4. 每条 Acceptance 的 Network Evidence 都满足同 Order、同 Session 和 `LEGAL_ACCEPTANCE` 约束。

成功响应返回 `consumer_contract_id`、三种显式 Acceptance ID、`acceptance_completed_at=max(accepted_at)`、Order Version 与 Expires At。Payment Attempt 用三个显式 ID 替换原 `acceptance_summary_id/version/set_hash`，不新增另一种集合 Hash。

## 6. Risk 与分析使用方式

- Create Order Checkout Assessment 发生在消费者交互之前，不要求不存在的网络证据；它只使用当时已经提供的最小 Consumer/Device 输入。
- Payment Attempt Risk 使用最新 Consumer Context 和 `PAYMENT_START` Network Evidence，并可按策略同时引用同 Session 的 Acceptance Evidence。
- 跨系统契约显式传递 Consumer Ref/Email/Phone/IP/IP Network/Device Blind Index 及各自 Key Version，以及 Email Domain、电话国家、账单地域、IP 地域/ASN/网络类型、代理标记和设备解析维度。所传 Index 必须由 Vault 按已批准的 Commerce→Risk 共享分析 Purpose 生成；Risk 只能在该 Purpose 内关联，不得把它再用于身份认证、营销或其他用途。
- Risk 把这些值写入自己的不可变 Assessment Input、Feature Value 或类型化 `risk_fact_projection`；不保存原始 Email、Phone、地址、IP 或 User-Agent，也不回查 Commerce。
- OLTP 索引只服务在线等值与时间窗口查询。未来数仓通过经过治理的事件按 Order/Intent/Attempt/Event ID 汇聚，不允许分析人员 Reveal 原值或直接跨库查询。

可支持的当前特征包括：同 Email/Phone/IP/网段/设备的订单或尝试频次、Email Domain 风险、账单国家与 IP 国家不一致、代理/VPN/Tor/Hosting、ASN/网络类型集中度，以及设备/浏览器异常切换。

## 7. 模型结果

本次仍保持 23 张表：删除 1 张 Acceptance Summary，新增 1 张 Checkout Network Evidence。落模后的精确统计为 338 个字段、37 个唯一索引、22 个普通索引、26 条本库关系和 6 个关系图；11 张表保留业务乐观锁版本，10 张专项事实表创建后不可变。模型文件 SHA-256 为 `1af0a85620dd8b2ce99bf4553d0eca4c04926a5aee18970835194e9983eb97f3`，结构 SHA-256 为 `a4dd7fe478caa4a351962cc0d2fab58432687e08e41407b47375b10ad1093cd3`。

确定性生成器与自动契约测试已经通过模型级检查；PDManer 4.9.3 应用内打开与检查仍是人工门禁。

本设计不授权 Migration、Java 实现、物理数据库、Vault 服务实现或运行环境。

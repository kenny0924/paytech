# Commerce 隐私与履约精简设计

> 状态：历史 17 表阶段。隐私、Network Evidence、Acceptance 与 Fulfillment 决策仍有效；物理数量、Consumer Document 与 Commerce BCR 归属已由[文档投影、审计与取消撤销精简设计](./2026-08-24-commerce-document-audit-cancel-reversal-design.md)覆盖，当前模型为 14 表。

> 退款域后续精简决定：本文件中与退款 Reservation、Reserve Change、Payment Projection、UNKNOWN/Release Pending 或 Finance Ack 相关的前序表述，继续由本次 Commerce Refund 精简覆盖；以文末第 4.1 节和目标基线为准。其余隐私、Network Evidence、Acceptance 与 Fulfillment 决策保持有效。

## 1. 决策

本设计固化 2026-08-24 已确认的四项 V1 决策：

1. Consumer 敏感值采用单轨稳定 Vault Token，不再同时保存 Blind Index、Key Version 或 Masked Value。
2. `consumer_reference` 是 Client 命名空间内的业务引用，直接结构化保存；Email、可选 Phone 分别保存稳定 `*_vault_token`；姓名与完整账单地址使用独立 `consumer_profile_vault_token`，不得与 Email/Phone 重复保存。
3. Checkout 网络证据不是本项目的加密字段：Commerce 直接保存可信解析后的完整客户端 IP、完整 User-Agent、可选可信代理链及结构化风险维度；不保存 Network Vault Token、Blind Index、Key Version 或设备标识。
4. Consumer Contract 与 Acceptance 继续保持一对多。每个 Contract 必须恰有 `CONSUMER_TERMS`、`REFUND_POLICY`、`PRIVACY_NOTICE` 三条不可变 Acceptance。
5. V1 不建设主动履约模块。Chargeback 后由 Risk 将 Dispute Case 置为 `EVIDENCE_REQUIRED`，向 Client 发出 `DisputeEvidenceRequested`，并通过既有 Evidence Package/Reference 收集材料。

## 2. Commerce 物理模型

### 2.1 Consumer Context

`t_consumer_context` 精确保留：

- `consumer_context_id`
- `order_id`
- `consumer_reference`
- `email_vault_token`
- `phone_vault_token`
- `consumer_profile_vault_token`
- `email_domain`
- `phone_country_code`
- `billing_country_code`
- `billing_region_code`
- `billing_city`
- `billing_postal_prefix`
- `version`
- `create_time`
- `update_time`

同一规范化 Email/Phone 在平台内部同字段作用域返回稳定 Token。Token 同时服务相等判断、统计关联和授权 Reveal；Vault 内部密钥轮换不得改变业务 Token。Token 不得出现在外部响应、普通日志、MQ 或通用审计 JSON 中。

### 2.2 Checkout Network Evidence

删除 Network Vault/Blind/Device 字段，新增：

- `client_ip_address VARCHAR(45) NOT NULL`
- `user_agent VARCHAR(1024) NOT NULL`
- `trusted_proxy_chain VARCHAR(2048) NULL`

保留 Geo、ASN、Network Type、Proxy/VPN/Tor/Hosting、Browser、OS 和 `device_type` 解析维度。V1 不采集 `device_identifier`。`idx_network_client_ip(client_ip_address,occurred_at)` 服务受控频次分析。

### 2.3 Acceptance

`t_consumer_contract 1:N t_consumer_acceptance` 不变，继续使用 `uk_consumer_acceptance(consumer_contract_id,acceptance_type)`。

### 2.4 删除 Fulfillment

删除：

- `t_fulfillment`
- `t_fulfillment_confirmation`
- `t_fulfillment_evidence`
- 履约索引、关系、Diagram Group、BCR 类型与事件
- Order 的 `FULFILLMENT_PENDING/FULFILLED` 状态和 `fulfilled_at`
- Commerce 履约接口、Platform 履约 Webhook、Risk 履约查询及 Finance 履约成熟条件

支付成功后的 Order 保持 `PAID`；退款、争议与财务追偿使用各自权威状态，不通过 Order 履约状态表达。

## 3. Chargeback RFI

Risk 既有 `dispute_case`、`dispute_state_change`、`dispute_evidence_package`、`dispute_evidence_ref` 和 `dispute_submission_request` 承接按需举证，不新增通用 RFI 表。Dispute 建案后按原因进入 `EVIDENCE_REQUIRED` 并发布 `DisputeEvidenceRequested`；Client 截止时间必须早于 Provider 截止时间。Client 未及时响应时允许认损，普通非平台/Provider 错误的本金和渠道费用默认由 Client 承担。

Finance 不再把 Fulfillment 作为 `PENDING -> AVAILABLE` 条件，改由 Provider/Bank Match、账期、Risk Restriction、Reserve Requirement 和对账策略控制；Chargeback 建案后冻结未结算余额或准备金，不足部分形成 Client Recovery Receivable。

## 4. 历史目标基线

以下数量只记录本阶段的 17 表结果，不是当前权威物理基线：

- 17 张表
- 266 个字段
- 29 个唯一索引
- 16 个普通索引
- 19 条本库关系
- 4 个 Diagram Groups
- 8 张业务 Version 表
- 7 张创建后不可变表
- 4 个 Commerce BCR Record Type

本设计只授权 PDManer、需求与文档更新，不授权 Java、Migration、物理数据库或部署环境。

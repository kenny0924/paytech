# acquiring V1 PDManer 物理建模决策（原 Commerce V1）

> **2026-10-18 拆分（V2 第 03 步）：** commerce-service 拆为 acquiring 与 consumer 两服务两库（[任务卡](../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/03-acquiring-consumer.md)、design/03 §2.5）。本模型 `acquiring.pdma.json` 只含 mor_acquiring 的 14 张表（transaction / checkout / command 共库 13 张与本库 `t_compensation_event`），由 `scripts/pdmaner/generate-acquiring-model.mjs` 生成；`t_customer` 归 [consumer 模型](../../../consumer/docs/database/CONSUMER_PHYSICAL_DECISIONS.md)，`t_client_stored_card_setting` 归 client-master（D42），`t_commerce_order.customer_id` 只是软引用，不建跨库关系。按 C7 / S10 删除 `t_command_idempotency` 与 `t_commerce_order.context_format`、`preparation_command_id`；`t_consumer_acceptance.command_idempotency_id` 按已执行 Schema 改为可空的历史引用列。下文涉及这三项与 15 表的段落为历史记录，以第 2 节 Manifest 与生成器为准。


> **2026-09-16 取消时间归属：** [取消修订](../../../../requirements/20260916/commerce-CancelOrder接口重构/technique.md)新增 t_commerce_order.cancelled_at DATETIME(6) NULL，订单取消不再依赖 Session 时间。Session 保留自身取消时间；Direct Pay 可没有 Session。人工增量 SQL 仅准备，未执行。

> **2026-09-16 创建方式：** [响应与模型修订](../../../../requirements/20260916/commerce-订单查询合并Checkout/response-shape-amendment.md)增加 t_commerce_order.creation_mode VARCHAR(32) NOT NULL DEFAULT 'HOSTED_CHECKOUT'，由服务端创建时固定；DIRECT_PAY 仅预留。物理版本字段与索引保持，增量 SQL 未执行。

> **2026-09-16：** [CreateCheckout 业务修订](../../../../requirements/20260916/commerce-CreateCheckout业务重构/technique.md)增加 Order 创建国家可空列，新写必须提供，NULL 仅保留历史兼容。CREATE_CHECKOUT 不再产生新命令记录，旧快照及其他操作不清理。

> **2026-09-11 模型切片：** 当前物理模型采用 [OE / Client / Pricing 四系统决定](../../../../agent-docs/decisions/2026-09-11-cct-commerce-oe-pricing-model.md)。本页历史正文中的数字 OE、Assignment、独立 Acceptance、Client 多 OE 合同与 Agreement-OE 结算条款由该决定精确取代；本页摘要描述当前模型产物，运行代码、SQL 执行及待迁移状态仅见 [当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)。Risk 本期不动。

最后更新：2026-09-05
状态：15 表 Commerce Checkout 首片物理模型已生成；自动契约验证结果以本次执行记录为准，PDManer 4.9.3 应用内打开与检查仍是人工兼容性门禁。

> **2026-09-05 横切规范更新：** 模型采用当前最小 `compensation_event`、无物理 CHECK/FK 的规则；Vault
> 真实消费者能力仍按[当前实现台账](../../../../agent-docs/CURRENT_IMPLEMENTATION.md)的 VAULT-01 门禁推进。不得恢复旧
> Source/Version/Schema/Hash 字段。

## 1. 范围与权威来源

本文件冻结 Commerce V1 从逻辑目录落到 PDManer 的物理类型、可空性、索引、本库关系、图组和数据库外约束。权威输入为：

- [Commerce 逻辑数据库模型](../../../../agent-docs/database/COMMERCE_DATABASE.md)
- [Commerce 现实场景](../../../../agent-docs/database/scenarios/COMMERCE_REAL_WORLD_SCENARIOS.md)
- [Commerce 订单上下文精简设计](../../../../agent-docs/plans/2026-08-24-commerce-order-context-simplification-design.md)
- [Commerce 隐私与履约精简设计](../../../../agent-docs/plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md)
- [Commerce 文档投影、审计与取消撤销精简设计](../../../../agent-docs/plans/2026-08-24-commerce-document-audit-cancel-reversal-design.md)
- [Commerce 引用型订单快照压缩设计（历史）](../../../../agent-docs/plans/2026-08-23-commerce-reference-snapshot-compression-design.md)
- [Commerce 平衡型 23 表设计（历史）](../../../../agent-docs/plans/2026-08-23-commerce-balanced-physical-model-design.md)
- [共享 PDManer 模板](../../../../agent-docs/templates/pdmaner/template.pdma.json)

本次授权止于 PDManer 设计产物、生成器、自动契约测试和人工 Schema SQL 文件；不授权手工应用 SQL、Java 模块、物理数据库、Nacos、MQ、Vault
环境或其他运行基础设施。项目不使用 Flyway、Liquibase 或应用启动时 Schema 迁移。

## 2. 已冻结物理 Manifest

下列数量和摘要直接来自本次已生成模型；`modelSha256` 和结构投影摘要均读取实际产物后回填。

<!-- ACQUIRING_PHYSICAL_MANIFEST_START -->
```json
{
  "modelTotals": {
    "tables": 16,
    "fields": 237,
    "uniqueIndexes": 23,
    "accessIndexes": 12,
    "relations": 15,
    "diagramGroups": 4
  },
  "templateSha256": "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540",
  "modelSha256": "6cae77e067f4bc5dd590f1b8f3546988faa5160174f103b999f3b0c7ddcd6eb1",
  "structuralProjectionSha256": "15ac80e63b9268b1c0ee094689c8c9a82ddd6aa1f9f7c11a8d534a86b4a7d794"
}
```
<!-- ACQUIRING_PHYSICAL_MANIFEST_END -->

第 08 步（消息拓扑接入）新增 paytech-messaging 消费端标准表 `t_consumed_event` 与 `t_consumed_aggregate_version`，列名、类型、
可空性、主键与 `idx_consumed_event_aggregate` 索引取自 `paytech/foundation/paytech-messaging/src/main/resources/schema/messaging-consumed-event.sql`
（design/08 §4）。二者归入 `可靠执行` Diagram Group；字符串列为 `ascii_bin`；`t_consumed_aggregate_version` 是本模型唯一的复合主键
（`aggregate_type, aggregate_id`），生成器以字段清单的 `PK` 标注表达；两表不含业务 `version`，不计入下表的审计时间与乐观锁统计。

四个 Diagram Group 为 `订单与不可变上下文`、`Checkout 与消费者法律`、`支付与退款`、`可靠执行`。不得恢复历史 `t_order_snapshot`、Tax/Term 本地副本、三张已删除退款表、`t_consumer_document`、Commerce BCR 或主动履约 Diagram/Relation Cell。

## 3. 模板派生与确定性

- 完整克隆 `agent-docs/templates/pdmaner/template.pdma.json`，保留模板顶层、Profile、DDL 模板、Domain、Naming Rule 和 UI 元数据，只定点替换 Commerce 模型内容。
- `dbConn`、`dbConns` 与 `profile.default.dbConn` 均清空；模型不保存主机、端口、用户名、密码或真实环境名。
- 实体、字段、索引、索引字段、Diagram 和 Diagram Cell ID 使用 `SHA-256(code-mor/commerce/database/v1:path)` 的确定性 UUID v5；相同输入必须逐字节生成相同 JSON。
- 序列化固定为两空格缩进和一个末尾换行；`--check` 只做逐字节校验且不写文件。

## 4. 通用物理规则

| 语义        | 决策                                                                                                                                                                                                                                                                                                                                      |
|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 物理命名      | 表使用 `t_` + 小写 `snake_case`；唯一索引使用 `uk_`，访问索引使用 `idx_`                                                                                                                                                                                                                                                                                   |
| 主键        | 每表一个 Snowflake `BIGINT` 主键，`autoIncrement=false`                                                                                                                                                                                                                                                                                        |
| 金额/比例     | `DECIMAL(24,8)` / `DECIMAL(18,10)`                                                                                                                                                                                                                                                                                                      |
| 时间/币种     | UTC `DATETIME(6)` / ISO 4217 `CHAR(3)`                                                                                                                                                                                                                                                                                                  |
| JSON/TEXT | JSON 仅用于 `command_idempotency.request_snapshot_json`（最多 16 KiB UTF-8）、`command_idempotency.response_body_json`（最多 4 KiB UTF-8）和 `compensation_event.payload_json`（最多 4 KiB UTF-8）；均使用受控 Object。Create Checkout Response 中的 Checkout URL 具有访问能力，只能用于原命令恢复/重放，禁止进入普通日志、MQ、Webhook 或通用审计；补偿 Payload 禁止 Vault Token。仅脱敏错误摘要使用 TEXT；不允许 BLOB |
| 审计时间      | 9 张可变业务/技术表使用 `create_time + update_time`；6 张不可变事实表仅 `create_time`                                                                                                                                                                                                                                                                      |
| 乐观锁       | 8 张业务表使用 `version`；`t_compensation_event` 仅使用技术 `lock_version`                                                                                                                                                                                                                                                                          |

7 张业务 `version` 白名单为：`t_commerce_order`、`t_order_payment_projection`、`t_order_funds_availability_projection`、
`t_order_refund_position`、`t_checkout_session`、`t_consumer_context`、`t_refund_request`（`t_command_idempotency` 已删除）。

6 张创建后不可变表为：`t_order_context`、`t_order_legal`、`t_order_item`、`t_consumer_contract`、`t_checkout_network_evidence`、`t_consumer_acceptance`。

## 5. 数据库外业务约束

### 5.1 Order、Checkout 与 Consumer

- `uk_order_client_order(client_id,client_order_id)` 永久阻止同一 Client 复用订单业务标识；它不是请求幂等键。OE CLOSED 后不得自动替换订单或跨 OE 重试，Client 只能用新的 `client_order_id` 创建新订单。
- Order 状态只允许 `CREATED`、`OPEN`、`PAID`、`CANCELLED`、`EXPIRED`；`paid_at` 只在支付成功后存在；`cancelled_at` 保存订单确定取消的原时刻，不从更新或终止请求时间推算，重复取消不改写。可空 `status_reason_code` 从 Commerce Order Status Reason Catalog 解释当前取消、过期或其他终态，不作为历史表替代品。模型不含 `fulfilled_at`、履约状态或履约表。
- `t_order_context` 冻结 CCT-I13 V5 返回的 `platform_service_charge_scope_id` 与 `platform_service_party_id`。前者定位 `PLATFORM_SERVICE_CHARGE` Scope，后者定位 Partner Agreement 唯一 `PLATFORM_SIGNATORY` Party；它们是外部稳定 ID，不建立本地或跨库外键。PARTNER 来源时，它们与 `t_commerce_order.local_partner_id`、`partner_share_scope_id` 四项成组非空；非 PARTNER 来源四项成组为空。
- 2026-10-24 商业关系快照：`t_order_relationship_binding` 按 `uk_order_relationship_role(order_id, role_code)` 逐角色冻结 commercial 返回的快照，与 `t_order_context` 同一事务写入、之后不可变；`role_code` / `subject_type` 保存 commercial 发布的稳定 Code（字符串列，写入前校验取值），`party_id` 是 client-master Party 外部稳定 ID，不建跨库外键。`t_order_context` 的 `fund_flow`、`snapshot_model_version` 在固定 Mock 解析或 commercial 未返回快照时为 NULL，`effective_relationship_model_version` 此时及存量订单为 0。经营主体来源只保留 `CLIENT` / `PARTNER`（删除 `PLATFORM`）。
- `t_consumer_context` 保存 `consumer_reference`、Email/Phone/Profile 三个稳定 Token 及批准的 Email/Phone/Billing 派生维度。`consumer_reference` 是可空的 Client 命名空间非 PII 不透明业务引用（当前 Customer API 不提供该字段时保持 NULL，不生成占位身份），禁止复用 Email、Phone、证件号或其他个人原值。Profile Token 只保存姓名和完整账单地址，不重复 Email/Phone。
- `t_checkout_session` 以 ASCII binary `public_token` 和冻结的 `public_origin` 生成 Hosted Checkout URL；不保存 Public
  Token Hash、Vault Token 或 KMS 引用。
- `t_order_funds_availability_projection` 与 Order 一对一，Finance 事件分别以 Economics 和 Maturity
  的唯一引用、事件键、版本与载荷哈希推进；Commerce 不按本机时钟宣布成熟。
- Consumer 模型不含 Blind Index、Key Version、Masked Value、旧 Composite Token、Vault Record Version 或 Metadata Hash。当前
  Vault V1 只有 Protect/Reveal，没有 Binding 或 Retire；Commerce 命令恢复复用首次 Protect 后已保存的
  Token，引用替换或解除须由未来真实消费者驱动窄 V2 设计。
- `t_checkout_network_evidence` 直接保存 `client_ip_address`、`user_agent`、可选 `trusted_proxy_chain` 和不可变 Geo/ASN/Network/Proxy/VPN/Tor/Hosting/Browser/OS/device_type 维度。它不保存 Network Vault Token、Blind Index、Key Version 或 `device_identifier`；`idx_network_client_ip(client_ip_address,occurred_at)` 仅服务受控频次/取证分析。
- Acceptance 与 `LEGAL_ACCEPTANCE` Evidence 在同一本地事务插入。COM-I10 实时校验同 Order/Session 的三个显式不可变 `CONSUMER_TERMS`、`REFUND_POLICY`、`PRIVACY_NOTICE`，不维护 Acceptance Summary。

### 5.2 退款、取消撤销、文档、RFI 与可靠执行

- Commerce 退款域只保留 `t_order_refund_position` 与 `t_refund_request`。CLIENT 发起时 `client_refund_id` 必填并在 Client 命名空间内唯一；OPERATIONS 发起时该字段为空并依靠 Command Idempotency Key。Refund Request 使用 `refund_amount`，状态严格为 `PENDING`、`PROCESSING`、`SUCCEEDED`、`FAILED`、`CANCELLED`；本地行只保存 Payment `channel_refund_id` 这一可空且唯一的稳定下游 ID。Commerce 本地预占成功后直接调用 PAY-I04；Payment `UNKNOWN` 只保留在 Payment 本地并映射 Commerce 为 `PROCESSING`，成功同时通知 Commerce 与 Finance，失败/取消只通知 Commerce。
- Cancel 只关闭没有成功 Authorization/Capture/Sale 且没有已提交或 UNKNOWN 渠道结果的订单。授权成功未 Capture 时 Reversal 解析为 Payment Void；SALE/全额 Capture 后解析为标准全额 Refund。Cancel 不自动升级，Captured Reversal 后 Order 保持 `PAID`，部分 Capture 自动 Reversal 在 V1 不支持。
- Commerce 不保存 Consumer Document Current Projection。Receipt/Credit Note 的状态、版本、文件与更正引用由 Platform Support 保存，Finance 保存不可变 Fiscal Fact；Commerce 通过 `t_compensation_event` 可靠发布最小生成来源事实。
- Commerce 不建设主动履约域：没有 `t_fulfillment`、`t_fulfillment_confirmation`、`t_fulfillment_evidence`、履约事件或履约 Diagram Group。Chargeback RFI/举证由 Risk 的 Dispute Case、Evidence Package/Reference 和 Submission Request 承接。
- `t_command_idempotency` 同 caller/operation/key 唯一，同 Key 不同 Request Hash 冲突；`process_status` 固定为
  `PROCESSING/SUCCEEDED/FAILED`，`recovery_stage` 使用三个操作阶段的无重复闭合集合。`request_snapshot_json` 最多 16 KiB
  UTF-8，`response_body_json` 最多 4 KiB UTF-8；含 Checkout URL 的回执具有访问能力，只用于原命令恢复/重放，禁止进入普通日志、MQ、Webhook
  或通用审计。`t_compensation_event`
  与业务变化同事务写入，提交后执行；以 `(event_key,event_type,sub_event_type)` 唯一，并使用 Claim/Lease/
  `lock_version` 保证多实例互斥。固定扫描索引为 `(execute_status,next_execute_at,id)` 与
  `(execute_status,lock_until,id)`；`execute_status='INIT'`、`execute_count=0`、`next_execute_at=CURRENT_TIMESTAMP(6)`、
  `lock_version=1`、`create_time/update_time=CURRENT_TIMESTAMP(6)` 是安全默认值。可选 `payload_json` 最大 4 KiB
  UTF-8，只保存无法从本库权威事实恢复的最小非敏感 JSON Object，禁止 Vault Token。

## 6. 审计取舍、索引、关系与安全

- Commerce V1 删除 `t_business_change_record` 与 `t_business_change_version`，不以通用 JSON 复制主表历史。结构化 Current、冻结上下文和专项追加证据分别承担在线状态与必要留存；未来只有出现明确监管用途时才重新评估具体审计模型。
- 24 个非主键唯一索引、12 个访问索引和 15 条本库逻辑 Correlation 全部显式声明，但不生成物理 FK。Owner 写入口在同一本地事务中锁定实际
  Root、校验完整 Tuple，并用唯一索引/CAS 防并发错配。Vault、CCT、Risk、Finance、Payment、Platform、File 和 MQ 仅由逻辑 ID、Token 或
  Event 字段关联，绝不建立跨库外键。
- Consumer Token、IP、User-Agent、可信代理链和派生维度均是受限数据，不得进入通用审计 JSON、普通日志、MQ 或普通业务页面；最小权限、Purpose、访问审计和留存治理是未来实现门禁。
- PAN、CVV/CVC、完整磁道、PIN/PIN Block、Vault 密文和 Secrets Manager 凭据不得进入模型。

## 7. 实施门禁

PDManer 模型是当前设计源，审核后的当前 Schema SQL 是可执行数据库定义。未来获准数据库实现时，必须在同一变更保持
PDManer、经人工评审并手工执行的完整 Schema SQL、DO/Mapper/Repository 和仓库自有 `local-environment` 真实 MySQL `auto`
测试一致；禁止 Flyway、Liquibase、Testcontainers 或应用启动时 Schema/初始化数据迁移。本文件不授权这些工件或运行环境。

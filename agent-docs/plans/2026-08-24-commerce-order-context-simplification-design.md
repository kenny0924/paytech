# Commerce 订单上下文精简设计

最后更新：2026-08-24  
状态：用户已确认；订单结构决策继续有效，全库统计与消费者/网络/履约边界已同步到后续 [Commerce 隐私与履约精简设计](./2026-08-24-commerce-privacy-fulfillment-simplification-design.md)

## 1. 决策结论

Commerce 不再为一笔 Order 另造一份包含金额、分类、Legal 和 Item 的聚合快照及自有 Hash。当前模型收敛为：

```text
t_commerce_order      订单身份 + 冻结 OE/来源 + Industry/MCC + 订单级金额 + 生命周期
  |-- 1:1 t_order_context  交易时点 + CCT/Risk/Finance 不可变外部证据引用
  |       `-- 1:1 t_order_legal  Agreement Association + Legal 文件/Hash + Seller 展示值
  `-- 1:N t_order_item     商品服务与逐行金额
```

这个设计保留了 Checkout 必须的本地自包含性，同时删除了一对一快照与 Order 的重复字段、Schema 版本和 Commerce 自建聚合 Hash。

## 2. 精确物理目标

全库统计固定为：

| 项目 | 数量 |
|---|---:|
| 表 | 20 |
| 字段 | 302 |
| 非主键唯一索引 | 34 |
| 访问索引 | 16 |
| 本库关系 | 22 |
| Diagram Group | 5 |

### 2.1 `t_commerce_order`：22 字段

```text
order_id
order_no
client_id
client_order_id
operating_entity_id
entity_source_type
local_partner_id
industry_code
mcc_code
merchandise_amount
discount_amount
net_amount
tax_amount
rounding_amount
total_amount
currency
order_status
expires_at
paid_at
version
create_time
update_time
```

创建后冻结 `client_id + client_order_id`、OE/来源、Industry/MCC、全部金额币种和 `expires_at`。Order 状态、`paid_at` 与 `version` 按状态机更新。V1 不建设主动履约域，Order 支付成功后保持 `PAID`，不保存 `fulfilled_at` 或履约状态。

唯一约束为 `uk_order_client_order(client_id,client_order_id)`。它永久阻止同一 Client 复用订单业务标识，但不是请求幂等机制：

- 相同 CreateOrder 命令重试必须复用原 `idempotency_key`；
- `t_command_idempotency.request_hash` 只判定同一幂等键下的运输重放是否同内容；
- 换新幂等键却复用已存在 `client_order_id` 时，不创建第二笔 Order，返回 `CLIENT_ORDER_ID_CONFLICT`，可附已存在 `order_no`；
- 不在 Order 保存 `creation_request_hash`，也不通过一个历史 Hash 猜测不同命令是否等价。

### 2.2 `t_order_context`：13 字段

```text
order_context_id
order_id
commercial_context_time
assignment_id
cct_commercial_context_hash
client_pricing_scope_id
partner_share_scope_id
risk_checkout_assessment_id
finance_tax_snapshot_id
finance_tax_snapshot_hash
consumer_country_code
locale
create_time
```

`uk_order_context(order_id)` 保证每 Order 一行。Context 一次写入后不更新，只保存不属于 Order 主表的外部证据坐标；不保存 Industry/MCC、金额币种或订单状态。

`commercial_context_time` 是业务时点，不是记录创建时间：

1. 首次 CreateOrder 先冻结 `command_idempotency.operation_context_time`；
2. 同一精确值作为 CCT-I13 `context_as_of_time`；
3. CCT/Risk/Finance 前置全部成功后，该值原样写入 `order_context.commercial_context_time`；
4. 命令恢复与重放必须复用，禁止重新取 `now()`；
5. Finance 后续调用 CCT-I14 时把它映射回 `context_as_of_time`，以精确读取原 Scope 闭包。

CCT V4 `cct_commercial_context_hash` 和 Finance `finance_tax_snapshot_hash` 仍保留：它们由各自事实所有者按已批准的不可变契约生成，不是 Commerce 对 Order 聚合重复做 Hash。

### 2.3 `t_order_legal` 与 `t_order_item`

- `t_order_legal.order_context_id` 按 `uk_order_legal(order_context_id)` 一对一归属 Context；Legal 通过 Context 获得同次 CCT 证据语义。
- `t_order_item.order_id` 直接归属 Order，`uk_order_item(order_id,client_item_id)` 保证同 Order 的 Client Item ID 唯一。
- Legal 与 Item 仍然本地自包含、创建后不可变；不因删除 Snapshot Root 而合并成宽表或 JSON。

## 3. 删除与保留边界

当前模型删除：

- `t_order_snapshot`；
- `order_snapshot_id`、`snapshot_schema_version`、`snapshot_hash`、`cct_context_as_of_time`；
- `t_commerce_order.creation_request_hash`；
- `external_order_id`，用业务语义更准确的 `client_order_id` 取代。

继续保留：

- Finance 自有 `tax_snapshot.calculation_hash` 的 Commerce 精确副本 `finance_tax_snapshot_hash`；
- CCT-I13 V4 Bundle 的 `cct_commercial_context_hash`，仅作原子响应审计关联，不作单个 Scope 的 CCT-I14 校验参数；
- Legal 文件内容 Hash、事件 Payload Hash、文档内容 Hash 等各自具有实际消费者的完整性字段。

## 4. 跨系统契约

### 4.1 Payment

Commerce 以 `order_id + order_context_id` 创建唯一 Payment Intent，同时显式传递并由 Payment 冻结：Client、Operating Entity、Assignment、Industry/MCC、金额币种、到期时点和必要外部证据 ID。Payment 不再保存或比对 Commerce Snapshot ID/Schema/Hash。

### 4.2 Risk

Checkout Risk 评估使用 Commerce 在内存构建的 `OrderPreparationContext`。Risk 保存自有 Input Snapshot/Hash 和不可变 Assessment；Commerce 只冻结实际采用的 `ALLOW` Assessment ID，不需要先生成 Commerce Order Hash。

### 4.3 Finance 与 CCT

Finance 处理 `PaymentSucceeded` 前，必须把 Payment 与 Commerce 返回的 `order_id + order_context_id`、Client/Partner/OE、Assignment、Industry/MCC、金额币种、Tax ID/Hash 逐值核对。然后以：

```text
GetImmutableCommercialTermScope(
  term_scope_id,
  agreement_operating_entity_id,
  context_as_of_time = order_context.commercial_context_time
)
```

读取准确历史 Scope 闭包。暂时网络错误不 ACK 原 `PaymentSucceeded`，由 Broker 以同一事件重投；Not Found、Owner/OE/Association/Scope 关系不一致或对象损坏是永久完整性错误，告警并进入 DLQ，禁止使用当前、默认或零费率。

## 5. 支付重试与 Operating Entity 失效

### 5.1 临时 Provider/MID 问题

临时 Provider/MID 短缺、路由失败或一次 Attempt 明确失败时，Payment 可在同一 Order、同一 Intent、同一 Operating Entity 下新建 Attempt。Provider 和 MID 可变，Order 已冻结的 OE 不可变。`UNKNOWN` 仍是活动状态，未收敛前不能开启竞争 Attempt。

### 5.2 OE 永久不可用

Commerce 与 Payment 消费 CCT `OperatingEntityChanged`。当 `operating_entity_status=CLOSED`，事件同时携带 `reason_code + source_version` 时，按下列顺序 Fail Closed：

1. 阻止该 OE 下新 Attempt；
2. 若有活动或 `UNKNOWN` Attempt，等待渠道查证并收敛，禁止强制跨过；
3. 若在途 Attempt 最终成功，按正常支付成功流程处理，不取消 Order；若它收敛为非成功终态，则在确认无活动/`UNKNOWN` Attempt 后，Commerce 使用 Payment Intent 的 Expected Version 执行永久终止；
4. Intent 终止成功后，取消尚未支付的 Order 和 Checkout Session；
5. Client 如仍需交易，必须用新的 `client_order_id` 发起新 CreateOrder。

系统不自动创建替换订单，不为新旧 Order 增加替换关系，也不在原 Order 上跨 OE 重试。`SUSPENDED` 的在途处置不在本轮扩展。

## 6. 明确不做

- 不自动替换 Order，不增加 `replaces_order_id/replaced_by_order_id`；
- 不让 Client 选择 Operating Entity、Provider 或 MID；
- 不恢复 Commerce 聚合 Snapshot Schema/Hash；
- 不建立跨库外键、跨库查询或分布式事务；
- 不从当前 CCT/Finance 状态重构历史交易事实；
- 本决策不授权 Migration、Java 模块、物理数据库或运行环境。

## 7. 验收标准

- PDManer 精确为 20 表、302 字段、34 个唯一索引、16 个访问索引、22 条本库关系和 5 个 Diagram Group；
- `t_order_snapshot`、`external_order_id`、`creation_request_hash`、`snapshot_schema_version`、`snapshot_hash` 不出现在当前物理模型；
- `t_commerce_order`、`t_order_context`、`t_order_legal`、`t_order_item` 的字段、索引和 1:1/1:N 关系与本文一致；
- 需求目录、全局时序、Commerce/Payment/Risk/Finance/Platform 契约和现实场景对 `order_id + order_context_id` 使用同一语义；
- 生成器 `--check`、模型契约测试、场景文档测试和 requirements-site 测试全部通过；
- PDManer 4.9.3 应用内打开/检查继续作为人工兼容门禁。

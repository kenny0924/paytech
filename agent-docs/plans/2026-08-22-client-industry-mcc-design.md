# Client 行业与 MCC 交易分类设计

> **Commerce 后续决策：** 行业与 MCC 双字段结论仍有效，但本文中的 `creation_request_hash`、Order Snapshot Hash 与 Payment `source_snapshot_hash` 已被 [2026-08-24 Commerce 订单上下文精简设计](./2026-08-24-commerce-order-context-simplification-design.md) 取代。

> **历史阶段提示：** 本文保留该阶段 55 表 / 620 字段基线、测试结果、Digest 与决策原文；当前基线已进一步收敛为 [44 表 Commercial Term 设计](./2026-08-23-cct-commercial-term-consolidation-design.md)。不得据此恢复旧 Scope/Hash 或 Agreement/Legal 结构。

最后更新：2026-08-22
状态：已实施并通过自动验证
人工门禁：PDManer 4.9.3 应用内检查待人工确认

## 1. 已确认结论

- `industry_code` 与 `mcc_code` 是两个不同维度，不能合并或互相推导：
  - `industry_code` 是 Code-MoR 平台维护的业务行业分类；
  - `mcc_code` 是支付收单语义下的四位 Merchant Category Code。
- Client Business Profile 可以登记多组已批准的 `industry_code + mcc_code`；一组行业可以对应多个 MCC，一个 MCC 也可能被多个平台行业使用。
- 一笔 Order 只允许且必须提交一组 `industry_code + mcc_code`，这是不可回退的已确认决策，不是待复核项。当前不设计一笔订单包含多组分类；未来多商户或多 MCC 场景必须拆单或重新审批设计。
- Client 创建 Order 时直接提交 `industry_code + mcc_code`，不提交 `classification_id`。
- Client 提交的是声明值，不是可信授权事实。Commerce 必须通过 CCT 同步接口验证该组合属于 Client 当前有效 Business Profile，验证失败时不得创建 Order。
- Client 仍不能提交或选择 Operating Entity、Provider 或 MID。CCT 使用已批准交易分类解析 Operating Entity；Payment 只能在该主体下按 MCC 选择 MID。
- Order 创建后冻结两项代码，后续 Profile、行业目录或 MCC 配置变化不得改写历史 Order、Payment、Risk 或 Finance 事实。

## 2. Client & Contract 模型

### 2.1 业务分类表

将 `client_business_activity` 更名为 `client_business_classification`，结构收敛为：

```text
client_business_classification
- classification_id
- business_profile_id
- industry_code
- mcc_code
- primary_slot
- create_time
- update_time
```

约束：

- `uk_biz_class_pair(business_profile_id, industry_code, mcc_code)`：同一 Business Profile 不得重复登记同一组合；
- `uk_biz_class_primary(business_profile_id, primary_slot)`：一个 Business Profile 最多一组主要分类；主要分类 `primary_slot=1`，其他为 `NULL`；
- `industry_code` 取值来源为平台行业目录，是开放目录代码，不在数据库注释中伪造闭合枚举；
- `mcc_code` 必须是四位数字字符串，取值还必须存在于平台采用的收单/MCC 目录；保留前导零，不使用整数类型；
- 删除 `classification_scheme`、`classification_code`、`activity_id`，不保留双写兼容字段；
- Business Profile 封存时，Hash Schema V2 的 `content_hash` 必须包含按 `industry_code,mcc_code` 稳定排序后的完整分类集合；每项分别纳入两个代码及 `primary_slot`。既有 Profile 保持其原 Hash Schema Version 和 `content_hash`，不因该输入形状升级而重算。

`classification_id` 只用于 CCT 内部关系和跨系统审计证明。它可以由 CCT-I13 返回，但不能替代创建订单请求中的两个业务代码。

### 2.2 Scope 统一

CCT 现有 `activity_code` 或 `scope_activity_code` 不再保留模糊语义。下列 Scope/Rule 均改为显式 `industry_code + mcc_code`：

- `client_entity_affiliation`；
- `client_entity_assignment`；
- `entity_assignment_rule`；
- `agreement_scope_binding`；
- `client_pricing_scope`；
- `partner_share_scope`。

前三张 Assignment Scope/Rule 必须使用准确值，不允许 `NULL/*/GLOBAL`，也不保存 Assignment `scope_hash/content_hash/hash_schema_version`。后三张商业配置 Scope 才允许每个维度使用经过批准的 Canonical `*` 通配值，并把两个维度分别纳入 `scope_hash` 与父级 `content_hash`。Business Profile 分类、Order 与 CCT-I13 输入/Proof 始终不允许 `*`，不能只 Hash 拼接后的自由字符串。

### 2.3 CCT-I13 验证与证明

Commerce 调用 CCT-I13 时精确传入 `client_id`、`country_code`、`currency`、`locale`、准确的 `industry_code + mcc_code` 以及唯一 `context_as_of_time`；分类代码不得为 `*`，`mcc_code` 必须为四位数字字符串。CCT 必须在同一个只读一致性事务和 `context_as_of_time` 内完成：

1. 验证 Client 当前有效 Business Profile 中存在该准确组合；
2. 返回 `business_profile_id + version_no + content_hash`；
3. 返回命中的 `classification_id + industry_code + mcc_code`；
4. 使用同一组合只读解析已经 ACTIVE 的 Assignment，并返回 `assignment_id/rule_id/affiliation_id/entity_source_relation_id/source_type/operating_entity_id` 与准确 Scope；随后解析 Client/Partner Agreement、Pricing、Partner Share、Eligibility 和 Consumer Legal；
5. 把 Business Profile 与分类证明纳入 CCT-I13 返回的 `commercial_context_hash`；Commerce 将其连同 Snapshot 输入保存为 `cct_commercial_context_hash`，二者不混用。

任一组合不存在、Profile 非有效、Scope 无唯一结果、MCC 格式非法、输入含 `*`、任一 Proof 的代码/ID/Version/Hash 不一致、Eligibility 非 READY 或一致性读取失败时，整个 Bundle Fail Closed。Commerce 只冻结同一次返回的 Profile/Classification Proof 与 `commercial_context_hash`；Payment、Risk 与 Finance 只能从不可变 Order Snapshot 继承同一组合。

## 3. Order API 与 Commerce

创建 Order 的外部请求直接包含：

```json
{
  "industry_code": "DIGITAL_SERVICES",
  "mcc_code": "5817"
}
```

两项都必填、都参与 Canonical `creation_request_hash`。同一幂等键提交不同分类组合必须返回冲突。

`order_snapshot` 冻结以下结构化字段：

- `business_profile_id`；
- `business_profile_version_no`；
- `business_profile_content_hash`；
- `business_classification_id`；
- `industry_code`；
- `mcc_code`。

这些字段全部参与 `cct_commercial_context_hash` 和 `snapshot_hash`。统计必须读取订单时点快照，不能回查 Client 当前 Profile 后改写历史分类。

一笔 Order 的所有 Item 共用这一组交易分类。`product_type_code` 和 Finance `tax_category_code` 仍是商品/税务维度，不得当作行业或 MCC。

## 4. Payment Orchestration

- `payment_intent` 从 Commerce 不可变 Order Snapshot 复制 `industry_code + mcc_code`，并校验它们被 `source_snapshot_hash` 覆盖；Intent 创建后不可修改。
- `routing_policy_binding` 和 `routing_coverage` 使用显式 `industry_code + mcc_code`，不再使用 `activity_code`。
- `routing_decision.input_hash` 必须覆盖 Intent 冻结的两个代码。
- `mid_capability.mcc_code` 继续表示 MID 获准处理的 MCC；候选 MID 必须匹配本次 Intent 的 MCC 或批准的配置通配规则。
- `industry_code` 用于平台策略、路由分析和统计；它不替代 MID 的 `mcc_code` 能力约束。
- Attempt 重试继承同一 Intent 分类，不允许通过新 Attempt 切换行业或 MCC。需要改变分类时必须创建新 Order。

## 5. Risk & Compliance

- `policy_scope.activity_code` 改为 `industry_code + mcc_code`；
- `business_acceptance_rule.classification_scheme/classification_code` 改为 `industry_code + mcc_code`；
- `checkout_assessment` 和 `transaction_assessment` 结构化冻结两个代码，便于风险批准率、拒绝率和规则命中统计；
- `checkout_input_snapshot`、`assessment_input_snapshot`、Rule/Feature 输入 Hash 均覆盖两个代码；
- `risk_fact_projection` 对交易事实保存两个结构化维度，供受控 SQL/Script Feature 和历史窗口统计使用；
- 风险规则不得从 Item 名称猜测 MCC，也不得把技术失败记成行业 DENY。

## 6. Finance

- `economics_snapshot` 冻结 `industry_code + mcc_code`，并纳入 `calculation_hash`；PAYMENT、REFUND、DISPUTE、COST_ADJUSTMENT 都继承原交易分类，不重新读取 Client 当前 Profile。
- `EconomicsCalculated` 事件携带两个代码，使收入、Client Fee、Partner Share、Provider Cost 与利润可以按订单时点行业和 MCC 统计。
- Finance `tax_category` 继续只表示税务处理分类，不等同于 `industry_code` 或 `mcc_code`。

## 7. 统计口径

| 统计问题 | 权威订单时点维度 |
|---|---|
| 下单量、GMV、退款率 | Commerce `order_snapshot.industry_code/mcc_code` |
| 支付成功率、路由和 MID 表现 | Payment `payment_intent.industry_code/mcc_code` |
| 风控通过率、拒绝率、规则命中 | Risk Assessment/Fact 的 `industry_code/mcc_code` |
| 收入、成本、分润和利润 | Finance `economics_snapshot.industry_code/mcc_code` |

各系统只统计自己拥有的事实；未来数据仓库按稳定 Order/Intent/Event ID 汇聚，不能用跨库 OLTP 查询代替数据同步。

## 8. 接口与失败规则

- 创建订单请求缺少任一代码：参数错误；
- MCC 不是四位数字字符串：参数错误；
- 组合不属于 Client 当前有效 Business Profile：业务拒绝；
- CCT 证明缺失、过期、冲突或调用失败：技术 Fail Closed，不创建 Order；
- 已存在相同幂等键但代码组合不同：幂等冲突；
- Client 请求中出现 `classification_id`、Operating Entity、Provider 或 MID 选择字段：不接受为路由依据。

## 9. 本次不做

- 一笔 Order 多组行业/MCC；
- Item 级 MCC；
- 自动根据商品描述猜测或改写 MCC；
- Client 自由填写未审批行业/MCC；
- 多商户 Marketplace 拆分、跨 MCC 拆单或 Direct Pay；
- 新增 Analytics 核心系统或跨库统计查询；
- 为 Commerce、Payment、Risk、Finance 提前生成 PDManer、Migration 或 Java 实现。

## 10. 落模影响

- 业务分类表仍是一张，不新增映射表；后续 Assignment 四表精简使 Client & Contract 当前模型收敛为 51 表。
- CCT 的六个当前交易 Scope/Rule 均将模糊活动字段拆为两个明确字段；Assignment 三表使用准确值，三张商业 Scope 保留受控通配。当前 Catalog 为 51 表、576 字段、78 个唯一索引、59 个访问索引、78 个本库关系和 6 个 Diagram Groups，生成器验证已完成并通过。
- Client PDManer、生成器、字段元数据、模型测试、逻辑 Catalog、物理决策、导航和需求目录必须在同一变更同步。
- 除当前获准落模的 Client & Contract 外，其他五套系统的物理模型仍未获授权，继续按既定顺序等待单独授权。

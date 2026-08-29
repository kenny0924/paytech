# Commerce 引用型订单快照压缩设计（历史）

最后更新：2026-08-24  
状态：**已被 [2026-08-24 Commerce 订单上下文精简设计](./2026-08-24-commerce-order-context-simplification-design.md) 取代，仅作历史决策记录，不得作为当前建模或实现依据。**

> 当前模型已删除 `t_order_snapshot`、`snapshot_schema_version`、`snapshot_hash` 和 Order 上的 `creation_request_hash`，并把 `external_order_id` 更名为 `client_order_id`。以下内容完整保留用于说明上一版方案为何曾经存在；若与当前权威文档冲突，一律以 2026-08-24 设计为准。

## 1. 目标

在不削弱 Checkout 常用查询、消费者法律证据、订单金额和历史可追溯性的前提下，把 Commerce 的 Order Snapshot 从复制 CCT/Finance 完整内部事实，收敛为：

```text
Commerce 本地必须自包含的交易事实
  + 外部系统不可变事实的精确稳定引用
  + 各来源明确约定用途的完整性 Hash 或审计摘要
```

当前六张 Snapshot 表、123 个字段压缩为三张表、62 个字段：

| 表 | 当前字段 | 目标字段 | 决策 |
|---|---:|---:|---|
| `t_order_snapshot` | 30 | 24 | 保留订单核心事实、金额和最小跨系统引用 |
| `t_order_legal` | 25 | 21 | 保留 Checkout 必须自包含的三份 Legal 与 Seller 展示值 |
| `t_order_item` | 17 | 17 | 保留商品、税务输入和逐行金额 |
| `t_order_tax_line` | 19 | 0 | 删除；完整税务明细由 Finance Tax Snapshot 独占 |
| `t_order_commercial_term` | 18 | 0 | 删除；按 CCT Commercial Term Scope 精确引用 |
| `t_order_term_component` | 14 | 0 | 删除；组件随 Scope 在 CCT 长期不可变读取 |
| 合计 | **123** | **62** | 净减少 **61** 个字段 |

Commerce 全库由 26 表、390 字段压缩为 23 表、329 字段。删除六个唯一索引、一个访问索引和四条本库关系后，物理目标为 37 个唯一索引、18 个访问索引、26 条本库关系；六个 Diagram Group 保持不变。

## 2. 引用边界

“引用其他表”不表示跨库外键、跨库查询、共享表或视图。CCT、Commerce、Risk 和 Finance 继续拥有独立数据库。Commerce 只保存普通 `BIGINT` 业务 ID 与必要 Hash，通过内部 HTTP 按精确 ID 读取来源系统的不可变历史事实。

强制规则：

- 禁止用引用 ID 重新解析来源系统当前 `ACTIVE` 记录；
- CCT 已批准的 Commercial Term Scope、所属 Version/Plan、Component 和 Agreement Association 必须长期可按精确 ID 读取，不得覆盖或物理删除；
- Finance Tax Snapshot、Tax Line 和 Calculation Hash 必须长期不可变且可按 `tax_snapshot_id + calculation_hash` 查询；
- 外部不可变查询必须 Fail Closed：暂时网络错误不 ACK 原 `PaymentSucceeded`，由 Broker 以同一事件重投；永久缺失、关系不一致或内容损坏进入 DLQ/人工处置并告警，禁止 Journal，不创建无业务事实归属的通用补偿任务；
- 不得退回当前费率、默认费率、零费率或重新运行当前交易资格解析；
- Checkout 常用页面不得依赖 CCT 或 Finance 实时可用，详细税务审计和 Finance 经济计算除外。

## 3. 三张目标表

### 3.1 `t_order_snapshot`：24 字段

```text
order_snapshot_id
order_id
snapshot_schema_version
cct_context_as_of_time
industry_code
mcc_code
assignment_id
cct_commercial_context_hash
client_pricing_scope_id
partner_share_scope_id
risk_checkout_assessment_id
finance_tax_snapshot_id
finance_tax_snapshot_hash
merchandise_amount
discount_amount
net_amount
tax_amount
rounding_amount
total_amount
currency
consumer_country_code
locale
snapshot_hash
create_time
```

字段矩阵：

- `client_pricing_scope_id` 始终非空；一个 Scope ID 已能唯一定位所属 Version、Plan、Agreement 与全部 Component，不重复保存 Plan/Version ID；
- `entity_source_type=PARTNER` 时 `partner_share_scope_id` 非空，`PLATFORM/CLIENT` 时为空；
- `finance_tax_snapshot_id + finance_tax_snapshot_hash` 始终成组非空；Hash 对应 Finance `tax_snapshot.calculation_hash`；
- `risk_checkout_assessment_id` 只引用 Risk 中实际返回 `ALLOW` 的不可变 Assessment；Order 创建成功本身表达 ALLOW，不重复保存 Outcome/Reason Hash；
- `snapshot_schema_version=2`，Hash Domain 升级为 `CODE-MOR:COMMERCE:ORDER_SNAPSHOT:2`，不得用新结构重算 V1 历史 Hash；
- `snapshot_hash` 使用本文关联的 Commerce 权威文档所定义的完整 V2 Canonical 契约；它覆盖 Order 创建后不可变身份/参与方 Tuple、Snapshot 业务字段、Legal 与按 `client_item_id,order_item_id` 稳定排序的 Item，显式排除自身、审计时间和 Order 可变状态投影。

删除的九个现有字段：

```text
business_profile_id
business_profile_version_no
business_profile_content_hash
business_classification_id
assignment_rule_id
assignment_affiliation_id
entity_source_relation_id
risk_checkout_outcome
risk_checkout_reason_hash
```

删除理由：

- Commerce、Payment、Risk 和 Finance 的订单时点统计消费 `industry_code + mcc_code`，不消费 CCT Profile/Classification 内部行身份；V4 `cct_commercial_context_hash` 保留为 CCT-I13 原始整包响应的审计关联摘要，不作为单 Scope 内容 Hash；
- `assignment_id` 已唯一定位 Rule、Affiliation、Source Relation 与 Operating Entity，Commerce 不需要复制自动分配内部解释路径；
- Risk 拥有 Outcome、Reason、Rule Hit 和输入历史，Commerce 只需要已采用的 Assessment ID。

### 3.2 `t_order_legal`：21 字段

```text
order_legal_id
order_snapshot_id
client_agreement_operating_entity_id
partner_agreement_operating_entity_id
consumer_terms_document_id
consumer_terms_file_id
consumer_terms_content_hash
refund_policy_document_id
refund_policy_file_id
refund_policy_content_hash
privacy_notice_document_id
privacy_notice_file_id
privacy_notice_content_hash
seller_disclosure_id
public_legal_name
public_registration_number
public_tax_registration_number
public_address_text
public_support_email
public_support_phone
create_time
```

字段矩阵：

- `client_agreement_operating_entity_id` 始终非空；该 Association ID 可唯一定位 Agreement 与 Operating Entity，不重复保存 `client_agreement_id`；
- `entity_source_type=PARTNER` 时 `partner_agreement_operating_entity_id` 非空，其他来源为空；该 ID 同样唯一定位 Partner Agreement；
- CCT-I13 必须验证返回的 Association 所属 Agreement、Owner 和 Operating Entity 与完整交易上下文一致；
- 三份 Legal 的 Document ID、File ID、Content Hash 分别承担来源事实、内容读取和接受证据职责，不能进一步压成单一 ID；
- Seller 公开展示值继续在 Commerce 本地冻结，Checkout 不回查 CCT 当前 Party 或 Seller Disclosure；
- 删除 `seller_country_code/seller_locale`，因为 CCT-I13 对 Order 的同一准确 `consumer_country_code + locale` 解析 Seller Disclosure，二者必须逐值相等且不存在隐式 Locale 回退；如果未来批准 Locale Fallback，必须重新设计并恢复实际命中 Scope。

### 3.3 `t_order_item`：17 字段

```text
order_item_id
order_snapshot_id
client_item_id
item_name
item_description
product_type_code
tax_category_code
quantity
unit_amount
line_gross_amount
line_discount_amount
line_net_amount
line_tax_amount
line_total_amount
currency
sort_no
create_time
```

本轮不压缩 Item：

- Item 具有真实 1:N 基数；
- `product_type_code/tax_category_code` 是 Finance 税务请求的原始输入，不允许从 Industry/MCC 或最终税额反推；
- 行金额和 `line_tax_amount` 是 Checkout、退款金额校验和消费者展示所需的本地事实；
- 完整税种、税率、辖区、Registration、Rule Version、Reverse Charge 与责任方只存在于 Finance Tax Snapshot/Line。

## 4. 删除的三个本地副本

### 4.1 删除 `t_order_tax_line`

Finance 继续完整保存 `tax_snapshot + tax_line`。FIN-I17 只向 Commerce 返回：

```text
tax_snapshot_id
calculation_hash
total_collected_tax
total_reverse_charge_tax
currency
每个 source_order_item_id 对应的 line_tax_amount
```

Commerce 将总实收税写入 `t_order_snapshot.tax_amount`，将每个 Item 的实收税写入 `t_order_item.line_tax_amount`。完整明细使用 Finance 新增或明确化的不可变查询：

```text
GetImmutableTaxSnapshot(tax_snapshot_id, expected_calculation_hash)
```

查询返回原 Snapshot、Tax Lines、Registration、Jurisdiction、Rule/Rate/Treatment 与责任模式。Consumer Receipt/Invoice/Credit Note 的法定税务内容继续由 Finance Fiscal Record 与 Platform 文档链负责，不要求 Commerce 复制 Tax Line。

### 4.2 删除 `t_order_commercial_term` 与 `t_order_term_component`

CCT-I13 仍实时解析完整 V4 Bundle 并计算 `commercial_context_hash`，但 Commerce 只持久化实际命中的：

```text
client_pricing_scope_id
partner_share_scope_id (nullable)
cct_commercial_context_hash
```

CCT 提供只面向内部消费者的精确历史查询：

```text
GetImmutableCommercialTermScope(
  term_scope_id,
  agreement_operating_entity_id,
  context_as_of_time
)
```

响应必须包含 `owner_type/owner_id`、`agreement_id/operating_entity_id`、Scope 所属 Plan/Version、Version No/有效区间/舍入方式、完整 Scope 和按稳定顺序返回的全部 Component。CCT 精确读取长期保留的结构化行，验证 Scope→Version→Plan→Agreement→Component、Association 与 Agreement/OE、Plan Owner 与 Agreement Owner，以及 Version/Agreement 在 `context_as_of_time` 的业务有效期；不重新执行 CCT-I13、不重新选最小 Priority，也不要求记录仍为当前 ACTIVE。

该接口只证明单个 Scope 闭包与指定 Agreement Association/OE/As-Of 自洽，不能验证或重算完整 V4，也不能证明唯一最优命中、Assignment/Legal/Seller 或另一 Scope 属于同次 Bundle；V4 Hash 因此不是 I14 入参或信任锚。

Finance 在 `PaymentSucceeded` 后先把 Payment 与 COM-I04 返回的 `order_snapshot_id/snapshot_schema_version/snapshot_hash` 逐值交叉校验，再分别以 Scope ID、对应 Agreement Association ID 和 `cct_context_as_of_time` 调用 CCT。Finance 将返回的 Owner/OE 与订单 Client/Partner/OE 比对，并按订单国家、币种、Industry、MCC 与实际支付方式核对 Scope 维度；校验成功后只把可重放计算所需的 Version/Scope ID、Component 参数和舍入方式冻结进 `economics_snapshot/economics_component`，查询用 Plan/有效期/Scope 元数据不冒充本地完整副本。

暂时网络或可重试 5xx 时不 ACK 原 `PaymentSucceeded`，等待 Broker 重投；Not Found、Association/Owner/OE/Scope 关系不一致或对象损坏是永久完整性错误，告警并进入 DLQ，禁止生成 Economics、Journal 或退回当前/默认/零费率。

## 5. Risk 输入时序修正

Checkout Risk 发生在最终 Order Snapshot 落库之前，最终 `snapshot_hash` 又覆盖 Risk Assessment 和 Finance Tax 结果，因此 Risk 输入不能要求尚未生成的最终 `order_snapshot_hash`。

Commerce 应先构造内存中的 `OrderPreparationContext`，其输入 Hash 覆盖：

- 预分配 Order/Item ID；
- Client、Operating Entity、Source Tuple；
- 国家、Locale、Industry、MCC、税前 `merchandise_amount/discount_amount/net_amount` 和币种；此时 Tax Snapshot、`tax_amount` 与最终 `total_amount` 尚不存在；
- CCT V4 Commercial Context Hash；
- Client/Partner Agreement Association ID；
- Client Pricing/Partner Share Scope ID；
- 三份 Legal 与 Seller Disclosure；
- 当前 Risk 所需的 Consumer/Device 最小输入。

Risk 冻结自己的 `checkout_input_snapshot.input_hash` 并返回 Assessment ID。Commerce 随后取得 Finance Tax Snapshot，最后计算 `CODE-MOR:COMMERCE:ORDER_SNAPSHOT:2`。Risk 不依赖最终 Snapshot Hash，Payment Attempt Risk 仍使用最终不可变 Snapshot Hash。

## 6. 查询和运行时边界

| 场景 | 是否只读 Commerce | 说明 |
|---|---|---|
| Hosted Checkout 展示金额、Item、行税额、Seller 和三份 Legal | 是 | Root + Legal + Item 本地自包含 |
| Consumer 接受三份 Legal | 是 | 使用本地 File ID/Content Hash |
| Payment 创建 Intent/Attempt | 是 | 使用 Order/OE、分类、Assignment、Snapshot Hash |
| 普通订单详情 | 是 | 不要求税率、辖区或费率组件 |
| 完整税务审计 | 否 | 按 Tax Snapshot ID/Hash 查询 Finance |
| Finance 计算 Client Fee/Partner Share | 否 | 先交叉校验 Payment/Commerce Snapshot 锚，再按 Scope ID + Agreement Association + CCT As-Of 查询 CCT 并冻结计算参数 |
| 历史 Risk 解释 | 否 | 按 Assessment ID 查询 Risk |

## 7. 明确不做

- 不增加新的 Term Content Hash、Scope Hash 或 Component Hash；V4 CCT Commercial Context Hash 仅标识原 CCT-I13 整包响应，不能被单 Scope 查询反向验证；
- 不把外部引用建立为跨库物理外键；
- 不让 Commerce 调用 Finance/CCT 才能渲染普通 Checkout；
- 不把 `t_order_legal` 合并到 `t_order_snapshot`：只能减少两个本地主外键字段，却会把根表扩宽到 43 列并混入大段法律展示文本；
- 不创建通用 Snapshot 引用表、EAV、JSON 扩展或多态 External Reference 表；
- 不创建 Migration、Java 模块、物理数据库或运行环境。

## 8. 验收标准

- Commerce 当前 Catalog 精确为 23 表、329 字段、37 个唯一索引、18 个访问索引、26 条本库关系和 6 个 Diagram Group；
- Snapshot 聚合只包含 `t_order_snapshot/t_order_legal/t_order_item`，合计 62 字段；
- 三张删除表及其字段、索引、关系、Diagram Cell 不再出现在当前 PDManer 模型；
- CCT、Commerce、Risk、Finance、需求目录、全局时序、现实场景和物理决策对引用型快照使用同一语义；
- Checkout 常用查询仍本地自包含；完整税务明细由 Finance 查询；Finance 商业计算按 CCT 精确 Scope ID 读取并本地冻结；
- 生成器重复运行字节级稳定，`--check`、模型测试、场景文档测试和 requirements-site 全套测试通过；
- PDManer 4.9.3 应用内打开与检查继续作为人工兼容门禁。

# CCT Payment Method Profile 与组合定价模型决策

- Decision status: Confirmed
- Implementation and deployment status: see [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)
- Decision date: 2026-09-10
- Supersedes: CCT `commercial_term_scope` 的动态定价维度、Scope 到 Component 的父子结构，以及所有把 Payment Method Profile 当作 Pricing Rule、Payment Method 主表或可修改 Profile Version 的方案

## 1. 背景

Client Pricing 需要按支付方式、国家、币种、卡组织或资金类型、卡等级和 3DS 结果组合匹配，并同时支持百分比、固定金额、阶梯及多组件相加。把这些维度固定成 `commercial_term_scope` 列，会让每次增加维度都修改表结构；把规则直接放入每个 Client/OE 定价，又会重复描述相同支付场景。

本决定把“支付场景描述”和“合同价格”拆开：前者由 CCT 作为全局基础配置维护，后者继续属于具体 Commercial Term Version 与 Operating Entity。

## 2. 已确认决定

### D1. 基础配置命名为 Payment Method Profile

`payment_method_profile` 表示一个可复用的支付方式特征集合，不叫 Pricing Rule，也不拥有费率、Client、Operating Entity、Agreement、有效期或优先级。

每个 Profile 必须绑定一个 `payment_method_code`。Payment Method 是 Payment 拥有的稳定代码目录/应用常量，CCT 不建立 Payment Method 主表，也不复制名称、图标、路由能力或 Provider 配置。

### D2. Profile 使用代码作为聚合外部身份

`payment_method_profile_code` 在 CCT 内全局唯一，创建后不可修改、不可回收复用，也不得把 Client、OE、费率或版本号编码进 Code。

- Profile 自身和跨聚合、跨系统引用均使用 `payment_method_profile_code`；
- Profile 的 Condition 是聚合内部 Child，使用 `payment_method_profile_id` 关联父记录；
- 本决定不要求所有表为了 code-first 而新增 Code。只有已经拥有全局唯一、稳定、不可复用 Code 的对象才优先以 Code 跨系统关联；事务事实、不可变版本和没有业务 Code 的 Child 继续使用其稳定 ID 或明确的复合坐标。

### D3. 不建立 Profile Version、Dimension Definition 或 Condition Value 表

Profile 生命周期为 `DRAFT -> ACTIVE -> RETIRED`：

- DRAFT 可在 Profile Root 的 BCR/CAS 保护下修改名称、支付方式和 Condition；
- ACTIVE 后 `payment_method_profile_code`、`payment_method_code` 和全部 Condition 永久冻结；
- RETIRED 只阻止新 Commercial Term Version 引用，历史引用仍可读取；
- 条件语义变化必须创建新 Profile 和新 Code，不覆盖旧 Profile，也不增加 Profile Version 表。

每条 `payment_method_profile_condition` 直接保存 `dimension_code + condition_value`。允许的 Dimension Code、值域、规范化和事实 Owner 由版本化应用/API 合同控制，不使用数据库自由文本扩展，也不建立 `dimension_definition` 表。

多值不保存 CSV，也不建立 `condition_value` 子表：一个值一行；同一 `dimension_code` 的多行是 OR，不同 `dimension_code` 之间是 AND。缺少某个 Dimension 表示该维度不限制，不保存 `*`。首期只支持离散等值条件，金额区间由定价 Band 表表达。

### D4. Commercial Term Price 关联 Profile

`commercial_term_price` 属于一个准确 `commercial_term_version + operating_entity_id`，并以 `payment_method_profile_code` 关联一个 ACTIVE Profile。Profile 可被所有 Client、Local Partner、Commercial Term Version 和 Operating Entity 复用；价格、优先级和组件不进入 Profile。

同一 Version/OE 下：

1. 先按 Profile 的 `payment_method_code` 与全部 Condition 匹配交易事实；
2. 多个候选命中时，先选择不同 `dimension_code` 数量最多的 Profile；
3. 具体度相同再选择最小 `priority_no`；
4. 无命中、事实缺失、非法值或仍无法唯一解释时 Fail Closed。

Price 必须使用准确非空 OE；旧 `commercial_term_scope.operating_entity_id IS NULL` 的全 Agreement-OE 语义在迁移时展开为每个被 Agreement 覆盖的准确 OE Price，不再使用 NULL 或 `*` 表达业务通配。

### D5. 组件相加，阶梯使用独立 Band

一个 Price 拥有一个或多个按 `component_no` 稳定排序的 Component。`component_type` 为：

- `PERCENTAGE`：固定百分比费率；
- `FIXED`：固定金额；
- `TIERED`：由一个或多个 Band 定义阶梯。

多个 Component 的结果相加，因此百分比、固定金额和阶梯可任意组合。`TIERED` 的 `band_mode` 为 `WHOLE_BASE` 或 `PROGRESSIVE`；Band 使用连续、无重叠的半开区间 `[lower_bound, upper_bound)`，第一档从 0 开始，最后一档 `upper_bound=NULL`。

`PROGRESSIVE` 首期只允许比例 Band，固定金额使用独立 `FIXED` Component。周期累计交易额/笔数阶梯不属于此静态模型；若需要，必须由 Finance 的 Billing Period/Usage 权威模型另行设计。

### D6. 晚到支付事实不由 CCT 猜测

卡组织、资金类型、卡等级和实际 3DS 分类由 Payment 在执行过程中标准化产生。CCT 不读取 PAN、完整 BIN、Provider 原始 Payload，也不得用“没有 `THREE_DS_CHALLENGE`”推断非 3DS。

现行 CCT-I13 V5 在创建 Order 时已经冻结 Scope，而此时未必具备最终支付事实，因此不能把旧 Scope ID 静默解释成新 Price。后续 Wire 迁移必须使用新版本合同：下单冻结 Commercial Term Version/Agreement-OE 与上下文时点，支付事实明确后解析并冻结准确 Price、Profile Code、Component/Band 证据。历史 V5 Hash、Scope ID 和已冻结交易继续按原合同读取，不重算、不回填。

## 3. 物理模型

保留：

- `commercial_term_plan`
- `commercial_term_version`

新增或替换：

- `payment_method_profile`
- `payment_method_profile_condition`（PDManer 物理表名为满足 32 字符门禁缩写为 `t_payment_method_profile_cond`）
- `commercial_term_price`
- `commercial_term_component`（父引用由 Scope 改为 Price，并增加顺序与阶梯模式）
- `commercial_term_component_band`

删除：

- `commercial_term_scope`

模型关系只作为 PDManer Correlation、索引和应用事务不变量表达，不生成物理 `FOREIGN KEY` 或 `CHECK`。

## 4. 兼容与实施边界

- 当前 CCT 尚无已证明的完整业务 Schema 或定价 Java 实现，因此本轮首先修改设计源、生成器、PDManer 工件和模型门禁，不执行共享或生产数据库迁移。
- Commerce 现有 `client_pricing_scope_id`、Finance 现有 `source_rule_scope_id`、CCT-I13/I14 V5 和相关 Canonical Hash 都是待迁移消费者；它们不能仅靠字段改名接入新模型。
- Payment 需要另行确认并实现安全的卡资金类型、卡等级与认证分类事实；CCT 不先造这些支付事实。
- 新 Wire、历史 Reader、数据转换和下游 PDManer/SQL 改造必须分别经过 Owner 设计和验证。本决定不授权跨库写入、直接改生产数据或批量重写历史记录。

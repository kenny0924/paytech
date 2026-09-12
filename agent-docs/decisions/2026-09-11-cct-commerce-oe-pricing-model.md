# OE、Client 与 Pricing 四系统模型调整

> 2026-09-12 Commerce 实现范围修正：用户确认旧代码未发布，基础创建直接替换，不保留快照/回执 Schema 版本、
> 历史创建分支或旧 Owner Mock。本文此前针对 Commerce 旧创建代码的兼容要求在该切片内不再适用；
> 幂等恢复、并发 CAS、已执行 SQL 记录及其他系统边界保持。具体合同见 [Commerce 基础创建切片](../../requirements/20260911/client-contract-OE与Client模型梳理/commerce-precheck-implementation.md)。

- Decision status: Confirmed
- Decision date: 2026-09-11
- Scope: CCT、Commerce、Payment、Finance 的 PDManer、生成器、模型校验、人工 SQL 与配套文档。
- Risk 本期不修改；Java、接口运行迁移、SQL 执行、部署状态仅见 [当前实现台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 授权与取代范围

用户在完成模型及阶段 3 方案讨论后明确“Risk 的先不动，其余的可以先改”，并补充“PDManer 的本期改动的时候也一起调整掉”。因此，前期“不改 PDManer”的限制在本四系统模型切片内解除。完整需求及逐列设计见 [需求目录](../../requirements/20260911/client-contract-OE与Client模型梳理/delivery.md)；跨系统总体方案保留 Risk 后续接入部分，不把它视为本期已实现。

本决定精确取代旧 CCT 四表 Assignment、数字 OE 主键、Client 多 OE Agreement、全 Client 唯一 Pricing Plan、独立 Agreement Acceptance，以及新交易在创建 Checkout 时冻结全部上下文的要求。Payment Method Profile 的条件匹配和 Component/Band 计算语义、Partner 多 OE 合同、Finance Provider Cost Scope 继续保留。旧接口、历史记录及旧 Hash 的解释不变；其他业务主题继续由原 Owner 文档管理。

## 2. CCT 模型

`t_operating_entity.entity_no` 为唯一主键，`CHAR(5) CHARACTER SET ascii COLLATE ascii_bin`，格式为创建时二位经营国家代码加 `001`–`999`，如 `AE001`。删除新模型中的数字 `operating_entity_id`，编号不可修改、回收或作为路由规则使用；一个 OE 只有一个经营国家，法律 Party 不得改绑。其他本地主键继续使用原 Snowflake BIGINT。

`t_operating_entity_scope` 保存申请目标及已批准的准确 `consumer_country_code/currency/industry_code/mcc_code` 组合，无通配。完整目标范围经过审核和 OE Root 版本核对后原子替换；拒绝或过时结果不撤销原范围。`ONBOARDING` 与 `SCOPE_CHANGE` 分别冻结 `ENTITY_UNDERWRITING` 和 `ENTITY_SCOPE_REVIEW`。新身份及审核合同未在 Risk 接入前，此新流程禁止激活，不得伪造旧数字 ID 或默认通过。

Client 保留 Business Profile 下的经营分类及唯一 Primary 分类。`t_client_entity_affiliation` 只维护 Client/OE 授权、状态、有效期和 `priority_no`；不重复保存四要素。同一 Client 的有效 OE 关联和有效优先级分别唯一。关联即允许使用该 OE 当前及以后已批准、且符合 Client 分类的范围。按优先级逐候选验证 Scope、来源、合同、法律材料及其他必要条件；依赖不可用或全局拒绝不能伪装成候选不支持。CCT 查询只读，不创建 Assignment。

新写模型删除 `t_entity_assignment_rule`、`t_client_entity_assignment` 和 `t_agreement_acceptance`。`t_agreement_signatory` 演进为 `t_agreement_party`，合并签署字段；`(agreement_id,party_id,party_role)` 唯一。同合同同 Party 的所有角色一次签署并在同一本地事务写入相同最终结果，待签身份冻结、签后证据不可覆盖。最小角色主键行签后占 `acceptance_ref_slot=1`，其余为 NULL；组内 Slot 唯一与签署引用/Slot 唯一共同防止跨 Party 复用签署结果。在线接受与证据登记 Actor 分开，原文件与签署方式矩阵继续有效。

每份新 Client Agreement 精确绑定 Client + OE；Client Pricing Plan 属于该 Agreement。Plan 的 Client/OE 冗余字段必须与合同一致。每个 Plan 同时最多一个 ACTIVE Version，有效期不可重叠。Partner 的共享 Agreement/OE 关联及两类 Partner Plan 保留，Version 仍保存 Agreement。Price 精确绑定 Version + OE + Payment Method Profile，并保存 Client 的四项结算输入：工作日数、业务日历、时区、截止秒；Partner Price 四项均为空。旧 Agreement/OE 关联的新写模型移除结算字段。

初期使用 UTC 和 `86399`，截止为 `23:59:59.000000`，超过该微秒边界滚至下一工作日；N 按冻结日历计算。费用和成熟输入必须来自同一命中 Client Price。日期边界与缺失日历不得猜测。

## 3. Commerce、Payment 与 Finance

创建 Checkout 仅做基本可用性预检并保存基础 Order/Items/Session，OE、税额和最终总额尚可为空。支付准备命令先持久化候选与恢复身份，再冻结准确 OE、Affiliation/Scope/Source、Agreements、Term Versions、Seller/Legal、Tax 和最终金额；此后才能取得 Consumer 接受证据和发起 Payment。Commerce 承担编排、重试与终止竞态。已提交上下文不可覆盖，后续 Attempt 不跨 OE；历史订单不重算。

Order/Intent/Economics 显式携带 `context_format`，新流为 `TERM_PRICE`，历史解释为 `LEGACY_SCOPE`。Order Context 使用稳定外部事实引用，Client Agreement 直接引用合同根。物理字段 `platform_charge_version_id` 对应接口 `platformServiceChargeVersionId`，采用短名满足字段命名长度要求。

Payment 新增每个成功 Attempt 唯一的不可变 `t_payment_pricing_fact`，保存标准化支付方式、批准的卡片描述、成功 Action、金额/币种、成功时刻及固定 Term Versions。事实不齐不得按零费率入账，也不得回退已成功支付状态。后续由 CCT 按固定 Version 和完整事实选择准确 Price，再由 Finance 计算。不得把 PAN、BIN 或 Provider 原始 Payload 带入 CCT。

Finance Economics 保存 Payment Fact、Client Price 与结算输入；Component 保存准确 Price/Profile、复用 Version/Component 引用及受限 Band Evidence。`CCT_PRICE_COMPONENT` 与旧 CCT Scope、Finance Provider Cost Scope 分支分开，新分支不填写旧 Scope/Content Hash。阶梯证据最多 1000 项、64 KiB，按 bandNo 排序；字段白名单和舍入算法见阶段 3 合同。

所有专用 OE 引用使用 CHAR(5)。允许混合主体的字段必须连同类型解释：CCT BCR 的 `record_id/aggregate_root_id`、Payment Routing Policy 的 `scope_id`、Finance 的 `entity_id`、Account 的 `owner_id`、Economic Component 的 `party_id` 使用大小写敏感 VARCHAR(64)。OE 存 entityNo，其他数字身份存规范十进制字符串；Finance Book/Account/Journal 等自身主键仍为 BIGINT，禁止数值强制转换或跨类型比较。

## 4. 物理与历史边界

新建库 Schema 由四份规范 PDManer 文件生成；每个服务仅一份当前完整 Schema。模型关系不是物理 FK，SQL 不创建 CHECK/FK。创建前必须完成应用兼容验证；不得对现有环境直接执行这些完整建表文件。

移出新写模型不等于删除历史物理表或历史列。已有数字 OE、Assignment、独立 Acceptance、旧 Scope、JSON/Hash、订单与财务经济事实必须保留原始解释。迁移必须先盘点实际数据、冻结旧写入口、逐主体审核映射，再制定独立人工迁移和回退方案；本期不生成推测性映射，不自动改历史 JSON、Hash、金额或旧 ID。可用旧 Reader 与新 Writer 的隔离是运行迁移前置条件。

SQL 文件、复现命令、验证证据及未完成边界见 [本期实施记录](../../requirements/20260911/client-contract-OE与Client模型梳理/model-implementation.md)。

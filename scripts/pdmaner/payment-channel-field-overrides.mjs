/** Channel 账号与能力查询的已确认字段语义。 */
export const CHANNEL_FIELD_OVERRIDES = Object.freeze({
  "channel_account.account_vault_token": { extProps: { characterSet: "ascii", collation: "ascii_bin" } },
  "channel_account.entity_no": { comment: "账号所属 Operating Entity 的不可变 CHAR(5) 编号，来源于 CCT；与其当前绑定 MID 的 OE 一致，不作为路由规则。" },
  "mid_capability.mid_capability_id": { comment: "一个 MID 在完整交易条件组合下的能力配置组标识；明细按能力代码分别解析。" },
  "mid_capability.scheme_code": { comment: "支付体系或网络代码，可为精确代码或 *；请求未提供体系时仅匹配 *。取值来源：Scheme Catalog；非固定枚举。" },
  "mid_capability.country_code": { comment: "匹配 Commerce 冻结到 Payment Intent 的消费者国家，可为精确两位代码或 *。取值来源：ISO 3166-1 alpha-2；非固定枚举。" },
  "mid_capability.mcc_code": { comment: "渠道批准用于该 MID 的精确四位业务 MCC，不允许 *，不代表默认收单 MCC。取值来源：CCT/Commerce frozen classification；非固定枚举。" },
  "mid_capability.capability_status": { comment: "整组能力条件的启停状态；DISABLED 不参与匹配，不等价于任一 BOOLEAN 能力的 false。允许值：ENABLED、DISABLED。" },
  "mid_capability_detail.boolean_value": { comment: "BOOLEAN 类型的能力值：1 明确支持，0 明确不支持；其他类型必须为 NULL。" },
  "mid_capability_detail.decimal_value": { comment: "DECIMAL 类型的精确能力值；金额能力使用交易币种主单位；其他类型必须为 NULL。" },
  "mid_capability_detail.code_value": { comment: "CODE 类型的能力目录值；其他类型必须为 NULL；允许值由该能力的注册定义限定。" },
  // 2026-10-19 渠道成本四表自 billing 迁入（requirements/20261019/billing-计费事实模型重构与渠道成本迁出/db/channel-connector/ddl.sql）：
  // 字段键与 Payment 其他表共用时，以表级注释保持与评审 DDL 逐字一致；scope_id 为 BIGINT，不继承策略作用域的 ascii 编码。
  "channel_cost_plan.id": { comment: "服务侧生成的渠道成本方案 Snowflake 主键。" },
  "channel_cost_plan.channel_id": { comment: "Payment Channel（本库 t_payment_channel.id）。" },
  "channel_cost_plan.version": { comment: "切换当前成本版本和状态时使用的乐观锁版本号。", defaultValue: "0" },
  "channel_cost_plan.create_time": { comment: "该记录在本库中的 UTC 微秒创建时间。" },
  "channel_cost_plan.update_time": { comment: "该可变记录最后一次持久化更新的 UTC 微秒时间。" },
  "channel_cost_plan_ver.id": { comment: "服务侧生成的渠道成本方案版本 Snowflake 主键。" },
  "channel_cost_plan_ver.content_hash": { comment: "覆盖版本、全部适用范围和成本组件的不可变内容摘要。" },
  "channel_cost_plan_ver.status": { comment: "该成本版本写入时冻结的审批和发布状态。允许值：DRAFT、PENDING_APPROVAL、APPROVED、ACTIVE、REJECTED、INACTIVE。" },
  "channel_cost_plan_ver.create_time": { comment: "该记录在本库中的 UTC 微秒创建时间。" },
  "channel_cost_plan_ver.update_time": { comment: "该可变记录最后一次持久化更新的 UTC 微秒时间。" },
  "channel_cost_scope.id": { comment: "服务侧生成的渠道成本适用范围 Snowflake 主键。" },
  "channel_cost_scope.mid_id": { comment: "Payment Channel MID（本库 t_channel_mid.id）；写入时校验存在、归属方案的 Channel 且 entity_no 一致。" },
  "channel_cost_scope.entity_no": { comment: "该成本适用范围限定的 Operating Entity 稳定业务ID，须等于 MID 归属的 OE。" },
  "channel_cost_scope.country_code": { comment: "该成本适用范围限定的 ISO 3166-1 alpha-2 国家代码。取值来源：ISO 3166-1 alpha-2 国家代码；非固定枚举。" },
  "channel_cost_scope.currency": { comment: "该成本适用范围限定的 ISO 4217 三位币种代码，也是成本金额的币种。取值来源：ISO 4217 币种代码；非固定枚举。" },
  "channel_cost_scope.payment_method_code": { comment: "该成本适用范围限定的 Payment 支付方式目录代码。取值来源：Payment 支付方式目录；非固定枚举。" },
  "channel_cost_scope.action_type": { comment: "该成本适用范围限定的支付、退款或争议动作类别。取值来源：Payment 动作类型目录；非固定枚举。" },
  "channel_cost_scope.create_time": { comment: "该记录在本库中的 UTC 微秒创建时间。" },
  "channel_cost_component.id": { comment: "服务侧生成的渠道成本组件 Snowflake 主键。" },
  "channel_cost_component.scope_id": { comment: "该成本组件所属的本库渠道成本适用范围。", extProps: {} },
  "channel_cost_component.currency": { comment: "固定金额使用的 ISO 4217 三位币种代码，须等于范围币种。取值来源：ISO 4217 币种代码；非固定枚举。" },
  "channel_cost_component.create_time": { comment: "该记录在本库中的 UTC 微秒创建时间。" }
});

/** 渠道成本四表的索引注释，与评审 DDL 逐字一致；未登记的索引沿用生成器默认注释。 */
export const CHANNEL_INDEX_COMMENTS = Object.freeze({
  "channel_cost_plan.uk_channel_cost_plan": "保证业务唯一性：channel_id",
  "channel_cost_plan.uk_channel_cost_code": "保证业务唯一性：plan_code",
  "channel_cost_plan.uk_channel_cost_current": "保证业务唯一性：current_ver_id",
  "channel_cost_plan_ver.uk_channel_cost_ver": "保证业务唯一性：plan_id、cost_ver",
  "channel_cost_scope.uk_channel_cost_scope_no": "保证业务唯一性：cost_ver_id、scope_no",
  "channel_cost_scope.uk_channel_cost_scope": "保证业务唯一性：cost_ver_id、mid_id、entity_no、country_code、currency、payment_method_code、action_type",
  "channel_cost_scope.idx_channel_cost_match": "支持访问路径：mid_id、entity_no、country_code、currency",
  "channel_cost_component.uk_channel_cost_component": "保证业务唯一性：scope_id、component_code"
});

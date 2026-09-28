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
  "mid_capability_detail.code_value": { comment: "CODE 类型的能力目录值；其他类型必须为 NULL；允许值由该能力的注册定义限定。" }
});

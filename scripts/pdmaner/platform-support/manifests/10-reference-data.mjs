import {
  defineTable,
  field,
  primaryKey as pk,
  types,
  uniqueIndex as uk,
} from "../schema-dsl.mjs";

// 全平台参考数据（D41，design/03 §3.1）：Platform 为 Owner；mor_ledger 与 mor_billing 持同结构只读副本（第 07 步）。
// 列、类型与索引逐字沿用 finance-schema.sql 的 t_currency_spec，只把 Owner 相关的表注释与创建时间注释改为 Platform。
export const TABLES = Object.freeze([
  defineTable({
    key: "currency_spec",
    name: "币种规则",
    comment: "保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 为 Owner，Ledger 与 Billing 持只读副本。",
    fields: [
      pk("id", "币种规则ID", "服务侧生成的币种规则 Snowflake 主键"),
      field("currency", "CHAR(3)", "NN", "币种", "ISO 4217 三位币种代码", { source: "ISO 4217 币种代码" }),
      field("minor_unit", "TINYINT", "NN", "最小货币单位位数", "记账与结算金额允许保留的小数位数，取值范围为零至八"),
      field("rounding_mode", types.code, "NN", "舍入方式", "金额从八位计算精度归整到币种精度时采用的固定方式", { values: ["HALF_UP"] }),
      field("status", types.code, "NN", "状态", "币种规则当前是否允许被新业务使用", { values: ["DRAFT", "ACTIVE", "INACTIVE"] }),
    ],
    indexes: [uk("uk_currency", "currency")],
  }),
]);

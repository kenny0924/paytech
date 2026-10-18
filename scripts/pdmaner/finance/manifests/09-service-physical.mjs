// 第 11b 步服务物理表（design/04 §3，任务卡 11b 第 2.1 项）：不进入 84 张表的 Finance 逻辑模型。
// 逻辑模型保留 Finance V1 的统一记账账户（t_account，余额投影列仍在）作为场景与规则契约的基准；
// 服务库按本文件做物理替换与追加：
//   - ledger：t_account 物理替换为只保存身份的 t_ledger_account（删除五个余额列），追加 paytech-messaging 两张消费表；
//   - account：新建余额桶权威表、已处理事实、已应用余额变化、桶移动（仅 Schema）、日终核对差异，与两张消费表。
import {
  accessIndex,
  commentedAccessIndex,
  defineTable,
  field,
  primaryKey,
  types,
  uniqueIndex,
} from "../schema-dsl.mjs";

const ACCOUNT_STATUS = ["ACTIVE", "INACTIVE", "CLOSED"];
const LEDGER_OWNER_TYPES = [
  "OPERATING_ENTITY",
  "FINANCE_ENTITY",
  "CLIENT",
  "LOCAL_PARTNER",
  "CHANNEL_ACCOUNT",
  "CASH_ACCOUNT",
  "TAX_AUTHORITY",
];
const BALANCE_OWNER_TYPES = ["CLIENT", "LOCAL_PARTNER", "CHANNEL_ACCOUNT", "CASH_ACCOUNT"];
const BALANCE_BUCKETS = ["PENDING", "AVAILABLE", "LOCKED", "NONE"];
const PAYABLE_BUCKETS = ["PENDING", "AVAILABLE", "LOCKED"];
const NORMAL_SIDES = ["DEBIT", "CREDIT"];
const BOOK_ENTITY_TYPES = ["OPERATING_ENTITY", "FINANCE_ENTITY"];
const ascii = { ascii: true };

/** ledger 库的 t_ledger_account：物理替换逻辑表 account（同一实体 ID 与字段 ID，库内关系保留）。 */
export const LEDGER_ACCOUNT = Object.freeze({
  replaces: "account",
  table: defineTable({
    key: "ledger_account",
    name: "会计科目账户",
    comment: "在具体账簿和可过账科目下按核算对象与余额桶建立唯一会计科目账户（只保存身份，不保存余额）；余额由不可变 Journal 聚合得出，Owner 余额桶的实时权威是 account 服务的 t_account_balance（design/04 §3）；CLIENT_PAYABLE 与 PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner PENDING 是共享待结算桶；Partner reversal 无法由 PENDING/AVAILABLE/LOCKED liability 吸收时使用资产科目 PARTNER_RECOVERY_RECEIVABLE/NONE；Client Recovery 使用 CLIENT_RECOVERY_RECEIVABLE/NONE，保证金使用 CLIENT_SECURITY_DEPOSIT/NONE；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 四个科目表达交易形成、交易对账完整和银行确认三个阶段，Channel Cost 单独使用 CHANNEL_FEE_ACCRUAL，银行现金使用 BANK_CASH。",
    audit: "custom",
    fields: [
      primaryKey("id", "会计科目账户ID", "服务侧生成的会计科目账户 Snowflake 主键，也是全局账户 ID"),
      field("ledger_book_id", "BIGINT", "NN", "总账账簿ID", "该账户严格归属的本库经营主体或财务主体币种账簿"),
      field("chart_account_id", "BIGINT", "NN", "会计科目ID", "该账户使用且必须允许过账的本库会计科目定义"),
      field("owner_type", types.code, "NN", "核算对象类型", "决定账户按经营主体、内部财务主体、Client、Local Partner、渠道账户、银行账户或税务机关隔离的类型", { values: LEDGER_OWNER_TYPES }),
      field("owner_id", "VARCHAR(64)", "NN", "核算对象ID", "按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键", ascii),
      field("balance_bucket", types.code, "NN", "余额桶", "PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner 的 PENDING 是共享待结算桶；PARTNER_RECOVERY_RECEIVABLE 使用 NONE；CLIENT_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，CLIENT_RECOVERY_RECEIVABLE/NONE、CLIENT_SECURITY_DEPOSIT/NONE 以及 Channel 三阶段账户均使用独立会计科目并采用 NONE，不复用应付款余额桶", { values: BALANCE_BUCKETS }),
      field("normal_side", types.code, "NN", "正常余额方向", "创建账户时从会计科目复制并冻结的正常余额方向；账户仍允许借贷双向过账", { values: NORMAL_SIDES }),
      field("account_status", types.code, "NN", "账户状态", "该具体核算账户当前是否允许新的会计分录过账", { values: ACCOUNT_STATUS }),
      field("create_time", types.dateTime, "NN", "创建时间", "该记录在 Ledger 数据库中的 UTC 微秒创建时间"),
      field("update_time", types.dateTime, "NN", "更新时间", "该可变记录最后一次持久化更新的 UTC 微秒时间"),
    ],
    indexes: [
      uniqueIndex("uk_account_identity", "ledger_book_id", "chart_account_id", "owner_type", "owner_id", "balance_bucket"),
      accessIndex("idx_account_owner", "owner_type", "owner_id", "ledger_book_id", "balance_bucket"),
      accessIndex("idx_account_chart_status", "chart_account_id", "ledger_book_id", "account_status"),
    ],
  }),
});

/** paytech-messaging 消费端标准表，与 acquiring-schema.sql 逐字一致（design/08 §4）。 */
export const CONSUMED_EVENT = defineTable({
  key: "consumed_event",
  name: "已消费领域事件",
  comment: "已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。",
  audit: "custom",
  fields: [
    field("event_id", "VARCHAR(128)", "NN", "事件ID", "领域事件信封 eventId（默认等于 eventKey），按其去重", { primaryKey: true, ascii: true }),
    field("event_type", "VARCHAR(32)", "NN", "事件类型", "已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）", ascii),
    field("aggregate_type", "VARCHAR(32)", "NN", "聚合类型", "领域事件所属聚合的大写类型代码", ascii),
    field("aggregate_id", "VARCHAR(40)", "NN", "聚合标识", "领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键", ascii),
    field("aggregate_version", "BIGINT", "NN", "聚合版本", "本条已消费领域事件携带的聚合版本"),
    field("consumed_at", types.dateTime, "NN", "消费时间", "消费事务提交的 UTC 微秒精度时间"),
  ],
  indexes: [
    commentedAccessIndex("idx_consumed_event_aggregate", "访问索引：aggregate_type、aggregate_id、aggregate_version",
      "aggregate_type", "aggregate_id", "aggregate_version"),
  ],
});

export const CONSUMED_AGGREGATE_VERSION = defineTable({
  key: "consumed_aggregate_version",
  name: "已消费聚合版本",
  comment: "已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；列、类型与主键与 messaging-consumed-event.sql 一致。",
  audit: "custom",
  fields: [
    field("aggregate_type", "VARCHAR(32)", "NN", "聚合类型", "领域事件所属聚合的大写类型代码", { primaryKey: true, ascii: true }),
    field("aggregate_id", "VARCHAR(40)", "NN", "聚合标识", "领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键", { primaryKey: true, ascii: true }),
    field("last_version", "BIGINT", "NN", "最大版本", "本服务已处理的该聚合最大领域事件版本"),
    field("update_time", types.dateTime, "NN", "更新时间", "数据库记录最近一次持久化更新的 UTC 微秒精度时间"),
  ],
});

const accountAudit = [
  field("create_time", types.dateTime, "NN", "创建时间", "记录创建的 UTC 微秒时间"),
  field("update_time", types.dateTime, "NN", "更新时间", "记录最近一次持久化更新的 UTC 微秒时间"),
];

/** account 库新建表（design/04 §3.2），按建表顺序。 */
export const ACCOUNT_BALANCE_TABLES = Object.freeze([
  defineTable({
    key: "account_balance",
    name: "账户余额",
    comment: "Owner 余额桶的实时权威（design/04 §3.2）：只由 EconomicFactRecorded 消费与按 billing 经济事实重建维护，余额版本 CAS 保护并发写入；与 ledger 分录的一致性由日终核对检查。",
    audit: "custom",
    fields: [
      primaryKey("id", "余额账户ID", "账户服务生成的余额账户 Snowflake 主键；与 ledger 账户身份按 (账簿主体, 科目代码, Owner, 余额桶) 对应，不复用 ledger 账户 ID"),
      field("book_entity_type", "VARCHAR(32)", "NN", "账簿主体类型", "余额所属 ledger 账簿的主体类型", { values: BOOK_ENTITY_TYPES, ascii: true }),
      field("book_entity_id", "VARCHAR(64)", "NN", "账簿主体标识", "余额所属 ledger 账簿的主体标识：OE 为 entityNo，Finance Entity 为 ledger 本地主键十进制字符串；来源 Owner 为 ledger，不建立跨库外键", ascii),
      field("currency", types.currency, "NN", "币种", "余额所属账簿的大写 ISO 4217 币种", ascii),
      field("account_code", "VARCHAR(64)", "NN", "科目代码", "余额对应的 ledger 标准科目代码（如 CLIENT_PAYABLE）；科目名称与属性以 ledger 为准，本表不复制", ascii),
      field("owner_type", types.code, "NN", "核算对象类型", "余额隔离的核算对象类型", { values: BALANCE_OWNER_TYPES }),
      field("owner_id", "VARCHAR(64)", "NN", "核算对象ID", "按 owner_type 解释的规范十进制稳定标识；来源 Owner 为 CCT 或 reconciliation，不建立跨库外键", ascii),
      field("balance_bucket", types.code, "NN", "余额桶", "应付款余额桶；独立科目使用 NONE", { values: BALANCE_BUCKETS }),
      field("normal_side", "VARCHAR(16)", "NN", "正常余额方向", "科目正常余额方向，由映射规则冻结，用于计算净余额", { values: NORMAL_SIDES }),
      field("debit_total", types.amount, "NN", "借方累计", "已应用经济事实累计的借方金额", { defaultValue: "0" }),
      field("credit_total", types.amount, "NN", "贷方累计", "已应用经济事实累计的贷方金额", { defaultValue: "0" }),
      field("net_balance", types.amount, "NN", "当前净余额", "按正常方向计算、允许为负的当前净余额；是 Owner 可用额度的实时权威", { defaultValue: "0" }),
      field("balance_version", "BIGINT", "NN", "余额并发版本", "余额条件更新使用的乐观锁版本，每次写入加一", { defaultValue: "0" }),
      field("last_fact_id", "BIGINT", "N", "末次经济事实ID", "最近一次改变本余额的 billing 经济事实 ID；尚未发生变化时为空"),
      field("last_fact_version", "BIGINT", "N", "末次经济事实版本", "最近一次改变本余额的经济事实版本；尚未发生变化时为空"),
      field("status", "VARCHAR(16)", "NN", "余额账户状态", "余额账户状态", { values: ["ACTIVE"] }),
      ...accountAudit,
    ],
    indexes: [
      uniqueIndex("uk_account_balance_identity", "book_entity_type", "book_entity_id", "currency", "account_code", "owner_type", "owner_id", "balance_bucket"),
      accessIndex("idx_account_balance_owner", "owner_type", "owner_id", "book_entity_id", "currency"),
    ],
  }),
  defineTable({
    key: "balance_applied_fact",
    name: "已处理经济事实",
    comment: "已处理的经济事实（design/04 §3.2）：按 fact_id + fact_version 幂等，并记录版本替代关系，使替代事实可冲回被替代事实的余额变化。",
    audit: "custom",
    fields: [
      primaryKey("id", "已处理事实ID", "账户服务生成的 Snowflake 主键"),
      field("fact_id", "BIGINT", "NN", "经济事实ID", "billing 经济事实 ID（t_economic_fact.id）；来源 Owner 为 billing，不建立跨库外键"),
      field("fact_version", "BIGINT", "NN", "经济事实版本", "经济事实版本；被替代事实先于自身事件到达时按替代事实版本减一登记"),
      field("fact_type", types.code, "N", "经济事实类型", "经济事实类型代码；仅由替代关系预登记、自身事件尚未到达时为空", { source: "billing EconomicsType" }),
      field("supersedes_fact_id", "BIGINT", "N", "被替代事实ID", "本事实替代的前一经济事实；首次登记为空"),
      field("superseded_by_fact_id", "BIGINT", "N", "替代事实ID", "替代本事实的后一经济事实；未被替代时为空"),
      field("entity_no", "VARCHAR(16)", "NN", "经营主体编号", "经济事实头的 Operating Entity 编号，用于按 OE 账簿统计已消费事实", ascii),
      field("finance_entity_id", "BIGINT", "N", "财务主体ID", "经济事实头的平台服务费 Finance Entity；无合作方为空，用于按 Finance Entity 账簿统计已消费事实"),
      field("currency", types.currency, "NN", "币种", "经济事实币种", ascii),
      field("apply_status", "VARCHAR(32)", "NN", "应用状态", "事实对余额的影响状态：APPLIED 已应用；REVERSED 已应用后被替代事实冲回；PENDING_SUPERSEDED 已被替代但自身事件尚未到达；SKIPPED 自身事件到达时已被替代、未应用", { values: ["APPLIED", "REVERSED", "PENDING_SUPERSEDED", "SKIPPED"] }),
      field("lines_hash", types.hash, "N", "余额变化摘要", "已应用余额变化的规范文本 SHA-256 小写十六进制；仅预登记、尚未处理时为空", ascii),
      field("applied_time", types.dateTime, "N", "处理时间", "事实被本服务处理的 UTC 微秒时间；仅预登记时为空"),
      ...accountAudit,
    ],
    indexes: [
      uniqueIndex("uk_balance_applied_fact", "fact_id", "fact_version"),
      accessIndex("idx_applied_fact_oe_book", "entity_no", "currency", "fact_id"),
      accessIndex("idx_applied_fact_fe_book", "finance_entity_id", "currency", "fact_id"),
    ],
  }),
  defineTable({
    key: "balance_applied_entry",
    name: "已应用余额变化",
    comment: "不可变的已应用余额变化：一条经济事实映射到余额账户的逐行借贷，供替代事实冲回与重建核对。",
    audit: "custom",
    fields: [
      primaryKey("id", "余额变化ID", "账户服务生成的 Snowflake 主键"),
      field("applied_fact_id", "BIGINT", "NN", "已处理事实ID", "所属已处理事实（t_balance_applied_fact.id）"),
      field("fact_id", "BIGINT", "NN", "经济事实ID", "billing 经济事实 ID，冗余保存便于按事实冲回"),
      field("line_no", "INTEGER", "NN", "明细行号", "产生该变化的经济事实明细行号；交易事实的总额（GROSS_AMOUNT）取自事实头，行号为 0"),
      field("balance_id", "BIGINT", "NN", "余额账户ID", "被改变的余额账户（t_account_balance.id）"),
      field("entry_side", "VARCHAR(16)", "NN", "借贷方向", "对余额账户的借贷方向", { values: NORMAL_SIDES }),
      field("amount", types.amount, "NN", "金额", "已按币种精度归整的正金额"),
      field("create_time", types.dateTime, "NN", "创建时间", "记录创建的 UTC 微秒时间"),
    ],
    indexes: [
      accessIndex("idx_applied_entry_fact", "applied_fact_id"),
      accessIndex("idx_applied_entry_balance", "balance_id"),
    ],
  }),
  defineTable({
    key: "balance_movement",
    name: "余额桶移动",
    comment: "账户侧余额桶移动命令记录（AVAILABLE → LOCKED 等），移动后发布 BalanceMoved 供 ledger 补记分录（design/04 §5）；阶段 0 只建表，无生产调用方。",
    audit: "custom",
    fields: [
      primaryKey("id", "余额移动ID", "账户服务生成的 Snowflake 主键"),
      field("book_entity_type", "VARCHAR(32)", "NN", "账簿主体类型", "余额所属 ledger 账簿的主体类型", { values: BOOK_ENTITY_TYPES, ascii: true }),
      field("book_entity_id", "VARCHAR(64)", "NN", "账簿主体标识", "余额所属 ledger 账簿的主体标识", ascii),
      field("currency", types.currency, "NN", "币种", "移动金额的币种", ascii),
      field("movement_sequence", "BIGINT", "NN", "移动序号", "同一账簿主体与币种内连续递增的移动序号，即 BalanceMoved 的 aggregateVersion"),
      field("account_code", "VARCHAR(64)", "NN", "科目代码", "移动所在的标准科目代码（CLIENT_PAYABLE 或 PARTNER_PAYABLE）", ascii),
      field("owner_type", types.code, "NN", "核算对象类型", "核算对象类型", { values: ["CLIENT", "LOCAL_PARTNER"] }),
      field("owner_id", "VARCHAR(64)", "NN", "核算对象ID", "核算对象规范十进制标识", ascii),
      field("from_bucket", types.code, "NN", "转出余额桶", "转出余额桶", { values: PAYABLE_BUCKETS }),
      field("to_bucket", types.code, "NN", "转入余额桶", "转入余额桶", { values: PAYABLE_BUCKETS }),
      field("amount", types.amount, "NN", "金额", "已按币种精度归整的正移动金额"),
      field("source_type", types.code, "NN", "来源类型", "发起移动的业务来源类型（如出款锁定、成熟释放）", { source: "account 移动来源目录", ascii: true }),
      field("source_ref", "VARCHAR(128)", "NN", "来源引用", "来源业务的稳定引用，同一来源只移动一次", ascii),
      field("movement_status", "VARCHAR(16)", "NN", "移动状态", "移动命令状态", { values: ["APPLIED"] }),
      ...accountAudit,
    ],
    indexes: [
      uniqueIndex("uk_balance_movement_source", "source_type", "source_ref"),
      uniqueIndex("uk_balance_movement_sequence", "book_entity_type", "book_entity_id", "currency", "movement_sequence"),
    ],
  }),
  defineTable({
    key: "balance_discrepancy",
    name: "余额核对差异",
    comment: "日终核对发现的账户余额与 ledger 分录聚合余额差异（design/04 §7）；裁决依据是 billing 经济事实，阶段 0 不接工单。",
    audit: "custom",
    fields: [
      primaryKey("id", "核对差异ID", "账户服务生成的 Snowflake 主键"),
      field("recon_date", "DATE", "NN", "核对日期", "核对任务执行的 UTC 日期"),
      field("ledger_book_id", "BIGINT", "NN", "总账账簿ID", "被核对的 ledger 账簿主键；来源 Owner 为 ledger，不建立跨库外键"),
      field("book_entity_type", "VARCHAR(32)", "NN", "账簿主体类型", "账簿主体类型", { values: BOOK_ENTITY_TYPES, ascii: true }),
      field("book_entity_id", "VARCHAR(64)", "NN", "账簿主体标识", "账簿主体标识", ascii),
      field("currency", types.currency, "NN", "币种", "账簿币种", ascii),
      field("account_code", "VARCHAR(64)", "NN", "科目代码", "存在差异的标准科目代码", ascii),
      field("owner_type", types.code, "NN", "核算对象类型", "核算对象类型", { values: BALANCE_OWNER_TYPES }),
      field("owner_id", "VARCHAR(64)", "NN", "核算对象ID", "核算对象标识", ascii),
      field("balance_bucket", types.code, "NN", "余额桶", "余额桶", { values: BALANCE_BUCKETS }),
      field("account_net_balance", types.amount, "N", "账户服务净额", "账户服务余额的净额；账户服务没有该余额时为空"),
      field("ledger_net_balance", types.amount, "N", "ledger 净额", "ledger 按分录聚合的净额；ledger 没有该账户时为空"),
      field("difference_amount", types.amount, "NN", "差异金额", "两侧净额差的绝对值，缺失一侧按零计"),
      field("status", "VARCHAR(16)", "NN", "处置状态", "差异处置状态；阶段 0 不接工单，只记录", { values: ["OPEN"] }),
      ...accountAudit,
    ],
    indexes: [
      uniqueIndex("uk_balance_discrepancy", "recon_date", "ledger_book_id", "account_code", "owner_type", "owner_id", "balance_bucket"),
      accessIndex("idx_balance_discrepancy_status", "status", "recon_date"),
    ],
  }),
]);

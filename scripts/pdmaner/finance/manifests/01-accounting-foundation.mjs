import {
  accessIndex,
  defineTable,
  field,
  primaryKey,
  relation,
  types,
  uniqueIndex,
} from "../schema-dsl.mjs";

const ACTIVE_STATUS = ["DRAFT", "ACTIVE", "INACTIVE"];
const ACCOUNT_STATUS = ["ACTIVE", "INACTIVE", "CLOSED"];
const ACCOUNT_OWNER_TYPES = [
  "OPERATING_ENTITY",
  "FINANCE_ENTITY",
  "CLIENT",
  "LOCAL_PARTNER",
  "CHANNEL_ACCOUNT",
  "CASH_ACCOUNT",
  "TAX_AUTHORITY",
];
const BALANCE_BUCKETS = ["PENDING", "AVAILABLE", "LOCKED", "NONE"];
const NORMAL_SIDES = ["DEBIT", "CREDIT"];
const LEDGER_ENTITY_TYPES = ["OPERATING_ENTITY", "FINANCE_ENTITY"];

export const TABLES = Object.freeze([
  defineTable({
    key: "currency_spec",
    name: "币种规则",
    comment: "只读副本，Owner platform-support（D41）：保存 Finance 使用的 ISO 4217 币种精度、舍入方式和启停状态；ledger 与 billing 各持一份，不在本服务写入。",
    fields: [
      primaryKey("id", "币种规则ID", "服务侧生成的币种规则 Snowflake 主键"),
      field("currency", types.currency, "NN", "币种", "ISO 4217 三位币种代码", { source: "ISO 4217 币种代码" }),
      field("minor_unit", "TINYINT", "NN", "最小货币单位位数", "记账与结算金额允许保留的小数位数，取值范围为零至八"),
      field("rounding_mode", types.code, "NN", "舍入方式", "金额从八位计算精度归整到币种精度时采用的固定方式", { values: ["HALF_UP"] }),
      field("status", types.code, "NN", "状态", "币种规则当前是否允许被新业务使用", { values: ACTIVE_STATUS }),
    ],
    indexes: [uniqueIndex("uk_currency", "currency")],
  }),

  defineTable({
    key: "chart_of_accounts",
    name: "会计科目表",
    comment: "保存可供账簿采用的稳定多级会计科目目录。",
    fields: [
      primaryKey("id", "会计科目表ID", "服务侧生成的会计科目表 Snowflake 主键"),
      field("chart_code", types.code, "NN", "科目表代码", "科目表稳定且唯一的业务代码", { source: "Finance 会计科目表代码" }),
      field("chart_name", types.name, "NN", "科目表名称", "供财务人员识别该科目目录的中文名称"),
      field("status", types.code, "NN", "状态", "科目表当前是否允许新账簿采用", { values: ["DRAFT", "ACTIVE", "INACTIVE"] }),
    ],
    indexes: [uniqueIndex("uk_chart_code", "chart_code")],
  }),

  defineTable({
    key: "chart_account",
    name: "会计科目定义",
    comment: "定义稳定会计科目表中的五类多级科目及其正常余额方向和可过账属性。",
    fields: [
      primaryKey("id", "会计科目ID", "服务侧生成的会计科目 Snowflake 主键"),
      field("chart_id", "BIGINT", "NN", "会计科目表ID", "该科目所属的本库会计科目表"),
      field("parent_account_id", "BIGINT", "N", "上级科目ID", "该科目的直接上级科目，顶级科目为空；科目表被任一账簿采用后既有父子关系不可修改"),
      field("account_code", types.code, "NN", "科目代码", "在同一会计科目表内唯一且激活后不可改变的科目代码", { source: "Finance 会计科目代码" }),
      field("account_name", types.name, "NN", "科目名称", "财务报表和记账界面使用的科目名称"),
      field("account_class", types.code, "NN", "科目类别", "会计恒等式中的五类科目分类", { values: ["ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"] }),
      field("normal_side", types.code, "NN", "正常余额方向", "该科目余额增加时采用的借贷方向", { values: NORMAL_SIDES }),
      field("posting_allowed", "BOOLEAN", "NN", "是否允许过账", "是否允许 Journal Line 直接记入该科目的结构属性；汇总父科目必须为否，科目表被账簿采用后不可修改"),
    ],
    indexes: [
      uniqueIndex("uk_chart_acct", "chart_id", "account_code"),
      accessIndex("idx_chart_acct_parent", "parent_account_id"),
      accessIndex("idx_chart_acct_class", "chart_id", "account_class"),
    ],
    relations: [
      relation("chart_id", "chart_of_accounts", "id"),
      relation("parent_account_id", "chart_account", "id"),
    ],
  }),

  defineTable({
    key: "finance_entity",
    name: "内部财务主体",
    comment: "保存 Finance 受控人工维护的内部财务主体，并以唯一法律 Party 稳定标识映射 CCT 中的平台签约法律主体；不复制法律资料。",
    fields: [
      primaryKey("id", "内部财务主体ID", "服务侧生成的内部财务主体 Snowflake 主键"),
      field("entity_code", types.code, "NN", "财务主体代码", "Finance 内部人工分配且永久唯一的财务主体代码", { source: "Finance Entity Code" }),
      field("legal_party_id", "BIGINT", "NN", "法律主体ID", "CCT Party 的稳定业务ID；由 Partner Agreement 唯一 PLATFORM_SIGNATORY 提供，不建立跨库外键"),
      field("display_name", "VARCHAR(256)", "NN", "显示名称", "供 Finance 内部操作和审计识别该财务主体的显示名称，不替代 CCT 法律资料"),
      field("status", types.code, "NN", "状态", "控制该财务主体能否用于创建新账簿和新交易；关闭后不得恢复激活", { values: ["ACTIVE", "CLOSED"] }),
    ],
    indexes: [
      uniqueIndex("uk_fin_entity_code", "entity_code"),
      uniqueIndex("uk_fin_entity_party", "legal_party_id"),
    ],
  }),

  defineTable({
    key: "ledger_book",
    name: "总账账簿",
    comment: "为单一 Operating Entity 或 Finance Entity 和单一币种建立独立会计账簿；主体类型与标识创建后不可修改。",
    fields: [
      primaryKey("id", "总账账簿ID", "服务侧生成的总账账簿 Snowflake 主键"),
      field("entity_type", types.code, "NN", "账簿主体类型", "区分由 CCT Operating Entity 或 Finance 本地内部财务主体拥有的账簿", { values: LEDGER_ENTITY_TYPES }),
      field("entity_id", "VARCHAR(64)", "NN", "账簿主体ID", "按主体类型解释的稳定业务ID；Operating Entity 来自 CCT，Finance Entity 使用本地主键，不建立多态外键"),
      field("currency", types.currency, "NN", "账簿币种", "该账簿唯一使用的 ISO 4217 三位币种代码", { source: "ISO 4217 币种代码" }),
      field("chart_id", "BIGINT", "NN", "会计科目表ID", "该账簿采用的本库稳定会计科目表"),
      field("book_status", types.code, "NN", "账簿状态", "账簿是否允许接收新的会计分录", { values: ["OPEN", "SUSPENDED", "CLOSED"] }),
      field("version", "BIGINT", "NN", "并发版本号", "更新账簿状态时进行条件更新的乐观锁版本号", { defaultValue: "0" }),
    ],
    indexes: [
      uniqueIndex("uk_ledger_book_entity", "entity_type", "entity_id", "currency"),
      accessIndex("idx_ledger_book_chart", "chart_id"),
      accessIndex("idx_ledger_book_status", "book_status"),
    ],
    relations: [relation("chart_id", "chart_of_accounts", "id")],
  }),

  defineTable({
    key: "account",
    name: "统一记账账户",
    comment: "在具体账簿和可过账科目下按核算对象与余额桶建立唯一记账账户，并保存可由不可变 Journal 重建的当前余额投影；CLIENT_PAYABLE 与 PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner PENDING 是共享待结算桶；Partner reversal 无法由 PENDING/AVAILABLE/LOCKED liability 吸收时使用资产科目 PARTNER_RECOVERY_RECEIVABLE/NONE；Client Recovery 使用 CLIENT_RECOVERY_RECEIVABLE/NONE，保证金使用 CLIENT_SECURITY_DEPOSIT/NONE；Channel 使用 CHANNEL_UNSETTLED_RECEIVABLE、CHANNEL_SETTLEMENT_RECEIVABLE、CHANNEL_UNSETTLED_PAYABLE、CHANNEL_SETTLEMENT_PAYABLE 四个科目表达交易形成、交易对账完整和银行确认三个阶段，Channel Cost 单独使用 CHANNEL_FEE_ACCRUAL，银行现金使用 BANK_CASH。",
    fields: [
      primaryKey("id", "统一记账账户ID", "服务侧生成的统一记账账户 Snowflake 主键"),
      field("ledger_book_id", "BIGINT", "NN", "总账账簿ID", "该账户严格归属的本库经营主体币种账簿"),
      field("chart_account_id", "BIGINT", "NN", "会计科目ID", "该账户使用且必须允许过账的本库会计科目定义"),
      field("owner_type", types.code, "NN", "核算对象类型", "决定账户余额按经营主体、内部财务主体、Client、Local Partner、渠道账户、银行账户或税务机关隔离的类型", { values: ACCOUNT_OWNER_TYPES }),
      field("owner_id", "VARCHAR(64)", "NN", "核算对象ID", "按owner_type解释的稳定字符串；OE为entityNo，其余数字身份保存规范十进制字符串；本库对象使用本地主键，外部对象不建立跨库外键"),
      field("balance_bucket", types.code, "NN", "余额桶", "PARTNER_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，其中 Partner 的 PENDING 是共享待结算桶；PARTNER_RECOVERY_RECEIVABLE 使用 NONE；CLIENT_PAYABLE 使用 PENDING、AVAILABLE、LOCKED，CLIENT_RECOVERY_RECEIVABLE/NONE、CLIENT_SECURITY_DEPOSIT/NONE 以及 Channel 三阶段账户均使用独立会计科目并采用 NONE，不复用应付款余额桶", { values: BALANCE_BUCKETS }),
      field("normal_side", types.code, "NN", "正常余额方向", "创建账户时从会计科目复制并冻结的正常余额方向；账户仍允许借贷双向过账", { values: NORMAL_SIDES }),
      field("account_status", types.code, "NN", "账户状态", "该具体核算账户当前是否允许新的会计分录过账", { values: ACCOUNT_STATUS }),
      field("debit_total", types.amount, "NN", "借方累计", "截至末次分录序号已计入该账户的累计借方金额", {
        defaultValue: "0",
      }),
      field("credit_total", types.amount, "NN", "贷方累计", "截至末次分录序号已计入该账户的累计贷方金额", {
        defaultValue: "0",
      }),
      field("net_balance", types.amount, "NN", "当前净余额", "按照本账户正常余额方向计算且可由不可变分录重建的有符号余额；正数表示正常方向，负数表示反向余额", {
        defaultValue: "0",
      }),
      field("last_journal_sequence", "BIGINT", "NN", "末次分录序号", "已经应用到账户余额投影的本账簿最后分录序号", {
        defaultValue: "0",
      }),
      field("balance_version", "BIGINT", "NN", "余额并发版本", "仅用于账户余额投影顺序条件更新的乐观锁版本，不表示账户身份版本", {
        defaultValue: "0",
      }),
    ],
    indexes: [
      uniqueIndex("uk_account_identity", "ledger_book_id", "chart_account_id", "owner_type", "owner_id", "balance_bucket"),
      accessIndex("idx_account_owner", "owner_type", "owner_id", "ledger_book_id", "balance_bucket"),
      accessIndex("idx_account_chart_status", "chart_account_id", "ledger_book_id", "account_status"),
    ],
    relations: [
      relation("ledger_book_id", "ledger_book", "id"),
      relation("chart_account_id", "chart_account", "id"),
    ],
  }),

  defineTable({
    key: "fiscal_calendar",
    name: "财务日历",
    comment: "保存 Operating Entity 或 Finance Entity 使用的财年起始月份和 IANA 时区规则；主体类型与标识创建后不可修改。",
    fields: [
      primaryKey("id", "财务日历ID", "服务侧生成的财务日历 Snowflake 主键"),
      field("entity_type", types.code, "NN", "日历主体类型", "区分使用该财务日历的 CCT Operating Entity 或 Finance 本地内部财务主体", { values: LEDGER_ENTITY_TYPES }),
      field("entity_id", "VARCHAR(64)", "NN", "日历主体ID", "按主体类型解释的稳定业务ID；Finance Entity 使用本地主键且不建立多态外键"),
      field("calendar_code", types.code, "NN", "日历代码", "V1 中该经营主体唯一法定财务日历的稳定展示和接口代码", { source: "Finance 财务日历代码" }),
      field("timezone", "VARCHAR(64)", "NN", "时区", "用于确定财务日期边界的 IANA 时区代码", { source: "IANA 时区数据库" }),
      field("year_start_month", "TINYINT", "NN", "财年起始月份", "财年开始月份，取值范围为一至十二"),
      field("version", "BIGINT", "NN", "并发版本号", "更新财务日历规则时进行条件更新的乐观锁版本号", { defaultValue: "0" }),
    ],
    indexes: [uniqueIndex("uk_fiscal_cal", "entity_type", "entity_id")],
  }),

  defineTable({
    key: "accounting_period",
    name: "会计期间",
    comment: "保存财务日历下可过账、关闭或重开的会计期间。",
    fields: [
      primaryKey("id", "会计期间ID", "服务侧生成的会计期间 Snowflake 主键"),
      field("calendar_id", "BIGINT", "NN", "财务日历ID", "该会计期间所属的本库财务日历"),
      field("fiscal_year", "INT", "NN", "财年", "该期间所属的四位财务年度"),
      field("period_no", "SMALLINT", "NN", "期间序号", "同一财年中严格递增的会计期间序号"),
      field("start_date", "DATE", "NN", "开始日期", "该会计期间包含的首个业务日期"),
      field("end_date", "DATE", "NN", "结束日期", "该会计期间包含的最后一个业务日期"),
      field("status", types.code, "NN", "期间状态", "控制该期间是否允许新会计分录过账", { values: ["FUTURE", "OPEN", "CLOSING", "CLOSED", "REOPENING"] }),
      field("reopen_count", "INT", "NN", "重开次数", "该期间完成受控重开的累计次数", { defaultValue: "0" }),
      field("version", "BIGINT", "NN", "并发版本号", "更新期间状态时进行条件更新的乐观锁版本号", { defaultValue: "0" }),
    ],
    indexes: [
      uniqueIndex("uk_account_period", "calendar_id", "fiscal_year", "period_no"),
      accessIndex("idx_period_date", "calendar_id", "start_date", "end_date"),
      accessIndex("idx_period_status", "calendar_id", "status"),
    ],
    relations: [relation("calendar_id", "fiscal_calendar", "id")],
  }),

  defineTable({
    key: "book_sequence",
    name: "账簿分录序列",
    comment: "为每个总账账簿在 Journal 过账本地事务中通过行锁分配严格递增的 Journal 序号；已提交 Journal 的序号不可复用。",
    fields: [
      primaryKey("id", "账簿分录序列ID", "服务侧生成的账簿分录序列 Snowflake 主键"),
      field("ledger_book_id", "BIGINT", "NN", "总账账簿ID", "使用该分录序列的本库总账账簿"),
      field("next_value", "BIGINT", "NN", "下一个序号", "下一次在 Journal 过账本地事务中通过 SELECT FOR UPDATE 锁定本行后可分配的正整数序号"),
    ],
    indexes: [uniqueIndex("uk_book_seq", "ledger_book_id")],
    relations: [relation("ledger_book_id", "ledger_book", "id", "1:1")],
  }),
]);

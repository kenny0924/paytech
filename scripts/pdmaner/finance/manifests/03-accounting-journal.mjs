import {
  accessIndex,
  defineTable,
  field,
  primaryKey,
  relation,
  types,
  uniqueIndex,
} from "../schema-dsl.mjs";

const STATUS_RULE_ROOT = ["DRAFT", "ACTIVE", "INACTIVE"];
const STATUS_RULE_VERSION = ["DRAFT", "PENDING_APPROVAL", "APPROVED", "ACTIVE", "REJECTED", "RETIRED"];
const APPROVAL_ROLES = ["MAKER", "CHECKER"];
const APPROVAL_DECISIONS = ["APPROVED", "REJECTED"];
const ENTRY_SIDES = ["DEBIT", "CREDIT"];
const BALANCE_BUCKETS = ["PENDING", "AVAILABLE", "LOCKED", "NONE"];

export const TABLES = Object.freeze([
  defineTable({
    key: "accounting_rule_bundle",
    name: "自动记账规则集",
    comment: "维护自动记账规则集的稳定身份和当前生效版本指针，不保存可执行表达式",
    fields: [
      primaryKey("id", "规则集主键", "自动记账规则集的服务侧雪花主键"),
      field("bundle_code", types.code, "NN", "规则集代码", "自动记账规则集的稳定业务代码", {
        source: "Finance 自动记账规则集代码目录",
      }),
      field("current_ver_id", "BIGINT", "N", "当前版本主键", "当前生效的自动记账规则版本主键"),
      field("status", types.code, "NN", "规则集状态", "自动记账规则集当前生命周期状态", {
        values: STATUS_RULE_ROOT,
      }),
      field("version", "BIGINT", "NN", "并发版本", "规则集条件更新使用的乐观锁版本", {
        defaultValue: "0",
      }),
    ],
    indexes: [
      uniqueIndex("uk_acct_rule_bundle", "bundle_code"),
      uniqueIndex("uk_acct_bundle_current", "current_ver_id"),
      accessIndex("idx_acct_bundle_status", "status"),
    ],
    relations: [relation("current_ver_id", "accounting_rule_bundle_ver", "id", "1:1")],
  }),

  defineTable({
    key: "accounting_rule_bundle_ver",
    name: "自动记账规则版本",
    comment: "保存一版自动记账规则的生效区间、发布状态和固定双人审批结果；提交审批时冻结完整有序规则行集，所有追加、提交、审批与当前指针切换事务统一先锁定 Bundle 再锁定 Version",
    fields: [
      primaryKey("id", "规则版本主键", "自动记账规则版本的服务侧雪花主键"),
      field("bundle_id", "BIGINT", "NN", "规则集主键", "所属自动记账规则集主键"),
      field("bundle_ver", "BIGINT", "NN", "规则版本号", "同一规则集内严格递增的业务版本号"),
      field("effective_from", types.dateTime, "NN", "生效时间", "该规则版本开始适用的 UTC 微秒时间"),
      field("effective_to", types.dateTime, "N", "失效时间", "该规则版本停止适用的 UTC 微秒时间，空值表示尚未失效"),
      field("status", types.code, "NN", "规则版本状态", "自动记账规则版本的审批和生效状态", {
        values: STATUS_RULE_VERSION,
      }),
      field("maker_id", "BIGINT", "NN", "编制人主键", "创建并提交该规则版本的平台用户主键"),
      field("checker_id", "BIGINT", "N", "复核人主键", "审批该规则版本的平台用户主键，必须与编制人不同"),
      field("approved_time", types.dateTime, "N", "批准时间", "复核人批准该规则版本的 UTC 微秒时间；未批准或已驳回时为空"),
    ],
    indexes: [
      uniqueIndex("uk_acct_rule_ver", "bundle_id", "bundle_ver"),
      accessIndex("idx_acct_ver_status", "status", "effective_from"),
    ],
    relations: [relation("bundle_id", "accounting_rule_bundle", "id")],
  }),

  defineTable({
    key: "accounting_rule_line",
    name: "自动记账规则行",
    comment: "把一个已计算金额来源确定性映射为借贷方向、科目、核算对象角色和余额桶，不承担金额计算；仅所属版本为 DRAFT 时可追加，版本离开 DRAFT 后禁止新增且规则行自身不可更新或删除",
    audit: "immutable",
    fields: [
      primaryKey("id", "规则行主键", "自动记账规则行的服务侧雪花主键"),
      field("bundle_ver_id", "BIGINT", "NN", "规则版本主键", "所属自动记账规则版本主键"),
      field("instruction_type", types.code, "NN", "记账指令类型", "该规则行适用的会计指令业务类型", {
        source: "Finance 会计指令类型目录",
      }),
      field("line_no", "INTEGER", "NN", "规则行序号", "同一规则版本和指令类型内稳定且有序的规则行序号"),
      field("line_code", types.code, "NN", "规则行代码", "同一规则版本和指令类型内稳定识别该会计含义的代码", {
        source: "Finance 自动记账规则行代码目录",
      }),
      field("amount_source_code", types.code, "NN", "金额来源代码", "指向交易经济组件或受控业务金额的稳定代码；同一来源允许生成多条借贷规则行；渠道成本差额分别使用 PROVIDER_COST_INCREASE 和 PROVIDER_COST_DECREASE", {
        source: "Finance 自动记账金额来源目录",
      }),
      field("owner_role", types.code, "NN", "账户核算对象角色", "由固定解释器解析统一账户核算对象类型和稳定ID的业务参与方角色", {
        source: "Finance 记账账户所有者角色目录",
      }),
      field("account_code", types.code, "NN", "科目代码", "在适用账簿科目表中必须唯一解析的叶子科目代码", {
        source: "Finance 会计科目代码目录",
      }),
      field("balance_bucket", types.code, "NN", "余额桶", "统一账户使用的余额桶；不区分运营状态的科目固定使用 NONE", {
        values: BALANCE_BUCKETS,
      }),
      field("entry_side", types.code, "NN", "借贷方向", "该规则行生成分录行的借方或贷方方向", {
        values: ENTRY_SIDES,
      }),
      field("required_flag", "BOOLEAN", "NN", "必需标志", "为 1 时金额来源缺失即拒绝生成指令，为 0 时来源缺失可跳过该规则行；来源存在但金额为零时不视为缺失，对同一金额来源的规则行整体跳过且不生成零金额指令行"),
    ],
    indexes: [
      uniqueIndex("uk_acct_rule_line", "bundle_ver_id", "instruction_type", "line_no"),
      uniqueIndex("uk_acct_rule_line_code", "bundle_ver_id", "instruction_type", "line_code"),
      accessIndex("idx_acct_rule_amount", "bundle_ver_id", "instruction_type", "amount_source_code"),
    ],
    relations: [relation("bundle_ver_id", "accounting_rule_bundle_ver", "id")],
  }),

  defineTable({
    key: "accounting_instruction",
    name: "会计记账指令",
    comment: "维护一个自动业务事实或已批准人工调整在单一账簿产生的平衡记账指令组及其验证、入账状态；同一服务费以 PARTNER 与 PLATFORM 两类指令分别绑定两本账",
    fields: [
      primaryKey("id", "记账指令主键", "会计记账指令的服务侧雪花主键"),
      field("ledger_book_id", "BIGINT", "NN", "账簿主键", "该指令唯一适用的本库 Ledger Book；全部指令行账户和最终 Journal Entry 必须逐值属于同一账簿"),
      field("instruction_type", types.code, "NN", "记账指令类型", "该指令所表达的业务会计动作类型", {
        source: "Finance 会计指令类型目录",
      }),
      field("origin_type", types.code, "NN", "指令来源类型", "区分规则自动生成和人工调整生成的指令", {
        values: ["AUTO", "MANUAL"],
      }),
      field("source_system", types.code, "N", "来源系统", "自动指令所消费权威事件的来源系统，人工指令为空", {
        source: "Code-MoR 系统代码目录",
      }),
      field("source_event_key", types.longCode, "N", "来源事件键", "自动指令所消费权威事件的稳定事件键，人工指令为空"),
      field("source_version", "BIGINT", "N", "来源版本", "自动指令所消费权威事件或事实的版本，人工指令为空"),
      field("source_payload_hash", types.hash, "N", "来源载荷摘要", "自动指令来源规范载荷的 SHA-256 小写十六进制摘要，人工指令为空"),
      field("transaction_economics_id", "BIGINT", "N", "交易经济结果主键", "自动交易类指令对应的本库不可变交易经济结果主键"),
      field("manual_adjustment_id", "BIGINT", "N", "人工调整主键", "人工指令对应且已经固定双人审批的人工调整主键"),
      field("accounting_rule_bundle_ver_id", "BIGINT", "N", "规则版本主键", "自动指令冻结使用的自动记账规则版本主键"),
      field("input_hash", types.hash, "N", "指令输入摘要", "自动指令确定性输入或人工调整冻结内容的 SHA-256 小写十六进制摘要"),
      field("status", types.code, "NN", "记账指令状态", "记账指令当前验证和入账状态", {
        values: ["RECEIVED", "VALIDATING", "VALIDATED", "POSTED", "REJECTED", "CONFLICT"],
      }),
      field("maker_id", "BIGINT", "N", "编制人主键", "人工指令对应的调整编制人主键，自动指令为空"),
      field("checker_id", "BIGINT", "N", "复核人主键", "人工指令对应的调整复核人主键，自动指令为空且必须与编制人不同"),
      field("version", "BIGINT", "NN", "并发版本", "记账指令状态条件更新使用的乐观锁版本", {
        defaultValue: "0",
      }),
    ],
    indexes: [
      uniqueIndex("uk_instr_source", "source_system", "source_event_key", "instruction_type"),
      uniqueIndex("uk_instr_manual", "manual_adjustment_id"),
      accessIndex("idx_instr_book_status", "ledger_book_id", "status", "create_time"),
      accessIndex("idx_instr_economics", "transaction_economics_id"),
      accessIndex("idx_instr_rule_ver", "accounting_rule_bundle_ver_id"),
      accessIndex("idx_instr_status", "status", "create_time"),
    ],
    relations: [
      relation("ledger_book_id", "ledger_book", "id"),
      relation("transaction_economics_id", "transaction_economics", "id"),
      relation("manual_adjustment_id", "manual_adjustment", "id", "1:1"),
      relation("accounting_rule_bundle_ver_id", "accounting_rule_bundle_ver", "id"),
    ],
  }),

  defineTable({
    key: "instruction_line",
    name: "记账指令明细",
    comment: "追加保存已解析到唯一统一记账账户的借贷指令行；全部账户必须属于 Instruction Header 指定的同一 Ledger Book",
    audit: "immutable",
    fields: [
      primaryKey("id", "指令明细主键", "记账指令明细的服务侧雪花主键"),
      field("instruction_id", "BIGINT", "NN", "记账指令主键", "所属会计记账指令主键"),
      field("line_no", "INTEGER", "NN", "明细序号", "同一记账指令内稳定且有序的明细序号"),
      field("accounting_rule_line_id", "BIGINT", "N", "自动记账规则行主键", "自动指令行采用的自动记账规则行主键"),
      field("manual_adjustment_line_id", "BIGINT", "N", "人工调整明细主键", "人工指令行一一对应的已批准人工调整明细主键"),
      field("account_id", "BIGINT", "NN", "统一记账账户主键", "该指令行准备写入且已冻结账簿、科目、核算对象和余额桶的本库账户"),
      field("entry_side", types.code, "NN", "借贷方向", "指令行的借方或贷方方向", {
        values: ENTRY_SIDES,
      }),
      field("amount", types.amount, "NN", "记账金额", "按币种精度舍入后且必须大于零的记账金额"),
    ],
    indexes: [
      uniqueIndex("uk_instr_line", "instruction_id", "line_no"),
      uniqueIndex("uk_instr_adj_line", "manual_adjustment_line_id"),
      accessIndex("idx_instr_line_rule", "accounting_rule_line_id"),
      accessIndex("idx_instr_line_account", "account_id"),
    ],
    relations: [
      relation("instruction_id", "accounting_instruction", "id"),
      relation("accounting_rule_line_id", "accounting_rule_line", "id"),
      relation("manual_adjustment_line_id", "manual_adjustment_line", "id", "1:1"),
      relation("account_id", "account", "id"),
    ],
  }),

  defineTable({
    key: "journal_entry",
    name: "会计分录",
    comment: "追加保存一条记账指令在单一账簿和开放期间内形成的不可变会计分录头",
    audit: "immutable",
    fields: [
      primaryKey("id", "会计分录主键", "会计分录的服务侧雪花主键"),
      field("ledger_book_id", "BIGINT", "NN", "账簿主键", "该不可变分录所属的账簿主键"),
      field("accounting_period_id", "BIGINT", "NN", "会计期间主键", "分录过账日期所属且过账时必须开放的会计期间主键"),
      field("instruction_id", "BIGINT", "NN", "记账指令主键", "产生该会计分录的已验证记账指令主键"),
      field("journal_sequence", "BIGINT", "NN", "账簿分录序号", "在所属账簿内原子取得且严格递增的分录序号"),
      field("posting_date", "DATE", "NN", "过账日期", "该会计分录所属的业务过账日期"),
      field("entry_type", types.code, "NN", "分录类型", "区分标准、冲回、调整和关账类分录的受控类型", {
        values: ["STANDARD", "REVERSAL", "ADJUSTMENT", "CLOSING", "REOPENING"],
      }),
      field("reversal_of_entry_id", "BIGINT", "N", "原分录主键", "冲回分录严格反向引用的原不可变会计分录主键"),
    ],
    indexes: [
      uniqueIndex("uk_journal_seq", "ledger_book_id", "journal_sequence"),
      uniqueIndex("uk_journal_instr", "instruction_id", "ledger_book_id"),
      uniqueIndex("uk_journal_reversal", "reversal_of_entry_id"),
      accessIndex("idx_journal_period", "accounting_period_id", "posting_date"),
    ],
    relations: [
      relation("ledger_book_id", "ledger_book", "id"),
      relation("accounting_period_id", "accounting_period", "id"),
      relation("instruction_id", "accounting_instruction", "id"),
      relation("reversal_of_entry_id", "journal_entry", "id", "1:1"),
    ],
  }),

  defineTable({
    key: "journal_line",
    name: "会计分录明细",
    comment: "追加保存会计分录中不可变的借贷行，并通过统一账户保持账簿、科目、核算对象和余额桶可追溯",
    audit: "immutable",
    fields: [
      primaryKey("id", "分录明细主键", "会计分录明细的服务侧雪花主键"),
      field("journal_entry_id", "BIGINT", "NN", "会计分录主键", "所属不可变会计分录主键"),
      field("line_no", "INTEGER", "NN", "明细序号", "同一会计分录内稳定且有序的明细序号"),
      field("instruction_line_id", "BIGINT", "NN", "指令明细主键", "生成该分录明细的记账指令明细主键"),
      field("account_id", "BIGINT", "NN", "统一记账账户主键", "该分录明细实际记入且必须属于分录头账簿的本库统一账户"),
      field("entry_side", types.code, "NN", "借贷方向", "该分录明细的借方或贷方方向", {
        values: ENTRY_SIDES,
      }),
      field("amount", types.amount, "NN", "记账金额", "按币种精度舍入且必须大于零的不可变分录金额"),
    ],
    indexes: [
      uniqueIndex("uk_journal_line", "journal_entry_id", "line_no"),
      uniqueIndex("uk_journal_instruction_line", "instruction_line_id"),
      accessIndex("idx_journal_line_account", "account_id"),
    ],
    relations: [
      relation("journal_entry_id", "journal_entry", "id"),
      relation("instruction_line_id", "instruction_line", "id", "1:1"),
      relation("account_id", "account", "id"),
    ],
  }),

  defineTable({
    key: "manual_adjustment",
    name: "人工会计调整",
    comment: "维护人工会计调整申请头、平衡金额、受控文件证据和提交审批状态",
    fields: [
      primaryKey("id", "人工调整主键", "人工会计调整申请的服务侧雪花主键"),
      field("adjustment_no", types.longCode, "NN", "调整单号", "人工会计调整申请的稳定业务单号"),
      field("ledger_book_id", "BIGINT", "NN", "账簿主键", "人工调整唯一适用的账簿主键"),
      field("accounting_period_id", "BIGINT", "NN", "会计期间主键", "人工调整唯一适用且提交时必须开放的会计期间主键"),
      field("currency", types.currency, "NN", "币种", "人工调整全部明细共同使用的 ISO 4217 三字母币种", {
        source: "ISO 4217",
      }),
      field("reason_code", types.code, "NN", "调整原因代码", "人工会计调整使用的受控业务原因代码", {
        source: "Finance 人工会计调整原因目录",
      }),
      field("file_id", "BIGINT", "N", "证据文件主键", "Platform 文件服务保存的受控调整证据文件主键"),
      field("file_content_hash", types.hash, "N", "文件内容摘要", "Platform 文件服务返回且与证据文件主键成组保存的 SHA-256 小写十六进制摘要"),
      field("total_debit", types.amount, "NN", "借方合计", "提交时必须大于零并等于贷方合计的借方总金额"),
      field("total_credit", types.amount, "NN", "贷方合计", "提交时必须大于零并等于借方合计的贷方总金额"),
      field("content_hash", types.hash, "NN", "调整内容摘要", "覆盖申请头和有序明细的规范化 SHA-256 小写十六进制摘要"),
      field("submitted_time", types.dateTime, "N", "提交时间", "人工调整进入不可修改提交态的 UTC 微秒时间"),
      field("status", types.code, "NN", "调整状态", "人工会计调整申请的当前生命周期状态", {
        values: ["DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "POSTED", "CANCELLED"],
      }),
      field("maker_id", "BIGINT", "NN", "编制人主键", "创建并提交人工调整的平台用户主键"),
      field("version", "BIGINT", "NN", "并发版本", "人工调整条件更新使用的乐观锁版本", {
        defaultValue: "0",
      }),
    ],
    indexes: [
      uniqueIndex("uk_manual_adj", "adjustment_no"),
      accessIndex("idx_manual_adj_book", "ledger_book_id", "status"),
      accessIndex("idx_manual_adj_period", "accounting_period_id", "status"),
      accessIndex("idx_manual_adj_file", "file_id"),
      accessIndex("idx_manual_adj_status", "status", "submitted_time"),
    ],
    relations: [
      relation("ledger_book_id", "ledger_book", "id"),
      relation("accounting_period_id", "accounting_period", "id"),
    ],
  }),

  defineTable({
    key: "manual_adjustment_line",
    name: "人工会计调整明细",
    comment: "保存人工会计调整中冻结的借贷明细，提交后只读并由人工记账指令逐行承接",
    fields: [
      primaryKey("id", "人工调整明细主键", "人工会计调整明细的服务侧雪花主键"),
      field("adjustment_id", "BIGINT", "NN", "人工调整主键", "所属人工会计调整申请主键"),
      field("line_no", "INTEGER", "NN", "明细序号", "同一人工调整内稳定且有序的明细序号"),
      field("account_id", "BIGINT", "NN", "统一记账账户主键", "人工调整实际使用且必须属于调整头账簿的本库统一账户"),
      field("entry_side", types.code, "NN", "借贷方向", "人工调整明细的借方或贷方方向", {
        values: ENTRY_SIDES,
      }),
      field("amount", types.amount, "NN", "调整金额", "按币种精度舍入且必须大于零的调整金额"),
      field("line_description", "VARCHAR(512)", "NN", "明细说明", "不含敏感值的人工调整业务原因说明"),
    ],
    indexes: [
      uniqueIndex("uk_manual_adj_line", "adjustment_id", "line_no"),
      accessIndex("idx_manual_line_account", "account_id"),
    ],
    relations: [
      relation("adjustment_id", "manual_adjustment", "id"),
      relation("account_id", "account", "id"),
    ],
  }),

  defineTable({
    key: "adjustment_approval",
    name: "人工会计调整审批",
    comment: "追加保存人工会计调整固定双人审批的角色、决定和被批准内容摘要",
    audit: "immutable",
    fields: [
      primaryKey("id", "调整审批主键", "人工会计调整审批记录的服务侧雪花主键"),
      field("adjustment_id", "BIGINT", "NN", "人工调整主键", "被审批的人工会计调整申请主键"),
      field("approval_role", types.code, "NN", "审批角色", "本条审批记录承担的编制或复核角色", {
        values: APPROVAL_ROLES,
      }),
      field("actor_id", "BIGINT", "NN", "审批人主键", "执行该审批动作的平台用户主键"),
      field("decision", types.code, "NN", "审批决定", "审批人对冻结调整内容作出的决定", {
        values: APPROVAL_DECISIONS,
      }),
      field("approved_content_hash", types.hash, "NN", "批准内容摘要", "审批时确认且必须等于人工调整内容摘要的 SHA-256 小写十六进制值"),
      field("decision_time", types.dateTime, "NN", "决定时间", "审批决定形成的 UTC 微秒时间"),
    ],
    indexes: [
      uniqueIndex("uk_adj_approval", "adjustment_id", "approval_role"),
      accessIndex("idx_adj_approval_actor", "actor_id", "decision_time"),
    ],
    relations: [relation("adjustment_id", "manual_adjustment", "id")],
  }),
]);

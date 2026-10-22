// Card model content that the Payment Orchestration generator does not produce (refactor step 12, design/05).
//
// The four card tables were added to the committed Payment model by hand and never entered generate-payment-model.mjs;
// card-model-baseline.json keeps them verbatim (entities with their card-local relations, and the Card diagram) as moved
// out of payment.pdma.json. applyCardModelAdditions() then applies the step 12 delta so that the model renders exactly the
// reviewed paytech/card/docs/database/sql/card-schema.sql:
// - t_payment_card_instrument: + source_submission_id / expires_at, uk_card_instrument_submission, idx_card_instrument_expires
//   and the Card-owned instrumentRef comments (two-stage creation, design/05 §2.1-§2.2);
// - new t_card_material_ticket (S3 one-time reveal ticket);
// - per-database infrastructure: t_compensation_event comes from the partition, t_consumed_event and
//   t_consumed_aggregate_version are copied from paytech-messaging schema/messaging-consumed-event.sql;
// - 2026-10-22 BIN library (requirements/20261022/card-BIN库与卡属性 technique §3.3-§3.6): t_card_bin_library_version,
//   t_card_bin_range, t_card_bin_import_error and t_card_bin_library_activation, drawn on their own "BIN 库" diagram.
// Only physical PK/UK/index structure is modelled; there is no CHECK or FOREIGN KEY. t_card_material_ticket.instrument_ref is
// the decimal string of the instrument key (VARCHAR vs BIGINT), so it is documented in comments, not as a model relation.
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

const MODEL_KEY = "code-mor/card/database/v1";
const baselinePath = new URL("./card-model-baseline.json", import.meta.url);

function stableUuid(key) {
  const bytes = Buffer.from(createHash("sha256").update(`${MODEL_KEY}:${key}`).digest("hex").slice(0, 32), "hex");
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

const ASCII = Object.freeze({ characterSet: "ascii", collation: "ascii_bin" });

// [defKey, defName, type, len, notNull, ascii, comment, defaultValue?]
const TICKET_FIELDS = [
  ["card_material_ticket_id", "读卡票据标识", "BIGINT", "", true, false, "读卡票据的 Snowflake 内部主键。"],
  ["ticket_hash", "票据摘要", "CHAR", 64, true, true, "票据原值（随机 256 位）的 SHA-256 小写十六进制；票据原值只返回给签发方，不落库、不记录日志。"],
  ["instrument_ref", "卡交易工具引用", "VARCHAR", 32, true, true, "票据可读取的卡交易工具引用（t_payment_card_instrument 主键十进制字符串），创建后不可修改。"],
  ["attempt_id", "支付尝试标识", "BIGINT", "", true, false, "签发方 Payment 声明的支付尝试 ID，仅用于审计与核对，Card 不读取 Payment 数据。"],
  ["action_id", "资金动作标识", "BIGINT", "", true, false, "签发方 Payment 声明的当前资金动作 ID，仅用于审计与核对。"],
  ["purpose", "票据用途", "VARCHAR", 32, true, true, "票据用途，读卡入口只接受与之相同的用途。允许值：CHANNEL_EXECUTION。"],
  ["expires_at", "票据截止时间", "DATETIME", 6, true, false, "票据 UTC 微秒精度截止时间，签发后不超过 90 秒且不晚于工具期限。"],
  ["used_at", "票据使用时间", "DATETIME", 6, false, false, "票据被读卡入口使用的 UTC 微秒精度时间；NULL 表示未使用，非空后不可再次使用。"],
  ["caller", "签发调用方", "VARCHAR", 64, true, true, "签发请求的调用方服务名（D26），只接受 payment-service。"],
  ["create_time", "创建时间", "DATETIME", 6, true, false, "应用写入的票据签发 UTC 微秒精度时间。"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "应用写入的票据最近有效更新 UTC 微秒精度时间。"],
];

const BIN_VERSION_FIELDS = [
  ["version_id", "版本标识", "BIGINT", "", true, false, "BIN 库版本的 Snowflake 内部主键，创建后不可修改。"],
  ["version_no", "版本号", "INT", "", true, false, "对运营展示的版本号，从 1 起按创建顺序递增，创建后不可修改。"],
  ["status", "版本状态", "VARCHAR", 16, true, true, "版本状态。允许值：VALIDATING、VALID、INVALID、ACTIVE、RETIRED。VALIDATING 为等待扫描、校验与写入；INVALID 为文件或数据校验失败，不能激活；ACTIVE 为当前生效版本；RETIRED 为曾经生效、已被其他版本替换，可再次激活（回切）。"],
  ["active_slot", "生效占位", "TINYINT", "", false, false, "当前生效占位：status=ACTIVE 时为 1，其余状态为 NULL；与唯一索引一起保证同一时刻只有一个 ACTIVE 版本。"],
  ["file_address", "文件地址", "VARCHAR", 255, false, true, "导入文件在 Platform 文件服务的稳定地址 /files/v1/{fileId}；初始化种子版本为 NULL。"],
  ["file_content_hash", "文件摘要", "CHAR", 64, false, true, "创建版本时从 Platform 读取并冻结的文件 SHA-256 小写十六进制；导入时逐字节核对，不一致判 INVALID。种子版本为 NULL。"],
  ["file_name", "文件名", "VARCHAR", 255, false, false, "导入文件的展示名，取自 Platform 文件元数据；仅用于运营页面展示。种子版本为 NULL。"],
  ["total_rows", "数据行数", "INT", "", false, false, "文件数据行数（不含表头）；校验完成前为 NULL。"],
  ["processed_rows", "已写入行数", "INT", "", true, false, "已写入号段的数据行数，用于展示导入进度；VALID、ACTIVE、RETIRED 时等于 total_rows。", "0"],
  ["error_rows", "错误行数", "INT", "", true, false, "校验失败的数据行数（可能大于已记录的错误明细条数）；大于 0 时状态为 INVALID。", "0"],
  ["failure_reason", "失败原因", "VARCHAR", 256, false, false, "文件级失败原因（扫描未通过、表头不符、哈希不一致、超过行数上限等）；不含卡号；其他状态为 NULL。"],
  ["created_by", "导入人", "BIGINT", "", false, false, "发起导入的 Operations 账号 ID；初始化种子版本为 NULL。"],
  ["version", "并发版本", "INT", "", true, false, "乐观并发版本，新建为 1，每次状态或进度更新成功加 1。", "1"],
  ["create_time", "创建时间", "DATETIME", 6, true, false, "应用写入的版本创建 UTC 微秒时间。"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "应用写入的版本最近更新 UTC 微秒时间。"],
];

const BIN_RANGE_FIELDS = [
  ["range_id", "号段标识", "BIGINT", "", true, false, "BIN 号段的 Snowflake 内部主键。"],
  ["version_id", "版本标识", "BIGINT", "", true, false, "所属 BIN 库版本，创建后不可修改。"],
  ["bin_prefix", "号段前缀", "VARCHAR", 8, true, true, "号段前缀，6 至 8 位数字；按最长前缀匹配卡 BIN。"],
  ["prefix_length", "前缀位数", "TINYINT", "", true, false, "号段前缀位数，6、7 或 8，等于 bin_prefix 长度。"],
  ["card_brand", "卡组织", "VARCHAR", 32, true, true, "卡组织（主品牌）。允许值：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS。"],
  ["funding_type", "借贷记类型", "VARCHAR", 16, false, true, "借贷记类型；数据文件未提供时为 NULL。允许值：CREDIT、DEBIT、PREPAID、CHARGE。"],
  ["card_level", "卡等级", "VARCHAR", 32, false, true, "卡等级，格式 [A-Z0-9_]{1,32}；未提供时为 NULL。取值来源：BIN 数据文件；非固定枚举。"],
  ["card_product", "卡产品", "VARCHAR", 16, false, true, "卡产品类别；未提供时为 NULL。允许值：CONSUMER、COMMERCIAL。"],
  ["issuer_country", "发卡国", "CHAR", 2, true, true, "发卡国 ISO 3166-1 alpha-2。取值来源：ISO 3166-1 alpha-2；非固定枚举。"],
  ["issuer_region", "发卡区域", "VARCHAR", 16, true, true, "发卡区域，导入时按 Platform 国家区域映射由 issuer_country 推导并冻结；映射之后变化不影响本行。取值来源：Platform t_country_region.region_code；非固定枚举。"],
  ["issuer_name", "发卡行", "VARCHAR", 128, false, false, "发卡行名称，仅供运营查询展示；未提供时为 NULL。"],
  ["create_time", "创建时间", "DATETIME", 6, true, false, "应用写入的号段创建 UTC 微秒时间。"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "应用写入的号段最近更新 UTC 微秒时间；号段创建后不修改，与 create_time 相同。"],
];

const BIN_IMPORT_ERROR_FIELDS = [
  ["error_id", "错误标识", "BIGINT", "", true, false, "导入错误明细的 Snowflake 内部主键。"],
  ["version_id", "版本标识", "BIGINT", "", true, false, "所属 BIN 库版本。"],
  ["line_no", "行号", "INT", "", true, false, "数据文件中的物理行号，表头为第 1 行。"],
  ["column_name", "列名", "VARCHAR", 32, false, true, "出错列名；整行错误（列数不符、重复前缀等）为 NULL。"],
  ["error_code", "错误代码", "VARCHAR", 64, true, true, "错误代码。允许值：COLUMN_COUNT_MISMATCH、REQUIRED_MISSING、INVALID_FORMAT、INVALID_VALUE、DUPLICATE_PREFIX、COUNTRY_REGION_MISSING、ENCODING_INVALID。"],
  ["error_message", "错误说明", "VARCHAR", 256, true, false, "面向运营的中文错误说明，不含卡号。"],
  ["value_excerpt", "值摘录", "VARCHAR", 64, false, false, "出错值摘录：超过 8 位的连续数字不回显（存 NULL），其他值截断到 64 字符。"],
  ["create_time", "创建时间", "DATETIME", 6, true, false, "应用写入的错误明细创建 UTC 微秒时间。"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "应用写入的错误明细最近更新 UTC 微秒时间；创建后不修改。"],
];

const BIN_ACTIVATION_FIELDS = [
  ["activation_id", "激活标识", "BIGINT", "", true, false, "激活记录的 Snowflake 内部主键。"],
  ["version_id", "版本标识", "BIGINT", "", true, false, "本次被激活的 BIN 库版本。"],
  ["previous_version_id", "原生效版本", "BIGINT", "", false, false, "本次激活前的 ACTIVE 版本，激活后变为 RETIRED；此前没有生效版本时为 NULL。"],
  ["activation_type", "激活类型", "VARCHAR", 16, true, true, "激活类型。允许值：ACTIVATE、ROLLBACK。ACTIVATE 为激活 VALID 版本；ROLLBACK 为重新激活 RETIRED 版本（回切）。"],
  ["operator_account_id", "操作人", "BIGINT", "", false, false, "操作的 Operations 账号 ID；初始化种子激活为 NULL。"],
  ["create_time", "创建时间", "DATETIME", 6, true, false, "应用写入的激活 UTC 微秒时间。"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "应用写入的记录最近更新 UTC 微秒时间；创建后不修改。"],
];

const CONSUMED_EVENT_FIELDS = [
  ["event_id", "事件标识", "VARCHAR", 128, true, true, "事件 ID（信封 eventId，默认等于 eventKey）"],
  ["event_type", "事件类型", "VARCHAR", 32, true, true, "事件类型（PascalCase，登记于事件目录）"],
  ["aggregate_type", "聚合类型", "VARCHAR", 32, true, true, "聚合类型（大写 Code）"],
  ["aggregate_id", "聚合标识", "VARCHAR", 40, true, true, "聚合标识（分区键）"],
  ["aggregate_version", "聚合版本", "BIGINT", "", true, false, "本事件携带的聚合版本"],
  ["consumed_at", "消费时间", "DATETIME", 6, true, false, "消费事务提交时间（UTC）"],
];

const CONSUMED_AGGREGATE_FIELDS = [
  ["aggregate_type", "聚合类型", "VARCHAR", 32, true, true, "聚合类型（大写 Code）"],
  ["aggregate_id", "聚合标识", "VARCHAR", 40, true, true, "聚合标识（分区键）"],
  ["last_version", "已处理最大版本", "BIGINT", "", true, false, "本服务已处理的最大聚合版本"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "最近推进时间（UTC）"],
];

const INSTRUMENT_FIELDS_AFTER = Object.freeze({
  client_id: ["source_submission_id", "来源提交标识", "BIGINT", "", true, false, "申请本工具的 Commerce 来源提交全局稳定标识；与 client_id 组成唯一键，Payment 重试同一提交时 Card 返回同一工具。"],
  payment_stored_card_id: ["expires_at", "用卡截止时间", "DATETIME", 6, true, false, "工具首次用卡资格的 UTC 微秒精度截止时间：取 Payment 给出的期限上限与卡有效期中较早者，等于 CVV 暂存期限；创建后不可修改。"],
});

const INSTRUMENT_COMMENTS = Object.freeze({
  table: "每个来源提交一条，记录本次使用的卡凭证、所属 Client 与用卡期限；主键十进制字符串即 Card 工具引用。",
  payment_card_instrument_id: "卡交易工具的 Snowflake 内部主键；其十进制字符串即 Card 工具引用 instrumentRef，Payment 支付工具 Token 的 instrument_ref 保存该值。",
  client_id: "所属 CCT Client 稳定标识，由 Payment 从已核对的 Payment Intent 传入，创建后不可修改。",
});

function typeContext(model) {
  return {
    types: Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id])),
    domains: Object.fromEntries(model.domains.map((domain) => [domain.defKey, domain.id])),
  };
}

function buildField(context, table, [defKey, defName, type, len, notNull, ascii, comment, defaultValue = ""], primaryKey = false) {
  const { types, domains } = context;
  const baseType = { BIGINT: types.BIGINT, INT: types.int, TINYINT: types.int, VARCHAR: types.string, CHAR: types.char,
    DATETIME: types.date }[type];
  invariant(baseType, `Unsupported card field type: ${table}.${defKey} ${type}`);
  let domain = "";
  if (type === "DATETIME") domain = domains.DateTime;
  else if (primaryKey && type === "BIGINT") domain = domains.PrimaryKey;
  return {
    defKey,
    defName,
    comment,
    type,
    len,
    scale: "",
    primaryKey,
    notNull,
    autoIncrement: false,
    defaultValue,
    hideInGraph: false,
    refDict: "",
    baseType,
    extProps: ascii ? { ...ASCII } : {},
    domain,
    id: stableUuid(`field/${table}/${defKey}`),
  };
}

function buildIndex(table, entity, defKey, unique, fieldKeys, comment) {
  return {
    defKey,
    defName: defKey.split("_").map((part) => part.toUpperCase()).join(" "),
    unique,
    comment,
    fields: fieldKeys.map((fieldKey, position) => {
      const field = entity.fields.find((candidate) => candidate.defKey === fieldKey);
      invariant(field, `Unknown card index field: ${table}.${defKey}.${fieldKey}`);
      return { fieldDefKey: field.id, ascOrDesc: "A", id: stableUuid(`index-field/${table}/${defKey}/${position}/${fieldKey}`) };
    }),
    id: stableUuid(`index/${table}/${defKey}`),
  };
}

function buildEntity(model, context, table, defName, comment, fieldRows, primaryKeys) {
  const entity = {
    id: stableUuid(`entity/${table}`),
    env: { base: { nameSpace: "", codeRoot: "" } },
    defKey: table,
    defName,
    comment,
    properties: {},
    sysProps: { nameTemplate: "{defKey}[{defName}]" },
    notes: {},
    headers: structuredClone(model.profile.headers),
    fields: fieldRows.map((row) => buildField(context, table, row, primaryKeys.includes(row[0]))),
    correlations: [],
    indexes: [],
    type: "P",
  };
  return entity;
}

function extendInstrument(entity, context) {
  const table = entity.defKey;
  entity.comment = INSTRUMENT_COMMENTS.table;
  for (const field of entity.fields) {
    if (INSTRUMENT_COMMENTS[field.defKey]) field.comment = INSTRUMENT_COMMENTS[field.defKey];
  }
  for (const [after, row] of Object.entries(INSTRUMENT_FIELDS_AFTER)) {
    const position = entity.fields.findIndex((field) => field.defKey === after);
    invariant(position >= 0, `Missing ${table}.${after}`);
    invariant(!entity.fields.some((field) => field.defKey === row[0]), `${table}.${row[0]} already exists`);
    entity.fields.splice(position + 1, 0, buildField(context, table, row));
  }
  invariant(entity.indexes.length === 0, `${table} baseline must have no index`);
  entity.indexes.push(
    buildIndex(table, entity, "uk_card_instrument_submission", true, ["client_id", "source_submission_id"], "唯一索引：client_id、source_submission_id"),
    buildIndex(table, entity, "idx_card_instrument_expires", false, ["expires_at", "payment_card_instrument_id"], "访问索引：expires_at、payment_card_instrument_id"),
  );
}

function tableCell(diagram, entity, position, height) {
  return {
    id: stableUuid(`diagram/${diagram}/table/${entity.defKey}`),
    shape: "table",
    position,
    count: 0,
    originKey: entity.id,
    type: "P",
    size: { width: 390, height },
  };
}

const cellHeight = (entity) => Math.max(220, 86 + entity.fields.length * 22);

/** Applies the step 12 card content to the card partition of the generated Payment Orchestration model. */
export function applyCardModelAdditions(partition) {
  const model = structuredClone(partition);
  const baseline = JSON.parse(readFileSync(baselinePath, "utf8"));
  const context = typeContext(model);
  invariant(JSON.stringify(model.entities.map((entity) => entity.defKey)) === JSON.stringify(["t_compensation_event"]),
    "Card partition of the generated Payment model must contain only t_compensation_event");
  const [compensation] = model.entities;
  const cards = baseline.entities.map((entity) => structuredClone(entity));
  const instrument = cards.find((entity) => entity.defKey === "t_payment_card_instrument");
  extendInstrument(instrument, context);

  const ticket = buildEntity(model, context, "t_card_material_ticket", "一次性读卡票据",
    "Card 签发给 Payment、由 Platform 凭其读卡一次的一次性读卡票据；只存票据摘要，不存卡数据。", TICKET_FIELDS, ["card_material_ticket_id"]);
  ticket.indexes.push(
    buildIndex(ticket.defKey, ticket, "uk_card_ticket_hash", true, ["ticket_hash"], "唯一索引：ticket_hash"),
    buildIndex(ticket.defKey, ticket, "idx_card_ticket_instrument", false, ["instrument_ref", "card_material_ticket_id"], "访问索引：instrument_ref、card_material_ticket_id"),
  );
  const consumedEvent = buildEntity(model, context, "t_consumed_event", "已消费领域事件", "已消费领域事件：按事件 ID 去重",
    CONSUMED_EVENT_FIELDS, ["event_id"]);
  consumedEvent.indexes.push(buildIndex(consumedEvent.defKey, consumedEvent, "idx_consumed_event_aggregate", false,
    ["aggregate_type", "aggregate_id", "aggregate_version"], ""));
  const consumedAggregate = buildEntity(model, context, "t_consumed_aggregate_version", "已消费聚合版本", "已消费聚合版本：判定乱序、重放与缺口",
    CONSUMED_AGGREGATE_FIELDS, ["aggregate_type", "aggregate_id"]);

  const binVersion = buildEntity(model, context, "t_card_bin_library_version", "BIN 库版本",
    "Card 拥有的 BIN 库版本：一次导入的完整号段数据集；同一时刻只有一个 ACTIVE 版本，激活与回切原子切换。", BIN_VERSION_FIELDS, ["version_id"]);
  binVersion.indexes.push(
    buildIndex(binVersion.defKey, binVersion, "uk_bin_library_version_no", true, ["version_no"], "唯一索引：version_no"),
    buildIndex(binVersion.defKey, binVersion, "uk_bin_library_active", true, ["active_slot"], "唯一索引：active_slot；保证同一时刻至多一个 ACTIVE 版本"),
    buildIndex(binVersion.defKey, binVersion, "uk_bin_library_file", true, ["file_address"], "唯一索引：file_address；同一文件只建一个版本，重复提交返回原版本"),
    buildIndex(binVersion.defKey, binVersion, "idx_bin_library_status", false, ["status", "create_time"], "支持访问路径：按状态筛选的版本列表"),
  );
  const binRange = buildEntity(model, context, "t_card_bin_range", "BIN 号段",
    "BIN 库版本中的号段与标准化卡属性；只按 BIN 前缀保存，不含完整卡号。", BIN_RANGE_FIELDS, ["range_id"]);
  binRange.indexes.push(buildIndex(binRange.defKey, binRange, "uk_card_bin_range", true, ["version_id", "bin_prefix"],
    "唯一索引：version_id、bin_prefix；同一版本内同一前缀只有一行，也是按前缀查询的访问路径"));
  const binImportError = buildEntity(model, context, "t_card_bin_import_error", "BIN 导入错误",
    "BIN 库导入校验失败的行级错误明细，每个版本至多记录前 200 条。", BIN_IMPORT_ERROR_FIELDS, ["error_id"]);
  binImportError.indexes.push(buildIndex(binImportError.defKey, binImportError, "idx_bin_import_error_version", false,
    ["version_id", "line_no"], "支持访问路径：按版本分页列出错误，按行号排序"));
  const binActivation = buildEntity(model, context, "t_card_bin_library_activation", "BIN 库激活记录",
    "BIN 库激活与回切的追加记录，满足操作人与时间留痕；与版本状态切换在同一本地事务写入。", BIN_ACTIVATION_FIELDS, ["activation_id"]);
  binActivation.indexes.push(buildIndex(binActivation.defKey, binActivation, "idx_bin_activation_time", false,
    ["create_time"], "支持访问路径：激活历史按时间倒序分页"));
  const binTables = [binVersion, binRange, binImportError, binActivation];

  model.entities = [...cards, ticket, ...binTables, compensation, consumedEvent, consumedAggregate];

  const cardDiagram = structuredClone(baseline.diagram);
  cardDiagram.comment = "Card 服务的卡、卡凭证、卡交易工具、已存卡与一次性读卡票据。";
  const instrumentCell = cardDiagram.canvasData.cells.find((cell) => cell.originKey === instrument.id);
  instrumentCell.size.height += 22 * Object.keys(INSTRUMENT_FIELDS_AFTER).length;
  cardDiagram.canvasData.cells.push(tableCell(cardDiagram.defKey, ticket, { x: 860, y: 400 }, cellHeight(ticket)));
  const reliability = model.diagrams.find((diagram) => diagram.defKey === "reliability");
  invariant(reliability, "Card partition must keep the reliability diagram");
  reliability.canvasData.cells.push(
    tableCell(reliability.defKey, consumedEvent, { x: 860, y: 0 }, cellHeight(consumedEvent)),
    tableCell(reliability.defKey, consumedAggregate, { x: 1290, y: 0 }, cellHeight(consumedAggregate)),
  );
  const binDiagram = {
    defKey: "bin_library",
    defName: "BIN 库",
    relationType: cardDiagram.relationType,
    canvasData: {
      cells: binTables.map((entity, position) => tableCell("bin_library", entity, { x: position * 430, y: 0 }, cellHeight(entity))),
    },
    id: stableUuid("diagram/bin_library"),
    comment: "Card BIN 库：版本、号段、导入错误明细与激活记录。",
  };
  model.diagrams = [cardDiagram, binDiagram, reliability];

  const ids = [
    ...model.entities.flatMap((entity) => [entity.id, ...entity.fields.map((field) => field.id),
      ...entity.indexes.flatMap((index) => [index.id, ...index.fields.map((part) => part.id)]),
      ...entity.correlations.map((relation) => relation.id)]),
    ...model.diagrams.flatMap((diagram) => [diagram.id, ...diagram.canvasData.cells.map((cell) => cell.id)]),
  ];
  invariant(new Set(ids).size === ids.length, "Card model IDs must be unique");
  return model;
}

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
//   t_consumed_aggregate_version are copied from paytech-messaging schema/messaging-consumed-event.sql.
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

// [defKey, defName, type, len, notNull, ascii, comment]
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

function buildField(context, table, [defKey, defName, type, len, notNull, ascii, comment], primaryKey = false) {
  const { types, domains } = context;
  const baseType = { BIGINT: types.BIGINT, VARCHAR: types.string, CHAR: types.char, DATETIME: types.date }[type];
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
    defaultValue: "",
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

  model.entities = [...cards, ticket, compensation, consumedEvent, consumedAggregate];

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
  model.diagrams = [cardDiagram, reliability];

  const ids = [
    ...model.entities.flatMap((entity) => [entity.id, ...entity.fields.map((field) => field.id),
      ...entity.indexes.flatMap((index) => [index.id, ...index.fields.map((part) => part.id)]),
      ...entity.correlations.map((relation) => relation.id)]),
    ...model.diagrams.flatMap((diagram) => [diagram.id, ...diagram.canvasData.cells.map((cell) => cell.id)]),
  ];
  invariant(new Set(ids).size === ids.length, "Card model IDs must be unique");
  return model;
}

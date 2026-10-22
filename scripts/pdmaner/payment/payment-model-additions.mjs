// 2026-10-22 3DS 全链路（requirements/20261022/payment-3DS全链路/technique.md 第 3.4 节）：Payment 首次作为 MQ 消费者
// （消费 channel-connector 的 ChannelPaymentNotified），在 payment 分区模型上追加 paytech-messaging 标准消费表 t_consumed_event、
// t_consumed_aggregate_version（列、类型、主键与索引与 acquiring / card 及 messaging-consumed-event.sql 一致，注释按需求 db/payment/ddl.sql）。
// t_payment_action.interaction_type 的注释修正在 payment-execution-field-overrides.mjs。
// 只作用于 payment 模型：channel-connector 与 card 模型不变。新增实体、字段、索引与图元的 ID 使用本文件的命名空间。
import { createHash } from "node:crypto";

const MODEL_KEY = "code-mor/payment/database/v1";

const ASCII = Object.freeze({ characterSet: "ascii", collation: "ascii_bin" });

const AGGREGATE_TYPE_COMMENT = "领域事件所属聚合的大写类型代码。";
const AGGREGATE_ID_COMMENT = "领域事件所属聚合的标识，同时是 RabbitMQ 分区路由键。";

// [defKey, defName, type, len, notNull, ascii, comment]
const CONSUMED_EVENT_FIELDS = [
  ["event_id", "事件标识", "VARCHAR", 128, true, true, "领域事件信封 eventId（默认等于 eventKey），按其去重。"],
  ["event_type", "事件类型", "VARCHAR", 32, true, true, "已消费领域事件的类型（PascalCase，登记于 paytech-messaging 事件目录）。"],
  ["aggregate_type", "聚合类型", "VARCHAR", 32, true, true, AGGREGATE_TYPE_COMMENT],
  ["aggregate_id", "聚合标识", "VARCHAR", 40, true, true, AGGREGATE_ID_COMMENT],
  ["aggregate_version", "聚合版本", "BIGINT", "", true, false, "本条已消费领域事件携带的聚合版本。"],
  ["consumed_at", "消费时间", "DATETIME", 6, true, false, "消费事务提交的 UTC 微秒精度时间。"],
];

const CONSUMED_AGGREGATE_FIELDS = [
  ["aggregate_type", "聚合类型", "VARCHAR", 32, true, true, AGGREGATE_TYPE_COMMENT],
  ["aggregate_id", "聚合标识", "VARCHAR", 40, true, true, AGGREGATE_ID_COMMENT],
  ["last_version", "已处理最大版本", "BIGINT", "", true, false, "本服务已处理的该聚合最大领域事件版本。"],
  ["update_time", "更新时间", "DATETIME", 6, true, false, "数据库记录最近一次持久化更新的 UTC 微秒精度时间。"],
];

const CONSUMED_EVENT_COMMENT = "已消费领域事件：paytech-messaging 消费端按事件 ID 去重的标准表（design/08 §4），列、类型、主键与索引与 "
  + "paytech-messaging 的 messaging-consumed-event.sql 一致；事件与版本合法性由消费拦截器保证。";
const CONSUMED_AGGREGATE_COMMENT = "已消费聚合版本：paytech-messaging 消费端按聚合记录已处理的最大版本，用于判定乱序、重放与缺口（design/08 §4）；"
  + "列、类型与主键与 messaging-consumed-event.sql 一致。";

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function stableUuid(key) {
  const bytes = Buffer.from(createHash("sha256").update(`${MODEL_KEY}:${key}`).digest("hex").slice(0, 32), "hex");
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function typeContext(model) {
  return {
    types: Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id])),
    domains: Object.fromEntries(model.domains.map((domain) => [domain.defKey, domain.id])),
  };
}

function buildField(context, table, [defKey, defName, type, len, notNull, ascii, comment], primaryKey) {
  const { types, domains } = context;
  const baseType = { BIGINT: types.BIGINT, VARCHAR: types.string, DATETIME: types.date }[type];
  invariant(baseType, `Unsupported payment field type: ${table}.${defKey} ${type}`);
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
    domain: type === "DATETIME" ? domains.DateTime : "",
    id: stableUuid(`field/${table}/${defKey}`),
  };
}

function buildEntity(model, context, table, defName, comment, rows, primaryKeys) {
  return {
    id: stableUuid(`entity/${table}`),
    env: { base: { nameSpace: "", codeRoot: "" } },
    defKey: table,
    defName,
    comment,
    properties: {},
    sysProps: { nameTemplate: "{defKey}[{defName}]" },
    notes: {},
    headers: structuredClone(model.profile.headers),
    fields: rows.map((row) => buildField(context, table, row, primaryKeys.includes(row[0]))),
    correlations: [],
    indexes: [],
    type: "P",
  };
}

function buildIndex(entity, defKey, fieldKeys, comment) {
  return {
    defKey,
    defName: defKey.split("_").map((part) => part.toUpperCase()).join(" "),
    unique: false,
    comment,
    fields: fieldKeys.map((fieldKey, position) => {
      const field = entity.fields.find((candidate) => candidate.defKey === fieldKey);
      invariant(field, `Unknown payment index field: ${entity.defKey}.${defKey}.${fieldKey}`);
      return { fieldDefKey: field.id, ascOrDesc: "A", id: stableUuid(`index-field/${entity.defKey}/${defKey}/${position}/${fieldKey}`) };
    }),
    id: stableUuid(`index/${entity.defKey}/${defKey}`),
  };
}

function tableCell(diagram, entity, position) {
  return {
    id: stableUuid(`diagram/${diagram}/table/${entity.defKey}`),
    shape: "table",
    position,
    count: 0,
    originKey: entity.id,
    type: "P",
    size: { width: 390, height: Math.max(220, 86 + entity.fields.length * 22) },
  };
}

/**
 * 在 payment 分区模型上追加 3DS 全链路的消费表。
 *
 * @param partition 按 Payment 分区生成的 payment 模型，不会被修改
 * @return 新模型
 */
export function applyPaymentModelAdditions(partition) {
  const model = structuredClone(partition);
  const context = typeContext(model);
  invariant(!model.entities.some((entity) => entity.defKey.startsWith("t_consumed_")), "payment 分区模型已存在消费表");

  const consumedEvent = buildEntity(model, context, "t_consumed_event", "已消费领域事件", CONSUMED_EVENT_COMMENT,
    CONSUMED_EVENT_FIELDS, ["event_id"]);
  consumedEvent.indexes.push(buildIndex(consumedEvent, "idx_consumed_event_aggregate",
    ["aggregate_type", "aggregate_id", "aggregate_version"], "访问索引：aggregate_type、aggregate_id、aggregate_version"));
  const consumedAggregate = buildEntity(model, context, "t_consumed_aggregate_version", "已消费聚合版本",
    CONSUMED_AGGREGATE_COMMENT, CONSUMED_AGGREGATE_FIELDS, ["aggregate_type", "aggregate_id"]);
  model.entities.push(consumedEvent, consumedAggregate);

  const reliability = model.diagrams.find((diagram) => diagram.defKey === "reliability");
  invariant(reliability, "payment 分区模型缺少 reliability 图组");
  reliability.canvasData.cells.push(tableCell(reliability.defKey, consumedEvent, { x: 860, y: 0 }),
    tableCell(reliability.defKey, consumedAggregate, { x: 1290, y: 0 }));

  const ids = [
    ...model.entities.flatMap((entity) => [entity.id, ...entity.fields.map((field) => field.id),
      ...entity.indexes.flatMap((index) => [index.id, ...index.fields.map((part) => part.id)]),
      ...entity.correlations.map((relation) => relation.id)]),
    ...model.diagrams.flatMap((diagram) => [diagram.id, ...diagram.canvasData.cells.map((cell) => cell.id)]),
  ];
  invariant(new Set(ids).size === ids.length, "payment 模型 ID 必须唯一");
  return model;
}

// 把渠道协议模块的 10 张表（protocol-tables.mjs）追加到 channel-connector 的 PDManer 模型。
// 2026-10-20 统一对外渠道出口第一段自 Platform Support 原样迁入：字段、注释、索引、关系与稳定 ID 的生成规则沿用迁移前的
// Platform 生成器（scripts/pdmaner/platform-support/generate-platform-support-model.mjs），因此 10 张表的实体、字段、索引与
// 关系 ID 与迁移前的 Platform 模型一致；关系只在 10 张表之间，不与 Payment 分区的表相连。
import { createHash } from "node:crypto";

import { TABLES } from "./protocol-tables.mjs";

/** 迁移前 Platform 模型的稳定 ID 命名空间；保留它使 10 张表的模型 ID 与迁移前一致。 */
const ID_NAMESPACE = "code-mor/platform-support/database/v1";

const DIAGRAM = Object.freeze({ defKey: "channel_protocol", defName: "渠道协议与外部 API 配置" });

const DESCRIBE_SUFFIX = "、标准 API、外部接入方与沙箱验证配置";

/** 渠道协议模块拥有的 10 张物理表，按 DSL 声明顺序。 */
export const PROTOCOL_TABLES = Object.freeze(TABLES.map((table) => `t_${table.physicalKey}`));

const tablesByLogicalKey = new Map(TABLES.map((table) => [table.defKey, table]));
const RELATIONS = Object.freeze(TABLES.flatMap((table) => table.relations));

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function stableUuid(key) {
  const bytes = Buffer.from(createHash("sha256").update(`${ID_NAMESPACE}:${key}`).digest("hex").slice(0, 32), "hex");
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
}

function physicalTableKey(logicalKey) {
  const table = tablesByLogicalKey.get(logicalKey);
  invariant(table, `未知协议逻辑表：${logicalKey}`);
  return `t_${table.physicalKey}`;
}

function relationStableKey(relation) {
  return `${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`;
}

function relationId(relation) {
  return stableUuid(`relation/${relationStableKey(relation)}`);
}

function baseTypeFor(field, typeIds, domainIds) {
  const result = { baseType: "", domain: "" };
  if (field.primaryKey) result.domain = domainIds.PrimaryKey;
  if (field.type === "BIGINT") result.baseType = typeIds.BIGINT;
  else if (field.type === "VARCHAR") result.baseType = typeIds.string;
  else if (field.type === "CHAR") result.baseType = typeIds.char;
  else if (["INT", "BOOLEAN"].includes(field.type)) result.baseType = typeIds.int;
  else if (field.type === "DATETIME") result.baseType = typeIds.date;
  else if (["JSON", "TEXT"].includes(field.type)) result.baseType = typeIds.largeText;
  invariant(result.baseType, `协议表不支持的字段类型：${field.defKey} ${field.type}`);
  if (field.type === "DATETIME") result.domain = domainIds.DateTime;
  return result;
}

function fieldComment(field) {
  const clauses = [`${field.description.replace(/。$/u, "")}。`];
  if (field.closedValues) clauses.push(`允许值：${field.closedValues.join("、")}。`);
  if (field.openSource) clauses.push(`取值来源：${field.openSource}；非固定枚举。`);
  return clauses.join("");
}

function buildIndexes(table, fieldsByKey) {
  return table.indexes.map((spec) => ({
    defKey: spec.defKey,
    defName: `${spec.unique ? "唯一" : "访问"}索引 ${spec.fields.join("、")}`,
    unique: spec.unique,
    comment: `${spec.unique ? "保证业务唯一性" : "支持访问路径"}：${spec.fields.join("、")}`,
    fields: spec.fields.map((fieldKey, position) => {
      const field = fieldsByKey.get(fieldKey);
      invariant(field, `协议表索引字段不存在：${table.defKey}.${spec.defKey}.${fieldKey}`);
      return {
        fieldDefKey: field.id,
        ascOrDesc: "A",
        id: stableUuid(`index-field/${table.defKey}/${spec.defKey}/${position}/${fieldKey}`),
      };
    }),
    id: stableUuid(`index/${table.defKey}/${spec.defKey}`),
  }));
}

function buildEntities(model) {
  const typeIds = Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id]));
  const domainIds = Object.fromEntries(model.domains.map((domain) => [domain.defKey, domain.id]));
  return TABLES.map((table) => {
    const fields = table.fields.map((manifestField) => {
      const mapping = baseTypeFor(manifestField, typeIds, domainIds);
      return {
        defKey: manifestField.defKey,
        defName: manifestField.defName,
        comment: fieldComment(manifestField),
        type: manifestField.type,
        len: manifestField.len,
        scale: manifestField.scale,
        primaryKey: manifestField.primaryKey,
        notNull: manifestField.notNull,
        autoIncrement: false,
        defaultValue: manifestField.defaultValue,
        hideInGraph: false,
        refDict: "",
        baseType: mapping.baseType,
        extProps: manifestField.characterSet ? {
          characterSet: manifestField.characterSet,
          collation: manifestField.collation,
        } : {},
        domain: mapping.domain,
        id: stableUuid(`field/${table.defKey}/${manifestField.defKey}`),
      };
    });
    const fieldsByKey = new Map(fields.map((field) => [field.defKey, field]));
    return {
      id: stableUuid(`entity/${table.defKey}`),
      env: { base: { nameSpace: "", codeRoot: "" } },
      defKey: physicalTableKey(table.defKey),
      defName: table.defName,
      comment: table.comment,
      properties: {},
      sysProps: { nameTemplate: "{defKey}[{defName}]" },
      notes: {},
      headers: structuredClone(model.profile.headers),
      fields,
      correlations: [],
      indexes: buildIndexes(table, fieldsByKey),
      type: "P",
    };
  });
}

function applyRelations(entitiesByKey) {
  for (const relation of RELATIONS) {
    const child = entitiesByKey.get(relation.childTable);
    const parent = entitiesByKey.get(relation.parentTable);
    invariant(child && parent, `协议表关系包含未知表：${JSON.stringify(relation)}`);
    const childField = child.fields.find((field) => field.defKey === relation.childField);
    const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
    invariant(childField && parentField, `协议表关系包含未知字段：${JSON.stringify(relation)}`);
    child.correlations.push({
      id: relationId(relation),
      myField: childField.id,
      refEntity: parent.id,
      refField: parentField.id,
      myRows: relation.cardinality.startsWith("n") ? "n" : "1",
      refRows: "1",
      innerType: "",
    });
  }
}

/** 与迁移前 Platform 图组相同的四列布局；图上只有 10 张协议表及其之间的关系。 */
function buildDiagram(entitiesByKey) {
  const tableKeys = TABLES.map((table) => table.defKey);
  const columnCount = 4;
  const heights = tableKeys.map((tableKey) => Math.max(220, 86 + entitiesByKey.get(tableKey).fields.length * 22));
  const rowOffsets = [];
  for (let row = 0, y = 0; row * columnCount < tableKeys.length; row += 1) {
    rowOffsets.push(y);
    y += Math.max(...heights.slice(row * columnCount, (row + 1) * columnCount)) + 60;
  }
  const cellByTable = new Map();
  const cells = tableKeys.map((tableKey, index) => {
    const cell = {
      id: stableUuid(`diagram/${DIAGRAM.defKey}/table/${tableKey}`),
      shape: "table",
      position: { x: (index % columnCount) * 430, y: rowOffsets[Math.floor(index / columnCount)] },
      count: 0,
      originKey: entitiesByKey.get(tableKey).id,
      type: "P",
      size: { width: 390, height: heights[index] },
    };
    cellByTable.set(tableKey, cell);
    return cell;
  });
  for (const relation of RELATIONS) {
    const childField = entitiesByKey.get(relation.childTable).fields.find((field) => field.defKey === relation.childField);
    const parentField = entitiesByKey.get(relation.parentTable).fields.find((field) => field.defKey === relation.parentField);
    cells.push({
      id: stableUuid(`diagram/${DIAGRAM.defKey}/relation/${relationStableKey(relation)}`),
      shape: "erdRelation",
      originKey: relationId(relation),
      source: { cell: cellByTable.get(relation.childTable).id, port: `${childField.id}%out` },
      target: { cell: cellByTable.get(relation.parentTable).id, port: `${parentField.id}%in` },
      relation: relation.cardinality,
      vertices: [],
      fillColor: "#ACDAFC",
      attrs: { line: { strokeDasharray: "" } },
      isLock: true,
    });
  }
  return {
    defKey: DIAGRAM.defKey,
    defName: DIAGRAM.defName,
    relationType: "field",
    canvasData: { cells },
    id: stableUuid(`diagram/${DIAGRAM.defKey}`),
    comment: `${DIAGRAM.defName} bounded-context view`,
  };
}

/**
 * 在 channel-connector 分区模型上追加 10 张协议表、表间关系与一个图组，并在模型说明中补充协议职责。
 *
 * @param model 已按 Payment 分区生成的 channel-connector 模型，会被原地修改
 * @return 同一个模型
 */
export function appendProtocolTables(model) {
  const existing = new Set(model.entities.map((entity) => entity.defKey));
  for (const table of PROTOCOL_TABLES) invariant(!existing.has(table), `协议表与分区表重名：${table}`);
  invariant(!model.diagrams.some((diagram) => diagram.defKey === DIAGRAM.defKey), `图组重名：${DIAGRAM.defKey}`);
  const entities = buildEntities(model);
  const entitiesByKey = new Map(TABLES.map((table, index) => [table.defKey, entities[index]]));
  applyRelations(entitiesByKey);
  model.entities.push(...entities);
  model.diagrams.push(buildDiagram(entitiesByKey));
  model.describe = `${model.describe}${DESCRIBE_SUFFIX}`;
  return model;
}

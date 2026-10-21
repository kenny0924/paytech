import { paymentChannelIdentityKey } from "../payment-channel-identity.mjs";
import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { RELATIONS, TABLES } from "./manifest.mjs";
import { CURRENT_COMPENSATION_EVENT } from "./manifests/08-reliability.mjs";
import {
  EXPECTED_SERVICE_TABLE_COUNTS,
  FINANCE_SERVICES,
  SERVICE_ADDED_TABLES,
  SERVICE_TABLE_VARIANTS,
  serviceTables,
} from "./service-split.mjs";
import {
  CHECK_BINDINGS,
  CHECK_CONTRACTS,
  DIAGRAM_GROUPS,
  FILE_REFERENCE_TUPLES,
} from "./model-policy.mjs";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const workspaceRoot = path.resolve(scriptDirectory, "../../..");
const templatePath = path.join(
  workspaceRoot,
  "agent-docs/templates/pdmaner/template.pdma.json",
);
// 第 07 步起不再输出单一 finance.pdma.json：逻辑模型只在内存生成并校验，按服务切分后写入各服务目录。
export function serviceModelPath(service) {
  return path.join(workspaceRoot, `paytech/${service}/docs/database/${service}.pdma.json`);
}

const SERVICE_TITLES = Object.freeze({
  tax: "Tax", payout: "Payout",
});

const MODEL_KEY = "code-mor/finance/database/v1";
const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const MYSQL_MAPPING_ID = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const JAVA_MAPPING_ID = "797A1496-D649-4261-89B4-544132EC3F36";
const MYBATIS_MAPPING_ID = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}

function stableUuid(key) {
  key = paymentChannelIdentityKey(key);
  if (key.startsWith("field/")) key = key.replace(/\/entity_no$/, "/operating_entity_id").replace(/\/liable_entity_no$/, "/liable_oe_id").replace(/\/client_agreement_id$/, "/client_agreement_oe_id");
  const bytes = Buffer.from(sha256(`${MODEL_KEY}:${key}`).slice(0, 32), "hex");
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
}

function physicalTableKey(logicalKey) {
  return `t_${logicalKey}`;
}

function logicalTableKey(physicalKey) {
  return physicalKey.replace(/^t_/u, "");
}

function relationStableKey(relation) {
  return `${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`;
}

function relationId(relation) {
  return stableUuid(`relation/${relationStableKey(relation)}`);
}

function readApprovedTemplate() {
  const bytes = readFileSync(templatePath);
  invariant(sha256(bytes) === EXPECTED_TEMPLATE_DIGEST, `共享模板摘要发生变化：${sha256(bytes)}`);
  return JSON.parse(bytes);
}

function normalizeTemplate(template) {
  const model = clone(template);
  model.name = "Code-MoR Finance Database V1";
  model.describe = "基于已确认 Finance V1 物理清单确定性生成的 PDManer 模型";
  model.dbConn = [];
  model.dbConns = [];
  model.profile.default.dbConn = "";

  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping]));
  mappings.get("BIGINT")[JAVA_MAPPING_ID] = "Long";
  mappings.get("BIGINT")[MYBATIS_MAPPING_ID] = "Long";
  mappings.get("double")[JAVA_MAPPING_ID] = "BigDecimal";
  mappings.get("double")[MYBATIS_MAPPING_ID] = "BigDecimal";
  mappings.get("date")[JAVA_MAPPING_ID] = "Instant";
  mappings.get("date")[MYBATIS_MAPPING_ID] = "Instant";
  mappings.get("char")[MYBATIS_MAPPING_ID] = "String";
  invariant(!mappings.has("localDate"), "共享模板不应预置 Finance 派生的 localDate 映射");
  const localDateMapping = {
    defKey: "localDate",
    id: stableUuid("type-mapping/localDate"),
    defName: "本地日期",
    [MYSQL_MAPPING_ID]: "DATE",
    [JAVA_MAPPING_ID]: "LocalDate",
    [MYBATIS_MAPPING_ID]: "LocalDate",
  };
  model.dataTypeMapping.mappings.push(localDateMapping);
  mappings.set("localDate", localDateMapping);

  const domains = new Map(model.domains.map((domain) => [domain.defKey, domain]));
  domains.get("Money").len = 24;
  domains.get("Money").scale = 8;
  domains.get("Decimal").len = 18;
  domains.get("Decimal").scale = 10;
  domains.get("DateTime").len = 6;
  domains.get("currency").applyFor = mappings.get("char").id;
  domains.get("currency").len = 3;

  for (const auditField of model.profile.default.entityInitFields) {
    const isCreate = auditField.defKey === "create_time";
    auditField.type = "DATETIME";
    auditField.len = 6;
    auditField.scale = "";
    auditField.notNull = true;
    auditField.autoIncrement = false;
    auditField.defaultValue = "";
    auditField.defName = isCreate ? "创建时间" : "更新时间";
    auditField.comment = isCreate
      ? "该记录在 Finance 数据库中的 UTC 微秒创建时间。"
      : "该可变记录最后一次持久化更新的 UTC 微秒时间。";
  }

  const namingRules = new Map(model.namingRules.map((rule) => [rule.id, rule]));
  for (const [id, objectName, maximum, ruleName] of [
    ["63F1DC0E-6A76-4B75-B3DA-4B00657B4E1B", "field", 32, "字段代码不超过 32 个字符"],
    ["668CBEE6-E0B7-4ACE-B72E-63942963B191", "entity", 32, "表代码不超过 32 个字符"],
    ["11BD987F-82E7-418E-A752-FDD84F1582A2", "field", 32, "字段代码不超过 32 个字符"],
    ["29D0A8D9-ABE2-451F-8A39-52FAB02E62B9", "index", 32, "索引代码不超过 32 个字符"],
  ]) {
    const rule = namingRules.get(id);
    if (rule) {
      rule.defName = ruleName;
      rule.programCode = `return (data.${objectName}.defKey||"").length <= ${maximum};`;
    }
  }
  const primaryKeyRule = namingRules.get("2BAB122B-8811-40BB-89F3-CDC24B5862D3");
  if (primaryKeyRule) {
    primaryKeyRule.defName = "主键必须为非自增 Snowflake BIGINT";
    primaryKeyRule.programCode = "let field=data.entity.fields.find(item=>item.primaryKey); return !!field && !field.autoIncrement && (field.type||field.dbType||'').toUpperCase()==='BIGINT';";
  }
  const tablePrefixRule = namingRules.get("1168C7C2-8E8E-4FB7-B639-B3DE839C395A");
  if (tablePrefixRule) {
    tablePrefixRule.defName = "物理表名使用 t_ 前缀和小写 snake_case";
    tablePrefixRule.programCode = "return /^t_[a-z][a-z0-9_]*$/.test(data.entity.defKey);";
  }
  const indexPrefixRule = namingRules.get("023450B3-AAE2-4DC1-AE63-2196DD82823D");
  if (indexPrefixRule) {
    indexPrefixRule.defName = "唯一索引以 uk_ 开头，普通索引以 idx_ 开头";
    indexPrefixRule.programCode = "return data.index.unique ? data.index.defKey.indexOf('uk_')===0 : data.index.defKey.indexOf('idx_')===0;";
  }
  const immutableTables = TABLES.filter((table) => table.audit === "immutable")
    .map((table) => physicalTableKey(table.defKey));
  const auditRule = namingRules.get("0B2F0BD2-3B84-4AB1-BA29-9DE9620AF608");
  if (auditRule) {
    auditRule.defName = "可变表使用双审计时间，不可变事实只保留创建时间";
    auditRule.programCode = `let actual=data.entity.fields.map(field=>field.defKey); let immutable=${JSON.stringify(immutableTables)}.includes(data.entity.defKey); return actual.includes('create_time') && (immutable ? !actual.includes('update_time') : actual.includes('update_time'));`;
  }
  const reservedRule = namingRules.get("BEC54F19-52D5-4882-BCE1-4439785F8001");
  if (reservedRule) reservedRule.programCode = reservedRule.programCode.replace(",status,", ",");
  const timeRule = namingRules.get("D330BCC3-DBAB-4677-8C5A-A301003A5878");
  if (timeRule) {
    timeRule.defName = "时刻字段使用 DATETIME(6)，日期事实使用 DATE";
    timeRule.programCode = "let key=(data.field.defKey||'').toLowerCase(); let type=(data.field.type||data.field.dbType||'').toUpperCase(); if(key.endsWith('_date')||key==='start_date'||key==='end_date'){return type==='DATE';} if(key.endsWith('_time')||key.endsWith('_at')||key.endsWith('_from')||key.endsWith('_to')||key.endsWith('_until')){return type==='DATETIME' && parseInt(data.field.len)===6;} return true;";
  }
  const mysqlDdlTemplate = model.profile.codeTemplates.find((templateSpec) => templateSpec.type === "dbDDL");
  const typeLengthTemplate = "{{=field.type}}{{?field.len>0}}{{='('}}{{=field.len}}{{?field.scale>0}}{{=','}}{{=field.scale}}{{?}}{{=')'}}{{?}}";
  invariant(mysqlDdlTemplate?.createTable.includes(typeLengthTemplate), "MySQL DDL template lacks controlled type fragment");
  mysqlDdlTemplate.createTable = mysqlDdlTemplate.createTable.replace(
    typeLengthTemplate,
    `${typeLengthTemplate}{{? field.extProps && field.extProps.characterSet }} CHARACTER SET {{=field.extProps.characterSet}} COLLATE {{=field.extProps.collation}}{{?}}`,
  );
  return model;
}

function baseTypeFor(field, typeIds, domainIds) {
  const result = { baseType: "", domain: "" };
  if (field.primaryKey) result.domain = domainIds.PrimaryKey;
  if (field.type === "BIGINT") result.baseType = typeIds.BIGINT;
  else if (field.type === "VARCHAR") result.baseType = typeIds.string;
  else if (field.type === "CHAR") result.baseType = typeIds.char;
  else if (field.type === "DECIMAL") result.baseType = typeIds.double;
  else if (field.type === "DATETIME") result.baseType = typeIds.date;
  else if (field.type === "DATE") result.baseType = typeIds.localDate;
  else if (["INT", "INTEGER", "TINYINT", "SMALLINT", "BOOLEAN"].includes(field.type)) result.baseType = typeIds.int;
  else if (["JSON", "TEXT"].includes(field.type)) result.baseType = typeIds.largeText;
  else invariant(false, `不支持的字段类型：${field.type}`);
  if (field.type === "DATETIME") result.domain = domainIds.DateTime;
  if (field.type === "DECIMAL" && field.len === 24 && field.scale === 8) result.domain = domainIds.Money;
  if (field.type === "DECIMAL" && field.len === 18 && field.scale === 10) result.domain = domainIds.Decimal;
  if (field.type === "CHAR" && field.len === 3) result.domain = domainIds.currency;
  return result;
}

function fieldComment(field) {
  const clauses = [`${field.description.replace(/。$/u, "")}。`];
  if (field.type === "BOOLEAN") {
    clauses.push(`${field.notNull ? "1 表示是，0 表示否" : "1 表示是，0 表示否，NULL 表示尚未形成结论"}。`);
  }
  if (field.closedValues) clauses.push(`允许值：${field.closedValues.join("、")}。`);
  if (field.openSource) clauses.push(`取值来源：${field.openSource}；非固定枚举。`);
  return clauses.join("");
}

function buildIndexes(table, fieldsByKey) {
  const names = new Set();
  const signatures = new Set();
  return table.indexes.map((spec) => {
    invariant(!names.has(spec.defKey), `索引名重复：${table.defKey}.${spec.defKey}`);
    names.add(spec.defKey);
    const signature = `${spec.unique}:${spec.fields.join(",")}`;
    invariant(!signatures.has(signature), `索引签名重复：${table.defKey}.${signature}`);
    signatures.add(signature);
    return {
      defKey: spec.defKey,
      defName: `${spec.unique ? "唯一" : "访问"}索引 ${spec.fields.join("、")}`,
      unique: spec.unique,
      comment: spec.comment ?? `${spec.unique ? "保证业务唯一性" : "支持访问路径"}：${spec.fields.join("、")}`,
      fields: spec.fields.map((fieldKey, position) => {
        const field = fieldsByKey.get(fieldKey);
        invariant(field, `索引字段不存在：${table.defKey}.${spec.defKey}.${fieldKey}`);
        return {
          fieldDefKey: field.id,
          ascOrDesc: "A",
          id: stableUuid(`index-field/${table.defKey}/${spec.defKey}/${position}/${fieldKey}`),
        };
      }),
      id: stableUuid(`index/${table.defKey}/${spec.defKey}`),
    };
  });
}

function buildEntities(model, tables = TABLES) {
  const typeIds = Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id]));
  const domainIds = Object.fromEntries(model.domains.map((domain) => [domain.defKey, domain.id]));
  return tables.map((table) => {
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
        extProps: (manifestField.ascii || ["entity_no", "liable_entity_no", "entity_id"].includes(manifestField.defKey) || (table.defKey === "account" && manifestField.defKey === "owner_id") || (table.defKey === "economic_fact_line" && ["party_id", "payer_id", "payee_id"].includes(manifestField.defKey)) || (table === CURRENT_COMPENSATION_EVENT && ["event_key", "event_type", "sub_event_type"].includes(manifestField.defKey)))
          ? { characterSet: "ascii", collation: "ascii_bin" } : {},
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
      headers: clone(model.profile.headers),
      fields,
      correlations: [],
      indexes: buildIndexes(table, fieldsByKey),
      type: "P",
    };
  });
}

function applyRelations(entities) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  for (const relation of RELATIONS) {
    const child = entitiesByKey.get(relation.childTable);
    const parent = entitiesByKey.get(relation.parentTable);
    invariant(child && parent, `关系包含未知表：${JSON.stringify(relation)}`);
    const childField = child.fields.find((field) => field.defKey === relation.childField);
    const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
    invariant(childField && parentField, `关系包含未知字段：${JSON.stringify(relation)}`);
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

function primaryGroupFor(tableKey) {
  const groups = DIAGRAM_GROUPS.filter((group) => group.primary.includes(tableKey));
  invariant(groups.length === 1, `表必须且只能属于一个主图组：${tableKey}`);
  return groups[0];
}

function diagramReferences(group) {
  const references = [];
  for (const relation of RELATIONS.filter((item) => primaryGroupFor(item.childTable).defKey === group.defKey)) {
    if (!group.primary.includes(relation.parentTable) && !references.includes(relation.parentTable)) {
      references.push(relation.parentTable);
    }
  }
  return references;
}

function buildDiagrams(entities) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  return DIAGRAM_GROUPS.map((group) => {
    const references = diagramReferences(group);
    const tableKeys = [...group.primary, ...references];
    const columnCount = 4;
    const heights = tableKeys.map((tableKey) => {
      const entity = entitiesByKey.get(tableKey);
      invariant(entity, `图组包含未知表：${group.defKey}.${tableKey}`);
      return Math.max(220, 86 + entity.fields.length * 22);
    });
    const rowOffsets = [];
    for (let row = 0, y = 0; row * columnCount < tableKeys.length; row += 1) {
      rowOffsets.push(y);
      y += Math.max(...heights.slice(row * columnCount, (row + 1) * columnCount)) + 60;
    }
    const cellByTable = new Map();
    const cells = tableKeys.map((tableKey, index) => {
      const entity = entitiesByKey.get(tableKey);
      const cell = {
        id: stableUuid(`diagram/${group.defKey}/table/${tableKey}`),
        shape: "table",
        position: { x: (index % columnCount) * 430, y: rowOffsets[Math.floor(index / columnCount)] },
        count: 0,
        originKey: entity.id,
        type: "P",
        size: { width: 390, height: heights[index] },
      };
      cellByTable.set(tableKey, cell);
      return cell;
    });
    const groupRelations = RELATIONS.filter((relation) => primaryGroupFor(relation.childTable).defKey === group.defKey);
    for (const relation of groupRelations) {
      const child = entitiesByKey.get(relation.childTable);
      const parent = entitiesByKey.get(relation.parentTable);
      const childField = child.fields.find((field) => field.defKey === relation.childField);
      const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
      invariant(cellByTable.has(relation.childTable) && cellByTable.has(relation.parentTable), `图组缺少关系锚点：${relationStableKey(relation)}`);
      cells.push({
        id: stableUuid(`diagram/${group.defKey}/relation/${relationStableKey(relation)}`),
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
      defKey: group.defKey,
      defName: group.defName,
      relationType: "field",
      canvasData: { cells },
      properties: {
        primaryTables: group.primary.map(physicalTableKey),
        referenceTables: references.map(physicalTableKey),
      },
      id: stableUuid(`diagram/${group.defKey}`),
      comment: `${group.defName}边界关系图`,
    };
  });
}

function isCodeLike(fieldKey) {
  return /(?:^|_)(?:code|type|status|currency|country|direction|decision|result|bucket|side|mode|role|method|validation|bearer)$/u.test(fieldKey)
    || ["source_system", "event_type", "execution_type", "document_type", "record_type"].includes(fieldKey);
}

function validateManifest() {
  invariant(TABLES.length === 84, `Finance 必须有 84 张表，当前为 ${TABLES.length}`);
  invariant(new Set(TABLES.map((table) => table.defKey)).size === TABLES.length, "Finance 表名重复");
  const primaryTables = DIAGRAM_GROUPS.flatMap((group) => group.primary);
  invariant(primaryTables.length === 84 && new Set(primaryTables).size === 84, "每张表必须恰好属于一个主图组");
  invariant(
    JSON.stringify([...primaryTables].sort()) === JSON.stringify(TABLES.map((table) => table.defKey).sort()),
    "图组主表集合与生产 Manifest 不一致",
  );
  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  for (const table of TABLES) {
    invariant(`t_${table.defKey}`.length <= 32, `表名超过 32 字符：t_${table.defKey}`);
    invariant(table.fields.filter((field) => field.primaryKey).length === 1, `表必须只有一个主键：${table.defKey}`);
    for (const field of table.fields) {
      invariant(field.defKey.length <= 32, `字段名超过 32 字符：${table.defKey}.${field.defKey}`);
      invariant(!(field.closedValues && field.openSource), `字段同时声明闭合/开放值：${table.defKey}.${field.defKey}`);
      if (isCodeLike(field.defKey)) {
        invariant(field.closedValues || field.openSource, `代码/类型/状态字段未声明取值契约：${table.defKey}.${field.defKey}`);
      }
      if (field.type === "BOOLEAN" && !field.notNull) {
        invariant(fieldComment(field).includes("NULL"), `可空布尔字段说明必须明确 NULL：${table.defKey}.${field.defKey}`);
      }
      if (["active_slot", "active_lock_flag"].includes(field.defKey)) {
        invariant(/1/u.test(field.description) && /NULL/u.test(field.description), `活动唯一占位说明必须明确 1/NULL：${table.defKey}.${field.defKey}`);
      }
      invariant(!/(?:^|_)(?:pan|cvv|cvc|track_data|pin_block|password|private_key|jdbc_url)(?:_|$)/iu.test(field.defKey), `禁止的敏感/连接字段：${table.defKey}.${field.defKey}`);
    }
  }
  for (const tuple of FILE_REFERENCE_TUPLES) {
    const table = tableByKey.get(tuple.table);
    invariant(table, `文件二元组表不存在：${tuple.table}`);
    const fileId = table.fields.find((field) => field.defKey === tuple.id);
    const fileHash = table.fields.find((field) => field.defKey === tuple.hash);
    invariant(fileId && fileHash, `文件引用必须保存 ID + Content Hash：${tuple.table}`);
    invariant(fileId.notNull === fileHash.notNull, `文件 ID/Hash 空值语义必须一致：${tuple.table}`);
  }
  invariant(!tableByKey.get("channel_debit_item").fields.some((field) => field.defKey === "channel_refund_attempt_id"), "不得恢复已删除的 Payment Refund Attempt 引用");
  const compensation = tableByKey.get("compensation_event");
  invariant(compensation.fields.some((field) => field.defKey === "lock_version"), "Compensation Event 必须使用 lock_version");
  invariant(!compensation.fields.some((field) => field.defKey === "version"), "Compensation Event 不得使用含混的 version");
  const idempotency = tableByKey.get("command_idempotency");
  invariant(!idempotency.fields.some((field) => field.defKey === "version"), "Command Idempotency 不需要机械 version");
  const account = tableByKey.get("account");
  invariant(account.fields.some((field) => field.defKey === "normal_side"), "统一账户必须冻结正常余额方向");
  const bookSequence = tableByKey.get("book_sequence");
  invariant(!bookSequence.fields.some((field) => field.defKey === "version"), "账簿分录序列使用事务行锁，不得并用乐观锁版本");
  invariant(Object.keys(CHECK_CONTRACTS).length >= 15, "Finance 物理 CHECK 契约不完整");
  invariant(
    JSON.stringify(Object.keys(CHECK_CONTRACTS).sort()) === JSON.stringify(Object.keys(CHECK_BINDINGS).sort()),
    "每条 CHECK 契约必须有且只有一个表绑定清单",
  );
  for (const [checkName, boundTables] of Object.entries(CHECK_BINDINGS)) {
    invariant(boundTables.length > 0, `CHECK 契约没有绑定表：${checkName}`);
    invariant(new Set(boundTables).size === boundTables.length, `CHECK 契约重复绑定表：${checkName}`);
    for (const tableKey of boundTables) invariant(tableByKey.has(tableKey), `CHECK 契约绑定未知表：${checkName}.${tableKey}`);
  }
}

function validateGeneratedModel(model) {
  const entitiesByKey = new Map(model.entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  for (const relation of RELATIONS) {
    const child = entitiesByKey.get(relation.childTable);
    const childField = child.fields.find((field) => field.defKey === relation.childField);
    const primary = child.fields.find((field) => field.primaryKey);
    const leftPrefix = childField.id === primary.id || child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id);
    invariant(leftPrefix, `关系子字段缺少左前缀索引：${relation.childTable}.${relation.childField}`);
    if (relation.cardinality === "1:1") {
      const exactUnique = child.indexes.filter((index) => index.unique && index.fields.length === 1 && index.fields[0].fieldDefKey === childField.id);
      invariant(childField.id === primary.id || exactUnique.length === 1, `1:1 关系必须由单字段 UK 保证：${relation.childTable}.${relation.childField}`);
    }
  }
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells)
    .filter((cell) => cell.shape === "erdRelation");
  invariant(relationCells.length === RELATIONS.length, "每条关系必须且只能出现在一个图组");
  invariant(new Set(relationCells.map((cell) => cell.originKey)).size === RELATIONS.length, "图组关系发生重复");
  invariant(model.dbConn.length === 0 && model.dbConns.length === 0 && model.profile.default.dbConn === "", "模型不得保存数据库连接");
}

export function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: model.entities.flatMap((entity) => entity.correlations).length,
    oneToOneRelations: model.entities.flatMap((entity) => entity.correlations).filter((relation) => relation.myRows === "1").length,
    diagramGroups: model.diagrams.length,
  };
}

export function structuralProjection(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    domains: model.domains.map((domain) => ({
      id: domain.id, defKey: domain.defKey, applyFor: domain.applyFor, len: domain.len, scale: domain.scale,
    })),
    entities: model.entities.map((entity) => {
      const fields = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        defName: entity.defName,
        fields: entity.fields.map((field) => ({
          id: field.id, defKey: field.defKey, defName: field.defName, comment: field.comment,
          type: field.type, len: field.len, scale: field.scale, primaryKey: field.primaryKey,
          notNull: field.notNull, autoIncrement: field.autoIncrement, baseType: field.baseType,
          domain: field.domain,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id, defKey: index.defKey, unique: index.unique,
          fields: index.fields.map((entry) => ({
            id: entry.id, field: fields.get(entry.fieldDefKey).defKey, ascOrDesc: entry.ascOrDesc,
          })),
        })),
        correlations: entity.correlations.map((relation) => {
          const parent = entitiesById.get(relation.refEntity);
          const parentFields = new Map(parent.fields.map((field) => [field.id, field]));
          return {
            childField: fields.get(relation.myField).defKey,
            parentTable: parent.defKey,
            parentField: parentFields.get(relation.refField).defKey,
            myRows: relation.myRows,
            refRows: relation.refRows,
          };
        }),
      };
    }),
    diagrams: model.diagrams.map((diagram) => ({
      id: diagram.id,
      defKey: diagram.defKey,
      primaryTables: diagram.properties.primaryTables,
      referenceTables: diagram.properties.referenceTables,
      cells: diagram.canvasData.cells.map((cell) => ({
        id: cell.id, shape: cell.shape, originKey: cell.originKey,
        source: cell.source, target: cell.target, relation: cell.relation,
      })),
    })),
  };
}

export function structuralProjectionDigest(model) {
  return sha256(JSON.stringify(structuralProjection(model)));
}

export function generateFinanceModel({ template = readApprovedTemplate() } = {}) {
  validateManifest();
  const model = normalizeTemplate(template);
  model.entities = buildEntities(model);
  applyRelations(model.entities);
  model.diagrams = buildDiagrams(model.entities);
  validateGeneratedModel(model);
  return model;
}

export function serializeFinanceModel(model = generateFinanceModel()) {
  return `${JSON.stringify(model, null, 2)}\n`;
}

/** 把逻辑模型投影为一个服务库的物理模型：只保留本库表、库内关系与相应图组。 */
export function projectServiceModel(model, service, tableKeys) {
  const physical = new Set(tableKeys.map(physicalTableKey));
  const projected = clone(model);
  projected.name = `Code-MoR ${SERVICE_TITLES[service]} Database V1`;
  projected.describe = `${SERVICE_TITLES[service]} 服务库物理模型：由 Finance 逻辑模型按 design/03 表归属切分（第 07 步），只保留本库关系`;
  projected.entities = projected.entities.filter((entity) => physical.has(entity.defKey));
  invariant(projected.entities.length === physical.size, `服务模型缺表：${service}`);
  const renamed = new Map();
  for (const spec of SERVICE_TABLE_VARIANTS[service] ?? []) {
    const table = spec.table ?? spec;
    const replacedKey = physicalTableKey(spec.replaces ?? table.defKey);
    const [variant] = buildEntities(model, [table]);
    const index = projected.entities.findIndex((entity) => entity.defKey === replacedKey);
    invariant(index >= 0, `服务模型变体没有对应的逻辑表：${service}.${replacedKey}`);
    if (spec.replaces) {
      adoptReplacedIdentity(variant, projected.entities[index]);
      renamed.set(replacedKey, variant.defKey);
    } else {
      variant.correlations = [];
    }
    projected.entities[index] = variant;
  }
  const added = SERVICE_ADDED_TABLES[service];
  if (added) {
    const entities = buildEntities(model, added.tables);
    projected.entities = added.prepend ? [...entities, ...projected.entities] : [...projected.entities, ...entities];
  }
  const entityIds = new Set(projected.entities.map((entity) => entity.id));
  for (const entity of projected.entities) {
    entity.correlations = entity.correlations.filter((relation) => entityIds.has(relation.refEntity));
  }
  const relationIds = new Set(projected.entities.flatMap((entity) => entity.correlations.map((relation) => relation.id)));
  projected.diagrams = projected.diagrams
    .map((diagram) => ({
      ...diagram,
      canvasData: {
        ...diagram.canvasData,
        cells: diagram.canvasData.cells.filter((cell) => (cell.shape === "table"
          ? entityIds.has(cell.originKey) : relationIds.has(cell.originKey))),
      },
      properties: {
        primaryTables: diagram.properties.primaryTables.filter((table) => physical.has(table))
          .map((table) => renamed.get(table) ?? table),
        referenceTables: diagram.properties.referenceTables.filter((table) => physical.has(table))
          .map((table) => renamed.get(table) ?? table),
      },
    }))
    .filter((diagram) => diagram.properties.primaryTables.length > 0);
  return projected;
}

/**
 * 物理更名变体沿用被替换逻辑表的实体 ID、同名字段 ID 与出向关系，使库内其他表的关系与图组锚点保持有效；删除的字段随之消失。
 */
function adoptReplacedIdentity(variant, replaced) {
  const replacedFields = new Map(replaced.fields.map((field) => [field.defKey, field.id]));
  const fieldIds = new Map();
  for (const field of variant.fields) {
    const adopted = replacedFields.get(field.defKey);
    if (adopted) {
      fieldIds.set(field.id, adopted);
      field.id = adopted;
    }
  }
  for (const index of variant.indexes) {
    for (const entry of index.fields) entry.fieldDefKey = fieldIds.get(entry.fieldDefKey) ?? entry.fieldDefKey;
  }
  const kept = new Set(variant.fields.map((field) => field.id));
  variant.id = replaced.id;
  variant.correlations = replaced.correlations.filter((relation) => kept.has(relation.myField));
}

/** 生成器维护的两个服务库物理模型（account、ledger、billing、reconciliation 已脱离生成器），按 FINANCE_SERVICES 顺序；每库表数必须等于 design/03 §4.2。 */
export function generateFinanceServiceModels(model = generateFinanceModel()) {
  const tablesByService = serviceTables();
  const models = new Map();
  for (const service of FINANCE_SERVICES) {
    const tableKeys = tablesByService.get(service);
    const count = tableKeys.length + (SERVICE_ADDED_TABLES[service]?.tables.length ?? 0);
    invariant(count === EXPECTED_SERVICE_TABLE_COUNTS[service],
      `${service} 表数 ${count} 与 design/03 §4.2 的 ${EXPECTED_SERVICE_TABLE_COUNTS[service]} 不一致`);
    models.set(service, projectServiceModel(model, service, tableKeys));
  }
  return models;
}

function summaryOf(model) {
  const totals = modelTotals(model);
  return `${totals.tables} tables, ${totals.fields} fields, ${totals.uniqueIndexes} unique indexes, ${totals.accessIndexes} access indexes, ${totals.relations} relations, ${totals.oneToOneRelations} one-to-one relations, ${totals.diagramGroups} diagram groups`;
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  invariant(unknown.length === 0, `未知参数：${unknown.join(", ")}`);
  const logical = generateFinanceModel();
  process.stdout.write(`Finance logical model validated (${summaryOf(logical)})\n`);
  for (const [service, model] of generateFinanceServiceModels(logical)) {
    const outputPath = serviceModelPath(service);
    const expected = serializeFinanceModel(model);
    if (process.argv.includes("--check")) {
      invariant(existsSync(outputPath), `${service} 模型不存在：${outputPath}`);
      invariant(readFileSync(outputPath, "utf8") === expected, `${service} 模型已过期，请重新生成`);
      process.stdout.write(`${service} model is up to date (${summaryOf(model)})\n`);
      continue;
    }
    mkdirSync(path.dirname(outputPath), { recursive: true });
    writeFileSync(outputPath, expected, "utf8");
    process.stdout.write(`Wrote ${service} model: ${outputPath} (${summaryOf(model)})\n`);
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runCli();
}

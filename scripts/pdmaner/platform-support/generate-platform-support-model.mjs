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
import {
  CHECK_BINDINGS,
  CHECK_CONTRACTS,
  COMPOSITE_FK_CONTRACTS,
  DIAGRAM_GROUPS,
  FILE_REFERENCE_TUPLES,
  VAULT_REFERENCE_SETS,
} from "./model-policy.mjs";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const workspaceRoot = path.resolve(scriptDirectory, "../../..");
const templatePath = path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json");
const outputPath = path.join(workspaceRoot, "platform-support-service/docs/database/platform-support.pdma.json");

const MODEL_KEY = "code-mor/platform-support/database/v1";
const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const MYSQL_MAPPING_ID = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const JAVA_MAPPING_ID = "797A1496-D649-4261-89B4-544132EC3F36";
const MYBATIS_MAPPING_ID = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";

const tablesByLogicalKey = new Map(TABLES.map((table) => [table.defKey, table]));
const tablesByPhysicalKey = new Map(TABLES.map((table) => [`t_${table.physicalKey}`, table]));

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
  const bytes = Buffer.from(sha256(`${MODEL_KEY}:${key}`).slice(0, 32), "hex");
  bytes[6] = (bytes[6] & 0x0f) | 0x50;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString("hex").toUpperCase();
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join("-");
}

function physicalTableKey(logicalKey) {
  const table = tablesByLogicalKey.get(logicalKey);
  invariant(table, `未知逻辑表：${logicalKey}`);
  return `t_${table.physicalKey}`;
}

function logicalTableKey(physicalKey) {
  const table = tablesByPhysicalKey.get(physicalKey);
  invariant(table, `未知物理表：${physicalKey}`);
  return table.defKey;
}

function relationStableKey(relation) {
  return `${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`;
}

function relationId(relation) {
  return stableUuid(`relation/${relationStableKey(relation)}`);
}

function readApprovedTemplate() {
  const bytes = readFileSync(templatePath);
  const digest = sha256(bytes);
  invariant(digest === EXPECTED_TEMPLATE_DIGEST, `共享模板摘要发生变化：${digest}`);
  return JSON.parse(bytes);
}

function normalizeTemplate(template) {
  const model = clone(template);
  model.name = "Code-MoR Platform Support Database V1";
  model.describe = "基于已确认 Platform Support V1 物理清单确定性生成的 PDManer 模型";
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
  invariant(!mappings.has("localDate"), "共享模板不应预置 Platform Support 派生的 localDate 映射");
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
      ? "该记录在 Platform Support 数据库中的 UTC 微秒创建时间。"
      : "该可变记录最后一次持久化更新的 UTC 微秒时间。";
  }

  const namingRules = new Map(model.namingRules.map((rule) => [rule.id, rule]));
  for (const [id, objectName, ruleName] of [
    ["63F1DC0E-6A76-4B75-B3DA-4B00657B4E1B", "field", "字段代码不超过 32 个字符"],
    ["668CBEE6-E0B7-4ACE-B72E-63942963B191", "entity", "表代码不超过 32 个字符"],
    ["11BD987F-82E7-418E-A752-FDD84F1582A2", "field", "字段代码不超过 32 个字符"],
    ["29D0A8D9-ABE2-451F-8A39-52FAB02E62B9", "index", "索引代码不超过 32 个字符"],
  ]) {
    const rule = namingRules.get(id);
    if (rule) {
      rule.defName = ruleName;
      rule.programCode = `return (data.${objectName}.defKey||"").length <= 32;`;
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
  const immutableTables = TABLES.filter((table) => table.audit === "immutable").map((table) => physicalTableKey(table.defKey));
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
    timeRule.programCode = "let key=(data.field.defKey||'').toLowerCase(); let type=(data.field.type||data.field.dbType||'').toUpperCase(); if(key.endsWith('_date')){return type==='DATE';} if(key.endsWith('_time')||key.endsWith('_at')||key.endsWith('_from')||key.endsWith('_to')||key.endsWith('_until')){return type==='DATETIME' && parseInt(data.field.len)===6;} return true;";
  }
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
      comment: `${spec.unique ? "保证业务唯一性" : "支持访问路径"}：${spec.fields.join("、")}`,
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
        extProps: {},
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
      properties: {
        logicalTableKey: table.defKey,
        compositeForeignKeys: clone(COMPOSITE_FK_CONTRACTS.filter(
          (contract) => contract.childTable === physicalTableKey(table.defKey),
        )),
      },
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
    if (!group.primary.includes(relation.parentTable) && !references.includes(relation.parentTable)) references.push(relation.parentTable);
  }
  return references;
}

function buildDiagrams(entities) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  return DIAGRAM_GROUPS.map((group) => {
    const references = diagramReferences(group);
    const tableKeys = [...group.primary, ...references];
    const columnCount = 4;
    const heights = tableKeys.map((tableKey) => Math.max(220, 86 + entitiesByKey.get(tableKey).fields.length * 22));
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

function isCodeLike(field) {
  if (!["VARCHAR", "CHAR"].includes(field.type)) return false;
  return /(?:^|_)(?:type|status|currency|country|direction|decision|result|mode|bearer)$/u.test(field.defKey)
    || ["source_system", "event_type", "execution_type", "document_type", "business_domain", "action_category", "matching_mode", "outcome"].includes(field.defKey);
}

function validateManifest() {
  invariant(TABLES.length === 92, `Platform Support 必须有 92 张表，当前为 ${TABLES.length}`);
  const primaryTables = DIAGRAM_GROUPS.flatMap((group) => group.primary);
  invariant(primaryTables.length === 92 && new Set(primaryTables).size === 92, "每张表必须恰好属于一个主图组");
  invariant(JSON.stringify([...primaryTables].sort()) === JSON.stringify(TABLES.map((table) => table.defKey).sort()), "图组主表集合与生产 Manifest 不一致");
  const tableByKey = new Map(TABLES.map((table) => [table.defKey, table]));
  for (const table of TABLES) {
    invariant(physicalTableKey(table.defKey).length <= 32, `表名超过 32 字符：${physicalTableKey(table.defKey)}`);
    invariant(table.fields.filter((field) => field.primaryKey).length === 1, `表必须只有一个主键：${table.defKey}`);
    for (const field of table.fields) {
      invariant(!(field.closedValues && field.openSource), `字段同时声明闭合/开放值：${table.defKey}.${field.defKey}`);
      if (isCodeLike(field)) invariant(field.closedValues || field.openSource, `代码/类型/状态字段未声明取值契约：${table.defKey}.${field.defKey}`);
      invariant(!/(?:^|_)(?:pan|cvv|cvc|track_data|pin_block|card_expiry|password|private_key|jdbc_url|ciphertext|encrypted_value|dek)(?:_|$)/iu.test(field.defKey), `禁止的敏感/连接字段：${table.defKey}.${field.defKey}`);
    }
  }
  for (const tuple of FILE_REFERENCE_TUPLES) {
    const table = tableByKey.get(tuple.table);
    const fileId = table?.fields.find((field) => field.defKey === tuple.id);
    const fileHash = table?.fields.find((field) => field.defKey === tuple.hash);
    invariant(fileId && fileHash && fileId.notNull === fileHash.notNull, `文件引用必须保存同空值语义的 ID + Content Hash：${tuple.table}`);
  }
  for (const tuple of VAULT_REFERENCE_SETS) {
    const table = tableByKey.get(tuple.table);
    invariant(tuple.fields.every((key) => table?.fields.some((field) => field.defKey === key)), `Vault 引用字段集合不完整：${tuple.table}`);
  }
  for (const forbidden of ["resource_grant", "file_access_grant", "file_access_event", "document_source_ref", "notification_preference", "audit_access_event", "dictionary_set", "dictionary_version", "dictionary_entry", "dictionary_translation", "dictionary_release", "job_definition", "job_schedule", "job_run", "job_attempt", "job_lease"]) invariant(!tableByKey.has(forbidden), `不得预建 t_${forbidden}`);
  const compensation = tableByKey.get("compensation_event");
  invariant(compensation.fields.some((field) => field.defKey === "lock_version"), "Compensation Event 必须使用 lock_version");
  invariant(!compensation.fields.some((field) => field.defKey === "version"), "Compensation Event 不得使用含混的 version");
  const commandIdempotency = tableByKey.get("command_idempotency");
  invariant(!commandIdempotency.fields.some((field) => field.defKey === "version"), "Command Idempotency 不得机械增加 version");
  invariant(Object.keys(CHECK_CONTRACTS).length >= 40, "Platform Support 物理 CHECK 契约不完整");
  invariant(JSON.stringify(Object.keys(CHECK_CONTRACTS).sort()) === JSON.stringify(Object.keys(CHECK_BINDINGS).sort()), "每条 CHECK 契约必须有且只有一个绑定清单");
  for (const [checkName, boundTables] of Object.entries(CHECK_BINDINGS)) {
    invariant(boundTables.length > 0 && new Set(boundTables).size === boundTables.length, `CHECK 绑定为空或重复：${checkName}`);
    for (const tableKey of boundTables) invariant(tableByKey.has(tableKey), `CHECK 契约绑定未知表：${checkName}.${tableKey}`);
  }
  invariant(new Set(COMPOSITE_FK_CONTRACTS.map((contract) => contract.name)).size === COMPOSITE_FK_CONTRACTS.length, "复合外键名称必须唯一");
  for (const contract of COMPOSITE_FK_CONTRACTS) {
    invariant(/^fk_[a-z0-9_]+$/u.test(contract.name) && contract.name.length <= 64, `非法复合外键名：${contract.name}`);
    invariant(contract.childFields.length >= 2 && contract.childFields.length === contract.parentFields.length, `复合外键列数不合法：${contract.name}`);
    const child = tablesByPhysicalKey.get(contract.childTable);
    const parent = tablesByPhysicalKey.get(contract.parentTable);
    invariant(child && parent, `复合外键引用未知表：${contract.name}`);
    const childFields = contract.childFields.map((fieldKey) => child.fields.find((field) => field.defKey === fieldKey));
    const parentFields = contract.parentFields.map((fieldKey) => parent.fields.find((field) => field.defKey === fieldKey));
    invariant(childFields.every(Boolean) && parentFields.every(Boolean), `复合外键引用未知字段：${contract.name}`);
    invariant(childFields.every((field, index) => field.type === parentFields[index].type && field.len === parentFields[index].len && field.scale === parentFields[index].scale), `复合外键字段类型不一致：${contract.name}`);
    invariant(child.indexes.some((index) => contract.childFields.every((field, offset) => index.fields[offset] === field)), `复合外键子表缺少左前缀索引：${contract.name}`);
    invariant(parent.indexes.some((index) => index.unique && index.fields.length === contract.parentFields.length && contract.parentFields.every((field, offset) => index.fields[offset] === field)), `复合外键父表缺少精确唯一键：${contract.name}`);
  }
  for (const relation of RELATIONS) {
    const parent = tableByKey.get(relation.parentTable);
    invariant(parent, `关系父表不存在：${relation.parentTable}`);
    invariant(parent.fields.some((field) => field.defKey === relation.parentField), `关系父字段不存在：${relation.parentTable}.${relation.parentField}`);
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
  for (const contract of COMPOSITE_FK_CONTRACTS) {
    const child = entitiesByKey.get(logicalTableKey(contract.childTable));
    const persisted = child?.properties?.compositeForeignKeys?.find((item) => item.name === contract.name);
    invariant(JSON.stringify(persisted) === JSON.stringify(contract), `复合外键未完整写入模型：${contract.name}`);
  }
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells).filter((cell) => cell.shape === "erdRelation");
  invariant(relationCells.length === RELATIONS.length, "每条关系必须且只能出现在一个图组");
  invariant(new Set(relationCells.map((cell) => cell.originKey)).size === RELATIONS.length, "图组关系发生重复");
  invariant(model.dbConn.length === 0 && model.dbConns.length === 0 && model.profile.default.dbConn === "", "模型不得保存数据库连接");
}

export function modelTotals(model) {
  const indexes = model.entities.flatMap((entity) => entity.indexes);
  const relations = model.entities.flatMap((entity) => entity.correlations);
  return {
    tables: model.entities.length,
    fields: model.entities.flatMap((entity) => entity.fields).length,
    uniqueIndexes: indexes.filter((index) => index.unique).length,
    accessIndexes: indexes.filter((index) => !index.unique).length,
    relations: relations.length,
    oneToOneRelations: relations.filter((relation) => relation.myRows === "1").length,
    diagramGroups: model.diagrams.length,
  };
}

export function structuralProjection(model) {
  const entitiesById = new Map(model.entities.map((entity) => [entity.id, entity]));
  return {
    domains: model.domains.map((domain) => ({ id: domain.id, defKey: domain.defKey, applyFor: domain.applyFor, len: domain.len, scale: domain.scale })),
    entities: model.entities.map((entity) => {
      const fields = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        defName: entity.defName,
        comment: entity.comment,
        properties: {
          logicalTableKey: entity.properties?.logicalTableKey,
          compositeForeignKeys: entity.properties?.compositeForeignKeys ?? [],
        },
        fields: entity.fields.map((field) => ({
          id: field.id, defKey: field.defKey, defName: field.defName, comment: field.comment,
          type: field.type, len: field.len, scale: field.scale, primaryKey: field.primaryKey,
          notNull: field.notNull, autoIncrement: field.autoIncrement, baseType: field.baseType, domain: field.domain,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id, defKey: index.defKey, unique: index.unique,
          fields: index.fields.map((entry) => ({ id: entry.id, field: fields.get(entry.fieldDefKey).defKey, ascOrDesc: entry.ascOrDesc })),
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

export function generatePlatformSupportModel({ template = readApprovedTemplate() } = {}) {
  validateManifest();
  const model = normalizeTemplate(template);
  model.entities = buildEntities(model);
  applyRelations(model.entities);
  model.diagrams = buildDiagrams(model.entities);
  validateGeneratedModel(model);
  return model;
}

export function serializePlatformSupportModel(model = generatePlatformSupportModel()) {
  return `${JSON.stringify(model, null, 2)}\n`;
}

function runCli() {
  const unknown = process.argv.slice(2).filter((argument) => argument !== "--check");
  invariant(unknown.length === 0, `未知参数：${unknown.join(", ")}`);
  const expected = serializePlatformSupportModel();
  const totals = modelTotals(JSON.parse(expected));
  const summary = `${totals.tables} tables, ${totals.fields} fields, ${totals.uniqueIndexes} unique indexes, ${totals.accessIndexes} access indexes, ${totals.relations} relations, ${totals.oneToOneRelations} one-to-one relations, ${totals.diagramGroups} diagram groups`;
  if (process.argv.includes("--check")) {
    invariant(existsSync(outputPath), `Platform Support 模型不存在：${outputPath}`);
    invariant(readFileSync(outputPath, "utf8") === expected, "Platform Support 模型已过期，请重新生成");
    process.stdout.write(`Platform Support model is up to date (${summary})\n`);
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote Platform Support model: ${outputPath}\n${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) runCli();

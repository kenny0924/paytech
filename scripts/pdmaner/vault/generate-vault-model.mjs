import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { EXPECTED_MODEL_TOTALS, RELATIONS, TABLES } from "./manifest.mjs";
import {
  ALLOWED_FIELDS_BY_TABLE,
  ALLOWED_INDEX_SIGNATURES,
  ALLOWED_RELATION_SIGNATURES,
  ALLOWED_TABLE_KEYS,
  APPROVED_DEFAULT_VALUES,
  APPROVED_SENSITIVE_FIELDS,
  ASCII_BINARY_FIELDS,
  CHECK_BINDINGS,
  CHECK_CONTRACTS,
  DIAGRAM_GROUPS,
  FORBIDDEN_FIELD_PATTERN,
  FORBIDDEN_TABLE_KEYS,
} from "./model-policy.mjs";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const workspaceRoot = path.resolve(scriptDirectory, "../../..");
const templatePath = path.join(workspaceRoot, "agent-docs/templates/pdmaner/template.pdma.json");
const outputPath = path.join(workspaceRoot, "vault-service/docs/database/vault.pdma.json");

const MODEL_KEY = "code-mor/vault/database/v1";
const EXPECTED_TEMPLATE_DIGEST = "51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540";
const MYSQL_MAPPING_ID = "29D1CE08-4C35-4D2D-AAA9-23D93305B52E";
const JAVA_MAPPING_ID = "797A1496-D649-4261-89B4-544132EC3F36";
const MYBATIS_MAPPING_ID = "895CFD1D-4273-4D32-A2C4-CAC70200AB5B";
const APPROVED_BINARY_FIELDS = new Set(Object.keys(APPROVED_SENSITIVE_FIELDS));

const tablesByLogicalKey = new Map(TABLES.map((tableSpec) => [tableSpec.defKey, tableSpec]));
const tablesByPhysicalKey = new Map(TABLES.map((tableSpec) => [tableSpec.physicalKey, tableSpec]));

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
  const tableSpec = tablesByLogicalKey.get(logicalKey);
  invariant(tableSpec, `未知逻辑表：${logicalKey}`);
  return tableSpec.physicalKey;
}

function logicalTableKey(physicalKey) {
  const tableSpec = tablesByPhysicalKey.get(physicalKey);
  invariant(tableSpec, `未知物理表：${physicalKey}`);
  return tableSpec.defKey;
}

function relationStableKey(relation) {
  return `${relation.childTable}/${relation.childField}/${relation.parentTable}/${relation.parentField}`;
}

function relationSignature(relation) {
  return `${relation.cardinality}|${relation.childTable}.${relation.childField}|${relation.parentTable}.${relation.parentField}`;
}

function indexSignature(indexSpec) {
  return `${indexSpec.unique ? "UK" : "IDX"}|${indexSpec.defKey}|${indexSpec.fields.join(",")}`;
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

function setMapping(mapping, mysqlType, javaType, mybatisType) {
  invariant(mapping, `共享模板缺少 ${mysqlType} 类型映射`);
  mapping[MYSQL_MAPPING_ID] = mysqlType;
  mapping[JAVA_MAPPING_ID] = javaType;
  mapping[MYBATIS_MAPPING_ID] = mybatisType;
}

function normalizeTemplate(template) {
  const model = clone(template);
  model.name = "Code-MoR Vault Database V1";
  model.describe = "基于批准的 Vault V1 六表物理清单（核心三表与策略三表）确定性生成的 PDManer 模型";
  model.dbConn = [];
  model.dbConns = [];
  model.profile.default.dbConn = "";

  const mappings = new Map(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping]));
  setMapping(mappings.get("BIGINT"), "BIGINT", "Long", "Long");
  setMapping(mappings.get("date"), "DATETIME", "Instant", "Instant");
  setMapping(mappings.get("bytes"), "BLOB", "byte[]", "byte[]");

  const domains = new Map(model.domains.map((domain) => [domain.defKey, domain]));
  domains.get("DateTime").len = 6;
  domains.get("DateTime").scale = "";

  for (const auditField of model.profile.default.entityInitFields) {
    const isCreateTime = auditField.defKey === "create_time";
    auditField.type = "DATETIME";
    auditField.len = 6;
    auditField.scale = "";
    auditField.notNull = true;
    auditField.autoIncrement = false;
    auditField.defaultValue = "";
    auditField.defName = isCreateTime ? "创建时间" : "更新时间";
    auditField.comment = isCreateTime
      ? "该记录在 Vault 数据库中的 UTC 微秒创建时间。"
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
    primaryKeyRule.programCode = "let fields=data.entity.fields||[]; let primary=fields.filter(field=>field.primaryKey); return primary.length===1 && primary[0].notNull && !primary[0].autoIncrement && (primary[0].type||primary[0].dbType||'').toUpperCase()==='BIGINT';";
  }

  const tablePrefixRule = namingRules.get("1168C7C2-8E8E-4FB7-B639-B3DE839C395A");
  if (tablePrefixRule) {
    tablePrefixRule.defName = "物理表名使用 t_ 前缀和小写 snake_case";
    tablePrefixRule.programCode = "return /^t_[a-z][a-z0-9_]*$/.test(data.entity.defKey);";
  }

  const indexPrefixRule = namingRules.get("023450B3-AAE2-4DC1-AE63-2196DD82823D");
  if (indexPrefixRule) {
    indexPrefixRule.defName = "唯一索引以 uk_ 开头，普通索引以 idx_ 开头";
    indexPrefixRule.programCode = "return data.index.unique ? /^uk_[a-z0-9_]+$/.test(data.index.defKey) : /^idx_[a-z0-9_]+$/.test(data.index.defKey);";
  }

  const auditRule = namingRules.get("0B2F0BD2-3B84-4AB1-BA29-9DE9620AF608");
  if (auditRule) {
    auditRule.defName = "可变表使用创建和更新时间，不可变访问事件与策略变更只保留创建时间";
    auditRule.programCode = "let actual=data.entity.fields.map(field=>field.defKey); let immutable=['t_vault_access_event','t_vault_policy_change'].includes(data.entity.defKey); return actual.includes('create_time') && (immutable ? !actual.includes('update_time') : actual.includes('update_time'));";
  }

  const fieldReservedRule = namingRules.get("BEC54F19-52D5-4882-BCE1-4439785F8001");
  if (fieldReservedRule) {
    fieldReservedRule.programCode = `let approvedWords={'t_vault_access_event':['action'],'t_vault_access_rule':['action','status'],'t_vault_data_type':['status']}; if((approvedWords[data.entity.defKey]||[]).includes(data.field.defKey)){return true;}\n${fieldReservedRule.programCode}`;
  }

  const timeRule = namingRules.get("D330BCC3-DBAB-4677-8C5A-A301003A5878");
  if (timeRule) {
    timeRule.defName = "UTC 时刻字段使用 DATETIME(6)";
    timeRule.programCode = "let key=(data.field.defKey||'').toLowerCase(); let type=(data.field.type||data.field.dbType||'').toUpperCase(); if(key.endsWith('_time')){return type==='DATETIME' && parseInt(data.field.len)===6;} return true;";
  }

  const largeFieldRule = namingRules.get("1C563E17-262B-4EB6-87F0-203CAC667CF0");
  if (largeFieldRule) {
    largeFieldRule.defName = "二进制大字段仅限 Vault 批准的密码学材料";
    largeFieldRule.programCode = "let key=data.entity.defKey+'.'+data.field.defKey; let type=(data.field.type||data.field.dbType||'').toUpperCase(); let allowed=['t_vault_record.value_fingerprint','t_vault_record.ciphertext','t_vault_record.encryption_nonce','t_vault_record.wrapped_dek','t_vault_protect_request.request_hash']; return !['BLOB','BINARY'].includes(type) || allowed.includes(key);";
  }

  const mysqlDdlTemplate = model.profile.codeTemplates.find((templateSpec) => templateSpec.type === "dbDDL");
  const typeLengthTemplate = "{{=field.type}}{{?field.len>0}}{{='('}}{{=field.len}}{{?field.scale>0}}{{=','}}{{=field.scale}}{{?}}{{=')'}}{{?}}";
  invariant(mysqlDdlTemplate?.createTable.includes(typeLengthTemplate), "MySQL DDL 模板缺少受控字段类型片段");
  mysqlDdlTemplate.createTable = mysqlDdlTemplate.createTable.replace(
    typeLengthTemplate,
    `${typeLengthTemplate}{{? field.extProps && field.extProps.characterSet }} CHARACTER SET {{=field.extProps.characterSet}} COLLATE {{=field.extProps.collation}}{{?}}`,
  );
  return model;
}

function baseTypeFor(fieldSpec, typeIds, domainIds) {
  const mapping = { baseType: "", domain: "" };
  if (fieldSpec.primaryKey) mapping.domain = domainIds.PrimaryKey;
  if (fieldSpec.type === "BIGINT") mapping.baseType = typeIds.BIGINT;
  else if (fieldSpec.type === "VARCHAR") mapping.baseType = typeIds.string;
  else if (fieldSpec.type === "DATETIME") mapping.baseType = typeIds.date;
  else if (fieldSpec.type === "BINARY" || fieldSpec.type === "BLOB") mapping.baseType = typeIds.bytes;
  else if (fieldSpec.type === "INT" || fieldSpec.type === "TINYINT") mapping.baseType = typeIds.int;
  else if (fieldSpec.type === "JSON") mapping.baseType = typeIds.largeText;
  else invariant(false, `不支持的 Vault 字段类型：${fieldSpec.type}`);
  if (fieldSpec.type === "DATETIME") mapping.domain = domainIds.DateTime;
  if (fieldSpec.type === "INT") mapping.domain = domainIds.Int;
  return mapping;
}

function fieldComment(fieldSpec) {
  return `${fieldSpec.description.replace(/。$/u, "")}。`;
}

function buildIndexes(tableSpec, fieldsByKey) {
  return tableSpec.indexes.map((indexSpec) => ({
    defKey: indexSpec.defKey,
    defName: `${indexSpec.unique ? "唯一" : "访问"}索引 ${indexSpec.fields.join("、")}`,
    unique: indexSpec.unique,
    comment: `${indexSpec.unique ? "保证业务唯一性" : "支持访问路径"}：${indexSpec.fields.join("、")}`,
    fields: indexSpec.fields.map((fieldKey, position) => {
      const modelField = fieldsByKey.get(fieldKey);
      invariant(modelField, `索引字段不存在：${tableSpec.defKey}.${indexSpec.defKey}.${fieldKey}`);
      return {
        fieldDefKey: modelField.id,
        ascOrDesc: "A",
        id: stableUuid(`index-field/${tableSpec.defKey}/${indexSpec.defKey}/${position}/${fieldKey}`),
      };
    }),
    id: stableUuid(`index/${tableSpec.defKey}/${indexSpec.defKey}`),
  }));
}

function buildEntities(model) {
  const typeIds = Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id]));
  const domainIds = Object.fromEntries(model.domains.map((domain) => [domain.defKey, domain.id]));
  return TABLES.map((tableSpec) => {
    const fields = tableSpec.fields.map((fieldSpec) => {
      const mapping = baseTypeFor(fieldSpec, typeIds, domainIds);
      return {
        defKey: fieldSpec.defKey,
        defName: fieldSpec.defName,
        comment: fieldComment(fieldSpec),
        type: fieldSpec.type,
        len: fieldSpec.len,
        scale: fieldSpec.scale,
        primaryKey: fieldSpec.primaryKey,
        notNull: fieldSpec.notNull,
        autoIncrement: false,
        defaultValue: fieldSpec.defaultValue,
        hideInGraph: false,
        refDict: "",
        baseType: mapping.baseType,
        extProps: fieldSpec.characterSet
          ? { characterSet: fieldSpec.characterSet, collation: fieldSpec.collation }
          : {},
        domain: mapping.domain,
        id: stableUuid(`field/${tableSpec.defKey}/${fieldSpec.defKey}`),
      };
    });
    const fieldsByKey = new Map(fields.map((modelField) => [modelField.defKey, modelField]));
    return {
      id: stableUuid(`entity/${tableSpec.defKey}`),
      env: { base: { nameSpace: "", codeRoot: "" } },
      defKey: tableSpec.physicalKey,
      defName: tableSpec.defName,
      comment: tableSpec.comment,
      properties: { logicalTableKey: tableSpec.defKey },
      sysProps: { nameTemplate: "{defKey}[{defName}]" },
      notes: {},
      headers: clone(model.profile.headers),
      fields,
      correlations: [],
      indexes: buildIndexes(tableSpec, fieldsByKey),
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

function buildDiagrams(entities) {
  const entitiesByKey = new Map(entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  return DIAGRAM_GROUPS.map((group) => {
    const tableCells = group.primary.map((tableKey, position) => {
      const entity = entitiesByKey.get(tableKey);
      invariant(entity, `图组包含未知表：${group.defKey}.${tableKey}`);
      return {
        id: stableUuid(`diagram/${group.defKey}/table/${tableKey}`),
        shape: "table",
        position: { x: (position % 3) * 430, y: Math.floor(position / 3) * 420 },
        count: 0,
        originKey: entity.id,
        type: "P",
        size: { width: 390, height: Math.max(220, 86 + entity.fields.length * 22) },
      };
    });
    const tableCellByKey = new Map(group.primary.map((tableKey, index) => [tableKey, tableCells[index]]));
    const relationCells = RELATIONS.map((relation) => {
      const child = entitiesByKey.get(relation.childTable);
      const parent = entitiesByKey.get(relation.parentTable);
      const childField = child.fields.find((field) => field.defKey === relation.childField);
      const parentField = parent.fields.find((field) => field.defKey === relation.parentField);
      return {
        id: stableUuid(`diagram/${group.defKey}/relation/${relationStableKey(relation)}`),
        shape: "erdRelation",
        originKey: relationId(relation),
        source: { cell: tableCellByKey.get(relation.childTable).id, port: `${childField.id}%out` },
        target: { cell: tableCellByKey.get(relation.parentTable).id, port: `${parentField.id}%in` },
        relation: relation.cardinality,
        vertices: [],
        fillColor: "#ACDAFC",
        attrs: { line: { strokeDasharray: "" } },
        isLock: true,
      };
    });
    return {
      defKey: group.defKey,
      defName: group.defName,
      relationType: "field",
      canvasData: { cells: [...tableCells, ...relationCells] },
      properties: {
        primaryTables: group.primary.map(physicalTableKey),
        referenceTables: [],
      },
      id: stableUuid(`diagram/${group.defKey}`),
      comment: `${group.defName}边界关系图`,
    };
  });
}

export function validateVaultManifest() {
  invariant(JSON.stringify(TABLES.map((tableSpec) => tableSpec.defKey)) === JSON.stringify(ALLOWED_TABLE_KEYS), "生产表顺序偏离批准白名单");
  invariant(TABLES.length === EXPECTED_MODEL_TOTALS.tables, `Vault 必须有 ${EXPECTED_MODEL_TOTALS.tables} 张表`);
  invariant(TABLES.flatMap((tableSpec) => tableSpec.fields).length === EXPECTED_MODEL_TOTALS.fields, `Vault 必须有 ${EXPECTED_MODEL_TOTALS.fields} 个字段`);
  invariant(FORBIDDEN_TABLE_KEYS.every((tableKey) => !tablesByLogicalKey.has(tableKey)), "Vault 包含禁止预建的表");
  invariant(DIAGRAM_GROUPS.length === 1 && DIAGRAM_GROUPS[0].defKey === "vault_core", "Vault 必须只有 vault_core 图组");
  invariant(JSON.stringify(DIAGRAM_GROUPS[0].primary) === JSON.stringify(ALLOWED_TABLE_KEYS), "vault_core 表顺序偏离批准清单");

  for (const tableSpec of TABLES) {
    invariant(tableSpec.physicalKey.length <= 32, `物理表名超过 32 字符：${tableSpec.physicalKey}`);
    invariant(JSON.stringify(tableSpec.fields.map((fieldSpec) => fieldSpec.defKey)) === JSON.stringify(ALLOWED_FIELDS_BY_TABLE[tableSpec.defKey]), `表字段偏离批准白名单：${tableSpec.defKey}`);
    invariant(JSON.stringify(tableSpec.indexes.map(indexSignature)) === JSON.stringify(ALLOWED_INDEX_SIGNATURES[tableSpec.defKey]), `表索引偏离批准白名单：${tableSpec.defKey}`);
    invariant(tableSpec.fields.filter((fieldSpec) => fieldSpec.primaryKey).length === 1, `表必须恰有一个主键：${tableSpec.defKey}`);
    invariant(tableSpec.fields[0].primaryKey && tableSpec.fields[0].type === "BIGINT", `首字段必须是 Snowflake BIGINT 主键：${tableSpec.defKey}`);
    const expectedAudit = tableSpec.audit === "immutable" ? ["create_time"] : ["create_time", "update_time"];
    const actualAudit = tableSpec.fields.filter((fieldSpec) => ["create_time", "update_time"].includes(fieldSpec.defKey)).map((fieldSpec) => fieldSpec.defKey);
    invariant(JSON.stringify(actualAudit) === JSON.stringify(expectedAudit), `审计时间结构错误：${tableSpec.defKey}`);
    for (const fieldSpec of tableSpec.fields) {
      invariant(!FORBIDDEN_FIELD_PATTERN.test(fieldSpec.defKey), `出现禁止字段：${tableSpec.defKey}.${fieldSpec.defKey}`);
      invariant(fieldSpec.defaultValue === (APPROVED_DEFAULT_VALUES[`${tableSpec.defKey}.${fieldSpec.defKey}`] ?? ""), `字段默认值偏离批准清单：${tableSpec.defKey}.${fieldSpec.defKey}`);
      if (APPROVED_BINARY_FIELDS.has(fieldSpec.defKey)) {
        invariant(APPROVED_SENSITIVE_FIELDS[fieldSpec.defKey] === tableSpec.defKey, `密码学字段出现在未批准表中：${tableSpec.defKey}.${fieldSpec.defKey}`);
      }
    }
    for (const indexSpec of tableSpec.indexes) {
      invariant(indexSpec.defKey.length <= 32, `索引名超过 32 字符：${indexSpec.defKey}`);
    }
  }

  for (const [fieldKey, tableKey] of Object.entries(APPROVED_SENSITIVE_FIELDS)) {
    invariant(tablesByLogicalKey.get(tableKey).fields.some((fieldSpec) => fieldSpec.defKey === fieldKey), `批准密码学字段缺失：${tableKey}.${fieldKey}`);
  }
  for (const fieldKey of ASCII_BINARY_FIELDS) {
    const matches = TABLES.flatMap((tableSpec) => tableSpec.fields).filter((fieldSpec) => fieldSpec.defKey === fieldKey);
    invariant(matches.length > 0, `批准 ASCII 字段缺失：${fieldKey}`);
    invariant(matches.every((fieldSpec) => fieldSpec.characterSet === "ascii" && fieldSpec.collation === "ascii_bin"), `机器标识必须使用 ascii/ascii_bin：${fieldKey}`);
  }

  const indexes = TABLES.flatMap((tableSpec) => tableSpec.indexes);
  invariant(indexes.filter((indexSpec) => indexSpec.unique).length === EXPECTED_MODEL_TOTALS.uniqueIndexes, "唯一索引总数错误");
  invariant(indexes.filter((indexSpec) => !indexSpec.unique).length === EXPECTED_MODEL_TOTALS.accessIndexes, "访问索引总数错误");
  invariant(RELATIONS.length === EXPECTED_MODEL_TOTALS.relations, "本库关系总数错误");
  invariant(JSON.stringify(RELATIONS.map(relationSignature)) === JSON.stringify(ALLOWED_RELATION_SIGNATURES), "本库关系偏离批准白名单");
  for (const relation of RELATIONS) {
    const child = tablesByLogicalKey.get(relation.childTable);
    const parent = tablesByLogicalKey.get(relation.parentTable);
    const childField = child?.fields.find((fieldSpec) => fieldSpec.defKey === relation.childField);
    const parentField = parent?.fields.find((fieldSpec) => fieldSpec.defKey === relation.parentField);
    invariant(childField && parentField, `关系字段不存在：${relationStableKey(relation)}`);
    invariant(childField.type === parentField.type && childField.len === parentField.len, `关系字段类型不一致：${relationStableKey(relation)}`);
    invariant(child.indexes.some((indexSpec) => indexSpec.fields[0] === relation.childField), `关系子字段缺少左前缀索引：${relation.childTable}.${relation.childField}`);
  }

  invariant(Object.keys(CHECK_CONTRACTS).length === 4, "Vault 必须恰有四条 CHECK 侧车契约");
  invariant(JSON.stringify(Object.keys(CHECK_CONTRACTS)) === JSON.stringify(Object.keys(CHECK_BINDINGS)), "CHECK 契约与绑定清单不一致");
  for (const boundTables of Object.values(CHECK_BINDINGS)) {
    invariant(boundTables.length > 0 && boundTables.every((tableKey) => tablesByLogicalKey.has(tableKey)), "CHECK 契约绑定未知表");
  }
}

function validateGeneratedModel(model) {
  invariant(model.dbConn.length === 0 && model.dbConns.length === 0 && model.profile.default.dbConn === "", "模型不得保存数据库连接");
  invariant(model.name === "Code-MoR Vault Database V1", "Vault 模型名称错误");
  invariant(JSON.stringify(modelTotals(model)) === JSON.stringify(EXPECTED_MODEL_TOTALS), "生成模型总数偏离批准基线");
  const entitiesByKey = new Map(model.entities.map((entity) => [logicalTableKey(entity.defKey), entity]));
  for (const relation of RELATIONS) {
    const child = entitiesByKey.get(relation.childTable);
    const childField = child.fields.find((field) => field.defKey === relation.childField);
    const primary = child.fields.find((field) => field.primaryKey);
    invariant(childField.id === primary.id || child.indexes.some((index) => index.fields[0]?.fieldDefKey === childField.id), `生成关系缺少左前缀索引：${relation.childTable}.${relation.childField}`);
  }
  const relationCells = model.diagrams.flatMap((diagram) => diagram.canvasData.cells).filter((cell) => cell.shape === "erdRelation");
  invariant(relationCells.length === RELATIONS.length, "每条关系必须且只能出现在 vault_core 图组");
  invariant(new Set(relationCells.map((cell) => cell.originKey)).size === RELATIONS.length, "图组关系发生重复");
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
    mappings: model.dataTypeMapping.mappings.map((mapping) => ({
      defKey: mapping.defKey,
      mysql: mapping[MYSQL_MAPPING_ID],
      java: mapping[JAVA_MAPPING_ID],
      mybatis: mapping[MYBATIS_MAPPING_ID],
    })),
    entities: model.entities.map((entity) => {
      const fieldsById = new Map(entity.fields.map((field) => [field.id, field]));
      return {
        id: entity.id,
        defKey: entity.defKey,
        defName: entity.defName,
        comment: entity.comment,
        fields: entity.fields.map((field) => ({
          id: field.id,
          defKey: field.defKey,
          defName: field.defName,
          comment: field.comment,
          type: field.type,
          len: field.len,
          scale: field.scale,
          primaryKey: field.primaryKey,
          notNull: field.notNull,
          autoIncrement: field.autoIncrement,
          baseType: field.baseType,
          domain: field.domain,
          extProps: field.extProps,
        })),
        indexes: entity.indexes.map((index) => ({
          id: index.id,
          defKey: index.defKey,
          unique: index.unique,
          fields: index.fields.map((entry) => ({
            id: entry.id,
            field: fieldsById.get(entry.fieldDefKey).defKey,
            ascOrDesc: entry.ascOrDesc,
          })),
        })),
        correlations: entity.correlations.map((relation) => {
          const parent = entitiesById.get(relation.refEntity);
          const parentFieldsById = new Map(parent.fields.map((field) => [field.id, field]));
          return {
            childField: fieldsById.get(relation.myField).defKey,
            parentTable: parent.defKey,
            parentField: parentFieldsById.get(relation.refField).defKey,
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
        id: cell.id,
        shape: cell.shape,
        originKey: cell.originKey,
        source: cell.source,
        target: cell.target,
        relation: cell.relation,
      })),
    })),
  };
}

export function structuralProjectionDigest(model) {
  return sha256(JSON.stringify(structuralProjection(model)));
}

export function generateVaultModel({ template = readApprovedTemplate() } = {}) {
  validateVaultManifest();
  const model = normalizeTemplate(template);
  model.entities = buildEntities(model);
  applyRelations(model.entities);
  model.diagrams = buildDiagrams(model.entities);
  validateGeneratedModel(model);
  return model;
}

export function serializeVaultModel(model = generateVaultModel()) {
  return `${JSON.stringify(model, null, 2)}\n`;
}

function totalsSummary(totals) {
  return `${totals.tables} tables, ${totals.fields} fields, ${totals.uniqueIndexes} unique indexes, ${totals.accessIndexes} access indexes, ${totals.relations} relations, ${totals.oneToOneRelations} one-to-one relations, ${totals.diagramGroups} diagram groups`;
}

function runCli() {
  const unknownArguments = process.argv.slice(2).filter((argument) => argument !== "--check");
  invariant(unknownArguments.length === 0, `未知参数：${unknownArguments.join(", ")}`);
  const expected = serializeVaultModel();
  const summary = totalsSummary(modelTotals(JSON.parse(expected)));
  if (process.argv.includes("--check")) {
    invariant(existsSync(outputPath), `Vault 模型不存在：${outputPath}`);
    invariant(readFileSync(outputPath, "utf8") === expected, "Vault 模型已过期，请重新生成");
    process.stdout.write(`Vault model is up to date (${summary})\n`);
    return;
  }
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, expected, "utf8");
  process.stdout.write(`Wrote Vault model: ${outputPath}\n${summary}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) runCli();

const TYPE_PATTERN = /^([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?$/u;
const IDENTIFIER_PATTERN = /^[a-z][a-z0-9_]*$/u;

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function requireIdentifier(kind, value) {
  invariant(IDENTIFIER_PATTERN.test(value), `${kind}必须使用小写 snake_case：${value}`);
  invariant(value.length <= 32, `${kind}超过 32 个字符：${value}`);
}

function requireChinese(kind, identifier, value) {
  invariant(/[\u3400-\u9fff]/u.test(value), `${kind}缺少中文语义：${identifier}`);
}

function parseType(typeToken) {
  const match = typeToken.match(TYPE_PATTERN);
  invariant(match, `无法解析字段类型：${typeToken}`);
  const parsedType = {
    type: match[1],
    len: match[2] === undefined ? "" : Number(match[2]),
    scale: match[3] === undefined ? "" : Number(match[3]),
  };
  invariant(["BIGINT", "INT", "TINYINT", "VARCHAR", "BINARY", "BLOB", "JSON", "DATETIME"].includes(parsedType.type), `Vault DSL 不支持字段类型：${typeToken}`);
  if (["BIGINT", "INT", "BLOB", "JSON"].includes(parsedType.type)) {
    invariant(parsedType.len === "" && parsedType.scale === "", `${parsedType.type} 不允许长度或精度：${typeToken}`);
  }
  if (["VARCHAR", "BINARY"].includes(parsedType.type)) {
    invariant(Number.isInteger(parsedType.len) && parsedType.len > 0 && parsedType.scale === "", `${parsedType.type} 必须声明正整数长度：${typeToken}`);
  }
  if (parsedType.type === "TINYINT") {
    invariant(parsedType.len === 1 && parsedType.scale === "", `Vault 布尔开关必须使用 TINYINT(1)：${typeToken}`);
  }
  if (parsedType.type === "DATETIME") {
    invariant(parsedType.len === 6 && parsedType.scale === "", `Vault 时刻必须使用 DATETIME(6)：${typeToken}`);
  }
  return Object.freeze(parsedType);
}

export function field(
  defKey,
  typeToken,
  nullability,
  defName,
  description,
  {
    primaryKey: isPrimaryKey = false,
    defaultValue = "",
    characterSet = null,
    collation = null,
  } = {},
) {
  requireIdentifier("字段名", defKey);
  invariant(nullability === "NN" || nullability === "N", `非法空值标记：${defKey}.${nullability}`);
  requireChinese("字段中文名", defKey, defName);
  requireChinese("字段中文说明", defKey, description);
  invariant(Boolean(characterSet) === Boolean(collation), `字符集与排序规则必须成对声明：${defKey}`);
  invariant(!characterSet || /^[a-z0-9_]+$/u.test(characterSet), `非法字符集：${defKey}.${characterSet}`);
  invariant(!collation || /^[a-z0-9_]+$/u.test(collation), `非法排序规则：${defKey}.${collation}`);

  const parsedType = parseType(typeToken);
  invariant(!isPrimaryKey || (parsedType.type === "BIGINT" && nullability === "NN"), `主键必须是非空 BIGINT：${defKey}`);
  invariant(!characterSet || parsedType.type === "VARCHAR", `仅 VARCHAR 字段可声明字符集：${defKey}`);

  return Object.freeze({
    defKey,
    ...parsedType,
    notNull: nullability === "NN",
    primaryKey: isPrimaryKey,
    defName,
    description,
    defaultValue,
    characterSet,
    collation,
  });
}

export function primaryKey(defKey, defName, description) {
  return field(defKey, "BIGINT", "NN", defName, description, { primaryKey: true });
}

function index(defKey, unique, fieldKeys) {
  requireIdentifier("索引名", defKey);
  invariant(defKey.startsWith(unique ? "uk_" : "idx_"), `索引前缀不正确：${defKey}`);
  invariant(fieldKeys.length > 0, `索引必须至少包含一个字段：${defKey}`);
  invariant(new Set(fieldKeys).size === fieldKeys.length, `索引字段重复：${defKey}`);
  for (const fieldKey of fieldKeys) requireIdentifier("索引字段名", fieldKey);
  return Object.freeze({ defKey, unique, fields: Object.freeze([...fieldKeys]) });
}

export function uniqueIndex(defKey, ...fieldKeys) {
  return index(defKey, true, fieldKeys.flat());
}

export function accessIndex(defKey, ...fieldKeys) {
  return index(defKey, false, fieldKeys.flat());
}

export function relationship(childField, parentTable, parentField, cardinality = "n:1") {
  requireIdentifier("关系子字段名", childField);
  requireIdentifier("关系父表名", parentTable);
  requireIdentifier("关系父字段名", parentField);
  invariant(cardinality === "n:1" || cardinality === "1:1", `非法关系基数：${cardinality}`);
  return Object.freeze({ childField, parentTable, parentField, cardinality });
}

export const relation = relationship;

const CREATE_TIME = field(
  "create_time",
  "DATETIME(6)",
  "NN",
  "创建时间",
  "该记录在 Vault 数据库中的 UTC 微秒创建时间",
);

const UPDATE_TIME = field(
  "update_time",
  "DATETIME(6)",
  "NN",
  "更新时间",
  "该可变记录最后一次持久化更新的 UTC 微秒时间",
);

export function table({ key, name, comment, audit, fields, indexes = [], relations = [] }) {
  requireIdentifier("逻辑表名", key);
  requireIdentifier("物理表名", `t_${key}`);
  requireChinese("表中文名", key, name);
  requireChinese("表中文说明", key, comment);
  invariant(audit === "mutable" || audit === "immutable", `非法审计模式：${key}.${audit}`);
  invariant(fields.length > 0, `表必须包含字段：${key}`);

  const auditFields = audit === "mutable" ? [CREATE_TIME, UPDATE_TIME] : [CREATE_TIME];
  const allFields = Object.freeze([...fields, ...auditFields]);
  const primaryKeys = allFields.filter((item) => item.primaryKey);
  invariant(primaryKeys.length === 1, `表必须恰有一个 Snowflake BIGINT 主键：${key}`);
  invariant(primaryKeys[0] === allFields[0], `主键必须是首字段：${key}`);
  invariant(primaryKeys[0].type === "BIGINT" && primaryKeys[0].notNull, `主键必须是非空 BIGINT：${key}`);
  invariant(new Set(allFields.map((item) => item.defKey)).size === allFields.length, `字段重复：${key}`);

  const fieldKeys = new Set(allFields.map((item) => item.defKey));
  invariant(new Set(indexes.map((item) => item.defKey)).size === indexes.length, `索引名重复：${key}`);
  for (const item of indexes) {
    for (const fieldKey of item.fields) {
      invariant(fieldKeys.has(fieldKey), `索引字段不存在：${key}.${item.defKey}.${fieldKey}`);
    }
  }
  for (const item of relations) {
    invariant(fieldKeys.has(item.childField), `关系子字段不存在：${key}.${item.childField}`);
    invariant(
      indexes.some((indexSpec) => indexSpec.fields[0] === item.childField),
      `关系子字段缺少左前缀索引：${key}.${item.childField}`,
    );
  }

  return Object.freeze({
    defKey: key,
    physicalKey: `t_${key}`,
    defName: name,
    comment,
    audit,
    fields: allFields,
    indexes: Object.freeze([...indexes]),
    relations: Object.freeze(relations.map((item) => Object.freeze({ ...item, childTable: key }))),
  });
}

export const defineTable = table;

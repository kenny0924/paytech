const TYPE_PATTERN = /^([A-Z]+)(?:\((\d+)(?:,(\d+))?\))?$/u;

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

function parseType(typeToken) {
  const match = typeToken.match(TYPE_PATTERN);
  invariant(match, `无法解析字段类型：${typeToken}`);
  return Object.freeze({
    type: match[1],
    len: match[2] === undefined ? "" : Number(match[2]),
    scale: match[3] === undefined ? "" : Number(match[3]),
  });
}

export function field(
  defKey,
  typeToken,
  nullability,
  defName,
  description,
  {
    values = null,
    source = null,
    primaryKey = false,
    defaultValue = "",
    characterSet = null,
    collation = null,
  } = {},
) {
  invariant(/^[a-z][a-z0-9_]*$/u.test(defKey), `非法字段名：${defKey}`);
  invariant(defKey.length <= 32, `字段名超过 32 字符：${defKey}`);
  invariant(["NN", "N"].includes(nullability), `非法空值标记：${defKey}.${nullability}`);
  invariant(/[\u3400-\u9fff]/u.test(defName), `字段缺少中文名：${defKey}`);
  invariant(/[\u3400-\u9fff]/u.test(description), `字段缺少中文说明：${defKey}`);
  invariant(!(values && source), `字段不能同时是闭合枚举和开放代码：${defKey}`);
  invariant(!values || values.length > 0, `闭合枚举不能为空：${defKey}`);
  invariant(Boolean(characterSet) === Boolean(collation), `字段字符集与排序规则必须成对声明：${defKey}`);
  invariant(!characterSet || /^[a-z0-9_]+$/u.test(characterSet), `非法字段字符集：${defKey}.${characterSet}`);
  invariant(!collation || /^[a-z0-9_]+$/u.test(collation), `非法字段排序规则：${defKey}.${collation}`);
  return Object.freeze({
    defKey,
    ...parseType(typeToken),
    notNull: nullability === "NN",
    primaryKey,
    defName,
    description,
    closedValues: values ? Object.freeze([...values]) : null,
    openSource: source,
    defaultValue,
    characterSet,
    collation,
  });
}

export function primaryKey(defKey, defName, description) {
  return field(defKey, "BIGINT", "NN", defName, description, { primaryKey: true });
}

function index(defKey, unique, fields) {
  invariant(new RegExp(`^${unique ? "uk" : "idx"}_[a-z0-9_]+$`, "u").test(defKey), `非法索引名：${defKey}`);
  invariant(defKey.length <= 32, `索引名超过 32 字符：${defKey}`);
  invariant(fields.length > 0, `索引没有字段：${defKey}`);
  invariant(new Set(fields).size === fields.length, `索引字段重复：${defKey}`);
  return Object.freeze({ defKey, unique, fields: Object.freeze([...fields]) });
}

export function uniqueIndex(defKey, ...fields) {
  return index(defKey, true, fields.flat());
}

export function accessIndex(defKey, ...fields) {
  return index(defKey, false, fields.flat());
}

export function relation(childField, parentTable, parentField, cardinality = "n:1") {
  invariant(["n:1", "1:1"].includes(cardinality), `非法关系基数：${cardinality}`);
  return Object.freeze({ childField, parentTable, parentField, cardinality });
}

const CREATE_TIME = field(
  "create_time",
  "DATETIME(6)",
  "NN",
  "创建时间",
  "该记录在 Platform Support 数据库中的 UTC 微秒创建时间",
);

const UPDATE_TIME = field(
  "update_time",
  "DATETIME(6)",
  "NN",
  "更新时间",
  "该可变记录最后一次持久化更新的 UTC 微秒时间",
);

function shortIndexKey(prefix, tableKey, fieldKey) {
  const full = `${prefix}_${tableKey}_${fieldKey}`;
  if (full.length <= 32) return full;
  return `${prefix}_${tableKey.slice(0, 12)}_${fieldKey.slice(0, 14)}`;
}

export function defineTable({
  key,
  physicalKey = key,
  name,
  comment,
  audit = "mutable",
  auditFieldOverrides = {},
  fields,
  indexes = [],
  relations = [],
}) {
  invariant(/^[a-z][a-z0-9_]*$/u.test(key), `非法逻辑表名：${key}`);
  invariant(/^[a-z][a-z0-9_]*$/u.test(physicalKey), `非法物理表名：${physicalKey}`);
  invariant(`t_${physicalKey}`.length <= 32, `物理表名超过 32 字符：t_${physicalKey}`);
  invariant(/[\u3400-\u9fff]/u.test(name), `表缺少中文名：${key}`);
  invariant(/[\u3400-\u9fff]/u.test(comment), `表缺少中文职责说明：${key}`);
  invariant(["mutable", "immutable", "custom"].includes(audit), `非法审计模式：${key}.${audit}`);
  invariant(fields.length > 0 && fields[0].primaryKey, `首字段必须是主键：${key}`);
  invariant(fields.filter((item) => item.primaryKey).length === 1, `表必须恰有一个主键：${key}`);
  const auditFields = audit === "mutable" ? [CREATE_TIME, UPDATE_TIME] : audit === "immutable" ? [CREATE_TIME] : [];
  invariant(Object.keys(auditFieldOverrides).every(key => auditFields.some(item => item.defKey === key)), "审计字段覆盖必须属于当前审计模式");
  const allFields = [...fields, ...auditFields.map(item => auditFieldOverrides[item.defKey] ?? item)];
  invariant(new Set(allFields.map((item) => item.defKey)).size === allFields.length, `字段重复：${key}`);
  const fieldKeys = new Set(allFields.map((item) => item.defKey));
  const normalizedIndexes = [...indexes];
  for (const item of relations) {
    invariant(fieldKeys.has(item.childField), `关系子字段不存在：${key}.${item.childField}`);
    const hasLeftPrefix = normalizedIndexes.some((entry) => entry.fields[0] === item.childField);
    if (!hasLeftPrefix) {
      const prefix = item.cardinality === "1:1" ? "uk" : "idx";
      const indexKey = shortIndexKey(prefix, physicalKey, item.childField);
      normalizedIndexes.push(item.cardinality === "1:1"
        ? uniqueIndex(indexKey, item.childField)
        : accessIndex(indexKey, item.childField));
    }
  }
  invariant(new Set(normalizedIndexes.map((item) => item.defKey)).size === normalizedIndexes.length, `索引名重复：${key}`);
  for (const item of normalizedIndexes) {
    for (const fieldKey of item.fields) invariant(fieldKeys.has(fieldKey), `索引字段不存在：${key}.${item.defKey}.${fieldKey}`);
  }
  return Object.freeze({
    defKey: key,
    physicalKey,
    defName: name,
    comment,
    audit,
    fields: Object.freeze(allFields),
    indexes: Object.freeze(normalizedIndexes),
    relations: Object.freeze(relations.map((item) => Object.freeze({ ...item, childTable: key }))),
  });
}

export const types = Object.freeze({
  dateTime: "DATETIME(6)",
  hash: "CHAR(64)",
  code: "VARCHAR(64)",
  longCode: "VARCHAR(128)",
  name: "VARCHAR(255)",
  text: "TEXT",
  json: "JSON",
});

export function idField(defKey, defName, nullability = "NN") {
  return field(defKey, "BIGINT", nullability, defName, `${defName}的稳定业务标识`);
}

export function codeField(defKey, defName, nullability = "NN", values = null, source = "Platform Support 受控代码目录") {
  return field(defKey, types.code, nullability, defName, `${defName}的稳定业务代码`, values ? { values } : { source });
}

export function stringField(defKey, defName, nullability = "NN", length = 255) {
  return field(defKey, `VARCHAR(${length})`, nullability, defName, `${defName}的业务值`);
}

export function timeField(defKey, defName, nullability = "NN") {
  return field(defKey, types.dateTime, nullability, defName, `${defName}对应的 UTC 微秒时间`);
}

export function integerField(defKey, defName, nullability = "NN") {
  return field(defKey, "INT", nullability, defName, `${defName}的整数值`);
}

export function longField(defKey, defName, nullability = "NN") {
  return field(defKey, "BIGINT", nullability, defName, `${defName}的长整数值`);
}

export function booleanField(defKey, defName, nullability = "NN") {
  return field(defKey, "BOOLEAN", nullability, defName, `${defName}，1 表示是，0 表示否`);
}

export function hashField(defKey, defName, nullability = "NN") {
  return field(defKey, types.hash, nullability, defName, `${defName}的 SHA-256 小写十六进制值`);
}

export function jsonField(defKey, defName, nullability = "NN") {
  return field(defKey, types.json, nullability, defName, `${defName}的有界版本化 JSON 内容`);
}

export function textField(defKey, defName, nullability = "NN") {
  return field(defKey, types.text, nullability, defName, `${defName}的有界文本内容`);
}

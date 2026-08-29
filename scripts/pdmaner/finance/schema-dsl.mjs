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
  { values = null, source = null, primaryKey = false, defaultValue = "" } = {},
) {
  invariant(/^[a-z][a-z0-9_]*$/u.test(defKey), `非法字段名：${defKey}`);
  invariant(["NN", "N"].includes(nullability), `非法空值标记：${defKey}.${nullability}`);
  invariant(/[\u3400-\u9fff]/u.test(defName), `字段缺少中文名：${defKey}`);
  invariant(/[\u3400-\u9fff]/u.test(description), `字段缺少中文说明：${defKey}`);
  invariant(!(values && source), `字段不能同时是闭合枚举和开放代码：${defKey}`);
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
  });
}

export function primaryKey(defKey, defName, description) {
  return field(defKey, "BIGINT", "NN", defName, description, { primaryKey: true });
}

export function uniqueIndex(defKey, ...fields) {
  return index(defKey, true, fields.flat());
}

export function accessIndex(defKey, ...fields) {
  return index(defKey, false, fields.flat());
}

function index(defKey, unique, fields) {
  invariant(new RegExp(`^${unique ? "uk" : "idx"}_[a-z0-9_]+$`, "u").test(defKey), `非法索引名：${defKey}`);
  invariant(defKey.length <= 32, `索引名超过 32 字符：${defKey}`);
  invariant(fields.length > 0, `索引没有字段：${defKey}`);
  return Object.freeze({ defKey, unique, fields: Object.freeze([...fields]) });
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
  "该记录在 Finance 数据库中的 UTC 微秒创建时间",
);
const UPDATE_TIME = field(
  "update_time",
  "DATETIME(6)",
  "NN",
  "更新时间",
  "该可变记录最后一次持久化更新的 UTC 微秒时间",
);

export function defineTable({
  key,
  name,
  comment,
  audit = "mutable",
  fields,
  indexes = [],
  relations = [],
}) {
  invariant(/^[a-z][a-z0-9_]*$/u.test(key), `非法逻辑表名：${key}`);
  invariant(/[\u3400-\u9fff]/u.test(name), `表缺少中文名：${key}`);
  invariant(/[\u3400-\u9fff]/u.test(comment), `表缺少中文职责说明：${key}`);
  invariant(["mutable", "immutable", "custom"].includes(audit), `非法审计模式：${key}.${audit}`);
  invariant(fields.length > 0 && fields[0].primaryKey, `首字段必须是主键：${key}`);
  const auditFields = audit === "mutable" ? [CREATE_TIME, UPDATE_TIME] : audit === "immutable" ? [CREATE_TIME] : [];
  const allFields = [...fields, ...auditFields];
  invariant(new Set(allFields.map((item) => item.defKey)).size === allFields.length, `字段重复：${key}`);
  const fieldKeys = new Set(allFields.map((item) => item.defKey));
  for (const item of indexes) {
    for (const fieldKey of item.fields) invariant(fieldKeys.has(fieldKey), `索引字段不存在：${key}.${item.defKey}.${fieldKey}`);
  }
  for (const item of relations) invariant(fieldKeys.has(item.childField), `关系子字段不存在：${key}.${item.childField}`);
  return Object.freeze({
    defKey: key,
    defName: name,
    comment,
    audit,
    fields: Object.freeze(allFields),
    indexes: Object.freeze([...indexes]),
    relations: Object.freeze(relations.map((item) => Object.freeze({ ...item, childTable: key }))),
  });
}

export const types = Object.freeze({
  amount: "DECIMAL(24,8)",
  ratio: "DECIMAL(18,10)",
  dateTime: "DATETIME(6)",
  currency: "CHAR(3)",
  country: "CHAR(2)",
  hash: "CHAR(64)",
  code: "VARCHAR(64)",
  longCode: "VARCHAR(128)",
  name: "VARCHAR(255)",
});

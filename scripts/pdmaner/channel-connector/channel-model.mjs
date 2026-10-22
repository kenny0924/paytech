// 2026-10-22 统一对外渠道出口第二段（requirements/20261020/channel-connector-统一对外渠道出口/technique.md 第 5 节）：
// 把 Payment 分区中的支付渠道实体 t_payment_channel 改造为支付渠道与服务渠道共用的 t_channel（归 protocol 模块），
// 并修正分区内引用渠道的注释与 t_channel_account.entity_no 的可空性。
// 改造只作用于 channel-connector 模型：Payment 生成器（generate-payment-model.mjs）与 payment / card 模型不变。
// 渠道实体沿用原实体、原有字段与唯一索引的稳定 ID，分区图组与 t_channel_account / t_channel_mid / t_channel_cost_plan 的关系保持连通；
// 新增字段与索引的 ID 使用本文件的命名空间。
// 2026-10-22 3DS 全链路（requirements/20261022/payment-3DS全链路/technique.md 第 3.4 节）：t_channel 增加 callback_definition_json，
// 并修正回调表 target_type、received_at 的注释。
import { createHash } from "node:crypto";

/** 新增字段与索引的稳定 ID 命名空间。 */
const ID_NAMESPACE = "code-mor/channel-connector/database/v1";

const SOURCE_TABLE = "t_payment_channel";

/** 渠道表物理名。 */
export const CHANNEL_TABLE = "t_channel";

const ASCII_BIN = Object.freeze({ characterSet: "ascii", collation: "ascii_bin" });

const CHANNEL_ID_COMMENT = "渠道内部唯一标识（本库 t_channel.channel_id）。";

const TARGET_TYPES = ["CHANNEL", "CHANNEL_ACCOUNT", "CHANNEL_MID", "MID_CAPABILITY", "CHANNEL_COST_VERSION",
  "STANDARD_API", "CHANNEL_API", "CHANNEL_SCRIPT", "CHANNEL_IMPLEMENTATION", "VERIFY_ACCOUNT", "VERIFY_CASE",
  "CHANNEL_CALL_GRANT"];

/** t_channel 的完整字段，按物理顺序；existing 为沿用原实体字段的 defKey。 */
const CHANNEL_FIELDS = Object.freeze([
  { defKey: "channel_id", existing: true, defName: "渠道标识",
    comment: "渠道的内部唯一标识；支付渠道的技术账号、MID、成本方案与 Payment 冻结的执行坐标均引用它。" },
  { defKey: "channel_code", existing: true, defName: "渠道编码", extProps: ASCII_BIN,
    comment: "平台稳定识别渠道的唯一业务代码，大小写敏感，格式为大写字母开头的大写字母、数字与下划线，2 至 64 字符，创建后不可修改；"
      + "也是外部 API 管理接口中的接入方编码。取值来源：渠道目录；非固定枚举。" },
  { defKey: "channel_name", existing: true, defName: "渠道名称", comment: "供运营配置和审阅展示的渠道正式名称。" },
  { defKey: "channel_type", type: "VARCHAR", len: 16, notNull: true, defName: "渠道类型", baseType: "string",
    comment: "渠道类型，创建后不可修改；PAYMENT 有绑定 OE 的技术账号、MID、能力与成本，只经支付入口执行；"
      + "SERVICE 最多一个不绑定 OE 的 ACTIVE 账号保存凭据，只经通用执行入口并按调用授权执行。允许值：PAYMENT、SERVICE。" },
  { defKey: "channel_status", existing: true, defName: "渠道状态",
    comment: "渠道从草稿、激活、暂停到关闭的当前运行状态；激活要求已配置出站地址。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。" },
  { defKey: "description", type: "VARCHAR", len: 512, notNull: false, defName: "说明", baseType: "string",
    comment: "渠道业务用途说明；未填写时为空，禁止填写凭据。" },
  { defKey: "endpoints", type: "JSON", len: "", notNull: false, defName: "出站地址", baseType: "largeText",
    comment: "本环境出站地址数组 [{endpointCode, baseUrl, sandbox}]，1 至 16 项，未配置时为空；endpointCode 在渠道内唯一，"
      + "baseUrl 的协议受部署允许列表限制（默认仅 https），不含用户信息、查询串与片段，最长 512 字符；保存时拦截回环、私网、链路本地、"
      + "保留与组播 IP 字面量及 localhost，执行时解析主机名后再次拦截，部署配置的 trusted-networks 网段放行；"
      + "sandbox 可选、缺省为 false，只有为 true 的地址允许沙箱验证运行。" },
  { defKey: "callback_definition_json", type: "JSON", len: "", notNull: false, defName: "通知定义", baseType: "largeText",
    comment: "支付渠道的支付结果通知定义：验签（algorithm 本期只允许 RSA_SHA256、签名请求头名、账号参数中的公钥名）、"
      + "通知字段路径（merchantReference、channelTransactionRef、status、eventTime）与成功应答（contentType、body）；"
      + "序列化后最多 4096 UTF-8 字节，禁止保存凭据或密钥；为空表示该渠道不接收通知，入站请求返回 404。" },
  { defKey: "version", type: "INT", len: "", notNull: true, defName: "并发版本", baseType: "int",
    comment: "名称、说明或出站地址保存时按版本条件更新并递增的并发版本，初始为 1；不是发布版本。" },
  { defKey: "created_by", type: "BIGINT", len: "", notNull: false, defName: "创建账号", baseType: "BIGINT",
    comment: "创建该渠道的 Operations 账号标识；2026-10-22 合表前创建且无接入方记录的渠道为空。" },
  { defKey: "updated_by", type: "BIGINT", len: "", notNull: false, defName: "修改账号", baseType: "BIGINT",
    comment: "最后一次修改该渠道的 Operations 账号标识；2026-10-22 合表前创建且未再修改、无接入方记录的渠道为空。" },
  { defKey: "create_time", existing: true },
  { defKey: "update_time", existing: true },
]);

/** 分区内其他表的注释修正：表.字段 → 新注释；notNull 仅在需要改变可空性时给出。 */
const FIELD_OVERRIDES = Object.freeze({
  "t_channel_account.channel_id": { comment: CHANNEL_ID_COMMENT },
  "t_channel_account.entity_no": { notNull: false,
    comment: "账号所属 Operating Entity 的不可变 CHAR(5) 编号，来源于 CCT；支付渠道账号必填，且与其当前绑定 MID 的 OE 一致，"
      + "不作为路由规则；服务渠道账号必须为空（应用校验），每个服务渠道最多一个 ACTIVE 账号保存凭据。" },
  "t_channel_mid.channel_id": { comment: CHANNEL_ID_COMMENT },
  "t_channel_cost_plan.channel_id": { comment: "支付渠道（本库 t_channel.channel_id，channel_type 为 PAYMENT）。" },
  // 2026-10-22 3DS 全链路（technique 第 3.4 节）：支付结果通知映射到 Payment Attempt；回调表随渠道迁入归 channel-connector。
  "t_channel_callback_item.target_type": { comment: "回调项映射到的业务对象闭合类型；PAYMENT_ATTEMPT 表示支付结果通知按商户订单号映射到 "
    + "Payment Attempt（target_id 为 Attempt ID，跨库引用不建外键）。允许值：PAYMENT_ATTEMPT、PAYMENT_ACTION、CHANNEL_REFUND、"
    + "CHANNEL_DISPUTE_NOTICE、CHANNEL_DISPUTE_ACTION、UNKNOWN。" },
  "t_channel_callback.received_at": { comment: "channel-connector 首次收到服务商回调或争议通知的业务时刻。" },
  "t_channel_config_change.target_type": { comment: `被变更对象类型；渠道出站地址变更记为 CHANNEL 与 CONFIGURATION。允许值：${TARGET_TYPES.join("、")}。` },
  "t_channel_config_change.from_status": { comment: "状态变更前的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组、成本版本或调用授权的状态代码；非固定枚举。" },
  "t_channel_config_change.to_status": { comment: "状态变更后的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组、成本版本或调用授权的状态代码；非固定枚举。" },
  "t_channel_config_change.change_detail": { comment: "外部 API 配置与渠道出站地址变更的字段摘要：{objectCode, beforeVersion, afterVersion, changes[{field, before, after}]}，"
    + "字段取值只记名称、编码与 SHA-256 摘要，不含定义原文、参数或凭据值；渠道状态、账号、MID、能力、成本版本与调用授权的变更为空。" },
});

/** 分区内其他表的表注释修正。 */
const TABLE_COMMENTS = Object.freeze({
  t_channel_account: "归属单个渠道的技术账号：支付渠道账号绑定一个 OE，服务渠道账号不绑定 OE 且每渠道最多一个 ACTIVE；账号参数及凭据只保存 Vault 引用。",
  t_channel_config_change: "渠道配置、成本版本、外部 API 配置与调用授权的人工变更留痕；与业务变更同事务写入，只增不改。",
});

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

/** 由规格构造一个新字段，类型 ID 取自模型的数据类型映射。 */
function newField(spec, typeIds) {
  const baseType = typeIds[spec.baseType];
  invariant(baseType, `未知数据类型映射：${spec.baseType}`);
  return {
    defKey: spec.defKey,
    defName: spec.defName,
    comment: spec.comment,
    type: spec.type,
    len: spec.len,
    scale: "",
    primaryKey: false,
    notNull: spec.notNull,
    autoIncrement: false,
    defaultValue: "",
    hideInGraph: false,
    refDict: "",
    baseType,
    extProps: {},
    domain: "",
    id: stableUuid(`field/${CHANNEL_TABLE}/${spec.defKey}`),
  };
}

/** 把支付渠道实体原地改造为 t_channel。 */
function reshapeChannel(entity, typeIds) {
  const existing = new Map(entity.fields.map((field) => [field.defKey, field]));
  entity.defKey = CHANNEL_TABLE;
  entity.defName = "渠道";
  entity.comment = "渠道（支付渠道与服务渠道）的稳定身份、类型、运行状态与本环境出站地址；支付渠道的技术账号、MID 与成本挂在其下。";
  entity.fields = CHANNEL_FIELDS.map((spec) => {
    if (!spec.existing) return newField(spec, typeIds);
    const field = existing.get(spec.defKey);
    invariant(field, `支付渠道实体缺少字段：${spec.defKey}`);
    if (spec.defName) field.defName = spec.defName;
    if (spec.comment) field.comment = spec.comment;
    if (spec.extProps) field.extProps = { ...spec.extProps };
    return field;
  });
  const fieldId = (key) => entity.fields.find((field) => field.defKey === key).id;
  const unique = entity.indexes.find((index) => index.defKey === "uk_payment_channel_code");
  invariant(unique && entity.indexes.length === 1, "支付渠道实体的索引与预期不符");
  unique.defKey = "uk_channel_code";
  unique.defName = "UK CHANNEL CODE";
  entity.indexes.push({
    defKey: "idx_channel_type_status",
    defName: "IDX CHANNEL TYPE STATUS",
    unique: false,
    comment: "访问索引：channel_type、channel_status",
    fields: ["channel_type", "channel_status"].map((key, position) => ({
      fieldDefKey: fieldId(key),
      ascOrDesc: "A",
      id: stableUuid(`index-field/${CHANNEL_TABLE}/idx_channel_type_status/${position}/${key}`),
    })),
    id: stableUuid(`index/${CHANNEL_TABLE}/idx_channel_type_status`),
  });
}

/** 修正分区内其他表的字段与表注释。 */
function applyOverrides(model) {
  const entities = new Map(model.entities.map((entity) => [entity.defKey, entity]));
  for (const [target, override] of Object.entries(FIELD_OVERRIDES)) {
    const [table, key] = target.split(".");
    const field = entities.get(table)?.fields.find((item) => item.defKey === key);
    invariant(field, `注释修正目标不存在：${target}`);
    field.comment = override.comment;
    if (override.notNull !== undefined) field.notNull = override.notNull;
  }
  for (const [table, comment] of Object.entries(TABLE_COMMENTS)) {
    invariant(entities.has(table), `表注释修正目标不存在：${table}`);
    entities.get(table).comment = comment;
  }
}

/**
 * 在 channel-connector 分区模型上把 t_payment_channel 改造为 t_channel，并修正相关注释；须在追加协议表之前执行。
 *
 * @param model 已按 Payment 分区生成的 channel-connector 模型，会被原地修改
 * @return 同一个模型
 */
export function reshapeChannelTables(model) {
  const entity = model.entities.find((item) => item.defKey === SOURCE_TABLE);
  invariant(entity, `分区模型缺少 ${SOURCE_TABLE}`);
  invariant(!model.entities.some((item) => item.defKey === CHANNEL_TABLE), `分区模型已存在 ${CHANNEL_TABLE}`);
  const typeIds = Object.fromEntries(model.dataTypeMapping.mappings.map((mapping) => [mapping.defKey, mapping.id]));
  reshapeChannel(entity, typeIds);
  applyOverrides(model);
  return model;
}

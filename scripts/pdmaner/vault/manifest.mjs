import {
  accessIndex,
  field,
  primaryKey,
  relationship,
  table,
  uniqueIndex,
} from "./schema-dsl.mjs";

const asciiField = (defKey, typeToken, nullability, defName, description) => field(
  defKey,
  typeToken,
  nullability,
  defName,
  description,
  { characterSet: "ascii", collation: "ascii_bin" },
);

export const TABLES = Object.freeze([
  table({
    key: "vault_record",
    name: "Vault 加密记录",
    comment: "保存权威不透明 Token、稳定值身份指纹与单记录信封加密材料，不拥有任何业务语义",
    audit: "mutable",
    fields: [
      primaryKey("record_id", "加密记录标识", "服务侧生成的 Snowflake 加密记录主键"),
      asciiField("token", "VARCHAR(64)", "NN", "不透明令牌", "区分大小写且不可顺序推导的 Vault 定位令牌"),
      asciiField("data_type", "VARCHAR(64)", "NN", "数据类型", "Vault 注册目录中的通用数据类型代码"),
      asciiField("token_mode", "VARCHAR(16)", "NN", "令牌模式", "令牌身份模式，受 CHECK 契约限制为 REFERENCE 或 STABLE"),
      field("value_fingerprint", "BINARY(32)", "N", "稳定值指纹", "服务端使用隔离 HMAC 密钥生成的稳定值身份指纹，仅 STABLE 模式存在"),
      field("ciphertext", "BLOB", "NN", "值密文", "使用每记录 AES-256-GCM 数据密钥生成且包含认证标签的密文"),
      field("encryption_nonce", "BINARY(12)", "NN", "加密随机数", "每条记录独立生成的十二字节 AES-GCM 随机数"),
      field("wrapped_dek", "BLOB", "NN", "封装数据密钥", "通过配置的 KMS 密钥封装后的每记录数据密钥密文"),
      field("kms_key_ref", "VARCHAR(512)", "NN", "KMS 密钥引用", "封装数据密钥所用的非敏感 KMS 密钥引用，不包含真实密钥材料"),
    ],
    indexes: [
      uniqueIndex("uk_vault_record_token", "token"),
      uniqueIndex("uk_vault_record_stable", "data_type", "value_fingerprint"),
    ],
  }),
  table({
    key: "vault_protect_request",
    name: "Vault 保护请求",
    comment: "保存成功 Protect 命令的调用方幂等映射，使响应丢失后的精确重试返回原始 Token",
    audit: "mutable",
    fields: [
      primaryKey("protect_request_id", "保护请求标识", "服务侧生成的 Snowflake 保护请求主键"),
      asciiField("caller_service", "VARCHAR(128)", "NN", "调用服务", "构成 Protect 幂等命名空间的内部调用服务代码"),
      asciiField("idempotency_key", "VARCHAR(128)", "NN", "幂等键", "调用方为同一逻辑 Protect 命令生成并在重试时复用的幂等键"),
      field("request_hash", "BINARY(32)", "NN", "请求哈希", "服务端独立 HMAC 密钥对规范化 Protect 请求内容生成的请求哈希"),
      field("record_id", "BIGINT", "NN", "加密记录标识", "本次成功 Protect 命令返回的本库 Vault 加密记录标识"),
    ],
    indexes: [
      uniqueIndex("uk_vault_protect_request", "caller_service", "idempotency_key"),
      accessIndex("idx_vault_protect_record", "record_id"),
    ],
    relations: [relationship("record_id", "vault_record", "record_id")],
  }),
  table({
    key: "vault_access_event",
    name: "Vault 访问事件",
    comment: "追加保存每次实际 Protect 与 Reveal 尝试的本地审计证据，禁止复制 Token、原值或密码学材料",
    audit: "immutable",
    fields: [
      primaryKey("access_event_id", "访问事件标识", "服务侧生成的 Snowflake 访问事件主键"),
      field("request_id", "VARCHAR(64)", "NN", "请求标识", "本次内部 HTTP 尝试的稳定请求追踪标识"),
      field("trace_id", "VARCHAR(64)", "N", "链路标识", "本次访问可选的分布式链路追踪标识"),
      asciiField("caller_service", "VARCHAR(128)", "NN", "调用服务", "发起本次访问的内部调用服务代码，缺失时记录 UNKNOWN"),
      asciiField("action", "VARCHAR(16)", "NN", "访问动作", "本次尝试的动作，受 CHECK 契约限制为 PROTECT 或 REVEAL"),
      asciiField("data_type", "VARCHAR(64)", "N", "数据类型", "策略或记录解析可得时对应的通用数据类型代码"),
      asciiField("purpose", "VARCHAR(64)", "N", "访问用途", "调用方请求且用于静态策略判断的稳定用途代码"),
      field("record_id", "BIGINT", "N", "加密记录标识", "已解析本库 Vault 加密记录的可选标识，早期失败或未知 Token 时为空"),
      asciiField("outcome", "VARCHAR(16)", "NN", "访问结果", "本次访问结果，受 CHECK 契约限制为 SUCCESS、DENIED 或 FAILED"),
      asciiField("result_code", "VARCHAR(64)", "NN", "结果代码", "不包含敏感信息的稳定访问结果代码"),
      asciiField("policy_version", "VARCHAR(64)", "N", "策略版本", "成功匹配静态访问策略时采用的可选策略版本"),
    ],
    indexes: [
      accessIndex("idx_vault_access_record", "record_id", "create_time"),
      accessIndex("idx_vault_access_caller", "caller_service", "create_time"),
      accessIndex("idx_vault_access_request", "request_id"),
    ],
    relations: [relationship("record_id", "vault_record", "record_id")],
  }),
]);

export const RELATIONS = Object.freeze(TABLES.flatMap((tableSpec) => tableSpec.relations));

export const EXPECTED_MODEL_TOTALS = Object.freeze({
  tables: 3,
  fields: 30,
  uniqueIndexes: 3,
  accessIndexes: 4,
  relations: 2,
  oneToOneRelations: 0,
  diagramGroups: 1,
});

export const EXPECTED_TABLE_TOTAL = EXPECTED_MODEL_TOTALS.tables;

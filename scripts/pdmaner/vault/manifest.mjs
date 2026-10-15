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
      asciiField("actor_id", "VARCHAR(64)", "N", "操作人", "运营人工加解密的运营账号标识，服务调用为空"),
      asciiField("action", "VARCHAR(16)", "NN", "访问动作", "本次尝试的动作，受 CHECK 契约限制为 PROTECT 或 REVEAL"),
      asciiField("data_type", "VARCHAR(64)", "N", "数据类型", "策略或记录解析可得时对应的通用数据类型代码"),
      asciiField("purpose", "VARCHAR(64)", "N", "访问用途", "调用方请求且用于静态策略判断的稳定用途代码"),
      field("record_id", "BIGINT", "N", "加密记录标识", "已解析本库 Vault 加密记录的可选标识，早期失败或未知 Token 时为空"),
      asciiField("outcome", "VARCHAR(16)", "NN", "访问结果", "本次访问结果，受 CHECK 契约限制为 SUCCESS、DENIED 或 FAILED"),
      asciiField("result_code", "VARCHAR(64)", "NN", "结果代码", "不包含敏感信息的稳定访问结果代码"),
      asciiField("policy_version", "VARCHAR(64)", "N", "策略版本", "授权判断时采用的数据库策略版本标识"),
      asciiField("reason_code", "VARCHAR(64)", "N", "理由码", "运营人工加解密选择的固定理由码，服务调用为空"),
      asciiField("ticket_ref", "VARCHAR(64)", "N", "工单号", "运营人工加解密关联的工单号，服务调用为空"),
    ],
    indexes: [
      accessIndex("idx_vault_access_record", "record_id", "create_time"),
      accessIndex("idx_vault_access_caller", "caller_service", "create_time"),
      accessIndex("idx_vault_access_request", "request_id"),
      accessIndex("idx_vault_access_actor", "actor_id", "create_time"),
    ],
    relations: [relationship("record_id", "vault_record", "record_id")],
  }),
  table({
    key: "vault_data_type",
    name: "Vault 数据类型",
    comment: "Vault 数据类型目录：Token 模式、数据级别与人工加解密开关，编码与 Token 模式创建后不可修改，只停用不删除",
    audit: "mutable",
    fields: [
      primaryKey("data_type_id", "数据类型标识", "服务侧生成的 Snowflake 数据类型主键"),
      asciiField("data_type_code", "VARCHAR(64)", "NN", "数据类型编码", "全局唯一的通用数据类型编码，创建后不可修改"),
      asciiField("token_mode", "VARCHAR(16)", "NN", "令牌模式", "该数据类型的令牌身份模式 REFERENCE 或 STABLE，创建后不可修改"),
      asciiField("data_class", "VARCHAR(16)", "NN", "数据级别", "数据级别 STANDARD 普通敏感或 SECRET 密钥，决定人工操作所需权限"),
      field("description", "VARCHAR(256)", "NN", "用途说明", "供运营理解该数据类型用途的中文说明，不含受保护值"),
      field("manual_protect_enabled", "TINYINT(1)", "NN", "允许人工加密", "是否允许运营在控制台人工加密该类型的值"),
      field("manual_reveal_enabled", "TINYINT(1)", "NN", "允许人工解密", "是否允许运营在控制台人工解密该类型的值"),
      asciiField("status", "VARCHAR(16)", "NN", "状态", "数据类型状态 ACTIVE 或 DISABLED，只停用不删除"),
      field("version", "INT", "NN", "乐观锁版本", "每次修改递增的乐观锁版本号"),
    ],
    indexes: [
      uniqueIndex("uk_vault_data_type_code", "data_type_code"),
    ],
  }),
  table({
    key: "vault_access_rule",
    name: "Vault 访问规则",
    comment: "服务访问规则：调用服务、动作、数据类型与访问用途的允许组合，只停用不删除",
    audit: "mutable",
    fields: [
      primaryKey("access_rule_id", "访问规则标识", "服务侧生成的 Snowflake 访问规则主键"),
      asciiField("caller_service", "VARCHAR(128)", "NN", "调用服务", "被授权的内部调用服务代码"),
      asciiField("action", "VARCHAR(16)", "NN", "访问动作", "被授权的动作 PROTECT 或 REVEAL"),
      asciiField("data_type_code", "VARCHAR(64)", "NN", "数据类型编码", "被授权的数据类型编码，由应用校验其存在"),
      asciiField("purpose_code", "VARCHAR(64)", "NN", "访问用途", "被授权的访问用途编码，OPERATIONS_MANUAL 为人工操作保留值"),
      asciiField("status", "VARCHAR(16)", "NN", "状态", "访问规则状态 ACTIVE 或 DISABLED，只停用不删除"),
      field("version", "INT", "NN", "乐观锁版本", "每次修改递增的乐观锁版本号"),
    ],
    indexes: [
      uniqueIndex("uk_vault_access_rule", "caller_service", "action", "data_type_code", "purpose_code"),
      accessIndex("idx_vault_access_rule_type", "data_type_code"),
    ],
  }),
  table({
    key: "vault_policy_change",
    name: "Vault 策略变更",
    comment: "追加保存每次数据类型或访问规则变更，每条记录对应一个单调递增的策略版本，快照不含任何受保护值",
    audit: "immutable",
    fields: [
      primaryKey("policy_change_id", "策略变更标识", "服务侧生成的 Snowflake 策略变更主键"),
      field("policy_version_no", "INT", "NN", "策略版本号", "本次变更后的策略版本号，单调递增"),
      asciiField("change_type", "VARCHAR(32)", "NN", "变更类型", "变更类型 SEED、DATA_TYPE_CREATE、DATA_TYPE_UPDATE、RULE_CREATE 或 RULE_UPDATE"),
      asciiField("target_type", "VARCHAR(16)", "NN", "对象类型", "变更对象类型 POLICY、DATA_TYPE 或 ACCESS_RULE"),
      asciiField("target_key", "VARCHAR(256)", "NN", "对象标识", "变更对象标识：数据类型编码或访问规则四元组"),
      field("before_snapshot", "JSON", "N", "变更前快照", "变更前的对象元数据，新建时为空，不含任何受保护值"),
      field("after_snapshot", "JSON", "NN", "变更后快照", "变更后的对象元数据，不含任何受保护值"),
      field("reason", "VARCHAR(500)", "NN", "变更理由", "操作人填写的变更理由"),
      asciiField("actor_id", "VARCHAR(64)", "NN", "操作人", "发起变更的运营账号标识，初始化数据为 SYSTEM"),
      field("request_id", "VARCHAR(64)", "NN", "请求标识", "发起变更的内部 HTTP 请求追踪标识"),
    ],
    indexes: [
      uniqueIndex("uk_vault_policy_version", "policy_version_no"),
      accessIndex("idx_vault_policy_change_target", "target_type", "target_key", "create_time"),
    ],
  }),
]);

export const RELATIONS = Object.freeze(TABLES.flatMap((tableSpec) => tableSpec.relations));

export const EXPECTED_MODEL_TOTALS = Object.freeze({
  tables: 6,
  fields: 64,
  uniqueIndexes: 6,
  accessIndexes: 7,
  relations: 2,
  oneToOneRelations: 0,
  diagramGroups: 1,
});

export const EXPECTED_TABLE_TOTAL = EXPECTED_MODEL_TOTALS.tables;

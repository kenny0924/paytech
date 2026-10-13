// 2026-10-13 外部 API 配置与执行六表；物理设计见 requirements/20260914/platform-support-外部API配置与执行/physical-design.md。
// 只保存当前配置，不保存流程实例、节点记录、运行上下文、报文、调用方白名单或发布版本。
import { accessIndex, defineTable, field, relation, uniqueIndex } from "../schema-dsl.mjs";

const ASCII_BIN = { characterSet: "ascii", collation: "ascii_bin" };
const UTF8_BIN = { characterSet: "utf8mb4", collation: "utf8mb4_bin" };

function configCode(defKey, defName, description, source) {
  return field(defKey, "VARCHAR(64)", "NN", defName, description, { source, ...ASCII_BIN });
}

function configName(defKey, defName) {
  return field(defKey, "VARCHAR(128)", "NN", defName, `${defName}，供 Operations 维护页面展示`);
}

const DESCRIPTION = field("description", "VARCHAR(512)", "N", "说明", "业务用途说明；未填写时为空");
const VERSION = field("version", "INT", "NN", "并发版本", "Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本");
const CREATED_BY = field("created_by", "BIGINT", "NN", "创建账号", "创建该配置的 Operations 账号标识");
const UPDATED_BY = field("updated_by", "BIGINT", "NN", "修改账号", "最后一次修改该配置的 Operations 账号标识");

export const TABLES = Object.freeze([
  defineTable({
    key: "standard_api",
    name: "标准 API",
    comment: "Platform 对内提供的标准 API 定义，例如 PAY、AUTHORIZE；请求与响应字段由 t_api_field 维护，不保存调用方白名单",
    fields: [
      field("standard_api_id", "BIGINT", "NN", "标准 API 主键", "标准 API 的内部技术标识", { primaryKey: true }),
      configCode("standard_api_code", "标准 API 编码", "业务系统调用内部接口时使用的标准 API 编码，大小写敏感，格式为大写字母开头的字母、数字与下划线", "Platform 标准 API 目录"),
      configName("standard_api_name", "标准 API 名称"),
      DESCRIPTION,
      VERSION,
      CREATED_BY,
      UPDATED_BY,
    ],
    indexes: [
      uniqueIndex("uk_standard_api_code", "standard_api_code"),
    ],
  }),
  defineTable({
    key: "api_field",
    name: "API 字段",
    comment: "标准 API 与渠道原子 API 共用的请求、响应字段定义；属于所属 API 的配置，随所属 API 整组替换，不单独记录版本与操作人",
    fields: [
      field("api_field_id", "BIGINT", "NN", "字段主键", "字段定义的内部技术标识", { primaryKey: true }),
      field("api_id", "BIGINT", "NN", "所属 API", "所属 API 标识：方向为 STANDARD_API_* 时指向 t_standard_api.standard_api_id，为 INTEGRATION_API_* 时指向 t_integration_api.integration_api_id；由应用校验"),
      field("direction", "VARCHAR(32)", "NN", "字段方向", "字段所属的 API 类型与请求或响应方向", { values: ["STANDARD_API_REQUEST", "STANDARD_API_RESPONSE", "INTEGRATION_API_REQUEST", "INTEGRATION_API_RESPONSE"], ...ASCII_BIN }),
      field("field_location", "VARCHAR(16)", "NN", "字段位置", "字段在 HTTP 报文中的位置；请求允许四种，响应只允许 HEADER、BODY", { values: ["HEADER", "BODY", "QUERY", "PATH"], ...ASCII_BIN }),
      field("field_path", "VARCHAR(512)", "NN", "字段路径", "BODY 字段的完整点分路径，数组元素用 [] 表示，最多 16 段、每段 1 至 64 字符；HEADER、QUERY、PATH 为参数名，HEADER 名称大小写不敏感判重由应用完成", UTF8_BIN),
      field("field_name", "VARCHAR(128)", "NN", "显示名称", "字段在 Operations 页面上的中文或业务显示名称"),
      field("data_type", "VARCHAR(16)", "NN", "数据类型", "字段值的数据类型；金额与精确小数以 STRING 传递", { values: ["STRING", "BOOLEAN", "INTEGER", "DECIMAL", "OBJECT", "ARRAY"], ...ASCII_BIN }),
      field("required_flag", "BOOLEAN", "NN", "是否必填", "1 表示必填，0 表示非必填；子字段只在父对象存在时校验，响应字段只在执行状态为 SUCCESS 时校验"),
      field("sort_no", "INT", "NN", "排序号", "同一 API、同一方向内的显示顺序，从小到大"),
      DESCRIPTION,
    ],
    indexes: [
      uniqueIndex("uk_api_field_path", "api_id", "direction", "field_location", "field_path"),
    ],
  }),
  defineTable({
    key: "integration",
    name: "外部接入方",
    comment: "渠道或其他出站外部接入方及本环境出站地址；与 Client Integration Readiness（PLT-I13）无关",
    fields: [
      field("integration_id", "BIGINT", "NN", "接入方主键", "外部接入方的内部技术标识", { primaryKey: true }),
      configCode("integration_code", "接入方编码", "业务系统调用标准 API 时传入的接入方编码，大小写敏感，不等同于 Payment Channel 编码", "Platform 外部接入方目录"),
      configName("integration_name", "接入方名称"),
      DESCRIPTION,
      field("endpoints", "JSON", "NN", "出站地址", "本环境出站地址数组 [{endpointCode, baseUrl}]，1 至 16 项；endpointCode 在接入方内唯一，baseUrl 仅允许 https、不含用户信息、查询串与片段，最长 512 字符；实际出站仍受部署网络白名单限制"),
      VERSION,
      CREATED_BY,
      UPDATED_BY,
    ],
    indexes: [
      uniqueIndex("uk_integration_code", "integration_code"),
    ],
  }),
  defineTable({
    key: "integration_api",
    name: "渠道原子 API",
    comment: "接入方的单次外部协议操作；请求与响应结构由 t_api_field 维护，传输、编解码、脚本、状态规则与输出映射保存在 definition_json",
    fields: [
      field("integration_api_id", "BIGINT", "NN", "原子 API 主键", "渠道原子 API 的内部技术标识", { primaryKey: true }),
      field("integration_id", "BIGINT", "NN", "所属接入方", "所属外部接入方标识"),
      configCode("api_code", "API 编码", "接入方内唯一的原子 API 编码，流程节点按此引用，大小写敏感", "所属接入方的原子 API 目录"),
      configName("api_name", "API 名称"),
      DESCRIPTION,
      field("definition_json", "JSON", "NN", "执行定义", "原子 API 执行定义：调用参数、协议固定值、传输、编解码、脚本引用、状态规则与按 SUCCESS/FAIL 分支的输出映射；序列化后最多 65536 UTF-8 字节，禁止保存凭据明文"),
      VERSION,
      CREATED_BY,
      UPDATED_BY,
    ],
    indexes: [
      uniqueIndex("uk_integration_api_code", "integration_id", "api_code"),
    ],
    relations: [
      relation("integration_id", "integration", "integration_id"),
    ],
  }),
  defineTable({
    key: "integration_script",
    name: "公共脚本",
    comment: "接入方内可复用的请求或响应阶段脚本；复杂签名、加密由代码注册函数提供，脚本不读取凭据明文",
    fields: [
      field("integration_script_id", "BIGINT", "NN", "脚本主键", "公共脚本的内部技术标识", { primaryKey: true }),
      field("integration_id", "BIGINT", "NN", "所属接入方", "所属外部接入方标识"),
      configCode("script_code", "脚本编码", "接入方内唯一的脚本编码，原子 API 按此引用，大小写敏感", "所属接入方的公共脚本目录"),
      configName("script_name", "脚本名称"),
      field("execution_stage", "VARCHAR(16)", "NN", "执行阶段", "脚本运行的 HTTP 处理阶段；REQUEST 读写请求工作对象，RESPONSE 读写响应工作副本", { values: ["REQUEST", "RESPONSE"], ...ASCII_BIN }),
      field("parameter_definition", "JSON", "NN", "参数声明", "脚本参数声明数组 [{name, dataType, required}]，最多 32 项，无参数时为空数组"),
      field("source_code", "TEXT", "NN", "脚本源码", "受限 Aviator 脚本源码，最多 32 KiB UTF-8；引擎固定，不另设引擎字段"),
      DESCRIPTION,
      VERSION,
      CREATED_BY,
      UPDATED_BY,
    ],
    indexes: [
      uniqueIndex("uk_integration_script_code", "integration_id", "script_code"),
    ],
    relations: [
      relation("integration_id", "integration", "integration_id"),
    ],
  }),
  defineTable({
    key: "integration_standard_api",
    name: "渠道标准 API 实现",
    comment: "接入方对某个标准 API 的当前实现；flow_definition_json 保存初始化、原子 API 节点、条件连线与返回出口，不保存流程实例或运行状态",
    fields: [
      field("integration_standard_api_id", "BIGINT", "NN", "实现主键", "渠道标准 API 实现的内部技术标识", { primaryKey: true }),
      field("integration_id", "BIGINT", "NN", "接入方", "实现该标准 API 的外部接入方标识"),
      field("standard_api_id", "BIGINT", "NN", "标准 API", "被实现的标准 API 标识"),
      field("flow_definition_json", "JSON", "NN", "流程定义", "流程定义：总预算、初始化项、入口、原子 API 节点、返回出口与条件连线；无环，序列化后最多 65536 UTF-8 字节"),
      VERSION,
      CREATED_BY,
      UPDATED_BY,
    ],
    indexes: [
      uniqueIndex("uk_integration_standard_api", "integration_id", "standard_api_id"),
      accessIndex("idx_integ_std_api_standard", "standard_api_id"),
    ],
    relations: [
      relation("integration_id", "integration", "integration_id"),
      relation("standard_api_id", "standard_api", "standard_api_id"),
    ],
  }),
]);

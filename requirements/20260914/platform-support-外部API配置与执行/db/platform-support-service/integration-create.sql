-- 外部 API 配置与执行六表：Platform Support 增量建表 SQL（2026-10-13）
-- 目标库：Platform Support 业务库（与 platform-support-schema.sql 同库）；只用于尚不存在这六张表的库。
-- 设计来源：platform-support-service/docs/database/platform-support.pdma.json（图组 integration）；
--   物理设计：requirements/20260914/platform-support-外部API配置与执行/physical-design.md。
-- 文件性质：已评审的待执行增量，尚未在任何环境执行；执行需单独授权，应用启动不会执行。
-- 执行顺序：六条 CREATE TABLE 互不依赖，按文件顺序执行即可；不含数据初始化。
-- 执行前核对：SHOW TABLES LIKE 't_standard_api' 等六张表均不存在。
-- 执行后核对：SHOW CREATE TABLE 逐表对照 PDManer 字段、类型、索引与注释。
-- 回滚：仅在六张表均为空时，由执行人按相反顺序手工删除这六张表；本文件不包含删除语句。
-- 不创建 CHECK 或 FOREIGN KEY；关联、枚举与引用保护由 Platform 应用代码校验。
SET NAMES utf8mb4;

CREATE TABLE `t_standard_api` (
  `standard_api_id` BIGINT NOT NULL COMMENT '标准 API 的内部技术标识。',
  `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务系统调用内部接口时使用的标准 API 编码，大小写敏感，格式为大写字母开头的字母、数字与下划线。取值来源：Platform 标准 API 目录；非固定枚举。',
  `standard_api_name` VARCHAR(128) NOT NULL COMMENT '标准 API 名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`standard_api_id`),
  UNIQUE KEY `uk_standard_api_code` (`standard_api_code`) COMMENT '保证业务唯一性：standard_api_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Platform 对内提供的标准 API 定义，例如 PAY、AUTHORIZE；请求与响应字段由 t_api_field 维护，不保存调用方白名单';

CREATE TABLE `t_api_field` (
  `api_field_id` BIGINT NOT NULL COMMENT '字段定义的内部技术标识。',
  `api_id` BIGINT NOT NULL COMMENT '所属 API 标识：方向为 STANDARD_API_* 时指向 t_standard_api.standard_api_id，为 INTEGRATION_API_* 时指向 t_integration_api.integration_api_id；由应用校验。',
  `direction` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段所属的 API 类型与请求或响应方向。允许值：STANDARD_API_REQUEST、STANDARD_API_RESPONSE、INTEGRATION_API_REQUEST、INTEGRATION_API_RESPONSE。',
  `field_location` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段在 HTTP 报文中的位置；请求允许四种，响应只允许 HEADER、BODY。允许值：HEADER、BODY、QUERY、PATH。',
  `field_path` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'BODY 字段的完整点分路径，数组元素用 [] 表示，最多 16 段、每段 1 至 64 字符；HEADER、QUERY、PATH 为参数名，HEADER 名称大小写不敏感判重由应用完成。',
  `field_name` VARCHAR(128) NOT NULL COMMENT '字段在 Operations 页面上的中文或业务显示名称。',
  `data_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段值的数据类型；金额与精确小数以 STRING 传递。允许值：STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY。',
  `required_flag` BOOLEAN NOT NULL COMMENT '1 表示必填，0 表示非必填；子字段只在父对象存在时校验，响应字段只在执行状态为 SUCCESS 时校验。',
  `sort_no` INT NOT NULL COMMENT '同一 API、同一方向内的显示顺序，从小到大。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`api_field_id`),
  UNIQUE KEY `uk_api_field_path` (`api_id`, `direction`, `field_location`, `field_path`) COMMENT '保证业务唯一性：api_id、direction、field_location、field_path'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='标准 API 与渠道原子 API 共用的请求、响应字段定义；属于所属 API 的配置，随所属 API 整组替换，不单独记录版本与操作人';

CREATE TABLE `t_integration` (
  `integration_id` BIGINT NOT NULL COMMENT '外部接入方的内部技术标识。',
  `integration_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务系统调用标准 API 时传入的接入方编码，大小写敏感，不等同于 Payment Channel 编码。取值来源：Platform 外部接入方目录；非固定枚举。',
  `integration_name` VARCHAR(128) NOT NULL COMMENT '接入方名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `endpoints` JSON NOT NULL COMMENT '本环境出站地址数组 [{endpointCode, baseUrl}]，1 至 16 项；endpointCode 在接入方内唯一，baseUrl 仅允许 https、不含用户信息、查询串与片段，最长 512 字符；实际出站仍受部署网络白名单限制。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_id`),
  UNIQUE KEY `uk_integration_code` (`integration_code`) COMMENT '保证业务唯一性：integration_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道或其他出站外部接入方及本环境出站地址；与 Client Integration Readiness（PLT-I13）无关';

CREATE TABLE `t_integration_api` (
  `integration_api_id` BIGINT NOT NULL COMMENT '渠道原子 API 的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的原子 API 编码，流程节点按此引用，大小写敏感。取值来源：所属接入方的原子 API 目录；非固定枚举。',
  `api_name` VARCHAR(128) NOT NULL COMMENT 'API 名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `definition_json` JSON NOT NULL COMMENT '原子 API 执行定义：调用参数、协议固定值、传输、编解码、脚本引用、状态规则与按 SUCCESS/FAIL 分支的输出映射；序列化后最多 65536 UTF-8 字节，禁止保存凭据明文。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_api_id`),
  UNIQUE KEY `uk_integration_api_code` (`integration_id`, `api_code`) COMMENT '保证业务唯一性：integration_id、api_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方的单次外部协议操作；请求与响应结构由 t_api_field 维护，传输、编解码、脚本、状态规则与输出映射保存在 definition_json';

CREATE TABLE `t_integration_script` (
  `integration_script_id` BIGINT NOT NULL COMMENT '公共脚本的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `script_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的脚本编码，原子 API 按此引用，大小写敏感。取值来源：所属接入方的公共脚本目录；非固定枚举。',
  `script_name` VARCHAR(128) NOT NULL COMMENT '脚本名称，供 Operations 维护页面展示。',
  `execution_stage` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '脚本运行的 HTTP 处理阶段；REQUEST 读写请求工作对象，RESPONSE 读写响应工作副本。允许值：REQUEST、RESPONSE。',
  `parameter_definition` JSON NOT NULL COMMENT '脚本参数声明数组 [{name, dataType, required}]，最多 32 项，无参数时为空数组。',
  `source_code` TEXT NOT NULL COMMENT '受限 Aviator 脚本源码，最多 32 KiB UTF-8；引擎固定，不另设引擎字段。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_script_id`),
  UNIQUE KEY `uk_integration_script_code` (`integration_id`, `script_code`) COMMENT '保证业务唯一性：integration_id、script_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方内可复用的请求或响应阶段脚本；复杂签名、加密由代码注册函数提供，脚本不读取凭据明文';

CREATE TABLE `t_integration_standard_api` (
  `integration_standard_api_id` BIGINT NOT NULL COMMENT '渠道标准 API 实现的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '实现该标准 API 的外部接入方标识。',
  `standard_api_id` BIGINT NOT NULL COMMENT '被实现的标准 API 标识。',
  `flow_definition_json` JSON NOT NULL COMMENT '流程定义：总预算、初始化项、入口、原子 API 节点、返回出口与条件连线；无环，序列化后最多 65536 UTF-8 字节。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_standard_api_id`),
  UNIQUE KEY `uk_integration_standard_api` (`integration_id`, `standard_api_id`) COMMENT '保证业务唯一性：integration_id、standard_api_id',
  KEY `idx_integ_std_api_standard` (`standard_api_id`) COMMENT '支持访问路径：standard_api_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方对某个标准 API 的当前实现；flow_definition_json 保存初始化、原子 API 节点、条件连线与返回出口，不保存流程实例或运行状态';

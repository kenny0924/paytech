-- channel-connector：渠道协议模块的 10 张表自 Platform Support 迁入，并扩展变更留痕表（需求 requirements/20261020/channel-connector-统一对外渠道出口，
-- 技术方案第 3、4 节，第一段）。审阅后由 root 手工执行。
-- 目标库：mor_channel_connector。前提：本库已有 18 张表（含 2026-10-20 t_channel_config_change），尚无下列 10 张协议表。
-- 影响：CREATE 10 张（t_standard_api、t_api_field、t_integration、t_integration_api、t_integration_script、t_integration_standard_api、
--   t_integration_verify_account、t_integration_verify_case、t_integration_verify_run、t_integration_verify_run_step，结构自
--   platform-support-schema.sql 原样复制）；t_channel_config_change 在 reason 之后增加 change_detail 列，target_type 与 change_type
--   的注释扩展允许值，并更新表注释；不改既有数据。执行后为 28 张表，与 channel-connector-schema.sql、channel-connector.pdma.json 逐列一致。
-- 数据：本文件只建结构，不导入数据。10 张表的数据按技术方案第 3.4 节另行用 mysqldump --no-create-info 自 mor_platform 导出后导入，
--   导入后逐表核对行数与 CHECKSUM TABLE。
-- 可重跑性：不可重跑（列或表已存在时报错）；部分执行失败时先对照本文件确认已完成的语句，再从失败处继续。
-- 回退（会丢失 10 张表中已导入或新写入的数据与 change_detail 内容，回退前先确认可以丢弃）：
--   DROP TABLE t_integration_verify_run_step, t_integration_verify_run, t_integration_verify_case, t_integration_verify_account,
--     t_integration_standard_api, t_integration_script, t_integration_api, t_integration, t_api_field, t_standard_api;
--   ALTER TABLE t_channel_config_change DROP COLUMN change_detail,
--     MODIFY COLUMN target_type VARCHAR(64) NOT NULL COMMENT '被变更对象类型。允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION。',
--     MODIFY COLUMN change_type VARCHAR(64) NOT NULL COMMENT '变更类型。允许值：CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL。',
--     COMMENT='渠道配置与成本版本的人工变更留痕；与业务变更同事务写入，只增不改。';
--   （回退 change_detail 前先确认已无 STANDARD_API 等新类型的记录，或一并删除这些记录。）
SET NAMES utf8mb4;

ALTER TABLE `t_channel_config_change`
  ADD COLUMN `change_detail` JSON NULL COMMENT '外部 API 配置变更的字段摘要：{objectCode, beforeVersion, afterVersion, changes[{field, before, after}]}，字段取值只记名称、编码与 SHA-256 摘要，不含定义原文、参数或凭据值；渠道、账号、MID、能力与成本版本的变更为空。' AFTER `reason`,
  MODIFY COLUMN `target_type` VARCHAR(64) NOT NULL COMMENT '被变更对象类型。允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION、STANDARD_API、INTEGRATION、INTEGRATION_API、INTEGRATION_SCRIPT、INTEGRATION_IMPLEMENTATION、VERIFY_ACCOUNT、VERIFY_CASE。',
  MODIFY COLUMN `change_type` VARCHAR(64) NOT NULL COMMENT '变更类型。允许值：CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL、DELETE。',
  COMMENT='渠道配置、成本版本与外部 API 配置的人工变更留痕；与业务变更同事务写入，只增不改。';

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
  `endpoints` JSON NOT NULL COMMENT '本环境出站地址数组 [{endpointCode, baseUrl, sandbox}]，1 至 16 项；endpointCode 在接入方内唯一，baseUrl 仅允许 https、不含用户信息、查询串与片段，最长 512 字符；sandbox 可选、缺省为 false，只有为 true 的地址允许沙箱验证运行；实际出站仍受部署网络白名单限制。',
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

CREATE TABLE `t_integration_verify_account` (
  `verify_account_id` BIGINT NOT NULL COMMENT '验证账号的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的验证账号编码，验证用例按此引用，大小写敏感。取值来源：所属接入方的验证账号目录；非固定枚举。',
  `account_name` VARCHAR(128) NOT NULL COMMENT '账号名称，供 Operations 维护页面展示。',
  `channel_code` VARCHAR(64) NOT NULL COMMENT '账号所属渠道编码，流程初始化时与输入中的账号身份核对。',
  `channel_account_id` VARCHAR(64) NOT NULL COMMENT '流程初始化时与输入中的账号身份核对的渠道账号标识。',
  `entity_no` VARCHAR(64) NOT NULL COMMENT '流程初始化时与输入中的账号身份核对的经营主体编号。',
  `parameters` JSON NOT NULL COMMENT '普通账号参数，字符串键值，最多 64 项、每个值最多 8192 字符。',
  `credentials` JSON NOT NULL COMMENT '沙箱凭据，字符串键值，最多 64 项、每个值最多 8192 字符；执行时包装为受控句柄，只能交给注册函数。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_account_id`),
  UNIQUE KEY `uk_verify_account_code` (`integration_id`, `account_code`) COMMENT '保证业务唯一性：integration_id、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='沙箱验证运行使用的渠道账号材料；按用户决定以明文保存非敏感沙箱材料，不写入 Vault，正式执行不读取';

CREATE TABLE `t_integration_verify_case` (
  `verify_case_id` BIGINT NOT NULL COMMENT '验证用例的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `case_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的验证用例编码，大小写敏感。取值来源：所属接入方的验证用例目录；非固定枚举。',
  `case_name` VARCHAR(128) NOT NULL COMMENT '用例名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `verify_account_id` BIGINT NOT NULL COMMENT '运行时使用的验证账号标识。',
  `steps_json` JSON NOT NULL COMMENT '有序步骤数组，1 至 10 步；每步含步骤标识、标准 API 编码、输入模板与期望，序列化后最多 65536 UTF-8 字节。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_case_id`),
  UNIQUE KEY `uk_verify_case_code` (`integration_id`, `case_code`) COMMENT '保证业务唯一性：integration_id、case_code',
  KEY `idx_verify_case_account` (`verify_account_id`) COMMENT '支持访问路径：verify_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方的沙箱验证用例：使用的验证账号与有序步骤，每步包含标准 API、输入模板与期望';

CREATE TABLE `t_integration_verify_run` (
  `verify_run_id` BIGINT NOT NULL COMMENT '验证运行的内部技术标识，以十进制字符串作为对外运行编号。',
  `verify_case_id` BIGINT NOT NULL COMMENT '被运行的验证用例标识；用例删除后保留原值。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `run_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行结论；FAILED 表示期望不满足或步骤出错，INTERRUPTED 表示执行进程中断后不再向沙箱重放。允许值：RUNNING、PASSED、FAILED、INTERRUPTED。',
  `case_snapshot_json` JSON NOT NULL COMMENT '发起运行时的用例步骤、期望、账号编码与账号版本，不含凭据。',
  `config_versions_json` JSON NULL COMMENT '运行实际加载的标准 API、接入方、原子 API、脚本与实现版本，按编码记录；运行未加载配置时为空。',
  `endpoints_json` JSON NULL COMMENT '运行时接入方出站地址及沙箱标记；运行未加载配置时为空。',
  `failure_summary` VARCHAR(512) NULL COMMENT '未通过时的首个原因；通过或运行中为空。',
  `started_by` BIGINT NOT NULL COMMENT '发起运行的 Operations 账号标识。',
  `start_time` DATETIME(6) NOT NULL COMMENT '发起运行的 UTC 微秒时间。',
  `finish_time` DATETIME(6) NULL COMMENT '运行得出结论的 UTC 微秒时间；运行中为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_run_id`),
  KEY `idx_verify_run_case` (`verify_case_id`, `start_time`) COMMENT '支持访问路径：verify_case_id、start_time',
  KEY `idx_verify_run_integration` (`integration_id`, `start_time`) COMMENT '支持访问路径：integration_id、start_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次沙箱验证运行的结论与证据：运行时的用例快照、配置版本与出站地址；记录不删除，用例删除后仍保留';

CREATE TABLE `t_integration_verify_run_step` (
  `verify_run_step_id` BIGINT NOT NULL COMMENT '运行步骤记录的内部技术标识。',
  `verify_run_id` BIGINT NOT NULL COMMENT '所属验证运行标识。',
  `step_no` INT NOT NULL COMMENT '步骤在用例中的顺序，从 1 开始。',
  `step_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用例内的步骤标识，供后续步骤模板引用。',
  `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该步骤执行的标准 API 编码。',
  `step_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '步骤结论；SKIPPED 表示前序步骤未通过而未执行。允许值：PASSED、FAILED、SKIPPED。',
  `resolved_input_json` JSON NULL COMMENT '模板渲染后的标准 API 输入；未执行或渲染失败时为空。',
  `result_json` JSON NULL COMMENT '执行结果：已执行时为执行状态与输出，执行前返回问题时为 HTTP 状态与错误码；未执行时为空。',
  `assertions_json` JSON NULL COMMENT '每条期望的实际值与是否满足；未执行时为空。',
  `trace_json` JSON NULL COMMENT '初始化项、节点、连线与出口轨迹及脱敏后的渠道请求与响应原文，单段报文最多保留 65536 字节；未执行时为空。',
  `elapsed_ms` INT NULL COMMENT '步骤执行耗时，单位毫秒；未执行时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`verify_run_step_id`),
  UNIQUE KEY `uk_verify_run_step` (`verify_run_id`, `step_no`) COMMENT '保证业务唯一性：verify_run_id、step_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='验证运行中一个步骤的实际输入、结果、期望核对与脱敏轨迹；运行结束时一次写入，不再修改';

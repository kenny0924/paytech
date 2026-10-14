-- 渠道沙箱验证：Platform Support 增量 SQL（2026-10-14）
-- 目标库：Platform Support 业务库（与 platform-support-schema.sql 同库）；前置条件：外部 API 六表已存在（integration-create.sql 已执行）。
-- 设计来源：platform-support-service/docs/database/platform-support.pdma.json（图组 integration）；
--   技术方案：requirements/20261014/platform-support-渠道沙箱验证/technique.md 第 3 节。
-- 文件性质：已评审的待执行增量，尚未在任何环境执行；执行需单独授权，应用启动不会执行。
-- 执行顺序：先 ALTER（只改 t_integration.endpoints 列注释，不改类型与数据），再按文件顺序四条 CREATE TABLE；不含数据初始化。
-- 执行前核对：SHOW TABLES LIKE 't_integration_verify%' 为空；t_integration 存在。
-- 执行后核对：SHOW CREATE TABLE 逐表对照 PDManer 字段、类型、索引与注释。
-- 回滚：四张表均为空时，由执行人按相反顺序手工删除；endpoints 列注释可保留（无数据影响）。本文件不包含删除语句。
-- 不创建 CHECK 或 FOREIGN KEY；关联、枚举与引用保护由 Platform 应用代码校验。
SET NAMES utf8mb4;

ALTER TABLE `t_integration` MODIFY COLUMN `endpoints` JSON NOT NULL COMMENT '本环境出站地址数组 [{endpointCode, baseUrl, sandbox}]，1 至 16 项；endpointCode 在接入方内唯一，baseUrl 仅允许 https、不含用户信息、查询串与片段，最长 512 字符；sandbox 可选、缺省为 false，只有为 true 的地址允许沙箱验证运行；实际出站仍受部署网络白名单限制。';

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

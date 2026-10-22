-- 数据查询：新增 t_data_source、t_query_template、t_query_template_version 三张表（待执行，需授权）。
-- 需求：requirements/20261022/platform-support-数据查询服务/technique.md 第 6 节。
-- 目标库：Platform Support（mor_platform / mor_platform_e2e）。只新增表，不修改已有表或数据。
-- 预检：SELECT COUNT(*) FROM information_schema.TABLES
--         WHERE TABLE_SCHEMA = DATABASE()
--           AND TABLE_NAME IN ('t_data_source', 't_query_template', 't_query_template_version');
--       结果须为 0；为 3 说明已执行，跳过本文件；为 1 或 2 时先人工核对已有表结构，不要直接重跑。
-- 执行顺序：先执行本文件，再部署包含 data-query 模块的 Platform；旧 Platform 不读写这三张表，可先建表后发布。
--          之后再执行初始化 DML 中数据查询的注册块（权限、路由、菜单，块号执行前按 live mor_platform 选定）。
-- 执行后核对：SHOW CREATE TABLE t_data_source; SHOW CREATE TABLE t_query_template;
--             SHOW CREATE TABLE t_query_template_version; 的字段、索引与表/字段注释须与
--             platform-support-schema.sql 和 platform-support.pdma.json（图组 data_query）一致。
-- 回滚：先回退 Platform（或关闭 data-query 入口），确认三张表中没有需要保留的数据源与模板后执行
--       DROP TABLE IF EXISTS `t_query_template_version`;
--       DROP TABLE IF EXISTS `t_query_template`;
--       DROP TABLE IF EXISTS `t_data_source`;
--       （数据源密码在 Vault 中的 REFERENCE 记录不随表删除，可保留；它们不再被任何业务行引用。）
SET NAMES utf8mb4;

CREATE TABLE `t_data_source` (
  `data_source_id` BIGINT NOT NULL COMMENT '服务侧生成的数据源 Snowflake 主键。',
  `data_source_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的数据源编码，创建后不可修改，删除后也不复用；格式为大写字母开头的大写字母、数字与下划线。',
  `data_source_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的数据源名称。',
  `engine_type` VARCHAR(32) NOT NULL COMMENT '被查库的引擎类型，决定执行引擎与 SQL 方言。允许值：MYSQL、DORIS。',
  `host` VARCHAR(255) NOT NULL COMMENT '被查库的主机名或 IP；不保存原始 JDBC URL。',
  `port` INT NOT NULL COMMENT '被查库端口，取值 1 至 65535。',
  `database_name` VARCHAR(128) NULL COMMENT '连接后的默认库；NULL 表示连接整个实例，模板中的表必须写成 库.表。',
  `username` VARCHAR(128) NOT NULL COMMENT '被查库的只读账号；启用前检查其授权只含只读权限。',
  `password_token` VARCHAR(255) NOT NULL COMMENT '账号密码的 Vault REFERENCE Token；数据类型 DATA_SOURCE_PASSWORD_V1，保存用途 DATA_SOURCE_CONFIGURATION，建立连接时以用途 DATA_SOURCE_CONNECTION 读取；不保存密码原文。',
  `connection_params_json` JSON NULL COMMENT '驱动参数 JSON 对象，键只允许 sslMode、connectTimeout、socketTimeout、connectionTimeZone、characterEncoding，值为字符串；NULL 表示不设置。安全相关参数由服务固定，不在此保存。',
  `max_pool_size` INT NOT NULL COMMENT '该数据源连接池的最大连接数，取值 1 至 data-query.limits.max-pool-size。',
  `status` VARCHAR(32) NOT NULL COMMENT '数据源状态；只有 ACTIVE 可被查询，DELETED 为软删除，保留该行供模板历史版本显示原数据源。允许值：INACTIVE、ACTIVE、DELETED。',
  `last_check_result` VARCHAR(32) NULL COMMENT '最后一次连接检查的结果；NULL 表示从未检查。允许值：PASSED、FAILED。',
  `last_check_message` VARCHAR(512) NULL COMMENT '最后一次连接检查的说明，未通过时列出具体原因；NULL 表示从未检查。',
  `last_check_time` DATETIME(6) NULL COMMENT '最后一次连接检查的 UTC 微秒时间；NULL 表示从未检查。',
  `description` VARCHAR(512) NULL COMMENT '数据源用途等补充说明。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '数据源乐观并发版本，从 1 开始，每次配置或状态变更加一；各实例按它判断是否重建连接池。',
  `created_by` BIGINT NOT NULL COMMENT '创建该数据源的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该数据源的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`data_source_id`),
  UNIQUE KEY `uk_data_source_code` (`data_source_code`) COMMENT '保证业务唯一性：data_source_code',
  KEY `idx_data_source_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存数据查询可读取的外部只读数据库连接配置、密码 Vault Token、状态与最后一次连接检查结果；修改后各实例按版本重建连接池。';

CREATE TABLE `t_query_template` (
  `template_id` BIGINT NOT NULL COMMENT '服务侧生成的查询模板 Snowflake 主键。',
  `template_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的模板编码，创建后不可修改；执行接口按它定位模板。格式为大写字母开头的大写字母、数字与下划线。',
  `template_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的模板名称。',
  `description` VARCHAR(512) NULL COMMENT '模板用途等补充说明。',
  `data_source_id` BIGINT NOT NULL COMMENT '当前版本使用的数据源，取值为 t_data_source.data_source_id；与当前版本行的 data_source_id 同事务写入，供列表筛选与删除数据源时的引用检查。',
  `status` VARCHAR(32) NOT NULL COMMENT '模板状态；只有 ACTIVE 可执行。允许值：ACTIVE、INACTIVE。',
  `current_version_no` INT NOT NULL COMMENT '当前生效的版本号，对应 t_query_template_version.version_no；保存新版本时同事务更新。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '模板乐观并发版本，从 1 开始，每次保存、恢复或启停加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该模板的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该模板的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_query_template_code` (`template_code`) COMMENT '保证业务唯一性：template_code',
  KEY `idx_query_template_source` (`data_source_id`, `status`) COMMENT '支持访问路径：data_source_id、status',
  KEY `idx_query_template_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存查询模板的编码、名称、状态与当前生效版本；内容保存在模板版本表，每次保存生成新版本并立即生效。';

CREATE TABLE `t_query_template_version` (
  `template_version_id` BIGINT NOT NULL COMMENT '服务侧生成的模板版本 Snowflake 主键。',
  `template_id` BIGINT NOT NULL COMMENT '所属查询模板，取值为 t_query_template.template_id。',
  `version_no` INT NOT NULL COMMENT '模板内的版本号，从 1 开始，每次保存加一。',
  `data_source_id` BIGINT NOT NULL COMMENT '该版本使用的数据源，取值为 t_data_source.data_source_id。',
  `body` MEDIUMTEXT NOT NULL COMMENT '统一模板语法的模板正文，UTF-8 不超过 data-query.limits.max-body-length（默认 48 KB）。',
  `parameters_json` JSON NOT NULL COMMENT '参数声明 JSON 数组，每项含 name、type、elementType、required、default、maxLength、min、max、allowedValues、maxSize、choices、fields；type 允许值：STRING、INTEGER、DECIMAL、BOOLEAN、DATE、DATETIME、ENUM、FRAGMENT、LIST。无参数时为空数组。',
  `columns_json` JSON NULL COMMENT '出参列声明 JSON 数组，每项含 name、label、mask、keepStart、keepEnd；mask 允许值：NONE、HIDE、FULL、KEEP_EDGES。NULL 表示全部列原样返回。',
  `constraints_json` JSON NOT NULL COMMENT '模板约束 JSON 对象，含 maxRows、timeoutSeconds、pagingMode、maxPageSize、countAllowed、cursorColumns；pagingMode 允许值：NONE、OFFSET、CURSOR。取值不超过 data-query.limits 硬上限。',
  `restored_from_version_no` INT NULL COMMENT '由历史版本恢复生成时的来源版本号；NULL 表示不是恢复生成。',
  `change_note` VARCHAR(512) NULL COMMENT '保存该版本时填写的变更说明。',
  `created_by` BIGINT NOT NULL COMMENT '保存该版本的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该版本保存的 UTC 微秒时间；版本行创建后不可变，没有更新时间。',
  PRIMARY KEY (`template_version_id`),
  UNIQUE KEY `uk_query_template_ver` (`template_id`, `version_no`) COMMENT '保证业务唯一性：template_id、version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存查询模板每次保存的不可变内容：数据源、正文、参数声明、列声明与约束；用于执行、查看、对比与恢复，创建后不修改。';

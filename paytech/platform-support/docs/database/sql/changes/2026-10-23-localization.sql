-- 多语言管理：新增 t_i18n_locale、t_i18n_application、t_i18n_namespace、t_i18n_key、t_i18n_translation、
-- t_i18n_release、t_i18n_release_bundle 七张表（待执行，需授权）。
-- 需求：requirements/20261022/platform-support-多语言管理/technique.md 第 3.2、4 节。
-- 目标库：Platform Support（mor_platform / mor_platform_e2e）。只新增表，不修改已有表或数据。
-- 预检：SELECT COUNT(*) FROM information_schema.TABLES
--         WHERE TABLE_SCHEMA = DATABASE()
--           AND TABLE_NAME IN ('t_i18n_locale', 't_i18n_application', 't_i18n_namespace', 't_i18n_key',
--                              't_i18n_translation', 't_i18n_release', 't_i18n_release_bundle');
--       结果须为 0；为 7 说明已执行，跳过本文件；为 1 至 6 时先人工核对已有表结构，不要直接重跑。
-- 执行顺序：先执行本文件，再部署包含 localization 模块的 Platform；旧 Platform 不读写这七张表，可先建表后发布。
--          之后再执行初始化 DML 中多语言的注册块（块 135：语言、应用、权限、菜单、路由、文件原因码与检查策略）。
-- 执行后核对：对七张表执行 SHOW CREATE TABLE，字段、索引与表/字段注释须与 platform-support-schema.sql
--             和 platform-support.pdma.json（图组 localization，待 PDManer 修复后补登）一致。
-- 回滚：先回退 Platform（或关闭多语言入口），确认七张表中没有需要保留的文案与发布后执行
--       DROP TABLE IF EXISTS `t_i18n_release_bundle`;
--       DROP TABLE IF EXISTS `t_i18n_release`;
--       DROP TABLE IF EXISTS `t_i18n_translation`;
--       DROP TABLE IF EXISTS `t_i18n_key`;
--       DROP TABLE IF EXISTS `t_i18n_namespace`;
--       DROP TABLE IF EXISTS `t_i18n_application`;
--       DROP TABLE IF EXISTS `t_i18n_locale`;
--       （已上传到公开桶的语言包文件不随表删除，按文件服务的通用孤儿清理处理。）
SET NAMES utf8mb4;

CREATE TABLE `t_i18n_locale` (
  `locale_id` BIGINT NOT NULL COMMENT '服务侧生成的语言 Snowflake 主键。',
  `locale_code` VARCHAR(35) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BCP 47 语言代码，如 en、zh-CN；全局唯一，创建后不可修改。取值来源：IETF BCP 47；非固定枚举。',
  `native_name` VARCHAR(64) NOT NULL COMMENT '本语言的自称，如“简体中文”，用于语言切换显示。',
  `english_name` VARCHAR(64) NOT NULL COMMENT '语言的英文名称，如 Simplified Chinese。',
  `text_direction` VARCHAR(8) NOT NULL COMMENT '文字方向；本期只登记 LTR 语言，RTL 仅预留。允许值：LTR、RTL。',
  `status` VARCHAR(16) NOT NULL COMMENT '语言状态；DISABLED 的语言不能被选为默认或必须覆盖语言，也不出现在 manifest 中，en 不能停用。允许值：ENABLED、DISABLED。',
  `sort_order` INT NOT NULL COMMENT '显示排序值，升序排列，取值 0 至 9999。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '语言乐观并发版本，从 1 开始，每次修改加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该记录的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该记录的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`locale_id`),
  UNIQUE KEY `uk_i18n_locale_code` (`locale_code`) COMMENT '保证业务唯一性：locale_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存系统支持的界面语言目录：BCP 47 代码、显示名、文字方向与启停状态；en 为全系统基准语言且不可停用。';

CREATE TABLE `t_i18n_application` (
  `application_id` BIGINT NOT NULL COMMENT '服务侧生成的应用 Snowflake 主键。',
  `application_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局唯一的应用编码，如 client-console；小写字母开头的小写字母、数字与连字符，创建后不可修改。',
  `application_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的应用名称。',
  `default_locale` VARCHAR(35) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用默认语言，取值为 ENABLED 的 t_i18n_locale.locale_code；取代需求中的渠道默认语言。',
  `required_locales` JSON NOT NULL COMMENT '必须覆盖的语言代码 JSON 数组，元素为 ENABLED 的 t_i18n_locale.locale_code、不重复、最多 20 个；发布时这些语言的覆盖率须达到阈值，可为空数组。',
  `coverage_threshold_percent` INT NOT NULL COMMENT '必须覆盖语言的翻译覆盖率阈值百分比，取值 0 至 100；translated × 100 ≥ 阈值 × total 才能发布。',
  `current_release_id` BIGINT NULL COMMENT '当前发布，取值为状态 READY 的 t_i18n_release.release_id；NULL 表示还没有上线的发布，前端只用构建内置文案。',
  `key_set_revision` INT NOT NULL DEFAULT 0 COMMENT 'key 集合修订号，从 0 开始，每次确认 key 导入加一；导入预览摘要依据它判断预览是否过期。',
  `content_revision` INT NOT NULL DEFAULT 0 COMMENT '文案修订号，从 0 开始，每次导入 key、保存或导入翻译加一；发布登记时核对生成语言包期间它没有变化。',
  `status` VARCHAR(16) NOT NULL COMMENT '应用状态；DISABLED 时不提供 manifest。允许值：ENABLED、DISABLED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '应用设置的乐观并发版本，从 1 开始，修改名称、默认语言、必须覆盖语言或阈值时加一；修订号与当前发布变化不改变它。',
  `created_by` BIGINT NOT NULL COMMENT '创建该记录的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该记录的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`application_id`),
  UNIQUE KEY `uk_i18n_app_code` (`application_code`) COMMENT '保证业务唯一性：application_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存语言包消费方应用：默认语言、必须覆盖语言与发布阈值、当前发布指针以及 key 集合与文案修订号；应用与前端渠道一一对应。';

CREATE TABLE `t_i18n_namespace` (
  `namespace_id` BIGINT NOT NULL COMMENT '服务侧生成的命名空间 Snowflake 主键。',
  `application_id` BIGINT NOT NULL COMMENT '所属应用，取值为 t_i18n_application.application_id。',
  `namespace_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '应用内唯一的命名空间编码，小写字母开头的小写字母、数字与连字符，不含点号，创建后不可修改。',
  `namespace_name` VARCHAR(64) NOT NULL COMMENT '在 Operations 页面显示的命名空间名称。',
  `description` VARCHAR(512) NULL COMMENT '命名空间用途说明；NULL 表示未填写。',
  `sort_order` INT NOT NULL COMMENT '显示排序值，升序排列，取值 0 至 9999。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '命名空间乐观并发版本，从 1 开始，每次修改加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该记录的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该记录的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`namespace_id`),
  UNIQUE KEY `uk_i18n_namespace` (`application_id`, `namespace_code`) COMMENT '保证业务唯一性：application_id、namespace_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存应用内的命名空间：前端懒加载与语言包文件的粒度，在后台创建与维护；其下 key 必须以“编码.”开头。';

CREATE TABLE `t_i18n_key` (
  `key_id` BIGINT NOT NULL COMMENT '服务侧生成的 key Snowflake 主键。',
  `application_id` BIGINT NOT NULL COMMENT '所属应用，取值为 t_i18n_application.application_id；与命名空间所属应用一致。',
  `namespace_id` BIGINT NOT NULL COMMENT '所属命名空间，取值为 t_i18n_namespace.namespace_id。',
  `key_code` VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '完整 key，如 team.invite.submit；必须以所属命名空间编码加点号开头。',
  `source_text` TEXT NOT NULL COMMENT '英文基准文案，ICU MessageFormat；由导入写入，运营不能修改。',
  `description` VARCHAR(512) NULL COMMENT '开发者说明，由导入写入；NULL 表示未提供。',
  `source_revision` INT NOT NULL DEFAULT 1 COMMENT '英文基准修订号，从 1 开始，基准文案变化时加一；翻译依据的修订号小于它即为基准已变更。',
  `status` VARCHAR(16) NOT NULL COMMENT 'key 状态；只有 ACTIVE 参与翻译与发布。允许值：ACTIVE、DEPRECATED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT 'key 乐观并发版本，从 1 开始，每次导入修改该行时加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该记录的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该记录的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`key_id`),
  UNIQUE KEY `uk_i18n_key` (`namespace_id`, `key_code`) COMMENT '保证业务唯一性：namespace_id、key_code',
  KEY `idx_i18n_key_app` (`application_id`, `status`, `key_code`) COMMENT '支持访问路径：application_id、status、key_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存由代码导入的文案 key 与英文基准；导入文件中没有的 key 标为废弃而不删除，重新出现时恢复并保留原翻译。';

CREATE TABLE `t_i18n_translation` (
  `translation_id` BIGINT NOT NULL COMMENT '服务侧生成的翻译 Snowflake 主键。',
  `key_id` BIGINT NOT NULL COMMENT '所属 key，取值为 t_i18n_key.key_id。',
  `locale_code` VARCHAR(35) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '翻译语言，取值为 t_i18n_locale.locale_code，不为 en。',
  `text` TEXT NOT NULL COMMENT '翻译文案，ICU MessageFormat；参数名集合与英文基准一致，保存与导入时校验。',
  `based_on_source_revision` INT NOT NULL COMMENT '保存该翻译时 key 的英文基准修订号；小于 t_i18n_key.source_revision 表示基准已变更。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '翻译乐观并发版本，从 1 开始，每次修改加一；批量保存按它逐条 CAS。',
  `created_by` BIGINT NOT NULL COMMENT '创建该记录的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该记录的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`translation_id`),
  UNIQUE KEY `uk_i18n_translation` (`key_id`, `locale_code`) COMMENT '保证业务唯一性：key_id、locale_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 key 在一个非英文语言下的翻译；不存状态，基准已变更由翻译依据的修订号与 key 当前修订号比较得出。en 不在本表存储。';

CREATE TABLE `t_i18n_release` (
  `release_id` BIGINT NOT NULL COMMENT '服务侧生成的发布 Snowflake 主键。',
  `application_id` BIGINT NOT NULL COMMENT '所属应用，取值为 t_i18n_application.application_id。',
  `release_no` INT NOT NULL COMMENT '应用内发布号，从 1 开始，按应用内最大值加一。',
  `based_on_content_revision` INT NOT NULL COMMENT '生成语言包时应用的文案修订号；登记时与应用当前修订号一致。',
  `status` VARCHAR(16) NOT NULL COMMENT '发布状态；PENDING_CHECK 等待语言包文件检查，READY 可作为当前发布，FAILED 有文件被拒绝。允许值：PENDING_CHECK、READY、FAILED。',
  `key_count` INT NOT NULL COMMENT '发布包含的 ACTIVE key 数。',
  `coverage` JSON NOT NULL COMMENT '发布时各语言覆盖率 JSON 数组，每项含 locale、required、totalKeys、translatedKeys、sourceChangedKeys、coveragePercent、satisfied。',
  `report` JSON NOT NULL COMMENT '发布报告 JSON 对象，含 missing（未翻译而回退英文）与 sourceChanged（基准已变更仍沿用旧翻译），各为 {locale, keyCode} 数组。',
  `release_note` VARCHAR(512) NULL COMMENT '发布说明；NULL 表示未填写。',
  `failure_reason` VARCHAR(512) NULL COMMENT '检查失败原因；非 FAILED 时为 NULL。',
  `ready_time` DATETIME(6) NULL COMMENT '检查通过变为 READY 的 UTC 微秒时间；未通过时为 NULL。',
  `created_by` BIGINT NOT NULL COMMENT '发布人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '发布登记的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该记录最后一次持久化更新（状态变化）的 UTC 微秒时间。',
  PRIMARY KEY (`release_id`),
  UNIQUE KEY `uk_i18n_release` (`application_id`, `release_no`) COMMENT '保证业务唯一性：application_id、release_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存应用的不可变发布：发布号、依据的文案修订号、覆盖率与发布报告；文案在语言包文件中，状态只从 PENDING_CHECK 走到 READY 或 FAILED。';

CREATE TABLE `t_i18n_release_bundle` (
  `bundle_id` BIGINT NOT NULL COMMENT '服务侧生成的语言包 Snowflake 主键。',
  `release_id` BIGINT NOT NULL COMMENT '所属发布，取值为 t_i18n_release.release_id。',
  `locale_code` VARCHAR(35) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '语言包语言，取值为 t_i18n_locale.locale_code。',
  `namespace_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '语言包命名空间编码，发布时的 t_i18n_namespace.namespace_code。',
  `file_address` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件服务地址 /files/v1/{fileId}；文件位于公开桶，公开地址由文件服务在读取时计算，不落库。',
  `content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '语言包内容 SHA-256，小写十六进制。',
  `byte_size` INT NOT NULL COMMENT '语言包字节数。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间；创建后不可变，没有更新时间。',
  PRIMARY KEY (`bundle_id`),
  UNIQUE KEY `uk_i18n_bundle` (`release_id`, `locale_code`, `namespace_code`) COMMENT '保证业务唯一性：release_id、locale_code、namespace_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存发布中每个语言 × 命名空间的语言包文件地址与内容摘要；不存语言包正文，创建后不修改。';

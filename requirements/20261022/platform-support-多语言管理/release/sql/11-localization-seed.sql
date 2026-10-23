-- 多语言管理 · 块 135 第 2 部分：语言 en、zh-CN 与 4 个应用（无发布、修订号 0），mor_platform。
-- 来源：../../db/dml.sql 第 413—435 行。前置：01-platform-ddl.sql 已执行（七张 t_i18n_* 表为空）。旧 Platform 不读这些表，可在窗口外执行。
-- 本文件不含 COMMIT：apply_sql.sh <step>-dry 执行后断开即回滚；<step> 在末尾追加 COMMIT。guard 不符时子查询多行报错，mysql 非零退出，
-- 未提交事务随断开回滚。语句正文逐字取自需求目录 db/ 的对应段（只去掉 START TRANSACTION / COMMIT），生成脚本另行核对。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
-- ---------------------------------------------------------------------------
-- 第 2 部分：多语言业务初始数据（technique.md 3.9 末段）。固定 ID 取块 135 的 1350xx 段，低于 Snowflake 范围的做法同块 132 第 1 段。
-- 语言：en（基准，不可停用）、zh-CN。应用：operations-console 默认 zh-CN；client-console、partner-console、checkout 默认 en；
-- 四个应用必须覆盖 zh-CN，阈值 100%（technique 未给阈值，取最严格值，运营可在「语言与应用」调整）。均无发布，修订号 0。
-- 前置：changes/2026-10-23-localization.sql 已执行；两表中没有这些语言代码与应用编码。
-- 可重跑：否。回退：见 dml-rollback.sql 第 2 部分。
-- ---------------------------------------------------------------------------

SET @i18n_seed_time = UTC_TIMESTAMP(6);
SET @i18n_seed_actor_id = 1;

INSERT INTO t_i18n_locale (locale_id, locale_code, native_name, english_name, text_direction, status, sort_order, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000135001, 'en', 'English', 'English', 'LTR', 'ENABLED', 10, 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135002, 'zh-CN', '简体中文', 'Chinese (Simplified)', 'LTR', 'ENABLED', 20, 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time);

INSERT INTO t_i18n_application (application_id, application_code, application_name, default_locale, required_locales, coverage_threshold_percent, current_release_id, key_set_revision, content_revision, status, version, created_by, updated_by, create_time, update_time) VALUES
  (900000000000135011, 'operations-console', 'Operations Console', 'zh-CN', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135012, 'client-console', 'Client Console', 'en', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135013, 'partner-console', 'Partner Console', 'en', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time),
  (900000000000135014, 'checkout', 'Checkout', 'en', JSON_ARRAY('zh-CN'), 100, NULL, 0, 0, 'ENABLED', 1, @i18n_seed_actor_id, @i18n_seed_actor_id, @i18n_seed_time, @i18n_seed_time);


SELECT (SELECT COUNT(*) FROM t_i18n_locale) AS locales_2, (SELECT COUNT(*) FROM t_i18n_application) AS applications_4;
SELECT IF((SELECT COUNT(*) FROM t_i18n_locale WHERE locale_id BETWEEN 900000000000135001 AND 900000000000135002) = 2
  AND (SELECT COUNT(*) FROM t_i18n_locale) = 2
  AND (SELECT COUNT(*) FROM t_i18n_application WHERE application_id BETWEEN 900000000000135011 AND 900000000000135014) = 4
  AND (SELECT COUNT(*) FROM t_i18n_application) = 4,
  'seed 2/4 ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;

-- 多语言管理 · 回滚块 135 第 2 部分：删除语言 2 行与应用 4 行，mor_platform。
-- 来源：../../db/dml-rollback.sql 第 21—25 行。前提：t_i18n_namespace / key / translation / release / release_bundle 均为空（guard 判定），
-- 否则先决定是否整表回退（93-rollback-ddl.sql）。通常只在彻底撤销本需求时执行。
-- 本文件不含 COMMIT：apply_sql.sh <step>-dry 执行后断开即回滚；<step> 在末尾追加 COMMIT。guard 不符时子查询多行报错，mysql 非零退出，
-- 未提交事务随断开回滚。语句正文逐字取自需求目录 db/ 的对应段（只去掉 START TRANSACTION / COMMIT），生成脚本另行核对。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
START TRANSACTION;
-- 第 2 部分：多语言初始数据（语言 2、应用 4）
DELETE FROM t_i18n_application WHERE application_id BETWEEN 900000000000135011 AND 900000000000135014;
DELETE FROM t_i18n_locale WHERE locale_id BETWEEN 900000000000135001 AND 900000000000135002;
SELECT IF((SELECT COUNT(*) FROM t_i18n_locale) = 0
  AND (SELECT COUNT(*) FROM t_i18n_application) = 0
  AND (SELECT COUNT(*) FROM t_i18n_namespace) + (SELECT COUNT(*) FROM t_i18n_key) + (SELECT COUNT(*) FROM t_i18n_translation) + (SELECT COUNT(*) FROM t_i18n_release) + (SELECT COUNT(*) FROM t_i18n_release_bundle) = 0,
  'rollback seed ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;

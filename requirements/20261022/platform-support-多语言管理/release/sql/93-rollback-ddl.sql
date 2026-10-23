-- 多语言管理 · 回退七张 t_i18n_* 表（mor_platform；DDL 隐式提交，不可 dry）。取自 01-platform-ddl.sql 文件头“回滚”段。
-- 只在彻底撤销本需求时执行，且必须：Platform 已回滚到 compose-rollback.yaml 镜像（旧 Platform 不读这些表）；91-rollback-seed 已执行或接受随表删除；
-- 用户确认这些表中的文案与发布不需要保留（备份目录有 mor_platform 全库）。已上传到公开桶的语言包文件不随表删除（文件服务的孤儿文件）。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';
SELECT IF(DATABASE() = @target_db, 'database ok', (SELECT 'wrong database' UNION ALL SELECT 'abort')) AS db_guard;
DROP TABLE IF EXISTS `t_i18n_release_bundle`;
DROP TABLE IF EXISTS `t_i18n_release`;
DROP TABLE IF EXISTS `t_i18n_translation`;
DROP TABLE IF EXISTS `t_i18n_key`;
DROP TABLE IF EXISTS `t_i18n_namespace`;
DROP TABLE IF EXISTS `t_i18n_application`;
DROP TABLE IF EXISTS `t_i18n_locale`;
SELECT COUNT(*) AS i18n_tables_left_0 FROM information_schema.TABLES WHERE TABLE_SCHEMA = @target_db AND TABLE_NAME LIKE 't\_i18n\_%';

-- Client Console 接入自助：Platform 增量 DDL（执行文件）
-- 目标库：Platform Support（本机 mor_platform、mor_platform_e2e；mor-test 未授权，未执行）
-- 前置：t_api_credential 不存在 credential_name 列（执行前先查 information_schema，存在则跳过）
-- 回滚：ALTER TABLE t_api_credential DROP COLUMN credential_name;（会丢失客户填写的名称）
-- 完整 Schema 已同步：platform-support-service/docs/database/sql/platform-support-schema.sql 与 PDManer 模型

ALTER TABLE `t_api_credential`
  ADD COLUMN `credential_name` VARCHAR(64) NULL COMMENT '客户为凭证填写的显示名称；历史凭证为空。' AFTER `key_prefix`;

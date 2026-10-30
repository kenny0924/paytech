-- mor_payout · 结算出款切片 S1 增量：核验意见备注与给 Owner 的说明（requirements/20261030/payout-收款账户与余额解释，原型 O2 / O5）
-- 性质：待执行文件，未执行。目标库 mor_payout（本机 mor-local-mysql-1 与 mor-test 同一文件），只用于已按 ddl.sql 首个版本建表的库；
--   新建空库直接执行 ddl.sql（已含这两列），不要再执行本文件。
-- 内容：t_beneficiary_account 增加两列，均 VARCHAR(500) NULL、无默认值、无索引，位置与 ddl.sql 首建版本一致：
--   review_maker_note（Maker 核验意见的内部备注，只给运营）、reason_note（拒绝时运营给 Owner 的说明，随拒绝通知 reasonText 发送）。
-- 执行者：有 ALTER 权限的管理员账号（payout_app 只有 SELECT, INSERT, UPDATE, DELETE）。
-- 前置检查：SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'mor_payout'
--   AND TABLE_NAME = 't_beneficiary_account' AND COLUMN_NAME IN ('review_maker_note', 'reason_note'); 应返回 0 行。
-- 重复执行：列已存在时报错 1060 中止，不产生改动。
-- 应用顺序：先执行本文件，再部署含这两列映射的 payout-service（旧版本不读写这两列，可先加列后发版）。
-- 回退：先回退 payout-service 版本，再执行
--   ALTER TABLE `t_beneficiary_account` DROP COLUMN `reason_note`, DROP COLUMN `review_maker_note`;（会丢失已填写的备注与说明）。
SET NAMES utf8mb4;
USE `mor_payout`;

ALTER TABLE `t_beneficiary_account`
  ADD COLUMN `review_maker_note` VARCHAR(500) NULL COMMENT 'Maker 核验意见的内部备注（可选，最多 500 字符），只给运营查看；恢复重新核验时清空。' AFTER `review_maker_decision`,
  ADD COLUMN `reason_note` VARCHAR(500) NULL COMMENT '拒绝时运营给 Owner 的说明（可选，最多 500 字符），随拒绝通知的 reasonText 发送；停用、关闭、恢复时清空。' AFTER `reason_code`;

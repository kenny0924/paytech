-- 33-archive-retired.sql · V2 不承载的旧数据归档
-- 影响：在 mor_v2_migration_archive 中保存 V2 不再承载的旧数据，保证阶段 3 删除旧库后不丢失；不改动任何业务库。
--   1) 三个旧库的 t_command_idempotency（design/03 §2：V2 删除，HTTP 幂等由 Gateway 承担）。
--   2) mor_commerce.t_commerce_order 的 context_format、preparation_command_id（第 03 步 C7 删除列），按 order_id 保存。
--   3) mor_risk 的 7 张争议表（design/03 §2.2 归 dispute；mor-test 不部署 dispute-service，不建 mor_dispute）。
-- 前置：10 已建 mor_v2_migration_archive；本文件含 CREATE TABLE（DDL 隐式提交），只在空归档库上执行一次。
-- 回滚：DROP DATABASE `mor_v2_migration_archive`（只含副本，源库未改动）。
-- 保留期：至少保留到阶段 3 删除旧库之后，由用户决定何时删除（见报告“待确认”）。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `mor_v2_migration_archive`.`cct_t_command_idempotency` LIKE `mor_cct`.`t_command_idempotency`;
INSERT INTO `mor_v2_migration_archive`.`cct_t_command_idempotency` SELECT * FROM `mor_cct`.`t_command_idempotency`;

CREATE TABLE `mor_v2_migration_archive`.`commerce_t_command_idempotency` LIKE `mor_commerce`.`t_command_idempotency`;
INSERT INTO `mor_v2_migration_archive`.`commerce_t_command_idempotency` SELECT * FROM `mor_commerce`.`t_command_idempotency`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_command_idempotency` LIKE `mor_risk`.`t_command_idempotency`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_command_idempotency` SELECT * FROM `mor_risk`.`t_command_idempotency`;

CREATE TABLE `mor_v2_migration_archive`.`commerce_t_commerce_order_retired_columns` (
  `order_id` BIGINT NOT NULL COMMENT '旧 mor_commerce.t_commerce_order 主键。',
  `context_format` VARCHAR(32) NOT NULL COMMENT '旧列原值（V2 第 03 步删除）。',
  `preparation_command_id` BIGINT NULL COMMENT '旧列原值（V2 第 03 步删除）。',
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='V2 迁移归档：t_commerce_order 删除列的原值';
INSERT INTO `mor_v2_migration_archive`.`commerce_t_commerce_order_retired_columns` (`order_id`, `context_format`, `preparation_command_id`)
SELECT `order_id`, `context_format`, `preparation_command_id` FROM `mor_commerce`.`t_commerce_order`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_case` LIKE `mor_risk`.`t_dispute_case`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_case` SELECT * FROM `mor_risk`.`t_dispute_case`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_evidence_package` LIKE `mor_risk`.`t_dispute_evidence_package`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_evidence_package` SELECT * FROM `mor_risk`.`t_dispute_evidence_package`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_evidence_ref` LIKE `mor_risk`.`t_dispute_evidence_ref`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_evidence_ref` SELECT * FROM `mor_risk`.`t_dispute_evidence_ref`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_liability_decision` LIKE `mor_risk`.`t_dispute_liability_decision`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_liability_decision` SELECT * FROM `mor_risk`.`t_dispute_liability_decision`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_outcome` LIKE `mor_risk`.`t_dispute_outcome`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_outcome` SELECT * FROM `mor_risk`.`t_dispute_outcome`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_state_change` LIKE `mor_risk`.`t_dispute_state_change`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_state_change` SELECT * FROM `mor_risk`.`t_dispute_state_change`;

CREATE TABLE `mor_v2_migration_archive`.`risk_t_dispute_submission_request` LIKE `mor_risk`.`t_dispute_submission_request`;
INSERT INTO `mor_v2_migration_archive`.`risk_t_dispute_submission_request` SELECT * FROM `mor_risk`.`t_dispute_submission_request`;


-- 20-platform-upgrade.sql · mor_platform 原库升级到 V2（57 表）
-- 差异来源：演练库对 mor-test 转储（56 表）与 platform-support-schema.sql（57 表）逐表比对 information_schema
--   （scripts/schema_diff.py）：56 张既有表的列、类型、可空、默认值、排序规则与索引全部一致，唯一差异是缺 t_currency_spec。
--   platform-support-initial-data.sql 种子行按主键比对（scripts/seed_diff.py）：除 t_currency_spec 外不缺行；
--   status / version 等差异是 mor-test 运营变更，保留现值；t_role_permission 缺的 5 个种子主键在库中以其他主键存在同一
--   role_id + permission_id 授权，不补。
-- 影响：
--   1) 建 t_currency_spec 并写 USD 行（DDL 与 DML 逐字取自 platform-support-schema.sql 与 platform-support-initial-data.sql）；
--   2) Gateway Target commerce-client-api 的发现名 commerce-service → acquiring-service（第 03 步改名；路由、Scope、
--      Guard 不变；Snapshot 由 Platform 实时编译，Gateway 下次拉取生效）。consumer 无 Gateway 路由（mor-test 未登记 Customer 路由），不新增 Target。
--   历史数据不改：t_webhook_event 等冻结载荷里的 commerce-service、审计属性里的旧服务名是历史事实，保持原样。
-- 前置：scripts/migrate.sh 核对 mor_platform 为 56 表且无 t_currency_spec，或已是 57 表（重复执行时 1) 跳过）。
-- 回滚：UPDATE t_gateway_target SET discovery_service_name='commerce-service', version=version+1, update_time=UTC_TIMESTAMP(6)
--         WHERE target_service_code='commerce-client-api' AND discovery_service_name='acquiring-service';
--       DROP TABLE t_currency_spec;（只含本文件写入的 USD 行，无其他读者前可删）
SET NAMES utf8mb4;
SET time_zone = '+00:00';
USE `mor_platform`;

CREATE TABLE IF NOT EXISTS `t_currency_spec` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的币种规则 Snowflake 主键。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `minor_unit` TINYINT NOT NULL COMMENT '记账与结算金额允许保留的小数位数，取值范围为零至八。',
  `rounding_mode` VARCHAR(64) NOT NULL COMMENT '金额从八位计算精度归整到币种精度时采用的固定方式。允许值：HALF_UP。',
  `status` VARCHAR(64) NOT NULL COMMENT '币种规则当前是否允许被新业务使用。允许值：DRAFT、ACTIVE、INACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency` (`currency`) COMMENT '保证业务唯一性：currency'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 为 Owner，Ledger 与 Billing 持只读副本。';

START TRANSACTION;
INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
SELECT 1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
 WHERE NOT EXISTS (SELECT 1 FROM `t_currency_spec` WHERE `currency` = 'USD');
UPDATE `t_gateway_target`
   SET `discovery_service_name` = 'acquiring-service', `version` = `version` + 1, `update_time` = UTC_TIMESTAMP(6)
 WHERE `target_service_code` = 'commerce-client-api' AND `discovery_service_name` = 'commerce-service';
COMMIT;

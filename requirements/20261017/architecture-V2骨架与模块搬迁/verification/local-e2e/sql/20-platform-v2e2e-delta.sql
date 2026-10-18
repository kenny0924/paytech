-- mor_platform_v2e2e：由 mor_platform_e2e 整库复制（apply-e2e.sh platform 步骤，mysqldump）后补齐与现行 Schema 的差异。
-- 复制原因：旧全链路库已含交接后的运营管理员、本机签名公钥、Client Console 与运营路由及其启用补丁；V2 本步不重建这些配置，
--           也不改动 mor_platform_e2e 本身（旧架构全链路仍可用）。
-- 执行前核对（只读，2026-10-18 已核）：mor_platform 相对 mor_platform_e2e 只多 t_currency_spec 一张表，无缺列；
--   t_api_scope 只有 orders.read（9300000000000001）。
-- 内容：1) t_currency_spec 建表与 USD 行，逐字取自 paytech/platform-support/docs/database/sql/platform-support-schema.sql
--        与 platform-support-initial-data.sql；2) Client API 4 个 Scope，逐字取自
--        requirements/20261017/commerce-ClientAPI测试环境联通/db/dml-api-scope.sql。
-- 预期：1 张表、1 行币种、4 行 Scope。回滚：DROP DATABASE mor_platform_v2e2e（整库为本步副本）。
USE mor_platform_v2e2e;

CREATE TABLE `t_currency_spec` (
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
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
INSERT INTO t_api_scope (api_scope_id, scope_code, display_name, description, version, create_time, update_time) VALUES
  (9300000000000002, 'checkouts.write', '创建结账', 'Client API V1：创建 Hosted Checkout 订单', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9300000000000003, 'orders.cancel', '取消订单', 'Client API V1：取消未支付订单', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9300000000000004, 'refunds.read', '查询退款', 'Client API V1：查询可退金额与退款', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
  (9300000000000005, 'refunds.write', '创建退款', 'Client API V1：对已支付订单创建退款', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
COMMIT;

-- 发布副本：逐字取自 paytech/billing/docs/database/sql/changes/2026-10-20-drop-currency-spec-copy.sql（mor_billing）。最后一步，另行授权。
-- billing：删除 Platform 币种规则只读副本（需求 requirements/20261020/platform-support-币种主数据维护与读取缓存，技术方案 2.4 与第 4 节）。
-- 审阅后手工执行，应用与测试不执行本文件。
-- 目标库：mor_billing。执行后与 billing-schema.sql、billing-initial-data.sql、billing.pdma.json 一致（10 张表）。
-- 原因：币种只在 Platform（mor_platform.t_currency_spec）一处维护；billing 改为启动时经 platform-support-api 读取全部币种规则，
--   放入进程内全量快照并定时刷新，计费事实登记与精度只读内存快照，不再读取本表。
-- 发布顺序：先部署读取 Platform 快照的新版 billing-service（需配置 BILLING_PLATFORM_SERVICE_URL）并验收通过，最后才执行本文件；
--   旧版应用仍读取本表，表删除后旧版无法登记或读取计费事实。
-- 数据影响：只删除副本表及其 USD 行（与 Platform 种子相同），不影响计费事实；t_billing_fact.currency 等列不变。
SET NAMES utf8mb4;

DROP TABLE `t_currency_spec`;

-- ==================== 回退 ====================
-- 回退旧版应用前，按下面原建表语句与 USD 行重建副本（与 Platform 当前数据核对后，按需补充其他已启用币种）：
--
-- CREATE TABLE `t_currency_spec` (
--   `id` BIGINT NOT NULL COMMENT '服务侧生成的币种规则 Snowflake 主键。',
--   `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
--   `minor_unit` TINYINT NOT NULL COMMENT '记账与结算金额允许保留的小数位数，取值范围为零至八。',
--   `rounding_mode` VARCHAR(64) NOT NULL COMMENT '金额从八位计算精度归整到币种精度时采用的固定方式。允许值：HALF_UP。',
--   `status` VARCHAR(64) NOT NULL COMMENT '币种规则当前是否允许被新业务使用。允许值：DRAFT、ACTIVE、INACTIVE。',
--   `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
--   `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
--   PRIMARY KEY (`id`),
--   UNIQUE KEY `uk_currency` (`currency`) COMMENT '保证业务唯一性：currency'
-- ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='只读副本，Owner platform-support（D41）：保存 Finance 使用的 ISO 4217 币种精度、舍入方式和启停状态；ledger 与 billing 各持一份，不在本服务写入。';
--
-- INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `create_time`, `update_time`)
-- VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

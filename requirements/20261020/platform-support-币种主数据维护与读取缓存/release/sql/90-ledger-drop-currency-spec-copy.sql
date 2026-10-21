-- 发布副本：逐字取自 paytech/ledger/docs/database/sql/changes/2026-10-20-drop-currency-spec-copy.sql（mor_ledger）。最后一步，另行授权。
-- 2026-10-20 币种主数据维护与读取缓存：mor_ledger 删除 t_currency_spec 只读副本
-- 需求目录：requirements/20261020/platform-support-币种主数据维护与读取缓存/（technique.md §2.4、§4）。
-- 文件性质：执行文件（变更脚本）。目标库：mor_ledger（本机 local-environment、mor-test）。
-- 内容：ledger 改为启动时加载并定时刷新 platform-support 的币种规则内存快照（GET /platform/v1/currency-specs），
--       不再读取本库副本；删除副本表及其 USD 初始化行。
-- 前置：新 ledger 镜像（读取 Platform 快照）已部署并验收通过；旧镜像仍读本表，不得在旧镜像运行时执行。
-- 执行顺序：Platform ALTER 与种子 → 替换 platform-support、ops-bff、ledger、billing 镜像 → 验收通过 → 本文件。
-- 本文件不可重复执行（表已不存在时 DROP 失败）；执行前确认表内只有 USD 一行（与 Platform 一致）。
SET NAMES utf8mb4;

DROP TABLE `t_currency_spec`;

-- ==================== 回滚 ====================
-- 先恢复旧 ledger 镜像前执行；表结构与初始化行与删除前的 ledger-schema.sql、ledger-initial-data.sql 逐字一致。
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
-- 若删除后 Platform 已启用其他币种且旧镜像需要使用，按 Platform 当前行逐条补入（id 取 Snowflake 或 < 1,000,000 的保留号）。

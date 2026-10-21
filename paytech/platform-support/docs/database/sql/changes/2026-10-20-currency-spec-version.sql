-- 币种主数据维护：t_currency_spec 增加乐观并发版本并修改表注释（待执行，需授权）。
-- 需求：requirements/20261020/platform-support-币种主数据维护与读取缓存/technique.md 第 2.1 节。
-- 目标库：Platform Support（mor_platform / mor_platform_e2e）。
-- 预检：SELECT COUNT(*) FROM information_schema.COLUMNS
--         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_currency_spec' AND COLUMN_NAME = 'version';
--       结果须为 0；否则说明已执行，跳过本文件。
-- 执行顺序：先执行本文件，再部署读写该列的新 Platform；旧 Platform 只读不写该表，加列后仍可正常运行。
--          之后再执行初始化 DML 末尾的「Operations currency spec console」块（块 125）与发布激活脚本。
-- 影响：已有行（USD）取默认版本 1；表注释去掉“Ledger 与 Billing 持只读副本”。不修改其他表或数据。
-- 执行后核对：SHOW CREATE TABLE t_currency_spec; 的 version 列定义与表注释须与
--             platform-support-schema.sql 和 platform-support.pdma.json 一致。
-- 回退：先回退新 Platform（旧版本不读该列），再执行
--       ALTER TABLE `t_currency_spec` DROP COLUMN `version`,
--         COMMENT='保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 为 Owner，Ledger 与 Billing 持只读副本。';
--       （已发生的版本递增随列一起丢失，不影响币种规则本身。）
SET NAMES utf8mb4;

ALTER TABLE `t_currency_spec`
  ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '币种规则乐观并发版本，从 1 开始，每次维护成功加一。' AFTER `status`,
  COMMENT='保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 唯一持有，ledger、billing 经内部 HTTP 读取并在进程内缓存快照。';

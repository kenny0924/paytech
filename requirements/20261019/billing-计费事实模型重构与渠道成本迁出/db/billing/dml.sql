-- billing：工作日日历初始化（需求 requirements/20261019/billing-计费事实模型重构与渠道成本迁出，技术方案第 2.3 节）。审阅后手工执行。
-- 目标库：mor_billing；前提：同目录 ddl.sql 已执行，t_business_calendar 为空。
-- 内容：1 行 DEFAULT 日历（UTC，周一至周五为工作日，不登记例外日）。Owner 与同步机制另立需求；本行与 billing-initial-data.sql 逐字一致。
-- 回退：DELETE FROM t_business_calendar WHERE calendar_code = 'DEFAULT';
SET NAMES utf8mb4;

INSERT INTO `t_business_calendar` (`calendar_code`, `display_name`, `timezone`, `status`, `create_time`, `update_time`)
VALUES ('DEFAULT', '默认工作日日历（周一至周五）', 'UTC', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- billing：登记阿联酋工作日日历 AE_BUSINESS_DAYS（需求 requirements/20261022/billing-支付事实自动登记，S15）。审阅后手工执行，应用与测试不执行本文件。
-- 背景：commercial 的 Client 价格结算字段固定引用 CommercialCodes.AE_BUSINESS_DAYS；billing 推算到期转可用时间时日历必须存在且 ACTIVE，否则事实停在重试。
-- 目标库：mor_billing（本机与 mor-test 分别授权执行）。前提：t_business_calendar 已按 billing-schema.sql 建立。
-- 影响：t_business_calendar 新增 1 行（Asia/Dubai，周一至周五为工作日，不登记例外日）；不改结构；执行后与 billing-initial-data.sql 一致。
-- 可重跑：是（主键已存在时不插入、不覆盖）。
-- 发布顺序：须在启用自动 PAYMENT 事实登记的 billing-service 处理首笔真实订单前执行；早于或晚于发布均可，缺失期间事实只会重试。
SET NAMES utf8mb4;

INSERT INTO `t_business_calendar` (`calendar_code`, `display_name`, `timezone`, `status`, `create_time`, `update_time`)
SELECT 'AE_BUSINESS_DAYS', '阿联酋工作日', 'Asia/Dubai', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `t_business_calendar` WHERE `calendar_code` = 'AE_BUSINESS_DAYS');

-- 回退（确认没有 PAYMENT 事实定价输入引用该日历、且 t_business_calendar_day 无该日历例外日后执行）：
-- DELETE FROM `t_business_calendar` WHERE `calendar_code` = 'AE_BUSINESS_DAYS';

-- 删除经营主体来源 PLATFORM（全系统只保留 CLIENT / PARTNER，不保留历史）。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/requirement.md 第 7.6 节、AC-13；technique.md 第 3.4.1 节（acquiring / risk-decision DML）。
-- 目标库：Risk Decision（mor_risk_decision）。
-- 预检（任一不为 0 须先停下，人工处理后再执行）：
--   1) 规则或流程中以 PLATFORM 为比较值的经营主体来源条件（已发布内容带摘要，不能用 SQL 改写，须由运营改规则并重新发布）：
--      SELECT COUNT(*) FROM t_rule_operand WHERE value_type = 'STRING' AND string_value = 'PLATFORM';
--      SELECT COUNT(*) FROM t_rule_action_parameter WHERE string_value = 'PLATFORM';
--   2) 存量 PLATFORM 输入快照数：SELECT COUNT(*) FROM t_checkout_input_snapshot WHERE entity_source_type = 'PLATFORM';
--      不为 0 时，先在 client-master 执行 2026-10-24-relationship-snapshot-data.sql，取得“我们的 PayFac” Local Partner ID，
--      在本文件执行前设置 SET @payfac_local_partner_id = <该 ID>;（PARTNER 来源必须带 Local Partner，否则查询 / 重放还原输入时报参数错误）。
-- 执行顺序：与 client-master / commercial / acquiring 的 PLATFORM 删除同批；先执行本文件，再部署不认 PLATFORM 的新 Risk 镜像。
--   新 Risk 拒绝 PLATFORM 输入，Commerce（acquiring）须在 Risk 之后或同批停止发送 PLATFORM。
-- 已知影响：改写后的快照 stable_input_hash 保持原值（按 PLATFORM 计算）；同一订单若以新来源重放评估会被判为内容不同。
--   mor-test 存量订单已完成准备，不会再重放；用户决定不保留历史。
-- 执行后核对：
--   SELECT COUNT(*) FROM t_risk_variable_member WHERE variable_code = 'ENTITY_SOURCE_VALUES';  -- 2
--   SELECT COUNT(*) FROM t_checkout_input_snapshot WHERE entity_source_type = 'PLATFORM';         -- 0
-- 回退（须同时回退新 Risk 镜像；改写过的快照无法区分原值，回退不还原）：
--   INSERT INTO t_risk_variable_member (member_id, variable_code, value_key, string_value, number_value, boolean_value,
--     datetime_value, duration_value, display_name, sort_order, create_time)
--   VALUES (900000000000092001, 'ENTITY_SOURCE_VALUES', 'PLATFORM', 'PLATFORM', NULL, NULL, NULL, NULL, '平台提供', 1, UTC_TIMESTAMP(6));
--   ALTER TABLE t_checkout_input_snapshot MODIFY COLUMN `entity_source_type` VARCHAR(64) NOT NULL
--     COMMENT '经营主体来源的稳定业务代码。允许值：PLATFORM、CLIENT、PARTNER。';
SET NAMES utf8mb4;

START TRANSACTION;

DELETE FROM t_risk_variable_member
WHERE member_id = 900000000000092001 AND variable_code = 'ENTITY_SOURCE_VALUES' AND value_key = 'PLATFORM';

-- 未设置 @payfac_local_partner_id 时不改写（预检 2 为 0 时无需设置）。
UPDATE t_checkout_input_snapshot
SET entity_source_type = 'PARTNER', local_partner_id = @payfac_local_partner_id
WHERE entity_source_type = 'PLATFORM' AND @payfac_local_partner_id IS NOT NULL;

COMMIT;

ALTER TABLE t_checkout_input_snapshot MODIFY COLUMN `entity_source_type` VARCHAR(64) NOT NULL
  COMMENT '经营主体来源的稳定业务代码。允许值：CLIENT、PARTNER。';

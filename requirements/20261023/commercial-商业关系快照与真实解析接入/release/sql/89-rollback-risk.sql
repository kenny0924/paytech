-- 回滚（mor_risk_decision）：撤销 50-risk-remove-platform.sql（risk-decision 旧镜像已换回之后执行）。
-- 恢复目录行 PLATFORM；被改写的输入快照 = PARTNER + local_partner_id = PayFac Partner + 创建时间早于 50 的改写时刻（50 输出的
-- relsnap_rewrite_time；REAL 期间新建的 PARTNER 快照晚于它，新 Mock 快照的 local_partner_id 是 107），改回 PLATFORM、local_partner_id 置空。
-- stable_input_hash 从未改动（50 不重算），改回后与原值一致。执行前把下一行改为 50 的实际输出。
SET NAMES utf8mb4;
SET @relsnap_rewrite_time = NULL;  -- 例如 '2026-10-26 02:10:00.000000'；为空时下面的 UPDATE 不生效
START TRANSACTION;
INSERT INTO t_risk_variable_member (member_id, variable_code, value_key, string_value, number_value, boolean_value,
  datetime_value, duration_value, display_name, sort_order, create_time)
SELECT 900000000000092001, 'ENTITY_SOURCE_VALUES', 'PLATFORM', 'PLATFORM', NULL, NULL, NULL, NULL, '平台提供', 1, '2026-09-13 05:11:54.534647'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_risk_variable_member WHERE member_id = 900000000000092001);
SELECT ROW_COUNT() AS `member_restored_expect_1`;
UPDATE t_checkout_input_snapshot SET entity_source_type = 'PLATFORM', local_partner_id = NULL
 WHERE entity_source_type = 'PARTNER' AND local_partner_id = 900000000001370001
   AND @relsnap_rewrite_time IS NOT NULL AND create_time < @relsnap_rewrite_time;
SELECT ROW_COUNT() AS `snapshots_reverted_expect_46`;
COMMIT;
ALTER TABLE `t_checkout_input_snapshot`
  MODIFY COLUMN `entity_source_type` varchar(64) NOT NULL COMMENT '经营主体来源的稳定业务代码。允许值：PLATFORM、CLIENT、PARTNER。';

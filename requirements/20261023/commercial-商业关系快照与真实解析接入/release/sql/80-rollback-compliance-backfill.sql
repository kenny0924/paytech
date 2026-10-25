-- 回滚（mor_compliance）：撤销 11-compliance-backfill.sql 回填的 Client 资格投影。
-- 只删回填后未再变更的行（version = 1 且只有 BACKFILL 一条状态变更）；新代码上线后经入驻审核或手工登记变更过的投影不在此删，见 81。
SET NAMES utf8mb4;
START TRANSACTION;
DELETE q FROM `t_role_qualification` q
  JOIN `t_qualification_state_change` s ON s.`qualification_id` = q.`qualification_id` AND s.`trigger_type` = 'BACKFILL'
 WHERE q.`version` = 1
   AND NOT EXISTS (SELECT 1 FROM `t_qualification_state_change` o WHERE o.`qualification_id` = q.`qualification_id` AND o.`trigger_type` <> 'BACKFILL');
SELECT ROW_COUNT() AS `role_qualification_deleted`;
DELETE s FROM `t_qualification_state_change` s
 WHERE s.`trigger_type` = 'BACKFILL'
   AND NOT EXISTS (SELECT 1 FROM `t_role_qualification` q WHERE q.`qualification_id` = s.`qualification_id`);
SELECT ROW_COUNT() AS `state_change_deleted`;
COMMIT;

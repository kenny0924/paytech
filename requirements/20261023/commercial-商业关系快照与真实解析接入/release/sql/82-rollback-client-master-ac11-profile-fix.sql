-- 回滚（mor_client_master）：撤销 23-client-master-ac11-profile-fix.sql，恢复 DEMO-CL-001 Client 资料的执行前取值
-- （draft_application_id = 237575207592763392、两个 content_hash 为空）。只改回 23 写入的合成值；23 之后若资料被其他流程改过则不动。
-- 已按补丁后资料建的订单冻结了合成 hash（acquiring t_order_legal 等），回滚不改订单，属不可逆部分。
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE `t_party_profile` SET `draft_application_id` = 237575207592763392, `content_hash` = NULL, `update_time` = UTC_TIMESTAMP(6)
 WHERE `profile_id` = 237575207592763395 AND `draft_application_id` IS NULL
   AND `content_hash` = SHA2('mor-test relsnap stage-7 AC-11: party profile 237575207592763395', 256);
SELECT ROW_COUNT() AS `party_profile_restored_expect_1`;
UPDATE `t_client_business_profile` SET `content_hash` = NULL, `update_time` = UTC_TIMESTAMP(6)
 WHERE `business_profile_id` = 237575207592763396
   AND `content_hash` = SHA2('mor-test relsnap stage-7 AC-11: business profile 237575207592763396', 256);
SELECT ROW_COUNT() AS `business_profile_restored_expect_1`;
COMMIT;

-- 回滚（mor_client_master）：撤销 22-client-master-data.sql（AE001 迁到 PayFac Partner、角色回填、收单资质 NOT NULL 之外的数据部分）。
-- 依据正式数据文件头的回退段，补两处：① 同时删除这些 BCR 行在发布后由 Admin API 写入的 t_business_change_version 快照（否则成孤儿）；
-- ② AE001 根由 21 种子建立（history_id 9000000000013810xx，在本段之外），按正式文件规则只把版本减 1。
-- 顺序：先把 client-master / commercial / acquiring / risk-decision 换回旧镜像（新代码不认 PLATFORM，数据改回 PLATFORM 后新 client-master
-- 候选查询会 503 失败关闭），再执行本文件，然后 84（种子）与 85（结构）。
-- **不可逆部分**：发布后经 Admin API 对 AE001 / PayFac Partner 的修改（收单资质、Partner 角色）及其审计快照随本文件丢弃，只能从执行前
-- mysqldump 查阅；发布后新建 Client 由新代码写入的 t_party_role CLIENT 行不在本号段，随 85 删表一起消失。
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM `t_party_role` WHERE `party_role_id` BETWEEN 900000000001370001 AND 900000000001379999;
SELECT ROW_COUNT() AS `party_role_deleted`;
DELETE FROM `t_partner_role` WHERE `local_partner_id` = 900000000001370001
   OR `partner_role_id` BETWEEN 900000000001370001 AND 900000000001379999;
SELECT ROW_COUNT() AS `partner_role_deleted`;
UPDATE `t_operating_entity_application` SET `source_type` = 'PLATFORM', `source_partner_id` = NULL, `update_time` = UTC_TIMESTAMP(6)
 WHERE `source_type` = 'PARTNER' AND `source_partner_id` = 900000000001370001;
SELECT ROW_COUNT() AS `application_reverted`;
UPDATE `t_business_change_record` SET `current_version` = `current_version` - 1, `update_time` = UTC_TIMESTAMP(6)
 WHERE `record_type` = 'OPERATING_ENTITY' AND `parent_history_id` IS NULL
   AND `history_id` NOT BETWEEN 900000000001370001 AND 900000000001379999
   AND `record_id` IN (SELECT `entity_no` FROM `t_entity_source_relation` WHERE `source_type` = 'PARTNER' AND `source_partner_id` = 900000000001370001);
SELECT ROW_COUNT() AS `oe_root_version_decremented`;
UPDATE `t_entity_source_relation` SET `source_type` = 'PLATFORM', `source_partner_id` = NULL, `update_time` = UTC_TIMESTAMP(6)
 WHERE `source_type` = 'PARTNER' AND `source_partner_id` = 900000000001370001;
SELECT ROW_COUNT() AS `source_reverted`;
DELETE FROM `t_business_change_version` WHERE `history_id` BETWEEN 900000000001370001 AND 900000000001379999
   OR `history_id` IN (SELECT `history_id` FROM (SELECT `history_id` FROM `t_business_change_record`
                        WHERE `record_type` = 'LOCAL_PARTNER' AND `record_id` = '900000000001370001') h);
SELECT ROW_COUNT() AS `bcv_deleted`;
DELETE FROM `t_business_change_record` WHERE `history_id` BETWEEN 900000000001370001 AND 900000000001379999
   OR (`record_type` = 'LOCAL_PARTNER' AND `record_id` = '900000000001370001');
SELECT ROW_COUNT() AS `bcr_deleted`;
DELETE FROM `t_local_partner` WHERE `local_partner_id` = 900000000001370001;
SELECT ROW_COUNT() AS `local_partner_deleted`;
COMMIT;

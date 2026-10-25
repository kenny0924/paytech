-- mor-test 专用（mor_client_master，root 手工执行）：AC-11 验收 Client 的资料定稿补丁。**需用户决定 D-3 批准后才执行**。
-- 原因（盘点 F-2 / GAP-PROFILE，既有缺陷，不是本需求引入）：client-master 入驻审核通过只写 t_party_profile.approved_time，
--   从不清空 draft_application_id、从不写 t_party_profile.content_hash 与 t_client_business_profile.content_hash（全仓 Mapper 没有写这两列的语句）。
--   mor-test 37 个已通过 Client 全部是 draft 非空 + 两个 hash 为空，client-master 候选查询（CheckoutEligibilityContext.classification）
--   因此判定分类无效 → 422 CLIENT_NOT_ELIGIBLE（即 technique F11 的 422 来源）；即使越过，commercial 合同签署校验也要求
--   draft 为空且 hash 合法，否则 409 CCT_CONTEXT_AMBIGUOUS。切 REAL 后所有真实 Client 都无法建单，AC-11 无法验收。
-- 本文件只补 AC-11 选定的 DEMO-CL-001 Client 237575207592763393（Platform 有 API 凭据、commercial 合同 ACTIVE、CARD 开通 USD、
--   ACQ V1 ACTIVE、已有 6 笔 US / en / USD 订单）。hash 为 SHA-256 of 固定说明串（合成值，不是资料内容摘要）。
-- 副作用：该 Client 的入驻申请详情按 draft_application_id 归集人员（ClientPeopleMapper），清空后已通过申请的详情页可能不再列出该资料；
--   申请已 APPROVED、不可编辑，不影响业务。根治需另立 client-master 需求（审核通过时定稿资料），见盘点 R-4。
-- 失败即停：前置值与盘点不符时 SIGNAL，不写。回滚：82-rollback-client-master-ac11-profile-fix.sql。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DROP PROCEDURE IF EXISTS `relsnap_ac11_assert`;
DELIMITER //
CREATE PROCEDURE `relsnap_ac11_assert`()
BEGIN
  IF (SELECT COUNT(*) FROM `t_party_profile` WHERE `profile_id` = 237575207592763395 AND `party_id` = 237575207592763394
        AND `approved_time` IS NOT NULL AND `draft_application_id` = 237575207592763392 AND `content_hash` IS NULL) <> 1
     OR (SELECT COUNT(*) FROM `t_client_business_profile` WHERE `business_profile_id` = 237575207592763396
        AND `client_id` = 237575207592763393 AND `content_hash` IS NULL AND `classification_confirmed_time` IS NOT NULL) <> 1
     OR (SELECT COUNT(*) FROM `t_client` WHERE `client_id` = 237575207592763393 AND `party_id` = 237575207592763394 AND `status` = 'ACTIVE') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AC-11 client profile state differs from the stage-6 inventory; abort';
  END IF;
END//
DELIMITER ;
CALL `relsnap_ac11_assert`();

START TRANSACTION;
UPDATE `t_party_profile`
   SET `draft_application_id` = NULL,
       `content_hash` = SHA2('mor-test relsnap stage-7 AC-11: party profile 237575207592763395', 256),
       `update_time` = UTC_TIMESTAMP(6)
 WHERE `profile_id` = 237575207592763395 AND `draft_application_id` = 237575207592763392 AND `content_hash` IS NULL;
SELECT ROW_COUNT() AS `party_profile_expect_1`;
UPDATE `t_client_business_profile`
   SET `content_hash` = SHA2('mor-test relsnap stage-7 AC-11: business profile 237575207592763396', 256),
       `update_time` = UTC_TIMESTAMP(6)
 WHERE `business_profile_id` = 237575207592763396 AND `content_hash` IS NULL;
SELECT ROW_COUNT() AS `business_profile_expect_1`;
COMMIT;

DROP PROCEDURE `relsnap_ac11_assert`;

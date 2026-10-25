-- 回滚（mor_compliance）：撤销 10-compliance-ddl.sql，须与 compliance 旧镜像（8a86a31c 之前的现行镜像）同一窗口进行，先换回旧镜像再执行。
-- 发布前 t_role_qualification / t_qualification_state_change 为空、没有 MANUAL 决定（00-precheck），这两张表只由新代码与 11 写入，
-- 所以完整回滚 = 清空两张投影表 + 删除 MANUAL 决定 + 结构改回。**不可逆部分**：发布后运营手工登记 / 撤销的资格（含依据说明
-- basis_note）与新代码在入驻审核通过时写入的投影全部丢弃，只能从执行前的 mysqldump 备份查阅；入驻审核写入的 REVIEWER 决定保留
-- （role_id 为十进制文本，改回 BIGINT 无损）。改回前若存在非数字 role_id（OPERATING_ENTITY 的 entity_no）则下面的检查中止。
SET NAMES utf8mb4;
START TRANSACTION;
DELETE FROM `t_qualification_state_change`;
SELECT ROW_COUNT() AS `state_change_deleted`;
DELETE FROM `t_role_qualification`;
SELECT ROW_COUNT() AS `role_qualification_deleted`;
DELETE FROM `t_qualification_decision` WHERE `decision_source` = 'MANUAL';
SELECT ROW_COUNT() AS `manual_decision_deleted`;
COMMIT;
DROP PROCEDURE IF EXISTS `relsnap_cmp_rollback_assert`;
DELIMITER //
CREATE PROCEDURE `relsnap_cmp_rollback_assert`()
BEGIN
  IF EXISTS (SELECT 1 FROM `t_qualification_decision` WHERE `case_id` IS NULL OR `role_id` NOT REGEXP '^[0-9]{1,19}$') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'non-numeric role_id or NULL case_id remains; cannot restore BIGINT / NOT NULL';
  END IF;
END//
DELIMITER ;
CALL `relsnap_cmp_rollback_assert`();
DROP PROCEDURE `relsnap_cmp_rollback_assert`;
-- 列定义与注释取自执行前 mysqldump（2026-10-25T11:12Z）。
ALTER TABLE `t_role_qualification`
  MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '角色的稳定业务标识。';
ALTER TABLE `t_qualification_decision`
  DROP COLUMN `basis_note`,
  MODIFY COLUMN `decision_source` varchar(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE。',
  MODIFY COLUMN `role_id` bigint NOT NULL COMMENT '角色的稳定业务标识。',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '尽调案件的稳定业务标识。';

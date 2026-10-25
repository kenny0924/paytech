-- 2026-10-24 商业关系快照与真实解析接入：compliance 角色资格支持运营手工登记，角色标识改为字符型。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md 第 3.3.2、3.4.2 节（S-05、S-07）。
--   t_qualification_decision：case_id 改可空（MANUAL 决定不关联案件）；role_id 由 BIGINT 改为 VARCHAR(64) ascii_bin
--     （OPERATING_ENTITY 存 entity_no，其余存十进制 ID，存量数值无损转换为十进制文本）；新增 basis_note；decision_source 注释加 MANUAL。
--   t_role_qualification：role_id 同上改为字符型；uk_role_qualification 随列重建（MODIFY COLUMN 自动重建包含该列的索引）。
--   uk_qual_decision_ver(case_id, decision_version) 对 NULL 不约束，MANUAL 决定不受影响；表数不变（25）。
-- 目标库：mor_compliance（root 手工执行）。执行前备份 t_qualification_decision 与 t_role_qualification；DDL 隐式提交，
-- 失败时先核对已完成的语句，禁止整段重跑。新代码（compliance 写入字符型 role_id）必须在本脚本执行后上线。
--
-- 回退（须与 compliance 代码一起回退；回退前先删除 MANUAL 决定与 OPERATING_ENTITY 资格，否则改回 BIGINT / NOT NULL 会失败或截断）：
--   DELETE FROM `t_qualification_state_change` WHERE `decision_id` IN (SELECT `decision_id` FROM `t_qualification_decision` WHERE `decision_source` = 'MANUAL');
--   DELETE FROM `t_role_qualification` WHERE `role_type` = 'OPERATING_ENTITY';
--   DELETE FROM `t_qualification_decision` WHERE `decision_source` = 'MANUAL';
--   ALTER TABLE `t_role_qualification`
--     MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。';
--   ALTER TABLE `t_qualification_decision`
--     DROP COLUMN `basis_note`,
--     MODIFY COLUMN `decision_source` VARCHAR(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE。',
--     MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。',
--     MODIFY COLUMN `case_id` BIGINT NOT NULL COMMENT '尽调案件的稳定业务标识。';
SET time_zone = '+00:00';

ALTER TABLE `t_qualification_decision`
  MODIFY COLUMN `case_id` BIGINT NULL COMMENT '尽调案件的稳定业务标识；运营手工登记或撤销（decision_source=MANUAL）的决定不关联案件，为空。',
  MODIFY COLUMN `role_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色的稳定业务标识：OPERATING_ENTITY 存 entity_no（两位大写字母加三位数字，如 AE001），CLIENT、LOCAL_PARTNER、RELATED_PERSON 存十进制 ID 文本。',
  MODIFY COLUMN `decision_source` VARCHAR(64) NOT NULL COMMENT '决定来源的稳定业务代码。允许值：AUTOMATED、REVIEWER、OVERRIDE、MANUAL。',
  ADD COLUMN `basis_note` VARCHAR(1000) NULL COMMENT '运营手工登记或撤销资格的依据说明，最多 1000 字符；仅 MANUAL 决定填写，其他来源为空。' AFTER `override_reason_code`;

ALTER TABLE `t_role_qualification`
  MODIFY COLUMN `role_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色的稳定业务标识：OPERATING_ENTITY 存 entity_no（两位大写字母加三位数字，如 AE001），CLIENT、LOCAL_PARTNER、RELATED_PERSON 存十进制 ID 文本。';

-- 执行后核对：
--   SHOW CREATE TABLE `t_qualification_decision`;
--   SHOW CREATE TABLE `t_role_qualification`;

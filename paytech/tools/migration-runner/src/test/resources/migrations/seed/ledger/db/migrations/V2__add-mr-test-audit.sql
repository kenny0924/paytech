ALTER TABLE `t_mr_test_entry` ADD COLUMN `note` VARCHAR(64) NULL COMMENT '第 2 版新增的备注。';

CREATE TABLE `t_mr_test_audit` (
  `audit_id` BIGINT NOT NULL COMMENT '测试审计标识。',
  `message` VARCHAR(128) NOT NULL COMMENT '测试审计内容。',
  PRIMARY KEY (`audit_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='迁移执行器测试审计表';

UPDATE `t_mr_test_entry` SET `note` = 'v2' WHERE `entry_id` = 1;

CREATE TABLE `t_mr_test_entry` (
  `entry_id` BIGINT NOT NULL COMMENT '测试条目标识。',
  `label` VARCHAR(64) NOT NULL COMMENT '测试标签。',
  PRIMARY KEY (`entry_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='迁移执行器测试表';
-- Keep the migration lock long enough for the second runner to start waiting on it.
SELECT SLEEP(2);

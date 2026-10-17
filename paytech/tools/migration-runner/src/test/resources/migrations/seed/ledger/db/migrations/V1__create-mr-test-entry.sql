-- Fixture V1: semicolons inside comments; strings; and `identifiers` must not split statements.
SET NAMES utf8mb4;

CREATE TABLE `t_mr_test_entry` (
  `entry_id` BIGINT NOT NULL COMMENT '测试条目标识; 注释内分号。',
  `label` VARCHAR(64) NOT NULL COMMENT '测试标签。',
  PRIMARY KEY (`entry_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='迁移执行器测试表';

/* block comment; still one statement below */
INSERT INTO `t_mr_test_entry` (`entry_id`, `label`)
VALUES (1, 'a;b'), (2, 'it''s; fine');

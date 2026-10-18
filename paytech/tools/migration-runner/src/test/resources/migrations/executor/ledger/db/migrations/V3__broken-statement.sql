ALTER TABLE `t_mr_test_entry` ADD COLUMN `extra` INT NULL COMMENT '第 3 版新增列；DDL 已执行不会回滚。';
-- Deliberate error: the table does not exist.
INSERT INTO `t_mr_test_missing` (`id`) VALUES (1);

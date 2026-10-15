-- Vault 数据类型值格式约束：结构变更（阶段 3 评审稿，待执行文件）
-- 目标库：mor_vault（本机、test 分别执行；应用启动不执行 DDL）。
-- 字符集：用 mysql --default-character-set=utf8mb4 执行；客户端默认 latin1 会把中文注释写成乱码。
-- 变更：t_vault_data_type 新增 2 列。value_format 带默认值 'TEXT'，保证旧版 Vault（不写该列）在发布窗口内照常新增数据类型；
--       现有行取默认值 TEXT，行为与现状一致。value_pattern 可空，仅值格式为 REGEX 时有值。
-- 不新增 CHECK / FOREIGN KEY（项目 2026-09-04 规范）：格式与正则的组合约束由 Vault 应用校验。
-- 执行顺序：本文件 → 发布新版 Vault → BFF → Web → 经管理接口设置现有类型的格式（不使用 DML）。
-- 执行前备份 mor_vault.t_vault_data_type。
-- 回滚（须先回退到不读取新列的 Vault 版本）：
--   ALTER TABLE t_vault_data_type DROP COLUMN value_pattern, DROP COLUMN value_format;

ALTER TABLE t_vault_data_type
  ADD COLUMN value_format VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TEXT' COMMENT '值格式 TEXT 不限 / JSON JSON 对象 / REGEX 按正则整值匹配' AFTER data_class,
  ADD COLUMN value_pattern VARCHAR(256) NULL COMMENT '值格式为 REGEX 时的 RE2 正则（不含首尾锚点，按整值匹配）；其余格式为空' AFTER value_format;

-- 核对：SHOW FULL COLUMNS FROM t_vault_data_type LIKE 'value_%'; 应为 2 行；SELECT value_format, COUNT(*) FROM t_vault_data_type GROUP BY value_format; 全部为 TEXT。

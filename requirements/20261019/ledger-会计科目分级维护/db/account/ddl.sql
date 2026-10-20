-- account 明细账可读账户号：结构变更（待执行文件，未在任何环境执行）
-- 目标库：mor_account（本机 local-environment / mor-test，分别授权）
-- 执行顺序：在 ledger 两份 SQL 之后（科目编号以 account-api 目录为准，与 ledger STANDARD 一致）；按第 1 段 → 第 2 段执行
-- 是否可重跑：否
-- 恢复：见文末；第 2 段之后恢复会丢失账户号

-- ===================== 第 1 段：加列与新表 =====================

ALTER TABLE `t_owner_account`
  ADD COLUMN `account_no` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '财务人员可读账户号：{主体}-{币种}-{科目编号}-S{序号}，S 表示明细账账户，序号按主体、币种与科目代码从 1 递增、至少 6 位；主体段 OE 为 entityNo、Finance Entity 为 FE 加主体主键；创建时生成后不再改变，只用于显示与搜索，不进入事件载荷、不作为跨系统关联键。' AFTER `id`;

CREATE TABLE `t_owner_account_no_sequence` (
  `id` BIGINT NOT NULL COMMENT '账户号序列的服务侧雪花主键。',
  `entity_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属法律主体类型。允许值：OPERATING_ENTITY、FINANCE_ENTITY。',
  `entity_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户所属主体标识：OE 为 CCT entityNo，Finance Entity 为 ledger 主数据主键十进制字符串。',
  `currency` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户币种，ISO 4217 大写三位代码。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户类型目录中的科目代码。',
  `last_value` BIGINT NOT NULL COMMENT '同主体同币种同科目最近一次分配给账户号的序号；在开户本地事务中以 INSERT … ON DUPLICATE KEY UPDATE 原子递增，已分配序号不复用，允许空洞。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 account 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_account_no_seq` (`entity_type`, `entity_id`, `currency`, `account_code`) COMMENT '保证业务唯一性：entity_type、entity_id、currency、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按主体、币种与科目代码为明细账账户分配可读账户号序号。';

-- ===================== 第 2 段：回填与约束 =====================

-- 2.1 存量明细账账户回填（科目编号与 account-api OwnerAccountType 目录一致）
UPDATE `t_owner_account` a
JOIN (
  SELECT o.`id`,
         CONCAT(IF(o.`entity_type` = 'FINANCE_ENTITY', CONCAT('FE', o.`entity_id`), o.`entity_id`), '-', o.`currency`, '-',
                CASE o.`account_code`
                  WHEN 'CLIENT_PAYABLE' THEN '22010101'
                  WHEN 'PARTNER_PAYABLE' THEN '22010201'
                  WHEN 'CLIENT_SECURITY_DEPOSIT' THEN '22110101'
                  WHEN 'CLIENT_RECOVERY_RECEIVABLE' THEN '11310101'
                  WHEN 'PARTNER_RECOVERY_RECEIVABLE' THEN '11310102'
                  WHEN 'BANK_CASH' THEN '10010101'
                END, '-S',
                LPAD(ROW_NUMBER() OVER (PARTITION BY o.`entity_type`, o.`entity_id`, o.`currency`, o.`account_code` ORDER BY o.`create_time`, o.`id`), 6, '0')) AS `account_no`
  FROM `t_owner_account` o
) n ON n.`id` = a.`id`
SET a.`account_no` = n.`account_no`;

-- 2.2 初始化序列
INSERT INTO `t_owner_account_no_sequence` (`id`, `entity_type`, `entity_id`, `currency`, `account_code`, `last_value`, `create_time`, `update_time`)
SELECT 990000000000000000 + ROW_NUMBER() OVER (ORDER BY `entity_type`, `entity_id`, `currency`, `account_code`),
       `entity_type`, `entity_id`, `currency`, `account_code`, COUNT(*), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM `t_owner_account`
GROUP BY `entity_type`, `entity_id`, `currency`, `account_code`;

-- 2.3 核对：应返回 0（出现未知 account_code 时 CONCAT 结果为 NULL，同样计入），否则停止并恢复
SELECT COUNT(*) AS `owner_account_without_no` FROM `t_owner_account` WHERE `account_no` IS NULL;

-- 2.4 改为必填并加唯一索引
ALTER TABLE `t_owner_account`
  MODIFY COLUMN `account_no` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '财务人员可读账户号：{主体}-{币种}-{科目编号}-S{序号}，S 表示明细账账户，序号按主体、币种与科目代码从 1 递增、至少 6 位；主体段 OE 为 entityNo、Finance Entity 为 FE 加主体主键；创建时生成后不再改变，只用于显示与搜索，不进入事件载荷、不作为跨系统关联键。',
  ADD UNIQUE KEY `uk_owner_account_no` (`account_no`) COMMENT '保证业务唯一性：account_no';

-- ===================== 恢复（仅在需要回退时执行） =====================
-- ALTER TABLE `t_owner_account` DROP INDEX `uk_owner_account_no`, DROP COLUMN `account_no`;
-- DROP TABLE `t_owner_account_no_sequence`;

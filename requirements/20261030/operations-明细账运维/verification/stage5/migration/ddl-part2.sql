-- ===================== 第 2 段：回填与约束（在 dml.sql 之后执行） =====================

-- 2.1 存量总账账户回填账户号（按账簿 + 科目内 create_time、id 顺序编号）
UPDATE `t_ledger_account` a
JOIN (
  SELECT la.`id`,
         CONCAT(IF(b.`entity_type` = 'FINANCE_ENTITY', fe.`entity_code`, b.`entity_id`), '-', b.`currency`, '-', c.`account_no`, '-',
                LPAD(ROW_NUMBER() OVER (PARTITION BY la.`ledger_book_id`, la.`chart_account_id` ORDER BY la.`create_time`, la.`id`), 4, '0')) AS `account_no`
  FROM `t_ledger_account` la
  JOIN `t_ledger_book` b ON b.`id` = la.`ledger_book_id`
  JOIN `t_chart_account` c ON c.`id` = la.`chart_account_id`
  LEFT JOIN `t_finance_entity` fe ON b.`entity_type` = 'FINANCE_ENTITY' AND fe.`id` = CAST(b.`entity_id` AS UNSIGNED)
) n ON n.`id` = a.`id`
SET a.`account_no` = n.`account_no`;

-- 2.2 初始化序列，使后续新户从存量最大序号之后继续
INSERT INTO `t_account_no_sequence` (`id`, `ledger_book_id`, `chart_account_id`, `last_value`, `create_time`, `update_time`)
SELECT 990000000000000000 + ROW_NUMBER() OVER (ORDER BY `ledger_book_id`, `chart_account_id`),
       `ledger_book_id`, `chart_account_id`, COUNT(*), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM `t_ledger_account`
GROUP BY `ledger_book_id`, `chart_account_id`;

-- 2.3 核对：以下两条都应返回 0，否则停止并按「恢复」处理
SELECT COUNT(*) AS `chart_account_without_no` FROM `t_chart_account` WHERE `account_no` IS NULL;
SELECT COUNT(*) AS `ledger_account_without_no` FROM `t_ledger_account` WHERE `account_no` IS NULL;

-- 2.4 改为必填并加唯一索引
ALTER TABLE `t_chart_account`
  MODIFY COLUMN `account_no` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '财务人员使用的分级数字科目编号：4-2-2-2，一级 4 位、每下一级加 2 位，最多 4 级；下级以上级编号开头；一级首位 1 资产、2 负债、3 权益、4 收入、5 费用。科目表被账簿采用后不可修改。',
  ADD UNIQUE KEY `uk_chart_acct_no` (`chart_id`, `account_no`) COMMENT '保证业务唯一性：chart_id、account_no';

ALTER TABLE `t_ledger_account`
  MODIFY COLUMN `account_no` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '财务人员可读账户号：{主体}-{币种}-{科目编号}-{序号}，序号按账簿与科目从 1 递增、至少 4 位；创建时生成后不再改变，只用于显示与搜索，不作为跨系统关联键。',
  ADD UNIQUE KEY `uk_ledger_account_no` (`account_no`) COMMENT '保证业务唯一性：account_no';


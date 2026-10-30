-- ledger 会计科目分级与可读账户号：结构变更（待执行文件，未在任何环境执行）
-- 目标库：mor_ledger（本机 local-environment / mor-test，分别授权）
-- 执行顺序：本文件第 1 段 → db/ledger/dml.sql → 本文件第 2 段
-- 是否可重跑：否（ALTER ADD COLUMN / CREATE TABLE 重跑会报错，报错即停止，不影响已执行部分）
-- 恢复：第 2 段之前可直接执行文末「恢复」段；第 2 段之后恢复会丢失变更记录与账户号
-- 阶段 4 以 ledger.pdma.json 为设计源重新生成完整 Schema，本文件与之逐字段一致

-- ===================== 第 1 段：加列与新表 =====================

ALTER TABLE `t_chart_account`
  ADD COLUMN `account_no` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '财务人员使用的分级数字科目编号：4-2-2-2，一级 4 位、每下一级加 2 位，最多 4 级；下级以上级编号开头；一级首位 1 资产、2 负债、3 权益、4 收入、5 费用。科目表被账簿采用后不可修改。' AFTER `parent_account_id`,
  ADD COLUMN `account_status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    COMMENT '科目启停状态。允许值：ACTIVE、INACTIVE。停用后不能新建账户、不能被新规则版本引用，已有账户照常记账与查询；与永久结构属性 posting_allowed 无关。' AFTER `posting_allowed`;

ALTER TABLE `t_ledger_account`
  ADD COLUMN `account_no` VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '财务人员可读账户号：{主体}-{币种}-{科目编号}-{序号}，序号按账簿与科目从 1 递增、至少 4 位；创建时生成后不再改变，只用于显示与搜索，不作为跨系统关联键。' AFTER `id`;

CREATE TABLE `t_chart_account_change` (
  `id` BIGINT NOT NULL COMMENT '科目变更记录的服务侧雪花主键。',
  `chart_id` BIGINT NOT NULL COMMENT '被变更科目所属的科目表。',
  `chart_account_id` BIGINT NOT NULL COMMENT '被变更的科目。',
  `change_type` VARCHAR(16) NOT NULL COMMENT '变更类型。允许值：CREATE、UPDATE、DISABLE、ENABLE。',
  `before_account_no` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '变更前科目编号；CREATE 为空。',
  `after_account_no` VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更后科目编号。',
  `before_parent_account_id` BIGINT NULL COMMENT '变更前上级科目；CREATE 或原为一级科目时为空。',
  `after_parent_account_id` BIGINT NULL COMMENT '变更后上级科目；一级科目为空。',
  `before_account_name` VARCHAR(255) NULL COMMENT '变更前科目名称；CREATE 为空。',
  `after_account_name` VARCHAR(255) NOT NULL COMMENT '变更后科目名称。',
  `before_account_status` VARCHAR(16) NULL COMMENT '变更前科目状态；CREATE 为空。允许值：ACTIVE、INACTIVE。',
  `after_account_status` VARCHAR(16) NOT NULL COMMENT '变更后科目状态。允许值：ACTIVE、INACTIVE。',
  `operator_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行变更的运营人员账号标识，取自 Gateway 传递的 X-Gateway-Actor-Subject。',
  `change_time` DATETIME(6) NOT NULL COMMENT '变更在 Finance 数据库中生效的 UTC 微秒时间；记录只追加，不更新、不删除。',
  PRIMARY KEY (`id`),
  KEY `idx_chart_acct_change_account` (`chart_id`, `chart_account_id`, `change_time`) COMMENT '支持访问路径：chart_id、chart_account_id、change_time',
  KEY `idx_chart_acct_change_time` (`chart_id`, `change_time`) COMMENT '支持访问路径：chart_id、change_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='科目新增、修改、停用、启用的不可变变更记录；科目维护不审批、直接生效，以本表留痕。';

CREATE TABLE `t_account_no_sequence` (
  `id` BIGINT NOT NULL COMMENT '账户号序列的服务侧雪花主键。',
  `ledger_book_id` BIGINT NOT NULL COMMENT '账户所属总账账簿。',
  `chart_account_id` BIGINT NOT NULL COMMENT '账户所属科目。',
  `last_value` BIGINT NOT NULL COMMENT '同账簿同科目最近一次分配给账户号的序号；在建户本地事务中以 INSERT … ON DUPLICATE KEY UPDATE 原子递增，已分配序号不复用，允许空洞。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Finance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account_no_seq` (`ledger_book_id`, `chart_account_id`) COMMENT '保证业务唯一性：ledger_book_id、chart_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按账簿与科目为总账账户分配可读账户号序号。';


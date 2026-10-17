-- Client Console 团队成员与邀请：Platform 增量 DDL（执行文件）
-- 目标库：Platform Support 业务库（本机 mor_platform_e2e、mor-test 均未授权，未执行；mor_platform 为自动测试独占库，按测试规范手工建表）
-- 内容：新建 t_tenant_invitation（字段、索引与注释按 PDManer 模型 platform-support.pdma.json 的 t_tenant_invitation，无 FK/CHECK）
-- 前置：表不存在（执行前先查 information_schema.TABLES，已存在则跳过本文件）
-- 回滚：DROP TABLE t_tenant_invitation;（仅在没有业务邀请数据时；已有数据先导出）
-- 完整 Schema 已同步：platform-support-service/docs/database/sql/platform-support-schema.sql 与测试 init/ddl.sql

CREATE TABLE `t_tenant_invitation` (
  `invitation_id` BIGINT NOT NULL COMMENT '租户成员邀请的稳定业务标识。',
  `invitation_no` VARCHAR(64) NOT NULL COMMENT '租户邀请编号的业务值。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `invited_email_vault_token` VARCHAR(255) NOT NULL COMMENT '受邀邮箱 Vault Token的业务值。',
  `invited_email_masked` VARCHAR(255) NOT NULL COMMENT '受邀邮箱脱敏值的业务值。',
  `member_type` VARCHAR(64) NOT NULL COMMENT '受邀成员类型的稳定业务代码。允许值：OWNER、ADMIN、MEMBER。',
  `initial_role_id` BIGINT NOT NULL COMMENT '初始角色的稳定业务标识。',
  `invited_by_member_id` BIGINT NOT NULL COMMENT '邀请发起成员的稳定业务标识。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '邀请到期时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '租户邀请状态的稳定业务代码。允许值：PENDING、ACCEPTED、EXPIRED、REVOKED、SUPERSEDED。',
  `active_slot` INT NULL COMMENT '活动邀请占位的整数值。',
  `accepted_account_id` BIGINT NULL COMMENT '接受账号的稳定业务标识。',
  `accepted_member_id` BIGINT NULL COMMENT '接受成员的稳定业务标识。',
  `accepted_time` DATETIME(6) NULL COMMENT '接受时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '租户邀请并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`invitation_id`),
  UNIQUE KEY `uk_tenant_invitation_no` (`invitation_no`) COMMENT '保证业务唯一性：invitation_no',
  UNIQUE KEY `uk_tenant_invite_active` (`tenant_id`, `invited_email_vault_token`, `active_slot`) COMMENT '保证业务唯一性：tenant_id、invited_email_vault_token、active_slot',
  KEY `idx_invite_inviter` (`invited_by_member_id`, `tenant_id`) COMMENT '支持访问路径：invited_by_member_id、tenant_id',
  KEY `idx_invite_accepted_member` (`accepted_member_id`, `tenant_id`, `accepted_account_id`) COMMENT '支持访问路径：accepted_member_id、tenant_id、accepted_account_id',
  KEY `idx_tenant_invit_initial_role_i` (`initial_role_id`) COMMENT '支持访问路径：initial_role_id',
  KEY `idx_tenant_invit_accepted_accou` (`accepted_account_id`) COMMENT '支持访问路径：accepted_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存租户邀请的目标邮箱、成员类型、单个初始角色和接受结果；初始角色同租户且当前有效由锁定事务校验';

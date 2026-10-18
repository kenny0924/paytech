-- Explicit one-time upgrade of the existing 22-table Platform projection.
-- Preflight schema/data and back up first. MySQL DDL auto-commits; stop on the first failure.
-- No table/data removal, initialization replay or Bootstrap reset.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout = 15;

ALTER TABLE `t_password_credential` DROP FOREIGN KEY `fk_password_credential_account_id`;
ALTER TABLE `t_tenant_member` DROP FOREIGN KEY `fk_tenant_member_tenant_id`;
ALTER TABLE `t_tenant_member` DROP FOREIGN KEY `fk_tenant_member_account_id`;
ALTER TABLE `t_role` DROP FOREIGN KEY `fk_role_tenant_id`;
ALTER TABLE `t_role_permission` DROP FOREIGN KEY `fk_role_permission_role_id`;
ALTER TABLE `t_role_permission` DROP FOREIGN KEY `fk_role_permission_permission_id`;
ALTER TABLE `t_auth_session` DROP FOREIGN KEY `fk_auth_session_tenant_id`;
ALTER TABLE `t_refresh_token_generation` DROP FOREIGN KEY `fk_refresh_token_generation_session_id`;
ALTER TABLE `t_refresh_token_generation` DROP FOREIGN KEY `fk_refresh_token_generation_replaced_by_generation_id`;
ALTER TABLE `t_gateway_route` DROP FOREIGN KEY `fk_gateway_route_permission_id`;
ALTER TABLE `t_gateway_route_header` DROP FOREIGN KEY `fk_gateway_route_header_route_id`;
ALTER TABLE `t_gateway_route_query_param` DROP FOREIGN KEY `fk_gateway_route_query_param_route_id`;
ALTER TABLE `t_gateway_guard_rule` DROP FOREIGN KEY `fk_gateway_guard_rule_route_id`;
ALTER TABLE `t_audit_event` DROP FOREIGN KEY `fk_audit_event_chain_id`;
ALTER TABLE `t_audit_event` DROP FOREIGN KEY `fk_audit_event_tenant_id`;
ALTER TABLE `t_audit_event_attr` DROP FOREIGN KEY `fk_audit_event_attr_audit_event_id`;
ALTER TABLE `t_security_event` DROP FOREIGN KEY `fk_security_event_tenant_id`;
ALTER TABLE `t_tenant_member` DROP FOREIGN KEY `fk_tenant_member_inviter`;
ALTER TABLE `t_auth_session` DROP FOREIGN KEY `fk_auth_session_account_realm`;
ALTER TABLE `t_auth_session` DROP FOREIGN KEY `fk_auth_session_member_identity`;
ALTER TABLE `t_tenant_member_role` DROP FOREIGN KEY `fk_tenant_member_role_member`;
ALTER TABLE `t_tenant_member_role` DROP FOREIGN KEY `fk_tenant_member_role_role_scope`;
ALTER TABLE `t_gateway_route` DROP FOREIGN KEY `fk_gateway_route_target_channel`;
ALTER TABLE `t_console_resource` DROP FOREIGN KEY `fk_console_resource_parent_channel`;
ALTER TABLE `t_console_resource` DROP FOREIGN KEY `fk_console_resource_permission`;

ALTER TABLE `t_user_account`
  DROP CHECK `chk_user_account_positive_version`,
  DROP CHECK `chk_user_account_shape`,
  DROP CHECK `chk_user_account_credential`,
  MODIFY COLUMN `account_id` BIGINT NOT NULL COMMENT '人工用户账号的稳定业务标识。',
  MODIFY COLUMN `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  MODIFY COLUMN `account_type` VARCHAR(64) NOT NULL COMMENT '账号类型的稳定业务代码。允许值：HUMAN、BOOTSTRAP_ADMIN。',
  MODIFY COLUMN `login_name` VARCHAR(64) NULL COMMENT '非邮箱登录名的业务值。',
  MODIFY COLUMN `email_vault_token` VARCHAR(255) NULL COMMENT '登录邮箱 Vault Token的业务值。',
  MODIFY COLUMN `email_masked` VARCHAR(255) NULL COMMENT '登录邮箱脱敏值的业务值。',
  MODIFY COLUMN `email_verified_time` DATETIME(6) NULL COMMENT '邮箱验证时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '账号状态的稳定业务代码。允许值：PENDING_VERIFICATION、ACTIVE、DISABLED、CLOSED。',
  MODIFY COLUMN `consecutive_password_failures` INT NOT NULL DEFAULT 0 COMMENT '当前账号连续密码校验失败的累计次数。',
  MODIFY COLUMN `locked_until` DATETIME(6) NULL COMMENT '临时锁定截止时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `last_password_failure_time` DATETIME(6) NULL COMMENT '最近密码失败时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `credential_version` INT NOT NULL COMMENT '账号凭据版本的整数值。',
  MODIFY COLUMN `auth_epoch` INT NOT NULL COMMENT '账号认证纪元的整数值。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '账号并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_user_account_realm`, ADD UNIQUE KEY `uk_user_account_realm` (`account_id`, `realm`) COMMENT '保证业务唯一性：account_id、realm',
  DROP INDEX `uk_user_account_email`, ADD UNIQUE KEY `uk_user_account_email` (`realm`, `email_vault_token`) COMMENT '保证业务唯一性：realm、email_vault_token',
  DROP INDEX `uk_user_account_login`, ADD UNIQUE KEY `uk_user_account_login` (`realm`, `login_name`) COMMENT '保证业务唯一性：realm、login_name',
  COMMENT='保存 OPERATIONS 或 CUSTOMER Realm 的全局人工登录账号、锁定状态和认证撤销版本';

ALTER TABLE `t_password_credential`
  DROP CHECK `chk_password_credential_positive_version`,
  DROP CHECK `chk_password_credential_password_argon2`,
  DROP CHECK `chk_password_credential_password_algorithm_version`,
  DROP CHECK `chk_password_credential_password_status`,
  MODIFY COLUMN `password_credential_id` BIGINT NOT NULL COMMENT '密码凭据的稳定业务标识。',
  MODIFY COLUMN `account_id` BIGINT NOT NULL COMMENT '用户账号的稳定业务标识。',
  MODIFY COLUMN `password_verifier_phc` VARCHAR(512) NOT NULL COMMENT '密码 PHC 校验值的业务值。',
  MODIFY COLUMN `algorithm_code` VARCHAR(64) NOT NULL COMMENT '密码算法的稳定业务代码。允许值：ARGON2ID。',
  MODIFY COLUMN `algorithm_version` INT NOT NULL COMMENT '密码算法版本的整数值。',
  MODIFY COLUMN `memory_kib` INT NOT NULL COMMENT 'Argon2 内存参数的整数值。',
  MODIFY COLUMN `iterations` INT NOT NULL COMMENT 'Argon2 迭代次数的整数值。',
  MODIFY COLUMN `parallelism` INT NOT NULL COMMENT 'Argon2 并行度的整数值。',
  MODIFY COLUMN `credential_version` INT NOT NULL COMMENT '密码凭据版本的整数值。',
  MODIFY COLUMN `force_change_flag` BOOLEAN NOT NULL COMMENT '强制改密标志，1 表示是，0 表示否。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '密码凭据状态的稳定业务代码。允许值：ACTIVE、REVOKED。',
  MODIFY COLUMN `password_changed_time` DATETIME(6) NOT NULL COMMENT '密码变更时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '密码凭据并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_password_account`, ADD UNIQUE KEY `uk_password_account` (`account_id`) COMMENT '保证业务唯一性：account_id',
  COMMENT='保存账号唯一的当前 Argon2id PHC 校验值、算法参数和强制改密状态；Google-only 账号不创建占位行';

ALTER TABLE `t_access_tenant`
  DROP CHECK `chk_access_tenant_positive_version`,
  DROP CHECK `chk_access_tenant_tenant_source_tuple`,
  MODIFY COLUMN `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `tenant_type` VARCHAR(64) NOT NULL COMMENT '租户类型的稳定业务代码。允许值：CLIENT、PARTNER、INTERNAL。',
  MODIFY COLUMN `owner_ref_id` BIGINT NOT NULL COMMENT '租户所有者引用的稳定业务标识。',
  MODIFY COLUMN `tenant_name` VARCHAR(255) NOT NULL COMMENT '租户显示名称的业务值。',
  MODIFY COLUMN `owner_status` VARCHAR(64) NOT NULL COMMENT '所有者业务状态的稳定业务代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。',
  MODIFY COLUMN `source_version` INT NULL COMMENT '来源投影版本的整数值。',
  MODIFY COLUMN `source_payload_hash` CHAR(64) NULL COMMENT '来源投影载荷哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `security_status` VARCHAR(64) NOT NULL COMMENT '平台安全状态的稳定业务代码。允许值：ACTIVE、SUSPENDED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '租户并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_access_tenant`, ADD UNIQUE KEY `uk_access_tenant` (`tenant_type`, `owner_ref_id`) COMMENT '保证业务唯一性：tenant_type、owner_ref_id',
  COMMENT='保存 Client、Local Partner 或内部运营的访问隔离边界';

ALTER TABLE `t_tenant_member`
  DROP CHECK `chk_tenant_member_positive_version`,
  MODIFY COLUMN `member_id` BIGINT NOT NULL COMMENT '租户成员的稳定业务标识。',
  MODIFY COLUMN `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `account_id` BIGINT NOT NULL COMMENT '用户账号的稳定业务标识。',
  MODIFY COLUMN `member_type` VARCHAR(64) NOT NULL COMMENT '成员类型的稳定业务代码。允许值：OWNER、ADMIN、MEMBER。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '成员状态的稳定业务代码。允许值：ACTIVE、SUSPENDED、REVOKED。',
  MODIFY COLUMN `invited_by` BIGINT NULL COMMENT '邀请操作成员的稳定业务标识。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '成员并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_tenant_member`, ADD UNIQUE KEY `uk_tenant_member` (`tenant_id`, `account_id`) COMMENT '保证业务唯一性：tenant_id、account_id',
  DROP INDEX `uk_tenant_member_owner`, ADD UNIQUE KEY `uk_tenant_member_owner` (`member_id`, `tenant_id`) COMMENT '保证业务唯一性：member_id、tenant_id',
  DROP INDEX `uk_tenant_member_identity`, ADD UNIQUE KEY `uk_tenant_member_identity` (`member_id`, `tenant_id`, `account_id`) COMMENT '保证业务唯一性：member_id、tenant_id、account_id',
  DROP INDEX `idx_tenant_member_inviter`, ADD KEY `idx_tenant_member_inviter` (`invited_by`, `tenant_id`) COMMENT '支持访问路径：invited_by、tenant_id',
  DROP INDEX `idx_tenant_member_account_id`, ADD KEY `idx_tenant_member_account_id` (`account_id`) COMMENT '支持访问路径：account_id',
  COMMENT='保存全局用户账号加入访问租户后的独立授权状态；Realm、Tenant 类型和当前状态一致性由事务规则锁定';

ALTER TABLE `t_role`
  DROP CHECK `chk_role_positive_version`,
  DROP CHECK `chk_role_scope`,
  MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '授权角色的稳定业务标识。',
  MODIFY COLUMN `tenant_scope` VARCHAR(64) NOT NULL COMMENT '租户范围类型的稳定业务代码。允许值：PLATFORM、TENANT。',
  MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `scope_key` VARCHAR(128) NOT NULL COMMENT '规范范围键的业务值。',
  MODIFY COLUMN `role_code` VARCHAR(64) NOT NULL COMMENT '角色代码的业务值。',
  MODIFY COLUMN `role_name` VARCHAR(255) NOT NULL COMMENT '角色名称的业务值。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '角色状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  MODIFY COLUMN `system_role` BOOLEAN NOT NULL COMMENT '系统角色标志，1 表示是，0 表示否。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '角色并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_role_scope`, ADD UNIQUE KEY `uk_role_scope` (`scope_key`, `role_code`) COMMENT '保证业务唯一性：scope_key、role_code',
  DROP INDEX `uk_role_owner`, ADD UNIQUE KEY `uk_role_owner` (`role_id`, `scope_key`) COMMENT '保证业务唯一性：role_id、scope_key',
  DROP INDEX `idx_role_tenant_id`, ADD KEY `idx_role_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id',
  COMMENT='保存平台级或租户级稳定角色定义';

ALTER TABLE `t_permission`
  DROP CHECK `chk_permission_positive_version`,
  MODIFY COLUMN `permission_id` BIGINT NOT NULL COMMENT '操作权限的稳定业务标识。',
  MODIFY COLUMN `permission_code` VARCHAR(128) NOT NULL COMMENT '权限代码的业务值。',
  MODIFY COLUMN `resource_type` VARCHAR(64) NOT NULL COMMENT '资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `action_code` VARCHAR(64) NOT NULL COMMENT '操作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `risk_level` VARCHAR(64) NOT NULL COMMENT '权限风险等级的稳定业务代码。允许值：LOW、MEDIUM、HIGH、CRITICAL。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '权限状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '权限并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_permission`, ADD UNIQUE KEY `uk_permission` (`permission_code`) COMMENT '保证业务唯一性：permission_code',
  DROP INDEX `uk_resource_action`, ADD UNIQUE KEY `uk_resource_action` (`resource_type`, `action_code`) COMMENT '保证业务唯一性：resource_type、action_code',
  COMMENT='保存资源类型与操作动作组成的稳定权限目录';

ALTER TABLE `t_tenant_member_role`
  DROP CHECK `chk_tenant_member_role_positive_version`,
  DROP CHECK `chk_tenant_member_role_scope`,
  DROP CHECK `chk_tenant_member_role_effective_range`,
  MODIFY COLUMN `tenant_member_role_id` BIGINT NOT NULL COMMENT '成员角色授权的稳定业务标识。',
  MODIFY COLUMN `tenant_member_id` BIGINT NOT NULL COMMENT '租户成员的稳定业务标识。',
  MODIFY COLUMN `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '授权角色的稳定业务标识。',
  MODIFY COLUMN `role_scope_key` VARCHAR(128) NOT NULL COMMENT '冻结角色范围键的业务值。',
  MODIFY COLUMN `effective_from` DATETIME(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `effective_to` DATETIME(6) NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `granted_by` BIGINT NOT NULL COMMENT '授权操作人的稳定业务标识。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '授权状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '授权并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_tenant_member_role`, ADD UNIQUE KEY `uk_tenant_member_role` (`tenant_member_id`, `role_id`, `effective_from`) COMMENT '保证业务唯一性：tenant_member_id、role_id、effective_from',
  DROP INDEX `idx_tenant_grant_member_scope`, ADD KEY `idx_tenant_grant_member_scope` (`tenant_member_id`, `tenant_id`) COMMENT '支持访问路径：tenant_member_id、tenant_id',
  DROP INDEX `idx_tenant_grant_role_scope`, ADD KEY `idx_tenant_grant_role_scope` (`role_id`, `role_scope_key`) COMMENT '支持访问路径：role_id、role_scope_key',
  COMMENT='保存租户成员被授予角色的有效期和撤销状态';

ALTER TABLE `t_role_permission`
  DROP CHECK `chk_role_permission_positive_version`,
  MODIFY COLUMN `role_permission_id` BIGINT NOT NULL COMMENT '角色权限映射的稳定业务标识。',
  MODIFY COLUMN `role_id` BIGINT NOT NULL COMMENT '授权角色的稳定业务标识。',
  MODIFY COLUMN `permission_id` BIGINT NOT NULL COMMENT '操作权限的稳定业务标识。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '角色权限状态的稳定业务代码。允许值：ACTIVE、REVOKED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '角色权限并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_role_permission`, ADD UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`) COMMENT '保证业务唯一性：role_id、permission_id',
  DROP INDEX `idx_role_permiss_permission_id`, ADD KEY `idx_role_permiss_permission_id` (`permission_id`) COMMENT '支持访问路径：permission_id',
  COMMENT='保存角色与操作权限的当前授权或撤销状态；变更由 Audit Event 追加留痕';

ALTER TABLE `t_auth_session`
  DROP CHECK `chk_auth_session_positive_version`,
  DROP CHECK `chk_auth_session_context`,
  DROP CHECK `chk_auth_session_channel`,
  DROP CHECK `chk_auth_session_method`,
  DROP CHECK `chk_auth_session_versions`,
  DROP CHECK `chk_auth_session_times`,
  DROP CHECK `chk_auth_session_status`,
  MODIFY COLUMN `session_id` BIGINT NOT NULL COMMENT '认证会话的稳定业务标识。',
  MODIFY COLUMN `account_id` BIGINT NOT NULL COMMENT '用户账号的稳定业务标识。',
  MODIFY COLUMN `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  MODIFY COLUMN `channel` VARCHAR(64) NOT NULL COMMENT '认证渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER。',
  MODIFY COLUMN `context_type` VARCHAR(64) NOT NULL COMMENT '会话上下文类型的稳定业务代码。允许值：ACCOUNT、TENANT、BOOTSTRAP。',
  MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `tenant_member_id` BIGINT NULL COMMENT '租户成员的稳定业务标识。',
  MODIFY COLUMN `authentication_method` VARCHAR(64) NOT NULL COMMENT '认证方式的稳定业务代码。允许值：PASSWORD、GOOGLE。',
  MODIFY COLUMN `account_auth_epoch` INT NOT NULL COMMENT '账号认证纪元快照的整数值。',
  MODIFY COLUMN `credential_version_snapshot` INT NOT NULL COMMENT '凭据版本快照的整数值。',
  MODIFY COLUMN `issued_time` DATETIME(6) NOT NULL COMMENT '会话签发时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `last_reauthenticated_time` DATETIME(6) NOT NULL COMMENT '最近重新认证时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `last_activity_time` DATETIME(6) NOT NULL COMMENT '最近活动时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `idle_expire_time` DATETIME(6) NOT NULL COMMENT '空闲到期时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `absolute_expire_time` DATETIME(6) NOT NULL COMMENT '绝对到期时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `current_generation_no` INT NOT NULL COMMENT '当前刷新代次的整数值。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '认证会话状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED、COMPROMISED。',
  MODIFY COLUMN `revocation_reason` VARCHAR(64) NULL COMMENT '撤销原因的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `revoked_time` DATETIME(6) NULL COMMENT '撤销时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '认证会话并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `idx_auth_session_account`, ADD KEY `idx_auth_session_account` (`account_id`, `realm`, `status`, `absolute_expire_time`) COMMENT '支持访问路径：account_id、realm、status、absolute_expire_time',
  DROP INDEX `idx_auth_session_member`, ADD KEY `idx_auth_session_member` (`tenant_member_id`, `tenant_id`, `account_id`) COMMENT '支持访问路径：tenant_member_id、tenant_id、account_id',
  DROP INDEX `idx_auth_session_expire`, ADD KEY `idx_auth_session_expire` (`status`, `idle_expire_time`, `absolute_expire_time`) COMMENT '支持访问路径：status、idle_expire_time、absolute_expire_time',
  DROP INDEX `idx_auth_session_tenant_id`, ADD KEY `idx_auth_session_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id',
  COMMENT='保存单一 Realm、Channel 和 Account/Tenant/Bootstrap 上下文的可撤销认证 Session Family';

ALTER TABLE `t_refresh_token_generation`
  DROP CHECK `chk_refresh_token_generation_positive_version`,
  DROP CHECK `chk_refresh_token_generation_refresh_generation_slot`,
  DROP CHECK `chk_refresh_token_generation_refresh_generation_versions`,
  DROP CHECK `chk_refresh_token_generation_refresh_generation_times`,
  MODIFY COLUMN `refresh_generation_id` BIGINT NOT NULL COMMENT '刷新令牌代次的稳定业务标识。',
  MODIFY COLUMN `session_id` BIGINT NOT NULL COMMENT '认证会话的稳定业务标识。',
  MODIFY COLUMN `generation_no` INT NOT NULL COMMENT '刷新代次序号的整数值。',
  MODIFY COLUMN `token_lookup_id` VARCHAR(128) NOT NULL COMMENT '令牌公开查找标识的业务值。',
  MODIFY COLUMN `token_secret_hmac` CHAR(64) NOT NULL COMMENT 'KMS 密钥对刷新令牌高熵秘密生成的 HMAC-SHA-256 小写十六进制值。',
  MODIFY COLUMN `hmac_key_version` INT NOT NULL COMMENT '刷新 HMAC 密钥版本的整数值。',
  MODIFY COLUMN `issued_time` DATETIME(6) NOT NULL COMMENT '刷新令牌签发时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `expires_time` DATETIME(6) NOT NULL COMMENT '刷新令牌到期时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '刷新令牌状态的稳定业务代码。允许值：ISSUED、CONSUMED、REVOKED、EXPIRED。',
  MODIFY COLUMN `active_slot` INT NULL COMMENT '当前刷新代次占位的整数值。',
  MODIFY COLUMN `consumed_time` DATETIME(6) NULL COMMENT '刷新令牌消费时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `replaced_by_generation_id` BIGINT NULL COMMENT '后继刷新代次的稳定业务标识。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '刷新令牌并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_refresh_lookup`, ADD UNIQUE KEY `uk_refresh_lookup` (`token_lookup_id`) COMMENT '保证业务唯一性：token_lookup_id',
  DROP INDEX `uk_refresh_session_gen`, ADD UNIQUE KEY `uk_refresh_session_gen` (`session_id`, `generation_no`) COMMENT '保证业务唯一性：session_id、generation_no',
  DROP INDEX `uk_refresh_active`, ADD UNIQUE KEY `uk_refresh_active` (`session_id`, `active_slot`) COMMENT '保证业务唯一性：session_id、active_slot',
  DROP INDEX `idx_refresh_toke_replaced_by_ge`, ADD KEY `idx_refresh_toke_replaced_by_ge` (`replaced_by_generation_id`) COMMENT '支持访问路径：replaced_by_generation_id',
  COMMENT='保存认证会话一次性 Refresh Token 的公开查找标识、秘密 HMAC、轮换代次和重用检测状态';

ALTER TABLE `t_jwt_signing_key`
  DROP CHECK `chk_jwt_signing_key_positive_version`,
  DROP CHECK `chk_jwt_signing_key_jwt_signing_key_slot`,
  DROP CHECK `chk_jwt_signing_key_jwt_signing_key_version`,
  MODIFY COLUMN `signing_key_id` BIGINT NOT NULL COMMENT 'JWT 签名密钥的稳定业务标识。',
  MODIFY COLUMN `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  MODIFY COLUMN `kid` VARCHAR(128) NOT NULL COMMENT '签名密钥公开标识的业务值。',
  MODIFY COLUMN `algorithm` VARCHAR(64) NOT NULL COMMENT '签名算法的稳定业务代码。允许值：RS256。',
  MODIFY COLUMN `kms_key_ref` VARCHAR(512) NOT NULL COMMENT 'KMS 非秘密密钥引用的业务值。',
  MODIFY COLUMN `public_jwk_schema_version` INT NOT NULL COMMENT '公钥 JWK 结构版本的整数值。',
  MODIFY COLUMN `public_jwk_canonical_json` JSON NOT NULL COMMENT '规范公钥 JWK的有界版本化 JSON 内容。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '签名密钥状态的稳定业务代码。允许值：PREPARED、ACTIVE、RETIRING、RETIRED。',
  MODIFY COLUMN `active_slot` INT NULL COMMENT '当前签名密钥占位的整数值。',
  MODIFY COLUMN `not_before` DATETIME(6) NOT NULL COMMENT '签名生效起点对应的 UTC 微秒时间。',
  MODIFY COLUMN `signing_end_time` DATETIME(6) NULL COMMENT '签名截止时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `verification_end_time` DATETIME(6) NULL COMMENT '验签截止时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '签名密钥并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_jwt_signing_realm_kid`, ADD UNIQUE KEY `uk_jwt_signing_realm_kid` (`realm`, `kid`) COMMENT '保证业务唯一性：realm、kid',
  DROP INDEX `uk_jwt_signing_active`, ADD UNIQUE KEY `uk_jwt_signing_active` (`realm`, `active_slot`) COMMENT '保证业务唯一性：realm、active_slot',
  DROP INDEX `idx_jwt_signing_verify_end`, ADD KEY `idx_jwt_signing_verify_end` (`status`, `verification_end_time`) COMMENT '支持访问路径：status、verification_end_time',
  COMMENT='保存 Realm 隔离的 RS256 JWT 签名密钥公开材料、KMS 引用和轮换状态；Canonical JSON 仅允许有界版本化 RSA 公钥 JWK';

ALTER TABLE `t_gateway_target`
  DROP CHECK `chk_gateway_target_status`,
  DROP CHECK `chk_gateway_target_version`,
  MODIFY COLUMN `target_id` BIGINT NOT NULL COMMENT 'Gateway 受控目标的稳定业务标识。',
  MODIFY COLUMN `target_service_code` VARCHAR(128) NOT NULL COMMENT '目标服务代码的业务值。',
  MODIFY COLUMN `discovery_service_name` VARCHAR(255) NOT NULL COMMENT '服务发现名称的业务值。',
  MODIFY COLUMN `channel` VARCHAR(64) NOT NULL COMMENT 'Gateway 目标渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、PROVIDER_CALLBACK。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT 'Gateway 目标状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT 'Gateway 目标并发版本的整数值。',
  MODIFY COLUMN `created_by` BIGINT NOT NULL COMMENT '创建账号的稳定业务标识。',
  MODIFY COLUMN `updated_by` BIGINT NOT NULL COMMENT '修改账号的稳定业务标识。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_gateway_target_code`, ADD UNIQUE KEY `uk_gateway_target_code` (`target_service_code`) COMMENT '保证业务唯一性：target_service_code',
  DROP INDEX `uk_gateway_target_channel`, ADD UNIQUE KEY `uk_gateway_target_channel` (`target_id`, `channel`) COMMENT '保证业务唯一性：target_id、channel',
  DROP INDEX `idx_gateway_target_status`, ADD KEY `idx_gateway_target_status` (`channel`, `status`) COMMENT '支持访问路径：channel、status',
  COMMENT='保存已通过架构与安全审批的公共入口目标当前状态；Gateway 仍以静态 Approved Target Registry 复核身份三元组';

ALTER TABLE `t_gateway_route`
  DROP CHECK `chk_gateway_route_status`,
  DROP CHECK `chk_gateway_route_version`,
  DROP CHECK `chk_gateway_route_authorization`,
  DROP CHECK `chk_gateway_route_limits`,
  MODIFY COLUMN `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  MODIFY COLUMN `route_code` VARCHAR(128) NOT NULL COMMENT 'Gateway 路由代码的业务值。',
  MODIFY COLUMN `channel` VARCHAR(64) NOT NULL COMMENT 'Gateway 路由渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、PROVIDER_CALLBACK。',
  MODIFY COLUMN `http_method` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Gateway 预编译精确 HTTP Method 集合中的 ASCII 大写方法。取值来源：Gateway 预编译精确 HTTP Method 集合；非固定枚举。',
  MODIFY COLUMN `external_path_template` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ASCII 编码、大小写敏感的规范化公共 Path Template。',
  MODIFY COLUMN `downstream_path_template` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ASCII 编码、大小写敏感的规范化目标 Path Template。',
  MODIFY COLUMN `target_id` BIGINT NOT NULL COMMENT 'Gateway 受控目标的稳定业务标识。',
  MODIFY COLUMN `security_profile_code` VARCHAR(64) NOT NULL COMMENT '安全档案代码的稳定业务代码。允许值：OPERATIONS_JWT、CLIENT_JWT、PARTNER_JWT、CLIENT_API_CREDENTIAL、CHECKOUT_SESSION、PROVIDER_CALLBACK、PUBLIC。',
  MODIFY COLUMN `authorization_mode` VARCHAR(64) NOT NULL COMMENT '授权模式的稳定业务代码。允许值：PUBLIC、HUMAN_AUTHENTICATED、HUMAN_PERMISSION、API_SCOPE、CHECKOUT_RESOURCE、PROVIDER_CALLBACK。',
  MODIFY COLUMN `permission_id` BIGINT NULL COMMENT '操作权限的稳定业务标识。',
  MODIFY COLUMN `required_scope_code` VARCHAR(128) NULL COMMENT '必需 API Scope 代码的业务值。',
  MODIFY COLUMN `request_cookie_mode` VARCHAR(64) NOT NULL COMMENT '请求 Cookie 模式的稳定业务代码。允许值：DROP、REQUIRE_PROFILE_COOKIE。',
  MODIFY COLUMN `response_cookie_mode` VARCHAR(64) NOT NULL COMMENT '响应 Cookie 模式的稳定业务代码。允许值：NONE、ISSUE_ONLY、ISSUE_OR_CLEAR、CLEAR_ONLY。',
  MODIFY COLUMN `body_limit_bytes` BIGINT NOT NULL COMMENT '请求体字节上限的长整数值。',
  MODIFY COLUMN `body_receive_timeout_millis` INT NOT NULL COMMENT '请求体接收超时毫秒数的整数值。',
  MODIFY COLUMN `response_timeout_millis` INT NOT NULL COMMENT '目标响应超时毫秒数的整数值。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT 'Gateway 路由状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT 'Gateway 路由并发版本的整数值。',
  MODIFY COLUMN `created_by` BIGINT NOT NULL COMMENT '创建账号的稳定业务标识。',
  MODIFY COLUMN `updated_by` BIGINT NOT NULL COMMENT '修改账号的稳定业务标识。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_gateway_route_code`, ADD UNIQUE KEY `uk_gateway_route_code` (`route_code`) COMMENT '保证业务唯一性：route_code',
  DROP INDEX `uk_gateway_route_path`, ADD UNIQUE KEY `uk_gateway_route_path` (`http_method`, `external_path_template`) COMMENT '保证业务唯一性：http_method、external_path_template',
  DROP INDEX `idx_gateway_route_target`, ADD KEY `idx_gateway_route_target` (`target_id`, `channel`, `status`) COMMENT '支持访问路径：target_id、channel、status',
  DROP INDEX `idx_gateway_route_channel`, ADD KEY `idx_gateway_route_channel` (`channel`, `status`, `target_id`) COMMENT '支持访问路径：channel、status、target_id',
  DROP INDEX `idx_gateway_route_permission`, ADD KEY `idx_gateway_route_permission` (`permission_id`) COMMENT '支持访问路径：permission_id',
  COMMENT='保存六类外部 HTTP 入口的当前 Route、受控 Target、安全档案、授权模式和传输上限，不保存运行时计数或发布版本';

ALTER TABLE `t_gateway_route_header`
  MODIFY COLUMN `route_header_id` BIGINT NOT NULL COMMENT 'Gateway 路由 Header 的稳定业务标识。',
  MODIFY COLUMN `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  MODIFY COLUMN `header_name` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'ASCII HTTP field-name Token；大小写不敏感唯一，Platform/Gateway 仍执行完整 Allowlist 校验。取值来源：RFC 9110 HTTP field-name token 与 Platform/Gateway Allowlist；非固定枚举。',
  MODIFY COLUMN `header_use` VARCHAR(64) NOT NULL COMMENT 'Header 用途的稳定业务代码。允许值：CORS_REQUEST_ALLOWED、FORWARD_REQUEST。',
  DROP INDEX `uk_gateway_route_header`, ADD UNIQUE KEY `uk_gateway_route_header` (`route_id`, `header_name`, `header_use`) COMMENT '保证业务唯一性：route_id、header_name、header_use',
  COMMENT='保存 Route 当前允许的完整 Header 名称及用途；聚合更新时整体替换，不开放通配、前缀或可信身份 Header';

ALTER TABLE `t_gateway_route_query_param`
  MODIFY COLUMN `route_query_param_id` BIGINT NOT NULL COMMENT 'Gateway 路由查询参数的稳定业务标识。',
  MODIFY COLUMN `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  MODIFY COLUMN `parameter_name` VARCHAR(255) NOT NULL COMMENT '查询参数名称的业务值。',
  DROP INDEX `uk_gateway_route_query`, ADD UNIQUE KEY `uk_gateway_route_query` (`route_id`, `parameter_name`) COMMENT '保证业务唯一性：route_id、parameter_name',
  COMMENT='保存 Route 当前允许的完整 Query Parameter 名称；未声明名称由 Gateway 在调用目标前拒绝';

ALTER TABLE `t_gateway_guard_rule`
  DROP CHECK `chk_gateway_guard_rule_gateway_guard_shape`,
  DROP CHECK `chk_gateway_guard_rule_gateway_guard_positive`,
  MODIFY COLUMN `guard_rule_id` BIGINT NOT NULL COMMENT 'Gateway 路由保护规则的稳定业务标识。',
  MODIFY COLUMN `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  MODIFY COLUMN `rule_type` VARCHAR(64) NOT NULL COMMENT '保护规则类型的稳定业务代码。允许值：RATE_LIMIT、CONCURRENCY。',
  MODIFY COLUMN `dimension` VARCHAR(64) NOT NULL COMMENT '保护规则维度的稳定业务代码。允许值：SHARED_ROUTE、CLIENT_IP、AUTHENTICATED_PRINCIPAL、TENANT。',
  MODIFY COLUMN `capacity` BIGINT NULL COMMENT '令牌容量的长整数值。',
  MODIFY COLUMN `refill_tokens` BIGINT NULL COMMENT '补充令牌数的长整数值。',
  MODIFY COLUMN `refill_period_millis` BIGINT NULL COMMENT '补充周期毫秒数的长整数值。',
  MODIFY COLUMN `concurrency_limit` BIGINT NULL COMMENT '并发上限的长整数值。',
  DROP INDEX `uk_gateway_guard_rule`, ADD UNIQUE KEY `uk_gateway_guard_rule` (`route_id`, `rule_type`, `dimension`) COMMENT '保证业务唯一性：route_id、rule_type、dimension',
  COMMENT='保存 Route 当前统一的 Rate 或 Concurrency 参数；Redis 只保存运行状态，Tenant 维度使用可信租户身份分桶';

ALTER TABLE `t_audit_chain`
  DROP CHECK `chk_audit_chain_positive_version`,
  MODIFY COLUMN `chain_id` BIGINT NOT NULL COMMENT '审计哈希链的稳定业务标识。',
  MODIFY COLUMN `chain_key` VARCHAR(128) NOT NULL COMMENT '审计链键的业务值。',
  MODIFY COLUMN `partition_date` DATE NOT NULL COMMENT '该审计链所属的 UTC 日历日期。',
  MODIFY COLUMN `shard_no` INT NOT NULL COMMENT '审计分片序号的整数值。',
  MODIFY COLUMN `previous_partition_hash` CHAR(64) NULL COMMENT '前分区终止哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `last_sequence_no` INT NOT NULL COMMENT '最后审计序号的整数值。',
  MODIFY COLUMN `last_event_hash` CHAR(64) NULL COMMENT '最后审计事件哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '审计链状态的稳定业务代码。允许值：OPEN、SEALED、ARCHIVED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '审计链并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_audit_chain_key`, ADD UNIQUE KEY `uk_audit_chain_key` (`chain_key`) COMMENT '保证业务唯一性：chain_key',
  DROP INDEX `uk_audit_chain`, ADD UNIQUE KEY `uk_audit_chain` (`partition_date`, `shard_no`) COMMENT '保证业务唯一性：partition_date、shard_no',
  COMMENT='保存日期与 Shard 级审计 Hash Chain 的尾指针和跨分区连接摘要';

ALTER TABLE `t_audit_event`
  DROP CHECK `chk_audit_event_audit_sequence`,
  MODIFY COLUMN `audit_event_id` BIGINT NOT NULL COMMENT '审计事件的稳定业务标识。',
  MODIFY COLUMN `chain_id` BIGINT NOT NULL COMMENT '审计链的稳定业务标识。',
  MODIFY COLUMN `sequence_no` INT NOT NULL COMMENT '审计序号的整数值。',
  MODIFY COLUMN `previous_event_hash` CHAR(64) NULL COMMENT '前审计事件哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `canonical_version` INT NOT NULL COMMENT '规范化算法版本的整数值。',
  MODIFY COLUMN `attribute_root_hash` CHAR(64) NOT NULL COMMENT '审计属性根哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `event_hash` CHAR(64) NOT NULL COMMENT '审计事件哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `source_system` VARCHAR(64) NULL COMMENT '来源系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  MODIFY COLUMN `source_event_key` VARCHAR(128) NULL COMMENT '来源事件键的业务值。',
  MODIFY COLUMN `source_version` INT NULL COMMENT '来源版本的整数值。',
  MODIFY COLUMN `source_payload_hash` CHAR(64) NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `schema_version` INT NOT NULL COMMENT '审计结构版本的整数值。',
  MODIFY COLUMN `actor_type` VARCHAR(64) NOT NULL COMMENT '操作主体类型的稳定业务代码。允许值：USER、CREDENTIAL、WORKLOAD、SYSTEM。',
  MODIFY COLUMN `actor_id` VARCHAR(128) NOT NULL COMMENT '操作主体标识的业务值。',
  MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `action_code` VARCHAR(64) NOT NULL COMMENT '审计动作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `resource_system` VARCHAR(64) NOT NULL COMMENT '资源所有系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  MODIFY COLUMN `resource_type` VARCHAR(64) NOT NULL COMMENT '资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `resource_id` VARCHAR(128) NOT NULL COMMENT '资源标识的业务值。',
  MODIFY COLUMN `result` VARCHAR(64) NOT NULL COMMENT '审计结果的稳定业务代码。允许值：SUCCEEDED、DENIED、FAILED。',
  MODIFY COLUMN `request_id` VARCHAR(128) NOT NULL COMMENT '请求标识的业务值。',
  MODIFY COLUMN `trace_id` VARCHAR(128) NOT NULL COMMENT '链路标识的业务值。',
  MODIFY COLUMN `occurred_time` DATETIME(6) NOT NULL COMMENT '审计发生时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `received_time` DATETIME(6) NOT NULL COMMENT '审计接收时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `payload_hash` CHAR(64) NOT NULL COMMENT '本地审计载荷哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_audit_source`, ADD UNIQUE KEY `uk_audit_source` (`source_system`, `source_event_key`) COMMENT '保证业务唯一性：source_system、source_event_key',
  DROP INDEX `uk_audit_sequence`, ADD UNIQUE KEY `uk_audit_sequence` (`chain_id`, `sequence_no`) COMMENT '保证业务唯一性：chain_id、sequence_no',
  DROP INDEX `uk_audit_event_hash`, ADD UNIQUE KEY `uk_audit_event_hash` (`event_hash`) COMMENT '保证业务唯一性：event_hash',
  DROP INDEX `idx_audit_resource`, ADD KEY `idx_audit_resource` (`resource_system`, `resource_type`, `resource_id`, `occurred_time`) COMMENT '支持访问路径：resource_system、resource_type、resource_id、occurred_time',
  DROP INDEX `idx_audit_actor`, ADD KEY `idx_audit_actor` (`actor_type`, `actor_id`, `occurred_time`) COMMENT '支持访问路径：actor_type、actor_id、occurred_time',
  DROP INDEX `idx_audit_event_tenant_id`, ADD KEY `idx_audit_event_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id',
  COMMENT='不可变保存操作、文件访问和敏感 Reveal 的规范化可验证审计事实';

ALTER TABLE `t_audit_event_attr`
  DROP CHECK `chk_audit_event_attr_audit_attr_value`,
  MODIFY COLUMN `attr_id` BIGINT NOT NULL COMMENT '审计事件属性的稳定业务标识。',
  MODIFY COLUMN `audit_event_id` BIGINT NOT NULL COMMENT '审计事件的稳定业务标识。',
  MODIFY COLUMN `attr_no` INT NOT NULL COMMENT '审计属性序号的整数值。',
  MODIFY COLUMN `attr_key` VARCHAR(128) NOT NULL COMMENT '审计属性键的业务值。',
  MODIFY COLUMN `value_text` VARCHAR(1000) NULL COMMENT '审计属性文本值的业务值。',
  MODIFY COLUMN `value_vault_token` VARCHAR(255) NULL COMMENT '审计属性 Vault Token的业务值。',
  MODIFY COLUMN `value_masked` VARCHAR(1000) NULL COMMENT '审计属性脱敏值的业务值。',
  MODIFY COLUMN `value_hash` CHAR(64) NOT NULL COMMENT '审计属性值哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `classification` VARCHAR(64) NOT NULL COMMENT '审计属性分类的稳定业务代码。允许值：PUBLIC、INTERNAL、CONFIDENTIAL、RESTRICTED。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_audit_attr`, ADD UNIQUE KEY `uk_audit_attr` (`audit_event_id`, `attr_no`) COMMENT '保证业务唯一性：audit_event_id、attr_no',
  DROP INDEX `idx_audit_attr_key`, ADD KEY `idx_audit_attr_key` (`attr_key`, `value_hash`) COMMENT '支持访问路径：attr_key、value_hash',
  COMMENT='不可变保存公开脱敏标量或受 Vault 保护敏感值的严格互斥属性';

ALTER TABLE `t_security_event`
  DROP CHECK `chk_security_event_security_source`,
  MODIFY COLUMN `security_event_id` BIGINT NOT NULL COMMENT '平台安全事件的稳定业务标识。',
  MODIFY COLUMN `event_type` VARCHAR(64) NOT NULL COMMENT '安全事件类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `severity` VARCHAR(64) NOT NULL COMMENT '安全事件严重级别的稳定业务代码。允许值：INFO、LOW、MEDIUM、HIGH、CRITICAL。',
  MODIFY COLUMN `actor_ref` VARCHAR(128) NULL COMMENT '安全主体引用的业务值。',
  MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  MODIFY COLUMN `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  MODIFY COLUMN `source_event_key` VARCHAR(128) NOT NULL COMMENT '来源事件键的业务值。',
  MODIFY COLUMN `source_version` INT NOT NULL COMMENT '来源版本的整数值。',
  MODIFY COLUMN `source_payload_hash` CHAR(64) NOT NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `schema_version` INT NOT NULL COMMENT '安全事件结构版本的整数值。',
  MODIFY COLUMN `event_hash` CHAR(64) NOT NULL COMMENT '安全事件哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `occurred_time` DATETIME(6) NOT NULL COMMENT '安全事件发生时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_security_event`, ADD UNIQUE KEY `uk_security_event` (`event_hash`) COMMENT '保证业务唯一性：event_hash',
  DROP INDEX `uk_security_source`, ADD UNIQUE KEY `uk_security_source` (`source_system`, `source_event_key`, `event_type`) COMMENT '保证业务唯一性：source_system、source_event_key、event_type',
  DROP INDEX `idx_security_event_severity`, ADD KEY `idx_security_event_severity` (`severity`, `occurred_time`) COMMENT '支持访问路径：severity、occurred_time',
  DROP INDEX `idx_security_event_tenant_id`, ADD KEY `idx_security_event_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id',
  COMMENT='不可变保存身份、权限、Credential、OTP、Webhook 和文件安全事件';

ALTER TABLE `t_command_idempotency`
  DROP CHECK `chk_command_idempotency_command_response`,
  MODIFY COLUMN `id` BIGINT NOT NULL COMMENT '命令幂等记录的稳定业务标识。',
  MODIFY COLUMN `caller_scope` CHAR(64) NOT NULL COMMENT '规范调用方范围哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `operation_type` VARCHAR(64) NOT NULL COMMENT '幂等操作类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  MODIFY COLUMN `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键的业务值。',
  MODIFY COLUMN `request_hash` CHAR(64) NOT NULL COMMENT '幂等请求哈希的 SHA-256 小写十六进制值。',
  MODIFY COLUMN `response_ref` VARCHAR(255) NULL COMMENT '幂等响应引用的业务值。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '幂等执行状态的稳定业务代码。允许值：PROCESSING、SUCCEEDED、FAILED。',
  MODIFY COLUMN `expire_time` DATETIME(6) NOT NULL COMMENT '幂等记录到期时间对应的 UTC 微秒时间。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_command_idempotency`, ADD UNIQUE KEY `uk_command_idempotency` (`caller_scope`, `operation_type`, `idempotency_key`) COMMENT '保证业务唯一性：caller_scope、operation_type、idempotency_key',
  DROP INDEX `idx_command_idem_expire`, ADD KEY `idx_command_idem_expire` (`status`, `expire_time`) COMMENT '支持访问路径：status、expire_time',
  COMMENT='保存认证调用边界内 HTTP 或内部命令的请求摘要与稳定响应引用';

ALTER TABLE `t_console_resource`
  DROP CHECK `chk_console_resource_positive_version`,
  DROP CHECK `chk_console_resource_shape`,
  DROP CHECK `chk_console_resource_status`,
  MODIFY COLUMN `resource_id` BIGINT NOT NULL COMMENT '控制台界面资源的稳定业务标识。',
  MODIFY COLUMN `resource_code` VARCHAR(128) NOT NULL COMMENT '控制台资源代码的业务值。',
  MODIFY COLUMN `channel` VARCHAR(64) NOT NULL COMMENT '控制台渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER。',
  MODIFY COLUMN `resource_type` VARCHAR(64) NOT NULL COMMENT '控制台资源类型的稳定业务代码。允许值：MENU、BUTTON。',
  MODIFY COLUMN `parent_resource_id` BIGINT NULL COMMENT '父控制台资源的稳定业务标识。',
  MODIFY COLUMN `display_name` VARCHAR(255) NOT NULL COMMENT '展示名称的业务值。',
  MODIFY COLUMN `frontend_route` VARCHAR(1000) NULL COMMENT '前端相对路由的业务值。',
  MODIFY COLUMN `component_key` VARCHAR(255) NULL COMMENT '前端组件注册键的业务值。',
  MODIFY COLUMN `permission_id` BIGINT NOT NULL COMMENT '可见性权限的稳定业务标识。',
  MODIFY COLUMN `sort_order` INT NOT NULL COMMENT '同级排序序号的整数值。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '控制台资源状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  MODIFY COLUMN `version` INT NOT NULL COMMENT '控制台资源并发版本的整数值。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  MODIFY COLUMN `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  DROP INDEX `uk_console_resource_code`, ADD UNIQUE KEY `uk_console_resource_code` (`resource_code`) COMMENT '保证业务唯一性：resource_code',
  DROP INDEX `uk_console_resource_channel`, ADD UNIQUE KEY `uk_console_resource_channel` (`resource_id`, `channel`) COMMENT '保证业务唯一性：resource_id、channel',
  DROP INDEX `idx_console_resource_parent`, ADD KEY `idx_console_resource_parent` (`parent_resource_id`, `channel`, `sort_order`) COMMENT '支持访问路径：parent_resource_id、channel、sort_order',
  DROP INDEX `idx_console_resource_permission`, ADD KEY `idx_console_resource_permission` (`permission_id`) COMMENT '支持访问路径：permission_id',
  DROP INDEX `idx_console_resource_channel`, ADD KEY `idx_console_resource_channel` (`channel`, `status`, `parent_resource_id`, `sort_order`) COMMENT '支持访问路径：channel、status、parent_resource_id、sort_order',
  COMMENT='保存 Operations、Client、Partner Console 的当前 Menu/Button 导航资源；可见性复用 Permission，但不替代 BFF 授权';

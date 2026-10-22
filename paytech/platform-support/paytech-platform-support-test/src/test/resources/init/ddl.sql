-- 仅供人工审阅并初始化 local-environment 的空本地 Schema；应用和测试绝不自动执行。
-- 完整表定义原样摘自 docs/database/sql/platform-support-schema.sql；已有库勿重复建表或重置。
-- platform_app 为 local-environment 既有本地账号；真实/共享环境不得使用本文件。
DROP DATABASE mor_platform;
CREATE DATABASE IF NOT EXISTS mor_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'platform_app'@'%' IDENTIFIED BY 'local-platform-change-me-2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_platform.* TO 'platform_app'@'%';
USE mor_platform;

CREATE TABLE `t_user_account` (
  `account_id` BIGINT NOT NULL COMMENT '人工用户账号的稳定业务标识。',
  `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `account_type` VARCHAR(64) NOT NULL COMMENT '账号类型的稳定业务代码。允许值：HUMAN、BOOTSTRAP_ADMIN。',
  `login_name` VARCHAR(64) NULL COMMENT '非邮箱登录名的业务值。',
  `email_vault_token` VARCHAR(255) NULL COMMENT '登录邮箱 Vault Token的业务值。',
  `email_masked` VARCHAR(255) NULL COMMENT '登录邮箱脱敏值的业务值。',
  `email_verified_time` DATETIME(6) NULL COMMENT '邮箱验证时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '账号状态的稳定业务代码。允许值：PENDING_VERIFICATION、ACTIVE、DISABLED、CLOSED。',
  `consecutive_password_failures` INT NOT NULL DEFAULT 0 COMMENT '当前账号连续密码校验失败的累计次数。',
  `locked_until` DATETIME(6) NULL COMMENT '临时锁定截止时间对应的 UTC 微秒时间。',
  `last_password_failure_time` DATETIME(6) NULL COMMENT '最近密码失败时间对应的 UTC 微秒时间。',
  `credential_version` INT NOT NULL COMMENT '账号凭据版本的整数值。',
  `auth_epoch` INT NOT NULL COMMENT '账号认证纪元的整数值。',
  `version` INT NOT NULL COMMENT '账号并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_account_realm` (`account_id`, `realm`) COMMENT '保证业务唯一性：account_id、realm',
  UNIQUE KEY `uk_user_account_email` (`realm`, `email_vault_token`) COMMENT '保证业务唯一性：realm、email_vault_token',
  UNIQUE KEY `uk_user_account_login` (`realm`, `login_name`) COMMENT '保证业务唯一性：realm、login_name'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 OPERATIONS 或 CUSTOMER Realm 的全局人工登录账号、锁定状态和认证撤销版本';

CREATE TABLE `t_password_credential` (
  `password_credential_id` BIGINT NOT NULL COMMENT '密码凭据的稳定业务标识。',
  `account_id` BIGINT NOT NULL COMMENT '用户账号的稳定业务标识。',
  `password_verifier_phc` VARCHAR(512) NOT NULL COMMENT '密码 PHC 校验值的业务值。',
  `algorithm_code` VARCHAR(64) NOT NULL COMMENT '密码算法的稳定业务代码。允许值：ARGON2ID。',
  `algorithm_version` INT NOT NULL COMMENT '密码算法版本的整数值。',
  `memory_kib` INT NOT NULL COMMENT 'Argon2 内存参数的整数值。',
  `iterations` INT NOT NULL COMMENT 'Argon2 迭代次数的整数值。',
  `parallelism` INT NOT NULL COMMENT 'Argon2 并行度的整数值。',
  `credential_version` INT NOT NULL COMMENT '密码凭据版本的整数值。',
  `force_change_flag` BOOLEAN NOT NULL COMMENT '强制改密标志，1 表示是，0 表示否。',
  `status` VARCHAR(64) NOT NULL COMMENT '密码凭据状态的稳定业务代码。允许值：ACTIVE、REVOKED。',
  `password_changed_time` DATETIME(6) NOT NULL COMMENT '密码变更时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '密码凭据并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`password_credential_id`),
  UNIQUE KEY `uk_password_account` (`account_id`) COMMENT '保证业务唯一性：account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存账号唯一的当前 Argon2id PHC 校验值、算法参数和强制改密状态；Google-only 账号不创建占位行';

CREATE TABLE `t_access_tenant` (
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `tenant_type` VARCHAR(64) NOT NULL COMMENT '租户类型的稳定业务代码。允许值：CLIENT、PARTNER、INTERNAL。',
  `owner_ref_id` BIGINT NOT NULL COMMENT '租户所有者引用的稳定业务标识。',
  `tenant_name` VARCHAR(255) NOT NULL COMMENT '租户显示名称的业务值。',
  `owner_status` VARCHAR(64) NOT NULL COMMENT '所有者业务状态的稳定业务代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。',
  `source_version` INT NULL COMMENT '来源投影版本的整数值。',
  `source_payload_hash` CHAR(64) NULL COMMENT '来源投影载荷哈希的 SHA-256 小写十六进制值。',
  `security_status` VARCHAR(64) NOT NULL COMMENT '平台安全状态的稳定业务代码。允许值：ACTIVE、SUSPENDED。',
  `version` INT NOT NULL COMMENT '租户并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`tenant_id`),
  UNIQUE KEY `uk_access_tenant` (`tenant_type`, `owner_ref_id`) COMMENT '保证业务唯一性：tenant_type、owner_ref_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Client、Local Partner 或内部运营的访问隔离边界';

CREATE TABLE `t_tenant_member` (
  `member_id` BIGINT NOT NULL COMMENT '租户成员的稳定业务标识。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `account_id` BIGINT NOT NULL COMMENT '用户账号的稳定业务标识。',
  `member_type` VARCHAR(64) NOT NULL COMMENT '成员类型的稳定业务代码。允许值：OWNER、ADMIN、MEMBER。',
  `status` VARCHAR(64) NOT NULL COMMENT '成员状态的稳定业务代码。允许值：ACTIVE、SUSPENDED、REVOKED。',
  `invited_by` BIGINT NULL COMMENT '邀请操作成员的稳定业务标识。',
  `version` INT NOT NULL COMMENT '成员并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_tenant_member` (`tenant_id`, `account_id`) COMMENT '保证业务唯一性：tenant_id、account_id',
  UNIQUE KEY `uk_tenant_member_owner` (`member_id`, `tenant_id`) COMMENT '保证业务唯一性：member_id、tenant_id',
  UNIQUE KEY `uk_tenant_member_identity` (`member_id`, `tenant_id`, `account_id`) COMMENT '保证业务唯一性：member_id、tenant_id、account_id',
  KEY `idx_tenant_member_inviter` (`invited_by`, `tenant_id`) COMMENT '支持访问路径：invited_by、tenant_id',
  KEY `idx_tenant_member_account_id` (`account_id`) COMMENT '支持访问路径：account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存全局用户账号加入访问租户后的独立授权状态；Realm、Tenant 类型和当前状态一致性由事务规则锁定';

CREATE TABLE `t_role` (
  `role_id` BIGINT NOT NULL COMMENT '授权角色的稳定业务标识。',
  `tenant_scope` VARCHAR(64) NOT NULL COMMENT '租户范围类型的稳定业务代码。允许值：PLATFORM、TENANT。',
  `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  `scope_key` VARCHAR(128) NOT NULL COMMENT '规范范围键的业务值。',
  `role_code` VARCHAR(64) NOT NULL COMMENT '角色代码的业务值。',
  `role_name` VARCHAR(255) NOT NULL COMMENT '角色名称的业务值。',
  `status` VARCHAR(64) NOT NULL COMMENT '角色状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  `system_role` BOOLEAN NOT NULL COMMENT '系统角色标志，1 表示是，0 表示否。',
  `version` INT NOT NULL COMMENT '角色并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_scope` (`scope_key`, `role_code`) COMMENT '保证业务唯一性：scope_key、role_code',
  UNIQUE KEY `uk_role_owner` (`role_id`, `scope_key`) COMMENT '保证业务唯一性：role_id、scope_key',
  KEY `idx_role_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存平台级或租户级稳定角色定义';

CREATE TABLE `t_permission` (
  `permission_id` BIGINT NOT NULL COMMENT '操作权限的稳定业务标识。',
  `permission_code` VARCHAR(128) NOT NULL COMMENT '权限代码的业务值。',
  `resource_type` VARCHAR(64) NOT NULL COMMENT '资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `action_code` VARCHAR(64) NOT NULL COMMENT '操作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `risk_level` VARCHAR(64) NOT NULL COMMENT '权限风险等级的稳定业务代码。允许值：LOW、MEDIUM、HIGH、CRITICAL。',
  `status` VARCHAR(64) NOT NULL COMMENT '权限状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  `version` INT NOT NULL COMMENT '权限并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`permission_id`),
  UNIQUE KEY `uk_permission` (`permission_code`) COMMENT '保证业务唯一性：permission_code',
  UNIQUE KEY `uk_resource_action` (`resource_type`, `action_code`) COMMENT '保证业务唯一性：resource_type、action_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存资源类型与操作动作组成的稳定权限目录';

CREATE TABLE `t_tenant_member_role` (
  `tenant_member_role_id` BIGINT NOT NULL COMMENT '成员角色授权的稳定业务标识。',
  `tenant_member_id` BIGINT NOT NULL COMMENT '租户成员的稳定业务标识。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `role_id` BIGINT NOT NULL COMMENT '授权角色的稳定业务标识。',
  `role_scope_key` VARCHAR(128) NOT NULL COMMENT '冻结角色范围键的业务值。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `granted_by` BIGINT NOT NULL COMMENT '授权操作人的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '授权状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  `version` INT NOT NULL COMMENT '授权并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`tenant_member_role_id`),
  UNIQUE KEY `uk_tenant_member_role` (`tenant_member_id`, `role_id`, `effective_from`) COMMENT '保证业务唯一性：tenant_member_id、role_id、effective_from',
  KEY `idx_tenant_grant_member_scope` (`tenant_member_id`, `tenant_id`) COMMENT '支持访问路径：tenant_member_id、tenant_id',
  KEY `idx_tenant_grant_role_scope` (`role_id`, `role_scope_key`) COMMENT '支持访问路径：role_id、role_scope_key'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存租户成员被授予角色的有效期和撤销状态';

CREATE TABLE `t_role_permission` (
  `role_permission_id` BIGINT NOT NULL COMMENT '角色权限映射的稳定业务标识。',
  `role_id` BIGINT NOT NULL COMMENT '授权角色的稳定业务标识。',
  `permission_id` BIGINT NOT NULL COMMENT '操作权限的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '角色权限状态的稳定业务代码。允许值：ACTIVE、REVOKED。',
  `version` INT NOT NULL COMMENT '角色权限并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`role_permission_id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`) COMMENT '保证业务唯一性：role_id、permission_id',
  KEY `idx_role_permiss_permission_id` (`permission_id`) COMMENT '支持访问路径：permission_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存角色与操作权限的当前授权或撤销状态；变更由 Audit Event 追加留痕';

CREATE TABLE `t_auth_session` (
  `session_id` BIGINT NOT NULL COMMENT '认证会话的稳定业务标识。',
  `account_id` BIGINT NOT NULL COMMENT '用户账号的稳定业务标识。',
  `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `channel` VARCHAR(64) NOT NULL COMMENT '认证渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER。',
  `context_type` VARCHAR(64) NOT NULL COMMENT '会话上下文类型的稳定业务代码。允许值：ACCOUNT、TENANT、BOOTSTRAP。',
  `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  `tenant_member_id` BIGINT NULL COMMENT '租户成员的稳定业务标识。',
  `authentication_method` VARCHAR(64) NOT NULL COMMENT '认证方式的稳定业务代码。允许值：PASSWORD、GOOGLE。',
  `account_auth_epoch` INT NOT NULL COMMENT '账号认证纪元快照的整数值。',
  `credential_version_snapshot` INT NOT NULL COMMENT '凭据版本快照的整数值。',
  `issued_time` DATETIME(6) NOT NULL COMMENT '会话签发时间对应的 UTC 微秒时间。',
  `last_reauthenticated_time` DATETIME(6) NOT NULL COMMENT '最近重新认证时间对应的 UTC 微秒时间。',
  `last_activity_time` DATETIME(6) NOT NULL COMMENT '最近活动时间对应的 UTC 微秒时间。',
  `idle_expire_time` DATETIME(6) NOT NULL COMMENT '空闲到期时间对应的 UTC 微秒时间。',
  `absolute_expire_time` DATETIME(6) NOT NULL COMMENT '绝对到期时间对应的 UTC 微秒时间。',
  `current_generation_no` INT NOT NULL COMMENT '当前刷新代次的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '认证会话状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED、COMPROMISED。',
  `revocation_reason` VARCHAR(64) NULL COMMENT '撤销原因的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `revoked_time` DATETIME(6) NULL COMMENT '撤销时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '认证会话并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`session_id`),
  KEY `idx_auth_session_account` (`account_id`, `realm`, `status`, `absolute_expire_time`) COMMENT '支持访问路径：account_id、realm、status、absolute_expire_time',
  KEY `idx_auth_session_member` (`tenant_member_id`, `tenant_id`, `account_id`) COMMENT '支持访问路径：tenant_member_id、tenant_id、account_id',
  KEY `idx_auth_session_expire` (`status`, `idle_expire_time`, `absolute_expire_time`) COMMENT '支持访问路径：status、idle_expire_time、absolute_expire_time',
  KEY `idx_auth_session_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存单一 Realm、Channel 和 Account/Tenant/Bootstrap 上下文的可撤销认证 Session Family';

CREATE TABLE `t_refresh_token_generation` (
  `refresh_generation_id` BIGINT NOT NULL COMMENT '刷新令牌代次的稳定业务标识。',
  `session_id` BIGINT NOT NULL COMMENT '认证会话的稳定业务标识。',
  `generation_no` INT NOT NULL COMMENT '刷新代次序号的整数值。',
  `token_lookup_id` VARCHAR(128) NOT NULL COMMENT '令牌公开查找标识的业务值。',
  `token_secret_hmac` CHAR(64) NOT NULL COMMENT 'KMS 密钥对刷新令牌高熵秘密生成的 HMAC-SHA-256 小写十六进制值。',
  `hmac_key_version` INT NOT NULL COMMENT '刷新 HMAC 密钥版本的整数值。',
  `issued_time` DATETIME(6) NOT NULL COMMENT '刷新令牌签发时间对应的 UTC 微秒时间。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '刷新令牌到期时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '刷新令牌状态的稳定业务代码。允许值：ISSUED、CONSUMED、REVOKED、EXPIRED。',
  `active_slot` INT NULL COMMENT '当前刷新代次占位的整数值。',
  `consumed_time` DATETIME(6) NULL COMMENT '刷新令牌消费时间对应的 UTC 微秒时间。',
  `replaced_by_generation_id` BIGINT NULL COMMENT '后继刷新代次的稳定业务标识。',
  `version` INT NOT NULL COMMENT '刷新令牌并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`refresh_generation_id`),
  UNIQUE KEY `uk_refresh_lookup` (`token_lookup_id`) COMMENT '保证业务唯一性：token_lookup_id',
  UNIQUE KEY `uk_refresh_session_gen` (`session_id`, `generation_no`) COMMENT '保证业务唯一性：session_id、generation_no',
  UNIQUE KEY `uk_refresh_active` (`session_id`, `active_slot`) COMMENT '保证业务唯一性：session_id、active_slot',
  KEY `idx_refresh_toke_replaced_by_ge` (`replaced_by_generation_id`) COMMENT '支持访问路径：replaced_by_generation_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存认证会话一次性 Refresh Token 的公开查找标识、秘密 HMAC、轮换代次和重用检测状态';

CREATE TABLE `t_jwt_signing_key` (
  `signing_key_id` BIGINT NOT NULL COMMENT 'JWT 签名密钥的稳定业务标识。',
  `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `kid` VARCHAR(128) NOT NULL COMMENT '签名密钥公开标识的业务值。',
  `algorithm` VARCHAR(64) NOT NULL COMMENT '签名算法的稳定业务代码。允许值：RS256。',
  `kms_key_ref` VARCHAR(512) NOT NULL COMMENT 'KMS 非秘密密钥引用的业务值。',
  `public_jwk_schema_version` INT NOT NULL COMMENT '公钥 JWK 结构版本的整数值。',
  `public_jwk_canonical_json` JSON NOT NULL COMMENT '规范公钥 JWK的有界版本化 JSON 内容。',
  `status` VARCHAR(64) NOT NULL COMMENT '签名密钥状态的稳定业务代码。允许值：PREPARED、ACTIVE、RETIRING、RETIRED。',
  `active_slot` INT NULL COMMENT '当前签名密钥占位的整数值。',
  `not_before` DATETIME(6) NOT NULL COMMENT '签名生效起点对应的 UTC 微秒时间。',
  `signing_end_time` DATETIME(6) NULL COMMENT '签名截止时间对应的 UTC 微秒时间。',
  `verification_end_time` DATETIME(6) NULL COMMENT '验签截止时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '签名密钥并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`signing_key_id`),
  UNIQUE KEY `uk_jwt_signing_realm_kid` (`realm`, `kid`) COMMENT '保证业务唯一性：realm、kid',
  UNIQUE KEY `uk_jwt_signing_active` (`realm`, `active_slot`) COMMENT '保证业务唯一性：realm、active_slot',
  KEY `idx_jwt_signing_verify_end` (`status`, `verification_end_time`) COMMENT '支持访问路径：status、verification_end_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Realm 隔离的 RS256 JWT 签名密钥公开材料、KMS 引用和轮换状态；Canonical JSON 仅允许有界版本化 RSA 公钥 JWK';

CREATE TABLE `t_api_credential` (
  `credential_id` BIGINT NOT NULL COMMENT '客户端接口凭证的稳定业务标识。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `key_prefix` VARCHAR(32) NOT NULL COMMENT '密钥前缀的业务值。',
  `credential_name` VARCHAR(64) NULL COMMENT '客户为凭证填写的显示名称；历史凭证为空。',
  `key_verifier_phc` VARCHAR(512) NOT NULL COMMENT '密钥 PHC 校验值的业务值。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '凭证到期时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '凭证状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  `rotated_from_id` BIGINT NULL COMMENT '轮换来源凭证的稳定业务标识。',
  `version` INT NOT NULL COMMENT '凭证并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`credential_id`),
  UNIQUE KEY `uk_key_prefix` (`key_prefix`) COMMENT '保证业务唯一性：key_prefix',
  KEY `idx_api_credential_tenant` (`tenant_id`, `status`, `expires_time`) COMMENT '支持访问路径：tenant_id、status、expires_time',
  KEY `idx_api_credenti_rotated_from_i` (`rotated_from_id`) COMMENT '支持访问路径：rotated_from_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 CLIENT Tenant API Key 的有界查找前缀、PHC 校验值和生命周期；Scope 行是唯一授权来源';

CREATE TABLE `t_api_scope` (
  `api_scope_id` BIGINT NOT NULL COMMENT 'API Scope 目录的服务侧 Snowflake 主键；与凭证授权明细 scope_id 不同。',
  `scope_code` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '机器 API 权限的稳定小写 ASCII 编码，取值由本目录管理；创建后不可修改或回收复用，Route 与授权记录按此编码引用。',
  `display_name` VARCHAR(255) NOT NULL COMMENT 'API Scope 的非空展示名称，可由获授权运营人员修改，不参与认证匹配。',
  `description` VARCHAR(500) NULL COMMENT 'API Scope 的业务能力说明；未填写时为 NULL，不保存秘密、凭证值或客户资料。',
  `version` INT NOT NULL COMMENT '目录记录的乐观并发版本，创建为 1；每次成功修改递增 1，不表示历史业务版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`api_scope_id`),
  UNIQUE KEY `uk_api_scope_code` (`scope_code`) COMMENT '保证业务唯一性：scope_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存机器 API Scope 的全局定义和展示说明；不保存凭证授权、目录状态或版本历史';

CREATE TABLE `t_api_credential_scope` (
  `scope_id` BIGINT NOT NULL COMMENT '接口凭证范围的稳定业务标识。',
  `credential_id` BIGINT NOT NULL COMMENT '接口凭证的稳定业务标识。',
  `scope_code` VARCHAR(128) NOT NULL COMMENT '授权范围代码的业务值。',
  `resource_pattern` VARCHAR(255) NOT NULL COMMENT '资源模式的业务值。',
  `action_code` VARCHAR(64) NOT NULL COMMENT '操作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '范围状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  `version` INT NOT NULL COMMENT '范围并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`scope_id`),
  UNIQUE KEY `uk_credential_scope` (`credential_id`, `scope_code`, `resource_pattern`, `action_code`) COMMENT '保证业务唯一性：credential_id、scope_code、resource_pattern、action_code',
  KEY `idx_api_scope_credential` (`scope_code`, `credential_id`) COMMENT '支持访问路径：scope_code、credential_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 API Credential 的可查询和可审计授权明细';

CREATE TABLE `t_gateway_target` (
  `target_id` BIGINT NOT NULL COMMENT 'Gateway 受控目标的稳定业务标识。',
  `target_service_code` VARCHAR(128) NOT NULL COMMENT '目标服务代码的业务值。',
  `discovery_service_name` VARCHAR(255) NOT NULL COMMENT '服务发现名称的业务值。',
  `channel` VARCHAR(64) NOT NULL COMMENT 'Gateway 目标渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、CHANNEL_CALLBACK。',
  `status` VARCHAR(64) NOT NULL COMMENT 'Gateway 目标状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT 'Gateway 目标并发版本的整数值。',
  `created_by` BIGINT NOT NULL COMMENT '创建账号的稳定业务标识。',
  `updated_by` BIGINT NOT NULL COMMENT '修改账号的稳定业务标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`target_id`),
  UNIQUE KEY `uk_gateway_target_code` (`target_service_code`) COMMENT '保证业务唯一性：target_service_code',
  UNIQUE KEY `uk_gateway_target_channel` (`target_id`, `channel`) COMMENT '保证业务唯一性：target_id、channel',
  KEY `idx_gateway_target_status` (`channel`, `status`) COMMENT '支持访问路径：channel、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存由 Platform 统一管理的公共入口目标当前配置；Gateway 从完整快照读取目标身份与服务发现名称并执行通用校验';

CREATE TABLE `t_gateway_route` (
  `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `route_code` VARCHAR(128) NOT NULL COMMENT 'Gateway 路由代码的业务值。',
  `channel` VARCHAR(64) NOT NULL COMMENT 'Gateway 路由渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、CHANNEL_CALLBACK。',
  `http_method` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Gateway 预编译精确 HTTP Method 集合中的 ASCII 大写方法。取值来源：Gateway 预编译精确 HTTP Method 集合；非固定枚举。',
  `external_path_template` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ASCII 编码、大小写敏感的规范化公共 Path Template。',
  `downstream_path_template` VARCHAR(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ASCII 编码、大小写敏感的规范化目标 Path Template。',
  `target_id` BIGINT NOT NULL COMMENT 'Gateway 受控目标的稳定业务标识。',
  `security_profile_code` VARCHAR(64) NOT NULL COMMENT '安全档案代码的稳定业务代码。允许值：OPERATIONS_JWT、CLIENT_JWT、PARTNER_JWT、CLIENT_API_CREDENTIAL、CHECKOUT_SESSION、CHANNEL_CALLBACK、PUBLIC。',
  `authorization_mode` VARCHAR(64) NOT NULL COMMENT '授权模式的稳定业务代码。允许值：PUBLIC、HUMAN_AUTHENTICATED、HUMAN_PERMISSION、API_SCOPE、CHECKOUT_RESOURCE、CHANNEL_CALLBACK。',
  `permission_id` BIGINT NULL COMMENT '操作权限的稳定业务标识。',
  `required_scope_code` VARCHAR(128) NULL COMMENT '必需 API Scope 代码的业务值。',
  `request_cookie_mode` VARCHAR(64) NOT NULL COMMENT '请求 Cookie 模式的稳定业务代码。允许值：DROP、REQUIRE_PROFILE_COOKIE。',
  `response_cookie_mode` VARCHAR(64) NOT NULL COMMENT '响应 Cookie 模式的稳定业务代码。允许值：NONE、ISSUE_ONLY、ISSUE_OR_CLEAR、CLEAR_ONLY。',
  `idempotency_mode` VARCHAR(64) NOT NULL DEFAULT 'NONE' COMMENT '请求幂等模式的稳定业务代码。允许值：NONE、REQUIRED。',
  `body_limit_bytes` BIGINT NOT NULL COMMENT '请求体字节上限的长整数值。',
  `body_receive_timeout_millis` INT NOT NULL COMMENT '请求体接收超时毫秒数的整数值。',
  `response_timeout_millis` INT NOT NULL COMMENT '目标响应超时毫秒数的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT 'Gateway 路由状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT 'Gateway 路由并发版本的整数值。',
  `created_by` BIGINT NOT NULL COMMENT '创建账号的稳定业务标识。',
  `updated_by` BIGINT NOT NULL COMMENT '修改账号的稳定业务标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`route_id`),
  UNIQUE KEY `uk_gateway_route_code` (`route_code`) COMMENT '保证业务唯一性：route_code',
  UNIQUE KEY `uk_gateway_route_path` (`http_method`, `external_path_template`) COMMENT '保证业务唯一性：http_method、external_path_template',
  KEY `idx_gateway_route_target` (`target_id`, `channel`, `status`) COMMENT '支持访问路径：target_id、channel、status',
  KEY `idx_gateway_route_channel` (`channel`, `status`, `target_id`) COMMENT '支持访问路径：channel、status、target_id',
  KEY `idx_gateway_route_permission` (`permission_id`) COMMENT '支持访问路径：permission_id',
  KEY `idx_gateway_route_scope` (`required_scope_code`, `route_id`) COMMENT '支持访问路径：required_scope_code、route_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存六类外部 HTTP 入口的当前 Route、受控 Target、安全档案、授权模式和传输上限，不保存运行时计数或发布版本';

CREATE TABLE `t_gateway_route_header` (
  `route_header_id` BIGINT NOT NULL COMMENT 'Gateway 路由 Header 的稳定业务标识。',
  `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `header_name` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'ASCII HTTP field-name Token；大小写不敏感唯一，Platform/Gateway 仍执行完整 Allowlist 校验。取值来源：RFC 9110 HTTP field-name token 与 Platform/Gateway Allowlist；非固定枚举。',
  `header_use` VARCHAR(64) NOT NULL COMMENT 'Header 用途的稳定业务代码。允许值：CORS_REQUEST_ALLOWED、FORWARD_REQUEST。',
  PRIMARY KEY (`route_header_id`),
  UNIQUE KEY `uk_gateway_route_header` (`route_id`, `header_name`, `header_use`) COMMENT '保证业务唯一性：route_id、header_name、header_use'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Route 当前允许的完整 Header 名称及用途；聚合更新时整体替换，不开放通配、前缀或可信身份 Header';

CREATE TABLE `t_gateway_route_query_param` (
  `route_query_param_id` BIGINT NOT NULL COMMENT 'Gateway 路由查询参数的稳定业务标识。',
  `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `parameter_name` VARCHAR(255) NOT NULL COMMENT '查询参数名称的业务值。',
  PRIMARY KEY (`route_query_param_id`),
  UNIQUE KEY `uk_gateway_route_query` (`route_id`, `parameter_name`) COMMENT '保证业务唯一性：route_id、parameter_name'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Route 当前允许的完整 Query Parameter 名称；未声明名称由 Gateway 在调用目标前拒绝';

CREATE TABLE `t_gateway_guard_rule` (
  `guard_rule_id` BIGINT NOT NULL COMMENT 'Gateway 路由保护规则的稳定业务标识。',
  `route_id` BIGINT NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `rule_type` VARCHAR(64) NOT NULL COMMENT '保护规则类型的稳定业务代码。允许值：RATE_LIMIT、CONCURRENCY。',
  `dimension` VARCHAR(64) NOT NULL COMMENT '保护规则维度的稳定业务代码。允许值：SHARED_ROUTE、CLIENT_IP、AUTHENTICATED_PRINCIPAL、TENANT。',
  `capacity` BIGINT NULL COMMENT '令牌容量的长整数值。',
  `refill_tokens` BIGINT NULL COMMENT '补充令牌数的长整数值。',
  `refill_period_millis` BIGINT NULL COMMENT '补充周期毫秒数的长整数值。',
  `concurrency_limit` BIGINT NULL COMMENT '并发上限的长整数值。',
  PRIMARY KEY (`guard_rule_id`),
  UNIQUE KEY `uk_gateway_guard_rule` (`route_id`, `rule_type`, `dimension`) COMMENT '保证业务唯一性：route_id、rule_type、dimension'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Route 当前统一的 Rate 或 Concurrency 参数；Redis 只保存运行状态，Tenant 维度使用可信租户身份分桶';

CREATE TABLE `t_audit_chain` (
  `chain_id` BIGINT NOT NULL COMMENT '审计哈希链的稳定业务标识。',
  `chain_key` VARCHAR(128) NOT NULL COMMENT '审计链键的业务值。',
  `partition_date` DATE NOT NULL COMMENT '该审计链所属的 UTC 日历日期。',
  `shard_no` INT NOT NULL COMMENT '审计分片序号的整数值。',
  `previous_partition_hash` CHAR(64) NULL COMMENT '前分区终止哈希的 SHA-256 小写十六进制值。',
  `last_sequence_no` INT NOT NULL COMMENT '最后审计序号的整数值。',
  `last_event_hash` CHAR(64) NULL COMMENT '最后审计事件哈希的 SHA-256 小写十六进制值。',
  `status` VARCHAR(64) NOT NULL COMMENT '审计链状态的稳定业务代码。允许值：OPEN、SEALED、ARCHIVED。',
  `version` INT NOT NULL COMMENT '审计链并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`chain_id`),
  UNIQUE KEY `uk_audit_chain_key` (`chain_key`) COMMENT '保证业务唯一性：chain_key',
  UNIQUE KEY `uk_audit_chain` (`partition_date`, `shard_no`) COMMENT '保证业务唯一性：partition_date、shard_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存日期与 Shard 级审计 Hash Chain 的尾指针和跨分区连接摘要';

CREATE TABLE `t_audit_event` (
  `audit_event_id` BIGINT NOT NULL COMMENT '审计事件的稳定业务标识。',
  `chain_id` BIGINT NOT NULL COMMENT '审计链的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '审计序号的整数值。',
  `previous_event_hash` CHAR(64) NULL COMMENT '前审计事件哈希的 SHA-256 小写十六进制值。',
  `canonical_version` INT NOT NULL COMMENT '规范化算法版本的整数值。',
  `attribute_root_hash` CHAR(64) NOT NULL COMMENT '审计属性根哈希的 SHA-256 小写十六进制值。',
  `event_hash` CHAR(64) NOT NULL COMMENT '审计事件哈希的 SHA-256 小写十六进制值。',
  `source_system` VARCHAR(64) NULL COMMENT '来源系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `source_event_key` VARCHAR(128) NULL COMMENT '来源事件键的业务值。',
  `source_version` INT NULL COMMENT '来源版本的整数值。',
  `source_payload_hash` CHAR(64) NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  `schema_version` INT NOT NULL COMMENT '审计结构版本的整数值。',
  `actor_type` VARCHAR(64) NOT NULL COMMENT '操作主体类型的稳定业务代码。允许值：USER、CREDENTIAL、WORKLOAD、SYSTEM。',
  `actor_id` VARCHAR(128) NOT NULL COMMENT '操作主体标识的业务值。',
  `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  `action_code` VARCHAR(64) NOT NULL COMMENT '审计动作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `resource_system` VARCHAR(64) NOT NULL COMMENT '资源所有系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `resource_type` VARCHAR(64) NOT NULL COMMENT '资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `resource_id` VARCHAR(128) NOT NULL COMMENT '资源标识的业务值。',
  `result` VARCHAR(64) NOT NULL COMMENT '审计结果的稳定业务代码。允许值：SUCCEEDED、DENIED、FAILED。',
  `request_id` VARCHAR(128) NOT NULL COMMENT '请求标识的业务值。',
  `trace_id` VARCHAR(128) NOT NULL COMMENT '链路标识的业务值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '审计发生时间对应的 UTC 微秒时间。',
  `received_time` DATETIME(6) NOT NULL COMMENT '审计接收时间对应的 UTC 微秒时间。',
  `payload_hash` CHAR(64) NOT NULL COMMENT '本地审计载荷哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`audit_event_id`),
  UNIQUE KEY `uk_audit_source` (`source_system`, `source_event_key`) COMMENT '保证业务唯一性：source_system、source_event_key',
  UNIQUE KEY `uk_audit_sequence` (`chain_id`, `sequence_no`) COMMENT '保证业务唯一性：chain_id、sequence_no',
  UNIQUE KEY `uk_audit_event_hash` (`event_hash`) COMMENT '保证业务唯一性：event_hash',
  KEY `idx_audit_resource` (`resource_system`, `resource_type`, `resource_id`, `occurred_time`) COMMENT '支持访问路径：resource_system、resource_type、resource_id、occurred_time',
  KEY `idx_audit_actor` (`actor_type`, `actor_id`, `occurred_time`) COMMENT '支持访问路径：actor_type、actor_id、occurred_time',
  KEY `idx_audit_event_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变保存操作、文件访问和敏感 Reveal 的规范化可验证审计事实';

CREATE TABLE `t_audit_event_attr` (
  `attr_id` BIGINT NOT NULL COMMENT '审计事件属性的稳定业务标识。',
  `audit_event_id` BIGINT NOT NULL COMMENT '审计事件的稳定业务标识。',
  `attr_no` INT NOT NULL COMMENT '审计属性序号的整数值。',
  `attr_key` VARCHAR(128) NOT NULL COMMENT '审计属性键的业务值。',
  `value_text` VARCHAR(1000) NULL COMMENT '审计属性文本值的业务值。',
  `value_vault_token` VARCHAR(255) NULL COMMENT '审计属性 Vault Token的业务值。',
  `value_masked` VARCHAR(1000) NULL COMMENT '审计属性脱敏值的业务值。',
  `value_hash` CHAR(64) NOT NULL COMMENT '审计属性值哈希的 SHA-256 小写十六进制值。',
  `classification` VARCHAR(64) NOT NULL COMMENT '审计属性分类的稳定业务代码。允许值：PUBLIC、INTERNAL、CONFIDENTIAL、RESTRICTED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`attr_id`),
  UNIQUE KEY `uk_audit_attr` (`audit_event_id`, `attr_no`) COMMENT '保证业务唯一性：audit_event_id、attr_no',
  KEY `idx_audit_attr_key` (`attr_key`, `value_hash`) COMMENT '支持访问路径：attr_key、value_hash'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变保存公开脱敏标量或受 Vault 保护敏感值的严格互斥属性';

CREATE TABLE `t_security_event` (
  `security_event_id` BIGINT NOT NULL COMMENT '平台安全事件的稳定业务标识。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '安全事件类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `severity` VARCHAR(64) NOT NULL COMMENT '安全事件严重级别的稳定业务代码。允许值：INFO、LOW、MEDIUM、HIGH、CRITICAL。',
  `actor_ref` VARCHAR(128) NULL COMMENT '安全主体引用的业务值。',
  `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识。',
  `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `source_event_key` VARCHAR(128) NOT NULL COMMENT '来源事件键的业务值。',
  `source_version` INT NOT NULL COMMENT '来源版本的整数值。',
  `source_payload_hash` CHAR(64) NOT NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  `schema_version` INT NOT NULL COMMENT '安全事件结构版本的整数值。',
  `event_hash` CHAR(64) NOT NULL COMMENT '安全事件哈希的 SHA-256 小写十六进制值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '安全事件发生时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`security_event_id`),
  UNIQUE KEY `uk_security_event` (`event_hash`) COMMENT '保证业务唯一性：event_hash',
  UNIQUE KEY `uk_security_source` (`source_system`, `source_event_key`, `event_type`) COMMENT '保证业务唯一性：source_system、source_event_key、event_type',
  KEY `idx_security_event_severity` (`severity`, `occurred_time`) COMMENT '支持访问路径：severity、occurred_time',
  KEY `idx_security_event_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='不可变保存身份、权限、Credential、OTP、Webhook 和文件安全事件';

CREATE TABLE `t_command_idempotency` (
  `id` BIGINT NOT NULL COMMENT '命令幂等记录的稳定业务标识。',
  `caller_scope` CHAR(64) NOT NULL COMMENT '规范调用方范围哈希的 SHA-256 小写十六进制值。',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '幂等操作类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '幂等键的业务值。',
  `request_hash` CHAR(64) NOT NULL COMMENT '幂等请求哈希的 SHA-256 小写十六进制值。',
  `response_ref` VARCHAR(255) NULL COMMENT '幂等响应引用的业务值。',
  `status` VARCHAR(64) NOT NULL COMMENT '幂等执行状态的稳定业务代码。允许值：PROCESSING、SUCCEEDED、FAILED。',
  `expire_time` DATETIME(6) NOT NULL COMMENT '幂等记录到期时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_command_idempotency` (`caller_scope`, `operation_type`, `idempotency_key`) COMMENT '保证业务唯一性：caller_scope、operation_type、idempotency_key',
  KEY `idx_command_idem_expire` (`status`, `expire_time`) COMMENT '支持访问路径：status、expire_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存认证调用边界内 HTTP 或内部命令的请求摘要与稳定响应引用';

CREATE TABLE `t_console_resource` (
  `resource_id` BIGINT NOT NULL COMMENT '控制台界面资源的稳定业务标识。',
  `resource_code` VARCHAR(128) NOT NULL COMMENT '控制台资源代码的业务值。',
  `channel` VARCHAR(64) NOT NULL COMMENT '控制台渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER。',
  `resource_type` VARCHAR(64) NOT NULL COMMENT '控制台资源类型的稳定业务代码。允许值：MENU、BUTTON。',
  `parent_resource_id` BIGINT NULL COMMENT '父控制台资源的稳定业务标识。',
  `display_name` VARCHAR(255) NOT NULL COMMENT '展示名称的业务值。',
  `frontend_route` VARCHAR(1000) NULL COMMENT '前端相对路由的业务值。',
  `component_key` VARCHAR(255) NULL COMMENT '前端组件注册键的业务值。',
  `permission_id` BIGINT NOT NULL COMMENT '可见性权限的稳定业务标识。',
  `sort_order` INT NOT NULL COMMENT '同级排序序号的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '控制台资源状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT '控制台资源并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`resource_id`),
  UNIQUE KEY `uk_console_resource_code` (`resource_code`) COMMENT '保证业务唯一性：resource_code',
  UNIQUE KEY `uk_console_resource_channel` (`resource_id`, `channel`) COMMENT '保证业务唯一性：resource_id、channel',
  KEY `idx_console_resource_parent` (`parent_resource_id`, `channel`, `sort_order`) COMMENT '支持访问路径：parent_resource_id、channel、sort_order',
  KEY `idx_console_resource_permission` (`permission_id`) COMMENT '支持访问路径：permission_id',
  KEY `idx_console_resource_channel` (`channel`, `status`, `parent_resource_id`, `sort_order`) COMMENT '支持访问路径：channel、status、parent_resource_id、sort_order'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 Operations、Client、Partner Console 的当前 Menu/Button 导航资源；可见性复用 Permission，但不替代 BFF 授权';

CREATE TABLE `t_authentication_challenge` (
  `challenge_id` BIGINT NOT NULL COMMENT '认证挑战的稳定业务标识。',
  `challenge_no` VARCHAR(64) NOT NULL COMMENT '认证挑战编号的业务值。',
  `realm` VARCHAR(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `purpose` VARCHAR(64) NOT NULL COMMENT '认证挑战用途的稳定业务代码。允许值：SIGN_UP_EMAIL、PASSWORD_RESET、EMAIL_CHANGE、TENANT_INVITATION_ACCEPT。',
  `account_id` BIGINT NULL COMMENT '用户账号的稳定业务标识。',
  `tenant_invitation_id` BIGINT NULL COMMENT '租户邀请的稳定业务标识。',
  `email_vault_token` VARCHAR(255) NOT NULL COMMENT '挑战邮箱 Vault Token的业务值。',
  `email_masked` VARCHAR(255) NOT NULL COMMENT '挑战邮箱脱敏值的业务值。',
  `active_scope_hash` CHAR(64) NOT NULL COMMENT '活动挑战范围哈希的 SHA-256 小写十六进制值。',
  `secret_hmac` CHAR(64) NOT NULL COMMENT 'KMS 密钥对挑战标识、用途、范围和高熵秘密生成的 HMAC-SHA-256 小写十六进制值。',
  `hmac_key_version` INT NOT NULL COMMENT '挑战 HMAC 密钥版本的整数值。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '挑战到期时间对应的 UTC 微秒时间。',
  `max_attempts` INT NOT NULL DEFAULT 5 COMMENT '认证挑战固定允许五次校验尝试。',
  `used_attempts` INT NOT NULL DEFAULT 0 COMMENT '认证挑战已经消耗的校验尝试次数。',
  `status` VARCHAR(64) NOT NULL COMMENT '认证挑战状态的稳定业务代码。允许值：PENDING、CONSUMED、EXPIRED、SUPERSEDED、REVOKED。',
  `active_slot` INT NULL COMMENT '活动挑战占位的整数值。',
  `consumed_time` DATETIME(6) NULL COMMENT '挑战消费时间对应的 UTC 微秒时间。',
  `supersedes_challenge_id` BIGINT NULL COMMENT '被替代挑战的稳定业务标识。',
  `version` INT NOT NULL COMMENT '认证挑战并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`challenge_id`),
  UNIQUE KEY `uk_auth_challenge_no` (`challenge_no`) COMMENT '保证业务唯一性：challenge_no',
  UNIQUE KEY `uk_auth_challenge_active` (`active_scope_hash`, `active_slot`) COMMENT '保证业务唯一性：active_scope_hash、active_slot',
  KEY `idx_auth_challenge_account` (`account_id`, `status`) COMMENT '支持访问路径：account_id、status',
  KEY `idx_auth_challenge_invite` (`tenant_invitation_id`, `status`) COMMENT '支持访问路径：tenant_invitation_id、status',
  KEY `idx_auth_challenge_expire` (`status`, `expires_time`) COMMENT '支持访问路径：status、expires_time',
  KEY `idx_authenticati_supersedes_cha` (`supersedes_challenge_id`) COMMENT '支持访问路径：supersedes_challenge_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存注册、密码重置、邮箱变更或租户邀请接受的一次性认证挑战 HMAC，不复用业务邮箱验证码';

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

CREATE TABLE `t_file_storage_instance` (
  `instance_id` BIGINT NOT NULL COMMENT '存储实例主键。',
  `instance_code` VARCHAR(64) NOT NULL COMMENT '存储实例代码，即文件与上传记录上的 storage_provider；格式 [A-Z][A-Z0-9_]{0,63}，创建后不可修改，被记录引用后不可删除。',
  `storage_type` VARCHAR(16) NOT NULL COMMENT '存储类型，决定使用的 SDK。允许值：MINIO、S3。',
  `endpoint` VARCHAR(255) NULL COMMENT 'HTTPS 根地址，同时是浏览器直传与下载授权的地址；S3 为空时使用区域默认地址，MINIO 必填。',
  `region` VARCHAR(64) NOT NULL COMMENT '签名区域，例如 us-east-1、me-central-1。',
  `path_style_access` TINYINT NOT NULL COMMENT '是否使用路径风格访问：1 表示路径风格（兼容 S3 的存储通常需要），0 表示虚拟主机风格。',
  `trusted_certificate_pem` TEXT NULL COMMENT '私有 CA 或自签名证书 PEM（公开内容）；为空时使用 JDK 默认信任库，非空时只信任该证书。',
  `credential_mode` VARCHAR(16) NOT NULL COMMENT '凭据方式；MINIO 只允许 STATIC。允许值：STATIC、IAM_ROLE。',
  `credential_vault_token` VARCHAR(255) NULL COMMENT '静态访问密钥的 Vault Token（数据类型 FILE_STORAGE_CREDENTIAL_V1，值为 {"accessKeyId","secretAccessKey"}）；STATIC 必填，IAM_ROLE 为空；每次存储操作前以用途 FILE_STORAGE_ACCESS 读取，不缓存原值。',
  `credential_update_time` DATETIME(6) NULL COMMENT '最近一次录入或更换密钥（含切换凭据方式）的 UTC 微秒时间。',
  `connect_timeout_ms` INT NOT NULL COMMENT '连接超时毫秒数，1000—10000，默认 2000。',
  `read_timeout_ms` INT NOT NULL COMMENT '读写超时毫秒数，1000—60000，默认 10000。',
  `status` VARCHAR(16) NOT NULL COMMENT '实例状态；只有 ACTIVE 实例可读写，DISABLED 读写都返回不可用。允许值：DRAFT、ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`instance_id`),
  UNIQUE KEY `uk_file_storage_instance_code` (`instance_code`) COMMENT '保证业务唯一性：instance_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存运营维护的对象存储实例：类型、地址、区域、私有证书、凭据方式与密钥 Vault Token；实例代码即文件记录上的存储提供方';

CREATE TABLE `t_file_storage_bucket` (
  `bucket_id` BIGINT NOT NULL COMMENT '存储桶主键。',
  `instance_id` BIGINT NOT NULL COMMENT '所属存储实例 ID。',
  `bucket_name` VARCHAR(255) NOT NULL COMMENT '存储桶名称，即文件与上传记录上的 storage_container_ref；创建后不可修改，被记录引用后不可删除。',
  `status` VARCHAR(16) NOT NULL COMMENT '存储桶状态；只有 ACTIVE 接受新上传，ACTIVE 与 READ_ONLY 可读，DISABLED 读写都返回不可用。允许值：DRAFT、ACTIVE、READ_ONLY、DISABLED。',
  `last_check_time` DATETIME(6) NULL COMMENT '最近一次启用检查的 UTC 微秒时间；从未检查为空。',
  `last_check_result` VARCHAR(64) NULL COMMENT '最近一次启用检查结果；从未检查为空。允许值：OK、NOT_FOUND、VERSIONING_DISABLED、ENCRYPTION_DISABLED、ACCESS_DENIED、UNAVAILABLE。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`bucket_id`),
  UNIQUE KEY `uk_file_storage_bucket` (`instance_id`, `bucket_name`) COMMENT '保证业务唯一性：instance_id、bucket_name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存存储实例下登记的存储桶、读写状态与最近一次启用检查结果；Platform 只登记和检查，不建桶、不改桶策略';

CREATE TABLE `t_file_storage_route` (
  `route_id` BIGINT NOT NULL COMMENT '写入路由主键。',
  `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统，即内部调用 Token 的服务名（[a-z][a-z0-9-]{0,63}）；* 表示默认路由，没有精确路由的来源使用默认路由。取值来源：内部调用身份的服务名；非固定枚举。',
  `bucket_id` BIGINT NOT NULL COMMENT '新上传写入的存储桶 ID；保存时必须为 ACTIVE 桶且所属实例 ACTIVE。',
  `scan_policy_code` VARCHAR(64) NOT NULL COMMENT '新上传冻结的扫描策略代码；保存时必须存在 ACTIVE 修订，上传开始时冻结当前 ACTIVE 修订。取值来源：t_file_scan_policy.policy_code；非固定枚举。',
  `max_upload_seconds` INT NOT NULL COMMENT '新上传的授权有效秒数，60—900。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`route_id`),
  UNIQUE KEY `uk_file_storage_route_source` (`source_system`) COMMENT '保证业务唯一性：source_system',
  KEY `idx_file_storage_route_bucket` (`bucket_id`) COMMENT '支持访问路径：bucket_id（停用实例或桶前检查引用路由）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存按来源系统选择新上传存储桶、扫描策略与上传期限的写入路由；改路由只影响之后的新上传';

CREATE TABLE `t_file_upload` (
  `upload_id` BIGINT NOT NULL COMMENT '文件上传会话的稳定业务标识。',
  `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识；内部文件接口的上传为空，历史 Client 入驻上传保留当时解析出的入驻租户。',
  `uploader_id` BIGINT NULL COMMENT '上传人账号 ID；调用方有人工操作人时传入并原样记录，纯系统上传为空，Platform 不核对其真实性。',
  `source_system` VARCHAR(64) NOT NULL COMMENT '上传来源系统，即开始上传时内部调用身份的服务名；Platform 自行发起的上传为 platform-support。创建后不变。取值来源：内部调用身份的服务名；非固定枚举。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '来源系统声明的上传原因代码，格式 [A-Z][A-Z0-9_]{0,63}，用于反查文件为什么上传；创建后不变。取值来源：来源系统自定义；非固定枚举。',
  `expected_size` BIGINT NOT NULL COMMENT '预期文件大小的长整数值。',
  `expected_mime` VARCHAR(255) NOT NULL COMMENT '预期媒体类型的业务值。',
  `display_name` VARCHAR(255) NOT NULL COMMENT '去除路径和控制字符后的文件展示名，最大 255 Unicode 字符；仅授权页面展示，不作为对象存储 Key 或鉴权依据。',
  `storage_provider` VARCHAR(64) NOT NULL COMMENT '此次上传使用的对象存储适配器代码，来自受控配置；创建后固定，恢复不得改用当前默认适配器。',
  `storage_container_ref` VARCHAR(255) NOT NULL COMMENT '此次上传的目标存储容器引用；不含凭据，创建后固定，完成和恢复必须使用原容器。',
  `storage_object_ref` VARCHAR(255) NOT NULL COMMENT 'Platform 创建的高熵上传对象引用，不含原始文件名或个人资料；与适配器和容器组成上传目标，不能由浏览器替换。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '本次上传在开始时冻结的 Platform File Scan Policy 修订 ID；完成后由 File 继承，不因等待期间默认策略变化而重选。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '上传到期时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '上传状态的稳定业务代码。允许值：CREATED、UPLOADING、COMPLETED、EXPIRED、CANCELLED。',
  `version` INT NOT NULL COMMENT '上传并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`upload_id`),
  UNIQUE KEY `uk_upload_storage_target` (`storage_provider`, `storage_container_ref`, `storage_object_ref`) COMMENT '同一存储对象上传目标只归属一条 Upload，防止不同申请共享可写目标',
  KEY `idx_file_upload_tenant` (`tenant_id`, `status`, `create_time`) COMMENT '支持访问路径：tenant_id、status、create_time',
  KEY `idx_file_upload_due` (`status`, `expires_time`) COMMENT '支持访问路径：status、expires_time',
  KEY `idx_file_upload_uploader_id` (`uploader_id`) COMMENT '支持访问路径：uploader_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存受控上传的来源系统与原因、租户与上传人、展示名、原存储目标和冻结扫描策略；不记录业务归属，上传授权由存储适配器签发，不持久化临时 URL、签名或额外上传秘密';

CREATE TABLE `t_file_scan_policy` (
  `policy_id` BIGINT NOT NULL COMMENT '文件扫描策略修订的稳定业务标识。',
  `policy_code` VARCHAR(64) NOT NULL COMMENT '扫描策略代码的业务值。',
  `revision_no` INT NOT NULL COMMENT '扫描策略修订号的整数值。',
  `rule_schema_version` INT NOT NULL COMMENT '规则结构版本的整数值。',
  `allowed_mime_rules` JSON NOT NULL COMMENT '允许媒体类型规则的有界版本化 JSON 内容。',
  `max_size_bytes` BIGINT NOT NULL COMMENT '最大文件字节数的长整数值。',
  `effective_from` DATETIME(6) NULL COMMENT '生效开始时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '生效结束时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '扫描策略修订状态的稳定业务代码。允许值：DRAFT、ACTIVE、RETIRED。',
  `active_slot` INT NULL COMMENT '活动修订占位的整数值。',
  `version` INT NOT NULL COMMENT '扫描策略并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`policy_id`),
  UNIQUE KEY `uk_file_scan_revision` (`policy_code`, `revision_no`) COMMENT '保证业务唯一性：policy_code、revision_no',
  UNIQUE KEY `uk_file_scan_active` (`policy_code`, `active_slot`) COMMENT '保证业务唯一性：policy_code、active_slot',
  KEY `idx_file_scan_effective` (`status`, `effective_from`, `effective_to`) COMMENT '支持访问路径：status、effective_from、effective_to'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一行保存一个扫描策略修订；激活时同策略代码仅允许一个 ACTIVE 修订且内容随后冻结';

CREATE TABLE `t_file_scan_requirement` (
  `requirement_id` BIGINT NOT NULL COMMENT '文件扫描要求的稳定业务标识。',
  `policy_id` BIGINT NOT NULL COMMENT '扫描策略修订的稳定业务标识。',
  `requirement_no` INT NOT NULL COMMENT '扫描要求序号的整数值。',
  `scanner_code` VARCHAR(64) NOT NULL COMMENT '扫描器代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `scan_type` VARCHAR(64) NOT NULL COMMENT '扫描类型的稳定业务代码。允许值：ANTI_MALWARE、DLP、FORMAT。',
  `minimum_scanner_version` VARCHAR(64) NOT NULL COMMENT '最低扫描器版本的业务值。',
  `accepted_result_codes` JSON NOT NULL COMMENT '接受结果代码集合的有界版本化 JSON 内容。',
  `version` INT NOT NULL COMMENT '扫描要求并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`requirement_id`),
  UNIQUE KEY `uk_file_scan_req_no` (`policy_id`, `requirement_no`) COMMENT '保证业务唯一性：policy_id、requirement_no',
  UNIQUE KEY `uk_file_scan_req` (`policy_id`, `scanner_code`, `scan_type`) COMMENT '保证业务唯一性：policy_id、scanner_code、scan_type',
  UNIQUE KEY `uk_file_scan_req_owner` (`requirement_id`, `policy_id`) COMMENT '保证业务唯一性：requirement_id、policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一个扫描策略修订必须执行的扫描器和接受结果；策略激活后要求随策略一起冻结';

CREATE TABLE `t_file_object` (
  `file_id` BIGINT NOT NULL COMMENT '文件对象的稳定业务标识。',
  `creation_source` VARCHAR(64) NOT NULL COMMENT '文件创建来源的稳定业务代码。允许值：UPLOAD、DOCUMENT_RENDER、INTERNAL。',
  `upload_id` BIGINT NULL COMMENT '文件上传的稳定业务标识。',
  `source_system` VARCHAR(64) NOT NULL COMMENT '文件来源系统，完成上传时从上传记录复制，用于反查文件来源。取值来源：内部调用身份的服务名；非固定枚举。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '来源系统声明的上传原因代码，完成上传时从上传记录复制。取值来源：来源系统自定义；非固定枚举。',
  `storage_provider` VARCHAR(64) NOT NULL COMMENT '存储提供方的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `storage_container_ref` VARCHAR(255) NOT NULL COMMENT '存储容器引用的业务值。',
  `storage_object_ref` VARCHAR(255) NOT NULL COMMENT '存储对象引用的业务值。',
  `object_version` VARCHAR(128) NOT NULL COMMENT '存储对象版本的业务值。',
  `content_hash` CHAR(64) NOT NULL COMMENT '文件内容哈希的 SHA-256 小写十六进制值。',
  `size_bytes` BIGINT NOT NULL COMMENT '文件字节数的长整数值。',
  `mime_type` VARCHAR(255) NOT NULL COMMENT '对象内容的规范媒体类型。取值来源：IANA Media Types 与 Platform 允许列表；非固定枚举。',
  `storage_encryption_key_ref` VARCHAR(255) NOT NULL COMMENT '存储加密密钥引用的业务值。',
  `classification` VARCHAR(64) NOT NULL COMMENT '文件分类的稳定业务代码。允许值：PUBLIC、INTERNAL、CONFIDENTIAL、RESTRICTED。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '冻结扫描策略修订的稳定业务标识。',
  `scan_status` VARCHAR(64) NOT NULL COMMENT '扫描状态的稳定业务代码。允许值：PENDING_SCAN、SCANNING、AVAILABLE、QUARANTINED、REJECTED。',
  `object_status` VARCHAR(64) NOT NULL COMMENT '对象状态的稳定业务代码。允许值：ACTIVE、UNAVAILABLE、RETIRED。',
  `available_time` DATETIME(6) NULL COMMENT '文件可用时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '文件并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`file_id`),
  UNIQUE KEY `uk_storage_object` (`storage_provider`, `storage_container_ref`, `storage_object_ref`, `object_version`) COMMENT '保证业务唯一性：storage_provider、storage_container_ref、storage_object_ref、object_version',
  UNIQUE KEY `uk_file_upload` (`upload_id`) COMMENT '保证业务唯一性：upload_id',
  UNIQUE KEY `uk_file_content_owner` (`file_id`, `content_hash`) COMMENT '保证业务唯一性：file_id、content_hash',
  UNIQUE KEY `uk_file_policy_owner` (`file_id`, `scan_policy_id`) COMMENT '保证业务唯一性：file_id、scan_policy_id',
  KEY `idx_file_scan_status` (`scan_status`, `create_time`) COMMENT '支持访问路径：scan_status、create_time',
  KEY `idx_file_object_scan_policy_id` (`scan_policy_id`) COMMENT '支持访问路径：scan_policy_id',
  KEY `idx_file_source` (`source_system`, `reason_code`, `create_time`) COMMENT '支持访问路径：source_system、reason_code、create_time（按来源与原因反查文件）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存不可变对象存储引用、内容摘要和创建时冻结的扫描策略修订';

CREATE TABLE `t_file_scan_result` (
  `scan_result_id` BIGINT NOT NULL COMMENT '文件扫描结果的稳定业务标识。',
  `file_id` BIGINT NOT NULL COMMENT '文件的稳定业务标识。',
  `scan_requirement_id` BIGINT NOT NULL COMMENT '扫描要求的稳定业务标识。',
  `scan_policy_id` BIGINT NOT NULL COMMENT '冻结扫描策略修订的稳定业务标识。',
  `attempt_no` INT NOT NULL COMMENT '扫描尝试序号的整数值。',
  `scanner_runtime_version` VARCHAR(64) NOT NULL COMMENT '扫描器运行版本的业务值。',
  `result_code` VARCHAR(64) NOT NULL COMMENT '扫描结果代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `scanned_time` DATETIME(6) NOT NULL COMMENT '扫描完成时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`scan_result_id`),
  UNIQUE KEY `uk_file_scan_attempt` (`file_id`, `scan_requirement_id`, `attempt_no`) COMMENT '保证业务唯一性：file_id、scan_requirement_id、attempt_no',
  KEY `idx_scan_file_policy` (`file_id`, `scan_policy_id`) COMMENT '支持访问路径：file_id、scan_policy_id',
  KEY `idx_scan_req_policy` (`scan_requirement_id`, `scan_policy_id`) COMMENT '支持访问路径：scan_requirement_id、scan_policy_id',
  KEY `idx_file_scan_re_scan_policy_id` (`scan_policy_id`) COMMENT '支持访问路径：scan_policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存文件对其冻结策略修订中每项扫描要求的运行版本与判定';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿执行记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '领域事件或本地恢复动作的全局稳定幂等键。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '需要发布或本地恢复执行的领域事件类型代码。取值来源：CCT 唯一 Compensation Handler Catalog；非固定枚举。取值来源：宿主 Handler 受控目录；非固定枚举。',
  `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的业务子类型；无业务子类型时使用短横线占位。取值来源：所属 Handler 的子类型目录，无业务子类型时固定短横线占位；非固定枚举。取值来源：宿主 Handler 受控目录；非固定枚举。',
  `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可空或最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。',
  `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '补偿任务当前的可恢复执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '补偿任务已经尝试执行的累计次数。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '补偿任务下一次允许被 Claim 的 UTC 微秒精度时刻。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前 Claim 补偿任务的执行实例标识。',
  `lock_until` DATETIME(6) NULL COMMENT '当前补偿任务执行租约到期的 UTC 微秒精度时刻。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿执行最近一次失败的稳定错误分类代码。取值来源：CCT Compensation Error Catalog；非固定枚举。取值来源：Foundation 与宿主错误目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '补偿执行最近一次失败的脱敏错误摘要，不保存敏感原文。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT '补偿任务 Claim 与完成回写使用的独立技术 CAS 版本，不是业务 Revision 或来源版本。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT 'Unique index for compensation_event on event_key, event_type, sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT 'Access index for compensation_event on execute_status, next_execute_at, id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT 'Access index for compensation_event on execute_status, lock_until, id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本服务 Handler 的可恢复任务；uk_comp_event_identity(event_key,event_type,sub_event_type)；状态 INIT/PROCESSING/ERROR/FAILED；可选最小 JSON Object 最大 4 KiB UTF-8，禁止敏感值、Vault Token 和已落库上下文；Claim/Lease 使用 lock_version，安全默认值与执行协议服从现行补偿指南；Payload 最小化：payload_json 可空或为最大 4 KiB UTF-8 的不可变 JSON Object；禁止 Vault Token、敏感值及可从本库恢复的上下文。；Lease 状态矩阵：PROCESSING 必须同时具有未过期的 lock_owner 与 lock_until；INIT、ERROR、FAILED 不得保留活动 Lease。；Lease Holder 写回：只有持有当前活动 Lease 的执行者可以完成、删除或回写该次尝试；旧执行者不得覆盖新 Lease Holder 的结果。；登记原子性：权威业务事实、业务 History 与 compensation_event 必须在同一本地事务提交；事务内任一点失败全部回滚。；成功删除：Handler 只有取得确定成功结果后才允许删除 compensation_event；MQ Handler 必须等待 Broker Confirm。；失败保留：达到最大重试次数后必须进入 FAILED、保留记录并告警；人工重开必须记录 Actor、Reason 与 Ticket 后回到 ERROR 等待重新 Claim。；Lease 接管：PROCESSING 的 Lease 超时后允许重新 Claim；Claim 与完成/回写都必须使用 lock_version CAS。';

CREATE TABLE `t_notification_request` (
  `request_id` BIGINT NOT NULL COMMENT '请求主键。',
  `tenant_id` BIGINT NULL COMMENT '请求归属的 Platform Access Tenant 稳定标识；平台通知为空，非收件人归属。',
  `source_system` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '模块调用方确定的可信来源系统；与 source_event_key 共同标识一次通知意图。取值来源：可信模块调用方来源系统目录；非固定枚举。',
  `source_event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '来源系统内永久唯一的通知业务标识，不是 HTTP Idempotency-Key。',
  `notification_template_id` BIGINT NOT NULL COMMENT '模板定义 ID，不是历史版本 ID。',
  `default_locale` VARCHAR(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '默认语言。',
  `default_parameters` JSON NULL COMMENT '受理后不可变的普通字符串参数 JSON；最多 64 项、16 KiB；禁止认证秘密。',
  `notification_route_id` BIGINT NOT NULL COMMENT '本次选择的路由。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`request_id`),
  UNIQUE KEY `uk_notify_source` (`source_system`, `source_event_key`) COMMENT '保证业务唯一性：source_system、source_event_key',
  KEY `idx_notification_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id',
  KEY `idx_notification_notification_t` (`notification_template_id`) COMMENT '支持访问路径：notification_template_id',
  KEY `idx_notification_notification_r` (`notification_route_id`) COMMENT '支持访问路径：notification_route_id',
  KEY `idx_notify_request_create_time` (`create_time`) COMMENT '支持访问路径：create_time（运营按时间查询发送记录）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知请求的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

CREATE TABLE `t_notification_delivery` (
  `delivery_id` BIGINT NOT NULL COMMENT '投递主键。',
  `request_id` BIGINT NOT NULL COMMENT '所属 Request。',
  `address_vault_token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'EMAIL_ADDRESS_V1 稳定邮箱引用；Purpose 为 PLATFORM_NOTIFICATION_EMAIL_V1；仅外发前允许 Reveal，禁止日志及补偿 Payload。SMS 尚未实现。',
  `locale` VARCHAR(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '收件人语言，缺失时继承 Request.default_locale。',
  `parameters` JSON NULL COMMENT '收件人普通字符串参数 JSON，按键覆盖 Request 默认值；NULL 或空对象不覆盖；禁止认证秘密。',
  `status` VARCHAR(32) NOT NULL COMMENT '当前投递状态。允许值：PENDING、PROCESSING、RETRY_WAIT、SENT、DELIVERED、FAILED、UNKNOWN。',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已创建发送尝试数量，初始为 0；查询不递增。',
  `next_retry_time` DATETIME(6) NULL COMMENT '下次允许新建发送的 UTC 微秒时间；无重试时为空，查询不使用。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1；不是历史修订号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`delivery_id`),
  UNIQUE KEY `uk_notify_recipient` (`request_id`, `address_vault_token`) COMMENT '保证业务唯一性：request_id、address_vault_token'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知投递的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

CREATE TABLE `t_notification_attempt` (
  `attempt_id` BIGINT NOT NULL COMMENT '发送尝试主键。',
  `delivery_id` BIGINT NOT NULL COMMENT '所属 Delivery。',
  `provider_id` BIGINT NOT NULL COMMENT '本次实际使用的发送账号配置身份。',
  `provider_request_no` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '按供应商协议生成且外发前固定的请求号；与 provider_id 组合唯一。',
  `provider_message_id` VARCHAR(255) NULL COMMENT 'Provider 返回的消息标识。',
  `status` VARCHAR(32) NOT NULL COMMENT '发送提交的确认状态。允许值：PROCESSING、ACCEPTED、FAILED、UNKNOWN。',
  `response_code` VARCHAR(64) NULL COMMENT '初次发送调用的安全响应码。',
  `resolution_source` VARCHAR(32) NULL COMMENT '结果确认来源，如 SEND_RESPONSE、PROVIDER_QUERY。允许值：SEND_RESPONSE、PROVIDER_QUERY。',
  `resolution_code` VARCHAR(64) NULL COMMENT '后续查询确认结果的安全响应码。',
  `started_time` DATETIME(6) NOT NULL COMMENT '本次发送尝试开始时间。',
  `finished_time` DATETIME(6) NULL COMMENT '初次发送调用结束时间。',
  `resolved_time` DATETIME(6) NULL COMMENT '原提交获得明确结果的时间；未确认时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`attempt_id`),
  UNIQUE KEY `uk_notify_provider_request` (`provider_request_no`, `provider_id`) COMMENT '保证业务唯一性：provider_request_no、provider_id',
  KEY `idx_notify_attempt_delivery` (`delivery_id`, `status`) COMMENT '支持访问路径：delivery_id、status',
  KEY `idx_notification_provider_id` (`provider_id`) COMMENT '支持访问路径：provider_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知发送尝试的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

CREATE TABLE `t_notification_template` (
  `template_id` BIGINT NOT NULL COMMENT '模板定义主键。',
  `template_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '唯一模板编码，表达通知用途。',
  `medium` VARCHAR(16) NOT NULL COMMENT 'EMAIL 或 SMS。允许值：EMAIL、SMS。',
  `status` VARCHAR(16) NOT NULL COMMENT '模板启停状态。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1；不是历史修订号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_notify_template_code` (`template_code`) COMMENT '保证业务唯一性：template_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知模板的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

CREATE TABLE `t_notification_template_content` (
  `content_id` BIGINT NOT NULL COMMENT '内容主键。',
  `template_id` BIGINT NOT NULL COMMENT '所属模板定义。',
  `locale` VARCHAR(35) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '内容语言。',
  `subject_template` TEXT NULL COMMENT '邮件标题模板；SMS 不使用。',
  `body_template` TEXT NOT NULL COMMENT '正文模板。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1；不是历史修订号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`content_id`),
  UNIQUE KEY `uk_notify_template_locale` (`template_id`, `locale`) COMMENT '保证业务唯一性：template_id、locale'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知语言内容的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

CREATE TABLE `t_notification_provider` (
  `provider_id` BIGINT NOT NULL COMMENT 'Provider 主键。',
  `provider_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '唯一编码，标识具体发送账号。',
  `medium` VARCHAR(16) NOT NULL COMMENT 'EMAIL 或 SMS。允许值：EMAIL、SMS。',
  `provider_type` VARCHAR(32) NOT NULL COMMENT '服务商类型，由发送适配器声明，如 AMAZON_SES；创建后不可改。取值来源：通知发送适配器声明的服务商类型；非固定枚举。',
  `status` VARCHAR(16) NOT NULL COMMENT '启用、停用。允许值：ACTIVE、DISABLED。',
  `region` VARCHAR(32) NULL COMMENT '服务商区域，如 me-central-1；SES 必填。',
  `from_address` VARCHAR(254) NULL COMMENT '发件地址，须为该区域已验证身份；SES 必填。',
  `configuration_set` VARCHAR(64) NULL COMMENT 'SES Configuration Set，可空。',
  `credential_vault_token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT 'NOTIFICATION_PROVIDER_CREDENTIAL_V1 密钥对的 Vault Token；为空表示未录入；只在发送前以 NOTIFICATION_DELIVERY Reveal，禁止日志与接口返回。',
  `credential_update_time` DATETIME(6) NULL COMMENT '最近一次录入或更换密钥的 UTC 微秒时间。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1；不是历史修订号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`provider_id`),
  UNIQUE KEY `uk_notify_provider_code` (`provider_code`) COMMENT '保证业务唯一性：provider_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知发送账号的当前权威事实；普通 Email 首期，密钥只保存其 Vault Token，不保存认证秘密或正文快照';

CREATE TABLE `t_notification_route` (
  `route_id` BIGINT NOT NULL COMMENT '路由主键。',
  `notification_template_id` BIGINT NOT NULL COMMENT '对应模板。',
  `primary_provider_id` BIGINT NOT NULL COMMENT '主 Provider。',
  `fallback_provider_id` BIGINT NULL COMMENT '备用 Provider。',
  `status` VARCHAR(16) NOT NULL COMMENT '启用、停用。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1；不是历史修订号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`route_id`),
  UNIQUE KEY `uk_notify_template_route` (`notification_template_id`) COMMENT '保证业务唯一性：notification_template_id',
  KEY `idx_notification_primary_provid` (`primary_provider_id`) COMMENT '支持访问路径：primary_provider_id',
  KEY `idx_notification_fallback_provi` (`fallback_provider_id`) COMMENT '支持访问路径：fallback_provider_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知主备路由的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

-- 2026-10-15 Client Webhook 投递
CREATE TABLE `t_webhook_endpoint` (
  `endpoint_id` BIGINT NOT NULL COMMENT 'Webhook 端点主键。',
  `tenant_id` BIGINT NOT NULL COMMENT '端点所属的 CLIENT Access Tenant。',
  `url` VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Client 的 HTTPS 接收地址明文；不含 userinfo 与 fragment，修改后下一次尝试即使用新地址。',
  `event_types` JSON NOT NULL COMMENT '订阅的公开事件类型字符串数组，非空且不重复；取消订阅后尚未完成的投递被取消。',
  `status` VARCHAR(32) NOT NULL COMMENT '端点状态；停用后不再生成新投递，未完成的投递被取出时取消。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`endpoint_id`),
  KEY `idx_webhook_endpoint_tenant` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='CLIENT 租户的 Webhook 接收地址与订阅事件类型；每个 Client 最多 5 个，由运营代配置';

CREATE TABLE `t_webhook_signing_secret` (
  `signing_secret_id` BIGINT NOT NULL COMMENT 'Webhook 签名密钥主键。',
  `endpoint_id` BIGINT NOT NULL COMMENT '所属 Webhook 端点。',
  `rotation_no` INT NOT NULL COMMENT '端点内第几把密钥，从 1 开始。',
  `secret_vault_token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'WEBHOOK_SIGNING_SECRET_V1 的 REFERENCE Token；Purpose 为 WEBHOOK_SIGNING；仅发送前 Reveal，禁止日志及补偿 Payload。',
  `status` VARCHAR(32) NOT NULL COMMENT '密钥状态；轮换后旧密钥变为 RETIRED。允许值：ACTIVE、RETIRED。',
  `signing_slot` INT NULL COMMENT 'ACTIVE 时为 1，否则为空；与端点组成唯一键，保证同时只有一把 ACTIVE。',
  `valid_until` DATETIME(6) NULL COMMENT 'RETIRED 后仍参与签名的 UTC 截止时间：宽限轮换为轮换时间加 1 小时，立即作废为轮换时间；ACTIVE 时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`signing_secret_id`),
  UNIQUE KEY `uk_webhook_secret_rotation` (`endpoint_id`, `rotation_no`) COMMENT '保证业务唯一性：endpoint_id、rotation_no',
  UNIQUE KEY `uk_webhook_secret_active` (`endpoint_id`, `signing_slot`) COMMENT '保证业务唯一性：endpoint_id、signing_slot'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='端点签名密钥的 Vault 引用与轮换宽限；同一端点同时只有一把 ACTIVE，宽限内的 RETIRED 密钥也参与签名';

CREATE TABLE `t_webhook_event` (
  `event_id` BIGINT NOT NULL COMMENT 'Platform 内部事件主键，不作为公开 eventId。',
  `tenant_id` BIGINT NOT NULL COMMENT '按来源 Client 解析得到的 CLIENT Access Tenant。',
  `source_system` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '通过白名单的调用方服务名。取值来源：内部 HTTP 调用方服务名白名单；非固定枚举。',
  `source_event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '来源冻结的公开 eventId；同一 Client 与来源系统内永久唯一。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '公开事件类型。取值来源：Webhook 公开事件类型目录；非固定枚举。',
  `payload_schema_version` INT NOT NULL COMMENT 'Envelope 的 schemaVersion。',
  `source_version` INT NOT NULL COMMENT '来源资源版本；只用于重复提交比较。',
  `source_payload_hash` CHAR(64) NOT NULL COMMENT 'data.object 的 RFC 8785 规范字节 SHA-256 小写十六进制。',
  `payload_hash` CHAR(64) NOT NULL COMMENT '完整 Envelope 的 RFC 8785 规范字节 SHA-256 小写十六进制；每次发送前用于校验重新规范化的字节。',
  `payload` JSON NOT NULL COMMENT '冻结的公开 Envelope；不含 Token、PII 或内部标识，规范化后不超过 4096 字节。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_webhook_event_source` (`tenant_id`, `source_event_key`, `source_system`) COMMENT '保证业务唯一性：tenant_id、source_event_key、source_system'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='来源系统提交的冻结公开事件；受理后不可变，Retry 与 Replay 发送同一 Envelope';

CREATE TABLE `t_webhook_delivery` (
  `delivery_id` BIGINT NOT NULL COMMENT 'Webhook 投递主键；也是补偿任务的事件键。',
  `webhook_event_id` BIGINT NOT NULL COMMENT '所属 Webhook 事件。',
  `tenant_id` BIGINT NOT NULL COMMENT '与事件、端点相同的 CLIENT Access Tenant；用于列表查询。',
  `endpoint_id` BIGINT NOT NULL COMMENT '投递目标端点。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '冗余事件类型，用于列表筛选与订阅复核。取值来源：Webhook 公开事件类型目录；非固定枚举。',
  `status` VARCHAR(32) NOT NULL COMMENT '投递状态；SUCCEEDED、DEAD、CANCELLED 为终态。允许值：CREATED、PROCESSING、RETRY_WAIT、SUCCEEDED、DEAD、CANCELLED。',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已创建的尝试数量，初始为 0。',
  `last_attempt_time` DATETIME(6) NULL COMMENT '最近一次尝试开始的 UTC 微秒时间；尚未尝试时为空。',
  `replay_of_delivery_id` BIGINT NULL COMMENT '人工 Replay 时指向原投递；原始投递为空。',
  `requested_by` BIGINT NULL COMMENT '人工 Replay 的 Operations 账号；原始投递为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`delivery_id`),
  KEY `idx_webhook_delivery_event` (`webhook_event_id`) COMMENT '支持访问路径：webhook_event_id',
  KEY `idx_webhook_delivery_tenant` (`tenant_id`, `create_time`) COMMENT '支持访问路径：tenant_id、create_time',
  KEY `idx_webhook_delivery_endpoint` (`endpoint_id`, `create_time`) COMMENT '支持访问路径：endpoint_id、create_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一个事件到一个端点的投递，含原始投递与人工 Replay；重试节奏与执行租约由补偿任务承担';

CREATE TABLE `t_webhook_attempt` (
  `attempt_id` BIGINT NOT NULL COMMENT 'Webhook 投递尝试主键。',
  `delivery_id` BIGINT NOT NULL COMMENT '所属 Webhook 投递。',
  `attempt_no` INT NOT NULL COMMENT '投递内的尝试序号，从 1 开始。',
  `request_url` VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '本次实际请求的端点地址。',
  `signing_secret_id` BIGINT NOT NULL COMMENT '本次签名使用的 ACTIVE 密钥。',
  `grace_secret_id` BIGINT NULL COMMENT '宽限期内参与签名的 RETIRED 密钥；无宽限时为空。',
  `status` VARCHAR(32) NOT NULL COMMENT '尝试状态；PROCESSING 遗留表示发送中断，恢复时记为 FAILED。允许值：PROCESSING、SUCCEEDED、FAILED。',
  `http_status` INT NULL COMMENT '收到响应时的 HTTP 状态码。',
  `failure_code` VARCHAR(64) NULL COMMENT '失败原因代码；成功时为空。允许值：HTTP_STATUS、TIMEOUT、CONNECT_FAILED、TLS_FAILED、DNS_FAILED、BLOCKED_ADDRESS、INTERRUPTED。',
  `duration_ms` INT NULL COMMENT '从发出请求到结束的毫秒数；未完成时为空。',
  `started_time` DATETIME(6) NOT NULL COMMENT '尝试开始的 UTC 微秒时间。',
  `finished_time` DATETIME(6) NULL COMMENT '尝试结束的 UTC 微秒时间；PROCESSING 时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`attempt_id`),
  UNIQUE KEY `uk_webhook_attempt_no` (`delivery_id`, `attempt_no`) COMMENT '保证业务唯一性：delivery_id、attempt_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='每次 HTTP 投递尝试的地址、签名密钥与结果；发送前登记为 PROCESSING，结束后写入结果';

CREATE TABLE `t_currency_spec` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的币种规则 Snowflake 主键。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 三位币种代码。取值来源：ISO 4217 币种代码；非固定枚举。',
  `minor_unit` TINYINT NOT NULL COMMENT '记账与结算金额允许保留的小数位数，取值范围为零至八。',
  `rounding_mode` VARCHAR(64) NOT NULL COMMENT '金额从八位计算精度归整到币种精度时采用的固定方式。允许值：HALF_UP。',
  `status` VARCHAR(64) NOT NULL COMMENT '币种规则当前是否允许被新业务使用。允许值：DRAFT、ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '币种规则乐观并发版本，从 1 开始，每次维护成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency` (`currency`) COMMENT '保证业务唯一性：currency'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存全平台使用的 ISO 4217 币种精度、舍入方式和启停状态；Platform 唯一持有，ledger、billing 经内部 HTTP 读取并在进程内缓存快照。';

-- 全平台参考数据种子：与 platform-support-initial-data.sql 相同的 USD 币种规则；作为常驻参考数据保留，用例只增删自己的其他币种。
INSERT INTO `t_currency_spec` (`id`, `currency`, `minor_unit`, `rounding_mode`, `status`, `version`, `create_time`, `update_time`)
VALUES (1001, 'USD', 2, 'HALF_UP', 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- 工作流 10 张表（2026-10-22，摘自 platform-support-schema.sql，已有库按 changes/2026-10-22-workflow.sql 增量执行）。
CREATE TABLE `t_workflow_definition` (
  `definition_id` BIGINT NOT NULL COMMENT '流程定义的 Snowflake 主键。',
  `definition_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '流程定义的业务编码；创建后不可修改，全局唯一。',
  `definition_name` VARCHAR(128) NOT NULL COMMENT '流程定义的显示名称，随草稿保存修改。',
  `description` VARCHAR(512) NULL COMMENT '流程定义的用途说明；未填写时为空。',
  `status` VARCHAR(32) NOT NULL COMMENT '流程定义是否允许发起新实例；INACTIVE 不影响在途实例。允许值：ACTIVE、INACTIVE。',
  `current_version_no` INT NULL COMMENT '最新一次发布的版本号，发起实例使用该版本；从未发布时为空。',
  `draft_version_no` INT NULL COMMENT '当前唯一草稿的版本号；没有草稿时为空，保证每个定义最多一份草稿。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建流程定义的 Operations 账号 ID（t_user_account.account_id）。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改流程定义或其草稿的 Operations 账号 ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`definition_id`),
  UNIQUE KEY `uk_workflow_def_code` (`definition_code`) COMMENT '保证业务唯一性：definition_code',
  KEY `idx_workflow_def_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Operations 用流程图配置的流程模板；草稿可改，发布生成不可变版本，再修改即生成新版本；停用后不能发起新实例，在途实例照常运行';

CREATE TABLE `t_workflow_definition_version` (
  `definition_version_id` BIGINT NOT NULL COMMENT '流程版本的 Snowflake 主键。',
  `definition_id` BIGINT NOT NULL COMMENT '该版本所属的流程定义 ID。',
  `version_no` INT NOT NULL COMMENT '定义内从 1 开始递增的版本号；草稿发布后号码不变。',
  `status` VARCHAR(32) NOT NULL COMMENT 'DRAFT 可随草稿保存覆盖；PUBLISHED 后内容不可变。允许值：DRAFT、PUBLISHED。',
  `graph_json` JSON NOT NULL COMMENT '节点、连线、排他条件、审批、动作引用与通知规则，结构见工作流技术方案 3.1；不超过 48 KiB。',
  `start_form_json` JSON NOT NULL COMMENT '启动表单字段定义，与表单动作共用结构，见工作流技术方案 3.2；最多 50 个字段，不超过 12 KiB。',
  `action_snapshot_json` JSON NULL COMMENT '发布时冻结的引用动作模板：模板 ID、模板版本、类型与配置；草稿为空。',
  `published_by` BIGINT NULL COMMENT '发布该版本的 Operations 账号 ID；草稿为空。',
  `published_time` DATETIME(6) NULL COMMENT '版本发布的 UTC 微秒时间；草稿为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该版本（新建定义或从发布版本复制草稿）的 Operations 账号 ID。',
  `updated_by` BIGINT NOT NULL COMMENT '最后保存该草稿或发布该版本的 Operations 账号 ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`definition_version_id`),
  UNIQUE KEY `uk_workflow_def_ver` (`definition_id`, `version_no`) COMMENT '保证业务唯一性：definition_id、version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='流程定义的草稿或已发布版本；发布后流程图、启动表单与动作模板快照不可变，实例发起时绑定版本';

CREATE TABLE `t_workflow_action_template` (
  `action_template_id` BIGINT NOT NULL COMMENT '动作模板的 Snowflake 主键。',
  `template_code` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '动作模板的业务编码；创建后不可修改，全局唯一。',
  `template_name` VARCHAR(128) NOT NULL COMMENT '动作模板的显示名称。',
  `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式；DATA_QUERY 本期只预留，不能被发布的流程引用。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。',
  `description` VARCHAR(512) NULL COMMENT '动作模板的用途说明；未填写时为空。',
  `config_json` JSON NOT NULL COMMENT '按动作类型的配置，结构见工作流技术方案 3.3。',
  `status` VARCHAR(32) NOT NULL COMMENT '动作模板是否允许被新发布的流程引用。允许值：ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次修改加一；发布时作为模板版本冻结进版本快照。',
  `created_by` BIGINT NOT NULL COMMENT '创建动作模板的 Operations 账号 ID。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改动作模板的 Operations 账号 ID。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`action_template_id`),
  UNIQUE KEY `uk_workflow_tpl_code` (`template_code`) COMMENT '保证业务唯一性：template_code',
  KEY `idx_workflow_tpl_type` (`action_type`, `status`) COMMENT '支持访问路径：action_type、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='动作目录中可复用的节点动作配置：类型、输入、输出与验证方式；停用后新发布的流程不能引用，已发布版本使用冻结快照照常运行';

CREATE TABLE `t_workflow_instance` (
  `instance_id` BIGINT NOT NULL COMMENT '流程实例的 Snowflake 主键。',
  `definition_id` BIGINT NOT NULL COMMENT '实例所属的流程定义 ID。',
  `definition_version_id` BIGINT NOT NULL COMMENT '发起时绑定的已发布流程版本 ID；实例全程按该版本执行。',
  `title` VARCHAR(128) NOT NULL COMMENT '发起人填写的实例标题。',
  `business_key` VARCHAR(128) NULL COMMENT '可选的业务关联键（如 CHANNEL:GEIDEA-SA），只用于检索和展示，工作流不解释其含义；未填写时为空。',
  `status` VARCHAR(32) NOT NULL COMMENT '实例运行状态；COMPLETED、TERMINATED 为终态，终态后只读。允许值：RUNNING、COMPLETED、TERMINATED。',
  `start_values_json` JSON NOT NULL COMMENT '启动表单提交值，作为 start.* 实例变量；发起后不可修改。',
  `assignment_json` JSON NOT NULL COMMENT '发起时冻结的各节点审批人与各动作执行人（版本默认值加发起时的修改）。',
  `started_by` BIGINT NOT NULL COMMENT '发起实例的 Operations 账号 ID。',
  `start_time` DATETIME(6) NOT NULL COMMENT '实例发起的 UTC 微秒时间。',
  `end_time` DATETIME(6) NULL COMMENT '实例完成或终止的 UTC 微秒时间；运行中为空。',
  `terminated_by` BIGINT NULL COMMENT '终止实例的 Operations 账号 ID；未终止时为空。',
  `termination_reason` VARCHAR(512) NULL COMMENT '终止实例时填写的原因；未终止时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`instance_id`),
  KEY `idx_workflow_inst_status` (`status`, `start_time`) COMMENT '支持访问路径：status、start_time',
  KEY `idx_workflow_inst_def` (`definition_id`, `status`) COMMENT '支持访问路径：definition_id、status',
  KEY `idx_workflow_inst_starter` (`started_by`, `start_time`) COMMENT '支持访问路径：started_by、start_time',
  KEY `idx_workflow_inst_bizkey` (`business_key`) COMMENT '支持访问路径：business_key'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次具体的流程运行；绑定发起时的已发布版本，节点、动作与配置全程不变，运行中只能经转交变更人员';

CREATE TABLE `t_workflow_node_run` (
  `node_run_id` BIGINT NOT NULL COMMENT '节点运行的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '节点运行所属的流程实例 ID。',
  `node_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '绑定流程版本中的节点 key。',
  `node_type` VARCHAR(32) NOT NULL COMMENT '节点在流程图中的类型。允许值：START、END、TASK、EXCLUSIVE、PARALLEL_SPLIT、PARALLEL_JOIN。',
  `round_no` INT NOT NULL COMMENT '同一实例同一节点的轮次，从 1 开始；新一轮为该节点已有最大轮次加一。',
  `branch_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '所在并行分支，取并行分叉出边的 key；不在并行分支内时为空。',
  `previous_node_run_id` BIGINT NULL COMMENT '实际来源的节点运行 ID；START 与并行汇合节点为空。',
  `status` VARCHAR(32) NOT NULL COMMENT 'TASK 依次为 ACTIVE（执行动作）、APPROVING（待审批），结束于 PASSED、RETURNED（退回修改）、REJECTED（被驳回）或 CANCELLED；并行汇合为 WAITING 直到全部分支完成；START、END、排他网关与并行分叉写入即 PASSED。允许值：ACTIVE、APPROVING、WAITING、PASSED、RETURNED、REJECTED、CANCELLED。',
  `selected_edge_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '排他网关按条件选中的出边 key；其他节点为空。',
  `activated_time` DATETIME(6) NOT NULL COMMENT '节点运行创建即激活的 UTC 微秒时间。',
  `completed_time` DATETIME(6) NULL COMMENT '节点运行进入 PASSED、RETURNED、REJECTED 或 CANCELLED 的 UTC 微秒时间；进行中为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`node_run_id`),
  UNIQUE KEY `uk_workflow_node_round` (`instance_id`, `node_key`, `round_no`) COMMENT '保证业务唯一性：instance_id、node_key、round_no',
  KEY `idx_workflow_node_status` (`instance_id`, `status`) COMMENT '支持访问路径：instance_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='实例中一个节点的一轮运行；退回修改与驳回重新激活时新建一轮，previous_node_run_id 记录实际来源，用于驳回定位上一节点';

CREATE TABLE `t_workflow_action_run` (
  `action_run_id` BIGINT NOT NULL COMMENT '动作运行的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '动作运行所属的流程实例 ID。',
  `node_run_id` BIGINT NOT NULL COMMENT '动作运行所属的节点运行 ID，每轮节点运行各有一份。',
  `action_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '绑定流程版本中的动作 key，同时是输出变量前缀。',
  `action_template_id` BIGINT NOT NULL COMMENT '引用的动作模板 ID；执行配置以流程版本的模板快照为准。',
  `action_type` VARCHAR(32) NOT NULL COMMENT '动作的提交与验证方式，取自版本快照。允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM。',
  `required` TINYINT NOT NULL COMMENT '1 表示必选，节点放行前必须 VERIFIED；0 表示可选，不阻塞放行。',
  `assignee_id` BIGINT NOT NULL COMMENT '当前指派的执行人 Operations 账号 ID；转交后更新。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING 未提交；VERIFIED 最后一次验证通过且不再接受提交；UNVERIFIED 最后一次验证未通过；CANCELLED 节点被终止或结束时未完成。允许值：PENDING、VERIFIED、UNVERIFIED、CANCELLED。',
  `output_json` JSON NULL COMMENT '执行人最近一次提交的输出，按动作类型与配置校验；未提交时为空。',
  `submitted_by` BIGINT NULL COMMENT '最近一次提交的 Operations 账号 ID；未提交时为空。',
  `submitted_time` DATETIME(6) NULL COMMENT '最近一次提交的 UTC 微秒时间；未提交时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`action_run_id`),
  UNIQUE KEY `uk_workflow_action_node` (`node_run_id`, `action_key`) COMMENT '保证业务唯一性：node_run_id、action_key',
  KEY `idx_workflow_action_assignee` (`assignee_id`, `status`) COMMENT '支持访问路径：assignee_id、status',
  KEY `idx_workflow_action_inst` (`instance_id`) COMMENT '支持访问路径：instance_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='节点运行中一个动作的执行情况与最近一次提交的输出；以最后一次验证为准，验证通过后锁定，变量取自最新一轮 VERIFIED 的输出';

CREATE TABLE `t_workflow_verification` (
  `verification_id` BIGINT NOT NULL COMMENT '验证记录的 Snowflake 主键。',
  `action_run_id` BIGINT NOT NULL COMMENT '被验证的动作运行 ID。',
  `instance_id` BIGINT NOT NULL COMMENT '验证所属的流程实例 ID。',
  `result` VARCHAR(32) NOT NULL COMMENT '本次验证的结论。允许值：PASSED、FAILED。',
  `evidence_json` JSON NULL COMMENT '本次验证依据的证据，如文件 ID 列表、扫描状态或对象类型、ID、版本与状态；没有证据时为空。',
  `failure_reason` VARCHAR(512) NULL COMMENT '验证未通过的中文原因；通过时为空。',
  `verified_by` BIGINT NOT NULL COMMENT '提交或重新验证的 Operations 账号 ID。',
  `verified_time` DATETIME(6) NOT NULL COMMENT '本次验证完成的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`verification_id`),
  KEY `idx_workflow_verify_action` (`action_run_id`, `verified_time`) COMMENT '支持访问路径：action_run_id、verified_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='动作每一次验证的结论、证据与未通过原因；追加后不可变，验证调用失败或超时记为 FAILED，不会误判为通过';

CREATE TABLE `t_workflow_approval` (
  `approval_id` BIGINT NOT NULL COMMENT '审批行的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '审批所属的流程实例 ID。',
  `node_run_id` BIGINT NOT NULL COMMENT '审批所属的节点运行 ID，每轮节点运行各有一组。',
  `approver_id` BIGINT NOT NULL COMMENT '当前审批人 Operations 账号 ID；转交后更新，同一节点运行内不重复。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING 待决定；APPROVED 同意；RETURNED 退回修改；REJECTED 驳回；WITHDRAWN 因他人决定或实例终止而撤回。允许值：PENDING、APPROVED、RETURNED、REJECTED、WITHDRAWN。',
  `decision_comment` VARCHAR(512) NULL COMMENT '审批人填写的意见；退回修改与驳回必填，同意可空。',
  `reject_target_node_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '汇合后第一个节点驳回时指定的目标分支末 TASK 节点 key；其他情形为空。',
  `decided_time` DATETIME(6) NULL COMMENT '审批决定或撤回的 UTC 微秒时间；待决定时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`approval_id`),
  UNIQUE KEY `uk_workflow_approval` (`node_run_id`, `approver_id`) COMMENT '保证业务唯一性：node_run_id、approver_id',
  KEY `idx_workflow_approval_user` (`approver_id`, `status`) COMMENT '支持访问路径：approver_id、status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='节点运行中每位审批人的一行审批；ANY 任一人同意即放行，ALL 需全部同意，任一人退回修改或驳回即按该决定处理，其余行撤回';

CREATE TABLE `t_workflow_transfer` (
  `transfer_id` BIGINT NOT NULL COMMENT '转交记录的 Snowflake 主键。',
  `instance_id` BIGINT NOT NULL COMMENT '转交所属的流程实例 ID。',
  `target_type` VARCHAR(32) NOT NULL COMMENT '被转交的是动作运行还是审批行。允许值：ACTION_RUN、APPROVAL。',
  `target_id` BIGINT NOT NULL COMMENT '被转交的动作运行 ID 或审批 ID，由 target_type 区分。',
  `from_user_id` BIGINT NOT NULL COMMENT '转交前的执行人或审批人 Operations 账号 ID。',
  `to_user_id` BIGINT NOT NULL COMMENT '转交后的执行人或审批人 Operations 账号 ID。',
  `reason` VARCHAR(512) NULL COMMENT '转交时填写的说明；未填写时为空。',
  `transferred_by` BIGINT NOT NULL COMMENT '执行转交的 Operations 账号 ID：原处理人本人或有实例管理权限的人。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`transfer_id`),
  KEY `idx_workflow_transfer_inst` (`instance_id`, `create_time`) COMMENT '支持访问路径：instance_id、create_time',
  KEY `idx_workflow_transfer_target` (`target_type`, `target_id`) COMMENT '支持访问路径：target_type、target_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='动作执行人或审批人的转交历史；追加后不可变，不能转给自己，也不能转给同一节点的其他审批人';

CREATE TABLE `t_workflow_callback` (
  `callback_id` BIGINT NOT NULL COMMENT '通知或回调记录的 Snowflake 主键，也是补偿任务与回调事件的业务键。',
  `instance_id` BIGINT NOT NULL COMMENT '触发通知或回调的流程实例 ID。',
  `node_run_id` BIGINT NULL COMMENT '节点级触发的节点运行 ID；实例级触发为空。',
  `trigger_code` VARCHAR(32) NOT NULL COMMENT '触发规则的状态变化；STARTED、COMPLETED、TERMINATED 为实例级，其余为节点级。允许值：ACTIVATED、APPROVAL_OPENED、PASSED、RETURNED_FOR_REVISION、REJECTED、VERIFICATION_FAILED、TRANSFERRED、STARTED、COMPLETED、TERMINATED。',
  `callback_type` VARCHAR(32) NOT NULL COMMENT 'NOTIFY 通知人员（邮件）；CALLBACK 向 topic_platform_workflow_event 发布业务事件。允许值：NOTIFY、CALLBACK。',
  `target` JSON NOT NULL COMMENT 'NOTIFY 为解析后的收件人账号 ID 列表与渠道；CALLBACK 为回调编码；登记后不可变。',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING 待发送或重试中；SENT 已送达；FAILED 超过重试上限，可人工重试。允许值：PENDING、SENT、FAILED。',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '已执行的发送次数，初始为 0。',
  `last_error` VARCHAR(512) NULL COMMENT '最近一次失败的脱敏错误摘要；未失败时为空。',
  `sent_time` DATETIME(6) NULL COMMENT '发送成功的 UTC 微秒时间；未送达时为空。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1，每次条件更新成功加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`callback_id`),
  KEY `idx_workflow_cb_inst` (`instance_id`, `create_time`) COMMENT '支持访问路径：instance_id、create_time',
  KEY `idx_workflow_cb_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='状态变化按规则触发的一次通知（邮件）或业务回调（MQ 事件）及其投递状态；与状态变化同事务登记，提交后由补偿任务执行';

-- 数据查询（2026-10-22）：三张表原样摘自 docs/database/sql/platform-support-schema.sql。
CREATE TABLE `t_data_source` (
  `data_source_id` BIGINT NOT NULL COMMENT '服务侧生成的数据源 Snowflake 主键。',
  `data_source_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的数据源编码，创建后不可修改，删除后也不复用；格式为大写字母开头的大写字母、数字与下划线。',
  `data_source_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的数据源名称。',
  `engine_type` VARCHAR(32) NOT NULL COMMENT '被查库的引擎类型，决定执行引擎与 SQL 方言。允许值：MYSQL、DORIS。',
  `host` VARCHAR(255) NOT NULL COMMENT '被查库的主机名或 IP；不保存原始 JDBC URL。',
  `port` INT NOT NULL COMMENT '被查库端口，取值 1 至 65535。',
  `database_name` VARCHAR(128) NULL COMMENT '连接后的默认库；NULL 表示连接整个实例，模板中的表必须写成 库.表。',
  `username` VARCHAR(128) NOT NULL COMMENT '被查库的只读账号；启用前检查其授权只含只读权限。',
  `password_token` VARCHAR(255) NOT NULL COMMENT '账号密码的 Vault REFERENCE Token；数据类型 DATA_SOURCE_PASSWORD_V1，保存用途 DATA_SOURCE_CONFIGURATION，建立连接时以用途 DATA_SOURCE_CONNECTION 读取；不保存密码原文。',
  `connection_params_json` JSON NULL COMMENT '驱动参数 JSON 对象，键只允许 sslMode、connectTimeout、socketTimeout、connectionTimeZone、characterEncoding，值为字符串；NULL 表示不设置。安全相关参数由服务固定，不在此保存。',
  `max_pool_size` INT NOT NULL COMMENT '该数据源连接池的最大连接数，取值 1 至 data-query.limits.max-pool-size。',
  `status` VARCHAR(32) NOT NULL COMMENT '数据源状态；只有 ACTIVE 可被查询，DELETED 为软删除，保留该行供模板历史版本显示原数据源。允许值：INACTIVE、ACTIVE、DELETED。',
  `last_check_result` VARCHAR(32) NULL COMMENT '最后一次连接检查的结果；NULL 表示从未检查。允许值：PASSED、FAILED。',
  `last_check_message` VARCHAR(512) NULL COMMENT '最后一次连接检查的说明，未通过时列出具体原因；NULL 表示从未检查。',
  `last_check_time` DATETIME(6) NULL COMMENT '最后一次连接检查的 UTC 微秒时间；NULL 表示从未检查。',
  `description` VARCHAR(512) NULL COMMENT '数据源用途等补充说明。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '数据源乐观并发版本，从 1 开始，每次配置或状态变更加一；各实例按它判断是否重建连接池。',
  `created_by` BIGINT NOT NULL COMMENT '创建该数据源的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该数据源的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`data_source_id`),
  UNIQUE KEY `uk_data_source_code` (`data_source_code`) COMMENT '保证业务唯一性：data_source_code',
  KEY `idx_data_source_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存数据查询可读取的外部只读数据库连接配置、密码 Vault Token、状态与最后一次连接检查结果；修改后各实例按版本重建连接池。';

CREATE TABLE `t_query_template` (
  `template_id` BIGINT NOT NULL COMMENT '服务侧生成的查询模板 Snowflake 主键。',
  `template_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的模板编码，创建后不可修改；执行接口按它定位模板。格式为大写字母开头的大写字母、数字与下划线。',
  `template_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的模板名称。',
  `description` VARCHAR(512) NULL COMMENT '模板用途等补充说明。',
  `data_source_id` BIGINT NOT NULL COMMENT '当前版本使用的数据源，取值为 t_data_source.data_source_id；与当前版本行的 data_source_id 同事务写入，供列表筛选与删除数据源时的引用检查。',
  `status` VARCHAR(32) NOT NULL COMMENT '模板状态；只有 ACTIVE 可执行。允许值：ACTIVE、INACTIVE。',
  `current_version_no` INT NOT NULL COMMENT '当前生效的版本号，对应 t_query_template_version.version_no；保存新版本时同事务更新。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '模板乐观并发版本，从 1 开始，每次保存、恢复或启停加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该模板的操作人，取值为 t_user_account.account_id。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该模板的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_query_template_code` (`template_code`) COMMENT '保证业务唯一性：template_code',
  KEY `idx_query_template_source` (`data_source_id`, `status`) COMMENT '支持访问路径：data_source_id、status',
  KEY `idx_query_template_status` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存查询模板的编码、名称、状态与当前生效版本；内容保存在模板版本表，每次保存生成新版本并立即生效。';

CREATE TABLE `t_query_template_version` (
  `template_version_id` BIGINT NOT NULL COMMENT '服务侧生成的模板版本 Snowflake 主键。',
  `template_id` BIGINT NOT NULL COMMENT '所属查询模板，取值为 t_query_template.template_id。',
  `version_no` INT NOT NULL COMMENT '模板内的版本号，从 1 开始，每次保存加一。',
  `data_source_id` BIGINT NOT NULL COMMENT '该版本使用的数据源，取值为 t_data_source.data_source_id。',
  `body` MEDIUMTEXT NOT NULL COMMENT '统一模板语法的模板正文，UTF-8 不超过 data-query.limits.max-body-length（默认 48 KB）。',
  `parameters_json` JSON NOT NULL COMMENT '参数声明 JSON 数组，每项含 name、type、elementType、required、default、maxLength、min、max、allowedValues、maxSize、choices、fields；type 允许值：STRING、INTEGER、DECIMAL、BOOLEAN、DATE、DATETIME、ENUM、FRAGMENT、LIST。无参数时为空数组。',
  `columns_json` JSON NULL COMMENT '出参列声明 JSON 数组，每项含 name、label、mask、keepStart、keepEnd；mask 允许值：NONE、HIDE、FULL、KEEP_EDGES。NULL 表示全部列原样返回。',
  `constraints_json` JSON NOT NULL COMMENT '模板约束 JSON 对象，含 maxRows、timeoutSeconds、pagingMode、maxPageSize、countAllowed、cursorColumns；pagingMode 允许值：NONE、OFFSET、CURSOR。取值不超过 data-query.limits 硬上限。',
  `restored_from_version_no` INT NULL COMMENT '由历史版本恢复生成时的来源版本号；NULL 表示不是恢复生成。',
  `change_note` VARCHAR(512) NULL COMMENT '保存该版本时填写的变更说明。',
  `created_by` BIGINT NOT NULL COMMENT '保存该版本的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该版本保存的 UTC 微秒时间；版本行创建后不可变，没有更新时间。',
  PRIMARY KEY (`template_version_id`),
  UNIQUE KEY `uk_query_template_ver` (`template_id`, `version_no`) COMMENT '保证业务唯一性：template_id、version_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存查询模板每次保存的不可变内容：数据源、正文、参数声明、列声明与约束；用于执行、查看、对比与恢复，创建后不修改。';

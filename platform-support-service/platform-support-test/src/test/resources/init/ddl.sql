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

CREATE TABLE `t_file_upload` (
  `upload_id` BIGINT NOT NULL COMMENT '文件上传会话的稳定业务标识。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `uploader_id` BIGINT NOT NULL COMMENT '上传用户的稳定业务标识。',
  `expected_size` BIGINT NOT NULL COMMENT '预期文件大小的长整数值。',
  `expected_mime` VARCHAR(255) NOT NULL COMMENT '预期媒体类型的业务值。',
  `owner_system` VARCHAR(64) NOT NULL COMMENT '上传目标所属业务系统代码，来自 Platform 受控代码目录；Client 入驻由 CCT 授权结果固定，浏览器不得提交。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '上传目标的受控资源类型；Client 入驻固定为 CLIENT_APPLICATION_SUBJECT，不允许上传后变更目标类型。取值来源：Platform 受控文件 Owner 类型目录；非固定枚举。',
  `owner_ref_id` VARCHAR(128) NOT NULL COMMENT '来源 Owner 定义的不可变目标坐标；Client 入驻为 CCT 的 applicationId:subjectPartyId，两段均为规范正整数 ID，不复制 Client 名称或其他可变事实。',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存受控上传的准确 Owner 目标、租户与上传人、展示名、原存储目标和冻结扫描策略；上传授权由存储适配器签发，不持久化临时 URL、签名或额外上传秘密';

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
  KEY `idx_file_object_scan_policy_id` (`scan_policy_id`) COMMENT '支持访问路径：scan_policy_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存不可变对象存储引用、内容摘要和创建时冻结的扫描策略修订';

CREATE TABLE `t_file_owner_ref` (
  `owner_ref_row_id` BIGINT NOT NULL COMMENT '文件所有者引用的稳定业务标识。',
  `file_id` BIGINT NOT NULL COMMENT '文件的稳定业务标识。',
  `owner_system` VARCHAR(64) NOT NULL COMMENT '所有者系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `owner_type` VARCHAR(64) NOT NULL COMMENT '所有者资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `owner_ref_id` VARCHAR(128) NOT NULL COMMENT '所有者资源标识的业务值。',
  `purpose_code` VARCHAR(64) NOT NULL COMMENT '文件用途代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`owner_ref_row_id`),
  UNIQUE KEY `uk_file_owner` (`file_id`, `owner_system`, `owner_type`, `owner_ref_id`, `purpose_code`) COMMENT '保证业务唯一性：file_id、owner_system、owner_type、owner_ref_id、purpose_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存文件与来源系统业务资源之间的最小安全引用';

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

CREATE TABLE `t_standard_api` (
  `standard_api_id` BIGINT NOT NULL COMMENT '标准 API 的内部技术标识。',
  `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务系统调用内部接口时使用的标准 API 编码，大小写敏感，格式为大写字母开头的字母、数字与下划线。取值来源：Platform 标准 API 目录；非固定枚举。',
  `standard_api_name` VARCHAR(128) NOT NULL COMMENT '标准 API 名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`standard_api_id`),
  UNIQUE KEY `uk_standard_api_code` (`standard_api_code`) COMMENT '保证业务唯一性：standard_api_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Platform 对内提供的标准 API 定义，例如 PAY、AUTHORIZE；请求与响应字段由 t_api_field 维护，不保存调用方白名单';

CREATE TABLE `t_api_field` (
  `api_field_id` BIGINT NOT NULL COMMENT '字段定义的内部技术标识。',
  `api_id` BIGINT NOT NULL COMMENT '所属 API 标识：方向为 STANDARD_API_* 时指向 t_standard_api.standard_api_id，为 INTEGRATION_API_* 时指向 t_integration_api.integration_api_id；由应用校验。',
  `direction` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段所属的 API 类型与请求或响应方向。允许值：STANDARD_API_REQUEST、STANDARD_API_RESPONSE、INTEGRATION_API_REQUEST、INTEGRATION_API_RESPONSE。',
  `field_location` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段在 HTTP 报文中的位置；请求允许四种，响应只允许 HEADER、BODY。允许值：HEADER、BODY、QUERY、PATH。',
  `field_path` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'BODY 字段的完整点分路径，数组元素用 [] 表示，最多 16 段、每段 1 至 64 字符；HEADER、QUERY、PATH 为参数名，HEADER 名称大小写不敏感判重由应用完成。',
  `field_name` VARCHAR(128) NOT NULL COMMENT '字段在 Operations 页面上的中文或业务显示名称。',
  `data_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '字段值的数据类型；金额与精确小数以 STRING 传递。允许值：STRING、BOOLEAN、INTEGER、DECIMAL、OBJECT、ARRAY。',
  `required_flag` BOOLEAN NOT NULL COMMENT '1 表示必填，0 表示非必填；子字段只在父对象存在时校验，响应字段只在执行状态为 SUCCESS 时校验。',
  `sort_no` INT NOT NULL COMMENT '同一 API、同一方向内的显示顺序，从小到大。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`api_field_id`),
  UNIQUE KEY `uk_api_field_path` (`api_id`, `direction`, `field_location`, `field_path`) COMMENT '保证业务唯一性：api_id、direction、field_location、field_path'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='标准 API 与渠道原子 API 共用的请求、响应字段定义；属于所属 API 的配置，随所属 API 整组替换，不单独记录版本与操作人';

CREATE TABLE `t_integration` (
  `integration_id` BIGINT NOT NULL COMMENT '外部接入方的内部技术标识。',
  `integration_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务系统调用标准 API 时传入的接入方编码，大小写敏感，不等同于 Payment Channel 编码。取值来源：Platform 外部接入方目录；非固定枚举。',
  `integration_name` VARCHAR(128) NOT NULL COMMENT '接入方名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `endpoints` JSON NOT NULL COMMENT '本环境出站地址数组 [{endpointCode, baseUrl, sandbox}]，1 至 16 项；endpointCode 在接入方内唯一，baseUrl 仅允许 https、不含用户信息、查询串与片段，最长 512 字符；sandbox 可选、缺省为 false，只有为 true 的地址允许沙箱验证运行；实际出站仍受部署网络白名单限制。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_id`),
  UNIQUE KEY `uk_integration_code` (`integration_code`) COMMENT '保证业务唯一性：integration_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道或其他出站外部接入方及本环境出站地址；与 Client Integration Readiness（PLT-I13）无关';

CREATE TABLE `t_integration_api` (
  `integration_api_id` BIGINT NOT NULL COMMENT '渠道原子 API 的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的原子 API 编码，流程节点按此引用，大小写敏感。取值来源：所属接入方的原子 API 目录；非固定枚举。',
  `api_name` VARCHAR(128) NOT NULL COMMENT 'API 名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `definition_json` JSON NOT NULL COMMENT '原子 API 执行定义：调用参数、协议固定值、传输、编解码、脚本引用、状态规则与按 SUCCESS/FAIL 分支的输出映射；序列化后最多 65536 UTF-8 字节，禁止保存凭据明文。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_api_id`),
  UNIQUE KEY `uk_integration_api_code` (`integration_id`, `api_code`) COMMENT '保证业务唯一性：integration_id、api_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方的单次外部协议操作；请求与响应结构由 t_api_field 维护，传输、编解码、脚本、状态规则与输出映射保存在 definition_json';

CREATE TABLE `t_integration_script` (
  `integration_script_id` BIGINT NOT NULL COMMENT '公共脚本的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `script_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的脚本编码，原子 API 按此引用，大小写敏感。取值来源：所属接入方的公共脚本目录；非固定枚举。',
  `script_name` VARCHAR(128) NOT NULL COMMENT '脚本名称，供 Operations 维护页面展示。',
  `execution_stage` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '脚本运行的 HTTP 处理阶段；REQUEST 读写请求工作对象，RESPONSE 读写响应工作副本。允许值：REQUEST、RESPONSE。',
  `parameter_definition` JSON NOT NULL COMMENT '脚本参数声明数组 [{name, dataType, required}]，最多 32 项，无参数时为空数组。',
  `source_code` TEXT NOT NULL COMMENT '受限 Aviator 脚本源码，最多 32 KiB UTF-8；引擎固定，不另设引擎字段。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_script_id`),
  UNIQUE KEY `uk_integration_script_code` (`integration_id`, `script_code`) COMMENT '保证业务唯一性：integration_id、script_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方内可复用的请求或响应阶段脚本；复杂签名、加密由代码注册函数提供，脚本不读取凭据明文';

CREATE TABLE `t_integration_standard_api` (
  `integration_standard_api_id` BIGINT NOT NULL COMMENT '渠道标准 API 实现的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '实现该标准 API 的外部接入方标识。',
  `standard_api_id` BIGINT NOT NULL COMMENT '被实现的标准 API 标识。',
  `flow_definition_json` JSON NOT NULL COMMENT '流程定义：总预算、初始化项、入口、原子 API 节点、返回出口与条件连线；无环，序列化后最多 65536 UTF-8 字节。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`integration_standard_api_id`),
  UNIQUE KEY `uk_integration_standard_api` (`integration_id`, `standard_api_id`) COMMENT '保证业务唯一性：integration_id、standard_api_id',
  KEY `idx_integ_std_api_standard` (`standard_api_id`) COMMENT '支持访问路径：standard_api_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方对某个标准 API 的当前实现；flow_definition_json 保存初始化、原子 API 节点、条件连线与返回出口，不保存流程实例或运行状态';

CREATE TABLE `t_integration_verify_account` (
  `verify_account_id` BIGINT NOT NULL COMMENT '验证账号的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的验证账号编码，验证用例按此引用，大小写敏感。取值来源：所属接入方的验证账号目录；非固定枚举。',
  `account_name` VARCHAR(128) NOT NULL COMMENT '账号名称，供 Operations 维护页面展示。',
  `channel_code` VARCHAR(64) NOT NULL COMMENT '账号所属渠道编码，流程初始化时与输入中的账号身份核对。',
  `channel_account_id` VARCHAR(64) NOT NULL COMMENT '流程初始化时与输入中的账号身份核对的渠道账号标识。',
  `entity_no` VARCHAR(64) NOT NULL COMMENT '流程初始化时与输入中的账号身份核对的经营主体编号。',
  `parameters` JSON NOT NULL COMMENT '普通账号参数，字符串键值，最多 64 项、每个值最多 8192 字符。',
  `credentials` JSON NOT NULL COMMENT '沙箱凭据，字符串键值，最多 64 项、每个值最多 8192 字符；执行时包装为受控句柄，只能交给注册函数。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_account_id`),
  UNIQUE KEY `uk_verify_account_code` (`integration_id`, `account_code`) COMMENT '保证业务唯一性：integration_id、account_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='沙箱验证运行使用的渠道账号材料；按用户决定以明文保存非敏感沙箱材料，不写入 Vault，正式执行不读取';

CREATE TABLE `t_integration_verify_case` (
  `verify_case_id` BIGINT NOT NULL COMMENT '验证用例的内部技术标识。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `case_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接入方内唯一的验证用例编码，大小写敏感。取值来源：所属接入方的验证用例目录；非固定枚举。',
  `case_name` VARCHAR(128) NOT NULL COMMENT '用例名称，供 Operations 维护页面展示。',
  `description` VARCHAR(512) NULL COMMENT '业务用途说明；未填写时为空。',
  `verify_account_id` BIGINT NOT NULL COMMENT '运行时使用的验证账号标识。',
  `steps_json` JSON NOT NULL COMMENT '有序步骤数组，1 至 10 步；每步含步骤标识、标准 API 编码、输入模板与期望，序列化后最多 65536 UTF-8 字节。',
  `version` INT NOT NULL COMMENT 'Operations 保存时按版本条件更新的并发版本，初始为 1；不是发布版本。',
  `created_by` BIGINT NOT NULL COMMENT '创建该配置的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该配置的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_case_id`),
  UNIQUE KEY `uk_verify_case_code` (`integration_id`, `case_code`) COMMENT '保证业务唯一性：integration_id、case_code',
  KEY `idx_verify_case_account` (`verify_account_id`) COMMENT '支持访问路径：verify_account_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='接入方的沙箱验证用例：使用的验证账号与有序步骤，每步包含标准 API、输入模板与期望';

CREATE TABLE `t_integration_verify_run` (
  `verify_run_id` BIGINT NOT NULL COMMENT '验证运行的内部技术标识，以十进制字符串作为对外运行编号。',
  `verify_case_id` BIGINT NOT NULL COMMENT '被运行的验证用例标识；用例删除后保留原值。',
  `integration_id` BIGINT NOT NULL COMMENT '所属外部接入方标识。',
  `run_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运行结论；FAILED 表示期望不满足或步骤出错，INTERRUPTED 表示执行进程中断后不再向沙箱重放。允许值：RUNNING、PASSED、FAILED、INTERRUPTED。',
  `case_snapshot_json` JSON NOT NULL COMMENT '发起运行时的用例步骤、期望、账号编码与账号版本，不含凭据。',
  `config_versions_json` JSON NULL COMMENT '运行实际加载的标准 API、接入方、原子 API、脚本与实现版本，按编码记录；运行未加载配置时为空。',
  `endpoints_json` JSON NULL COMMENT '运行时接入方出站地址及沙箱标记；运行未加载配置时为空。',
  `failure_summary` VARCHAR(512) NULL COMMENT '未通过时的首个原因；通过或运行中为空。',
  `started_by` BIGINT NOT NULL COMMENT '发起运行的 Operations 账号标识。',
  `start_time` DATETIME(6) NOT NULL COMMENT '发起运行的 UTC 微秒时间。',
  `finish_time` DATETIME(6) NULL COMMENT '运行得出结论的 UTC 微秒时间；运行中为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`verify_run_id`),
  KEY `idx_verify_run_case` (`verify_case_id`, `start_time`) COMMENT '支持访问路径：verify_case_id、start_time',
  KEY `idx_verify_run_integration` (`integration_id`, `start_time`) COMMENT '支持访问路径：integration_id、start_time'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='一次沙箱验证运行的结论与证据：运行时的用例快照、配置版本与出站地址；记录不删除，用例删除后仍保留';

CREATE TABLE `t_integration_verify_run_step` (
  `verify_run_step_id` BIGINT NOT NULL COMMENT '运行步骤记录的内部技术标识。',
  `verify_run_id` BIGINT NOT NULL COMMENT '所属验证运行标识。',
  `step_no` INT NOT NULL COMMENT '步骤在用例中的顺序，从 1 开始。',
  `step_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用例内的步骤标识，供后续步骤模板引用。',
  `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该步骤执行的标准 API 编码。',
  `step_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '步骤结论；SKIPPED 表示前序步骤未通过而未执行。允许值：PASSED、FAILED、SKIPPED。',
  `resolved_input_json` JSON NULL COMMENT '模板渲染后的标准 API 输入；未执行或渲染失败时为空。',
  `result_json` JSON NULL COMMENT '执行结果：已执行时为执行状态与输出，执行前返回问题时为 HTTP 状态与错误码；未执行时为空。',
  `assertions_json` JSON NULL COMMENT '每条期望的实际值与是否满足；未执行时为空。',
  `trace_json` JSON NULL COMMENT '初始化项、节点、连线与出口轨迹及脱敏后的渠道请求与响应原文，单段报文最多保留 65536 字节；未执行时为空。',
  `elapsed_ms` INT NULL COMMENT '步骤执行耗时，单位毫秒；未执行时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`verify_run_step_id`),
  UNIQUE KEY `uk_verify_run_step` (`verify_run_id`, `step_no`) COMMENT '保证业务唯一性：verify_run_id、step_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='验证运行中一个步骤的实际输入、结果、期望核对与脱敏轨迹；运行结束时一次写入，不再修改';

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

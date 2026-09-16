-- REVIEW CANDIDATE. Existing tables must be absent or compared exactly; never overwrite them.
SET NAMES utf8mb4;

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
  `upload_token_hash` CHAR(64) NOT NULL COMMENT '上传令牌哈希的 SHA-256 小写十六进制值。',
  `expires_time` DATETIME(6) NOT NULL COMMENT '上传到期时间对应的 UTC 微秒时间。',
  `status` VARCHAR(64) NOT NULL COMMENT '上传状态的稳定业务代码。允许值：CREATED、UPLOADING、COMPLETED、EXPIRED、CANCELLED。',
  `version` INT NOT NULL COMMENT '上传并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`upload_id`),
  UNIQUE KEY `uk_upload_token` (`upload_token_hash`) COMMENT '保证业务唯一性：upload_token_hash',
  KEY `idx_file_upload_tenant` (`tenant_id`, `status`, `create_time`) COMMENT '支持访问路径：tenant_id、status、create_time',
  KEY `idx_file_upload_due` (`status`, `expires_time`) COMMENT '支持访问路径：status、expires_time',
  KEY `idx_file_upload_uploader_id` (`uploader_id`) COMMENT '支持访问路径：uploader_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存访问租户内受控文件上传的大小、媒体类型、一次性令牌摘要和到期状态';

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

CREATE TABLE `t_access_tenant` (
  `tenant_id` bigint NOT NULL COMMENT '访问租户的稳定业务标识。',
  `tenant_type` varchar(64) NOT NULL COMMENT '租户类型的稳定业务代码。允许值：CLIENT、PARTNER、INTERNAL。',
  `owner_ref_id` bigint NOT NULL COMMENT '租户所有者引用的稳定业务标识。',
  `tenant_name` varchar(255) NOT NULL COMMENT '租户显示名称的业务值。',
  `owner_status` varchar(64) NOT NULL COMMENT '所有者业务状态的稳定业务代码。允许值：PENDING、ACTIVE、SUSPENDED、TERMINATED。',
  `source_version` int DEFAULT NULL COMMENT '来源投影版本的整数值。',
  `source_payload_hash` char(64) DEFAULT NULL COMMENT '来源投影载荷哈希的 SHA-256 小写十六进制值。',
  `security_status` varchar(64) NOT NULL COMMENT '平台安全状态的稳定业务代码。允许值：ACTIVE、SUSPENDED。',
  `version` int NOT NULL COMMENT '租户并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`tenant_id`),
  UNIQUE KEY `uk_access_tenant` (`tenant_type`,`owner_ref_id`) COMMENT '保证业务唯一性：tenant_type、owner_ref_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Client、Local Partner 或内部运营的访问隔离边界';

CREATE TABLE `t_api_credential` (
  `credential_id` bigint NOT NULL COMMENT '客户端接口凭证的稳定业务标识。',
  `tenant_id` bigint NOT NULL COMMENT '访问租户的稳定业务标识。',
  `key_prefix` varchar(32) NOT NULL COMMENT '密钥前缀的业务值。',
  `key_verifier_phc` varchar(512) NOT NULL COMMENT '密钥 PHC 校验值的业务值。',
  `expires_time` datetime(6) NOT NULL COMMENT '凭证到期时间对应的 UTC 微秒时间。',
  `status` varchar(64) NOT NULL COMMENT '凭证状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  `rotated_from_id` bigint DEFAULT NULL COMMENT '轮换来源凭证的稳定业务标识。',
  `version` int NOT NULL COMMENT '凭证并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`credential_id`),
  UNIQUE KEY `uk_key_prefix` (`key_prefix`) COMMENT '保证业务唯一性：key_prefix',
  KEY `idx_api_credential_tenant` (`tenant_id`,`status`,`expires_time`) COMMENT '支持访问路径：tenant_id、status、expires_time',
  KEY `idx_api_credenti_rotated_from_i` (`rotated_from_id`) COMMENT '支持访问路径：rotated_from_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 CLIENT Tenant API Key 的有界查找前缀、PHC 校验值和生命周期；Scope 行是唯一授权来源';

CREATE TABLE `t_api_credential_scope` (
  `scope_id` bigint NOT NULL COMMENT '接口凭证范围的稳定业务标识。',
  `credential_id` bigint NOT NULL COMMENT '接口凭证的稳定业务标识。',
  `scope_code` varchar(128) NOT NULL COMMENT '授权范围代码的业务值。',
  `resource_pattern` varchar(255) NOT NULL COMMENT '资源模式的业务值。',
  `action_code` varchar(64) NOT NULL COMMENT '操作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `effective_from` datetime(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` datetime(6) DEFAULT NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `status` varchar(64) NOT NULL COMMENT '范围状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  `version` int NOT NULL COMMENT '范围并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`scope_id`),
  UNIQUE KEY `uk_credential_scope` (`credential_id`,`scope_code`,`resource_pattern`,`action_code`) COMMENT '保证业务唯一性：credential_id、scope_code、resource_pattern、action_code',
  KEY `idx_api_scope_credential` (`scope_code`,`credential_id`) COMMENT '支持访问路径：scope_code、credential_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 API Credential 的可查询和可审计授权明细';

CREATE TABLE `t_api_scope` (
  `api_scope_id` bigint NOT NULL COMMENT 'API Scope 目录的服务侧 Snowflake 主键；与凭证授权明细 scope_id 不同。',
  `scope_code` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '机器 API 权限的稳定小写 ASCII 编码，取值由本目录管理；创建后不可修改或回收复用，Route 与授权记录按此编码引用。',
  `display_name` varchar(255) NOT NULL COMMENT 'API Scope 的非空展示名称，可由获授权运营人员修改，不参与认证匹配。',
  `description` varchar(500) DEFAULT NULL COMMENT 'API Scope 的业务能力说明；未填写时为 NULL，不保存秘密、凭证值或客户资料。',
  `version` int NOT NULL COMMENT '目录记录的乐观并发版本，创建为 1；每次成功修改递增 1，不表示历史业务版本。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`api_scope_id`),
  UNIQUE KEY `uk_api_scope_code` (`scope_code`) COMMENT '保证业务唯一性：scope_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存机器 API Scope 的全局定义和展示说明；不保存凭证授权、目录状态或版本历史';

CREATE TABLE `t_audit_chain` (
  `chain_id` bigint NOT NULL COMMENT '审计哈希链的稳定业务标识。',
  `chain_key` varchar(128) NOT NULL COMMENT '审计链键的业务值。',
  `partition_date` date NOT NULL COMMENT '该审计链所属的 UTC 日历日期。',
  `shard_no` int NOT NULL COMMENT '审计分片序号的整数值。',
  `previous_partition_hash` char(64) DEFAULT NULL COMMENT '前分区终止哈希的 SHA-256 小写十六进制值。',
  `last_sequence_no` int NOT NULL COMMENT '最后审计序号的整数值。',
  `last_event_hash` char(64) DEFAULT NULL COMMENT '最后审计事件哈希的 SHA-256 小写十六进制值。',
  `status` varchar(64) NOT NULL COMMENT '审计链状态的稳定业务代码。允许值：OPEN、SEALED、ARCHIVED。',
  `version` int NOT NULL COMMENT '审计链并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`chain_id`),
  UNIQUE KEY `uk_audit_chain_key` (`chain_key`) COMMENT '保证业务唯一性：chain_key',
  UNIQUE KEY `uk_audit_chain` (`partition_date`,`shard_no`) COMMENT '保证业务唯一性：partition_date、shard_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存日期与 Shard 级审计 Hash Chain 的尾指针和跨分区连接摘要';

CREATE TABLE `t_audit_event` (
  `audit_event_id` bigint NOT NULL COMMENT '审计事件的稳定业务标识。',
  `chain_id` bigint NOT NULL COMMENT '审计链的稳定业务标识。',
  `sequence_no` int NOT NULL COMMENT '审计序号的整数值。',
  `previous_event_hash` char(64) DEFAULT NULL COMMENT '前审计事件哈希的 SHA-256 小写十六进制值。',
  `canonical_version` int NOT NULL COMMENT '规范化算法版本的整数值。',
  `attribute_root_hash` char(64) NOT NULL COMMENT '审计属性根哈希的 SHA-256 小写十六进制值。',
  `event_hash` char(64) NOT NULL COMMENT '审计事件哈希的 SHA-256 小写十六进制值。',
  `source_system` varchar(64) DEFAULT NULL COMMENT '来源系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `source_event_key` varchar(128) DEFAULT NULL COMMENT '来源事件键的业务值。',
  `source_version` int DEFAULT NULL COMMENT '来源版本的整数值。',
  `source_payload_hash` char(64) DEFAULT NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  `schema_version` int NOT NULL COMMENT '审计结构版本的整数值。',
  `actor_type` varchar(64) NOT NULL COMMENT '操作主体类型的稳定业务代码。允许值：USER、CREDENTIAL、WORKLOAD、SYSTEM。',
  `actor_id` varchar(128) NOT NULL COMMENT '操作主体标识的业务值。',
  `tenant_id` bigint DEFAULT NULL COMMENT '访问租户的稳定业务标识。',
  `action_code` varchar(64) NOT NULL COMMENT '审计动作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `resource_system` varchar(64) NOT NULL COMMENT '资源所有系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `resource_type` varchar(64) NOT NULL COMMENT '资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `resource_id` varchar(128) NOT NULL COMMENT '资源标识的业务值。',
  `result` varchar(64) NOT NULL COMMENT '审计结果的稳定业务代码。允许值：SUCCEEDED、DENIED、FAILED。',
  `request_id` varchar(128) NOT NULL COMMENT '请求标识的业务值。',
  `trace_id` varchar(128) NOT NULL COMMENT '链路标识的业务值。',
  `occurred_time` datetime(6) NOT NULL COMMENT '审计发生时间对应的 UTC 微秒时间。',
  `received_time` datetime(6) NOT NULL COMMENT '审计接收时间对应的 UTC 微秒时间。',
  `payload_hash` char(64) NOT NULL COMMENT '本地审计载荷哈希的 SHA-256 小写十六进制值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`audit_event_id`),
  UNIQUE KEY `uk_audit_sequence` (`chain_id`,`sequence_no`) COMMENT '保证业务唯一性：chain_id、sequence_no',
  UNIQUE KEY `uk_audit_event_hash` (`event_hash`) COMMENT '保证业务唯一性：event_hash',
  UNIQUE KEY `uk_audit_source` (`source_system`,`source_event_key`) COMMENT '保证业务唯一性：source_system、source_event_key',
  KEY `idx_audit_resource` (`resource_system`,`resource_type`,`resource_id`,`occurred_time`) COMMENT '支持访问路径：resource_system、resource_type、resource_id、occurred_time',
  KEY `idx_audit_actor` (`actor_type`,`actor_id`,`occurred_time`) COMMENT '支持访问路径：actor_type、actor_id、occurred_time',
  KEY `idx_audit_event_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变保存操作、文件访问和敏感 Reveal 的规范化可验证审计事实';

CREATE TABLE `t_audit_event_attr` (
  `attr_id` bigint NOT NULL COMMENT '审计事件属性的稳定业务标识。',
  `audit_event_id` bigint NOT NULL COMMENT '审计事件的稳定业务标识。',
  `attr_no` int NOT NULL COMMENT '审计属性序号的整数值。',
  `attr_key` varchar(128) NOT NULL COMMENT '审计属性键的业务值。',
  `value_text` varchar(1000) DEFAULT NULL COMMENT '审计属性文本值的业务值。',
  `value_vault_token` varchar(255) DEFAULT NULL COMMENT '审计属性 Vault Token的业务值。',
  `value_masked` varchar(1000) DEFAULT NULL COMMENT '审计属性脱敏值的业务值。',
  `value_hash` char(64) NOT NULL COMMENT '审计属性值哈希的 SHA-256 小写十六进制值。',
  `classification` varchar(64) NOT NULL COMMENT '审计属性分类的稳定业务代码。允许值：PUBLIC、INTERNAL、CONFIDENTIAL、RESTRICTED。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`attr_id`),
  UNIQUE KEY `uk_audit_attr` (`audit_event_id`,`attr_no`) COMMENT '保证业务唯一性：audit_event_id、attr_no',
  KEY `idx_audit_attr_key` (`attr_key`,`value_hash`) COMMENT '支持访问路径：attr_key、value_hash'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变保存公开脱敏标量或受 Vault 保护敏感值的严格互斥属性';

CREATE TABLE `t_auth_session` (
  `session_id` bigint NOT NULL COMMENT '认证会话的稳定业务标识。',
  `account_id` bigint NOT NULL COMMENT '用户账号的稳定业务标识。',
  `realm` varchar(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `channel` varchar(64) NOT NULL COMMENT '认证渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER。',
  `context_type` varchar(64) NOT NULL COMMENT '会话上下文类型的稳定业务代码。允许值：ACCOUNT、TENANT、BOOTSTRAP。',
  `tenant_id` bigint DEFAULT NULL COMMENT '访问租户的稳定业务标识。',
  `tenant_member_id` bigint DEFAULT NULL COMMENT '租户成员的稳定业务标识。',
  `authentication_method` varchar(64) NOT NULL COMMENT '认证方式的稳定业务代码。允许值：PASSWORD、GOOGLE。',
  `account_auth_epoch` int NOT NULL COMMENT '账号认证纪元快照的整数值。',
  `credential_version_snapshot` int NOT NULL COMMENT '凭据版本快照的整数值。',
  `issued_time` datetime(6) NOT NULL COMMENT '会话签发时间对应的 UTC 微秒时间。',
  `last_reauthenticated_time` datetime(6) NOT NULL COMMENT '最近重新认证时间对应的 UTC 微秒时间。',
  `last_activity_time` datetime(6) NOT NULL COMMENT '最近活动时间对应的 UTC 微秒时间。',
  `idle_expire_time` datetime(6) NOT NULL COMMENT '空闲到期时间对应的 UTC 微秒时间。',
  `absolute_expire_time` datetime(6) NOT NULL COMMENT '绝对到期时间对应的 UTC 微秒时间。',
  `current_generation_no` int NOT NULL COMMENT '当前刷新代次的整数值。',
  `status` varchar(64) NOT NULL COMMENT '认证会话状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED、COMPROMISED。',
  `revocation_reason` varchar(64) DEFAULT NULL COMMENT '撤销原因的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `revoked_time` datetime(6) DEFAULT NULL COMMENT '撤销时间对应的 UTC 微秒时间。',
  `version` int NOT NULL COMMENT '认证会话并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`session_id`),
  KEY `idx_auth_session_account` (`account_id`,`realm`,`status`,`absolute_expire_time`) COMMENT '支持访问路径：account_id、realm、status、absolute_expire_time',
  KEY `idx_auth_session_member` (`tenant_member_id`,`tenant_id`,`account_id`) COMMENT '支持访问路径：tenant_member_id、tenant_id、account_id',
  KEY `idx_auth_session_expire` (`status`,`idle_expire_time`,`absolute_expire_time`) COMMENT '支持访问路径：status、idle_expire_time、absolute_expire_time',
  KEY `idx_auth_session_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存单一 Realm、Channel 和 Account/Tenant/Bootstrap 上下文的可撤销认证 Session Family';

CREATE TABLE `t_authentication_challenge` (
  `challenge_id` bigint NOT NULL COMMENT '认证挑战的稳定业务标识。',
  `challenge_no` varchar(64) NOT NULL COMMENT '认证挑战编号的业务值。',
  `realm` varchar(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `purpose` varchar(64) NOT NULL COMMENT '认证挑战用途的稳定业务代码。允许值：SIGN_UP_EMAIL、PASSWORD_RESET、EMAIL_CHANGE、TENANT_INVITATION_ACCEPT。',
  `account_id` bigint DEFAULT NULL COMMENT '用户账号的稳定业务标识。',
  `tenant_invitation_id` bigint DEFAULT NULL COMMENT '租户邀请的稳定业务标识。',
  `email_vault_token` varchar(255) NOT NULL COMMENT '挑战邮箱 Vault Token的业务值。',
  `email_masked` varchar(255) NOT NULL COMMENT '挑战邮箱脱敏值的业务值。',
  `active_scope_hash` char(64) NOT NULL COMMENT '活动挑战范围哈希的 SHA-256 小写十六进制值。',
  `secret_hmac` char(64) NOT NULL COMMENT 'KMS 密钥对挑战标识、用途、范围和高熵秘密生成的 HMAC-SHA-256 小写十六进制值。',
  `hmac_key_version` int NOT NULL COMMENT '挑战 HMAC 密钥版本的整数值。',
  `expires_time` datetime(6) NOT NULL COMMENT '挑战到期时间对应的 UTC 微秒时间。',
  `max_attempts` int NOT NULL DEFAULT '5' COMMENT '认证挑战固定允许五次校验尝试。',
  `used_attempts` int NOT NULL DEFAULT '0' COMMENT '认证挑战已经消耗的校验尝试次数。',
  `status` varchar(64) NOT NULL COMMENT '认证挑战状态的稳定业务代码。允许值：PENDING、CONSUMED、EXPIRED、SUPERSEDED、REVOKED。',
  `active_slot` int DEFAULT NULL COMMENT '活动挑战占位的整数值。',
  `consumed_time` datetime(6) DEFAULT NULL COMMENT '挑战消费时间对应的 UTC 微秒时间。',
  `supersedes_challenge_id` bigint DEFAULT NULL COMMENT '被替代挑战的稳定业务标识。',
  `version` int NOT NULL COMMENT '认证挑战并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`challenge_id`),
  UNIQUE KEY `uk_auth_challenge_no` (`challenge_no`) COMMENT '保证业务唯一性：challenge_no',
  UNIQUE KEY `uk_auth_challenge_active` (`active_scope_hash`,`active_slot`) COMMENT '保证业务唯一性：active_scope_hash、active_slot',
  KEY `idx_auth_challenge_account` (`account_id`,`status`) COMMENT '支持访问路径：account_id、status',
  KEY `idx_auth_challenge_invite` (`tenant_invitation_id`,`status`) COMMENT '支持访问路径：tenant_invitation_id、status',
  KEY `idx_auth_challenge_expire` (`status`,`expires_time`) COMMENT '支持访问路径：status、expires_time',
  KEY `idx_authenticati_supersedes_cha` (`supersedes_challenge_id`) COMMENT '支持访问路径：supersedes_challenge_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存注册、密码重置、邮箱变更或租户邀请接受的一次性认证挑战 HMAC，不复用业务邮箱验证码';

CREATE TABLE `t_command_idempotency` (
  `id` bigint NOT NULL COMMENT '命令幂等记录的稳定业务标识。',
  `caller_scope` char(64) NOT NULL COMMENT '规范调用方范围哈希的 SHA-256 小写十六进制值。',
  `operation_type` varchar(64) NOT NULL COMMENT '幂等操作类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键的业务值。',
  `request_hash` char(64) NOT NULL COMMENT '幂等请求哈希的 SHA-256 小写十六进制值。',
  `response_ref` varchar(255) DEFAULT NULL COMMENT '幂等响应引用的业务值。',
  `status` varchar(64) NOT NULL COMMENT '幂等执行状态的稳定业务代码。允许值：PROCESSING、SUCCEEDED、FAILED。',
  `expire_time` datetime(6) NOT NULL COMMENT '幂等记录到期时间对应的 UTC 微秒时间。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_command_idempotency` (`caller_scope`,`operation_type`,`idempotency_key`) COMMENT '保证业务唯一性：caller_scope、operation_type、idempotency_key',
  KEY `idx_command_idem_expire` (`status`,`expire_time`) COMMENT '支持访问路径：status、expire_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存认证调用边界内 HTTP 或内部命令的请求摘要与稳定响应引用';

CREATE TABLE `t_console_resource` (
  `resource_id` bigint NOT NULL COMMENT '控制台界面资源的稳定业务标识。',
  `resource_code` varchar(128) NOT NULL COMMENT '控制台资源代码的业务值。',
  `channel` varchar(64) NOT NULL COMMENT '控制台渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER。',
  `resource_type` varchar(64) NOT NULL COMMENT '控制台资源类型的稳定业务代码。允许值：MENU、BUTTON。',
  `parent_resource_id` bigint DEFAULT NULL COMMENT '父控制台资源的稳定业务标识。',
  `display_name` varchar(255) NOT NULL COMMENT '展示名称的业务值。',
  `frontend_route` varchar(1000) DEFAULT NULL COMMENT '前端相对路由的业务值。',
  `component_key` varchar(255) DEFAULT NULL COMMENT '前端组件注册键的业务值。',
  `permission_id` bigint NOT NULL COMMENT '可见性权限的稳定业务标识。',
  `sort_order` int NOT NULL COMMENT '同级排序序号的整数值。',
  `status` varchar(64) NOT NULL COMMENT '控制台资源状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  `version` int NOT NULL COMMENT '控制台资源并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`resource_id`),
  UNIQUE KEY `uk_console_resource_code` (`resource_code`) COMMENT '保证业务唯一性：resource_code',
  UNIQUE KEY `uk_console_resource_channel` (`resource_id`,`channel`) COMMENT '保证业务唯一性：resource_id、channel',
  KEY `idx_console_resource_parent` (`parent_resource_id`,`channel`,`sort_order`) COMMENT '支持访问路径：parent_resource_id、channel、sort_order',
  KEY `idx_console_resource_permission` (`permission_id`) COMMENT '支持访问路径：permission_id',
  KEY `idx_console_resource_channel` (`channel`,`status`,`parent_resource_id`,`sort_order`) COMMENT '支持访问路径：channel、status、parent_resource_id、sort_order'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Operations、Client、Partner Console 的当前 Menu/Button 导航资源；可见性复用 Permission，但不替代 BFF 授权';

CREATE TABLE `t_gateway_guard_rule` (
  `guard_rule_id` bigint NOT NULL COMMENT 'Gateway 路由保护规则的稳定业务标识。',
  `route_id` bigint NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `rule_type` varchar(64) NOT NULL COMMENT '保护规则类型的稳定业务代码。允许值：RATE_LIMIT、CONCURRENCY。',
  `dimension` varchar(64) NOT NULL COMMENT '保护规则维度的稳定业务代码。允许值：SHARED_ROUTE、CLIENT_IP、AUTHENTICATED_PRINCIPAL、TENANT。',
  `capacity` bigint DEFAULT NULL COMMENT '令牌容量的长整数值。',
  `refill_tokens` bigint DEFAULT NULL COMMENT '补充令牌数的长整数值。',
  `refill_period_millis` bigint DEFAULT NULL COMMENT '补充周期毫秒数的长整数值。',
  `concurrency_limit` bigint DEFAULT NULL COMMENT '并发上限的长整数值。',
  PRIMARY KEY (`guard_rule_id`),
  UNIQUE KEY `uk_gateway_guard_rule` (`route_id`,`rule_type`,`dimension`) COMMENT '保证业务唯一性：route_id、rule_type、dimension'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Route 当前统一的 Rate 或 Concurrency 参数；Redis 只保存运行状态，Tenant 维度使用可信租户身份分桶';

CREATE TABLE `t_gateway_route` (
  `route_id` bigint NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `route_code` varchar(128) NOT NULL COMMENT 'Gateway 路由代码的业务值。',
  `channel` varchar(64) NOT NULL COMMENT 'Gateway 路由渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、CHANNEL_CALLBACK。',
  `http_method` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Gateway 预编译精确 HTTP Method 集合中的 ASCII 大写方法。取值来源：Gateway 预编译精确 HTTP Method 集合；非固定枚举。',
  `external_path_template` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ASCII 编码、大小写敏感的规范化公共 Path Template。',
  `downstream_path_template` varchar(1000) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'ASCII 编码、大小写敏感的规范化目标 Path Template。',
  `target_id` bigint NOT NULL COMMENT 'Gateway 受控目标的稳定业务标识。',
  `security_profile_code` varchar(64) NOT NULL COMMENT '安全档案代码的稳定业务代码。允许值：OPERATIONS_JWT、CLIENT_JWT、PARTNER_JWT、CLIENT_API_CREDENTIAL、CHECKOUT_SESSION、CHANNEL_CALLBACK、PUBLIC。',
  `authorization_mode` varchar(64) NOT NULL COMMENT '授权模式的稳定业务代码。允许值：PUBLIC、HUMAN_AUTHENTICATED、HUMAN_PERMISSION、API_SCOPE、CHECKOUT_RESOURCE、CHANNEL_CALLBACK。',
  `permission_id` bigint DEFAULT NULL COMMENT '操作权限的稳定业务标识。',
  `required_scope_code` varchar(128) DEFAULT NULL COMMENT '必需 API Scope 代码的业务值。',
  `request_cookie_mode` varchar(64) NOT NULL COMMENT '请求 Cookie 模式的稳定业务代码。允许值：DROP、REQUIRE_PROFILE_COOKIE。',
  `response_cookie_mode` varchar(64) NOT NULL COMMENT '响应 Cookie 模式的稳定业务代码。允许值：NONE、ISSUE_ONLY、ISSUE_OR_CLEAR、CLEAR_ONLY。',
  `body_limit_bytes` bigint NOT NULL COMMENT '请求体字节上限的长整数值。',
  `body_receive_timeout_millis` int NOT NULL COMMENT '请求体接收超时毫秒数的整数值。',
  `response_timeout_millis` int NOT NULL COMMENT '目标响应超时毫秒数的整数值。',
  `status` varchar(64) NOT NULL COMMENT 'Gateway 路由状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  `version` int NOT NULL COMMENT 'Gateway 路由并发版本的整数值。',
  `created_by` bigint NOT NULL COMMENT '创建账号的稳定业务标识。',
  `updated_by` bigint NOT NULL COMMENT '修改账号的稳定业务标识。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`route_id`),
  UNIQUE KEY `uk_gateway_route_code` (`route_code`) COMMENT '保证业务唯一性：route_code',
  UNIQUE KEY `uk_gateway_route_path` (`http_method`,`external_path_template`) COMMENT '保证业务唯一性：http_method、external_path_template',
  KEY `idx_gateway_route_target` (`target_id`,`channel`,`status`) COMMENT '支持访问路径：target_id、channel、status',
  KEY `idx_gateway_route_channel` (`channel`,`status`,`target_id`) COMMENT '支持访问路径：channel、status、target_id',
  KEY `idx_gateway_route_permission` (`permission_id`) COMMENT '支持访问路径：permission_id',
  KEY `idx_gateway_route_scope` (`required_scope_code`,`route_id`) COMMENT '支持访问路径：required_scope_code、route_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存六类外部 HTTP 入口的当前 Route、受控 Target、安全档案、授权模式和传输上限，不保存运行时计数或发布版本';

CREATE TABLE `t_gateway_route_header` (
  `route_header_id` bigint NOT NULL COMMENT 'Gateway 路由 Header 的稳定业务标识。',
  `route_id` bigint NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `header_name` varchar(255) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL COMMENT 'ASCII HTTP field-name Token；大小写不敏感唯一，Platform/Gateway 仍执行完整 Allowlist 校验。取值来源：RFC 9110 HTTP field-name token 与 Platform/Gateway Allowlist；非固定枚举。',
  `header_use` varchar(64) NOT NULL COMMENT 'Header 用途的稳定业务代码。允许值：CORS_REQUEST_ALLOWED、FORWARD_REQUEST。',
  PRIMARY KEY (`route_header_id`),
  UNIQUE KEY `uk_gateway_route_header` (`route_id`,`header_name`,`header_use`) COMMENT '保证业务唯一性：route_id、header_name、header_use'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Route 当前允许的完整 Header 名称及用途；聚合更新时整体替换，不开放通配、前缀或可信身份 Header';

CREATE TABLE `t_gateway_route_query_param` (
  `route_query_param_id` bigint NOT NULL COMMENT 'Gateway 路由查询参数的稳定业务标识。',
  `route_id` bigint NOT NULL COMMENT 'Gateway 公共路由的稳定业务标识。',
  `parameter_name` varchar(255) NOT NULL COMMENT '查询参数名称的业务值。',
  PRIMARY KEY (`route_query_param_id`),
  UNIQUE KEY `uk_gateway_route_query` (`route_id`,`parameter_name`) COMMENT '保证业务唯一性：route_id、parameter_name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Route 当前允许的完整 Query Parameter 名称；未声明名称由 Gateway 在调用目标前拒绝';

CREATE TABLE `t_gateway_target` (
  `target_id` bigint NOT NULL COMMENT 'Gateway 受控目标的稳定业务标识。',
  `target_service_code` varchar(128) NOT NULL COMMENT '目标服务代码的业务值。',
  `discovery_service_name` varchar(255) NOT NULL COMMENT '服务发现名称的业务值。',
  `channel` varchar(64) NOT NULL COMMENT 'Gateway 目标渠道的稳定业务代码。允许值：OPERATIONS、CLIENT、PARTNER、CHECKOUT、CLIENT_API、CHANNEL_CALLBACK。',
  `status` varchar(64) NOT NULL COMMENT 'Gateway 目标状态的稳定业务代码。允许值：ACTIVE、DISABLED。',
  `version` int NOT NULL COMMENT 'Gateway 目标并发版本的整数值。',
  `created_by` bigint NOT NULL COMMENT '创建账号的稳定业务标识。',
  `updated_by` bigint NOT NULL COMMENT '修改账号的稳定业务标识。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`target_id`),
  UNIQUE KEY `uk_gateway_target_code` (`target_service_code`) COMMENT '保证业务唯一性：target_service_code',
  UNIQUE KEY `uk_gateway_target_channel` (`target_id`,`channel`) COMMENT '保证业务唯一性：target_id、channel',
  KEY `idx_gateway_target_status` (`channel`,`status`) COMMENT '支持访问路径：channel、status'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存由 Platform 统一管理的公共入口目标当前配置；Gateway 从完整快照读取目标身份与服务发现名称并执行通用校验';

CREATE TABLE `t_jwt_signing_key` (
  `signing_key_id` bigint NOT NULL COMMENT 'JWT 签名密钥的稳定业务标识。',
  `realm` varchar(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `kid` varchar(128) NOT NULL COMMENT '签名密钥公开标识的业务值。',
  `algorithm` varchar(64) NOT NULL COMMENT '签名算法的稳定业务代码。允许值：RS256。',
  `kms_key_ref` varchar(512) NOT NULL COMMENT 'KMS 非秘密密钥引用的业务值。',
  `public_jwk_schema_version` int NOT NULL COMMENT '公钥 JWK 结构版本的整数值。',
  `public_jwk_canonical_json` json NOT NULL COMMENT '规范公钥 JWK的有界版本化 JSON 内容。',
  `status` varchar(64) NOT NULL COMMENT '签名密钥状态的稳定业务代码。允许值：PREPARED、ACTIVE、RETIRING、RETIRED。',
  `active_slot` int DEFAULT NULL COMMENT '当前签名密钥占位的整数值。',
  `not_before` datetime(6) NOT NULL COMMENT '签名生效起点对应的 UTC 微秒时间。',
  `signing_end_time` datetime(6) DEFAULT NULL COMMENT '签名截止时间对应的 UTC 微秒时间。',
  `verification_end_time` datetime(6) DEFAULT NULL COMMENT '验签截止时间对应的 UTC 微秒时间。',
  `version` int NOT NULL COMMENT '签名密钥并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`signing_key_id`),
  UNIQUE KEY `uk_jwt_signing_realm_kid` (`realm`,`kid`) COMMENT '保证业务唯一性：realm、kid',
  UNIQUE KEY `uk_jwt_signing_active` (`realm`,`active_slot`) COMMENT '保证业务唯一性：realm、active_slot',
  KEY `idx_jwt_signing_verify_end` (`status`,`verification_end_time`) COMMENT '支持访问路径：status、verification_end_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Realm 隔离的 RS256 JWT 签名密钥公开材料、KMS 引用和轮换状态；Canonical JSON 仅允许有界版本化 RSA 公钥 JWK';

CREATE TABLE `t_password_credential` (
  `password_credential_id` bigint NOT NULL COMMENT '密码凭据的稳定业务标识。',
  `account_id` bigint NOT NULL COMMENT '用户账号的稳定业务标识。',
  `password_verifier_phc` varchar(512) NOT NULL COMMENT '密码 PHC 校验值的业务值。',
  `algorithm_code` varchar(64) NOT NULL COMMENT '密码算法的稳定业务代码。允许值：ARGON2ID。',
  `algorithm_version` int NOT NULL COMMENT '密码算法版本的整数值。',
  `memory_kib` int NOT NULL COMMENT 'Argon2 内存参数的整数值。',
  `iterations` int NOT NULL COMMENT 'Argon2 迭代次数的整数值。',
  `parallelism` int NOT NULL COMMENT 'Argon2 并行度的整数值。',
  `credential_version` int NOT NULL COMMENT '密码凭据版本的整数值。',
  `force_change_flag` tinyint(1) NOT NULL COMMENT '强制改密标志，1 表示是，0 表示否。',
  `status` varchar(64) NOT NULL COMMENT '密码凭据状态的稳定业务代码。允许值：ACTIVE、REVOKED。',
  `password_changed_time` datetime(6) NOT NULL COMMENT '密码变更时间对应的 UTC 微秒时间。',
  `version` int NOT NULL COMMENT '密码凭据并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`password_credential_id`),
  UNIQUE KEY `uk_password_account` (`account_id`) COMMENT '保证业务唯一性：account_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存账号唯一的当前 Argon2id PHC 校验值、算法参数和强制改密状态；Google-only 账号不创建占位行';

CREATE TABLE `t_permission` (
  `permission_id` bigint NOT NULL COMMENT '操作权限的稳定业务标识。',
  `permission_code` varchar(128) NOT NULL COMMENT '权限代码的业务值。',
  `resource_type` varchar(64) NOT NULL COMMENT '资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `action_code` varchar(64) NOT NULL COMMENT '操作代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `risk_level` varchar(64) NOT NULL COMMENT '权限风险等级的稳定业务代码。允许值：LOW、MEDIUM、HIGH、CRITICAL。',
  `status` varchar(64) NOT NULL COMMENT '权限状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  `version` int NOT NULL COMMENT '权限并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`permission_id`),
  UNIQUE KEY `uk_permission` (`permission_code`) COMMENT '保证业务唯一性：permission_code',
  UNIQUE KEY `uk_resource_action` (`resource_type`,`action_code`) COMMENT '保证业务唯一性：resource_type、action_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存资源类型与操作动作组成的稳定权限目录';

CREATE TABLE `t_refresh_token_generation` (
  `refresh_generation_id` bigint NOT NULL COMMENT '刷新令牌代次的稳定业务标识。',
  `session_id` bigint NOT NULL COMMENT '认证会话的稳定业务标识。',
  `generation_no` int NOT NULL COMMENT '刷新代次序号的整数值。',
  `token_lookup_id` varchar(128) NOT NULL COMMENT '令牌公开查找标识的业务值。',
  `token_secret_hmac` char(64) NOT NULL COMMENT 'KMS 密钥对刷新令牌高熵秘密生成的 HMAC-SHA-256 小写十六进制值。',
  `hmac_key_version` int NOT NULL COMMENT '刷新 HMAC 密钥版本的整数值。',
  `issued_time` datetime(6) NOT NULL COMMENT '刷新令牌签发时间对应的 UTC 微秒时间。',
  `expires_time` datetime(6) NOT NULL COMMENT '刷新令牌到期时间对应的 UTC 微秒时间。',
  `status` varchar(64) NOT NULL COMMENT '刷新令牌状态的稳定业务代码。允许值：ISSUED、CONSUMED、REVOKED、EXPIRED。',
  `active_slot` int DEFAULT NULL COMMENT '当前刷新代次占位的整数值。',
  `consumed_time` datetime(6) DEFAULT NULL COMMENT '刷新令牌消费时间对应的 UTC 微秒时间。',
  `replaced_by_generation_id` bigint DEFAULT NULL COMMENT '后继刷新代次的稳定业务标识。',
  `version` int NOT NULL COMMENT '刷新令牌并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`refresh_generation_id`),
  UNIQUE KEY `uk_refresh_lookup` (`token_lookup_id`) COMMENT '保证业务唯一性：token_lookup_id',
  UNIQUE KEY `uk_refresh_session_gen` (`session_id`,`generation_no`) COMMENT '保证业务唯一性：session_id、generation_no',
  UNIQUE KEY `uk_refresh_active` (`session_id`,`active_slot`) COMMENT '保证业务唯一性：session_id、active_slot',
  KEY `idx_refresh_toke_replaced_by_ge` (`replaced_by_generation_id`) COMMENT '支持访问路径：replaced_by_generation_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存认证会话一次性 Refresh Token 的公开查找标识、秘密 HMAC、轮换代次和重用检测状态';

CREATE TABLE `t_role` (
  `role_id` bigint NOT NULL COMMENT '授权角色的稳定业务标识。',
  `tenant_scope` varchar(64) NOT NULL COMMENT '租户范围类型的稳定业务代码。允许值：PLATFORM、TENANT。',
  `tenant_id` bigint DEFAULT NULL COMMENT '访问租户的稳定业务标识。',
  `scope_key` varchar(128) NOT NULL COMMENT '规范范围键的业务值。',
  `role_code` varchar(64) NOT NULL COMMENT '角色代码的业务值。',
  `role_name` varchar(255) NOT NULL COMMENT '角色名称的业务值。',
  `status` varchar(64) NOT NULL COMMENT '角色状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  `system_role` tinyint(1) NOT NULL COMMENT '系统角色标志，1 表示是，0 表示否。',
  `version` int NOT NULL COMMENT '角色并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_scope` (`scope_key`,`role_code`) COMMENT '保证业务唯一性：scope_key、role_code',
  UNIQUE KEY `uk_role_owner` (`role_id`,`scope_key`) COMMENT '保证业务唯一性：role_id、scope_key',
  KEY `idx_role_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存平台级或租户级稳定角色定义';

CREATE TABLE `t_role_permission` (
  `role_permission_id` bigint NOT NULL COMMENT '角色权限映射的稳定业务标识。',
  `role_id` bigint NOT NULL COMMENT '授权角色的稳定业务标识。',
  `permission_id` bigint NOT NULL COMMENT '操作权限的稳定业务标识。',
  `status` varchar(64) NOT NULL COMMENT '角色权限状态的稳定业务代码。允许值：ACTIVE、REVOKED。',
  `version` int NOT NULL COMMENT '角色权限并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`role_permission_id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`) COMMENT '保证业务唯一性：role_id、permission_id',
  KEY `idx_role_permiss_permission_id` (`permission_id`) COMMENT '支持访问路径：permission_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存角色与操作权限的当前授权或撤销状态；变更由 Audit Event 追加留痕';

CREATE TABLE `t_security_event` (
  `security_event_id` bigint NOT NULL COMMENT '平台安全事件的稳定业务标识。',
  `event_type` varchar(64) NOT NULL COMMENT '安全事件类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
  `severity` varchar(64) NOT NULL COMMENT '安全事件严重级别的稳定业务代码。允许值：INFO、LOW、MEDIUM、HIGH、CRITICAL。',
  `actor_ref` varchar(128) DEFAULT NULL COMMENT '安全主体引用的业务值。',
  `tenant_id` bigint DEFAULT NULL COMMENT '访问租户的稳定业务标识。',
  `source_system` varchar(64) NOT NULL COMMENT '来源系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
  `source_event_key` varchar(128) NOT NULL COMMENT '来源事件键的业务值。',
  `source_version` int NOT NULL COMMENT '来源版本的整数值。',
  `source_payload_hash` char(64) NOT NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  `schema_version` int NOT NULL COMMENT '安全事件结构版本的整数值。',
  `event_hash` char(64) NOT NULL COMMENT '安全事件哈希的 SHA-256 小写十六进制值。',
  `occurred_time` datetime(6) NOT NULL COMMENT '安全事件发生时间对应的 UTC 微秒时间。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`security_event_id`),
  UNIQUE KEY `uk_security_event` (`event_hash`) COMMENT '保证业务唯一性：event_hash',
  UNIQUE KEY `uk_security_source` (`source_system`,`source_event_key`,`event_type`) COMMENT '保证业务唯一性：source_system、source_event_key、event_type',
  KEY `idx_security_event_severity` (`severity`,`occurred_time`) COMMENT '支持访问路径：severity、occurred_time',
  KEY `idx_security_event_tenant_id` (`tenant_id`) COMMENT '支持访问路径：tenant_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变保存身份、权限、Credential、OTP、Webhook 和文件安全事件';

CREATE TABLE `t_tenant_member` (
  `member_id` bigint NOT NULL COMMENT '租户成员的稳定业务标识。',
  `tenant_id` bigint NOT NULL COMMENT '访问租户的稳定业务标识。',
  `account_id` bigint NOT NULL COMMENT '用户账号的稳定业务标识。',
  `member_type` varchar(64) NOT NULL COMMENT '成员类型的稳定业务代码。允许值：OWNER、ADMIN、MEMBER。',
  `status` varchar(64) NOT NULL COMMENT '成员状态的稳定业务代码。允许值：ACTIVE、SUSPENDED、REVOKED。',
  `invited_by` bigint DEFAULT NULL COMMENT '邀请操作成员的稳定业务标识。',
  `version` int NOT NULL COMMENT '成员并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_tenant_member` (`tenant_id`,`account_id`) COMMENT '保证业务唯一性：tenant_id、account_id',
  UNIQUE KEY `uk_tenant_member_owner` (`member_id`,`tenant_id`) COMMENT '保证业务唯一性：member_id、tenant_id',
  UNIQUE KEY `uk_tenant_member_identity` (`member_id`,`tenant_id`,`account_id`) COMMENT '保证业务唯一性：member_id、tenant_id、account_id',
  KEY `idx_tenant_member_inviter` (`invited_by`,`tenant_id`) COMMENT '支持访问路径：invited_by、tenant_id',
  KEY `idx_tenant_member_account_id` (`account_id`) COMMENT '支持访问路径：account_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存全局用户账号加入访问租户后的独立授权状态；Realm、Tenant 类型和当前状态一致性由事务规则锁定';

CREATE TABLE `t_tenant_member_role` (
  `tenant_member_role_id` bigint NOT NULL COMMENT '成员角色授权的稳定业务标识。',
  `tenant_member_id` bigint NOT NULL COMMENT '租户成员的稳定业务标识。',
  `tenant_id` bigint NOT NULL COMMENT '访问租户的稳定业务标识。',
  `role_id` bigint NOT NULL COMMENT '授权角色的稳定业务标识。',
  `role_scope_key` varchar(128) NOT NULL COMMENT '冻结角色范围键的业务值。',
  `effective_from` datetime(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` datetime(6) DEFAULT NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `granted_by` bigint NOT NULL COMMENT '授权操作人的稳定业务标识。',
  `status` varchar(64) NOT NULL COMMENT '授权状态的稳定业务代码。允许值：ACTIVE、REVOKED、EXPIRED。',
  `version` int NOT NULL COMMENT '授权并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`tenant_member_role_id`),
  UNIQUE KEY `uk_tenant_member_role` (`tenant_member_id`,`role_id`,`effective_from`) COMMENT '保证业务唯一性：tenant_member_id、role_id、effective_from',
  KEY `idx_tenant_grant_member_scope` (`tenant_member_id`,`tenant_id`) COMMENT '支持访问路径：tenant_member_id、tenant_id',
  KEY `idx_tenant_grant_role_scope` (`role_id`,`role_scope_key`) COMMENT '支持访问路径：role_id、role_scope_key'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存租户成员被授予角色的有效期和撤销状态';

CREATE TABLE `t_user_account` (
  `account_id` bigint NOT NULL COMMENT '人工用户账号的稳定业务标识。',
  `realm` varchar(64) NOT NULL COMMENT '认证域的稳定业务代码。允许值：OPERATIONS、CUSTOMER。',
  `account_type` varchar(64) NOT NULL COMMENT '账号类型的稳定业务代码。允许值：HUMAN、BOOTSTRAP_ADMIN。',
  `login_name` varchar(64) DEFAULT NULL COMMENT '非邮箱登录名的业务值。',
  `email_vault_token` varchar(255) DEFAULT NULL COMMENT '登录邮箱 Vault Token的业务值。',
  `email_masked` varchar(255) DEFAULT NULL COMMENT '登录邮箱脱敏值的业务值。',
  `email_verified_time` datetime(6) DEFAULT NULL COMMENT '邮箱验证时间对应的 UTC 微秒时间。',
  `status` varchar(64) NOT NULL COMMENT '账号状态的稳定业务代码。允许值：PENDING_VERIFICATION、ACTIVE、DISABLED、CLOSED。',
  `consecutive_password_failures` int NOT NULL DEFAULT '0' COMMENT '当前账号连续密码校验失败的累计次数。',
  `locked_until` datetime(6) DEFAULT NULL COMMENT '临时锁定截止时间对应的 UTC 微秒时间。',
  `last_password_failure_time` datetime(6) DEFAULT NULL COMMENT '最近密码失败时间对应的 UTC 微秒时间。',
  `credential_version` int NOT NULL COMMENT '账号凭据版本的整数值。',
  `auth_epoch` int NOT NULL COMMENT '账号认证纪元的整数值。',
  `version` int NOT NULL COMMENT '账号并发版本的整数值。',
  `create_time` datetime(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` datetime(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_account_realm` (`account_id`,`realm`) COMMENT '保证业务唯一性：account_id、realm',
  UNIQUE KEY `uk_user_account_email` (`realm`,`email_vault_token`) COMMENT '保证业务唯一性：realm、email_vault_token',
  UNIQUE KEY `uk_user_account_login` (`realm`,`login_name`) COMMENT '保证业务唯一性：realm、login_name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 OPERATIONS 或 CUSTOMER Realm 的全局人工登录账号、锁定状态和认证撤销版本';

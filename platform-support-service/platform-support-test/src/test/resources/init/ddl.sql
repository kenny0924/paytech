-- 仅供人工审阅并初始化 local-environment 的空本地 Schema；应用和测试绝不自动执行。
-- 完整表定义原样摘自 docs/database/sql/platform-support-schema.sql；已有库勿重复建表或重置。
-- platform_app 为 local-environment 既有本地账号；真实/共享环境不得使用本文件。
DROP DATABASE mor_platform;
CREATE DATABASE IF NOT EXISTS mor_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'platform_app'@'%' IDENTIFIED BY 'local-platform-change-me-2026';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_platform.* TO 'platform_app'@'%';
USE mor_platform;

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

CREATE TABLE `t_api_credential` (
  `credential_id` BIGINT NOT NULL COMMENT '客户端接口凭证的稳定业务标识。',
  `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
  `key_prefix` VARCHAR(32) NOT NULL COMMENT '密钥前缀的业务值。',
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


-- Notification 普通 Email 首期；不自动执行。
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
  KEY `idx_notification_notification_r` (`notification_route_id`) COMMENT '支持访问路径：notification_route_id'
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
  `status` VARCHAR(16) NOT NULL COMMENT '启用、停用。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '并发更新版本，初始为 1；不是历史修订号。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`provider_id`),
  UNIQUE KEY `uk_notify_provider_code` (`provider_code`) COMMENT '保证业务唯一性：provider_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知发送账号的当前权威事实；普通 Email 首期，不保存认证秘密或正文快照';

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


-- Notification 复用的公共可靠任务表；来源：docs/database/sql/platform-support-schema.sql。
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

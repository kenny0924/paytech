-- 2026-09-21 普通 Email 首期；仅用于尚无 Notification 表的库，评审后手工执行。
-- 来源：platform-support-service/docs/database/platform-support.pdma.json；不自动迁移旧九表。

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

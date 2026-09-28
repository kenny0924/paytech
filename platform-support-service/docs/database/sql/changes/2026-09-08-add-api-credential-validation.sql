-- Platform Support PLT-I03 API Credential tables. Review and apply manually.
-- Preconditions: t_access_tenant already matches the current Platform Support schema;
--                neither target table exists in the selected Platform Support database.
-- Applications must not execute this file or create/seed these tables at startup.
SET NAMES utf8mb4;

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
  UNIQUE KEY `uk_credential_scope` (`credential_id`, `scope_code`, `resource_pattern`, `action_code`) COMMENT '保证业务唯一性：credential_id、scope_code、resource_pattern、action_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存 API Credential 的可查询和可审计授权明细';

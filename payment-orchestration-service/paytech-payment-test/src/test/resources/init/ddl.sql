-- ONLY mor-local MySQL at 127.0.0.1:13306; dedicated, EMPTY Channel test database.
-- REVIEW AND APPLY MANUALLY; applications and tests never execute this file.
CREATE DATABASE IF NOT EXISTS mor_payment_channel_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'payment_channel_test'@'%' IDENTIFIED BY 'local-channel-test-only';
GRANT SELECT, INSERT, UPDATE, DELETE ON mor_payment_channel_test.* TO 'payment_channel_test'@'%';
USE mor_payment_channel_test;

-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: payment-orchestration-service/docs/database/payment-orchestration.pdma.json
SET NAMES utf8mb4;

CREATE TABLE `t_payment_channel` (
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `channel_code` VARCHAR(64) NOT NULL COMMENT '平台稳定识别支付服务商的唯一业务代码。取值来源：Channel Catalog；非固定枚举。',
  `channel_name` VARCHAR(255) NOT NULL COMMENT '供运营配置和审阅展示的支付服务商正式名称。',
  `channel_status` VARCHAR(64) NOT NULL COMMENT '支付服务商从草稿、激活、暂停到关闭的当前运行状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_id`),
  UNIQUE KEY `uk_payment_channel_code` (`channel_code`) COMMENT '唯一索引：channel_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='支付渠道的稳定身份和当前运行状态。';

CREATE TABLE `t_channel_account` (
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账号所属 Operating Entity 的不可变 CHAR(5) 编号，来源于 CCT；与其当前绑定 MID 的 OE 一致，不作为路由规则。',
  `account_name` VARCHAR(128) NOT NULL COMMENT '运营维护的账号展示名称，不作为唯一技术身份。',
  `account_vault_token` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '引用 CHANNEL_ACCOUNT_CONFIG_V1 的 REFERENCE Token，含账号业务参数及渠道凭据；仅执行前以 CHANNEL_EXECUTION 用途读取；DRAFT 可空，其他状态执行时必须存在；不得进入日志或事件。',
  `account_status` VARCHAR(64) NOT NULL COMMENT '技术认证账户或租户从草稿、激活、暂停到关闭的当前状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `remark` VARCHAR(512) NULL COMMENT '运营维护的非敏感补充说明；无备注时为空，禁止填写凭据或个人敏感值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`channel_account_id`),
  UNIQUE KEY `uk_channel_account_owner` (`channel_account_id`, `channel_id`) COMMENT '唯一索引：channel_account_id、channel_id',
  KEY `idx_channel_account_status` (`channel_id`, `account_status`) COMMENT '访问索引：channel_id、account_status',
  KEY `idx_channel_account_entity` (`entity_no`, `account_status`) COMMENT '访问索引：entity_no、account_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='归属单个 Channel 与 OE 的技术账号；账号参数及凭据只保存 Vault 引用。';

CREATE TABLE `t_channel_mid` (
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `channel_id` BIGINT NOT NULL COMMENT 'Payment 管理的支付服务商内部唯一标识。',
  `channel_account_id` BIGINT NOT NULL COMMENT 'Payment 管理的服务商技术认证账户或租户内部唯一标识。',
  `external_mid_code` VARCHAR(128) NOT NULL COMMENT '服务商侧识别该 MID 的稳定代码，不保存凭据或 Secret。取值来源：Channel Adapter Catalog；非固定枚举。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT唯一不可变OE编号，创建时经营国家二位代码加001–999，如AE001；不是数字代理ID，编号不参与路由。',
  `mid_status` VARCHAR(64) NOT NULL COMMENT '服务商商户号从草稿、激活、暂停到关闭的当前状态。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  `activated_at` DATETIME(6) NULL COMMENT '商户号首次进入可承接生产交易状态的业务时刻。',
  `closed_at` DATETIME(6) NULL COMMENT '商户号永久进入关闭状态的业务时刻；未关闭时为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`mid_id`),
  UNIQUE KEY `uk_channel_mid` (`channel_id`, `external_mid_code`) COMMENT '唯一索引：channel_id、external_mid_code',
  KEY `idx_channel_mid_account` (`channel_account_id`, `mid_status`) COMMENT '访问索引：channel_account_id、mid_status',
  KEY `idx_channel_mid_entity` (`entity_no`, `mid_status`) COMMENT '访问索引：entity_no、mid_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务商商户号与唯一 Operating Entity 的稳定归属及当前 Channel Account 绑定；归属创建后不得改写。';

CREATE TABLE `t_mid_capability` (
  `mid_capability_id` BIGINT NOT NULL COMMENT '一个 MID 在完整交易条件组合下的能力配置组标识；明细按能力代码分别解析。',
  `mid_id` BIGINT NOT NULL COMMENT 'Payment 管理且唯一归属于一个 Operating Entity 的服务商商户号标识。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '支付尝试冻结本次使用的精确支付方式目录值；商户号 Capability 与 Health State 使用精确值，Routing Policy 可使用精确值或控制通配符 *。取值来源：Payment Method Catalog；非固定枚举。',
  `scheme_code` VARCHAR(64) NOT NULL COMMENT '支付体系或网络代码，可为精确代码或 *；请求未提供体系时仅匹配 *。取值来源：Scheme Catalog；非固定枚举。',
  `country_code` CHAR(2) NOT NULL COMMENT '匹配 Commerce 冻结到 Payment Intent 的消费者国家，可为精确两位代码或 *。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT '金额事实采用 ISO 4217 三位币种；配置 Scope 允许控制通配符 *，交易事实必须使用精确币种。取值来源：ISO 4217；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT '渠道批准用于该 MID 的精确四位业务 MCC，不允许 *，不代表默认收单 MCC。取值来源：CCT/Commerce frozen classification；非固定枚举。',
  `capability_status` VARCHAR(64) NOT NULL COMMENT '整组能力条件的启停状态；DISABLED 不参与匹配，不等价于任一 BOOLEAN 能力的 false。允许值：ENABLED、DISABLED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`mid_capability_id`),
  UNIQUE KEY `uk_mid_capability_scope` (`mid_id`, `payment_method_code`, `scheme_code`, `country_code`, `currency`, `mcc_code`) COMMENT '唯一索引：mid_id、payment_method_code、scheme_code、country_code、currency、mcc_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='商户号按支付方式、支付体系、消费者国家、币种和精确 MCC 定义的能力条件组。';

CREATE TABLE `t_mid_capability_detail` (
  `mid_capability_detail_id` BIGINT NOT NULL COMMENT '能力条件组内一项类型化能力值的唯一标识。',
  `mid_capability_id` BIGINT NOT NULL COMMENT '一条商户号支付方式、卡组织、地域与动作能力配置的内部唯一标识。',
  `capability_code` VARCHAR(128) NOT NULL COMMENT 'MID 固定五维 Scope 下受控的静态路由能力代码。取值来源：MID Capability Catalog；非固定枚举。',
  `value_type` VARCHAR(64) NOT NULL COMMENT 'MID 静态能力值采用 BOOLEAN、DECIMAL、INTEGER 或 CODE 的闭合类型。允许值：BOOLEAN、DECIMAL、CODE。',
  `boolean_value` TINYINT(1) NULL COMMENT 'BOOLEAN 类型的能力值：1 明确支持，0 明确不支持；其他类型必须为 NULL。',
  `decimal_value` DECIMAL(24,8) NULL COMMENT 'DECIMAL 类型的精确能力值；金额能力使用交易币种主单位；其他类型必须为 NULL。',
  `code_value` VARCHAR(128) NULL COMMENT 'CODE 类型的能力目录值；其他类型必须为 NULL；允许值由该能力的注册定义限定。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`mid_capability_detail_id`),
  UNIQUE KEY `uk_mid_cap_detail_code` (`mid_capability_id`, `capability_code`) COMMENT '唯一索引：mid_capability_id、capability_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='能力条件组下的类型化能力值；按能力代码唯一，值类型与受控目录一致。';

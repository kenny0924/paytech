-- Operations 通知发送账号维护：t_notification_provider 增加账号配置字段（待执行，需授权）。
-- 目标库：Platform Support（mor_platform / mor_platform_e2e）。执行后紧接 dml.sql。
-- 回退：ALTER TABLE t_notification_provider DROP COLUMN provider_type, DROP COLUMN region, DROP COLUMN from_address,
--       DROP COLUMN configuration_set, DROP COLUMN credential_vault_token, DROP COLUMN credential_update_time;
--       （会丢失已录入的密钥 Token 引用，回退前需评估。）
ALTER TABLE `t_notification_provider`
  ADD COLUMN `provider_type` VARCHAR(32) NOT NULL COMMENT '服务商类型，由发送适配器声明，如 AMAZON_SES；创建后不可改。取值来源：通知发送适配器声明的服务商类型；非固定枚举。' AFTER `medium`,
  ADD COLUMN `region` VARCHAR(32) NULL COMMENT '服务商区域，如 me-central-1；SES 必填。' AFTER `status`,
  ADD COLUMN `from_address` VARCHAR(254) NULL COMMENT '发件地址，须为该区域已验证身份；SES 必填。' AFTER `region`,
  ADD COLUMN `configuration_set` VARCHAR(64) NULL COMMENT 'SES Configuration Set，可空。' AFTER `from_address`,
  ADD COLUMN `credential_vault_token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT 'NOTIFICATION_PROVIDER_CREDENTIAL_V1 密钥对的 Vault Token；为空表示未录入；只在发送前以 NOTIFICATION_DELIVERY Reveal，禁止日志与接口返回。' AFTER `configuration_set`,
  ADD COLUMN `credential_update_time` DATETIME(6) NULL COMMENT '最近一次录入或更换密钥的 UTC 微秒时间。' AFTER `credential_vault_token`,
  COMMENT='通知发送账号的当前权威事实；普通 Email 首期，密钥只保存其 Vault Token，不保存认证秘密或正文快照';

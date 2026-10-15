-- 需求：20261015 payment-Card加密存储（技术方案第 3 版第 3 节）
-- 性质：增量执行文件，新建三张空表（卡、卡凭证、卡交易工具），不修改已有表或数据。
-- 目标库：Payment 业务库（本机测试库 mor_payment_channel_test）。新建库使用同步后的 Schema 与测试 init/ddl.sql。
-- 执行：由用户授权后手工执行；应用不自动执行。本机测试库已有第 2 版的空表 t_payment_card，须先单独 DROP（见 delivery.md 执行记录）。
-- 回滚：依次 DROP TABLE `t_payment_card_instrument`、`t_payment_card_credential`、`t_payment_card`（仅在没有需要保留的数据时执行）。

CREATE TABLE `t_payment_card` (
  `payment_card_id` BIGINT NOT NULL COMMENT 'Card 卡的 Snowflake 内部主键，创建后不可修改。',
  `card_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '以配置指纹密钥对卡号做 HMAC-SHA256 的小写十六进制；同一卡号恒定，用于按卡号定位与 Risk 评估，不能反推卡号。',
  `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡号不少于 16 位时为前 8 位，否则为前 6 位；创建后不可修改。',
  `pan_last4` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡号后 4 位；创建后不可修改。',
  `encrypted_pan` VARBINARY(128) NOT NULL COMMENT '以本卡 DEK 做 AES-256-GCM 加密的卡号：IV(12) ‖ 密文 ‖ Tag(16)，AAD 绑定 payment_card_id。',
  `dek_vault_token` CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本卡数据加密密钥（DEK）在 Vault 数据类型 PAYMENT_CARD_DEK 下的 REFERENCE Token；DEK 明文不落库。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_id`),
  UNIQUE KEY `uk_payment_card_fingerprint` (`card_fingerprint`) COMMENT '唯一索引：card_fingerprint'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Card 拥有的卡，每个卡号一条（按卡指纹唯一），每张卡一把由 Vault 托管的 DEK；只加密保存卡号，不存 CVV、磁道、PIN 或 DEK 明文。';

CREATE TABLE `t_payment_card_credential` (
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '卡凭证的 Snowflake 内部主键，创建后不可修改。',
  `payment_card_id` BIGINT NOT NULL COMMENT '所属卡，创建后不可修改。',
  `credential_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '以配置指纹密钥对卡号、有效期与规范化持卡人做 HMAC-SHA256 的小写十六进制；同一张卡下唯一。',
  `encrypted_details` VARBINARY(1024) NOT NULL COMMENT '以所属卡 DEK 做 AES-256-GCM 加密的有效期与持卡人原值：IV(12) ‖ 密文 ‖ Tag(16)，AAD 绑定 payment_card_credential_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡凭证创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡凭证最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_credential_id`),
  UNIQUE KEY `uk_payment_card_credential` (`payment_card_id`, `credential_digest`) COMMENT '唯一索引：payment_card_id、credential_digest'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='同一张卡下每组有效期与规范化持卡人一条；有效期与持卡人原值以所属卡的 DEK 加密保存。';

CREATE TABLE `t_payment_card_instrument` (
  `payment_card_instrument_id` BIGINT NOT NULL COMMENT '卡交易工具的 Snowflake 内部主键；其十进制字符串即支付工具 Token 的 instrument_ref。',
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '本次使用的卡凭证，创建后不可修改。',
  `client_id` BIGINT NOT NULL COMMENT '所属 CCT Client 稳定标识，取自已核对的 Payment Intent，创建后不可修改。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡交易工具创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡交易工具最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_instrument_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='每个支付工具一条，记录本次使用的卡凭证与所属 Client；主键十进制字符串即支付工具 Token 的 instrument_ref。';

-- 需求：20261015 commerce-Customer与存卡支付（技术方案 §2.4）—— Payment 库
-- 性质：增量执行文件。卡凭证与卡交易工具各加三列，新建已存卡表；不改已有数据。
-- 前提：t_payment_card_credential、t_payment_card_instrument 为空（新增 NOT NULL 列无默认值）。各环境目前均为空表。
-- 目标库：Payment 业务库（本机测试库 mor_payment_channel_test）。新建库使用同步后的 Schema 与测试 init/ddl.sql。
-- 执行：由用户授权后手工执行；应用不自动执行。
-- 回滚：DROP TABLE `t_payment_stored_card`；再对两表 DROP COLUMN 本文件新增的列（仅在没有需要保留的数据时执行）。

ALTER TABLE `t_payment_card_credential`
  ADD COLUMN `expiry_month` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡有效期月份（两位）；与已加密卡号分开保存，用于已存卡展示与过期判断，不含卡号。' AFTER `encrypted_details`,
  ADD COLUMN `expiry_year` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡有效期年份（四位）；用于已存卡展示与过期判断。' AFTER `expiry_month`,
  ADD COLUMN `verified_time` DATETIME(6) NULL COMMENT '首次以该凭证支付成功的 UTC 微秒精度时间；NULL 表示未验证，只有已验证凭证可用于存卡。' AFTER `expiry_year`;

ALTER TABLE `t_payment_card_instrument`
  ADD COLUMN `customer_id` BIGINT NULL COMMENT '本次支付关联的 Commerce Customer 标识；访客支付为 NULL。' AFTER `client_id`,
  ADD COLUMN `save_requested` TINYINT(1) NOT NULL COMMENT '消费者是否同意本次支付成功后保存卡；1 表示同意，仅在有 customer_id 时为 1。' AFTER `customer_id`,
  ADD COLUMN `payment_stored_card_id` BIGINT NULL COMMENT '用已存卡支付时指向的已存卡；手输卡为 NULL。' AFTER `save_requested`;

CREATE TABLE `t_payment_stored_card` (
  `payment_stored_card_id` BIGINT NOT NULL COMMENT '已存卡的 Snowflake 内部主键，创建后不可修改。',
  `stored_card_token` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外已存卡 Token：sc_ 前缀加随机串，按 Client 生成、不可推算；不含卡号、指纹或内部 ID。',
  `client_id` BIGINT NOT NULL COMMENT '所属 CCT Client 稳定标识，创建后不可修改。',
  `customer_id` BIGINT NOT NULL COMMENT '所属 Commerce Customer 标识，创建后不可修改；Payment 不保存 consumer_reference。',
  `payment_card_id` BIGINT NOT NULL COMMENT '已存的卡（按卡号唯一的那层），创建后不可修改。',
  `active_card_id` BIGINT NULL COMMENT '有效时等于 payment_card_id，删除时置 NULL；与 client_id、customer_id 组成唯一索引，保证同一 Customer 同一张卡最多一条有效已存卡。',
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '当前使用的已验证卡凭证；同卡以新有效期再次保存时更新。',
  `stored_card_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已存卡状态。允许值：ACTIVE、DELETED。',
  `consent_time` DATETIME(6) NOT NULL COMMENT '消费者最近一次同意保存的 UTC 微秒精度时间（支付成功时记录）。',
  `source_card_instrument_id` BIGINT NOT NULL COMMENT '首次保存来源的卡交易工具，可追溯到支付意图与来源订单。',
  `deleted_time` DATETIME(6) NULL COMMENT '删除或按 Customer 失效的 UTC 微秒精度时间；有效时为 NULL。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的已存卡创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的已存卡最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_stored_card_id`),
  UNIQUE KEY `uk_payment_stored_card_token` (`stored_card_token`) COMMENT '唯一索引：stored_card_token',
  UNIQUE KEY `uk_payment_stored_card_active` (`client_id`, `customer_id`, `active_card_id`) COMMENT '唯一索引：client_id、customer_id、active_card_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Commerce Customer 名下的已存卡：指向卡与当前已验证凭证，带消费者同意时间；删除只置状态。';

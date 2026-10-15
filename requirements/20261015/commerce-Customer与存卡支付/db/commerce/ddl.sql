-- 需求：20261015 commerce-Customer与存卡支付（技术方案 §2.1）—— Commerce 库
-- 性质：增量执行文件。新建 t_customer；t_commerce_order 增加可空列 customer_id；不改已有数据。
-- 目标库：Commerce 业务库（本机 mor_commerce）。新建库使用同步后的 Schema 与测试 init/ddl.sql。
-- 执行：由用户授权后手工执行；应用不自动执行。
-- 回滚：DROP TABLE `t_customer`；ALTER TABLE `t_commerce_order` DROP COLUMN `customer_id`（仅在没有需要保留的数据时执行）。

ALTER TABLE `t_commerce_order`
  ADD COLUMN `customer_id` BIGINT NULL COMMENT '建单时按 consumerReference 关联的 Customer；访客订单为 NULL，创建后不可修改。' AFTER `client_order_id`;

CREATE TABLE `t_customer` (
  `customer_id` BIGINT NOT NULL COMMENT 'Commerce Customer 的内部唯一标识，创建后不可修改；Payment 已存卡以此关联。',
  `customer_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '供 Client API 使用的不透明公开 Customer 编号，cus_ 前缀加随机串，创建后不可修改。',
  `client_id` BIGINT NOT NULL COMMENT '所属平台 Client 的内部唯一标识，创建后不可修改。',
  `consumer_reference` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Client 命名空间内的不透明、非 PII 消费者引用原值，由 Client 服务端传入，创建后不可修改；不得为 Email、Phone、证件号等个人原值。',
  `active_reference` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '有效时等于 consumer_reference，删除时置 NULL；与 client_id 组成唯一索引，保证同一引用同时最多一个有效 Customer。',
  `customer_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Customer 状态，删除不可撤销。允许值：ACTIVE、DELETED。',
  `deleted_time` DATETIME(6) NULL COMMENT '删除的 UTC 微秒精度时间；有效时为 NULL。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`) COMMENT '唯一索引：customer_no',
  UNIQUE KEY `uk_customer_active_reference` (`client_id`, `active_reference`) COMMENT '唯一索引：client_id、active_reference'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client 命名空间内的 Customer：身份只来自 Client 服务端传入的 consumer_reference，首期只保存身份与状态；删除不可撤销，同一引用再次使用时新建。';

-- 订单冻结 commercial 商业关系快照：新表 t_order_relationship_binding；t_order_context 加 fund_flow、
-- effective_relationship_model_version、snapshot_model_version。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/requirement.md 第 7.4 节；technique.md 第 3.3.4、3.4.3 节。
-- 目标库：acquiring（mor_acquiring）。设计源：paytech/acquiring/docs/database/acquiring.pdma.json。
-- 执行顺序：先执行本文件，再部署写入新列 / 新表的 acquiring 镜像（旧镜像插入 t_order_context 时不写
--   effective_relationship_model_version，NOT NULL 无默认值会失败，所以新旧镜像不能与本文件交错；执行与部署同批进行）。
-- 存量：t_order_context 现有行回填 effective_relationship_model_version = 0（只记录，下游沿用旧字段），fund_flow /
--   snapshot_model_version 保持 NULL；不为存量订单补建绑定行。
-- 执行后核对：
--   SHOW CREATE TABLE t_order_relationship_binding;
--   SELECT COUNT(*) FROM t_order_context WHERE effective_relationship_model_version IS NULL;  -- 0
--   SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
--     AND TABLE_NAME = 't_order_context' AND COLUMN_NAME IN ('fund_flow','effective_relationship_model_version','snapshot_model_version');
-- 回退（须同时回退 acquiring 镜像；新表中的快照随之丢弃）：
--   DROP TABLE t_order_relationship_binding;
--   ALTER TABLE t_order_context DROP COLUMN snapshot_model_version, DROP COLUMN effective_relationship_model_version,
--     DROP COLUMN fund_flow,
--     COMMENT = '支付准备时一次建立的不可变上下文；外部事实只保存稳定引用；保存CCT批准Scope/Affiliation/Source及固定Term Version，准确Price由完整成功支付事实解析；三份接受证据及原始Context不得覆盖；PARTNER的分成版本/服务费版本/平台Party三项成组非空且Order的local_partner_id非空，非 PARTNER三项成组为空且local_partner_id为空。';
SET NAMES utf8mb4;

CREATE TABLE `t_order_relationship_binding` (
  `binding_id` BIGINT NOT NULL COMMENT '订单商业关系中一个角色绑定的内部唯一标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 订单聚合的内部唯一标识。',
  `role_code` VARCHAR(64) NOT NULL COMMENT 'commercial 商业关系快照中的角色，每个订单每个角色至多一行。允许值：CLIENT、SELLER、ACQUIRING_OE、OE_PROVIDER、FUNDS_CUSTODIAN、TAX_RESPONSIBLE、PLATFORM_FEE_PAYER、ACQUIRER_SUB_MERCHANT、REFERRER、SPLIT_PAYEE、PLATFORM。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '角色绑定的主体类型。允许值：CLIENT、OPERATING_ENTITY、LOCAL_PARTNER、PLATFORM。',
  `subject_id` VARCHAR(64) NULL COMMENT '主体标识：CLIENT 为 clientId，OPERATING_ENTITY 为 entityNo，LOCAL_PARTNER 为 localPartnerId；主体类型为 PLATFORM 时为 NULL。',
  `party_id` BIGINT NULL COMMENT '主体对应的 client-master 法律 Party 稳定标识；commercial 未提供时为 NULL，不建立跨库外键。',
  `snapshot_model_version` INT NOT NULL COMMENT '本行所属商业关系快照的推导规则版本，与订单上下文 snapshot_model_version 相同。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_order_relationship_role` (`order_id`, `role_code`) COMMENT '唯一索引：order_id、role_code'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='订单创建时冻结 commercial 返回的商业关系快照，每个角色一行，记录主体类型、主体标识与法律 Party；与订单上下文同一事务写入，之后不可变；固定 Mock 解析不写本表。';

-- 先以可空加列，回填后再改 NOT NULL，避免存量行在 ADD COLUMN 时取隐式默认值。
ALTER TABLE `t_order_context`
  ADD COLUMN `fund_flow` VARCHAR(32) NULL COMMENT 'commercial 商业关系快照的资金流向；固定 Mock 解析时为 NULL。允许值：THROUGH_CUSTODIAN、DIRECT_TO_CLIENT。' AFTER `locale`,
  ADD COLUMN `effective_relationship_model_version` INT NULL COMMENT '订单冻结的商业关系生效模型版本：0 表示快照只记录、下游沿用旧字段，1 表示下游按快照生效；固定 Mock 解析及 2026-10-24 之前的存量订单为 0。' AFTER `fund_flow`,
  ADD COLUMN `snapshot_model_version` INT NULL COMMENT '记录的商业关系快照按哪一版推导规则产生；固定 Mock 解析时为 NULL。' AFTER `effective_relationship_model_version`;

UPDATE `t_order_context` SET `effective_relationship_model_version` = 0 WHERE `effective_relationship_model_version` IS NULL;

ALTER TABLE `t_order_context`
  MODIFY COLUMN `effective_relationship_model_version` INT NOT NULL COMMENT '订单冻结的商业关系生效模型版本：0 表示快照只记录、下游沿用旧字段，1 表示下游按快照生效；固定 Mock 解析及 2026-10-24 之前的存量订单为 0。',
  COMMENT = '支付准备时一次建立的不可变上下文；外部事实只保存稳定引用；保存CCT批准Scope/Affiliation/Source及固定Term Version，准确Price由完整成功支付事实解析；三份接受证据及原始Context不得覆盖；PARTNER的分成版本/服务费版本/平台Party三项成组非空且Order的local_partner_id非空，非 PARTNER三项成组为空且local_partner_id为空；commercial 商业关系快照逐角色冻结在 t_order_relationship_binding，资金流向与两个模型版本记在本表，同一事务写入。';

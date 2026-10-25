-- 回滚（mor_acquiring）：撤销 40-acquiring-ddl.sql（acquiring 旧镜像已换回、87 之后执行；旧镜像不写新列，先换镜像会因
-- effective_relationship_model_version NOT NULL 无默认值而插入失败——所以换回旧镜像与本文件须紧接执行，期间不建单）。
-- **不可逆部分**：REAL 期间冻结的商业关系快照（t_order_relationship_binding 全表、三列取值）随之丢弃，只能从执行前备份查阅。
SET NAMES utf8mb4;
DROP TABLE IF EXISTS `t_order_relationship_binding`;
ALTER TABLE `t_order_context` DROP COLUMN `snapshot_model_version`, DROP COLUMN `effective_relationship_model_version`,
  DROP COLUMN `fund_flow`,
  COMMENT = '支付准备时一次建立的不可变上下文；外部事实只保存稳定引用；保存CCT批准Scope/Affiliation/Source及固定Term Version，准确Price由完整成功支付事实解析；三份接受证据及原始Context不得覆盖；PARTNER的分成版本/服务费版本/平台Party三项成组非空且Order的local_partner_id非空，非 PARTNER三项成组为空且local_partner_id为空。';

-- 需求：20261015 commerce-Customer与存卡支付（技术方案 §2.1、§2.5，阶段 4 C2）—— Commerce 库
-- 性质：增量执行文件。新建 t_client_stored_card_setting；不改已有表或数据。在 ddl.sql 之后执行。
-- 目标库：Commerce 业务库（本机 mor_commerce）。新建库使用同步后的 Schema 与测试 init/ddl.sql。
-- 执行：由用户授权后手工执行；应用不自动执行。开通某个 Client 用 INSERT 一行 stored_card_enabled=1（各环境按需另行授权）。
-- 回滚：DROP TABLE `t_client_stored_card_setting`（仅在没有需要保留的数据时执行）。

CREATE TABLE `t_client_stored_card_setting` (
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 的内部唯一标识；每个 Client 至多一行，没有记录视为未开通。',
  `stored_card_enabled` TINYINT(1) NOT NULL COMMENT '是否开通存卡与已存卡支付；1 开通，0 关闭。关闭后已存卡保留但不展示、不可使用。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最近一次持久化更新的 UTC 微秒精度时间。',
  PRIMARY KEY (`client_id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='按 Client 开通存卡能力（CCT 暂无按 Client 配置落点时的 Commerce 本地配置）；首期由初始化 DML 或内部调用维护，运营页面后续提供。';

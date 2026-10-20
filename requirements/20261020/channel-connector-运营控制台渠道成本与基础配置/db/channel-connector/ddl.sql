-- 执行文件：channel-connector 运营控制台需求（technique.md 第 4 节）的数据库变更，内容与
-- paytech/channel-connector/docs/database/sql/changes/2026-10-20-channel-config-change.sql 相同。审阅后由操作员手工执行。
-- 目标库：mor_channel_connector（本机 local-environment 127.0.0.1:13306；mor-test 同名库，按发布阶段另行执行）。
-- 前置状态：本库已有 17 张表（含 2026-10-19 渠道成本四表），尚无 t_channel_config_change；执行前用 SHOW TABLES 核对。
-- 影响：CREATE 1 张，不改既有表与数据；执行后为 18 张表。
-- 可重跑性：不可重跑（表已存在时 CREATE 报错且不做任何修改）。
-- 回滚：DROP TABLE t_channel_config_change;（同时丢失已写入的变更记录）
USE mor_channel_connector;
SET NAMES utf8mb4;

CREATE TABLE `t_channel_config_change` (
  `id` BIGINT NOT NULL COMMENT '服务侧生成的变更记录 Snowflake 主键。',
  `target_type` VARCHAR(64) NOT NULL COMMENT '被变更对象类型。允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION。',
  `target_id` BIGINT NOT NULL COMMENT '被变更对象在本库的主键。',
  `change_type` VARCHAR(64) NOT NULL COMMENT '变更类型。允许值：CREATE、UPDATE、STATUS、CONFIGURATION、REBIND、CANCEL。',
  `from_status` VARCHAR(64) NULL COMMENT '状态变更前的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组或成本版本的状态代码；非固定枚举。',
  `to_status` VARCHAR(64) NULL COMMENT '状态变更后的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组或成本版本的状态代码；非固定枚举。',
  `operator_id` BIGINT NOT NULL COMMENT '执行变更的 Platform Actor 稳定业务 ID。',
  `reason` VARCHAR(512) NULL COMMENT '操作者填写的非敏感原因；禁止填写凭据。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`id`),
  KEY `idx_channel_config_change_target` (`target_type`, `target_id`, `id`) COMMENT '支持访问路径：target_type、target_id、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='渠道配置与成本版本的人工变更留痕；与业务变更同事务写入，只增不改。';

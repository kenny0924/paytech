-- 阶段 3 已批准设计的阶段 4 实现，未执行；唯一目标为已批准环境的 mor_risk。
-- 先停止 Risk 写入并备份。仅在新增根表不存在时运行；不是应用启动迁移。
-- 使用 mysql --batch，禁止 --force / 自动忽略错误。结构须先落入获批 PDManer。
SET @risk_saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE';
CREATE TEMPORARY TABLE risk_rule_set_ddl_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO risk_rule_set_ddl_gate
SELECT IF(DATABASE() = 'mor_risk', 'approved database name', NULL);
INSERT INTO risk_rule_set_ddl_gate
SELECT IF(NOT EXISTS (SELECT 1 FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_rule_set'), 'new root table absent', NULL);

CREATE TABLE `t_rule_set` (
  `rule_set_id` BIGINT NOT NULL COMMENT '规则集整体管理身份，创建后不变；由服务侧 Snowflake 生成，历史回填使用单独审阅的身份映射。',
  `decision_point_id` BIGINT NOT NULL COMMENT '所属 Risk 决策点标识；与规则集编码共同确定创建后不可改变的业务身份。',
  `rule_set_code` VARCHAR(64) NOT NULL COMMENT '决策点内永久保留的规则集编码。删除后不回收复用。取值来源：Risk 规则配置管理；非固定枚举。',
  `current_rule_set_version_id` BIGINT NOT NULL COMMENT '当前最高业务版本的规则集版本标识；必须属于同一决策点和编码。删除后保留，用于审计和命令回放。',
  `status` VARCHAR(64) NOT NULL COMMENT '规则集整体管理状态，与版本冻结和发布生效状态独立。允许值：CONFIGURABLE、DELETED。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '整体管理并发版本，从 1 开始；保存新业务版本、首次冻结当前版本及整体删除各增加 1，幂等回放不增加。',
  `deleted_by` BIGINT NULL COMMENT '整体删除的 Operations 操作者标识，来源 Platform Account；CONFIGURABLE 时为空，DELETED 时必填且不可改写。',
  `deleted_time` DATETIME(6) NULL COMMENT '整体删除事实在 Risk 本地事务中形成的 UTC 微秒时间；CONFIGURABLE 时为空，DELETED 时必填且不可改写。',
  `delete_reason_code` VARCHAR(64) NULL COMMENT '整体删除的固定业务原因；CONFIGURABLE 时为空，DELETED 时为 OPERATOR_REQUEST。允许值：OPERATOR_REQUEST。',
  `delete_trigger_id` CHAR(64) NULL COMMENT '由删除命令身份按专用域分隔规则生成的 SHA-256 小写十六进制触发标识，不保存原始幂等键；仅 DELETED 必填且不可改写。',
  `create_time` DATETIME(6) NOT NULL COMMENT '规则集首次形成配置的 UTC 微秒时间；历史回填取首个业务版本创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '整体管理状态或当前版本指针最后改变的 UTC 微秒时间；命令成功回放不改变。',
  PRIMARY KEY (`rule_set_id`),
  UNIQUE KEY `uk_rule_set_code` (`decision_point_id`, `rule_set_code`) COMMENT '保证业务唯一性：decision_point_id、rule_set_code',
  UNIQUE KEY `uk_rule_set_current` (`current_rule_set_version_id`) COMMENT '保证业务唯一性：current_rule_set_version_id',
  UNIQUE KEY `uk_rule_set_delete_trigger` (`delete_trigger_id`) COMMENT '保证业务唯一性：delete_trigger_id',
  KEY `idx_rule_set_list` (`status`, `decision_point_id`, `current_rule_set_version_id`) COMMENT '支持访问路径：status、decision_point_id、current_rule_set_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则集整体管理生命周期及当前配置入口；历史版本和发布闭包保持原有不可变事实';

DROP TEMPORARY TABLE risk_rule_set_ddl_gate;
SET SESSION sql_mode = @risk_saved_sql_mode;
-- 下一步是同目录 dml.sql。任何失败都停止，不运行旧 Risk 程序补建或修复根。

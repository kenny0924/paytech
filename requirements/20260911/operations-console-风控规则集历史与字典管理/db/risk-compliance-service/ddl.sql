-- RCO-16 已确认的增量 SQL；各环境执行状态见 delivery.md，禁止未核对检查点直接重跑。仅连接经批准环境的 mor_risk。
-- 先确认备份、停写窗口、模型评审与当前 Schema；mysql --batch，禁止 --force。
-- DDL 隐式提交；下列检查不是事务回滚保证。部分执行后停止并按检查点恢复，禁止直接重跑。
SET @rco16_risk_saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION,NO_ZERO_DATE,NO_ZERO_IN_DATE';
CREATE TEMPORARY TABLE rco16_risk_gate (checkpoint VARCHAR(100) NOT NULL);
INSERT INTO rco16_risk_gate SELECT IF(DATABASE() = 'mor_risk', 'exact database name', NULL);
INSERT INTO rco16_risk_gate
SELECT IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
  AND table_name IN ('t_rule_reject_reason','t_rule_subject_tag')) = 0,
  'two new tables absent', NULL);
INSERT INTO rco16_risk_gate
SELECT IF((SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
  AND table_name IN ('t_risk_rule','t_rule_action_parameter')) = 2
  AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND ((table_name = 't_risk_rule' AND index_name = 'idx_rule_reason')
    OR (table_name = 't_rule_action_parameter' AND index_name = 'idx_rule_param_string'))) = 0,
  'existing tables present and new indexes absent', NULL);
INSERT INTO rco16_risk_gate
SELECT IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 't_risk_rule' AND is_nullable = 'YES'
  AND ((column_name = 'reason_code' AND column_type = 'varchar(64)')
    OR (column_name = 'reason_message' AND column_type = 'varchar(500)'))) = 2
  AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 't_rule_action_parameter' AND column_name = 'string_value'
    AND column_type = 'varchar(512)' AND is_nullable = 'YES') = 1,
  'exact existing modified column shapes', NULL);

CREATE TABLE `t_rule_reject_reason` (
  `rejection_reason_id` BIGINT NOT NULL COMMENT '拒绝原因定义标识，由 Risk 服务侧 Snowflake 生成。',
  `reason_code` VARCHAR(64) NOT NULL COMMENT '规则 DENY 结果的拒绝原因编码，创建后固定且永久不复用。取值来源：Risk 运营维护的拒绝原因目录；非固定枚举。',
  `reason_name` VARCHAR(100) NOT NULL COMMENT '拒绝原因当前显示名称，1 至 100 字符；不代表历史规则保存时的名称。',
  `description` VARCHAR(500) NULL COMMENT '拒绝原因的内部含义说明，最多 500 字符；空串规范为 NULL。',
  `default_message` VARCHAR(500) NOT NULL COMMENT '选择该原因时带入规则的默认内部文案，1 至 500 字符；不回写已保存规则，不作为外部错误消息。',
  `status` VARCHAR(64) NOT NULL COMMENT '字典选择资格状态；停用仅禁止新选，不撤销已有配置。允许值：ACTIVE、DISABLED。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '字典并发版本，创建为 1；更新或启停必须匹配期望版本，每次成功更新增加 1。',
  `created_by` BIGINT NOT NULL COMMENT '创建该字典的认证 Operations Account 标识，由 BFF 可信上下文传入。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该字典的认证 Operations Account 标识；创建时等于 created_by。',
  `create_time` DATETIME(6) NOT NULL COMMENT '字典创建事实的 UTC 微秒时间，由 Risk 应用生成。',
  `update_time` DATETIME(6) NOT NULL COMMENT '字典最后成功变更的 UTC 微秒时间，用于人工核对当前状态。',
  PRIMARY KEY (`rejection_reason_id`),
  UNIQUE KEY `uk_rule_reject_reason_code` (`reason_code`) COMMENT '拒绝原因编码永久唯一，包括停用记录',
  KEY `idx_rule_reject_reason_list` (`status`, `reason_code`) COMMENT '支持按选择资格和编码排序的字典查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则 DENY 原因的业务字典；当前目录与不可变规则消息相互独立';

CREATE TABLE `t_rule_subject_tag` (
  `tag_id` BIGINT NOT NULL COMMENT '规则主体标签定义标识，由 Risk 服务侧 Snowflake 生成。',
  `tag_code` VARCHAR(64) NOT NULL COMMENT '主体标签编码，创建后固定且永久不复用。取值来源：Risk 运营维护的主体标签目录；非固定枚举。',
  `tag_name` VARCHAR(100) NOT NULL COMMENT '主体标签当前显示名称，1 至 100 字符；不代表历史配置时的名称。',
  `description` VARCHAR(500) NULL COMMENT '标签的内部业务含义说明，最多 500 字符；空串规范为 NULL。',
  `status` VARCHAR(64) NOT NULL COMMENT '标签新选择资格状态，不表示真实主体的标签状态。允许值：ACTIVE、DISABLED。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '标签并发版本，创建为 1；更新或启停必须匹配期望版本，每次成功更新增加 1。',
  `created_by` BIGINT NOT NULL COMMENT '创建标签的认证 Operations Account 标识，由 BFF 可信上下文传入。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改标签的认证 Operations Account 标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '标签定义创建的 UTC 微秒时间，由 Risk 应用生成。',
  `update_time` DATETIME(6) NOT NULL COMMENT '标签定义最后成功变更的 UTC 微秒时间，用于人工核对当前状态。',
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_rule_subject_tag_code` (`tag_code`) COMMENT '主体标签编码永久唯一，包括停用记录',
  KEY `idx_rule_subject_tag_list` (`status`, `tag_code`) COMMENT '支持按选择资格和编码排序的标签查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='规则可配置的主体标签定义，不存储任何主体的实际标签实例';

ALTER TABLE `t_risk_rule`
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '随不可变规则版本保存的原因代码；DENY 新选择由 Risk 拒绝原因字典治理，旧手工代码按基线兼容；其他终态保留原合同。取值来源：Risk 拒绝原因目录；历史手工代码按基线兼容；非固定枚举。',
  MODIFY COLUMN `reason_message` VARCHAR(500) NULL COMMENT '随不可变规则版本保存的内部拒绝文案；选原因时可带出默认值并单独覆盖，历史与非拒绝规则可空，不作为外部错误消息。',
  ADD KEY `idx_rule_reason` (`reason_code`,`rule_set_version_id`) COMMENT '支持由原因代码反查保存规则版本，查询仍验证 DENY 终态';
ALTER TABLE `t_rule_action_parameter`
  ADD KEY `idx_rule_param_string` (`string_value`,`parameter_definition_id`,`rule_action_id`) COMMENT '支持标签字面量反查；须同时验证 TAG 参数定义和 ADD_RISK_TAG 动作';

INSERT INTO rco16_risk_gate
SELECT IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name IN ('t_rule_reject_reason','t_rule_subject_tag')) = 21,
  'two tables have exactly 21 columns', NULL);
DROP TEMPORARY TABLE rco16_risk_gate;
SET SESSION sql_mode = @rco16_risk_saved_sql_mode;
-- 无回填、无种子、无旧规则 Hash 重算。DDL 成功后另做模型/完整 Schema 对照；不据此宣称业务验证通过。

-- 2026-09-11 Risk V1 规则配置人工增量；仅适用已核对的旧 Schema，一次性执行。
-- 执行前停止相关本地服务并备份受影响表。DDL 隐式提交；失败时先核对已完成步骤，禁止直接重跑。
-- 不修改已有规则、Hash、幂等记录或真实执行事实。共享/生产环境另行审核。
SET time_zone = '+00:00';

ALTER TABLE `t_rule_set_version`
  ADD COLUMN `content_format` VARCHAR(64) NOT NULL DEFAULT 'LEGACY' COMMENT '选择不可变内容与请求的规范投影合同；LEGACY 保存原合同，CONFIGURATION 保存附加动作与手工文案；不得根据空字段猜测历史格式。允许值：LEGACY、CONFIGURATION。' AFTER `content_hash`;

ALTER TABLE `t_risk_rule`
  ADD COLUMN `reason_message` VARCHAR(500) NULL COMMENT '随规则不可变版本保存的手工内部拒绝说明；历史记录与非拒绝规则可空，不作为对外错误消息。' AFTER `reason_code`,
  MODIFY COLUMN `reason_code` VARCHAR(64) NULL COMMENT '业务原因代码的稳定业务代码。取值来源：Risk 规则配置中手工输入的稳定原因代码，未来由原因目录选择；非固定枚举。';

ALTER TABLE `t_rule_action`
  ADD COLUMN `target_binding_code` VARCHAR(64) NULL COMMENT '配置动作引用的不可变工程主体绑定；终态动作必须为空，不保存实际主体标识。取值来源：Risk 按决策点发布的主体绑定目录；非固定枚举。' AFTER `sequence_no`;

ALTER TABLE `t_action_version`
  MODIFY COLUMN `handler_code` VARCHAR(64) NULL COMMENT '已注册执行实现的 Handler 编码；CONFIGURATION_ONLY 及其 RETIRED 历史配置为空，REGISTERED/ACTIVE 与执行版本历史必须有真实工程证据。取值来源：Risk 工程注册 Action Handler 目录；非固定枚举。',
  MODIFY COLUMN `implementation_version` VARCHAR(64) NULL COMMENT '已注册执行实现的版本；CONFIGURATION_ONLY 时为空，不使用占位版本。',
  MODIFY COLUMN `implementation_hash` CHAR(64) NULL COMMENT '真实执行实现的 SHA-256 小写十六进制值；CONFIGURATION_ONLY 时为空，不以配置定义 Hash 冒充实现证据。',
  MODIFY COLUMN `status` VARCHAR(64) NOT NULL COMMENT '执行版本状态的稳定业务代码。允许值：CONFIGURATION_ONLY、REGISTERED、ACTIVE、RETIRED。',
  MODIFY COLUMN `action_category` VARCHAR(64) NOT NULL COMMENT 'Action 分类的稳定业务代码。允许值：DECISION、RESTRICTION、LIST、NOTIFICATION、SETTLEMENT、LIMIT、TAG。';

-- 新目录数据与唯一初始化 DML 保持相同；已有 ID 或代码冲突会失败，禁止 INSERT IGNORE/覆盖。
-- 仅配置能力：执行 Handler/Version/Hash 明确为空；不得激活或绕过冻结门禁。
INSERT INTO `t_action_version` (`action_version_id`, `action_code`, `action_name`, `action_category`, `version_no`, `handler_code`, `implementation_version`, `implementation_hash`, `terminal_flag`, `decision_code`, `merge_policy`, `shadow_supported_flag`, `status`, `create_time`, `update_time`) VALUES
  (240004, 'APPLY_COOLDOWN', '冷却', 'RESTRICTION', 1, NULL, NULL, NULL, 0, NULL, 'MAX', 0, 'CONFIGURATION_ONLY', '2026-09-11 00:00:00.000000', '2026-09-11 00:00:00.000000'),
  (240005, 'ADD_RISK_TAG', '添加主体标签', 'TAG', 1, NULL, NULL, NULL, 0, NULL, 'UNION', 0, 'CONFIGURATION_ONLY', '2026-09-11 00:00:00.000000', '2026-09-11 00:00:00.000000');

INSERT INTO `t_action_parameter_definition` (`parameter_definition_id`, `action_version_id`, `parameter_code`, `value_type`, `required_flag`, `allowed_source_type`, `target_parameter_flag`, `create_time`) VALUES
  (2400041, 240004, 'SUBJECT_ID', 'STRING', 1, 'CONTEXT', 1, '2026-09-11 00:00:00.000000'),
  (2400042, 240004, 'DURATION', 'DURATION', 1, 'LITERAL', 0, '2026-09-11 00:00:00.000000'),
  (2400051, 240005, 'SUBJECT_ID', 'STRING', 1, 'CONTEXT', 1, '2026-09-11 00:00:00.000000'),
  (2400052, 240005, 'TAG', 'STRING', 1, 'LITERAL', 0, '2026-09-11 00:00:00.000000');

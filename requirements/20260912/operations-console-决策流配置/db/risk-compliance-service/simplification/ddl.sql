-- RCO-17 精简修订 / 各环境执行记录见 README.md。这里只执行扩展阶段。
-- 必须停写相关 Risk 配置、完成 precheck.sql 和原历史 Hash 读取核对，并取得目标库授权。
-- 由人工选择 mor_risk；不在此指定环境连接，不允许应用启动或测试执行。
ALTER TABLE `t_rule_set_version`
  ADD COLUMN `input_contract_code` VARCHAR(64) NULL COMMENT '本规则集版本绑定的工程输入合同代码；与 input_contract_version 一起准确定位 Risk 输入目录，不经旧顺序绑定推导。取值来源：Risk 工程输入合同目录；非固定枚举。' AFTER `decision_point_id`,
  ADD COLUMN `input_contract_version` INT NULL COMMENT '本规则集版本绑定的工程输入合同正整数版本；不追随最新定义，不是 HTTP API 版本。' AFTER `input_contract_code`;

ALTER TABLE `t_rule_operand`
  ADD COLUMN `field_code` VARCHAR(64) NULL COMMENT 'CONTEXT 来源引用所属规则版本输入合同中的准确字段代码；须允许规则引用，其他来源为空。取值来源：所属规则版本输入合同的字段目录；非固定枚举。' AFTER `input_field_id`;

ALTER TABLE `t_rule_action_parameter`
  ADD COLUMN `field_code` VARCHAR(64) NULL COMMENT 'CONTEXT 来源引用所属规则版本输入合同中的准确字段代码；须允许动作参数引用，其他来源为空。取值来源：所属规则版本输入合同的字段目录；非固定枚举。' AFTER `input_field_id`;

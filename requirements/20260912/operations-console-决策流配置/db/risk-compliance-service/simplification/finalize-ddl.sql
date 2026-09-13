-- RCO-17 精简修订 / 各环境执行记录见 README.md。此文件含不可事务回滚的删列操作。
-- 前置：回填已 COMMIT；postcheck.sql 全部通过；新版 Reader 已逐字节验证旧 Hash/回放；
-- 所有写入方已停写，恢复材料可用，且已取得本文件的具体执行授权。
ALTER TABLE `t_rule_set_version`
  MODIFY COLUMN `input_contract_code` VARCHAR(64) NOT NULL COMMENT '本规则集版本绑定的工程输入合同代码；与 input_contract_version 一起准确定位 Risk 输入目录，不经旧顺序绑定推导。取值来源：Risk 工程输入合同目录；非固定枚举。',
  MODIFY COLUMN `input_contract_version` INT NOT NULL COMMENT '本规则集版本绑定的工程输入合同正整数版本；不追随最新定义，不是 HTTP API 版本。';

ALTER TABLE `t_rule_operand`
  DROP INDEX `idx_rule_op_input`,
  DROP COLUMN `input_field_id`;

ALTER TABLE `t_rule_action_parameter`
  DROP INDEX `idx_rule_param_input`,
  DROP COLUMN `input_field_id`;

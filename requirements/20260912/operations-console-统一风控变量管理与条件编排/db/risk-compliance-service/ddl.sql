-- RCO-19 已批准模型的增量 SQL；执行授权另行确认，当前未应用。
-- 只新增结构及修改注释；不创建业务 CHECK/FK，不修改旧记录，不使用自动迁移。

CREATE TABLE `t_risk_variable` (
  `variable_id` BIGINT NOT NULL COMMENT '变量的稳定标识，由 Risk 分配；不作为共享值版本。',
  `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全库唯一且创建后不可修改、不可复用的变量编码，格式为大写字母开头的大写字母数字下划线。取值来源：Risk 统一变量库；非固定枚举。',
  `variable_name` VARCHAR(255) NOT NULL COMMENT '运营展示名称，可修改；不参与规则或流程内容 Hash。',
  `description` VARCHAR(500) NULL COMMENT '变量用途的非敏感说明；未填写时为空。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '来源类型，允许 INPUT、CONSTANT、COLLECTION、NODE_OUTPUT；创建后固定。允许值：INPUT、CONSTANT、COLLECTION、NODE_OUTPUT。',
  `value_type` VARCHAR(64) NOT NULL COMMENT '标量类型或集合成员类型，允许 STRING、NUMBER、BOOLEAN、DATETIME、DURATION；创建后固定。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `point_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '适用决策点；INPUT 和 NODE_OUTPUT 必填，常量和集合为空表示全部已支持决策点。取值来源：Risk 决策点目录；非固定枚举。',
  `source_contract_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'INPUT 的输入合同或 NODE_OUTPUT 的输出合同代码；其他来源为空。取值来源：Risk 精确输入或节点输出合同；非固定枚举。',
  `source_contract_version` INT NULL COMMENT '来源合同的准确版本；仅 INPUT 和 NODE_OUTPUT 非空，不取最新版本兜底。',
  `source_field` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '来源合同中的准确顶层字段键；仅 INPUT 和 NODE_OUTPUT 非空，不接受任意路径、SQL 或脚本。',
  `rule_condition_flag` TINYINT(1) NOT NULL COMMENT '是否可用于规则条件；0 或 1，受来源合同允许用途约束。',
  `flow_condition_flag` TINYINT(1) NOT NULL COMMENT '是否可用于流程连线条件；0 或 1，节点输出仅当前规则集节点出线可用。',
  `action_parameter_flag` TINYINT(1) NOT NULL COMMENT '是否可作为既有动作主体输入；0 或 1，本期只开放 INPUT 的批准主体槽位。',
  `system_managed_flag` TINYINT(1) NOT NULL COMMENT '是否为工程合同保留定义；0 或 1，系统定义的结构、状态、删除及系统集合成员不开放运营修改。',
  `format_code` VARCHAR(64) NOT NULL COMMENT '通用格式配置，允许 TEXT、UPPER_ALPHA_2、UPPER_ALPHA_3、DIGITS_4、TOKEN、BCP47、POSITIVE_ID、NONE；具体变量的关联由本行管理。允许值：TEXT、UPPER_ALPHA_2、UPPER_ALPHA_3、DIGITS_4、TOKEN、BCP47、POSITIVE_ID、NONE。',
  `numeric_unit` VARCHAR(64) NULL COMMENT 'NUMBER 的量纲，允许 SCALAR 或 MONEY；其他类型为空。允许值：SCALAR、MONEY。',
  `currency_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'MONEY 的固定币种，三位大写字母；与 currency_variable_code 二选一，非 MONEY 为空。取值来源：ISO 4217 币种代码；非固定枚举。',
  `currency_variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'MONEY 的币种来源变量编码，指向同决策点 INPUT STRING 币种变量；与 currency_code 二选一，非 MONEY 为空。取值来源：Risk 统一变量库的币种输入；非固定枚举。',
  `allowed_values_variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '可选值来源集合变量编码，必须同成员类型且适用范围覆盖本变量；为空表示无额外枚举范围，集合本身不再引用集合。取值来源：Risk 统一变量库的集合；非固定枚举。',
  `string_value` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT 'CONSTANT STRING 的当前值；仅匹配来源和值类型时非空，不接受敏感原值。',
  `number_value` DECIMAL(24,8) NULL COMMENT 'CONSTANT NUMBER 的当前精确数值；仅匹配来源和值类型时非空。',
  `boolean_value` TINYINT(1) NULL COMMENT 'CONSTANT BOOLEAN 的当前值，0 或 1；仅匹配来源和值类型时非空。',
  `datetime_value` DATETIME(6) NULL COMMENT 'CONSTANT DATETIME 的当前 UTC 微秒值；仅匹配来源和值类型时非空。',
  `duration_value` BIGINT NULL COMMENT 'CONSTANT DURATION 的当前非负毫秒值；仅匹配来源和值类型时非空。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前状态，允许 ACTIVE、DISABLED、DELETED；停用禁止新保存引用，删除仅无任何引用时允许且保留编码。允许值：ACTIVE、DISABLED、DELETED。',
  `version` BIGINT NOT NULL COMMENT '变量根的正整数 CAS 版本，定义、值或集合成员成功变更时加一。',
  `create_by` BIGINT NOT NULL COMMENT '创建主体的稳定账号或正式系统主体标识。',
  `update_by` BIGINT NOT NULL COMMENT '最近修改主体的稳定账号或正式系统主体标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT 'Risk 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '变量定义或成员最近成功更新的 UTC 微秒时间。',
  PRIMARY KEY (`variable_id`),
  UNIQUE KEY `uk_risk_variable_code` (`variable_code`) COMMENT '保证变量编码永久唯一',
  KEY `idx_risk_variable_filter` (`status`, `source_type`, `variable_code`) COMMENT '支持可用目录按状态和来源查询',
  KEY `idx_risk_variable_options` (`allowed_values_variable_code`) COMMENT '支持集合被用作可选值来源的反查',
  KEY `idx_risk_variable_currency` (`currency_variable_code`) COMMENT '支持币种变量使用关系和删除保护'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='统一拥有风控变量定义、来源映射、适用范围及当前常量值；不保存变量历史或执行结果';

CREATE TABLE `t_risk_variable_member` (
  `member_id` BIGINT NOT NULL COMMENT '集合成员行标识，整体替换时可重新分配；不是变量版本。',
  `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属 COLLECTION 变量编码，成员类型和量纲由变量根统一拥有。取值来源：Risk 统一变量库；非固定枚举。',
  `value_key` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '成员按通用类型规范化后的精确字符串键，用于唯一去重；不包含 Hash，字符串大小写及前导零保留。',
  `string_value` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT 'STRING 成员值；仅该类型非空。',
  `number_value` DECIMAL(24,8) NULL COMMENT 'NUMBER 成员精确数值；仅该类型非空。',
  `boolean_value` TINYINT(1) NULL COMMENT 'BOOLEAN 成员值，0 或 1；仅该类型非空。',
  `datetime_value` DATETIME(6) NULL COMMENT 'DATETIME 成员 UTC 微秒值；仅该类型非空。',
  `duration_value` BIGINT NULL COMMENT 'DURATION 成员非负毫秒值；仅该类型非空。',
  `display_name` VARCHAR(255) NULL COMMENT '成员的可选展示名称，不改变成员业务值；未填写时为空。',
  `sort_order` INT NOT NULL COMMENT '成员在管理与选项中的展示顺序，正整数；不参与集合包含判断。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该成员行在 Risk 数据库中的 UTC 微秒写入时间。',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_risk_variable_member` (`variable_code`, `value_key`) COMMENT '保证同一集合的规范值唯一',
  KEY `idx_risk_variable_member_order` (`variable_code`, `sort_order`, `member_id`) COMMENT '支持集合成员稳定分页和展示'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='保存同类型集合的当前成员及展示名称；由所属变量根统一事务与 CAS 管理，不建立成员版本';

ALTER TABLE `t_rule_operand`
  ADD COLUMN `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'VARIABLE 来源的统一变量编码；其他来源为空，历史 CONTEXT 仍使用原 field_code。取值来源：Risk 统一变量库；非固定枚举。',
  ADD COLUMN `numeric_unit` VARCHAR(64) NULL COMMENT '新变量格式 NUMBER 字面量的量纲 SCALAR 或 MONEY；变量引用及旧格式为空。允许值：SCALAR、MONEY。',
  ADD COLUMN `currency_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '新格式 MONEY 字面量的固定币种；与 currency_variable_code 二选一，其他情况为空。取值来源：ISO 4217 币种代码；非固定枚举。',
  ADD COLUMN `currency_variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '新格式 MONEY 字面量的币种变量编码；与 currency_code 二选一，其他情况为空。取值来源：Risk 统一变量库的币种输入；非固定枚举。',
  ADD KEY `idx_rule_operand_variable` (`variable_code`, `node_id`) COMMENT '支持统一变量的条件反向引用',
  ADD KEY `idx_rule_operand_currency` (`currency_variable_code`, `node_id`) COMMENT '支持金额字面量的币种引用反查',
  MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '新变量格式使用 VARIABLE/LITERAL，历史原格式保持。允许值：FEATURE、CONTEXT、LITERAL、VARIABLE。';

ALTER TABLE `t_rule_action_parameter`
  ADD COLUMN `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'VARIABLE 来源的统一变量编码，仅既有动作主体绑定可用；其他来源为空。取值来源：Risk 统一变量库；非固定枚举。',
  ADD KEY `idx_rule_param_variable` (`variable_code`, `rule_action_id`) COMMENT '支持既有动作主体变量的引用反查',
  MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '新变量格式使用 VARIABLE/LITERAL，历史原格式保持。允许值：FEATURE、CONTEXT、LITERAL、VARIABLE。';

ALTER TABLE `t_decision_flow_edge` MODIFY COLUMN `condition_json` VARCHAR(8192) NULL COMMENT 'CONDITIONAL 连线的受控条件树；schemaVersion=1 保留 source/values 历史，schemaVersion=2 使用统一操作数，UTF-8 最多8192字节；其他连线为空。';

ALTER TABLE `t_rule_set_version` MODIFY COLUMN `content_format` VARCHAR(64) NOT NULL DEFAULT 'LEGACY' COMMENT '选择不可变内容与请求的规范投影合同；LEGACY 保留原合同，CONFIGURATION 保留附加动作与文案合同，RISK_RULE_SET_VARIABLE_CONFIGURATION 保存统一变量引用；不得根据空字段猜测历史格式。允许值：LEGACY、CONFIGURATION、RISK_RULE_SET_VARIABLE_CONFIGURATION。';

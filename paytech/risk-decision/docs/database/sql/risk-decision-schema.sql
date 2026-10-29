-- Code-MoR Risk Decision schema: verbatim slice of the approved Risk & Compliance schema by table ownership (design/03 §2.2).
-- 只适用于人工审核后的空 mor_risk_decision 数据库；不得作为应用启动迁移或已有数据库升级脚本。
-- Source: paytech/risk-decision/docs/database/risk-decision.pdma.json

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

CREATE TABLE `t_decision_point` (
  `decision_point_id` BIGINT NOT NULL COMMENT '风险决策事件点的稳定业务标识。',
  `point_code` VARCHAR(64) NOT NULL COMMENT '事件点编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `point_name` VARCHAR(255) NOT NULL COMMENT '事件点名称的业务值。',
  `business_domain` VARCHAR(64) NOT NULL COMMENT '业务域的稳定业务代码。允许值：DUE_DILIGENCE、CHECKOUT、PAYMENT、SETTLEMENT、DISPUTE。',
  `status` VARCHAR(64) NOT NULL COMMENT '事件点状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`decision_point_id`),
  UNIQUE KEY `uk_decision_point` (`point_code`) COMMENT '保证业务唯一性：point_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存风险决策事件点的稳定身份和所属业务域';

CREATE TABLE `t_decision_point_version` (
  `point_version_id` BIGINT NOT NULL COMMENT '事件点协议版本的稳定业务标识。',
  `decision_point_id` BIGINT NOT NULL COMMENT '决策事件点的稳定业务标识。',
  `version_no` INT NOT NULL COMMENT '版本号的整数值。',
  `input_contract_code` VARCHAR(64) NOT NULL COMMENT '输入契约编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `input_contract_version` INT NOT NULL COMMENT '输入契约版本的整数值。',
  `output_contract_code` VARCHAR(64) NOT NULL COMMENT '输出契约编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `default_decision` VARCHAR(64) NOT NULL COMMENT '默认业务决定的稳定业务代码。允许值：ALLOW、REVIEW、DENY。',
  `evaluation_strategy` VARCHAR(64) NOT NULL COMMENT '评估策略的稳定业务代码。允许值：FIRST_TERMINAL。',
  `failure_strategy` VARCHAR(64) NOT NULL COMMENT '失败策略的稳定业务代码。允许值：FAIL_CLOSED。',
  `timeout_ms` INT NOT NULL COMMENT '超时毫秒数的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '协议版本状态的稳定业务代码。允许值：DRAFT、FROZEN、ACTIVE、RETIRED。',
  `content_hash` CHAR(64) NOT NULL COMMENT '协议内容哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`point_version_id`),
  UNIQUE KEY `uk_point_version` (`decision_point_id`, `version_no`) COMMENT '保证业务唯一性：decision_point_id、version_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结事件点输入输出契约、执行策略、失败策略和超时边界';

CREATE TABLE `t_decision_point_input_field` (
  `input_field_id` BIGINT NOT NULL COMMENT '事件点输入字段的稳定业务标识。',
  `point_version_id` BIGINT NOT NULL COMMENT '事件点版本的稳定业务标识。',
  `field_code` VARCHAR(64) NOT NULL COMMENT '字段编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `value_type` VARCHAR(64) NOT NULL COMMENT '值类型的稳定业务代码。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `data_classification` VARCHAR(64) NOT NULL COMMENT '数据分类的稳定业务代码。允许值：PUBLIC、INTERNAL、CONFIDENTIAL_RISK、RESTRICTED_COMPLIANCE。',
  `rule_reference_flag` BOOLEAN NOT NULL COMMENT '是否允许规则引用，1 表示是，0 表示否。',
  `action_parameter_flag` BOOLEAN NOT NULL COMMENT '是否允许作为 Action 参数，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`input_field_id`),
  UNIQUE KEY `uk_point_input` (`point_version_id`, `field_code`) COMMENT '保证业务唯一性：point_version_id、field_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='声明一个事件点版本允许规则和 Action 引用的工程注册 Context Field';

CREATE TABLE `t_decision_point_feature` (
  `binding_id` BIGINT NOT NULL COMMENT '事件点 Feature 白名单绑定的稳定业务标识。',
  `point_version_id` BIGINT NOT NULL COMMENT '事件点版本的稳定业务标识。',
  `feature_version_id` BIGINT NOT NULL COMMENT 'Feature 版本的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '执行顺序的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_point_feature` (`point_version_id`, `feature_version_id`) COMMENT '保证业务唯一性：point_version_id、feature_version_id',
  UNIQUE KEY `uk_point_feat_seq` (`point_version_id`, `sequence_no`) COMMENT '保证业务唯一性：point_version_id、sequence_no',
  KEY `idx_point_feat_ver` (`feature_version_id`) COMMENT '支持访问路径：feature_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个事件点版本允许运行和被规则引用的 Feature Version 顺序';

CREATE TABLE `t_decision_point_rule_set` (
  `binding_id` BIGINT NOT NULL COMMENT '事件点规则集绑定的稳定业务标识。',
  `point_version_id` BIGINT NOT NULL COMMENT '事件点版本的稳定业务标识。',
  `rule_set_version_id` BIGINT NOT NULL COMMENT '规则集版本的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '执行顺序的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_point_rule_set` (`point_version_id`, `rule_set_version_id`) COMMENT '保证业务唯一性：point_version_id、rule_set_version_id',
  UNIQUE KEY `uk_point_rule_seq` (`point_version_id`, `sequence_no`) COMMENT '保证业务唯一性：point_version_id、sequence_no',
  KEY `idx_point_rule_ver` (`rule_set_version_id`) COMMENT '支持访问路径：rule_set_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按顺序冻结一个事件点版本采用的 Rule Set Version';

CREATE TABLE `t_decision_point_obligation` (
  `obligation_id` BIGINT NOT NULL COMMENT '事件点系统义务的稳定业务标识。',
  `point_version_id` BIGINT NOT NULL COMMENT '事件点版本的稳定业务标识。',
  `trigger_decision` VARCHAR(64) NOT NULL COMMENT '触发决定的稳定业务代码。允许值：ALLOW、REVIEW、DENY。',
  `obligation_type` VARCHAR(64) NOT NULL COMMENT '义务类型的稳定业务代码。允许值：ACTION、RESERVE_LIMIT_GROUP、CHECK_SETTLEMENT_ELIGIBILITY。',
  `action_version_id` BIGINT NULL COMMENT 'Action 版本的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '执行顺序的整数值。',
  `failure_policy` VARCHAR(64) NOT NULL COMMENT '失败策略的稳定业务代码。允许值：FAIL_CLOSED、RETRY_ASYNC。',
  `enabled_flag` BOOLEAN NOT NULL COMMENT '是否启用，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`obligation_id`),
  UNIQUE KEY `uk_point_obligation` (`point_version_id`, `trigger_decision`, `sequence_no`) COMMENT '保证业务唯一性：point_version_id、trigger_decision、sequence_no',
  KEY `idx_point_ob_action` (`action_version_id`) COMMENT '支持访问路径：action_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结事件点版本在指定业务决定下必须执行的工程级 Action 或限额义务';

CREATE TABLE `t_feature_version` (
  `feature_version_id` BIGINT NOT NULL COMMENT 'Feature 执行版本的稳定业务标识。',
  `feature_code` VARCHAR(64) NOT NULL COMMENT 'Feature 编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `feature_name` VARCHAR(255) NOT NULL COMMENT 'Feature 名称的业务值。',
  `business_category` VARCHAR(64) NOT NULL COMMENT '业务分类的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `version_no` INT NOT NULL COMMENT '版本号的整数值。',
  `result_value_type` VARCHAR(64) NOT NULL COMMENT '结果值类型的稳定业务代码。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `handler_code` VARCHAR(64) NOT NULL COMMENT 'Handler 编码的稳定业务代码。取值来源：Risk 工程注册 Feature Handler 目录；非固定枚举。',
  `implementation_version` VARCHAR(64) NOT NULL COMMENT '实现版本的业务值。',
  `implementation_hash` CHAR(64) NOT NULL COMMENT '实现哈希的 SHA-256 小写十六进制值。',
  `timeout_ms` INT NOT NULL COMMENT '超时毫秒数的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '执行版本状态的稳定业务代码。允许值：REGISTERED、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`feature_version_id`),
  UNIQUE KEY `uk_feature_version` (`feature_code`, `version_no`) COMMENT '保证业务唯一性：feature_code、version_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结 Feature 目录身份、工程注册 Handler 的实现版本、哈希、返回类型和超时';

CREATE TABLE `t_feature_dependency` (
  `dependency_id` BIGINT NOT NULL COMMENT 'Feature 依赖的稳定业务标识。',
  `feature_version_id` BIGINT NOT NULL COMMENT 'Feature 版本的稳定业务标识。',
  `dependency_feature_version_id` BIGINT NOT NULL COMMENT '依赖 Feature 版本的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '依赖顺序的整数值。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`dependency_id`),
  UNIQUE KEY `uk_feature_dep` (`feature_version_id`, `dependency_feature_version_id`) COMMENT '保证业务唯一性：feature_version_id、dependency_feature_version_id',
  UNIQUE KEY `uk_feature_dep_seq` (`feature_version_id`, `sequence_no`) COMMENT '保证业务唯一性：feature_version_id、sequence_no',
  KEY `idx_feature_dep_ver` (`dependency_feature_version_id`) COMMENT '支持访问路径：dependency_feature_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一个 Feature Version 对其他 Feature Version 的有序显式依赖';

CREATE TABLE `t_velocity_feature_definition` (
  `feature_version_id` BIGINT NOT NULL COMMENT '与所属 Feature Version 共享的主键。',
  `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。允许值：PAYMENT_ATTEMPT_ASSESSED、PAYMENT_ATTEMPT_REJECTED、CHECKOUT_ASSESSED、CHECKOUT_REJECTED。',
  `anchor_subject_type` VARCHAR(64) NOT NULL COMMENT '锚定主体类型的稳定业务代码。允许值：CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT。',
  `metric` VARCHAR(64) NOT NULL COMMENT '累计算法的稳定业务代码。允许值：COUNT、SUM_AMOUNT、DISTINCT_COUNT、LAST_AMOUNT、SECONDS_SINCE_LAST。',
  `distinct_subject_type` VARCHAR(64) NULL COMMENT '去重主体类型的稳定业务代码。允许值：CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT。',
  `window_seconds` INT NOT NULL COMMENT '窗口秒数的整数值。',
  `include_current` BOOLEAN NOT NULL COMMENT '是否计入本次，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`feature_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个实时累计 Feature Version 的事件、锚定主体、算法和窗口；创建后不可修改';

CREATE TABLE `t_list_feature_definition` (
  `feature_version_id` BIGINT NOT NULL COMMENT '与所属 Feature Version 共享的主键。',
  `list_id` BIGINT NOT NULL COMMENT '风险名单的稳定业务标识。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CLIENT、OPERATING_ENTITY、CONSUMER、IP_ADDRESS、CARD_FINGERPRINT、CARD_BIN。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`feature_version_id`),
  KEY `idx_list_feature_list` (`list_id`) COMMENT '支持访问路径：list_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个名单命中 Feature Version 的名单与主体类型，主体取值由类型对应的风控点输入字段决定；创建后不可修改';

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

CREATE TABLE `t_rule_set_version` (
  `rule_set_version_id` BIGINT NOT NULL COMMENT '规则集版本的稳定业务标识。',
  `decision_point_id` BIGINT NOT NULL COMMENT '决策事件点的稳定业务标识。',
  `input_contract_code` VARCHAR(64) NOT NULL COMMENT '本规则集版本绑定的工程输入合同代码；与 input_contract_version 一起准确定位 Risk 输入目录，不经旧顺序绑定推导。取值来源：Risk 工程输入合同目录；非固定枚举。',
  `input_contract_version` INT NOT NULL COMMENT '本规则集版本绑定的工程输入合同正整数版本；不追随最新定义，不是 HTTP API 版本。',
  `rule_set_code` VARCHAR(64) NOT NULL COMMENT '规则集编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `rule_set_name` VARCHAR(255) NOT NULL COMMENT '规则集名称的业务值。',
  `version_no` INT NOT NULL COMMENT '版本号的整数值。',
  `status` VARCHAR(64) NOT NULL COMMENT '规则集版本状态的稳定业务代码。允许值：DRAFT、FROZEN、RETIRED。',
  `content_hash` CHAR(64) NOT NULL COMMENT '规则集内容哈希的 SHA-256 小写十六进制值。',
  `content_format` VARCHAR(64) NOT NULL DEFAULT 'LEGACY' COMMENT '选择不可变内容与请求的规范投影合同；LEGACY 保留原合同，CONFIGURATION 保留附加动作与文案合同，RISK_RULE_SET_VARIABLE_CONFIGURATION 保存统一变量引用；不得根据空字段猜测历史格式。允许值：LEGACY、CONFIGURATION、RISK_RULE_SET_VARIABLE_CONFIGURATION。',
  `created_by` BIGINT NOT NULL COMMENT '创建该不可变规则集版本的调用方声明 Actor Correlation；接入认证 Operations BFF 前不代表已验证人工身份。',
  `change_reason` VARCHAR(500) NOT NULL COMMENT '创建该规则集版本的 1–500 字符可展示中英文非敏感变更原因；禁止密钥、凭据、个人数据或完整 Payload。',
  `frozen_by` BIGINT NULL COMMENT '冻结操作人的稳定业务标识。',
  `frozen_time` DATETIME(6) NULL COMMENT '冻结时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`rule_set_version_id`),
  UNIQUE KEY `uk_rule_set_ver` (`decision_point_id`, `rule_set_code`, `version_no`) COMMENT '保证业务唯一性：decision_point_id、rule_set_code、version_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存事件点内可冻结、不可原地改写并由 Release 精确引用的规则集版本';

CREATE TABLE `t_risk_rule` (
  `rule_id` BIGINT NOT NULL COMMENT '风险规则的稳定业务标识。',
  `rule_set_version_id` BIGINT NOT NULL COMMENT '规则集版本的稳定业务标识。',
  `rule_code` VARCHAR(64) NOT NULL COMMENT '规则编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `rule_name` VARCHAR(255) NOT NULL COMMENT '规则名称的业务值。',
  `priority_no` INT NOT NULL COMMENT '规则优先级的整数值。',
  `enabled_flag` BOOLEAN NOT NULL COMMENT '是否启用，1 表示是，0 表示否。',
  `reason_code` VARCHAR(64) NULL COMMENT '随不可变规则版本保存的原因代码；DENY 新选择由 Risk 拒绝原因字典治理，旧手工代码按基线兼容；其他终态保留原合同。取值来源：Risk 拒绝原因目录；历史手工代码按基线兼容；非固定枚举。',
  `reason_message` VARCHAR(500) NULL COMMENT '随不可变规则版本保存的内部拒绝文案；选原因时可带出默认值并单独覆盖，历史与非拒绝规则可空，不作为外部错误消息。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`rule_id`),
  UNIQUE KEY `uk_risk_rule_code` (`rule_set_version_id`, `rule_code`) COMMENT '保证业务唯一性：rule_set_version_id、rule_code',
  UNIQUE KEY `uk_risk_rule_priority` (`rule_set_version_id`, `priority_no`) COMMENT '保证业务唯一性：rule_set_version_id、priority_no',
  KEY `idx_rule_reason` (`reason_code`, `rule_set_version_id`) COMMENT '支持由原因代码反查保存规则版本，查询仍验证 DENY 终态'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一个 Rule Set Version 内按优先级执行的风险规则';

CREATE TABLE `t_rule_node` (
  `node_id` BIGINT NOT NULL COMMENT '规则 AST 节点的稳定业务标识。',
  `rule_id` BIGINT NOT NULL COMMENT '风险规则的稳定业务标识。',
  `parent_node_id` BIGINT NULL COMMENT '父节点的稳定业务标识。',
  `node_type` VARCHAR(64) NOT NULL COMMENT '节点类型的稳定业务代码。允许值：GROUP、PREDICATE。',
  `group_operator` VARCHAR(64) NULL COMMENT '分组操作符的稳定业务代码。允许值：AND、OR、NOT。',
  `comparison_operator` VARCHAR(64) NULL COMMENT '比较操作符的稳定业务代码。允许值：EQ、NE、GT、GTE、LT、LTE、IN、NOT_IN、BETWEEN、IS_NULL、IS_NOT_NULL。',
  `sequence_no` INT NOT NULL COMMENT '同级顺序的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_rule_node_seq` (`rule_id`, `parent_node_id`, `sequence_no`) COMMENT '保证业务唯一性：rule_id、parent_node_id、sequence_no',
  KEY `idx_rule_node_parent` (`parent_node_id`) COMMENT '支持访问路径：parent_node_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存规则条件 AST 的 GROUP 或 PREDICATE 节点和同级顺序';

CREATE TABLE `t_rule_operand` (
  `operand_id` BIGINT NOT NULL COMMENT '规则操作数的稳定业务标识。',
  `node_id` BIGINT NOT NULL COMMENT '规则节点的稳定业务标识。',
  `operand_role` VARCHAR(64) NOT NULL COMMENT '操作数角色的稳定业务代码。允许值：LEFT、RIGHT。',
  `value_sequence` INT NOT NULL COMMENT '值顺序的整数值。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '新变量格式使用 VARIABLE/LITERAL，历史原格式保持。允许值：FEATURE、CONTEXT、LITERAL、VARIABLE。',
  `feature_version_id` BIGINT NULL COMMENT 'Feature 版本的稳定业务标识。',
  `field_code` VARCHAR(64) NULL COMMENT 'CONTEXT 来源引用所属规则版本输入合同中的准确字段代码；须允许规则引用，其他来源为空。取值来源：所属规则版本输入合同的字段目录；非固定枚举。',
  `value_type` VARCHAR(64) NOT NULL COMMENT '值类型的稳定业务代码。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `string_value` VARCHAR(512) NULL COMMENT '字符串值的业务值。',
  `number_value` DECIMAL(24,8) NULL COMMENT '规则字面量的高精度数值。',
  `boolean_value` BOOLEAN NULL COMMENT '布尔值，1 表示是，0 表示否，NULL 表示不适用。',
  `datetime_value` DATETIME(6) NULL COMMENT '时间值对应的 UTC 微秒时间。',
  `duration_value` BIGINT NULL COMMENT '持续时长毫秒的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'VARIABLE 来源的统一变量编码；其他来源为空，历史 CONTEXT 仍使用原 field_code。取值来源：Risk 统一变量库；非固定枚举。',
  `numeric_unit` VARCHAR(64) NULL COMMENT '新变量格式 NUMBER 字面量的量纲 SCALAR 或 MONEY；变量引用及旧格式为空。允许值：SCALAR、MONEY。',
  `currency_code` CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '新格式 MONEY 字面量的固定币种；与 currency_variable_code 二选一，其他情况为空。取值来源：ISO 4217 币种代码；非固定枚举。',
  `currency_variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '新格式 MONEY 字面量的币种变量编码；与 currency_code 二选一，其他情况为空。取值来源：Risk 统一变量库的币种输入；非固定枚举。',
  PRIMARY KEY (`operand_id`),
  KEY `idx_rule_operand_variable` (`variable_code`, `node_id`) COMMENT '支持统一变量的条件反向引用',
  KEY `idx_rule_operand_currency` (`currency_variable_code`, `node_id`) COMMENT '支持金额字面量的币种引用反查',
  UNIQUE KEY `uk_rule_operand` (`node_id`, `operand_role`, `value_sequence`) COMMENT '保证业务唯一性：node_id、operand_role、value_sequence',
  KEY `idx_rule_op_feature` (`feature_version_id`) COMMENT '支持访问路径：feature_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Predicate 的左值或右值来源以及准确类型';

CREATE TABLE `t_action_version` (
  `action_version_id` BIGINT NOT NULL COMMENT 'Action 执行版本的稳定业务标识。',
  `action_code` VARCHAR(64) NOT NULL COMMENT 'Action 编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `action_name` VARCHAR(255) NOT NULL COMMENT 'Action 名称的业务值。',
  `action_category` VARCHAR(64) NOT NULL COMMENT 'Action 分类的稳定业务代码。允许值：DECISION、RESTRICTION、LIST、NOTIFICATION、SETTLEMENT、LIMIT、TAG。',
  `version_no` INT NOT NULL COMMENT '版本号的整数值。',
  `handler_code` VARCHAR(64) NULL COMMENT '已注册执行实现的 Handler 编码；CONFIGURATION_ONLY 及其 RETIRED 历史配置为空，REGISTERED/ACTIVE 与执行版本历史必须有真实工程证据。取值来源：Risk 工程注册 Action Handler 目录；非固定枚举。',
  `implementation_version` VARCHAR(64) NULL COMMENT '已注册执行实现的版本；CONFIGURATION_ONLY 时为空，不使用占位版本。',
  `implementation_hash` CHAR(64) NULL COMMENT '真实执行实现的 SHA-256 小写十六进制值；CONFIGURATION_ONLY 时为空，不以配置定义 Hash 冒充实现证据。',
  `terminal_flag` BOOLEAN NOT NULL COMMENT '是否终态决定，1 表示是，0 表示否。',
  `decision_code` VARCHAR(64) NULL COMMENT '业务决定的稳定业务代码。允许值：ALLOW、REVIEW、DENY。',
  `merge_policy` VARCHAR(64) NOT NULL COMMENT '合并策略的稳定业务代码。允许值：FIRST、LAST、MOST_RESTRICTIVE、UNION、MIN、MAX。',
  `shadow_supported_flag` BOOLEAN NOT NULL COMMENT '是否支持 Shadow，1 表示是，0 表示否。',
  `status` VARCHAR(64) NOT NULL COMMENT '执行版本状态的稳定业务代码。允许值：CONFIGURATION_ONLY、REGISTERED、ACTIVE、RETIRED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`action_version_id`),
  UNIQUE KEY `uk_action_version` (`action_code`, `version_no`) COMMENT '保证业务唯一性：action_code、version_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结 Action 目录身份、Handler、业务决定、合并策略和 Shadow 支持能力';

CREATE TABLE `t_action_parameter_definition` (
  `parameter_definition_id` BIGINT NOT NULL COMMENT 'Action 参数定义的稳定业务标识。',
  `action_version_id` BIGINT NOT NULL COMMENT 'Action 版本的稳定业务标识。',
  `parameter_code` VARCHAR(64) NOT NULL COMMENT '参数编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `value_type` VARCHAR(64) NOT NULL COMMENT '值类型的稳定业务代码。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `required_flag` BOOLEAN NOT NULL COMMENT '是否必需，1 表示是，0 表示否。',
  `allowed_source_type` VARCHAR(64) NOT NULL COMMENT '允许来源类型的稳定业务代码。允许值：FEATURE、CONTEXT、LITERAL。',
  `target_parameter_flag` BOOLEAN NOT NULL COMMENT '是否目标参数，1 表示是，0 表示否。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`parameter_definition_id`),
  UNIQUE KEY `uk_action_param_def` (`action_version_id`, `parameter_code`) COMMENT '保证业务唯一性：action_version_id、parameter_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='声明 Action Version 允许的参数编码、值类型、来源和目标语义';

CREATE TABLE `t_rule_action` (
  `rule_action_id` BIGINT NOT NULL COMMENT '规则 Action 绑定的稳定业务标识。',
  `rule_id` BIGINT NOT NULL COMMENT '风险规则的稳定业务标识。',
  `action_version_id` BIGINT NOT NULL COMMENT 'Action 版本的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '执行顺序的整数值。',
  `target_binding_code` VARCHAR(64) NULL COMMENT '配置动作引用的不可变工程主体绑定；终态动作必须为空，不保存实际主体标识。取值来源：Risk 按决策点发布的主体绑定目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`rule_action_id`),
  UNIQUE KEY `uk_rule_action_seq` (`rule_id`, `sequence_no`) COMMENT '保证业务唯一性：rule_id、sequence_no',
  KEY `idx_rule_action_ver` (`action_version_id`) COMMENT '支持访问路径：action_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按顺序绑定规则命中后产生的 Action Version';

CREATE TABLE `t_rule_action_parameter` (
  `parameter_id` BIGINT NOT NULL COMMENT '规则 Action 参数绑定的稳定业务标识。',
  `rule_action_id` BIGINT NOT NULL COMMENT '规则 Action的稳定业务标识。',
  `parameter_definition_id` BIGINT NOT NULL COMMENT '参数定义的稳定业务标识。',
  `value_sequence` INT NOT NULL COMMENT '参数值顺序的整数值。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '新变量格式使用 VARIABLE/LITERAL，历史原格式保持。允许值：FEATURE、CONTEXT、LITERAL、VARIABLE。',
  `feature_version_id` BIGINT NULL COMMENT 'Feature 版本的稳定业务标识。',
  `field_code` VARCHAR(64) NULL COMMENT 'CONTEXT 来源引用所属规则版本输入合同中的准确字段代码；须允许动作参数引用，其他来源为空。取值来源：所属规则版本输入合同的字段目录；非固定枚举。',
  `string_value` VARCHAR(512) NULL COMMENT '字符串值的业务值。',
  `number_value` DECIMAL(24,8) NULL COMMENT 'Action 参数的高精度数值。',
  `boolean_value` BOOLEAN NULL COMMENT '布尔值，1 表示是，0 表示否，NULL 表示不适用。',
  `datetime_value` DATETIME(6) NULL COMMENT '时间值对应的 UTC 微秒时间。',
  `duration_value` BIGINT NULL COMMENT '持续时长毫秒的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'VARIABLE 来源的统一变量编码，仅既有动作主体绑定可用；其他来源为空。取值来源：Risk 统一变量库；非固定枚举。',
  PRIMARY KEY (`parameter_id`),
  KEY `idx_rule_param_variable` (`variable_code`, `rule_action_id`) COMMENT '支持既有动作主体变量的引用反查',
  UNIQUE KEY `uk_rule_action_param` (`rule_action_id`, `parameter_definition_id`, `value_sequence`) COMMENT '保证业务唯一性：rule_action_id、parameter_definition_id、value_sequence',
  KEY `idx_rule_param_def` (`parameter_definition_id`) COMMENT '支持访问路径：parameter_definition_id',
  KEY `idx_rule_param_feature` (`feature_version_id`) COMMENT '支持访问路径：feature_version_id',
  KEY `idx_rule_param_string` (`string_value`, `parameter_definition_id`, `rule_action_id`) COMMENT '支持标签字面量反查；须同时验证 TAG 参数定义和 ADD_RISK_TAG 动作'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存规则 Action 参数的 Feature、Context 或 Literal 类型化绑定';

CREATE TABLE `t_decision_release` (
  `release_id` BIGINT NOT NULL COMMENT '决策发布的稳定业务标识。',
  `release_no` VARCHAR(64) NOT NULL COMMENT '发布编号的业务值。',
  `decision_point_id` BIGINT NOT NULL COMMENT '决策事件点的稳定业务标识。',
  `point_version_id` BIGINT NOT NULL COMMENT '事件点版本的稳定业务标识。',
  `decision_flow_version_id` BIGINT NULL COMMENT '决策流版本的稳定业务标识。',
  `environment_code` VARCHAR(64) NOT NULL COMMENT '环境编码的稳定业务代码。允许值：TEST、PRODUCTION。',
  `status` VARCHAR(64) NOT NULL COMMENT '发布状态的稳定业务代码。允许值：DRAFT、VALIDATING、SHADOWING、REVIEW_PENDING、APPROVED、ACTIVE、SUPERSEDED、REJECTED。',
  `active_slot` BOOLEAN NULL COMMENT '活动发布占位，1 表示是，0 表示否，NULL 表示不适用。',
  `content_hash` CHAR(64) NOT NULL COMMENT '发布内容哈希的 SHA-256 小写十六进制值。',
  `created_by` BIGINT NOT NULL COMMENT '创建人的稳定业务标识。',
  `activated_time` DATETIME(6) NULL COMMENT '激活时间对应的 UTC 微秒时间。',
  `superseded_time` DATETIME(6) NULL COMMENT '被替代时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '发布并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`release_id`),
  UNIQUE KEY `uk_release_no` (`release_no`) COMMENT '保证业务唯一性：release_no',
  UNIQUE KEY `uk_release_active` (`decision_point_id`, `environment_code`, `active_slot`) COMMENT '保证业务唯一性：decision_point_id、environment_code、active_slot',
  KEY `idx_release_point_ver` (`point_version_id`) COMMENT '支持访问路径：point_version_id',
  KEY `idx_release_flow_ver` (`decision_flow_version_id`) COMMENT '支持访问路径：decision_flow_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个事件点和环境的完整候选或生效决策快照';

CREATE TABLE `t_release_audit_event` (
  `audit_event_id` BIGINT NOT NULL COMMENT '发布审计事件的稳定业务标识。',
  `release_id` BIGINT NOT NULL COMMENT '决策发布的稳定业务标识。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型的稳定业务代码。允许值：STATIC_CHECK、APPROVAL、STATE_CHANGE。',
  `event_time` DATETIME(6) NOT NULL COMMENT '事件时间对应的 UTC 微秒时间。',
  `check_code` VARCHAR(64) NULL COMMENT '检查编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `check_status` VARCHAR(64) NULL COMMENT '检查状态的稳定业务代码。允许值：PASSED、FAILED。',
  `severity` VARCHAR(64) NULL COMMENT '严重程度的稳定业务代码。允许值：INFO、WARNING、ERROR。',
  `error_code` VARCHAR(64) NULL COMMENT '错误代码的稳定业务代码。取值来源：Risk 发布检查错误目录；非固定枚举。',
  `error_summary` VARCHAR(1000) NULL COMMENT '脱敏错误摘要的业务值。',
  `shadow_run_id` BIGINT NULL COMMENT '旁路批次的稳定业务标识。',
  `approval_request_id` VARCHAR(128) NULL COMMENT '审核请求标识的业务值。',
  `approval_status` VARCHAR(64) NULL COMMENT '审核状态的稳定业务代码。允许值：APPROVED、REJECTED。',
  `reviewer_id` BIGINT NULL COMMENT '审核人的稳定业务标识。',
  `review_comment` VARCHAR(1000) NULL COMMENT '审核意见的业务值。',
  `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码。允许值：DRAFT、VALIDATING、SHADOWING、REVIEW_PENDING、APPROVED、ACTIVE、SUPERSEDED、REJECTED。',
  `to_status` VARCHAR(64) NULL COMMENT '新状态的稳定业务代码。允许值：DRAFT、VALIDATING、SHADOWING、REVIEW_PENDING、APPROVED、ACTIVE、SUPERSEDED、REJECTED。',
  `reason_code` VARCHAR(64) NULL COMMENT '变更原因的稳定业务代码。取值来源：Risk 发布状态原因目录；非固定枚举。',
  `trigger_type` VARCHAR(64) NULL COMMENT '触发类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `trigger_id` VARCHAR(128) NULL COMMENT '触发标识的业务值。',
  `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识。',
  `aggregate_version` INT NULL COMMENT '聚合版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`audit_event_id`),
  UNIQUE KEY `uk_release_audit_check` (`release_id`, `event_type`, `check_code`) COMMENT '保证业务唯一性：release_id、event_type、check_code',
  UNIQUE KEY `uk_release_audit_state` (`release_id`, `event_type`, `aggregate_version`) COMMENT '保证业务唯一性：release_id、event_type、aggregate_version',
  UNIQUE KEY `uk_release_audit_approval` (`release_id`, `event_type`, `approval_request_id`) COMMENT '保证业务唯一性：release_id、event_type、approval_request_id',
  KEY `idx_release_audit_time` (`release_id`, `event_time`) COMMENT '支持访问路径：release_id、event_time',
  KEY `idx_release_audit_shadow` (`shadow_run_id`, `event_time`) COMMENT '支持访问路径：shadow_run_id、event_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='以类型化不可变追加事件保存发布静态检查、人工审核和状态迁移事实';

CREATE TABLE `t_release_shadow_run` (
  `shadow_run_id` BIGINT NOT NULL COMMENT '发布旁路批次的稳定业务标识。',
  `run_no` VARCHAR(64) NOT NULL COMMENT '旁路批次编号的业务值。',
  `baseline_release_id` BIGINT NOT NULL COMMENT '基线发布的稳定业务标识。',
  `candidate_release_id` BIGINT NOT NULL COMMENT '候选发布的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '旁路状态的稳定业务代码。允许值：CREATED、RUNNING、COMPLETED、FAILED、CANCELLED。',
  `sample_target` INT NOT NULL COMMENT '目标样本数的整数值。',
  `sample_count` INT NOT NULL COMMENT '实际样本数的整数值。',
  `started_time` DATETIME(6) NULL COMMENT '开始时间对应的 UTC 微秒时间。',
  `completed_time` DATETIME(6) NULL COMMENT '完成时间对应的 UTC 微秒时间。',
  `allow_to_review_count` INT NULL COMMENT '允许转审核数的整数值。',
  `allow_to_deny_count` INT NULL COMMENT '允许转拒绝数的整数值。',
  `deny_to_allow_count` INT NULL COMMENT '拒绝转允许数的整数值。',
  `action_change_count` INT NULL COMMENT 'Action 变化数的整数值。',
  `feature_missing_count` INT NULL COMMENT 'Feature 缺失数的整数值。',
  `failed_count` INT NULL COMMENT '失败数的整数值。',
  `timed_out_count` INT NULL COMMENT '超时数的整数值。',
  `p95_millis` INT NULL COMMENT 'P95 耗时毫秒的整数值。',
  `p99_millis` INT NULL COMMENT 'P99 耗时毫秒的整数值。',
  `check_status` VARCHAR(64) NULL COMMENT '质量检查状态的稳定业务代码。允许值：PASSED、FAILED。',
  `version` BIGINT NOT NULL COMMENT '旁路批次并发版本的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`shadow_run_id`),
  UNIQUE KEY `uk_shadow_run_no` (`run_no`) COMMENT '保证业务唯一性：run_no',
  KEY `idx_shadow_baseline` (`baseline_release_id`) COMMENT '支持访问路径：baseline_release_id',
  KEY `idx_shadow_candidate` (`candidate_release_id`) COMMENT '支持访问路径：candidate_release_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 PRIMARY 基线与候选 Release 的一批旁路比较执行';

CREATE TABLE `t_decision_flow` (
  `flow_id` BIGINT NOT NULL COMMENT 'Risk 服务侧 Snowflake 生成的流程管理身份；创建后不变。',
  `decision_point_id` BIGINT NOT NULL COMMENT '本库决策点身份；与流程编码共同确定不可改变的业务身份。',
  `flow_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '决策点内永久唯一的大写流程编码；删除后不回收。取值来源：Risk 流程配置管理；非固定枚举。',
  `current_flow_version_id` BIGINT NOT NULL COMMENT '本流程当前最高业务版本；与新版本在同一事务切换，删除后保留。',
  `status` VARCHAR(64) NOT NULL COMMENT '与运行发布无关的整体管理状态。允许值：CONFIGURABLE、DELETED。',
  `version` BIGINT NOT NULL DEFAULT 1 COMMENT '从 1 开始；每次保存新配置或首次删除加 1；幂等回放不增加。',
  `deleted_by` BIGINT NULL COMMENT '来源 Platform Account 的永久账号标识，只保存跨系统身份引用；CONFIGURABLE 时为空，DELETED 时必填且不改写。',
  `deleted_time` DATETIME(6) NULL COMMENT '整体删除的 UTC 微秒时间；CONFIGURABLE 时为空，DELETED 时必填且不改写。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '当前版本指针或删除状态最后改变的 UTC 微秒时间；成功回放不改变。',
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_decision_flow_code` (`decision_point_id`, `flow_code`) COMMENT '保证业务唯一性：decision_point_id、flow_code',
  KEY `idx_decision_flow_list` (`decision_point_id`, `status`, `update_time`, `flow_id`) COMMENT '支持访问路径：decision_point_id、status、update_time、flow_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存决策流独立身份、当前配置入口及整体删除状态；不表达运行发布状态';

CREATE TABLE `t_decision_flow_version` (
  `flow_version_id` BIGINT NOT NULL COMMENT 'Risk 服务侧 Snowflake 生成的不可变配置版本身份。',
  `flow_id` BIGINT NOT NULL COMMENT '所属本库决策流管理身份；创建后不变。',
  `version_no` INT NOT NULL COMMENT '同一流程内从 1 开始连续递增；只在成功保存新配置时增加。',
  `flow_name` VARCHAR(100) NOT NULL COMMENT '本次保存的流程名称快照，1 至 100 个 Unicode 码点；以后改名不回写。',
  `input_contract_code` VARCHAR(64) NOT NULL COMMENT '保存时选定的 Risk 决策点输入合同。取值来源：Risk 输入合同目录；非固定枚举。',
  `input_contract_version` INT NOT NULL COMMENT '保存时输入合同的准确正整数版本；规则集引用必须与其一致。',
  `created_by` BIGINT NOT NULL COMMENT '来源 Platform Account 的永久账号标识，只保存可信操作者身份，不复制账号资料。',
  `change_reason` VARCHAR(500) NOT NULL COMMENT '本次完整保存的人工说明，1 至 500 个 Unicode 码点；不包含个人原值或凭据。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '不可变配置明细，始终等于 create_time。',
  PRIMARY KEY (`flow_version_id`),
  UNIQUE KEY `uk_flow_version_no` (`flow_id`, `version_no`) COMMENT '保证业务唯一性：flow_id、version_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每次完整保存生成的不可变配置版本头；历史只读，不是运行发布版本';

CREATE TABLE `t_decision_flow_node` (
  `node_id` BIGINT NOT NULL COMMENT 'Risk 服务侧 Snowflake 生成的版本内节点行身份；每次另存生成新行。',
  `flow_version_id` BIGINT NOT NULL COMMENT '所属不可变流程版本，所有节点及连线必须处于同一版本。',
  `node_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本内大小写敏感的节点键；同一编辑节点跨版本保留，删除再添加使用新键。',
  `node_type` VARCHAR(64) NOT NULL COMMENT '节点业务类型。允许值：START、RULE_SET、END。',
  `node_name` VARCHAR(100) NOT NULL COMMENT '保存时的节点名称快照，1 至 100 个 Unicode 码点。',
  `rule_set_version_id` BIGINT NULL COMMENT '准确引用本库规则集版本；仅 RULE_SET 必填，其他类型为空；不追随最新版本。',
  `decision_code` VARCHAR(64) NULL COMMENT '仅 END 必填，其他类型为空。允许值：ALLOW、REVIEW、DENY。',
  `reason_code` VARCHAR(64) NULL COMMENT '仅 DENY 结束节点必填，其他情况为空。取值来源：本库拒绝原因目录；非固定枚举。',
  `reason_message` VARCHAR(500) NULL COMMENT '仅 DENY 结束节点必填，保存时的内部拒绝说明；目录修改不回写，其他情况为空。',
  `position_x` INT NOT NULL COMMENT '编辑画布中的整数像素坐标，范围负 100000 至正 100000；HTTP 使用十进制字符串。',
  `position_y` INT NOT NULL COMMENT '编辑画布中的整数像素坐标，范围负 100000 至正 100000；HTTP 使用十进制字符串。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '不可变配置明细，始终等于 create_time。',
  PRIMARY KEY (`node_id`),
  UNIQUE KEY `uk_flow_node_key` (`flow_version_id`, `node_key`) COMMENT '保证业务唯一性：flow_version_id、node_key',
  KEY `idx_flow_node_reason` (`reason_code`, `flow_version_id`, `node_id`) COMMENT '支持访问路径：reason_code、flow_version_id、node_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存每个配置版本的开始、规则集和结束节点；规则版本与原因直接可查，不保存运行结果';

CREATE TABLE `t_decision_flow_edge` (
  `edge_id` BIGINT NOT NULL COMMENT 'Risk 服务侧 Snowflake 生成的版本内连线行身份。',
  `flow_version_id` BIGINT NOT NULL COMMENT '所属不可变流程版本；来源和目标节点必须同属此版本。',
  `edge_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本内大小写敏感的稳定连线键；不以创建时间或坐标决定顺序。',
  `source_node_id` BIGINT NOT NULL COMMENT '本库同一流程版本的来源节点；只允许 START 或 RULE_SET。',
  `target_node_id` BIGINT NOT NULL COMMENT '本库同一流程版本的目标节点；不能指向 START 或自身。',
  `edge_type` VARCHAR(64) NOT NULL COMMENT '出口业务含义。允许值：UNCONDITIONAL、CONDITIONAL、ELSE。',
  `priority_no` INT NOT NULL COMMENT 'CONDITIONAL 从 1 连续排列且同源唯一；ELSE 与 UNCONDITIONAL 固定为 0，两者不能同源共存。',
  `edge_name` VARCHAR(100) NOT NULL COMMENT '运营填写的连线名称快照，1 至 100 个 Unicode 码点。',
  `condition_json` VARCHAR(8192) NULL COMMENT 'CONDITIONAL 连线的受控条件树；schemaVersion=1 保留 source/values 历史，schemaVersion=2 使用统一操作数，UTF-8 最多8192字节；其他连线为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '不可变配置明细，始终等于 create_time。',
  PRIMARY KEY (`edge_id`),
  UNIQUE KEY `uk_flow_edge_key` (`flow_version_id`, `edge_key`) COMMENT '保证业务唯一性：flow_version_id、edge_key',
  UNIQUE KEY `uk_flow_edge_order` (`flow_version_id`, `source_node_id`, `priority_no`) COMMENT '保证业务唯一性：flow_version_id、source_node_id、priority_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存版本内节点连线、显式出口顺序及受控条件树；一条连线一个条件，不保存求值结果';

CREATE TABLE `t_decision_flow_end_output` (
  `output_id` BIGINT NOT NULL COMMENT 'Risk 服务侧 Snowflake 生成的结束节点输出行身份。',
  `flow_version_id` BIGINT NOT NULL COMMENT '所属不可变流程版本；与结束节点同属此版本。',
  `node_id` BIGINT NOT NULL COMMENT '本库同一流程版本的放行结束节点。',
  `output_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '决策点合同声明的附加输出编码，如 AUTHENTICATION_REQUIREMENT。取值来源：决策点工程合同的附加输出声明；非固定枚举。',
  `output_value` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '该输出在决策点合同中允许的取值，如 THREE_DS_REQUIRED；由应用按合同校验。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  PRIMARY KEY (`output_id`),
  UNIQUE KEY `uk_flow_end_output` (`node_id`, `output_code`) COMMENT '保证业务唯一性：node_id、output_code',
  KEY `idx_flow_end_output_version` (`flow_version_id`) COMMENT '支持访问路径：flow_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存放行结束节点配置的附加输出；编码与取值由决策点合同声明，创建后不可修改';

CREATE TABLE `t_risk_variable` (
  `variable_id` BIGINT NOT NULL COMMENT '变量的稳定标识，由 Risk 分配；不作为共享值版本。',
  `variable_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全库唯一且创建后不可修改、不可复用的变量编码，格式为大写字母开头的大写字母数字下划线。取值来源：Risk 统一变量库；非固定枚举。',
  `variable_name` VARCHAR(255) NOT NULL COMMENT '运营展示名称，可修改；不参与规则或流程内容 Hash。',
  `description` VARCHAR(500) NULL COMMENT '变量用途的非敏感说明；未填写时为空。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '来源类型，允许 INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST；创建后固定。允许值：INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST。',
  `value_type` VARCHAR(64) NOT NULL COMMENT '标量类型或集合成员类型，允许 STRING、NUMBER、BOOLEAN、DATETIME、DURATION；创建后固定。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `point_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '适用决策点；INPUT 和 NODE_OUTPUT 必填，常量和集合为空表示全部已支持决策点。取值来源：Risk 决策点目录；非固定枚举。',
  `source_contract_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'INPUT 的输入合同或 NODE_OUTPUT 的输出合同代码；其他来源为空。取值来源：Risk 精确输入或节点输出合同；非固定枚举。',
  `source_contract_version` INT NULL COMMENT '来源合同的准确版本；仅 INPUT 和 NODE_OUTPUT 非空，不取最新版本兜底。',
  `feature_version_id` BIGINT NULL COMMENT 'VELOCITY 与 LIST 来源创建时生成的不可变 Feature Version；其他来源为空。',
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
  KEY `idx_risk_variable_currency` (`currency_variable_code`) COMMENT '支持币种变量使用关系和删除保护',
  KEY `idx_risk_variable_feature` (`feature_version_id`) COMMENT '支持由 Feature Version 反查所属变量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一拥有风控变量定义、来源映射、适用范围及当前常量值；不保存变量历史或执行结果';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存同类型集合的当前成员及展示名称；由所属变量根统一事务与 CAS 管理，不建立成员版本';

CREATE TABLE `t_decision_evaluation` (
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `evaluation_request_id` VARCHAR(128) NOT NULL COMMENT '评估请求标识的业务值。',
  `decision_point_id` BIGINT NOT NULL COMMENT '决策事件点的稳定业务标识。',
  `point_version_id` BIGINT NOT NULL COMMENT '事件点版本的稳定业务标识。',
  `release_id` BIGINT NOT NULL COMMENT '决策发布的稳定业务标识。',
  `evaluation_mode` VARCHAR(64) NOT NULL COMMENT '评估模式的稳定业务代码。允许值：PRIMARY、SHADOW、REPLAY。',
  `evaluation_time` DATETIME(6) NOT NULL COMMENT '评估时间锚点对应的 UTC 微秒时间。',
  `technical_status` VARCHAR(64) NOT NULL COMMENT '技术状态的稳定业务代码。允许值：SUCCEEDED、FAILED、TIMED_OUT。',
  `proposed_decision` VARCHAR(64) NULL COMMENT '建议业务决定的稳定业务代码。允许值：ALLOW、REVIEW、DENY。',
  `business_decision` VARCHAR(64) NULL COMMENT '正式业务决定的稳定业务代码。允许值：ALLOW、REVIEW、DENY。',
  `decision_adjustment_reason_code` VARCHAR(64) NULL COMMENT '决定调整原因的稳定业务代码。取值来源：Risk 决定调整原因目录；非固定枚举。',
  `default_decision_used_flag` BOOLEAN NOT NULL COMMENT '是否使用默认决定，1 表示是，0 表示否。',
  `started_time` DATETIME(6) NOT NULL COMMENT '开始时间对应的 UTC 微秒时间。',
  `completed_time` DATETIME(6) NOT NULL COMMENT '完成时间对应的 UTC 微秒时间。',
  `elapsed_millis` INT NOT NULL COMMENT '执行耗时毫秒的整数值。',
  `input_snapshot_hash` CHAR(64) NOT NULL COMMENT '输入快照哈希的 SHA-256 小写十六进制值。',
  `failure_code` VARCHAR(64) NULL COMMENT '失败代码的稳定业务代码。取值来源：Risk 评估失败目录；非固定枚举。',
  `shadow_run_id` BIGINT NULL COMMENT '旁路批次的稳定业务标识。',
  `baseline_evaluation_id` BIGINT NULL COMMENT '基线评估的稳定业务标识。',
  `decision_changed_flag` BOOLEAN NULL COMMENT '决定是否变化，1 表示是，0 表示否，NULL 表示不适用。',
  `action_changed_flag` BOOLEAN NULL COMMENT 'Action 是否变化，1 表示是，0 表示否，NULL 表示不适用。',
  `comparison_severity` VARCHAR(64) NULL COMMENT '比较严重程度的稳定业务代码。允许值：NONE、LOW、MEDIUM、HIGH、CRITICAL。',
  `compared_time` DATETIME(6) NULL COMMENT '比较时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`evaluation_id`),
  UNIQUE KEY `uk_evaluation_request` (`evaluation_request_id`) COMMENT '保证业务唯一性：evaluation_request_id',
  KEY `idx_eval_release` (`release_id`) COMMENT '支持访问路径：release_id',
  KEY `idx_eval_point_time` (`decision_point_id`, `evaluation_time`) COMMENT '支持访问路径：decision_point_id、evaluation_time',
  KEY `idx_eval_point_ver` (`point_version_id`) COMMENT '支持访问路径：point_version_id',
  KEY `idx_eval_baseline` (`baseline_evaluation_id`) COMMENT '支持访问路径：baseline_evaluation_id',
  UNIQUE KEY `uk_eval_shadow_baseline` (`shadow_run_id`, `baseline_evaluation_id`) COMMENT '保证业务唯一性：shadow_run_id、baseline_evaluation_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存任一决策事件点在 PRIMARY、SHADOW 或 REPLAY 模式下的一次完整执行事实';

CREATE TABLE `t_evaluation_feature_result` (
  `result_id` BIGINT NOT NULL COMMENT '评估 Feature 结果的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `feature_version_id` BIGINT NOT NULL COMMENT 'Feature 版本的稳定业务标识。',
  `execution_sequence` INT NOT NULL COMMENT '执行顺序的整数值。',
  `technical_status` VARCHAR(64) NOT NULL COMMENT '技术状态的稳定业务代码。允许值：SUCCEEDED、FAILED、TIMED_OUT。',
  `value_type` VARCHAR(64) NOT NULL COMMENT '值类型的稳定业务代码。允许值：STRING、NUMBER、BOOLEAN、DATETIME、DURATION。',
  `string_value` VARCHAR(512) NULL COMMENT '字符串结果的业务值。',
  `number_value` DECIMAL(24,8) NULL COMMENT 'Feature 输出的高精度数值。',
  `boolean_value` BOOLEAN NULL COMMENT '布尔结果，1 表示是，0 表示否，NULL 表示不适用。',
  `datetime_value` DATETIME(6) NULL COMMENT '时间结果对应的 UTC 微秒时间。',
  `duration_value` BIGINT NULL COMMENT '持续时长毫秒的长整数值。',
  `elapsed_millis` INT NOT NULL COMMENT '执行耗时毫秒的整数值。',
  `failure_code` VARCHAR(64) NULL COMMENT '失败代码的稳定业务代码。取值来源：Risk Feature 失败目录；非固定枚举。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`result_id`),
  UNIQUE KEY `uk_eval_feature` (`evaluation_id`, `feature_version_id`) COMMENT '保证业务唯一性：evaluation_id、feature_version_id',
  UNIQUE KEY `uk_eval_feature_seq` (`evaluation_id`, `execution_sequence`) COMMENT '保证业务唯一性：evaluation_id、execution_sequence',
  KEY `idx_eval_feature_ver` (`feature_version_id`) COMMENT '支持访问路径：feature_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存评估中实际执行的 Feature Version、技术结果、类型化值和耗时';

CREATE TABLE `t_evaluation_rule_trace` (
  `trace_id` BIGINT NOT NULL COMMENT '评估规则轨迹的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `trace_type` VARCHAR(64) NOT NULL COMMENT '轨迹类型的稳定业务代码。允许值：RULE、PREDICATE。',
  `rule_set_version_id` BIGINT NULL COMMENT '规则集版本的稳定业务标识。',
  `rule_id` BIGINT NOT NULL COMMENT '风险规则的稳定业务标识。',
  `node_id` BIGINT NULL COMMENT '规则节点的稳定业务标识。',
  `execution_sequence` INT NOT NULL COMMENT '执行顺序的整数值。',
  `result_value` BOOLEAN NULL COMMENT '结果值，1 表示是，0 表示否，NULL 表示不适用。',
  `terminal_flag` BOOLEAN NULL COMMENT '是否触发终态，1 表示是，0 表示否，NULL 表示不适用。',
  `elapsed_millis` INT NULL COMMENT '执行耗时毫秒的整数值。',
  `failure_code` VARCHAR(64) NULL COMMENT '失败代码的稳定业务代码。取值来源：Risk 规则轨迹失败目录；非固定枚举。',
  `rule_result_slot` BOOLEAN NULL COMMENT '规则结果唯一槽位，1 表示是，0 表示否，NULL 表示不适用。',
  `predicate_result_slot` BOOLEAN NULL COMMENT 'Predicate 结果唯一槽位，1 表示是，0 表示否，NULL 表示不适用。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `uk_eval_rule_trace_rule` (`evaluation_id`, `rule_id`, `rule_result_slot`) COMMENT '保证业务唯一性：evaluation_id、rule_id、rule_result_slot',
  UNIQUE KEY `uk_eval_rule_trace_predicate` (`evaluation_id`, `node_id`, `predicate_result_slot`) COMMENT '保证业务唯一性：evaluation_id、node_id、predicate_result_slot',
  UNIQUE KEY `uk_eval_rule_trace_seq` (`evaluation_id`, `trace_type`, `execution_sequence`) COMMENT '保证业务唯一性：evaluation_id、trace_type、execution_sequence',
  KEY `idx_eval_rule_trace_set` (`rule_set_version_id`) COMMENT '支持访问路径：rule_set_version_id',
  KEY `idx_eval_trace_rule` (`rule_id`) COMMENT '支持访问路径：rule_id',
  KEY `idx_eval_rule_trace_node` (`node_id`) COMMENT '支持访问路径：node_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='以受控 RULE/PREDICATE 行类型保存不可变的规则和 Predicate 可解释轨迹';

CREATE TABLE `t_evaluation_flow_trace` (
  `trace_id` BIGINT NOT NULL COMMENT '评估流程轨迹的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `step_no` INT NOT NULL COMMENT '从 1 开始的节点经过顺序；开始节点为 1，结束节点为最后一行。',
  `node_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '评估所用决策流版本内的节点键；流程版本由评估的发布得出。',
  `node_type` VARCHAR(64) NOT NULL COMMENT '节点业务类型。允许值：START、RULE_SET、END。',
  `rule_set_version_id` BIGINT NULL COMMENT '规则集节点引用的规则集版本；其他节点为空。',
  `matched_flag` BOOLEAN NULL COMMENT '规则集节点是否有规则命中，1 表示是，0 表示否，NULL 表示不适用。',
  `node_decision` VARCHAR(64) NULL COMMENT '规则集节点首个终态命中的决定或结束节点的结论；没有时为空。允许值：ALLOW、REVIEW、DENY。',
  `reused_flag` BOOLEAN NULL COMMENT '规则集节点是否复用同一规则集版本此前的执行结果，1 表示是，0 表示否，NULL 表示不适用。',
  `exit_edge_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '离开本节点所走连线在流程版本内的键；结束节点为空。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`trace_id`),
  UNIQUE KEY `uk_eval_flow_trace_step` (`evaluation_id`, `step_no`) COMMENT '保证业务唯一性：evaluation_id、step_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按经过顺序保存一次评估实际经过的决策流节点、规则集节点结果与所走连线';

CREATE TABLE `t_evaluation_action_intent` (
  `intent_id` BIGINT NOT NULL COMMENT '评估 Action Intent 的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `source_type` VARCHAR(64) NOT NULL COMMENT 'Action 来源类型的稳定业务代码。允许值：RULE、OBLIGATION。',
  `rule_action_id` BIGINT NULL COMMENT '规则 Action的稳定业务标识。',
  `obligation_id` BIGINT NULL COMMENT 'Action 型系统义务的稳定业务标识。',
  `action_version_id` BIGINT NOT NULL COMMENT 'Action 版本的稳定业务标识。',
  `resolved_target_type` VARCHAR(64) NOT NULL COMMENT '解析目标类型的稳定业务代码。取值来源：Risk Action 目标类型目录；非固定枚举。',
  `resolved_target_key` VARCHAR(256) NOT NULL COMMENT '解析目标键的业务值。',
  `intent_sequence` INT NOT NULL COMMENT 'Intent 顺序的整数值。',
  `merged_effect_id` BIGINT NULL COMMENT '合并后 Effect的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT 'Intent 状态的稳定业务代码。允许值：COLLECTED、MERGED、DISCARDED、FAILED。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`intent_id`),
  UNIQUE KEY `uk_eval_intent_rule` (`evaluation_id`, `rule_action_id`, `intent_sequence`) COMMENT '保证业务唯一性：evaluation_id、rule_action_id、intent_sequence',
  UNIQUE KEY `uk_eval_intent_obligation` (`evaluation_id`, `obligation_id`, `intent_sequence`) COMMENT '保证业务唯一性：evaluation_id、obligation_id、intent_sequence',
  KEY `idx_intent_rule_action` (`rule_action_id`) COMMENT '支持访问路径：rule_action_id',
  KEY `idx_intent_obligation` (`obligation_id`) COMMENT '支持访问路径：obligation_id',
  KEY `idx_intent_action_ver` (`action_version_id`) COMMENT '支持访问路径：action_version_id',
  KEY `idx_intent_effect` (`merged_effect_id`) COMMENT '支持访问路径：merged_effect_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存规则 Action 或 Action 型系统义务命中后、冲突合并前产生的原始有序 Action；RESERVE_LIMIT_GROUP 义务不进入本表';

CREATE TABLE `t_action_effect` (
  `effect_id` BIGINT NOT NULL COMMENT '合并后 Action Effect 的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `action_version_id` BIGINT NOT NULL COMMENT 'Action 版本的稳定业务标识。',
  `effect_mode` VARCHAR(64) NOT NULL COMMENT 'Effect 模式的稳定业务代码。允许值：REAL、SIMULATED。',
  `target_type` VARCHAR(64) NOT NULL COMMENT '目标类型的稳定业务代码。取值来源：Risk Action 目标类型目录；非固定枚举。',
  `target_key` VARCHAR(256) NOT NULL COMMENT '目标键的业务值。',
  `merge_policy` VARCHAR(64) NOT NULL COMMENT '合并策略的稳定业务代码。允许值：FIRST、LAST、MOST_RESTRICTIVE、UNION、MIN、MAX。',
  `effect_hash` CHAR(64) NOT NULL COMMENT 'Effect 内容哈希的 SHA-256 小写十六进制值。',
  `effect_status` VARCHAR(64) NOT NULL COMMENT 'Effect 状态的稳定业务代码。允许值：CREATED、APPLIED、PENDING_ASYNC、FAILED、SIMULATED。',
  `idempotency_key` VARCHAR(128) NULL COMMENT '执行幂等键的业务值。',
  `execution_type` VARCHAR(64) NULL COMMENT '执行类型的稳定业务代码。允许值：LOCAL_SYNC、LOCAL_HANDLER、MQ_PUBLISH。',
  `execution_status` VARCHAR(64) NULL COMMENT '执行状态的稳定业务代码。允许值：INIT、PROCESSING、SUCCEEDED、ERROR、FAILED。',
  `attempt_count` INT NULL COMMENT '执行次数的整数值。',
  `started_time` DATETIME(6) NULL COMMENT '开始时间对应的 UTC 微秒时间。',
  `completed_time` DATETIME(6) NULL COMMENT '完成时间对应的 UTC 微秒时间。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近错误代码的稳定业务代码。取值来源：Risk Action 执行错误目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要的业务值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`effect_id`),
  UNIQUE KEY `uk_action_effect` (`evaluation_id`, `action_version_id`, `target_type`, `target_key`) COMMENT '保证业务唯一性：evaluation_id、action_version_id、target_type、target_key',
  UNIQUE KEY `uk_action_effect_key` (`idempotency_key`) COMMENT '保证业务唯一性：idempotency_key',
  KEY `idx_effect_action_ver` (`action_version_id`) COMMENT '支持访问路径：action_version_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存同目标 Action Intent 按固定策略合并后的最终真实或模拟副作用';

CREATE TABLE `t_checkout_input_snapshot` (
  `snapshot_id` BIGINT NOT NULL COMMENT '结账风控输入快照的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '正式决策评估的稳定业务标识。',
  `order_id` BIGINT NOT NULL COMMENT 'Commerce 预分配订单的稳定业务标识；结账风控点的业务身份。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 客户的稳定业务标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'CCT 为本次结账确定的 Operating Entity 编号。取值来源：CCT Operating Entity；非固定枚举。',
  `entity_source_type` VARCHAR(64) NOT NULL COMMENT '经营主体来源的稳定业务代码。允许值：CLIENT、PARTNER。',
  `local_partner_id` BIGINT NULL COMMENT 'PARTNER 来源主体所属 Local Partner 的稳定业务标识；其他来源为空。',
  `consumer_country_code` CHAR(2) NOT NULL COMMENT '订单上的 ISO 3166-1 alpha-2 消费者国家。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 alpha-3 订单币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `merchandise_amount` DECIMAL(24,8) NOT NULL COMMENT '优惠前商品总额，订单币种主单位，精度为 24 位、小数位为 8 位。',
  `discount_amount` DECIMAL(24,8) NOT NULL COMMENT '商品优惠总额，订单币种主单位，精度为 24 位、小数位为 8 位。',
  `net_amount` DECIMAL(24,8) NOT NULL COMMENT '税前商品净额，订单币种主单位，精度为 24 位、小数位为 8 位。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '行业代码的稳定业务代码。取值来源：CCT 行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT 'CCT 确定且保留前导零的四位 MCC。取值来源：CCT 批准 MCC 目录；非固定枚举。',
  `line_count` INT NOT NULL COMMENT '订单商品明细行数的正整数值。',
  `stable_input_hash` CHAR(64) NOT NULL COMMENT '业务身份与全部输入字段的稳定输入哈希，SHA-256 小写十六进制值；重放内容比较使用。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`snapshot_id`),
  UNIQUE KEY `uk_checkout_eval` (`evaluation_id`) COMMENT '保证业务唯一性：evaluation_id',
  UNIQUE KEY `uk_checkout_order` (`order_id`) COMMENT '保证业务唯一性：order_id',
  KEY `idx_checkout_client` (`client_id`, `create_time`) COMMENT '支持访问路径：client_id、create_time',
  KEY `idx_checkout_entity` (`entity_no`, `create_time`) COMMENT '支持访问路径：entity_no、create_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一次 CHECKOUT_PREPARE 正式评估使用的 Commerce 订单与商业事实输入及稳定输入摘要';

CREATE TABLE `t_transaction_risk_assessment` (
  `assessment_id` BIGINT NOT NULL COMMENT '交易风险评估快照的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '支付 Intent的稳定业务标识。',
  `payment_attempt_id` BIGINT NOT NULL COMMENT '支付 Attempt的稳定业务标识。',
  `risk_action_id` BIGINT NOT NULL COMMENT 'Payment 风控 Action的稳定业务标识。',
  `order_id` BIGINT NOT NULL COMMENT '订单的稳定业务标识。',
  `client_id` BIGINT NOT NULL COMMENT 'Client 客户的稳定业务标识。',
  `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Payment Intent 冻结的 Operating Entity 编号。取值来源：CCT Operating Entity；非固定枚举。',
  `payment_method_code` VARCHAR(64) NOT NULL COMMENT '支付方式代码的稳定业务代码。取值来源：Payment Method Catalog；非固定枚举。',
  `capture_mode` VARCHAR(64) NOT NULL COMMENT '捕获模式的稳定业务代码。允许值：AUTOMATIC、MANUAL。',
  `amount` DECIMAL(24,8) NOT NULL COMMENT '支付金额，精度为 24 位、小数位为 8 位。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 alpha-3 支付币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `industry_code` VARCHAR(64) NOT NULL COMMENT '行业代码的稳定业务代码。取值来源：CCT 行业目录；非固定枚举。',
  `mcc_code` VARCHAR(4) NOT NULL COMMENT 'Payment Intent 冻结且保留前导零的四位 MCC。取值来源：CCT 批准 MCC 目录；非固定枚举。',
  `consumer_country_code` CHAR(2) NULL COMMENT 'Payment Intent 冻结的 ISO 3166-1 alpha-2 消费者国家；未提供时为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `consumer_reference` VARCHAR(128) NULL COMMENT '消费者引用的业务值。',
  `consumer_email_vault_token` VARCHAR(128) NULL COMMENT '消费者 Email Token的业务值。',
  `consumer_phone_vault_token` VARCHAR(128) NULL COMMENT '消费者 Phone Token的业务值。',
  `client_ip_address` VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '首次评估冻结的消费者 IP 原值；受限字段，不进入日志、MQ 或稳定输入摘要。',
  `user_agent` VARCHAR(512) NULL COMMENT '首次评估冻结的浏览器 User-Agent；仅记录，不参与规则或稳定输入摘要。',
  `card_fingerprint` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment Card 提供的跨交易稳定卡指纹；非卡支付为空。',
  `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '卡号前 6 至 8 位数字；非卡支付为空。',
  `issuer_country_code` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的发卡国 ISO 3166-1 alpha-2；未识别或非卡支付为空。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `card_brand` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的卡组织；未识别或非卡支付为空。取值来源：Card BIN 库卡组织代码；非固定枚举。',
  `funding_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT 'Payment 冻结的借贷记类型；未识别或非卡支付为空。取值来源：Card BIN 库借贷记代码；非固定枚举。',
  `authentication_requirement` VARCHAR(64) NULL COMMENT '认证要求的稳定业务代码。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。',
  `stable_input_hash` CHAR(64) NOT NULL COMMENT '稳定输入哈希的 SHA-256 小写十六进制值。',
  `restricted_input_hash` CHAR(64) NOT NULL COMMENT '受限输入哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`assessment_id`),
  UNIQUE KEY `uk_txn_risk_eval` (`evaluation_id`) COMMENT '保证业务唯一性：evaluation_id',
  UNIQUE KEY `uk_txn_risk_attempt` (`payment_attempt_id`) COMMENT '保证业务唯一性：payment_attempt_id',
  KEY `idx_txn_risk_order` (`order_id`) COMMENT '支持访问路径：order_id',
  KEY `idx_txn_risk_client` (`client_id`, `create_time`) COMMENT '支持访问路径：client_id、create_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一个 Payment Attempt 风控评估使用的 Payment 最小输入、稳定输入摘要和认证要求';

CREATE TABLE `t_settlement_risk_assessment` (
  `assessment_id` BIGINT NOT NULL COMMENT '结算风险评估快照的稳定业务标识。',
  `evaluation_id` BIGINT NOT NULL COMMENT '决策评估的稳定业务标识。',
  `checkpoint_type` VARCHAR(64) NOT NULL COMMENT '出款检查点的稳定业务代码。允许值：PAYOUT_CREATION、PAYOUT_FINAL_APPROVAL、PAYOUT_EXECUTION_CREATION、PAYOUT_EXTERNAL_SUBMISSION。',
  `role_type` VARCHAR(64) NOT NULL COMMENT '角色类型的稳定业务代码。允许值：CLIENT、LOCAL_PARTNER。',
  `role_id` BIGINT NOT NULL COMMENT '角色的稳定业务标识。',
  `operating_entity_id` BIGINT NOT NULL COMMENT '经营主体的稳定业务标识。',
  `gross_amount` DECIMAL(24,8) NOT NULL COMMENT '等于 Finance owner_debit_amount 与 cash_required_amount；不得使用 requested_amount 或 beneficiary_amount。',
  `currency` CHAR(3) NOT NULL COMMENT 'ISO 4217 alpha-3 出款币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `bank_country` CHAR(2) NOT NULL COMMENT 'Finance Beneficiary Account 冻结的 ISO 3166-1 alpha-2 开户银行国家。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `source_fact_type` VARCHAR(64) NOT NULL COMMENT '来源事实类型的稳定业务代码。允许值：PAYOUT、PAYOUT_EXECUTION。',
  `source_fact_id` BIGINT NOT NULL COMMENT '来源事实的稳定业务标识。',
  `source_fact_version` INT NOT NULL COMMENT '评估命令观察并锁定的变更前 expected version，创建类检查固定为 1，不得引用尚未产生的未来版本。',
  `input_hash` CHAR(64) NOT NULL COMMENT '结算输入哈希的 SHA-256 小写十六进制值。',
  `eligibility_decision` VARCHAR(64) NULL COMMENT '结算资格决定的稳定业务代码。允许值：ALLOW、RESTRICT。',
  `required_amount` DECIMAL(24,8) NULL COMMENT '最低保证金金额，精度为 24 位、小数位为 8 位。',
  `required_currency` CHAR(3) NULL COMMENT '最低保证金使用的 ISO 4217 alpha-3 币种。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `control_set_hash` CHAR(64) NULL COMMENT '命中控制集合哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`assessment_id`),
  UNIQUE KEY `uk_settle_risk_eval` (`evaluation_id`) COMMENT '保证业务唯一性：evaluation_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结一次 Client 或 Local Partner 手工出款资格检查的检查点输入和最终结果';

CREATE TABLE `t_risk_restriction` (
  `restriction_id` BIGINT NOT NULL COMMENT '风险限制的稳定业务标识。',
  `restriction_no` VARCHAR(64) NOT NULL COMMENT '风险限制编号的业务值。',
  `restriction_type` VARCHAR(64) NOT NULL COMMENT '限制类型的稳定业务代码。允许值：COOLDOWN、BLOCK、REVIEW_ONLY。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY。',
  `subject_key` VARCHAR(256) NOT NULL COMMENT '规范化主体键的业务值。',
  `source_effect_id` BIGINT NOT NULL COMMENT '来源 Action Effect的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '限制状态的稳定业务代码。允许值：ACTIVE、EXPIRED、RELEASED、SUPERSEDED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `supersedes_restriction_id` BIGINT NULL COMMENT '被替代限制的稳定业务标识。',
  `version` INT NOT NULL COMMENT '限制并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`restriction_id`),
  UNIQUE KEY `uk_risk_restrict_no` (`restriction_no`) COMMENT '保证业务唯一性：restriction_no',
  KEY `idx_restrict_subject` (`subject_type`, `subject_key`, `status`, `effective_from`) COMMENT '支持访问路径：subject_type、subject_key、status、effective_from',
  KEY `idx_restrict_effect` (`source_effect_id`) COMMENT '支持访问路径：source_effect_id',
  KEY `idx_restrict_prev` (`supersedes_restriction_id`) COMMENT '支持访问路径：supersedes_restriction_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存消费者、IP 或其他受控主体的冷却、阻断及其有效区间';

CREATE TABLE `t_risk_list` (
  `list_id` BIGINT NOT NULL COMMENT '风险名单的稳定业务标识。',
  `list_code` VARCHAR(64) NOT NULL COMMENT '名单编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `list_name` VARCHAR(255) NOT NULL COMMENT '名单名称的业务值。',
  `supported_subject_type` VARCHAR(64) NOT NULL COMMENT '支持主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。',
  `matching_mode` VARCHAR(64) NOT NULL COMMENT '匹配模式的稳定业务代码。取值来源：Risk 工程注册名单匹配模式目录；非固定枚举。',
  `list_purpose` VARCHAR(64) NOT NULL COMMENT '名单用途的稳定业务代码。允许值：BLOCK、ALLOW。',
  `status` VARCHAR(64) NOT NULL COMMENT '名单状态的稳定业务代码。允许值：ACTIVE、INACTIVE。',
  `version` INT NOT NULL COMMENT '名单并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`list_id`),
  UNIQUE KEY `uk_risk_list_code` (`list_code`) COMMENT '保证业务唯一性：list_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存由工程注册匹配模式解释的风险名单稳定定义';

CREATE TABLE `t_risk_list_entry` (
  `entry_id` BIGINT NOT NULL COMMENT '风险名单成员的稳定业务标识。',
  `list_id` BIGINT NOT NULL COMMENT '风险名单的稳定业务标识。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。',
  `subject_key` VARCHAR(256) NOT NULL COMMENT '规范化主体键的业务值。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '成员来源的稳定业务代码。允许值：ACTION、MANUAL。',
  `source_effect_id` BIGINT NULL COMMENT '来源 Action Effect的稳定业务标识。',
  `reason` VARCHAR(500) NULL COMMENT '加入原因的业务值。',
  `created_by` BIGINT NULL COMMENT '人工添加人的稳定业务标识。',
  `deactivated_by` BIGINT NULL COMMENT '人工失效人的稳定业务标识。',
  `deactivated_reason` VARCHAR(500) NULL COMMENT '失效原因的业务值。',
  `status` VARCHAR(64) NOT NULL COMMENT '成员状态的稳定业务代码。允许值：ACTIVE、EXPIRED、RELEASED、SUPERSEDED。',
  `active_slot` BOOLEAN NULL COMMENT '当前生效槽位，1 表示是，0 表示否，NULL 表示不适用。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '生效时间对应的 UTC 微秒时间。',
  `effective_to` DATETIME(6) NULL COMMENT '失效时间对应的 UTC 微秒时间。',
  `supersedes_entry_id` BIGINT NULL COMMENT '被替代成员的稳定业务标识。',
  `version` INT NOT NULL COMMENT '成员并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`entry_id`),
  UNIQUE KEY `uk_list_entry_active` (`list_id`, `subject_type`, `subject_key`, `active_slot`) COMMENT '保证业务唯一性：list_id、subject_type、subject_key、active_slot',
  KEY `idx_list_entry_subject` (`subject_type`, `subject_key`, `effective_from`) COMMENT '支持访问路径：subject_type、subject_key、effective_from',
  KEY `idx_list_entry_effect` (`source_effect_id`) COMMENT '支持访问路径：source_effect_id',
  KEY `idx_list_entry_prev` (`supersedes_entry_id`) COMMENT '支持访问路径：supersedes_entry_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存名单内规范化主体的生效区间、来源 Action 或人工维护记录和替代链';

CREATE TABLE `t_payment_attempt_fact` (
  `fact_id` BIGINT NOT NULL COMMENT 'Payment Attempt 事实投影的稳定业务标识。',
  `payment_attempt_id` BIGINT NOT NULL COMMENT '支付 Attempt的稳定业务标识。',
  `payment_intent_id` BIGINT NOT NULL COMMENT '支付 Intent的稳定业务标识。',
  `attempt_status` VARCHAR(64) NOT NULL COMMENT 'Attempt 状态的稳定业务代码。允许值：CREATED、SUBMITTED、REQUIRES_ACTION、AUTHORIZED、SUCCEEDED、FAILED、CANCELLED、VOIDED、UNKNOWN、CONFLICTED。',
  `result_reason` VARCHAR(64) NULL COMMENT '结果原因的稳定业务代码。取值来源：Payment Attempt 状态原因目录；非固定枚举。',
  `limit_reservation_group_id` BIGINT NULL COMMENT '限额占用组的稳定业务标识。',
  `source_event_key` VARCHAR(128) NOT NULL COMMENT '来源事件键的业务值。',
  `source_version` INT NOT NULL COMMENT '来源版本的整数值。',
  `source_payload_hash` CHAR(64) NOT NULL COMMENT '来源载荷哈希的 SHA-256 小写十六进制值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '来源发生时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '事实投影并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`fact_id`),
  UNIQUE KEY `uk_attempt_fact` (`payment_attempt_id`) COMMENT '保证业务唯一性：payment_attempt_id',
  UNIQUE KEY `uk_attempt_event` (`source_event_key`) COMMENT '保证业务唯一性：source_event_key',
  KEY `idx_attempt_limit_group` (`limit_reservation_group_id`) COMMENT '支持访问路径：limit_reservation_group_id',
  KEY `idx_attempt_intent` (`payment_intent_id`) COMMENT '支持访问路径：payment_intent_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存 Payment Attempt 权威状态、Limit Group 和单调来源版本的本地投影';

CREATE TABLE `t_velocity_event` (
  `velocity_event_id` BIGINT NOT NULL COMMENT '风险频次事件的稳定业务标识。',
  `event_code` VARCHAR(64) NOT NULL COMMENT '事件编码的稳定业务代码。取值来源：Risk Velocity 事件目录；非固定枚举。',
  `source_type` VARCHAR(64) NOT NULL COMMENT '来源类型的稳定业务代码。取值来源：Risk Velocity 来源类型目录；非固定枚举。',
  `source_id` BIGINT NOT NULL COMMENT '来源事实的稳定业务标识。',
  `source_version` INT NOT NULL COMMENT '来源版本的整数值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '事件发生时间对应的 UTC 微秒时间。',
  `amount` DECIMAL(24,8) NULL COMMENT '事件金额，精度为 24 位、小数位为 8 位。',
  `currency` CHAR(3) NULL COMMENT 'ISO 4217 alpha-3 事件币种；无金额时为 NULL。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `event_hash` CHAR(64) NOT NULL COMMENT '风险事件哈希的 SHA-256 小写十六进制值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`velocity_event_id`),
  UNIQUE KEY `uk_velocity_source` (`source_type`, `source_id`, `source_version`, `event_code`) COMMENT '保证业务唯一性：source_type、source_id、source_version、event_code',
  KEY `idx_velocity_time` (`event_code`, `occurred_time`) COMMENT '支持访问路径：event_code、occurred_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存供滑动窗口 Feature 精确计数或计额的不可变来源事件';

CREATE TABLE `t_velocity_event_subject` (
  `event_subject_id` BIGINT NOT NULL COMMENT '风险事件主体关联的稳定业务标识。',
  `velocity_event_id` BIGINT NOT NULL COMMENT '风险频次事件的稳定业务标识。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY、CARD_FINGERPRINT、CARD_BIN。',
  `subject_key` VARCHAR(256) NOT NULL COMMENT '规范化主体键的业务值。',
  `event_code` VARCHAR(64) NOT NULL COMMENT '冻结事件编码的稳定业务代码。取值来源：Risk Velocity 事件目录；非固定枚举。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '冻结事件发生时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`event_subject_id`),
  UNIQUE KEY `uk_velocity_subject` (`velocity_event_id`, `subject_type`, `subject_key`) COMMENT '保证业务唯一性：velocity_event_id、subject_type、subject_key',
  KEY `idx_velocity_subject` (`subject_type`, `subject_key`, `event_code`, `occurred_time`, `velocity_event_id`) COMMENT '支持访问路径：subject_type、subject_key、event_code、occurred_time、velocity_event_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存风险频次事件与受控主体的明确关联及用于窗口查询的冻结事件维度';

CREATE TABLE `t_limit_version` (
  `limit_version_id` BIGINT NOT NULL COMMENT '限额版本的稳定业务标识。',
  `limit_code` VARCHAR(64) NOT NULL COMMENT '限额编码的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `limit_name` VARCHAR(255) NOT NULL COMMENT '限额名称的业务值。',
  `version_no` INT NOT NULL COMMENT '版本号的整数值。',
  `subject_type` VARCHAR(64) NOT NULL COMMENT '限额主体类型的稳定业务代码。允许值：CONSUMER、EMAIL_TOKEN、PHONE_TOKEN、IP_ADDRESS、CLIENT、OPERATING_ENTITY。',
  `metric_type` VARCHAR(64) NOT NULL COMMENT '计量类型的稳定业务代码。允许值：AMOUNT、COUNT、AMOUNT_AND_COUNT。',
  `window_unit` VARCHAR(64) NOT NULL COMMENT '窗口单位的稳定业务代码。允许值：MINUTE、HOUR、DAY、MONTH。',
  `window_size` INT NOT NULL COMMENT '窗口大小的整数值。',
  `amount_limit` DECIMAL(24,8) NULL COMMENT '金额阈值，精度为 24 位、小数位为 8 位。',
  `count_limit` BIGINT NULL COMMENT '次数阈值的长整数值。',
  `currency_mode` VARCHAR(64) NOT NULL COMMENT '币种模式的稳定业务代码。允许值：FIXED、PER_CURRENCY、NO_CURRENCY。',
  `fixed_currency` CHAR(3) NULL COMMENT 'FIXED 模式使用的 ISO 4217 alpha-3 币种，其他模式为 NULL。取值来源：ISO 4217 alpha-3；非固定枚举。',
  `content_hash` CHAR(64) NOT NULL COMMENT '限额版本内容哈希的 SHA-256 小写十六进制值。',
  `status` VARCHAR(64) NOT NULL COMMENT '限额版本状态的稳定业务代码。允许值：DRAFT、FROZEN、RETIRED。',
  `frozen_by` BIGINT NULL COMMENT '冻结操作人的稳定业务标识。',
  `frozen_time` DATETIME(6) NULL COMMENT '冻结时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`limit_version_id`),
  UNIQUE KEY `uk_limit_version` (`limit_code`, `version_no`) COMMENT '保证业务唯一性：limit_code、version_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='以稳定编码和版本号冻结硬限额身份、主体、计量方式、UTC 窗口及阈值；content_hash 使用 CODE-MOR:RISK:LIMIT_VERSION:1 Canonical Schema';

CREATE TABLE `t_decision_point_limit_binding` (
  `binding_id` BIGINT NOT NULL COMMENT '事件点限额绑定的稳定业务标识。',
  `obligation_id` BIGINT NOT NULL COMMENT '系统义务的稳定业务标识。',
  `limit_version_id` BIGINT NOT NULL COMMENT '限额版本的稳定业务标识。',
  `sequence_no` INT NOT NULL COMMENT '执行顺序的整数值。',
  `subject_source_type` VARCHAR(64) NOT NULL COMMENT '主体来源类型的稳定业务代码。允许值：FEATURE、CONTEXT。',
  `subject_feature_version_id` BIGINT NULL COMMENT '主体 Feature 版本的稳定业务标识。',
  `subject_input_field_id` BIGINT NULL COMMENT '主体输入字段的稳定业务标识。',
  `amount_source_type` VARCHAR(64) NULL COMMENT '金额来源类型的稳定业务代码。允许值：FEATURE、CONTEXT。',
  `amount_feature_version_id` BIGINT NULL COMMENT '金额 Feature 版本的稳定业务标识。',
  `amount_input_field_id` BIGINT NULL COMMENT '金额输入字段的稳定业务标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`binding_id`),
  UNIQUE KEY `uk_point_limit_ver` (`obligation_id`, `limit_version_id`) COMMENT '保证业务唯一性：obligation_id、limit_version_id',
  UNIQUE KEY `uk_point_limit_seq` (`obligation_id`, `sequence_no`) COMMENT '保证业务唯一性：obligation_id、sequence_no',
  KEY `idx_point_limit_ver` (`limit_version_id`) COMMENT '支持访问路径：limit_version_id',
  KEY `idx_point_limit_sub_feat` (`subject_feature_version_id`) COMMENT '支持访问路径：subject_feature_version_id',
  KEY `idx_point_limit_sub_input` (`subject_input_field_id`) COMMENT '支持访问路径：subject_input_field_id',
  KEY `idx_point_limit_amt_feat` (`amount_feature_version_id`) COMMENT '支持访问路径：amount_feature_version_id',
  KEY `idx_point_limit_amt_input` (`amount_input_field_id`) COMMENT '支持访问路径：amount_input_field_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='冻结纯限额系统义务采用的有序 Limit Version，以及主体键和金额的 Feature 或 Context 来源；零绑定表示仍创建空占用组';

CREATE TABLE `t_limit_position` (
  `position_id` BIGINT NOT NULL COMMENT '限额计数器的稳定业务标识。',
  `limit_version_id` BIGINT NOT NULL COMMENT '限额版本的稳定业务标识。',
  `subject_key` VARCHAR(256) NOT NULL COMMENT '规范化主体键的业务值。',
  `currency_scope_key` VARCHAR(16) NOT NULL COMMENT 'FIXED/PER_CURRENCY 使用实际 ISO 4217 alpha-3 币种，NO_CURRENCY 使用字面值 NO_CURRENCY。',
  `window_start` DATETIME(6) NOT NULL COMMENT '窗口开始时间对应的 UTC 微秒时间。',
  `window_end` DATETIME(6) NOT NULL COMMENT '窗口结束时间对应的 UTC 微秒时间。',
  `reserved_amount` DECIMAL(24,8) NOT NULL COMMENT '已预占金额，精度为 24 位、小数位为 8 位。',
  `committed_amount` DECIMAL(24,8) NOT NULL COMMENT '已提交金额，精度为 24 位、小数位为 8 位。',
  `reserved_count` BIGINT NOT NULL COMMENT '已预占次数的长整数值。',
  `committed_count` BIGINT NOT NULL COMMENT '已提交次数的长整数值。',
  `version` INT NOT NULL COMMENT '计数器并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`position_id`),
  UNIQUE KEY `uk_limit_position` (`limit_version_id`, `subject_key`, `currency_scope_key`, `window_start`) COMMENT '保证业务唯一性：limit_version_id、subject_key、currency_scope_key、window_start',
  KEY `idx_limit_pos_window` (`window_end`) COMMENT '支持访问路径：window_end'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存某 Limit Version 与规范化主体在一个固定 UTC 窗口内的权威预占和已提交金额次数';

CREATE TABLE `t_limit_reservation_group` (
  `reservation_group_id` BIGINT NOT NULL COMMENT '限额占用组的稳定业务标识。',
  `assessment_id` BIGINT NOT NULL COMMENT '交易风险评估的稳定业务标识。',
  `obligation_id` BIGINT NOT NULL COMMENT '纯限额系统义务的稳定业务标识。',
  `status` VARCHAR(64) NOT NULL COMMENT '占用组状态的稳定业务代码。允许值：RESERVED、PAYMENT_UNKNOWN、COMMITTED、RELEASED。',
  `hold_reason` VARCHAR(64) NULL COMMENT '继续占用原因的稳定业务代码。取值来源：Risk 限额占用原因目录；非固定枚举。',
  `resolved_time` DATETIME(6) NULL COMMENT '收口时间对应的 UTC 微秒时间。',
  `version` INT NOT NULL COMMENT '占用组并发版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`reservation_group_id`),
  UNIQUE KEY `uk_limit_group_assess` (`assessment_id`) COMMENT '保证业务唯一性：assessment_id',
  KEY `idx_limit_group_ob` (`obligation_id`) COMMENT '支持访问路径：obligation_id',
  KEY `idx_limit_group_status` (`status`, `create_time`) COMMENT '支持访问路径：status、create_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存一次交易风险评估对准确纯限额系统义务的原子占用集合；Group 是当前状态的唯一权威';

CREATE TABLE `t_limit_reservation` (
  `reservation_id` BIGINT NOT NULL COMMENT '限额占用明细的稳定业务标识。',
  `reservation_group_id` BIGINT NOT NULL COMMENT '限额占用组的稳定业务标识。',
  `position_id` BIGINT NOT NULL COMMENT '限额计数器的稳定业务标识。',
  `amount` DECIMAL(24,8) NULL COMMENT '占用金额，精度为 24 位、小数位为 8 位。',
  `count_value` BIGINT NOT NULL COMMENT '占用次数的长整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`reservation_id`),
  UNIQUE KEY `uk_limit_reservation` (`reservation_group_id`, `position_id`) COMMENT '保证业务唯一性：reservation_group_id、position_id',
  KEY `idx_limit_res_position` (`position_id`) COMMENT '支持访问路径：position_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变地保存占用组对一个 Position 的金额和次数占用；生命周期由 Group 与状态变更记录统一表达';

CREATE TABLE `t_limit_group_state_change` (
  `change_id` BIGINT NOT NULL COMMENT '限额占用组状态变更的稳定业务标识。',
  `reservation_group_id` BIGINT NOT NULL COMMENT '限额占用组的稳定业务标识。',
  `from_status` VARCHAR(64) NULL COMMENT '原状态的稳定业务代码。允许值：RESERVED、PAYMENT_UNKNOWN、COMMITTED、RELEASED。',
  `to_status` VARCHAR(64) NOT NULL COMMENT '新状态的稳定业务代码。允许值：RESERVED、PAYMENT_UNKNOWN、COMMITTED、RELEASED。',
  `reason_code` VARCHAR(64) NULL COMMENT '变更原因的稳定业务代码。取值来源：Risk 限额状态原因目录；非固定枚举。',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值。',
  `actor_id` BIGINT NULL COMMENT '操作人的稳定业务标识。',
  `aggregate_version` INT NOT NULL COMMENT '聚合版本的整数值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`change_id`),
  UNIQUE KEY `uk_limit_group_state` (`reservation_group_id`, `aggregate_version`) COMMENT '保证业务唯一性：reservation_group_id、aggregate_version'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存占用组状态迁移、触发来源和聚合版本';

CREATE TABLE `t_limit_movement` (
  `movement_id` BIGINT NOT NULL COMMENT '限额计数器变动的稳定业务标识。',
  `reservation_id` BIGINT NOT NULL COMMENT '限额占用的稳定业务标识。',
  `movement_type` VARCHAR(64) NOT NULL COMMENT '变动类型的稳定业务代码。允许值：RESERVE、COMMIT、RELEASE。',
  `amount_delta` DECIMAL(24,8) NOT NULL COMMENT '本次预占、提交或释放所转移的非负绝对金额。',
  `count_delta` BIGINT NOT NULL COMMENT '次数变化量的长整数值。',
  `trigger_type` VARCHAR(64) NOT NULL COMMENT '触发类型的稳定业务代码。取值来源：Risk & Compliance 受控代码目录；非固定枚举。',
  `trigger_id` VARCHAR(128) NOT NULL COMMENT '触发标识的业务值。',
  `occurred_time` DATETIME(6) NOT NULL COMMENT '发生时间对应的 UTC 微秒时间。',
  `position_version` INT NOT NULL COMMENT '变动后 Position 版本的整数值。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  PRIMARY KEY (`movement_id`),
  UNIQUE KEY `uk_limit_movement` (`trigger_type`, `trigger_id`, `movement_type`, `reservation_id`) COMMENT '保证业务唯一性：trigger_type、trigger_id、movement_type、reservation_id',
  KEY `idx_limit_move_res` (`reservation_id`) COMMENT '支持访问路径：reservation_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='追加保存预占、提交或释放对 Reservation 所属 Position 造成的权威金额和次数变动；Position 通过不可变 Reservation 重建';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '补偿事件的稳定业务标识。',
  `event_key` VARCHAR(128) NOT NULL COMMENT '事件键的业务值。',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型的稳定业务代码。取值来源：Risk & Compliance 应用全局唯一 Handler 目录；非固定枚举。',
  `sub_event_type` VARCHAR(64) NOT NULL COMMENT '事件子类型的稳定业务代码。取值来源：对应 Handler 的 Risk 业务子类型目录；非固定枚举。',
  `payload_json` VARCHAR(4096) NULL COMMENT '可选且不超过 4 KiB UTF-8 的最小非敏感 JSON Object；默认从本库权威事实重建。',
  `execute_status` VARCHAR(64) NOT NULL DEFAULT 'INIT' COMMENT '执行状态的稳定业务代码。允许值：INIT、PROCESSING、ERROR、FAILED。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '执行次数的整数值。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '下次执行时间对应的 UTC 微秒时间。',
  `lock_owner` VARCHAR(64) NULL COMMENT '租约持有者的业务值。',
  `lock_until` DATETIME(6) NULL COMMENT '租约截止时间对应的 UTC 微秒时间。',
  `last_error_code` VARCHAR(64) NULL COMMENT '最近错误代码的稳定业务代码。取值来源：Risk 补偿执行错误目录；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近脱敏错误摘要的业务值。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim 与 Lease 条件更新使用的正长整数版本，登记初始值固定为 1。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间对应的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间对应的 UTC 微秒时间。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '保证业务唯一性：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '支持访问路径：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '支持访问路径：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存与业务事务原子登记的 MQ 发布或本地可恢复动作及 Claim Lease';

-- 2026-10-29 REQ-03 承保记录（requirements/20261029/risk-decision-承保记录）：Client 承保版本、自动承保规则版本与币种阈值。
CREATE TABLE `t_client_underwriting` (
  `underwriting_id` BIGINT NOT NULL COMMENT '承保记录版本的稳定业务标识（每个版本一行）。',
  `client_id` BIGINT NOT NULL COMMENT '被承保 Client 的稳定业务标识（client-master 角色 ID，跨库值引用，不建外键）。',
  `version_no` INT NOT NULL COMMENT '同一 Client 内从 1 递增的承保版本号。',
  `status` VARCHAR(32) NOT NULL COMMENT '承保版本状态。允许值：DRAFT、SUBMITTED、APPROVED、DECLINED、SUPERSEDED。APPROVED 后内容不可变，被新版本批准替代时为 SUPERSEDED。',
  `open_slot` TINYINT(1) NULL COMMENT '进行中占位：DRAFT、SUBMITTED 为 1，其他为 NULL；保证同一 Client 至多一个进行中版本。',
  `effective_slot` TINYINT(1) NULL COMMENT '有效结论占位：当前有效的 APPROVED 为 1，其他为 NULL；保证同一 Client 至多一个有效承保结论。',
  `supersedes_underwriting_id` BIGINT NULL COMMENT '本版本批准后替代的上一有效版本 ID；首个版本或未替代时为空。',
  `decision_source` VARCHAR(32) NULL COMMENT '结论来源。允许值：MANUAL、AUTO_RULE；未决定时为空。',
  `rule_version_id` BIGINT NULL COMMENT 'AUTO_RULE 时使用的自动承保规则版本 ID；人工结论为空。',
  `client_display_name` VARCHAR(255) NOT NULL COMMENT '快照时 Client 的法定名称或个人姓名，仅供列表展示，冻结后不随主档变化。',
  `client_no` VARCHAR(64) NOT NULL COMMENT '快照时 Client 编号，仅供列表展示与搜索。',
  `client_type` VARCHAR(16) NOT NULL COMMENT '快照时 Client 类型。允许值：INDIVIDUAL、ORGANIZATION。',
  `jurisdiction_code` VARCHAR(16) NOT NULL COMMENT '快照时 Client 入驻辖区代码。取值来源：client-master 辖区包；非固定枚举。',
  `crr` VARCHAR(16) NULL COMMENT '快照时尽调客户风险评级。允许值：LOW、MEDIUM、HIGH；存量尽调未评为空，按 MEDIUM 处理。',
  `review_level` VARCHAR(16) NOT NULL COMMENT '决定复核要求的风险级别：crr 非空时等于 crr，为空时为 MEDIUM。允许值：LOW、MEDIUM、HIGH。HIGH 须四眼复核。',
  `dd_tier` VARCHAR(32) NULL COMMENT '快照时尽调档次。允许值：SIMPLIFIED、STANDARD、ENHANCED。',
  `mcc_code` CHAR(4) NULL COMMENT '快照时运营确认的主 MCC，四位数字保留前导零；草稿可空，提交必填。取值来源：Platform MCC 参考数据；非固定枚举。',
  `industry_code` VARCHAR(64) NULL COMMENT '快照时运营确认的主行业代码。取值来源：Platform 行业参考数据；非固定枚举。',
  `network_high_risk_mcc` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '快照时该 MCC 是否被卡组织公布为高风险（Platform t_ref_mcc.network_high_risk）。1 是，0 否。',
  `expected_monthly_volume` DECIMAL(24,8) NULL COMMENT '快照时 Client 申报的预计月交易金额，币种为 volume_currency。',
  `volume_currency` CHAR(3) NULL COMMENT '预计月交易金额与客单价的 ISO 4217 币种。取值来源：ISO 4217；非固定枚举。',
  `expected_ticket` DECIMAL(24,8) NULL COMMENT '快照时 Client 申报的预计单笔金额，币种为 volume_currency。',
  `fulfillment_type` VARCHAR(32) NULL COMMENT '快照时申报的交付方式。允许值：INSTANT、PRE_ORDER、SUBSCRIPTION、SERVICE；存量申请未采集为空。',
  `fulfillment_days` SMALLINT NULL COMMENT '快照时申报的预售交付天数 1—365；仅 PRE_ORDER 有值。',
  `website_url` VARCHAR(2048) NULL COMMENT '快照时申报的网站。',
  `refund_policy_url` VARCHAR(2048) NULL COMMENT '快照时申报的退款政策链接。',
  `expected_payment_methods` VARCHAR(512) NULL COMMENT '快照时申报的期望支付方式（commercial capability_code 规范化逗号串），原样冻结。',
  `source_case_id` BIGINT NULL COMMENT '输入来源：compliance 入驻尽调案件 ID；草稿可空，提交必填。',
  `source_case_version` BIGINT NULL COMMENT '输入来源：读取时的尽调案件版本。',
  `source_application_id` BIGINT NULL COMMENT '输入来源：client-master 入驻申请 ID。',
  `source_draft_revision` BIGINT NULL COMMENT '输入来源：读取时的申请资料版本 draft_revision。',
  `source_classification_confirmed_time` DATETIME(6) NULL COMMENT '输入来源：运营确认 MCC / 行业分类的 UTC 微秒时间；为空表示分类未确认，不能提交。',
  `observation_days` SMALLINT NULL COMMENT '承保设定的观察期天数 1—365；提交必填，默认 30。',
  `observation_transactions` INT NULL COMMENT '承保设定的观察期交易笔数 1—100000；提交必填，默认 100；以先到者为准由 REQ-04 解释。',
  `require_deposit` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否要求最低保证金（Risk 决策事实；下发 compliance 控制与执行归 REQ-18）。1 是，0 否。',
  `deposit_amount` DECIMAL(24,8) NULL COMMENT '要求的最低保证金金额，> 0；不要求时为空。',
  `deposit_currency` CHAR(3) NULL COMMENT '最低保证金 ISO 4217 币种；不要求时为空。取值来源：ISO 4217；非固定枚举。',
  `deposit_blocks_collection` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '保证金未到账前是否建议阻塞收款；只有 require_deposit=1 时可为 1。执行另由 REQ-04 / REQ-18 决定。',
  `suggested_settlement_days` SMALLINT NULL COMMENT '结算周期建议 T+N 的 N（0—90）；未给出为空。',
  `high_risk_mcc` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否属于需卡组织高风险登记的 MCC（默认取 network_high_risk_mcc，可人工改并写理由）；登记事实归 channel-connector。1 是，0 否。',
  `high_risk_mcc_reason` VARCHAR(500) NULL COMMENT 'high_risk_mcc 与默认值不同时的理由。',
  `estimated_exposure` DECIMAL(24,8) NULL COMMENT '估算敞口金额（B10，参考值）：expected_monthly_volume × exposure_risk_days / 30，保留 2 位；不可算为空。',
  `exposure_risk_days` SMALLINT NULL COMMENT '估算敞口使用的风险天数（交付方式 + T+N，T+N 为空按 0）；不可算为空。',
  `limit_note` VARCHAR(500) NULL COMMENT '额度说明（不设逐 Client 额度，见需求 Q-4）。',
  `underwriting_note` VARCHAR(2000) NULL COMMENT '承保说明：交付周期与拒付暴露评估及其他判断。',
  `handover_note` VARCHAR(500) NULL COMMENT '交接给商务的备注。',
  `decline_reason` VARCHAR(500) NULL COMMENT 'DECLINED 时的拒绝原因；其他状态为空。',
  `revision_reason` VARCHAR(500) NULL COMMENT '由修订创建的版本的修订原因；首个版本与其他新建版本为空。',
  `review_comment` VARCHAR(500) NULL COMMENT 'HIGH 复核时复核人填写的意见；可空。',
  `created_by` BIGINT NOT NULL COMMENT '建草稿或触发自动承保的 Operations 账号 ID。',
  `submitted_by` BIGINT NULL COMMENT '提交人 Operations 账号 ID；自动承保为空。',
  `submitted_time` DATETIME(6) NULL COMMENT '提交（输入快照冻结）的 UTC 微秒时间；自动承保为生成时间。',
  `decided_by` BIGINT NULL COMMENT '作出 APPROVED / DECLINED 的 Operations 账号 ID；LOW / MEDIUM 等于提交人，HIGH 为复核人，自动承保为空。',
  `decided_time` DATETIME(6) NULL COMMENT '作出结论的 UTC 微秒时间。',
  `superseded_time` DATETIME(6) NULL COMMENT '被新版本替代的 UTC 微秒时间；未替代为空。',
  `version` INT NOT NULL COMMENT '并发更新版本，初始为 1，每次条件更新加一。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`underwriting_id`),
  UNIQUE KEY `uk_client_underwriting_ver` (`client_id`, `version_no`) COMMENT '保证业务唯一性：client_id、version_no',
  UNIQUE KEY `uk_client_underwriting_open` (`client_id`, `open_slot`) COMMENT '同一 Client 至多一个进行中版本',
  UNIQUE KEY `uk_client_underwriting_effective` (`client_id`, `effective_slot`) COMMENT '同一 Client 至多一个有效结论',
  KEY `idx_client_underwriting_list` (`status`, `update_time`) COMMENT '支持访问路径：status、update_time（列表与待复核）',
  KEY `idx_client_underwriting_decided` (`status`, `decided_time`) COMMENT '支持访问路径：status、decided_time（本月已批准 / 已拒绝统计）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Client 承保记录：每个版本一行，提交时冻结输入快照与来源坐标；APPROVED 不可变，修订产生新版本并替代旧版本；Risk 最低保证金要求的决策事实';

CREATE TABLE `t_underwriting_rule_version` (
  `rule_version_id` BIGINT NOT NULL COMMENT '自动承保规则版本的稳定业务标识。',
  `version_no` INT NOT NULL COMMENT '从 1 递增的规则版本号。',
  `status` VARCHAR(32) NOT NULL COMMENT '规则版本状态。允许值：DRAFT、ACTIVE、RETIRED；同时至多一个 ACTIVE。',
  `active_slot` TINYINT(1) NULL COMMENT 'ACTIVE 为 1，其他为 NULL。',
  `excluded_mcc_codes` VARCHAR(1024) NULL COMMENT '风控额外排除（不可自动承保）的 MCC，四位数字、升序、英文逗号分隔；为空表示只排除卡组织高风险。',
  `activated_time` DATETIME(6) NULL COMMENT '置为 ACTIVE 的 UTC 微秒时间。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`rule_version_id`),
  UNIQUE KEY `uk_uw_rule_version_no` (`version_no`) COMMENT '保证业务唯一性：version_no',
  UNIQUE KEY `uk_uw_rule_version_active` (`active_slot`) COMMENT '至多一个 ACTIVE 版本'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动承保规则版本（CRR=LOW、币种月流水阈值、非高风险 MCC）；ACTIVE 后内容不改，新阈值以新版本 DML 发布';

CREATE TABLE `t_underwriting_rule_threshold` (
  `threshold_id` BIGINT NOT NULL COMMENT '阈值行的稳定业务标识。',
  `rule_version_id` BIGINT NOT NULL COMMENT '所属自动承保规则版本 ID。',
  `currency` CHAR(3) NOT NULL COMMENT '预计月流水币种（ISO 4217）；没有阈值的币种不能自动承保。取值来源：ISO 4217；非固定枚举。',
  `monthly_volume_limit` DECIMAL(24,8) NOT NULL COMMENT '可自动承保的预计月流水上限（含），> 0。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Risk & Compliance 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`threshold_id`),
  UNIQUE KEY `uk_uw_rule_threshold` (`rule_version_id`, `currency`) COMMENT '保证业务唯一性：rule_version_id、currency'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自动承保规则版本的币种月流水阈值';

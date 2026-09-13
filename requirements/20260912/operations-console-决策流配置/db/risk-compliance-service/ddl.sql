-- RCO-17 已确认的 4 表增量 DDL；本机授权与执行范围见 local-application.md。
-- 对应唯一 PDManer、生成源和完整 Schema；本文件不授权其他环境执行。
-- 手工连接并核对目标库；不建库、不回填旧数据、不修改现有表。

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
  `condition_json` VARCHAR(8192) NULL COMMENT '仅 CONDITIONAL 必填；受控 schemaVersion=1 JSON Object，规范化后最多 8192 UTF-8 字节，最多 32 个条件树节点、深度 8；其他类型为空。仅整树读取，不用 JSON 路径做数据库检索。',
  `create_time` DATETIME(6) NOT NULL COMMENT '本行在 Risk 数据库中创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '不可变配置明细，始终等于 create_time。',
  PRIMARY KEY (`edge_id`),
  UNIQUE KEY `uk_flow_edge_key` (`flow_version_id`, `edge_key`) COMMENT '保证业务唯一性：flow_version_id、edge_key',
  UNIQUE KEY `uk_flow_edge_order` (`flow_version_id`, `source_node_id`, `priority_no`) COMMENT '保证业务唯一性：flow_version_id、source_node_id、priority_no'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存版本内节点连线、显式出口顺序及受控条件树；一条连线一个条件，不保存求值结果';


-- Vault 策略入库与人工访问审计：结构变更（阶段 3 评审稿，待执行文件）
-- 目标库：mor_vault（各环境分别执行；应用启动不会执行 DDL）。
-- 字符集：用 mysql --default-character-set=utf8mb4 执行；客户端默认 latin1 会把中文（注释、名称、理由）写成乱码。
-- 新增 3 张表（31 个字段）；修改 1 张表：t_vault_access_event 新增 3 个可空字段与 1 个索引（policy_version 仅改注释）。
-- 不新增 CHECK / FOREIGN KEY（项目 2026-09-04 规范）；既有约束不变。
-- 执行前备份 mor_vault；回滚：DROP 三张新表，ALTER TABLE t_vault_access_event DROP INDEX/COLUMN（新字段可空，回滚不影响既有服务）。
-- 初始策略数据见同目录 dml-local.sql / dml-test.sql；Vault 不读取策略文件。

CREATE TABLE t_vault_data_type (
  data_type_id BIGINT NOT NULL COMMENT '数据类型服务侧 Snowflake 主键',
  data_type_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据类型编码，创建后不可修改',
  token_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Token 模式 REFERENCE/STABLE，创建后不可修改',
  data_class VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据级别 STANDARD 普通敏感 / SECRET 密钥',
  description VARCHAR(256) NOT NULL COMMENT '用途说明',
  manual_protect_enabled TINYINT(1) NOT NULL COMMENT '是否允许运营人工加密',
  manual_reveal_enabled TINYINT(1) NOT NULL COMMENT '是否允许运营人工解密',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态 ACTIVE/DISABLED；只停用不删除',
  version INT NOT NULL COMMENT '乐观锁版本',
  create_time DATETIME(6) NOT NULL COMMENT '创建的 UTC 时刻',
  update_time DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 时刻',
  PRIMARY KEY (data_type_id),
  UNIQUE KEY uk_vault_data_type_code (data_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vault 数据类型目录';

CREATE TABLE t_vault_access_rule (
  access_rule_id BIGINT NOT NULL COMMENT '访问规则服务侧 Snowflake 主键',
  caller_service VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用服务名',
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '动作 PROTECT/REVEAL',
  data_type_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '数据类型编码',
  purpose_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '访问用途编码',
  status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态 ACTIVE/DISABLED',
  version INT NOT NULL COMMENT '乐观锁版本',
  create_time DATETIME(6) NOT NULL COMMENT '创建的 UTC 时刻',
  update_time DATETIME(6) NOT NULL COMMENT '最后更新的 UTC 时刻',
  PRIMARY KEY (access_rule_id),
  UNIQUE KEY uk_vault_access_rule (caller_service, action, data_type_code, purpose_code),
  KEY idx_vault_access_rule_type (data_type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vault 服务访问规则（caller × action × data type × purpose）';

CREATE TABLE t_vault_policy_change (
  policy_change_id BIGINT NOT NULL COMMENT '策略变更服务侧 Snowflake 主键',
  policy_version_no INT NOT NULL COMMENT '变更后的策略版本号，单调递增',
  change_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更类型 SEED/DATA_TYPE_CREATE/DATA_TYPE_UPDATE/RULE_CREATE/RULE_UPDATE',
  target_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对象类型 POLICY/DATA_TYPE/ACCESS_RULE',
  target_key VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对象标识：数据类型编码或规则四元组',
  before_snapshot JSON NULL COMMENT '变更前对象元数据（不含任何受保护值）',
  after_snapshot JSON NOT NULL COMMENT '变更后对象元数据（不含任何受保护值）',
  reason VARCHAR(500) NOT NULL COMMENT '变更理由',
  actor_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人账号 ID；初始化数据为 SYSTEM',
  request_id VARCHAR(64) NOT NULL COMMENT '请求关联标识',
  create_time DATETIME(6) NOT NULL COMMENT '变更的 UTC 时刻',
  PRIMARY KEY (policy_change_id),
  UNIQUE KEY uk_vault_policy_version (policy_version_no),
  KEY idx_vault_policy_change_target (target_type, target_key, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vault 策略变更记录（每次变更一个版本）';

ALTER TABLE t_vault_access_event
  ADD COLUMN actor_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '人工操作的运营账号 ID；服务调用为空' AFTER caller_service,
  MODIFY COLUMN policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '生效策略的版本',
  ADD COLUMN reason_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '人工操作理由码' AFTER policy_version,
  ADD COLUMN ticket_ref VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '人工操作关联工单号' AFTER reason_code,
  ADD KEY idx_vault_access_actor (actor_id, create_time);

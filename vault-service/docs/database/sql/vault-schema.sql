CREATE TABLE t_vault_record (
  record_id BIGINT NOT NULL COMMENT 'Vault Record 服务侧 Snowflake 主键',
  token VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '不透明 Vault Token',
  data_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受保护值的数据类型代码',
  token_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Token 生成模式',
  value_fingerprint BINARY(32) NULL COMMENT 'STABLE 模式的值身份 HMAC 指纹',
  ciphertext BLOB NOT NULL COMMENT 'AES-GCM 密文及认证标签',
  encryption_nonce BINARY(12) NOT NULL COMMENT '值加密使用的 GCM Nonce',
  wrapped_dek BLOB NOT NULL COMMENT '经密钥提供方封装的数据加密密钥',
  kms_key_ref VARCHAR(512) NOT NULL COMMENT '封装数据密钥使用的非敏感密钥引用',
  create_time DATETIME(6) NOT NULL COMMENT '记录创建的 UTC 时刻',
  update_time DATETIME(6) NOT NULL COMMENT '记录最后更新的 UTC 时刻',
  PRIMARY KEY (record_id),
  UNIQUE KEY uk_vault_record_token (token),
  UNIQUE KEY uk_vault_record_stable (data_type, value_fingerprint),
  CONSTRAINT ck_vault_record_mode CHECK (token_mode IN ('REFERENCE','STABLE')),
  CONSTRAINT ck_vault_record_fingerprint CHECK ((token_mode='STABLE' AND value_fingerprint IS NOT NULL) OR (token_mode='REFERENCE' AND value_fingerprint IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vault 受保护值记录';

CREATE TABLE t_vault_protect_request (
  protect_request_id BIGINT NOT NULL COMMENT 'Protect 请求服务侧 Snowflake 主键',
  caller_service VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起 Protect 的调用服务名',
  idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方逻辑命令幂等键',
  request_hash BINARY(32) NOT NULL COMMENT 'Protect 规范请求的 HMAC 哈希',
  record_id BIGINT NOT NULL COMMENT '成功 Protect 对应的 Vault Record 主键',
  create_time DATETIME(6) NOT NULL COMMENT '映射创建的 UTC 时刻',
  update_time DATETIME(6) NOT NULL COMMENT '映射最后更新的 UTC 时刻',
  PRIMARY KEY (protect_request_id),
  UNIQUE KEY uk_vault_protect_request (caller_service, idempotency_key),
  KEY idx_vault_protect_record (record_id),
  CONSTRAINT fk_vault_protect_record FOREIGN KEY (record_id)
    REFERENCES t_vault_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vault Protect 幂等请求映射';

CREATE TABLE t_vault_access_event (
  access_event_id BIGINT NOT NULL COMMENT '访问事件服务侧 Snowflake 主键',
  request_id VARCHAR(64) NOT NULL COMMENT '本次 HTTP 尝试的请求关联标识',
  trace_id VARCHAR(64) NULL COMMENT '合法 W3C Trace Context 的 Trace ID',
  caller_service VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发起访问的调用服务名',
  action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际命中的 Vault 操作',
  data_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '已安全解析的数据类型代码',
  purpose VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '已安全解析的访问用途代码',
  record_id BIGINT NULL COMMENT '已安全解析并定位的 Vault Record 主键',
  outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '访问处理结果',
  result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定结果代码',
  policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '生效静态 Allowlist 的版本',
  create_time DATETIME(6) NOT NULL COMMENT '访问事件创建的 UTC 时刻',
  PRIMARY KEY (access_event_id),
  KEY idx_vault_access_record (record_id, create_time),
  KEY idx_vault_access_caller (caller_service, create_time),
  KEY idx_vault_access_request (request_id),
  CONSTRAINT fk_vault_access_record FOREIGN KEY (record_id)
    REFERENCES t_vault_record (record_id),
  CONSTRAINT ck_vault_access_action CHECK (action IN ('PROTECT','REVEAL')),
  CONSTRAINT ck_vault_access_outcome CHECK (outcome IN ('SUCCESS','DENIED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vault 本地不可变访问审计事件';

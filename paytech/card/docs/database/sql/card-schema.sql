-- Code-MoR target schema generated from the approved PDManer model.
-- Apply manually to an EMPTY database only; existing environments require a reviewed migration.
-- Execution status: agent-docs/CURRENT_IMPLEMENTATION.md. Verify Java compatibility before rollout.
-- Source: paytech/card/docs/database/card.pdma.json
-- mor_card (card-service, design/05): the four card tables move from mor_payment without structural change except
-- t_payment_card_instrument (+ source_submission_id / expires_at for the two-stage creation, design/05 §2.1-§2.2);
-- t_card_material_ticket is new (S3 one-time reveal ticket); t_compensation_event, t_consumed_event and
-- t_consumed_aggregate_version are the per-database infrastructure tables.
-- 2026-10-22 BIN 库（requirements/20261022/card-BIN库与卡属性）：新增 t_card_bin_library_version、t_card_bin_range、
-- t_card_bin_import_error、t_card_bin_library_activation；已有环境用该需求 db/card/ddl.sql 增量执行。
SET NAMES utf8mb4;

CREATE TABLE `t_payment_card` (
  `payment_card_id` BIGINT NOT NULL COMMENT 'Card 卡的 Snowflake 内部主键，创建后不可修改。',
  `card_fingerprint` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '以配置指纹密钥对卡号做 HMAC-SHA256 的小写十六进制；同一卡号恒定，用于按卡号定位与 Risk 评估，不能反推卡号。',
  `card_bin` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡号不少于 16 位时为前 8 位，否则为前 6 位；创建后不可修改。',
  `pan_last4` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡号后 4 位；创建后不可修改。',
  `encrypted_pan` VARBINARY(128) NOT NULL COMMENT '以本卡 DEK 做 AES-256-GCM 加密的卡号：IV(12) ‖ 密文 ‖ Tag(16)，AAD 绑定 payment_card_id。',
  `dek_vault_token` CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本卡数据加密密钥（DEK）在 Vault 数据类型 PAYMENT_CARD_DEK 下的 REFERENCE Token；DEK 明文不落库。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_id`),
  UNIQUE KEY `uk_payment_card_fingerprint` (`card_fingerprint`) COMMENT '唯一索引：card_fingerprint'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Card 拥有的卡，每个卡号一条（按卡指纹唯一），每张卡一把由 Vault 托管的 DEK；只加密保存卡号，不存 CVV、磁道、PIN 或 DEK 明文。';

CREATE TABLE `t_payment_card_credential` (
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '卡凭证的 Snowflake 内部主键，创建后不可修改。',
  `payment_card_id` BIGINT NOT NULL COMMENT '所属卡，创建后不可修改。',
  `credential_digest` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '以配置指纹密钥对卡号、有效期与规范化持卡人做 HMAC-SHA256 的小写十六进制；同一张卡下唯一。',
  `encrypted_details` VARBINARY(1024) NOT NULL COMMENT '以所属卡 DEK 做 AES-256-GCM 加密的有效期与持卡人原值：IV(12) ‖ 密文 ‖ Tag(16)，AAD 绑定 payment_card_credential_id。',
  `expiry_month` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡有效期月份（两位）；与已加密卡号分开保存，用于已存卡展示与过期判断，不含卡号。',
  `expiry_year` CHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡有效期年份（四位）；用于已存卡展示与过期判断。',
  `verified_time` DATETIME(6) NULL COMMENT '首次以该凭证支付成功的 UTC 微秒精度时间；NULL 表示未验证，只有已验证凭证可用于存卡。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡凭证创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡凭证最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_credential_id`),
  UNIQUE KEY `uk_payment_card_credential` (`payment_card_id`, `credential_digest`) COMMENT '唯一索引：payment_card_id、credential_digest'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='同一张卡下每组有效期与规范化持卡人一条；有效期与持卡人原值以所属卡的 DEK 加密保存。';

CREATE TABLE `t_payment_card_instrument` (
  `payment_card_instrument_id` BIGINT NOT NULL COMMENT '卡交易工具的 Snowflake 内部主键；其十进制字符串即 Card 工具引用 instrumentRef，Payment 支付工具 Token 的 instrument_ref 保存该值。',
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '本次使用的卡凭证，创建后不可修改。',
  `client_id` BIGINT NOT NULL COMMENT '所属 CCT Client 稳定标识，由 Payment 从已核对的 Payment Intent 传入，创建后不可修改。',
  `source_submission_id` BIGINT NOT NULL COMMENT '申请本工具的 Commerce 来源提交全局稳定标识；与 client_id 组成唯一键，Payment 重试同一提交时 Card 返回同一工具。',
  `customer_id` BIGINT NULL COMMENT '本次支付关联的 Commerce Customer 标识；访客支付为 NULL。',
  `save_requested` TINYINT(1) NOT NULL COMMENT '消费者是否同意本次支付成功后保存卡；1 表示同意，仅在有 customer_id 时为 1。',
  `payment_stored_card_id` BIGINT NULL COMMENT '用已存卡支付时指向的已存卡；手输卡为 NULL。',
  `expires_at` DATETIME(6) NOT NULL COMMENT '工具首次用卡资格的 UTC 微秒精度截止时间：取 Payment 给出的期限上限与卡有效期中较早者，等于 CVV 暂存期限；创建后不可修改。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡交易工具创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的卡交易工具最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_card_instrument_id`),
  UNIQUE KEY `uk_card_instrument_submission` (`client_id`, `source_submission_id`) COMMENT '唯一索引：client_id、source_submission_id',
  KEY `idx_card_instrument_expires` (`expires_at`, `payment_card_instrument_id`) COMMENT '访问索引：expires_at、payment_card_instrument_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='每个来源提交一条，记录本次使用的卡凭证、所属 Client 与用卡期限；主键十进制字符串即 Card 工具引用。';

CREATE TABLE `t_payment_stored_card` (
  `payment_stored_card_id` BIGINT NOT NULL COMMENT '已存卡的 Snowflake 内部主键，创建后不可修改。',
  `stored_card_token` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '对外已存卡 Token：sc_ 前缀加随机串，按 Client 生成、不可推算；不含卡号、指纹或内部 ID。',
  `client_id` BIGINT NOT NULL COMMENT '所属 CCT Client 稳定标识，创建后不可修改。',
  `customer_id` BIGINT NOT NULL COMMENT '所属 Commerce Customer 标识，创建后不可修改；Payment 不保存 consumer_reference。',
  `payment_card_id` BIGINT NOT NULL COMMENT '已存的卡（按卡号唯一的那层），创建后不可修改。',
  `active_card_id` BIGINT NULL COMMENT '有效时等于 payment_card_id，删除时置 NULL；与 client_id、customer_id 组成唯一索引，保证同一 Customer 同一张卡最多一条有效已存卡。',
  `payment_card_credential_id` BIGINT NOT NULL COMMENT '当前使用的已验证卡凭证；同卡以新有效期再次保存时更新。',
  `stored_card_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '已存卡状态。允许值：ACTIVE、DELETED。',
  `consent_time` DATETIME(6) NOT NULL COMMENT '消费者最近一次同意保存的 UTC 微秒精度时间（支付成功时记录）。',
  `source_card_instrument_id` BIGINT NOT NULL COMMENT '首次保存来源的卡交易工具，可追溯到支付意图与来源订单。',
  `deleted_time` DATETIME(6) NULL COMMENT '删除或按 Customer 失效的 UTC 微秒精度时间；有效时为 NULL。',
  `version` BIGINT NOT NULL COMMENT '可变业务行并发更新使用的单调乐观锁版本。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的已存卡创建 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的已存卡最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`payment_stored_card_id`),
  UNIQUE KEY `uk_payment_stored_card_token` (`stored_card_token`) COMMENT '唯一索引：stored_card_token',
  UNIQUE KEY `uk_payment_stored_card_active` (`client_id`, `customer_id`, `active_card_id`) COMMENT '唯一索引：client_id、customer_id、active_card_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Commerce Customer 名下的已存卡：指向卡与当前已验证凭证，带消费者同意时间；删除只置状态。';

CREATE TABLE `t_card_material_ticket` (
  `card_material_ticket_id` BIGINT NOT NULL COMMENT '读卡票据的 Snowflake 内部主键。',
  `ticket_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '票据原值（随机 256 位）的 SHA-256 小写十六进制；票据原值只返回给签发方，不落库、不记录日志。',
  `instrument_ref` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '票据可读取的卡交易工具引用（t_payment_card_instrument 主键十进制字符串），创建后不可修改。',
  `attempt_id` BIGINT NOT NULL COMMENT '签发方 Payment 声明的支付尝试 ID，仅用于审计与核对，Card 不读取 Payment 数据。',
  `action_id` BIGINT NOT NULL COMMENT '签发方 Payment 声明的当前资金动作 ID，仅用于审计与核对。',
  `purpose` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '票据用途，读卡入口只接受与之相同的用途。允许值：CHANNEL_EXECUTION。',
  `expires_at` DATETIME(6) NOT NULL COMMENT '票据 UTC 微秒精度截止时间，签发后不超过 90 秒且不晚于工具期限。',
  `used_at` DATETIME(6) NULL COMMENT '票据被读卡入口使用的 UTC 微秒精度时间；NULL 表示未使用，非空后不可再次使用。',
  `caller` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '签发请求的调用方服务名（D26），只接受 payment-service。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的票据签发 UTC 微秒精度时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的票据最近有效更新 UTC 微秒精度时间。',
  PRIMARY KEY (`card_material_ticket_id`),
  UNIQUE KEY `uk_card_ticket_hash` (`ticket_hash`) COMMENT '唯一索引：ticket_hash',
  KEY `idx_card_ticket_instrument` (`instrument_ref`, `card_material_ticket_id`) COMMENT '访问索引：instrument_ref、card_material_ticket_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Card 签发给 Payment、由 Platform 凭其读卡一次的一次性读卡票据；只存票据摘要，不存卡数据。';

CREATE TABLE `t_card_bin_library_version` (
  `version_id` BIGINT NOT NULL COMMENT 'BIN 库版本的 Snowflake 内部主键，创建后不可修改。',
  `version_no` INT NOT NULL COMMENT '对运营展示的版本号，从 1 起按创建顺序递增，创建后不可修改。',
  `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本状态。允许值：VALIDATING、VALID、INVALID、ACTIVE、RETIRED。VALIDATING 为等待扫描、校验与写入；INVALID 为文件或数据校验失败，不能激活；ACTIVE 为当前生效版本；RETIRED 为曾经生效、已被其他版本替换，可再次激活（回切）。',
  `active_slot` TINYINT NULL COMMENT '当前生效占位：status=ACTIVE 时为 1，其余状态为 NULL；与唯一索引一起保证同一时刻只有一个 ACTIVE 版本。',
  `file_address` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '导入文件在 Platform 文件服务的稳定地址 /files/v1/{fileId}；初始化种子版本为 NULL。',
  `file_content_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '创建版本时从 Platform 读取并冻结的文件 SHA-256 小写十六进制；导入时逐字节核对，不一致判 INVALID。种子版本为 NULL。',
  `file_name` VARCHAR(255) NULL COMMENT '导入文件的展示名，取自 Platform 文件元数据；仅用于运营页面展示。种子版本为 NULL。',
  `total_rows` INT NULL COMMENT '文件数据行数（不含表头）；校验完成前为 NULL。',
  `processed_rows` INT NOT NULL DEFAULT 0 COMMENT '已写入号段的数据行数，用于展示导入进度；VALID、ACTIVE、RETIRED 时等于 total_rows。',
  `error_rows` INT NOT NULL DEFAULT 0 COMMENT '校验失败的数据行数（可能大于已记录的错误明细条数）；大于 0 时状态为 INVALID。',
  `failure_reason` VARCHAR(256) NULL COMMENT '文件级失败原因（扫描未通过、表头不符、哈希不一致、超过行数上限等）；不含卡号；其他状态为 NULL。',
  `created_by` BIGINT NULL COMMENT '发起导入的 Operations 账号 ID；初始化种子版本为 NULL。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '乐观并发版本，新建为 1，每次状态或进度更新成功加 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的版本创建 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的版本最近更新 UTC 微秒时间。',
  PRIMARY KEY (`version_id`),
  UNIQUE KEY `uk_bin_library_version_no` (`version_no`) COMMENT '唯一索引：version_no',
  UNIQUE KEY `uk_bin_library_active` (`active_slot`) COMMENT '唯一索引：active_slot；保证同一时刻至多一个 ACTIVE 版本',
  UNIQUE KEY `uk_bin_library_file` (`file_address`) COMMENT '唯一索引：file_address；同一文件只建一个版本，重复提交返回原版本',
  KEY `idx_bin_library_status` (`status`, `create_time`) COMMENT '支持访问路径：按状态筛选的版本列表'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Card 拥有的 BIN 库版本：一次导入的完整号段数据集；同一时刻只有一个 ACTIVE 版本，激活与回切原子切换。';

CREATE TABLE `t_card_bin_range` (
  `range_id` BIGINT NOT NULL COMMENT 'BIN 号段的 Snowflake 内部主键。',
  `version_id` BIGINT NOT NULL COMMENT '所属 BIN 库版本，创建后不可修改。',
  `bin_prefix` VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '号段前缀，6 至 8 位数字；按最长前缀匹配卡 BIN。',
  `prefix_length` TINYINT NOT NULL COMMENT '号段前缀位数，6、7 或 8，等于 bin_prefix 长度。',
  `card_brand` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '卡组织（主品牌）。允许值：VISA、MASTERCARD、AMEX、UNIONPAY、MADA、DISCOVER、JCB、DINERS。',
  `funding_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '借贷记类型；数据文件未提供时为 NULL。允许值：CREDIT、DEBIT、PREPAID、CHARGE。',
  `card_level` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '卡等级，格式 [A-Z0-9_]{1,32}；未提供时为 NULL。取值来源：BIN 数据文件；非固定枚举。',
  `card_product` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '卡产品类别；未提供时为 NULL。允许值：CONSUMER、COMMERCIAL。',
  `issuer_country` CHAR(2) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发卡国 ISO 3166-1 alpha-2。取值来源：ISO 3166-1 alpha-2；非固定枚举。',
  `issuer_region` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发卡区域，导入时按 Platform 国家区域映射由 issuer_country 推导并冻结；映射之后变化不影响本行。取值来源：Platform t_ref_country.region_code；非固定枚举。',
  `issuer_name` VARCHAR(128) NULL COMMENT '发卡行名称，仅供运营查询展示；未提供时为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的号段创建 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的号段最近更新 UTC 微秒时间；号段创建后不修改，与 create_time 相同。',
  PRIMARY KEY (`range_id`),
  UNIQUE KEY `uk_card_bin_range` (`version_id`, `bin_prefix`) COMMENT '唯一索引：version_id、bin_prefix；同一版本内同一前缀只有一行，也是按前缀查询的访问路径'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='BIN 库版本中的号段与标准化卡属性；只按 BIN 前缀保存，不含完整卡号。';

CREATE TABLE `t_card_bin_import_error` (
  `error_id` BIGINT NOT NULL COMMENT '导入错误明细的 Snowflake 内部主键。',
  `version_id` BIGINT NOT NULL COMMENT '所属 BIN 库版本。',
  `line_no` INT NOT NULL COMMENT '数据文件中的物理行号，表头为第 1 行。',
  `column_name` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '出错列名；整行错误（列数不符、重复前缀等）为 NULL。',
  `error_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '错误代码。允许值：COLUMN_COUNT_MISMATCH、REQUIRED_MISSING、INVALID_FORMAT、INVALID_VALUE、DUPLICATE_PREFIX、COUNTRY_REGION_MISSING、ENCODING_INVALID。',
  `error_message` VARCHAR(256) NOT NULL COMMENT '面向运营的中文错误说明，不含卡号。',
  `value_excerpt` VARCHAR(64) NULL COMMENT '出错值摘录：超过 8 位的连续数字不回显（存 NULL），其他值截断到 64 字符。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的错误明细创建 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的错误明细最近更新 UTC 微秒时间；创建后不修改。',
  PRIMARY KEY (`error_id`),
  KEY `idx_bin_import_error_version` (`version_id`, `line_no`) COMMENT '支持访问路径：按版本分页列出错误，按行号排序'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='BIN 库导入校验失败的行级错误明细，每个版本至多记录前 200 条。';

CREATE TABLE `t_card_bin_library_activation` (
  `activation_id` BIGINT NOT NULL COMMENT '激活记录的 Snowflake 内部主键。',
  `version_id` BIGINT NOT NULL COMMENT '本次被激活的 BIN 库版本。',
  `previous_version_id` BIGINT NULL COMMENT '本次激活前的 ACTIVE 版本，激活后变为 RETIRED；此前没有生效版本时为 NULL。',
  `activation_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '激活类型。允许值：ACTIVATE、ROLLBACK。ACTIVATE 为激活 VALID 版本；ROLLBACK 为重新激活 RETIRED 版本（回切）。',
  `operator_account_id` BIGINT NULL COMMENT '操作的 Operations 账号 ID；初始化种子激活为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '应用写入的激活 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '应用写入的记录最近更新 UTC 微秒时间；创建后不修改。',
  PRIMARY KEY (`activation_id`),
  KEY `idx_bin_activation_time` (`create_time`) COMMENT '支持访问路径：激活历史按时间倒序分页'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='BIN 库激活与回切的追加记录，满足操作人与时间留痕；与版本状态切换在同一本地事务写入。';

CREATE TABLE `t_compensation_event` (
  `id` BIGINT NOT NULL COMMENT '本地补偿事件记录的内部唯一标识。',
  `event_key` VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT 'Handler 读取本服务权威事实使用的稳定业务键，最长 128 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。',
  `event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '全应用唯一注册的 Handler 类型，最长 64 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。取值来源：Payment Event/Handler Catalog；非固定枚举。',
  `payload_json` VARCHAR(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL COMMENT '可选最小 JSON Object，通常为空；最多 4 KiB UTF-8 字节由框架校验，不得保存可从本库恢复的上下文、卡数据、Vault Token、凭据或临时交互指令。',
  `execute_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'INIT' COMMENT '本地补偿任务执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。成功后删除记录，FAILED 不改变业务资金状态。',
  `execute_count` INT NOT NULL DEFAULT 0 COMMENT '已开始的 Handler 尝试次数，非负，Claim 时增加；达到上限后不得继续产生副作用。',
  `next_execute_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。',
  `lock_owner` VARCHAR(64) NULL COMMENT '当前持有本地补偿事件数据库 Claim Lease 的服务实例标识；未被认领时为空。',
  `lock_until` DATETIME(6) NULL COMMENT '本地补偿事件 Claim Lease 到期并可被其他实例接管的时刻；无活动 Lease 时为空。',
  `last_error_code` VARCHAR(64) NULL COMMENT '补偿事件最近一次执行失败的稳定错误分类代码；尚无错误时为空。取值来源：Payment Error Catalog；非固定枚举。',
  `last_error_summary` VARCHAR(1000) NULL COMMENT '最近一次执行失败的脱敏摘要，最长 1000 字符；无错误时为空，不包含卡数据、凭据或其他敏感原值。',
  `lock_version` BIGINT NOT NULL DEFAULT 1 COMMENT 'Claim/Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。',
  `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本地补偿记录创建的 UTC 微秒时间。',
  `update_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本地补偿记录最近更新的 UTC 微秒时间，由写入口维护。',
  `sub_event_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL COMMENT '同一 Handler 下的受控业务子类型，最长 64 个 Java 字符；与 event_key、event_type 共同组成稳定唯一身份。取值来源：Payment Compensation Sub Event Catalog；非固定枚举。',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_event_identity` (`event_key`, `event_type`, `sub_event_type`) COMMENT '唯一索引：event_key、event_type、sub_event_type',
  KEY `idx_comp_event_ready` (`execute_status`, `next_execute_at`, `id`) COMMENT '访问索引：execute_status、next_execute_at、id',
  KEY `idx_comp_event_lease` (`execute_status`, `lock_until`, `id`) COMMENT '访问索引：execute_status、lock_until、id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='业务变化同事务登记的本地可靠执行记录；成功后删除，FAILED 保留并可受控重开。';

CREATE TABLE t_consumed_event (
    event_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件 ID（信封 eventId，默认等于 eventKey）',
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件类型（PascalCase，登记于事件目录）',
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    aggregate_version BIGINT NOT NULL COMMENT '本事件携带的聚合版本',
    consumed_at DATETIME(6) NOT NULL COMMENT '消费事务提交时间（UTC）',
    PRIMARY KEY (event_id),
    KEY idx_consumed_event_aggregate (aggregate_type, aggregate_id, aggregate_version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费领域事件：按事件 ID 去重';

CREATE TABLE t_consumed_aggregate_version (
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合类型（大写 Code）',
    aggregate_id VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '聚合标识（分区键）',
    last_version BIGINT NOT NULL COMMENT '本服务已处理的最大聚合版本',
    update_time DATETIME(6) NOT NULL COMMENT '最近推进时间（UTC）',
    PRIMARY KEY (aggregate_type, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_bin COMMENT = '已消费聚合版本：判定乱序、重放与缺口';

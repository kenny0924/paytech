-- 未执行草案（2026-10-22 阶段 3 评审稿）：card：BIN 库与卡属性，目标库 mor_card（card-service）。
-- 性质：需求级增量 DDL，只新增 4 张表，不改既有表；执行前须先更新 PDManer card.pdma.json（生成器 additions）并同步
--       paytech/card/docs/database/sql/card-schema.sql 与测试库 paytech-card-test/src/test/resources/init/ddl.sql。
-- 执行顺序：本文件 → db/card/dml.sql（种子版本）。可重跑：否（CREATE TABLE）。
-- 回退：DROP TABLE t_card_bin_library_activation, t_card_bin_import_error, t_card_bin_range, t_card_bin_library_version;
--       回退前须先回退依赖这些表的 Card 镜像。
SET NAMES utf8mb4;

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
  `issuer_region` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发卡区域，导入时按 Platform 国家区域映射由 issuer_country 推导并冻结；映射之后变化不影响本行。取值来源：Platform t_country_region.region_code；非固定枚举。',
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

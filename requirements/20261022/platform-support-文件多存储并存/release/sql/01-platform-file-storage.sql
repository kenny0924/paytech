-- 发布副本：逐字取自 paytech/platform-support/docs/database/sql/changes/2026-10-22-file-storage-management.sql（mor_platform，不带 USE，
--   库名由 scripts/apply_sql.sh 传入）。阶段 7 第 6 步执行，紧接着替换 platform-support-service（release.md 第 2、5 节）。
-- 兼容性：第 1、2、3、5 段对旧 Platform 无影响；第 4 段把 source_system / reason_code 改为 NOT NULL 后，旧 Platform 写 t_file_upload
--   与 t_file_object 的 INSERT 不带这两列，会被严格模式拒绝（入驻开始上传 / 完成上传失败），直到新 Platform 起来。
-- 回滚：
--   · 只回滚镜像（保留新表与新列）：执行 90-rollback-relax-new-columns.sql，把四个新列放宽为可空，旧 Platform 即可写入；
--   · 彻底回退结构：先回滚镜像，再按下文末尾“回退”段逆序执行（会丢弃新列与 3 张存储表）。
-- =============================================================================
-- Platform Support 文件多存储并存（第一段）增量 DDL / DML
-- 需求：requirements/20261022/platform-support-文件多存储并存/requirement.md（R1—R5、B1—B6）
-- 方案：requirements/20261022/platform-support-文件多存储并存/technique.md 第 3 节
--
-- 状态：未执行。人工评审后按下列顺序在目标库 mor_platform 执行；应用与测试不自动执行。
-- 执行账号：需要 CREATE / ALTER / INDEX 权限（本机 platform_app 持有 mor_platform ALL PRIVILEGES）。
--
-- 执行顺序：
--   1. 新建 t_file_storage_instance、t_file_storage_bucket、t_file_storage_route；
--   2. t_file_upload、t_file_object 加列（可空）；
--   3. 回填现有记录（现有上传与文件全部来自 Client 入驻）；
--   4. 新列改为 NOT NULL；t_file_object 建 idx_file_source；
--   5. t_file_upload 的 tenant_id、uploader_id、owner_system、owner_type、owner_ref_id 放宽为可空
--      （内部文件接口的上传没有 Owner，纯系统上传没有上传人与租户）；
--   6. 执行文末核验查询。
--
-- 不建 CHECK / FOREIGN KEY：credential_mode 与 Token 的组合、MINIO 只允许 STATIC、路由指向 ACTIVE 桶等规则由应用代码保证。
-- 不登记任何存储实例、桶或路由：登记经运营「存储管理」接口完成（密钥只进 Vault）。
-- =============================================================================

-- 1. 新表 -----------------------------------------------------------------------

CREATE TABLE `t_file_storage_instance` (
  `instance_id` BIGINT NOT NULL COMMENT '存储实例主键。',
  `instance_code` VARCHAR(64) NOT NULL COMMENT '存储实例代码，即文件与上传记录上的 storage_provider；格式 [A-Z][A-Z0-9_]{0,63}，创建后不可修改，被记录引用后不可删除。',
  `storage_type` VARCHAR(16) NOT NULL COMMENT '存储类型，决定使用的 SDK。允许值：MINIO、S3。',
  `endpoint` VARCHAR(255) NULL COMMENT 'HTTPS 根地址，同时是浏览器直传与下载授权的地址；S3 为空时使用区域默认地址，MINIO 必填。',
  `region` VARCHAR(64) NOT NULL COMMENT '签名区域，例如 us-east-1、me-central-1。',
  `path_style_access` TINYINT NOT NULL COMMENT '是否使用路径风格访问：1 表示路径风格（兼容 S3 的存储通常需要），0 表示虚拟主机风格。',
  `trusted_certificate_pem` TEXT NULL COMMENT '私有 CA 或自签名证书 PEM（公开内容）；为空时使用 JDK 默认信任库，非空时只信任该证书。',
  `credential_mode` VARCHAR(16) NOT NULL COMMENT '凭据方式；MINIO 只允许 STATIC。允许值：STATIC、IAM_ROLE。',
  `credential_vault_token` VARCHAR(255) NULL COMMENT '静态访问密钥的 Vault Token（数据类型 FILE_STORAGE_CREDENTIAL_V1，值为 {"accessKeyId","secretAccessKey"}）；STATIC 必填，IAM_ROLE 为空；每次存储操作前以用途 FILE_STORAGE_ACCESS 读取，不缓存原值。',
  `credential_update_time` DATETIME(6) NULL COMMENT '最近一次录入或更换密钥（含切换凭据方式）的 UTC 微秒时间。',
  `connect_timeout_ms` INT NOT NULL COMMENT '连接超时毫秒数，1000—10000，默认 2000。',
  `read_timeout_ms` INT NOT NULL COMMENT '读写超时毫秒数，1000—60000，默认 10000。',
  `status` VARCHAR(16) NOT NULL COMMENT '实例状态；只有 ACTIVE 实例可读写，DISABLED 读写都返回不可用。允许值：DRAFT、ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`instance_id`),
  UNIQUE KEY `uk_file_storage_instance_code` (`instance_code`) COMMENT '保证业务唯一性：instance_code'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存运营维护的对象存储实例：类型、地址、区域、私有证书、凭据方式与密钥 Vault Token；实例代码即文件记录上的存储提供方';

CREATE TABLE `t_file_storage_bucket` (
  `bucket_id` BIGINT NOT NULL COMMENT '存储桶主键。',
  `instance_id` BIGINT NOT NULL COMMENT '所属存储实例 ID。',
  `bucket_name` VARCHAR(255) NOT NULL COMMENT '存储桶名称，即文件与上传记录上的 storage_container_ref；创建后不可修改，被记录引用后不可删除。',
  `status` VARCHAR(16) NOT NULL COMMENT '存储桶状态；只有 ACTIVE 接受新上传，ACTIVE 与 READ_ONLY 可读，DISABLED 读写都返回不可用。允许值：DRAFT、ACTIVE、READ_ONLY、DISABLED。',
  `last_check_time` DATETIME(6) NULL COMMENT '最近一次启用检查的 UTC 微秒时间；从未检查为空。',
  `last_check_result` VARCHAR(64) NULL COMMENT '最近一次启用检查结果；从未检查为空。允许值：OK、NOT_FOUND、VERSIONING_DISABLED、ENCRYPTION_DISABLED、ACCESS_DENIED、UNAVAILABLE。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`bucket_id`),
  UNIQUE KEY `uk_file_storage_bucket` (`instance_id`, `bucket_name`) COMMENT '保证业务唯一性：instance_id、bucket_name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存存储实例下登记的存储桶、读写状态与最近一次启用检查结果；Platform 只登记和检查，不建桶、不改桶策略';

CREATE TABLE `t_file_storage_route` (
  `route_id` BIGINT NOT NULL COMMENT '写入路由主键。',
  `source_system` VARCHAR(64) NOT NULL COMMENT '来源系统，即内部调用 Token 的服务名（[a-z][a-z0-9-]{0,63}）；* 表示默认路由，没有精确路由的来源使用默认路由。取值来源：内部调用身份的服务名；非固定枚举。',
  `bucket_id` BIGINT NOT NULL COMMENT '新上传写入的存储桶 ID；保存时必须为 ACTIVE 桶且所属实例 ACTIVE。',
  `scan_policy_code` VARCHAR(64) NOT NULL COMMENT '新上传冻结的扫描策略代码；保存时必须存在 ACTIVE 修订，上传开始时冻结当前 ACTIVE 修订。取值来源：t_file_scan_policy.policy_code；非固定枚举。',
  `max_upload_seconds` INT NOT NULL COMMENT '新上传的授权有效秒数，60—900。',
  `version` INT NOT NULL COMMENT '乐观并发版本，新建为 1，每次成功修改递增 1。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`route_id`),
  UNIQUE KEY `uk_file_storage_route_source` (`source_system`) COMMENT '保证业务唯一性：source_system',
  KEY `idx_file_storage_route_bucket` (`bucket_id`) COMMENT '支持访问路径：bucket_id（停用实例或桶前检查引用路由）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存按来源系统选择新上传存储桶、扫描策略与上传期限的写入路由；改路由只影响之后的新上传';

-- 2. 加列（可空）----------------------------------------------------------------

ALTER TABLE `t_file_upload`
  ADD COLUMN `source_system` VARCHAR(64) NULL COMMENT '上传来源系统，即开始上传时内部调用身份的服务名；Platform 自行发起的上传为 platform-support。创建后不变。取值来源：内部调用身份的服务名；非固定枚举。' AFTER `uploader_id`,
  ADD COLUMN `reason_code` VARCHAR(64) NULL COMMENT '来源系统声明的上传原因代码，格式 [A-Z][A-Z0-9_]{0,63}，用于反查文件为什么上传；创建后不变。取值来源：来源系统自定义；非固定枚举。' AFTER `source_system`;

ALTER TABLE `t_file_object`
  ADD COLUMN `source_system` VARCHAR(64) NULL COMMENT '文件来源系统，完成上传时从上传记录复制，用于反查文件来源。取值来源：内部调用身份的服务名；非固定枚举。' AFTER `upload_id`,
  ADD COLUMN `reason_code` VARCHAR(64) NULL COMMENT '来源系统声明的上传原因代码，完成上传时从上传记录复制。取值来源：来源系统自定义；非固定枚举。' AFTER `source_system`;

-- 3. 回填 -----------------------------------------------------------------------

UPDATE `t_file_upload` SET `source_system` = 'platform-support', `reason_code` = 'CLIENT_ONBOARDING_MATERIAL'
WHERE `source_system` IS NULL;

UPDATE `t_file_object` SET `source_system` = 'platform-support', `reason_code` = 'CLIENT_ONBOARDING_MATERIAL'
WHERE `source_system` IS NULL;

-- 4. 改为 NOT NULL 并建索引 -----------------------------------------------------

ALTER TABLE `t_file_upload`
  MODIFY COLUMN `source_system` VARCHAR(64) NOT NULL COMMENT '上传来源系统，即开始上传时内部调用身份的服务名；Platform 自行发起的上传为 platform-support。创建后不变。取值来源：内部调用身份的服务名；非固定枚举。',
  MODIFY COLUMN `reason_code` VARCHAR(64) NOT NULL COMMENT '来源系统声明的上传原因代码，格式 [A-Z][A-Z0-9_]{0,63}，用于反查文件为什么上传；创建后不变。取值来源：来源系统自定义；非固定枚举。';

ALTER TABLE `t_file_object`
  MODIFY COLUMN `source_system` VARCHAR(64) NOT NULL COMMENT '文件来源系统，完成上传时从上传记录复制，用于反查文件来源。取值来源：内部调用身份的服务名；非固定枚举。',
  MODIFY COLUMN `reason_code` VARCHAR(64) NOT NULL COMMENT '来源系统声明的上传原因代码，完成上传时从上传记录复制。取值来源：来源系统自定义；非固定枚举。',
  ADD KEY `idx_file_source` (`source_system`, `reason_code`, `create_time`) COMMENT '支持访问路径：source_system、reason_code、create_time（按来源与原因反查文件）';

-- 5. 放宽 t_file_upload 的租户、上传人与 Owner 列 --------------------------------

ALTER TABLE `t_file_upload`
  MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识；Client 入驻上传为解析出的入驻租户，内部文件接口的上传为空。',
  MODIFY COLUMN `uploader_id` BIGINT NULL COMMENT '上传人账号 ID；调用方有人工操作人时传入并原样记录，纯系统上传为空，Platform 不核对其真实性。',
  MODIFY COLUMN `owner_system` VARCHAR(64) NULL COMMENT '上传目标所属业务系统代码，来自 Platform 受控代码目录；Client 入驻由 CCT 授权结果固定，浏览器不得提交；内部文件接口的上传为空（第二段删除）。',
  MODIFY COLUMN `owner_type` VARCHAR(64) NULL COMMENT '上传目标的受控资源类型；Client 入驻固定为 CLIENT_APPLICATION_SUBJECT，不允许上传后变更目标类型；内部文件接口的上传为空（第二段删除）。取值来源：Platform 受控文件 Owner 类型目录；非固定枚举。',
  MODIFY COLUMN `owner_ref_id` VARCHAR(128) NULL COMMENT '来源 Owner 定义的不可变目标坐标；Client 入驻为 CCT 的 applicationId:subjectPartyId，两段均为规范正整数 ID，不复制 Client 名称或其他可变事实；内部文件接口的上传为空（第二段删除）。';

-- 6. 核验查询 -------------------------------------------------------------------
-- 期望：3 张新表存在；新列 NOT NULL；无空值；索引存在。
-- SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()
--   AND table_name IN ('t_file_storage_instance','t_file_storage_bucket','t_file_storage_route');
-- SELECT table_name, column_name, is_nullable FROM information_schema.columns WHERE table_schema = DATABASE()
--   AND table_name IN ('t_file_upload','t_file_object')
--   AND column_name IN ('source_system','reason_code','tenant_id','uploader_id','owner_system','owner_type','owner_ref_id')
--   ORDER BY table_name, column_name;
-- SELECT COUNT(*) FROM t_file_upload WHERE source_system IS NULL OR reason_code IS NULL;   -- 0
-- SELECT COUNT(*) FROM t_file_object WHERE source_system IS NULL OR reason_code IS NULL;   -- 0
-- SHOW INDEX FROM t_file_object WHERE Key_name = 'idx_file_source';

-- =============================================================================
-- 回退（逆序；先停用新版本应用。旧镜像不读新表与新列）
-- 注意：放宽为可空的列若已出现 NULL（内部文件接口的上传），恢复 NOT NULL 前需先处理这些行。
-- =============================================================================
-- ALTER TABLE `t_file_upload`
--   MODIFY COLUMN `tenant_id` BIGINT NOT NULL COMMENT '访问租户的稳定业务标识。',
--   MODIFY COLUMN `uploader_id` BIGINT NOT NULL COMMENT '上传用户的稳定业务标识。',
--   MODIFY COLUMN `owner_system` VARCHAR(64) NOT NULL COMMENT '上传目标所属业务系统代码，来自 Platform 受控代码目录；Client 入驻由 CCT 授权结果固定，浏览器不得提交。',
--   MODIFY COLUMN `owner_type` VARCHAR(64) NOT NULL COMMENT '上传目标的受控资源类型；Client 入驻固定为 CLIENT_APPLICATION_SUBJECT，不允许上传后变更目标类型。取值来源：Platform 受控文件 Owner 类型目录；非固定枚举。',
--   MODIFY COLUMN `owner_ref_id` VARCHAR(128) NOT NULL COMMENT '来源 Owner 定义的不可变目标坐标；Client 入驻为 CCT 的 applicationId:subjectPartyId，两段均为规范正整数 ID，不复制 Client 名称或其他可变事实。';
-- ALTER TABLE `t_file_object` DROP KEY `idx_file_source`, DROP COLUMN `reason_code`, DROP COLUMN `source_system`;
-- ALTER TABLE `t_file_upload` DROP COLUMN `reason_code`, DROP COLUMN `source_system`;
-- DROP TABLE `t_file_storage_route`;
-- DROP TABLE `t_file_storage_bucket`;
-- DROP TABLE `t_file_storage_instance`;

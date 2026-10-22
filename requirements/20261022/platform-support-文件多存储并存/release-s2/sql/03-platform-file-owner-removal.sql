-- 发布副本：逐字取自 paytech/platform-support/docs/database/sql/changes/2026-10-22-file-owner-removal.sql（mor_platform，不带 USE）。
-- 阶段 7 最后一步，另行授权：新 Platform、新 CCT、两个新 BFF 稳定且验收通过后执行；执行前用 scripts/apply_sql.sh 的
--   platform-owner-backup 步骤再备份 t_file_upload 与 t_file_owner_ref。执行后不能再回滚到第二段之前的 Platform 镜像。
-- =============================================================================
-- Platform Support 文件多存储并存（第二段）增量 DDL：删除文件业务归属数据
-- 需求：requirements/20261022/platform-support-文件多存储并存/segment2.md 第 4.1、5、8 节
--
-- 状态：未执行。人工评审后在目标库 mor_platform 执行；应用与测试不自动执行。
-- 执行账号：需要 ALTER / DROP 权限（本机 platform_app 持有 mor_platform ALL PRIVILEGES）。
--
-- 前置条件（segment2.md 第 8 节第 3 步）：
--   - Platform 已换成不读写 owner_system / owner_type / owner_ref_id 与 t_file_owner_ref 的镜像（第二段删除旧入口的镜像）；
--   - CCT、client-console-bff、operations-console-bff 已切到内部文件接口与文件地址，新链路验收通过。
--   新代码不读写这些列和表，删除前后都能运行；旧镜像依赖它们，执行本文件后不能再回滚到第一段及更早的 Platform 镜像。
--
-- 执行前备份（在 shell 执行，备份文件留存到新链路稳定后再删）：
--   mysqldump --single-transaction --no-tablespaces -h <host> -P <port> -u <user> -p mor_platform \
--     t_file_owner_ref t_file_upload > mor_platform-file-owner-backup-$(date +%Y%m%d%H%M%S).sql
--
-- 执行顺序：
--   1. 执行前核验（记录行数，与备份文件比对）；
--   2. 删除 t_file_upload 的 owner_system、owner_type、owner_ref_id，同时更新表注释与 tenant_id 注释；
--   3. 删除 t_file_owner_ref；
--   4. 执行后核验。
--
-- 回退（仅在需要回滚到依赖这些列和表的旧镜像时执行；数据从上面的备份文件恢复）：
--   ALTER TABLE `t_file_upload`
--     MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识；Client 入驻上传为解析出的入驻租户，内部文件接口的上传为空。',
--     ADD COLUMN `owner_system` VARCHAR(64) NULL COMMENT '上传目标所属业务系统代码，来自 Platform 受控代码目录；Client 入驻由 CCT 授权结果固定，浏览器不得提交；内部文件接口的上传为空（第二段删除）。' AFTER `expected_mime`,
--     ADD COLUMN `owner_type` VARCHAR(64) NULL COMMENT '上传目标的受控资源类型；Client 入驻固定为 CLIENT_APPLICATION_SUBJECT，不允许上传后变更目标类型；内部文件接口的上传为空（第二段删除）。取值来源：Platform 受控文件 Owner 类型目录；非固定枚举。' AFTER `owner_system`,
--     ADD COLUMN `owner_ref_id` VARCHAR(128) NULL COMMENT '来源 Owner 定义的不可变目标坐标；Client 入驻为 CCT 的 applicationId:subjectPartyId，两段均为规范正整数 ID，不复制 Client 名称或其他可变事实；内部文件接口的上传为空（第二段删除）。' AFTER `owner_type`,
--     COMMENT = '保存受控上传的准确 Owner 目标、租户与上传人、展示名、原存储目标和冻结扫描策略；上传授权由存储适配器签发，不持久化临时 URL、签名或额外上传秘密';
--
--   CREATE TABLE `t_file_owner_ref` (
--     `owner_ref_row_id` BIGINT NOT NULL COMMENT '文件所有者引用的稳定业务标识。',
--     `file_id` BIGINT NOT NULL COMMENT '文件的稳定业务标识。',
--     `owner_system` VARCHAR(64) NOT NULL COMMENT '所有者系统的稳定业务代码。取值来源：Code-MoR 系统代码目录；非固定枚举。',
--     `owner_type` VARCHAR(64) NOT NULL COMMENT '所有者资源类型的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
--     `owner_ref_id` VARCHAR(128) NOT NULL COMMENT '所有者资源标识的业务值。',
--     `purpose_code` VARCHAR(64) NOT NULL COMMENT '文件用途代码的稳定业务代码。取值来源：Platform Support 受控代码目录；非固定枚举。',
--     `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
--     PRIMARY KEY (`owner_ref_row_id`),
--     UNIQUE KEY `uk_file_owner` (`file_id`, `owner_system`, `owner_type`, `owner_ref_id`, `purpose_code`) COMMENT '保证业务唯一性：file_id、owner_system、owner_type、owner_ref_id、purpose_code'
--   ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保存文件与来源系统业务资源之间的最小安全引用';
--
--   恢复数据：从备份文件导入 t_file_owner_ref 的全部行；t_file_upload 的三列按 upload_id 从备份回填
--   （可先把备份导入临时库，再执行 UPDATE t_file_upload u JOIN <临时库>.t_file_upload b ON b.upload_id=u.upload_id
--    SET u.owner_system=b.owner_system, u.owner_type=b.owner_type, u.owner_ref_id=b.owner_ref_id）。
-- =============================================================================

-- 1. 执行前核验 -----------------------------------------------------------------

SELECT COUNT(*) AS owner_ref_rows FROM t_file_owner_ref;
SELECT COUNT(*) AS uploads_with_owner FROM t_file_upload WHERE owner_system IS NOT NULL;

-- 2. 删除 t_file_upload 的业务归属列 ------------------------------------------------

ALTER TABLE `t_file_upload`
  MODIFY COLUMN `tenant_id` BIGINT NULL COMMENT '访问租户的稳定业务标识；内部文件接口的上传为空，历史 Client 入驻上传保留当时解析出的入驻租户。',
  DROP COLUMN `owner_system`,
  DROP COLUMN `owner_type`,
  DROP COLUMN `owner_ref_id`,
  COMMENT = '保存受控上传的来源系统与原因、租户与上传人、展示名、原存储目标和冻结扫描策略；不记录业务归属，上传授权由存储适配器签发，不持久化临时 URL、签名或额外上传秘密';

-- 3. 删除 t_file_owner_ref --------------------------------------------------------

DROP TABLE `t_file_owner_ref`;

-- 4. 执行后核验：两条查询均应返回 0 ---------------------------------------------------

SELECT COUNT(*) AS remaining_owner_columns FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_file_upload'
  AND COLUMN_NAME IN ('owner_system', 'owner_type', 'owner_ref_id');
SELECT COUNT(*) AS remaining_owner_tables FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_file_owner_ref';

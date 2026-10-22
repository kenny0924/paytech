-- channel-connector 第二段：t_payment_channel 与 t_integration 合并为 t_channel、7 张协议 / 沙箱表改名、新增 t_channel_call_grant
-- （需求 requirements/20261020/channel-connector-统一对外渠道出口，技术方案第 5.1、5.2 节；任务卡 tasks/P2.md 第 1 节）。
--
-- 执行前提（缺一不可）：
--   1. 目标库 mor_channel_connector，root 手工执行：mysql -uroot mor_channel_connector < phase2-20-migrate.sql（不要加 --force）。
--   2. 已执行 phase2-10-precheck.sql，第 1–5 段全部为 0 行，并已人工核对第 6 段对应关系表。
--   3. 已按 phase2-rollback.md 用 mysqldump 备份整库并确认备份可读；DDL 隐式提交，失败时只能用备份恢复。
--   4. 已停止 channel-connector 写入（停实例或冻结配置写入与沙箱运行），部署第二段镜像前执行。
-- 结构结果与 paytech/channel-connector/docs/database/sql/channel-connector-schema.sql 一致（28 张表）；第 3 段 ALTER 的列定义与注释由该
-- Schema 逐字生成。另留两张迁移辅助表 tmp_phase2_channel_map、tmp_phase2_row_count 供 phase2-30-verify.sql 核对，核对通过后按 verify 文件末尾
-- 的语句删除。
-- 数据规则（技术方案第 5.2 节）：
--   - 编码统一大写后比较：匹配上的合为一行，保留 t_payment_channel.channel_id；说明、出站地址、版本、创建与修改账号取自接入方，
--     update_time 取两者较大值，channel_type=PAYMENT。只有渠道的：出站地址为空、版本 1。只有接入方的：新建 DRAFT、PAYMENT 渠道，
--     channel_id 沿用 integration_id，名称、说明、出站地址、版本、账号与时间取自接入方。
--   - 协议与沙箱表的渠道列按对应关系改写（只有 MATCHED 且两 ID 不同的行需要改写）。
--   - t_channel_config_change：INTEGRATION_API / INTEGRATION_SCRIPT / INTEGRATION_IMPLEMENTATION 改为 CHANNEL_API / CHANNEL_SCRIPT /
--     CHANNEL_IMPLEMENTATION；能映射到现存接入方的 INTEGRATION 记录改为 CHANNEL，target_id 改为对应 channel_id，change_type 与
--     change_detail 不变，使渠道详情的变更记录页签能查到接入方时期的出站地址变更；已删除接入方的 INTEGRATION 记录无对应渠道，保留原值。
--   - 沙箱运行历史 t_channel_verify_run.case_snapshot_json / config_versions_json 只是历史快照，不改写：其中的 "integration" 键名、
--     接入方版本与旧 integration_id（MATCHED 渠道的旧 ID 不再存在于 t_channel）保持运行时原样。
-- 可重跑性：不可重跑。第 1 段守卫失败时没有任何改动；之后任一语句失败，按 phase2-rollback.md 用备份恢复后重新执行。

SET NAMES utf8mb4;
USE mor_channel_connector;

-- ---------------------------------------------------------------------------------------------------------------------
-- 1. 守卫：按 precheck 第 1–5 段的同一条件重算阻断数；不为 0 时第二条 INSERT 触发主键冲突，mysql 客户端立即终止。
-- ---------------------------------------------------------------------------------------------------------------------
SET @phase2_blocking = (
    (SELECT COUNT(*) FROM t_payment_channel WHERE NOT REGEXP_LIKE(channel_code, '^[A-Z][A-Z0-9_]{1,63}$', 'c'))
  + (SELECT COUNT(*) FROM t_integration WHERE NOT REGEXP_LIKE(integration_code, '^[A-Z][A-Z0-9_]{1,63}$', 'c'))
  + (SELECT COUNT(*) FROM (SELECT 1 FROM t_payment_channel GROUP BY CAST(UPPER(channel_code) AS BINARY) HAVING COUNT(*) > 1) d)
  + (SELECT COUNT(*) FROM (SELECT 1 FROM t_integration GROUP BY CAST(UPPER(integration_code) AS BINARY) HAVING COUNT(*) > 1) d)
  + (SELECT COUNT(*) FROM t_payment_channel p JOIN t_integration i ON CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY)
     WHERE CAST(p.channel_code AS BINARY) <> CAST(i.integration_code AS BINARY))
  + (SELECT COUNT(*) FROM t_integration_api WHERE integration_id NOT IN (SELECT integration_id FROM t_integration))
  + (SELECT COUNT(*) FROM t_integration_script WHERE integration_id NOT IN (SELECT integration_id FROM t_integration))
  + (SELECT COUNT(*) FROM t_integration_standard_api WHERE integration_id NOT IN (SELECT integration_id FROM t_integration))
  + (SELECT COUNT(*) FROM t_integration_verify_account WHERE integration_id NOT IN (SELECT integration_id FROM t_integration))
  + (SELECT COUNT(*) FROM t_integration_verify_case WHERE integration_id NOT IN (SELECT integration_id FROM t_integration))
  + (SELECT COUNT(*) FROM t_integration_verify_run WHERE integration_id NOT IN (SELECT integration_id FROM t_integration))
);
CREATE TEMPORARY TABLE tmp_phase2_guard (guard_key INT NOT NULL, PRIMARY KEY (guard_key));
INSERT INTO tmp_phase2_guard VALUES (1);
INSERT INTO tmp_phase2_guard SELECT 1 FROM DUAL WHERE @phase2_blocking > 0;
DROP TEMPORARY TABLE tmp_phase2_guard;

-- ---------------------------------------------------------------------------------------------------------------------
-- 2. 对应关系与迁移前行数（持久辅助表，verify 使用）。主键与唯一索引同时兜住 precheck 第 4 段的 ID 冲突。
-- ---------------------------------------------------------------------------------------------------------------------
CREATE TABLE tmp_phase2_channel_map (
  channel_id BIGINT NOT NULL COMMENT '合并后的渠道 ID',
  integration_id BIGINT NULL COMMENT '原接入方 ID；CHANNEL_ONLY 为空',
  channel_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '合并后的渠道编码',
  match_type VARCHAR(16) NOT NULL COMMENT 'MATCHED、CHANNEL_ONLY、INTEGRATION_ONLY',
  PRIMARY KEY (channel_id),
  UNIQUE KEY uk_tmp_phase2_integration (integration_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='第二段迁移辅助：渠道与接入方对应关系，核对后删除';

INSERT INTO tmp_phase2_channel_map (channel_id, integration_id, channel_code, match_type)
SELECT p.channel_id, i.integration_id, CONVERT(p.channel_code USING ascii), 'MATCHED'
FROM t_integration i JOIN t_payment_channel p ON CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY);

INSERT INTO tmp_phase2_channel_map (channel_id, integration_id, channel_code, match_type)
SELECT p.channel_id, NULL, CONVERT(p.channel_code USING ascii), 'CHANNEL_ONLY'
FROM t_payment_channel p
WHERE NOT EXISTS (SELECT 1 FROM t_integration i WHERE CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY));

INSERT INTO tmp_phase2_channel_map (channel_id, integration_id, channel_code, match_type)
SELECT i.integration_id, i.integration_id, i.integration_code, 'INTEGRATION_ONLY'
FROM t_integration i
WHERE NOT EXISTS (SELECT 1 FROM t_payment_channel p WHERE CAST(UPPER(p.channel_code) AS BINARY) = CAST(UPPER(i.integration_code) AS BINARY));

CREATE TABLE tmp_phase2_row_count (
  table_name VARCHAR(64) NOT NULL COMMENT '迁移后的表名',
  row_count BIGINT NOT NULL COMMENT '迁移前对应表的行数',
  PRIMARY KEY (table_name)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='第二段迁移辅助：迁移前行数，核对后删除';

INSERT INTO tmp_phase2_row_count (table_name, row_count)
SELECT 't_channel', COUNT(*) FROM tmp_phase2_channel_map
UNION ALL SELECT 't_channel_api', COUNT(*) FROM t_integration_api
UNION ALL SELECT 't_channel_script', COUNT(*) FROM t_integration_script
UNION ALL SELECT 't_channel_standard_api', COUNT(*) FROM t_integration_standard_api
UNION ALL SELECT 't_channel_verify_account', COUNT(*) FROM t_integration_verify_account
UNION ALL SELECT 't_channel_verify_case', COUNT(*) FROM t_integration_verify_case
UNION ALL SELECT 't_channel_verify_run', COUNT(*) FROM t_integration_verify_run
UNION ALL SELECT 't_channel_verify_run_step', COUNT(*) FROM t_integration_verify_run_step
UNION ALL SELECT 't_channel_account', COUNT(*) FROM t_channel_account
UNION ALL SELECT 't_channel_mid', COUNT(*) FROM t_channel_mid
UNION ALL SELECT 't_channel_cost_plan', COUNT(*) FROM t_channel_cost_plan
UNION ALL SELECT 't_channel_config_change', COUNT(*) FROM t_channel_config_change
UNION ALL SELECT 't_standard_api', COUNT(*) FROM t_standard_api
UNION ALL SELECT 't_api_field', COUNT(*) FROM t_api_field;

-- ---------------------------------------------------------------------------------------------------------------------
-- 3. 表结构：改名、列与索引、注释（隐式提交）。channel_type 与 version 先带默认值回填已有行，第 5 段去掉默认值。
-- ---------------------------------------------------------------------------------------------------------------------
RENAME TABLE
  `t_payment_channel` TO `t_channel`,
  `t_integration_api` TO `t_channel_api`,
  `t_integration_script` TO `t_channel_script`,
  `t_integration_standard_api` TO `t_channel_standard_api`,
  `t_integration_verify_account` TO `t_channel_verify_account`,
  `t_integration_verify_case` TO `t_channel_verify_case`,
  `t_integration_verify_run` TO `t_channel_verify_run`,
  `t_integration_verify_run_step` TO `t_channel_verify_run_step`;

ALTER TABLE `t_channel`
  MODIFY COLUMN `channel_id` BIGINT NOT NULL COMMENT '渠道的内部唯一标识；支付渠道的技术账号、MID、成本方案与 Payment 冻结的执行坐标均引用它。',
  MODIFY COLUMN `channel_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台稳定识别渠道的唯一业务代码，大小写敏感，格式为大写字母开头的大写字母、数字与下划线，2 至 64 字符，创建后不可修改；也是外部 API 管理接口中的接入方编码。取值来源：渠道目录；非固定枚举。',
  MODIFY COLUMN `channel_name` VARCHAR(255) NOT NULL COMMENT '供运营配置和审阅展示的渠道正式名称。',
  ADD COLUMN `channel_type` VARCHAR(16) NOT NULL DEFAULT 'PAYMENT' COMMENT '渠道类型，创建后不可修改；PAYMENT 有绑定 OE 的技术账号、MID、能力与成本，只经支付入口执行；SERVICE 最多一个不绑定 OE 的 ACTIVE 账号保存凭据，只经通用执行入口并按调用授权执行。允许值：PAYMENT、SERVICE。' AFTER `channel_name`,
  MODIFY COLUMN `channel_status` VARCHAR(64) NOT NULL COMMENT '渠道从草稿、激活、暂停到关闭的当前运行状态；激活要求已配置出站地址。允许值：DRAFT、ACTIVE、SUSPENDED、CLOSED。',
  ADD COLUMN `description` VARCHAR(512) NULL COMMENT '渠道业务用途说明；未填写时为空，禁止填写凭据。' AFTER `channel_status`,
  ADD COLUMN `endpoints` JSON NULL COMMENT '本环境出站地址数组 [{endpointCode, baseUrl, sandbox}]，1 至 16 项，未配置时为空；endpointCode 在渠道内唯一，baseUrl 的协议受部署允许列表限制（默认仅 https），不含用户信息、查询串与片段，最长 512 字符；保存时拦截回环、私网、链路本地、保留与组播 IP 字面量及 localhost，执行时解析主机名后再次拦截，部署配置的 trusted-networks 网段放行；sandbox 可选、缺省为 false，只有为 true 的地址允许沙箱验证运行。' AFTER `description`,
  ADD COLUMN `version` INT NOT NULL DEFAULT 1 COMMENT '名称、说明或出站地址保存时按版本条件更新并递增的并发版本，初始为 1；不是发布版本。' AFTER `endpoints`,
  ADD COLUMN `created_by` BIGINT NULL COMMENT '创建该渠道的 Operations 账号标识；2026-10-22 合表前创建且无接入方记录的渠道为空。' AFTER `version`,
  ADD COLUMN `updated_by` BIGINT NULL COMMENT '最后一次修改该渠道的 Operations 账号标识；2026-10-22 合表前创建且未再修改、无接入方记录的渠道为空。' AFTER `created_by`,
  DROP INDEX `uk_payment_channel_code`,
  ADD UNIQUE KEY `uk_channel_code` (`channel_code`) COMMENT '唯一索引：channel_code',
  ADD KEY `idx_channel_type_status` (`channel_type`, `channel_status`) COMMENT '访问索引：channel_type、channel_status',
  COMMENT='渠道（支付渠道与服务渠道）的稳定身份、类型、运行状态与本环境出站地址；支付渠道的技术账号、MID 与成本挂在其下。';

ALTER TABLE `t_channel_account`
  MODIFY COLUMN `channel_id` BIGINT NOT NULL COMMENT '渠道内部唯一标识（本库 t_channel.channel_id）。',
  MODIFY COLUMN `entity_no` CHAR(5) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '账号所属 Operating Entity 的不可变 CHAR(5) 编号，来源于 CCT；支付渠道账号必填，且与其当前绑定 MID 的 OE 一致，不作为路由规则；服务渠道账号必须为空（应用校验），每个服务渠道最多一个 ACTIVE 账号保存凭据。',
  COMMENT='归属单个渠道的技术账号：支付渠道账号绑定一个 OE，服务渠道账号不绑定 OE 且每渠道最多一个 ACTIVE；账号参数及凭据只保存 Vault 引用。';

ALTER TABLE `t_channel_mid`
  MODIFY COLUMN `channel_id` BIGINT NOT NULL COMMENT '渠道内部唯一标识（本库 t_channel.channel_id）。';

ALTER TABLE `t_channel_cost_plan`
  MODIFY COLUMN `channel_id` BIGINT NOT NULL COMMENT '支付渠道（本库 t_channel.channel_id，channel_type 为 PAYMENT）。';

ALTER TABLE `t_channel_config_change`
  MODIFY COLUMN `target_type` VARCHAR(64) NOT NULL COMMENT '被变更对象类型；渠道出站地址变更记为 CHANNEL 与 CONFIGURATION。允许值：CHANNEL、CHANNEL_ACCOUNT、CHANNEL_MID、MID_CAPABILITY、CHANNEL_COST_VERSION、STANDARD_API、CHANNEL_API、CHANNEL_SCRIPT、CHANNEL_IMPLEMENTATION、VERIFY_ACCOUNT、VERIFY_CASE、CHANNEL_CALL_GRANT。',
  MODIFY COLUMN `from_status` VARCHAR(64) NULL COMMENT '状态变更前的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组、成本版本或调用授权的状态代码；非固定枚举。',
  MODIFY COLUMN `to_status` VARCHAR(64) NULL COMMENT '状态变更后的状态；非状态变更为空。取值来源：渠道、账号、MID、能力组、成本版本或调用授权的状态代码；非固定枚举。',
  MODIFY COLUMN `change_detail` JSON NULL COMMENT '外部 API 配置与渠道出站地址变更的字段摘要：{objectCode, beforeVersion, afterVersion, changes[{field, before, after}]}，字段取值只记名称、编码与 SHA-256 摘要，不含定义原文、参数或凭据值；渠道状态、账号、MID、能力、成本版本与调用授权的变更为空。',
  COMMENT='渠道配置、成本版本、外部 API 配置与调用授权的人工变更留痕；与业务变更同事务写入，只增不改。';

ALTER TABLE `t_standard_api`
  MODIFY COLUMN `standard_api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务系统调用渠道执行入口时使用的标准 API 编码，大小写敏感，格式为大写字母开头的字母、数字与下划线。取值来源：channel-connector 标准 API 目录；非固定枚举。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  COMMENT='渠道对内提供的标准 API 定义，例如 PAY、AUTHORIZE、GEOCODE；请求与响应字段由 t_api_field 维护，服务渠道的调用方授权见 t_channel_call_grant';

ALTER TABLE `t_api_field`
  MODIFY COLUMN `api_id` BIGINT NOT NULL COMMENT '所属 API 标识：方向为 STANDARD_API_* 时指向 t_standard_api.standard_api_id，为 INTEGRATION_API_* 时指向 t_channel_api.channel_api_id；由应用校验。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。';

ALTER TABLE `t_channel_api`
  CHANGE COLUMN `integration_api_id` `channel_api_id` BIGINT NOT NULL COMMENT '渠道原子 API 的内部技术标识。',
  CHANGE COLUMN `integration_id` `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  MODIFY COLUMN `api_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的原子 API 编码，流程节点按此引用，大小写敏感。取值来源：所属渠道的原子 API 目录；非固定枚举。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_integration_api_code`,
  ADD UNIQUE KEY `uk_channel_api_code` (`channel_id`, `api_code`) COMMENT '保证业务唯一性：channel_id、api_code',
  COMMENT='渠道的单次外部协议操作；请求与响应结构由 t_api_field 维护，传输、编解码、脚本、状态规则与输出映射保存在 definition_json';

ALTER TABLE `t_channel_script`
  CHANGE COLUMN `integration_script_id` `channel_script_id` BIGINT NOT NULL COMMENT '公共脚本的内部技术标识。',
  CHANGE COLUMN `integration_id` `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  MODIFY COLUMN `script_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的脚本编码，原子 API 按此引用，大小写敏感。取值来源：所属渠道的公共脚本目录；非固定枚举。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_integration_script_code`,
  ADD UNIQUE KEY `uk_channel_script_code` (`channel_id`, `script_code`) COMMENT '保证业务唯一性：channel_id、script_code',
  COMMENT='渠道内可复用的请求或响应阶段脚本；复杂签名、加密由代码注册函数提供，脚本不读取凭据明文';

ALTER TABLE `t_channel_standard_api`
  CHANGE COLUMN `integration_standard_api_id` `channel_standard_api_id` BIGINT NOT NULL COMMENT '渠道标准 API 实现的内部技术标识。',
  CHANGE COLUMN `integration_id` `channel_id` BIGINT NOT NULL COMMENT '实现该标准 API 的渠道标识（t_channel.channel_id）。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_integration_standard_api`,
  DROP INDEX `idx_integ_std_api_standard`,
  ADD UNIQUE KEY `uk_channel_standard_api` (`channel_id`, `standard_api_id`) COMMENT '保证业务唯一性：channel_id、standard_api_id',
  ADD KEY `idx_channel_std_api_standard` (`standard_api_id`) COMMENT '支持访问路径：standard_api_id',
  COMMENT='渠道对某个标准 API 的当前实现；flow_definition_json 保存初始化、原子 API 节点、条件连线与返回出口，不保存流程实例或运行状态';

ALTER TABLE `t_channel_verify_account`
  CHANGE COLUMN `integration_id` `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  MODIFY COLUMN `account_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的验证账号编码，验证用例按此引用，大小写敏感。取值来源：所属渠道的验证账号目录；非固定枚举。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_verify_account_code`,
  ADD UNIQUE KEY `uk_verify_account_code` (`channel_id`, `account_code`) COMMENT '保证业务唯一性：channel_id、account_code';

ALTER TABLE `t_channel_verify_case`
  CHANGE COLUMN `integration_id` `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  MODIFY COLUMN `case_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道内唯一的验证用例编码，大小写敏感。取值来源：所属渠道的验证用例目录；非固定枚举。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  DROP INDEX `uk_verify_case_code`,
  ADD UNIQUE KEY `uk_verify_case_code` (`channel_id`, `case_code`) COMMENT '保证业务唯一性：channel_id、case_code',
  COMMENT='渠道的沙箱验证用例：使用的验证账号与有序步骤，每步包含标准 API、输入模板与期望';

ALTER TABLE `t_channel_verify_run`
  CHANGE COLUMN `integration_id` `channel_id` BIGINT NOT NULL COMMENT '所属渠道标识（t_channel.channel_id）。',
  MODIFY COLUMN `config_versions_json` JSON NULL COMMENT '运行实际加载的标准 API、渠道、原子 API、脚本与实现版本，按编码记录；运行未加载配置时为空。',
  MODIFY COLUMN `endpoints_json` JSON NULL COMMENT '运行时渠道出站地址及沙箱标记；运行未加载配置时为空。',
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  DROP INDEX `idx_verify_run_integration`,
  ADD KEY `idx_verify_run_channel` (`channel_id`, `start_time`) COMMENT '支持访问路径：channel_id、start_time';

ALTER TABLE `t_channel_verify_run_step`
  MODIFY COLUMN `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。';


CREATE TABLE `t_channel_call_grant` (
  `call_grant_id` BIGINT NOT NULL COMMENT '调用授权的内部技术标识。',
  `caller_service` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调用方服务名（调用方的 spring.application.name），小写字母开头，小写字母、数字或连字符，2 至 64 字符；只校验格式，不维护服务名册。取值来源：内部服务名；非固定枚举。',
  `channel_id` BIGINT NOT NULL COMMENT '被授权调用的服务渠道标识（t_channel.channel_id，channel_type 为 SERVICE）。',
  `standard_api_id` BIGINT NOT NULL COMMENT '被授权调用的标准 API 标识（t_standard_api.standard_api_id），授权时须已被该渠道实现；由应用校验。',
  `grant_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '授权当前是否放行；DISABLED 可再启用。允许值：ACTIVE、DISABLED。',
  `version` INT NOT NULL COMMENT '启用、停用时按版本条件更新的并发版本，初始为 1。',
  `created_by` BIGINT NOT NULL COMMENT '创建该授权的 Operations 账号标识。',
  `updated_by` BIGINT NOT NULL COMMENT '最后一次修改该授权的 Operations 账号标识。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在本库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`call_grant_id`),
  UNIQUE KEY `uk_channel_call_grant` (`caller_service`, `channel_id`, `standard_api_id`) COMMENT '保证业务唯一性：caller_service、channel_id、standard_api_id',
  KEY `idx_channel_call_grant_channel` (`channel_id`, `grant_status`) COMMENT '支持访问路径：channel_id、grant_status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务渠道的调用授权：调用方服务 × 渠道 × 标准 API 白名单，只有 ACTIVE 授权放行通用执行入口；授权只停用、不删除，同一组合只有一行，再启用沿用该行并递增版本';

-- ---------------------------------------------------------------------------------------------------------------------
-- 4. 数据合并与引用改写（一个本地事务）。
-- ---------------------------------------------------------------------------------------------------------------------
START TRANSACTION;

UPDATE t_channel c
JOIN tmp_phase2_channel_map m ON m.channel_id = c.channel_id AND m.match_type = 'MATCHED'
JOIN t_integration i ON i.integration_id = m.integration_id
SET c.description = i.description,
    c.endpoints = i.endpoints,
    c.version = i.version,
    c.created_by = i.created_by,
    c.updated_by = i.updated_by,
    c.update_time = GREATEST(c.update_time, i.update_time);

INSERT INTO t_channel (channel_id, channel_code, channel_name, channel_type, channel_status, description, endpoints, version,
  created_by, updated_by, create_time, update_time)
SELECT i.integration_id, i.integration_code, i.integration_name, 'PAYMENT', 'DRAFT', i.description, i.endpoints, i.version,
  i.created_by, i.updated_by, i.create_time, i.update_time
FROM t_integration i
JOIN tmp_phase2_channel_map m ON m.integration_id = i.integration_id AND m.match_type = 'INTEGRATION_ONLY';

UPDATE t_channel_api t JOIN tmp_phase2_channel_map m ON m.integration_id = t.channel_id
SET t.channel_id = m.channel_id WHERE m.match_type = 'MATCHED' AND m.channel_id <> m.integration_id;
UPDATE t_channel_script t JOIN tmp_phase2_channel_map m ON m.integration_id = t.channel_id
SET t.channel_id = m.channel_id WHERE m.match_type = 'MATCHED' AND m.channel_id <> m.integration_id;
UPDATE t_channel_standard_api t JOIN tmp_phase2_channel_map m ON m.integration_id = t.channel_id
SET t.channel_id = m.channel_id WHERE m.match_type = 'MATCHED' AND m.channel_id <> m.integration_id;
UPDATE t_channel_verify_account t JOIN tmp_phase2_channel_map m ON m.integration_id = t.channel_id
SET t.channel_id = m.channel_id WHERE m.match_type = 'MATCHED' AND m.channel_id <> m.integration_id;
UPDATE t_channel_verify_case t JOIN tmp_phase2_channel_map m ON m.integration_id = t.channel_id
SET t.channel_id = m.channel_id WHERE m.match_type = 'MATCHED' AND m.channel_id <> m.integration_id;
UPDATE t_channel_verify_run t JOIN tmp_phase2_channel_map m ON m.integration_id = t.channel_id
SET t.channel_id = m.channel_id WHERE m.match_type = 'MATCHED' AND m.channel_id <> m.integration_id;

UPDATE t_channel_config_change SET target_type = 'CHANNEL_API' WHERE target_type = 'INTEGRATION_API';
UPDATE t_channel_config_change SET target_type = 'CHANNEL_SCRIPT' WHERE target_type = 'INTEGRATION_SCRIPT';
UPDATE t_channel_config_change SET target_type = 'CHANNEL_IMPLEMENTATION' WHERE target_type = 'INTEGRATION_IMPLEMENTATION';
UPDATE t_channel_config_change c
JOIN tmp_phase2_channel_map m ON m.integration_id = c.target_id AND m.match_type IN ('MATCHED', 'INTEGRATION_ONLY')
SET c.target_type = 'CHANNEL', c.target_id = m.channel_id
WHERE c.target_type = 'INTEGRATION';

COMMIT;

-- ---------------------------------------------------------------------------------------------------------------------
-- 5. 收尾（隐式提交）：去掉回填用默认值，删除已并入 t_channel 的 t_integration。
-- ---------------------------------------------------------------------------------------------------------------------
ALTER TABLE `t_channel`
  ALTER COLUMN `channel_type` DROP DEFAULT,
  ALTER COLUMN `version` DROP DEFAULT;

DROP TABLE `t_integration`;

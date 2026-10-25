-- 2026-10-24 商业关系快照与真实解析接入（client-master，结构变更）。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md 第 3.3.1、3.4.1 节。
-- 内容：t_operating_entity 增加 acquiring_qualification（本文件先加可空列，回填与改 NOT NULL 在
--       2026-10-24-relationship-snapshot-data.sql 第 ① 步）；新建 t_partner_role、t_party_role、t_client_partner_relation；
--       t_entity_source_relation / t_operating_entity_application 的 source_type 注释删除 PLATFORM（只改注释，不改数据）。
-- 执行顺序：本文件 → 2026-10-24-relationship-snapshot-data.sql → 新版 client-master 代码。
-- 执行前备份 t_operating_entity、t_entity_source_relation、t_operating_entity_application；DDL 隐式提交，失败时先核对已完成的语句，
-- 禁止整段重跑（CREATE TABLE 已存在会失败，可跳过已完成语句）。
--
-- 回退（须与 commercial / acquiring / risk-decision 代码回退及数据文件回退同时进行，新代码已不认 PLATFORM）：
--   先执行数据文件的回退段，再：
--   DROP TABLE `t_client_partner_relation`;
--   DROP TABLE `t_party_role`;
--   DROP TABLE `t_partner_role`;
--   ALTER TABLE `t_operating_entity` DROP COLUMN `acquiring_qualification`;
--   source_type 注释恢复为含 PLATFORM 的旧文本（见 git 历史中的 client-master-schema.sql），不影响数据。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 1. 收单资质列：先可空，数据文件回填 PAYFAC 后改为 NOT NULL。
ALTER TABLE `t_operating_entity`
  ADD COLUMN `acquiring_qualification` VARCHAR(64) NULL COMMENT 'Operating Entity 经收单行认定的收单资质档位：MERCHANT 只能作为自身商户收单、不能承接 Client；MERCHANT_RESALE_APPROVED 经收单行批准转售、只承接 MOR；PAYFAC 支付服务商资质、可承接 MOR 与 PSP。允许值：MERCHANT、MERCHANT_RESALE_APPROVED、PAYFAC。' AFTER `status`;

-- 2. 三张新表（定义与 client-master-schema.sql 一致）。
CREATE TABLE `t_partner_role` (
  `partner_role_id` BIGINT NOT NULL COMMENT 'Local Partner 商业角色行的内部唯一标识。',
  `local_partner_id` BIGINT NOT NULL COMMENT '承担该角色的 Local Partner 标识。',
  `role_code` VARCHAR(64) NOT NULL COMMENT 'Local Partner 承担的商业角色代码。允许值：OE_PROVIDER、REFERRER。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `active_slot` TINYINT(1) NULL COMMENT '同一 Local Partner 同一角色同时只有一行有效的稀疏唯一槽位。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`partner_role_id`),
  UNIQUE KEY `uk_partner_role_active` (`local_partner_id`, `role_code`, `active_slot`) COMMENT 'Unique index for partner_role on local_partner_id, role_code, active_slot',
  KEY `idx_partner_role_code` (`role_code`, `status`) COMMENT 'Access index for partner_role on role_code, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Local Partner 商业角色；uk_partner_role_active(local_partner_id,role_code,active_slot)；role_code IN (OE_PROVIDER,REFERRER)，状态 ACTIVE/ENDED，仅 ACTIVE 的 active_slot=1；LOCAL_PARTNER 根的非审计子行，增减角色推进根 BCR 版本并同步 t_party_role；仍有 ACTIVE 的 PARTNER 来源关系时不得结束 OE_PROVIDER；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_party_role` (
  `party_role_id` BIGINT NOT NULL COMMENT '法律主体业务角色行的内部唯一标识。',
  `party_id` BIGINT NOT NULL COMMENT '自然人或机构法律身份根的内部唯一标识。',
  `role_code` VARCHAR(64) NOT NULL COMMENT '法律主体承担的业务角色代码。允许值：CLIENT、OPERATING_ENTITY、OE_PROVIDER、REFERRER、SPLIT_PAYEE。',
  `subject_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '角色对应的业务主体标识：CLIENT 为 client_id，OPERATING_ENTITY 为 entity_no，OE_PROVIDER/REFERRER 为 local_partner_id，均为十进制或编号文本。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `active_slot` TINYINT(1) NULL COMMENT '同一角色同一主体同时只有一行有效的稀疏唯一槽位。允许值：1 或 NULL。取值规则：状态为 ACTIVE 时为 1，其他状态为 NULL。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`party_role_id`),
  UNIQUE KEY `uk_party_role_active` (`role_code`, `subject_id`, `active_slot`) COMMENT 'Unique index for party_role on role_code, subject_id, active_slot',
  KEY `idx_party_role_party` (`party_id`, `status`) COMMENT 'Access index for party_role on party_id, status'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='法律主体统一角色索引；uk_party_role_active(role_code,subject_id,active_slot)；状态 ACTIVE/ENDED，仅 ACTIVE 的 active_slot=1，ENDED 填写 effective_to；Client 创建、Partner 角色维护时与所属根同一事务写入，是 Client / Operating Entity / Local Partner 根的非审计子行；应用校验，不创建 CHECK/FK';

CREATE TABLE `t_client_partner_relation` (
  `relation_id` BIGINT NOT NULL COMMENT 'Client 与 Local Partner 业务关系的内部唯一标识。',
  `client_id` BIGINT NOT NULL COMMENT '平台 Client 角色的内部唯一标识。',
  `local_partner_id` BIGINT NOT NULL COMMENT '与 Client 建立业务关系的 Local Partner 标识。',
  `relation_type` VARCHAR(64) NOT NULL COMMENT 'Client 与 Local Partner 的关系类型。允许值：REFERRAL。',
  `status` VARCHAR(64) NOT NULL COMMENT '当前业务记录在其所属聚合生命周期中的状态代码。允许值：ACTIVE、ENDED。',
  `effective_from` DATETIME(6) NOT NULL COMMENT '业务记录生效区间的 UTC 微秒精度起点，包含该时刻。',
  `effective_to` DATETIME(6) NULL COMMENT '业务记录生效区间的 UTC 微秒精度终点，不包含该时刻；为空表示未预定结束。',
  `create_time` DATETIME(6) NOT NULL COMMENT '数据库记录创建的 UTC 时间，精确到微秒。',
  `update_time` DATETIME(6) NOT NULL COMMENT '数据库记录最后一次更新的 UTC 时间，精确到微秒。',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_client_partner_rel` (`client_id`, `local_partner_id`, `relation_type`, `effective_from`) COMMENT 'Unique index for client_partner_relation on client_id, local_partner_id, relation_type, effective_from',
  KEY `idx_client_partner_rel_partner` (`local_partner_id`) COMMENT 'Access index for client_partner_relation on local_partner_id'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Client 与 Local Partner 的推荐等业务关系；uk_client_partner_rel(client_id,local_partner_id,relation_type,effective_from)；relation_type=REFERRAL，状态 ACTIVE/ENDED；本期只建表，没有写入方与读取方，与合同 referral_partner_id 的关系由后续需求确定；应用校验，不创建 CHECK/FK';

-- 3. source_type 取值删除 PLATFORM：只改列注释与表注释。
ALTER TABLE `t_entity_source_relation`
  MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '该 Operating Entity 来源关系对应的来源主体类型。允许值：CLIENT、PARTNER。',
  COMMENT='OE 的唯一来源认定关系；uk_source_effective(entity_no,effective_from)、uk_source_active(entity_no,active_source_slot)；source_type IN (CLIENT,PARTNER)，Owner XOR；状态仅 ACTIVE/ENDED，仅 ACTIVE 的 active_source_slot=1；Client 来源依据仅为 SAME_PARTY/VERIFIED_ASSOCIATION；CHECK ((status=''ACTIVE'' AND active_source_slot=1) OR (status=''ENDED'' AND active_source_slot IS NULL))；CHECK (effective_to IS NULL OR effective_from < effective_to)；Owner XOR：CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。；CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。；source_type、source_client_id、source_partner_id、client_source_basis 创建后不可修改；来源失效时只允许推进为 ENDED 并填写 effective_to。';

ALTER TABLE `t_operating_entity_application`
  MODIFY COLUMN `source_type` VARCHAR(64) NOT NULL COMMENT '本次 Operating Entity 入驻申请的法律来源类型，提交 Actor 或入口渠道不由该字段表达。允许值：CLIENT、PARTNER。',
  COMMENT='OE入驻或完整目标范围变更申请；uk_oe_app_no(application_no)、uk_oe_app_active(entity_no,active_slot)；申请状态/Slot 与 Client Application 相同；entity_no 非空稳定，ONBOARDING 使用 ENTITY_UNDERWRITING，SCOPE_CHANGE 使用 ENTITY_SCOPE_REVIEW（Risk 接入待后续实施）；仅 APPROVED 的 approved_source_relation_id 非空；application_type=ONBOARDING/SCOPE_CHANGE，Scope批准时核对base_entity_change_version；Risk新审核类型尚未接入时禁止自动激活范围；CHECK ((status IN (''DRAFT'',''SUBMITTED'',''UNDER_REVIEW'') AND active_slot=1) OR (status IN (''APPROVED'',''REJECTED'',''WITHDRAWN'') AND active_slot IS NULL))；CHECK ((qualification_decision_id IS NULL) = (qualification_decision_version IS NULL))；CHECK ((status=''APPROVED'' AND approved_source_relation_id IS NOT NULL) OR (status<>''APPROVED'' AND approved_source_relation_id IS NULL))；Risk Tuple 固定映射：role_type=OPERATING_ENTITY、role_id=entity_no、ONBOARDING 的 qualification_type=ENTITY_UNDERWRITING；SCOPE_CHANGE 的 qualification_type=ENTITY_SCOPE_REVIEW。；qualification_decision_id 与 qualification_decision_version 必须成组为空或成组非空。；APPROVED/REJECTED 前 due_diligence_case_id、qualification_decision_id、qualification_decision_version、qualification_type、role_type、role_id 必须匹配同一次 Risk 决策；WITHDRAWN 不要求 Decision。；qualification_type 由系统按申请类型固定为 ENTITY_UNDERWRITING 或 ENTITY_SCOPE_REVIEW，不接受调用方输入。；提交后 source_type、source_client_id、source_partner_id、client_source_basis 与 qualification_type 组成的 Source/Qualification Tuple 冻结且不可修改。；approved_source_relation_id 仅在 APPROVED 时非空，其他状态必须为空。；Source Owner XOR：CLIENT 要求 source_client_id IS NOT NULL AND source_partner_id IS NULL；PARTNER 要求 source_client_id IS NULL AND source_partner_id IS NOT NULL。；CLIENT 来源依据：SAME_PARTY 要求 Client Party=Operating Entity Party；VERIFIED_ASSOCIATION 要求两个 Party 不同，并由已批准申请、冻结材料和匹配的 Risk Qualification Decision 支撑；非 CLIENT 要求 client_source_basis IS NULL。；SCOPE_CHANGE 必须核对 base_entity_change_version 与完整目标 Scope，沿用既有主体和唯一来源关系，不回退主体状态；过时或拒绝的审核不能撤销现有范围。Risk 新 Tuple 未接入时禁止该流程激活范围。';

-- 核对：
-- SHOW CREATE TABLE `t_operating_entity`;            -- acquiring_qualification VARCHAR(64) NULL（数据文件执行后为 NOT NULL）
-- SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
--   AND table_name IN ('t_partner_role','t_party_role','t_client_partner_relation');   -- 期望 3

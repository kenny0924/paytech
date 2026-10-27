-- 2026-10-27 全球合规入驻与资格 · 切片 1 · 在途入驻数据清理（发布决策 P-8，requirements/20261027/client-master-全球合规入驻与资格切片1/delivery.md）
-- 目标库：mor_compliance（mor-test）、mor_compliance_v2e2e（本地 e2e）。由开发者审核后用具 DML 权限的账号手工执行；应用不会执行本文件。
--
-- 执行顺序：2026-10-27-onboarding-global.sql（保留行的 DDL）→ compliance-initial-data.sql（先 SET @approved_by）→ 本文件 → compliance 新镜像。
-- 执行前：compliance 服务已停止（无 Claim 中的补偿任务）；已 mysqldump 备份整库。用 mysql 客户端整文件执行：任一语句失败即退出，未提交的删除事务随连接回滚。
--
-- 清理口径（P-8，按最保守理解）：
--   1. 以"案件族"为单位：主案件（不是任何依赖的 child_case_id）及其经 t_dd_case_dependency 关联的子案件。
--      主案件 status = 'APPROVED' 的整族保留（含其子案件，即使子案件不是 APPROVED，以免留下断开的依赖）；其余整族删除。
--   2. 删除被删案件的全部子行：主体、主体快照、分类、依赖、要求、证据、检查、筛查执行、命中、命中名单、处置、状态迁移、风险因子、退回错误。
--   3. 资格：删除 case_id 属于被删案件的资格决定、引用这些决定的资格状态迁移；当前决定被删的角色资格及其全部状态迁移一并删除。
--      MANUAL 决定（case_id 为空）与 APPROVED 案件的决定全部保留，因此 ACTIVE Client 的资格保留。
--   4. 补偿任务：删除 eventKey 为被删主案件的 CLIENT_REVIEW_RESULT / CASE_SCREENING，以及 eventKey 为被删资格的 QUALIFICATION_CHANGED。
-- 回滚：恢复执行前的 mysqldump 备份（删除不可逆）。

-- ===== 0. 预检（只读）=====
-- 0.1 案件按状态
SELECT `status`, COUNT(*) AS case_count FROM `t_due_diligence_case` GROUP BY `status` ORDER BY `status`;

-- 0.2 待删 / 保留的主案件族
SELECT CASE WHEN c.`status` = 'APPROVED' THEN 'KEEP' ELSE 'DELETE' END AS family_action, COUNT(*) AS main_case_count
  FROM `t_due_diligence_case` c
 WHERE NOT EXISTS (SELECT 1 FROM `t_dd_case_dependency` d WHERE d.`child_case_id` = c.`case_id`)
 GROUP BY family_action;

-- 0.3 保留族中非 APPROVED 的子案件（随族保留，只作记录）
SELECT ch.`case_id`, ch.`status`, d.`parent_case_id`
  FROM `t_dd_case_dependency` d
  JOIN `t_due_diligence_case` p ON p.`case_id` = d.`parent_case_id` AND p.`status` = 'APPROVED'
  JOIN `t_due_diligence_case` ch ON ch.`case_id` = d.`child_case_id`
 WHERE ch.`status` <> 'APPROVED';

-- 0.4 【必须为 0，否则停止】子案件的父案件本身又是子案件（多层依赖，本脚本只处理两层）
SELECT COUNT(*) AS nested_dependency_count
  FROM `t_dd_case_dependency` d
 WHERE EXISTS (SELECT 1 FROM `t_dd_case_dependency` d2 WHERE d2.`child_case_id` = d.`parent_case_id`);

-- 0.5 【必须为 0，否则停止】当前决定来自待删案件、但还有保留决定的角色资格（删除会丢失有效资格历史，需人工处理）
SELECT COUNT(*) AS mixed_qualification_count
  FROM `t_role_qualification` q
  JOIN `t_qualification_decision` cur ON cur.`decision_id` = q.`current_decision_id`
  JOIN `t_due_diligence_case` cc ON cc.`case_id` = cur.`case_id`
 WHERE cc.`status` <> 'APPROVED'
   AND EXISTS (SELECT 1 FROM `t_qualification_state_change` s
                 JOIN `t_qualification_decision` k ON k.`decision_id` = s.`decision_id`
                WHERE s.`qualification_id` = q.`qualification_id`
                  AND (k.`case_id` IS NULL OR EXISTS (SELECT 1 FROM `t_due_diligence_case` kc
                                                      WHERE kc.`case_id` = k.`case_id` AND kc.`status` = 'APPROVED')));

-- 0.6 【必须为 0，否则停止】Claim 中的补偿任务（服务未停）
SELECT COUNT(*) AS processing_event_count FROM `t_compensation_event` WHERE `execute_status` = 'PROCESSING';

-- 0.7 资格与补偿任务现状
SELECT `role_type`, `status`, COUNT(*) AS qualification_count FROM `t_role_qualification` GROUP BY `role_type`, `status`;
SELECT `event_type`, `execute_status`, COUNT(*) AS event_count FROM `t_compensation_event` GROUP BY `event_type`, `execute_status`;

-- ===== 1. 计算删除集合 =====
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_main`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_case`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_decision`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_qualification`;
CREATE TEMPORARY TABLE `tmp_cleanup_main` (`case_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cleanup_case` (`case_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cleanup_decision` (`decision_id` BIGINT NOT NULL PRIMARY KEY);
CREATE TEMPORARY TABLE `tmp_cleanup_qualification` (`qualification_id` BIGINT NOT NULL PRIMARY KEY);

INSERT INTO `tmp_cleanup_main` (`case_id`)
SELECT c.`case_id` FROM `t_due_diligence_case` c
 WHERE c.`status` <> 'APPROVED'
   AND NOT EXISTS (SELECT 1 FROM `t_dd_case_dependency` d WHERE d.`child_case_id` = c.`case_id`);
INSERT INTO `tmp_cleanup_case` (`case_id`) SELECT `case_id` FROM `tmp_cleanup_main`;
INSERT IGNORE INTO `tmp_cleanup_case` (`case_id`)
SELECT d.`child_case_id` FROM `t_dd_case_dependency` d JOIN `tmp_cleanup_main` m ON m.`case_id` = d.`parent_case_id`;
INSERT INTO `tmp_cleanup_decision` (`decision_id`)
SELECT q.`decision_id` FROM `t_qualification_decision` q JOIN `tmp_cleanup_case` t ON t.`case_id` = q.`case_id`;
INSERT INTO `tmp_cleanup_qualification` (`qualification_id`)
SELECT r.`qualification_id` FROM `t_role_qualification` r JOIN `tmp_cleanup_decision` t ON t.`decision_id` = r.`current_decision_id`;

SELECT (SELECT COUNT(*) FROM `tmp_cleanup_main`) AS delete_main_cases, (SELECT COUNT(*) FROM `tmp_cleanup_case`) AS delete_cases,
       (SELECT COUNT(*) FROM `tmp_cleanup_decision`) AS delete_decisions, (SELECT COUNT(*) FROM `tmp_cleanup_qualification`) AS delete_qualifications;

-- ===== 2. 删除（一个事务）=====
START TRANSACTION;
DELETE l FROM `t_screening_hit_list` l
  JOIN `t_screening_hit` h ON h.`hit_id` = l.`hit_id`
  JOIN `t_screening_run` r ON r.`screening_run_id` = h.`screening_run_id`
  JOIN `tmp_cleanup_case` t ON t.`case_id` = r.`case_id`;
DELETE s FROM `t_screening_resolution` s
  JOIN `t_screening_hit` h ON h.`hit_id` = s.`hit_id`
  JOIN `t_screening_run` r ON r.`screening_run_id` = h.`screening_run_id`
  JOIN `tmp_cleanup_case` t ON t.`case_id` = r.`case_id`;
DELETE h FROM `t_screening_hit` h
  JOIN `t_screening_run` r ON r.`screening_run_id` = h.`screening_run_id`
  JOIN `tmp_cleanup_case` t ON t.`case_id` = r.`case_id`;
DELETE r FROM `t_screening_run` r JOIN `tmp_cleanup_case` t ON t.`case_id` = r.`case_id`;
DELETE c FROM `t_dd_check` c JOIN `tmp_cleanup_case` t ON t.`case_id` = c.`case_id`;
DELETE e FROM `t_dd_evidence_ref` e
  JOIN `t_dd_requirement` q ON q.`requirement_id` = e.`requirement_id`
  JOIN `tmp_cleanup_case` t ON t.`case_id` = q.`case_id`;
DELETE q FROM `t_dd_requirement` q JOIN `tmp_cleanup_case` t ON t.`case_id` = q.`case_id`;
DELETE p FROM `t_dd_party_snapshot` p
  JOIN `t_dd_case_subject` s ON s.`case_subject_id` = p.`case_subject_id`
  JOIN `tmp_cleanup_case` t ON t.`case_id` = s.`case_id`;
DELETE s FROM `t_dd_case_subject` s JOIN `tmp_cleanup_case` t ON t.`case_id` = s.`case_id`;
DELETE c FROM `t_dd_case_classification` c JOIN `tmp_cleanup_case` t ON t.`case_id` = c.`case_id`;
DELETE d FROM `t_dd_case_dependency` d JOIN `tmp_cleanup_case` t ON t.`case_id` = d.`parent_case_id`;
DELETE d FROM `t_dd_case_dependency` d JOIN `tmp_cleanup_case` t ON t.`case_id` = d.`child_case_id`;
DELETE f FROM `t_dd_risk_factor` f JOIN `tmp_cleanup_case` t ON t.`case_id` = f.`case_id`;
DELETE e FROM `t_dd_return_error` e JOIN `tmp_cleanup_case` t ON t.`case_id` = e.`case_id`;
DELETE s FROM `t_dd_case_state_change` s JOIN `tmp_cleanup_case` t ON t.`case_id` = s.`case_id`;
DELETE s FROM `t_qualification_state_change` s JOIN `tmp_cleanup_qualification` t ON t.`qualification_id` = s.`qualification_id`;
DELETE s FROM `t_qualification_state_change` s JOIN `tmp_cleanup_decision` t ON t.`decision_id` = s.`decision_id`;
DELETE r FROM `t_role_qualification` r JOIN `tmp_cleanup_qualification` t ON t.`qualification_id` = r.`qualification_id`;
DELETE q FROM `t_qualification_decision` q JOIN `tmp_cleanup_decision` t ON t.`decision_id` = q.`decision_id`;
DELETE e FROM `t_compensation_event` e JOIN `tmp_cleanup_main` t ON e.`event_key` = CAST(t.`case_id` AS CHAR) COLLATE utf8mb4_0900_ai_ci
 WHERE e.`event_type` IN ('CLIENT_REVIEW_RESULT', 'CASE_SCREENING');
DELETE e FROM `t_compensation_event` e JOIN `tmp_cleanup_qualification` t ON e.`event_key` = CAST(t.`qualification_id` AS CHAR) COLLATE utf8mb4_0900_ai_ci
 WHERE e.`event_type` = 'QUALIFICATION_CHANGED';
DELETE c FROM `t_due_diligence_case` c JOIN `tmp_cleanup_case` t ON t.`case_id` = c.`case_id`;
COMMIT;

-- ===== 3. 核验（全部应为 0，除 3.6 / 3.7 为保留现状）=====
-- 3.1 剩余主案件都是 APPROVED
SELECT COUNT(*) AS non_approved_main_cases FROM `t_due_diligence_case` c
 WHERE c.`status` <> 'APPROVED' AND NOT EXISTS (SELECT 1 FROM `t_dd_case_dependency` d WHERE d.`child_case_id` = c.`case_id`);
-- 3.2 孤儿子行
SELECT 'subject' AS child, COUNT(*) AS orphan_count FROM `t_dd_case_subject` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'snapshot', COUNT(*) FROM `t_dd_party_snapshot` x WHERE NOT EXISTS (SELECT 1 FROM `t_dd_case_subject` s WHERE s.`case_subject_id` = x.`case_subject_id`)
UNION ALL SELECT 'classification', COUNT(*) FROM `t_dd_case_classification` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'dependency', COUNT(*) FROM `t_dd_case_dependency` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`parent_case_id`)
                                                                    OR NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`child_case_id`)
UNION ALL SELECT 'requirement', COUNT(*) FROM `t_dd_requirement` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'evidence', COUNT(*) FROM `t_dd_evidence_ref` x WHERE NOT EXISTS (SELECT 1 FROM `t_dd_requirement` r WHERE r.`requirement_id` = x.`requirement_id`)
UNION ALL SELECT 'check', COUNT(*) FROM `t_dd_check` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'screening_run', COUNT(*) FROM `t_screening_run` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'screening_hit', COUNT(*) FROM `t_screening_hit` x WHERE NOT EXISTS (SELECT 1 FROM `t_screening_run` r WHERE r.`screening_run_id` = x.`screening_run_id`)
UNION ALL SELECT 'hit_list', COUNT(*) FROM `t_screening_hit_list` x WHERE NOT EXISTS (SELECT 1 FROM `t_screening_hit` h WHERE h.`hit_id` = x.`hit_id`)
UNION ALL SELECT 'resolution', COUNT(*) FROM `t_screening_resolution` x WHERE NOT EXISTS (SELECT 1 FROM `t_screening_hit` h WHERE h.`hit_id` = x.`hit_id`)
UNION ALL SELECT 'case_state_change', COUNT(*) FROM `t_dd_case_state_change` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'risk_factor', COUNT(*) FROM `t_dd_risk_factor` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'return_error', COUNT(*) FROM `t_dd_return_error` x WHERE NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'decision', COUNT(*) FROM `t_qualification_decision` x WHERE x.`case_id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE c.`case_id` = x.`case_id`)
UNION ALL SELECT 'role_qualification', COUNT(*) FROM `t_role_qualification` x WHERE NOT EXISTS (SELECT 1 FROM `t_qualification_decision` q WHERE q.`decision_id` = x.`current_decision_id`)
UNION ALL SELECT 'qualification_state_change', COUNT(*) FROM `t_qualification_state_change` x
           WHERE NOT EXISTS (SELECT 1 FROM `t_role_qualification` q WHERE q.`qualification_id` = x.`qualification_id`)
              OR NOT EXISTS (SELECT 1 FROM `t_qualification_decision` d WHERE d.`decision_id` = x.`decision_id`)
UNION ALL SELECT 'case_event', COUNT(*) FROM `t_compensation_event` x WHERE x.`event_type` IN ('CLIENT_REVIEW_RESULT', 'CASE_SCREENING')
           AND NOT EXISTS (SELECT 1 FROM `t_due_diligence_case` c WHERE CAST(c.`case_id` AS CHAR) COLLATE utf8mb4_0900_ai_ci = x.`event_key`)
UNION ALL SELECT 'qualification_event', COUNT(*) FROM `t_compensation_event` x WHERE x.`event_type` = 'QUALIFICATION_CHANGED'
           AND NOT EXISTS (SELECT 1 FROM `t_role_qualification` q WHERE CAST(q.`qualification_id` AS CHAR) COLLATE utf8mb4_0900_ai_ci = x.`event_key`);
-- 3.3 仍可被解析的旧程序（DDL 已置 RETIRED，应为 0）
SELECT COUNT(*) AS legacy_resolvable_programs FROM `t_dd_program` p
 WHERE p.`status` = 'ACTIVE' AND p.`program_id` NOT BETWEEN 9102710000000000 AND 9102719999999999;
-- 3.4 证据地址格式（应为 0）
SELECT COUNT(*) AS bad_file_address FROM `t_dd_evidence_ref` WHERE `file_address` NOT REGEXP '^/files/v1/[1-9][0-9]{0,18}$';
-- 3.5 进行中案件槽位（应为 0）
SELECT COUNT(*) AS active_slot_count FROM `t_due_diligence_case` WHERE `active_slot` IS NOT NULL;
-- 3.6 保留的案件与资格
SELECT `status`, COUNT(*) AS case_count FROM `t_due_diligence_case` GROUP BY `status`;
SELECT `role_type`, `status`, COUNT(*) AS qualification_count FROM `t_role_qualification` GROUP BY `role_type`, `status`;
-- 3.7 可选交叉核对（只读，运维会话执行；需要 mor_client_master 读权限）：保留的 CLIENT 资格对应的 Client 状态
-- SELECT c.`status`, COUNT(*) FROM `t_role_qualification` q JOIN `mor_client_master`.`t_client` c ON c.`client_id` = q.`role_id`
--  WHERE q.`role_type` = 'CLIENT' GROUP BY c.`status`;

DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_qualification`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_decision`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_case`;
DROP TEMPORARY TABLE IF EXISTS `tmp_cleanup_main`;

-- 2026-10-24 商业关系快照与真实解析接入：按入驻审核历史回填 Client 角色资格投影（S-09）。
-- 需求：requirements/20261023/commercial-商业关系快照与真实解析接入/technique.md 第 3.4.2 节。
-- 前置：2026-10-24-qualification-manual-and-role-id.sql 已执行。目标库：mor_compliance（root 手工执行）。
--
-- 规则与线上投影（DecideClientReviewProcessor）一致：投影按 (CLIENT, role_id, qualification_type) 一行；取该组合下
-- Client 入驻主案件（source_namespace=CCT_CLIENT_APPLICATION、role_type=CLIENT）的 REVIEWER 决定（decision_version 为空），
-- 按 decided_time、decision_id 取最新一条，不论结论：
--   最新为 APPROVED → APPROVED / NOT_REQUIRED（通过时线上新建或切换为 APPROVED）。
--   最新为 REJECTED 且更早存在同组合的 APPROVED 决定 → REJECTED / COMPLETED（线上拒绝只把已批准资格降级）。
--   最新为 REJECTED 且从未通过 → 不回填（线上拒绝不为没有资格的 Client 新建行）。
-- 写入：qualification_id = current_decision_id = 最新决定 ID；状态、有效期取自该决定；复审频率与下次复审时间为空；version 1。
--   状态变更：change_id = qualification_id，NULL → 当前状态，trigger_type BACKFILL，trigger_id = 决定 ID，aggregate_version 1，
--   occurred_time = 决定时间。线上逐次结论会形成多个版本，回填只保留最终状态一条（版本 1 必须 from_status 为空）。
-- 已有同组合投影的跳过；脚本可重复执行，重复执行不再插入。
-- AE001、PayFac Partner 与演示 Client 的资格不在此回填，阶段 7 经运营资格登记页手工登记（留审计）。
--
-- 回退（仅限回填后未再变更的行，即 version = 1）：
--   DELETE q FROM `t_role_qualification` q JOIN `t_qualification_state_change` s
--     ON s.`qualification_id` = q.`qualification_id` AND s.`trigger_type` = 'BACKFILL' WHERE q.`version` = 1;
--   DELETE FROM `t_qualification_state_change` WHERE `trigger_type` = 'BACKFILL';
SET time_zone = '+00:00';

START TRANSACTION;

INSERT INTO `t_role_qualification` (`qualification_id`, `role_type`, `role_id`, `qualification_type`, `current_decision_id`, `status`,
  `valid_from`, `valid_to`, `review_frequency_code`, `next_review_time`, `review_status`, `version`, `create_time`, `update_time`)
SELECT d.`decision_id`, 'CLIENT', d.`role_id`, d.`qualification_type`, d.`decision_id`, d.`outcome`,
  d.`valid_from`, d.`valid_to`, NULL, NULL, IF(d.`outcome` = 'APPROVED', 'NOT_REQUIRED', 'COMPLETED'), 1,
  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
  SELECT qd.*,
    ROW_NUMBER() OVER (PARTITION BY qd.`role_id`, qd.`qualification_type`
      ORDER BY qd.`decided_time` DESC, qd.`decision_id` DESC) AS `rn`,
    SUM(qd.`outcome` = 'APPROVED') OVER (PARTITION BY qd.`role_id`, qd.`qualification_type`) AS `approved_count`
  FROM `t_qualification_decision` qd
  JOIN `t_due_diligence_case` c ON c.`case_id` = qd.`case_id`
  WHERE qd.`role_type` = 'CLIENT' AND qd.`decision_source` = 'REVIEWER' AND qd.`decision_version` IS NULL
    AND qd.`outcome` IN ('APPROVED', 'REJECTED')
    AND c.`source_namespace` = 'CCT_CLIENT_APPLICATION' AND c.`role_type` = 'CLIENT'
) d
WHERE d.`rn` = 1
  AND (d.`outcome` = 'APPROVED' OR d.`approved_count` > 0)
  AND NOT EXISTS (SELECT 1 FROM `t_role_qualification` q
    WHERE q.`role_type` = 'CLIENT' AND q.`role_id` = d.`role_id` AND q.`qualification_type` = d.`qualification_type`);

INSERT INTO `t_qualification_state_change` (`change_id`, `qualification_id`, `from_status`, `to_status`, `decision_id`, `reason_code`,
  `trigger_type`, `trigger_id`, `actor_id`, `aggregate_version`, `occurred_time`, `create_time`)
SELECT q.`qualification_id`, q.`qualification_id`, NULL, q.`status`, q.`current_decision_id`, NULL,
  'BACKFILL', CAST(q.`current_decision_id` AS CHAR), NULL, 1, d.`decided_time`, UTC_TIMESTAMP(6)
FROM `t_role_qualification` q
JOIN `t_qualification_decision` d ON d.`decision_id` = q.`current_decision_id`
WHERE q.`role_type` = 'CLIENT' AND q.`version` = 1 AND q.`qualification_id` = q.`current_decision_id`
  AND d.`decision_source` = 'REVIEWER'
  AND NOT EXISTS (SELECT 1 FROM `t_qualification_state_change` s WHERE s.`qualification_id` = q.`qualification_id`);

COMMIT;

-- 执行后核对：
--   SELECT `status`, `review_status`, COUNT(*) FROM `t_role_qualification` WHERE `role_type` = 'CLIENT' GROUP BY 1, 2;
--   SELECT `to_status`, COUNT(*) FROM `t_qualification_state_change` WHERE `trigger_type` = 'BACKFILL' GROUP BY 1;

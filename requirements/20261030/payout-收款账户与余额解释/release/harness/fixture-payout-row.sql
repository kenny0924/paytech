-- 演练夹具：mor_payout 有 1 行数据时 91-mor_payout-ddl-rollback.sql 必须拒绝（只在断网演练容器执行）。
INSERT INTO t_beneficiary_account_event (id, beneficiary_account_id, event_no, from_status, to_status, action, reason_code, actor_type, actor_id,
  source_key, account_version_after, occurred_time, create_time)
VALUES (880000000901, 880000000902, 1, NULL, 'PENDING_REVIEW', 'SUBMIT', NULL, 'SYSTEM', 0, 'rehearsal-fixture', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

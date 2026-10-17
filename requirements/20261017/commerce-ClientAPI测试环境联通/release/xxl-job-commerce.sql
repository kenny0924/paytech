-- Client API 联通（2026-10-17）：mor-test XXL-Job 登记 commerce-service 执行器组与补偿扫描任务。
-- 设置与 platform-support-service（code-mor-87 登记，job id 2）一致：BEAN compensationEventScan、SHARDING_BROADCAST、
-- SERIAL_EXECUTION、cron */30 * * * * ?、超时 60 秒、失败重试 0；执行器自动注册（address_type=0）。
-- 执行前核对：SELECT COUNT(*) FROM xxl_job_group WHERE id=3 OR app_name='commerce-service';  -- 期望 0
--            SELECT COUNT(*) FROM xxl_job_info WHERE id=3;                                   -- 期望 0
-- 回滚：DELETE FROM xxl_job_info WHERE id=3; DELETE FROM xxl_job_group WHERE id=3;
INSERT INTO xxl_job_group (id, app_name, title, address_type, address_list, update_time)
VALUES (3, 'commerce-service', 'Commerce', 0, NULL, NOW());
INSERT INTO xxl_job_info (id, job_group, job_desc, add_time, update_time, author, alarm_email, schedule_type, schedule_conf,
  misfire_strategy, executor_route_strategy, executor_handler, executor_param, executor_block_strategy, executor_timeout,
  executor_fail_retry_count, glue_type, glue_source, glue_remark, glue_updatetime, child_jobid, trigger_status,
  trigger_last_time, trigger_next_time)
VALUES (3, 3, 'Commerce 本地补偿扫描（compensation_event）', NOW(), NOW(), 'code-mor', '', 'CRON', '*/30 * * * * ?',
  'DO_NOTHING', 'SHARDING_BROADCAST', 'compensationEventScan', '', 'SERIAL_EXECUTION', 60,
  0, 'BEAN', '', 'BEAN', NOW(), '', 1, 0, 0);

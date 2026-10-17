-- mor-test XXL-Job：修改默认管理员密码，登记 Platform 执行器分组与补偿扫描任务。只在 mor-test 的 xxl_job 库执行。
-- 以 xxl_job 账号连接，客户端须 utf8mb4。新密码经会话变量 @admin_password 传入（执行脚本从 .env.test 读取，不落文件、不打印）。
-- 可重复执行：分组与任务已存在时不重复插入。预期首次影响：user 1、group 1、info 1。
-- 回滚：DELETE FROM xxl_job_info WHERE executor_handler='compensationEventScan' AND job_group=(分组 id)；
--       DELETE FROM xxl_job_group WHERE app_name='platform-support-service'；管理员密码按备份恢复。
START TRANSACTION;
UPDATE xxl_job_user SET password = SHA2(@admin_password, 256) WHERE username = 'admin' AND CHAR_LENGTH(@admin_password) >= 32;
SELECT ROW_COUNT() AS admin_password;
INSERT INTO xxl_job_group (app_name, title, address_type, address_list, update_time)
SELECT 'platform-support-service', 'Platform Support', 0, NULL, UTC_TIMESTAMP()
 WHERE NOT EXISTS (SELECT 1 FROM xxl_job_group WHERE app_name = 'platform-support-service');
SELECT ROW_COUNT() AS executor_group;
SET @platform_group = (SELECT id FROM xxl_job_group WHERE app_name = 'platform-support-service');
INSERT INTO xxl_job_info (job_group, job_desc, add_time, update_time, author, alarm_email, schedule_type, schedule_conf,
  misfire_strategy, executor_route_strategy, executor_handler, executor_param, executor_block_strategy, executor_timeout,
  executor_fail_retry_count, glue_type, glue_source, glue_remark, glue_updatetime, child_jobid, trigger_status,
  trigger_last_time, trigger_next_time)
SELECT @platform_group, 'Platform 补偿任务扫描（compensation_event）', UTC_TIMESTAMP(), UTC_TIMESTAMP(), 'code-mor', '',
  'CRON', '*/30 * * * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST', 'compensationEventScan', '', 'SERIAL_EXECUTION', 60,
  0, 'BEAN', '', 'BEAN', UTC_TIMESTAMP(), '', 1, 0, 0
 WHERE NOT EXISTS (SELECT 1 FROM xxl_job_info WHERE job_group = @platform_group AND executor_handler = 'compensationEventScan');
SELECT ROW_COUNT() AS scan_job;
COMMIT;

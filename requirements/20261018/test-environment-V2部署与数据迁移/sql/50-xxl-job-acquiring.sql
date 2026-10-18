-- 50-xxl-job-acquiring.sql · XXL-Job：commerce-service 执行器与补偿扫描任务改由 acquiring-service 承接
-- 背景：mor-test 的 xxl_job 有执行器 commerce-service 与其 compensationEventScan 任务（BEAN、每 30 秒、分片广播、
--   单机串行、失败不重试，CURRENT_IMPLEMENTATION 2026-10-17 记录）。V2 中补偿事件随 mor_commerce 迁入 mor_acquiring，
--   compose 中 acquiring-service 以 XXL_JOB_EXECUTOR_APPNAME=acquiring-service 注册（platform-support-service 不变）。
-- 影响：
--   1) 新建执行器分组 acquiring-service（自动注册，地址由执行器心跳写入）；
--   2) 复制 commerce-service 分组下全部任务到新分组（同处理器、调度、路由、阻塞、超时、重试设置；描述改为 Acquiring），新任务先停（trigger_status=0），
--      acquiring-service 健康后由 runbook 在 Admin 页面启动；
--   3) 停止 commerce-service 分组下的任务（trigger_status=0），分组与任务保留供回滚，阶段 3 删除。
-- 不含：card-service（cardOrphanInstrumentPurge）、client-master-service、account-service 等执行器——compose 未给它们配置
--   XXL_JOB_* 环境变量，本次不登记（见报告待确认）。
-- 前置：commerce-service 分组存在；acquiring-service 分组不存在（scripts/migrate.sh xxl 步骤核对；不满足则跳过并提示）。
-- 回滚：UPDATE xxl_job_info i JOIN xxl_job_group g ON g.id = i.job_group SET i.trigger_status = 0 WHERE g.app_name = 'acquiring-service';
--       DELETE i FROM xxl_job_info i JOIN xxl_job_group g ON g.id = i.job_group WHERE g.app_name = 'acquiring-service';
--       DELETE FROM xxl_job_group WHERE app_name = 'acquiring-service';
--       然后在 Admin 页面重新启动 commerce-service 分组的任务。
SET NAMES utf8mb4;
USE `xxl_job`;
START TRANSACTION;
INSERT INTO `xxl_job_group` (`app_name`, `title`, `address_type`, `address_list`, `update_time`)
SELECT 'acquiring-service', 'acquiring-service', 0, NULL, NOW()
 WHERE NOT EXISTS (SELECT 1 FROM `xxl_job_group` WHERE `app_name` = 'acquiring-service');
SET @acquiring_group = (SELECT `id` FROM `xxl_job_group` WHERE `app_name` = 'acquiring-service');
INSERT INTO `xxl_job_info` (`job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`, `schedule_type`, `schedule_conf`,
  `misfire_strategy`, `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`,
  `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`, `trigger_status`,
  `trigger_last_time`, `trigger_next_time`)
SELECT @acquiring_group, 'Acquiring 本地补偿扫描（compensation_event，承接 commerce-service）', NOW(), NOW(), i.`author`, i.`alarm_email`, i.`schedule_type`, i.`schedule_conf`,
       i.`misfire_strategy`, i.`executor_route_strategy`, i.`executor_handler`, i.`executor_param`, i.`executor_block_strategy`, i.`executor_timeout`,
       i.`executor_fail_retry_count`, i.`glue_type`, i.`glue_source`, i.`glue_remark`, NOW(), i.`child_jobid`, 0, 0, 0
  FROM `xxl_job_info` i JOIN `xxl_job_group` g ON g.`id` = i.`job_group`
 WHERE g.`app_name` = 'commerce-service'
   AND NOT EXISTS (SELECT 1 FROM `xxl_job_info` x WHERE x.`job_group` = @acquiring_group AND x.`executor_handler` = i.`executor_handler`);
UPDATE `xxl_job_info` i JOIN `xxl_job_group` g ON g.`id` = i.`job_group`
   SET i.`trigger_status` = 0, i.`trigger_next_time` = 0, i.`update_time` = NOW()
 WHERE g.`app_name` = 'commerce-service';
COMMIT;

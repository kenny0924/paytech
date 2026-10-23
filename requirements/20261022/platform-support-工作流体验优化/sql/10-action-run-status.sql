-- 工作流体验优化 S1（技术方案 4.3）：动作运行状态迁移 VERIFIED → COMPLETED、UNVERIFIED → PENDING，
-- 并把 t_workflow_action_run 的表注释与 required、status 列注释同步为 platform-support-schema.sql / platform-support.pdma.json。
-- 待执行，需另行授权；审阅后由操作员手工执行，应用与测试不执行本文件。客户端须 utf8mb4。
--
-- 目标库参数：下方 @target_db。mor-test 为 mor_platform；本机 e2e 改为 mor_platform_v2e2e。库名不符时注释与数据都不变（0 行）。
-- 执行时机：在停止旧 Platform 与启动新 Platform 之间执行（停机窗口）。
--   旧 Platform 读到 COMPLETED、新 Platform 读到 VERIFIED / UNVERIFIED 都会因状态码未知而失败关闭，两边不能混跑。
-- 预检（只读）：
--   SELECT status, COUNT(*) FROM t_workflow_action_run GROUP BY status;   -- 记下 VERIFIED、UNVERIFIED 的行数 V、U
-- 第 1 步：列注释（DDL 隐式提交；只改注释，类型、可空性与数据不变，可重复执行）。
-- 第 2 步：数据迁移，期望影响行数 completed = V、pending = U。本文件不含 COMMIT：
--   · 试运行：执行后断开即回滚，只看两个数；
--   · 提交：核对两个数等于预检的 V、U 后在本文件后追加 COMMIT；guard 不符时报错，客户端停止，未提交的事务随断开回滚。
-- 执行后核对：SELECT status, COUNT(*) FROM t_workflow_action_run GROUP BY status;  只应有 PENDING / COMPLETED / CANCELLED；
--             SHOW CREATE TABLE t_workflow_action_run 与 platform-support-schema.sql 一致。
-- 回滚（先回退到旧 Platform 镜像，同样在停机窗口内）：
--   UPDATE t_workflow_action_run SET status = 'VERIFIED', version = version + 1, update_time = UTC_TIMESTAMP(6)
--    WHERE DATABASE() = @target_db AND status = 'COMPLETED';
--   原 UNVERIFIED 迁移后与 PENDING 无法区分，回滚后保持 PENDING（旧版同样允许执行人重新提交，不丢数据）；
--   注释回滚用旧注释重新执行第 1 步的 MODIFY：
--     表：'节点运行中一个动作的执行情况与最近一次提交的输出；以最后一次验证为准，验证通过后锁定，变量取自最新一轮 VERIFIED 的输出'
--     required：'1 表示必选，节点放行前必须 VERIFIED；0 表示可选，不阻塞放行。'
--     status：'PENDING 未提交；VERIFIED 最后一次验证通过且不再接受提交；UNVERIFIED 最后一次验证未通过；CANCELLED 节点被终止或结束时未完成。允许值：PENDING、VERIFIED、UNVERIFIED、CANCELLED。'
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';

-- 第 1 步：注释（库名不符时执行空语句）
SET @wf_comment_sql = IF(DATABASE() = @target_db,
  'ALTER TABLE `t_workflow_action_run`
     MODIFY COLUMN `required` TINYINT NOT NULL COMMENT ''1 表示必选，节点进入审批或放行前必须 COMPLETED；0 表示可选，不阻塞放行。'',
     MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT ''PENDING 待处理；COMPLETED 已完成；CANCELLED 节点结束或实例终止时仍未完成。允许值：PENDING、COMPLETED、CANCELLED。'',
     COMMENT = ''节点运行中一个动作的执行情况与最近一次完成的输出；必选动作在节点进入审批后锁定，变量取自最新一轮 COMPLETED 的输出''',
  'DO 0');
PREPARE wf_comment FROM @wf_comment_sql;
EXECUTE wf_comment;
DEALLOCATE PREPARE wf_comment;

-- 第 2 步：数据
START TRANSACTION;
SET @wf_time = UTC_TIMESTAMP(6);
SET @wf_verified = (SELECT COUNT(*) FROM t_workflow_action_run WHERE DATABASE() = @target_db AND status = 'VERIFIED');
SET @wf_unverified = (SELECT COUNT(*) FROM t_workflow_action_run WHERE DATABASE() = @target_db AND status = 'UNVERIFIED');
UPDATE t_workflow_action_run SET status = 'COMPLETED', version = version + 1, update_time = @wf_time
 WHERE DATABASE() = @target_db AND status = 'VERIFIED';
SET @wf_completed = ROW_COUNT();
UPDATE t_workflow_action_run SET status = 'PENDING', version = version + 1, update_time = @wf_time
 WHERE DATABASE() = @target_db AND status = 'UNVERIFIED';
SET @wf_pending = ROW_COUNT();
SELECT DATABASE() AS target, @wf_verified AS verified_before, @wf_completed AS completed,
       @wf_unverified AS unverified_before, @wf_pending AS pending,
       (SELECT COUNT(*) FROM t_workflow_action_run WHERE status IN ('VERIFIED', 'UNVERIFIED')) AS legacy_left_0;
-- guard：库名不符、影响行数与迁移前计数不一致或仍有旧状态时本语句报错（子查询多行），mysql 客户端停止，未提交的事务随断开回滚。
SELECT IF(DATABASE() = @target_db AND @wf_completed = @wf_verified AND @wf_pending = @wf_unverified
          AND (SELECT COUNT(*) FROM t_workflow_action_run WHERE status IN ('VERIFIED', 'UNVERIFIED')) = 0,
          'counts ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;

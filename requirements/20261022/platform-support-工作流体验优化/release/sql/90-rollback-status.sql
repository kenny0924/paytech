-- 工作流体验优化 · 回滚动作运行状态（内容取自 ../../sql/10-action-run-status.sql 文件头「回滚」段）。客户端须 utf8mb4。
-- 执行时机：只在 Platform 回退到旧镜像时，且必须在「停止新 Platform」与「启动旧 Platform」之间执行（停机窗口）：
--   旧 Platform 读到 COMPLETED 会因状态码未知而失败关闭，新 Platform 读到 VERIFIED 同样失败，两边不能混跑。
-- 迁移是有损的：原 UNVERIFIED 迁移后与 PENDING 无法区分，回滚后保持 PENDING（旧版同样允许执行人重新提交，不丢数据）。
--   新 Platform 运行期间完成的动作（COMPLETED）回滚为 VERIFIED。
-- 本文件不含 COMMIT：apply_sql.sh rollback-status-dry 断开即回滚；rollback-status 追加 COMMIT。guard 不符时报错停止。
-- 第 1 步注释为 DDL（隐式提交、可重复执行），dry 也会改注释；注释不影响任何代码行为。
SET NAMES utf8mb4;
SET @target_db = 'mor_platform';

SET @wf_comment_sql = IF(DATABASE() = @target_db,
  'ALTER TABLE `t_workflow_action_run`
     MODIFY COLUMN `required` TINYINT NOT NULL COMMENT ''1 表示必选，节点放行前必须 VERIFIED；0 表示可选，不阻塞放行。'',
     MODIFY COLUMN `status` VARCHAR(32) NOT NULL COMMENT ''PENDING 未提交；VERIFIED 最后一次验证通过且不再接受提交；UNVERIFIED 最后一次验证未通过；CANCELLED 节点被终止或结束时未完成。允许值：PENDING、VERIFIED、UNVERIFIED、CANCELLED。'',
     COMMENT = ''节点运行中一个动作的执行情况与最近一次提交的输出；以最后一次验证为准，验证通过后锁定，变量取自最新一轮 VERIFIED 的输出''',
  'DO 0');
PREPARE wf_comment FROM @wf_comment_sql;
EXECUTE wf_comment;
DEALLOCATE PREPARE wf_comment;

START TRANSACTION;
SET @wf_time = UTC_TIMESTAMP(6);
SET @wf_completed_before = (SELECT COUNT(*) FROM t_workflow_action_run WHERE DATABASE() = @target_db AND status = 'COMPLETED');
UPDATE t_workflow_action_run SET status = 'VERIFIED', version = version + 1, update_time = @wf_time
 WHERE DATABASE() = @target_db AND status = 'COMPLETED';
SET @wf_verified = ROW_COUNT();
SELECT DATABASE() AS target, @wf_completed_before AS completed_before, @wf_verified AS verified,
       (SELECT COUNT(*) FROM t_workflow_action_run WHERE status = 'COMPLETED') AS completed_left_0;
SELECT IF(DATABASE() = @target_db AND @wf_verified = @wf_completed_before
          AND (SELECT COUNT(*) FROM t_workflow_action_run WHERE status = 'COMPLETED') = 0,
          'counts ok', (SELECT 'mismatch' UNION ALL SELECT 'abort')) AS guard;

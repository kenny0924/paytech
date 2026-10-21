-- 统一对外渠道出口第一段 · 冻结确认（只读；阶段 7 第 4 步，导出前与导入核对后各执行一次，两次结果必须相同）。
-- 冻结 = 运营停止外部 API 配置写入、校验与沙箱运行；对账演示脚本（demo_seed / demo_cleanup 会增删 DEMO_NI 接入方）在窗口内不运行。
-- 期望：running_runs = 0；pending_compensation = 0；fingerprint 与上次执行完全相同（任何一项变化即说明仍有写入，停止迁移）。
SELECT
  (SELECT COUNT(*) FROM mor_platform.t_integration_verify_run WHERE run_status = 'RUNNING') AS running_runs,
  -- 补偿组件成功后删除记录（状态只有 INIT / PROCESSING / ERROR / FAILED），留存即未完成。
  (SELECT COUNT(*) FROM mor_platform.t_compensation_event WHERE event_type = 'INTEGRATION_VERIFY_RUN') AS pending_compensation,
  CONCAT_WS('|',
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_standard_api),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_api_field),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration_api),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration_script),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration_standard_api),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration_verify_account),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration_verify_case),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(update_time), '-')) FROM mor_platform.t_integration_verify_run),
    (SELECT CONCAT(COUNT(*), '@', IFNULL(MAX(create_time), '-')) FROM mor_platform.t_integration_verify_run_step)
  ) AS fingerprint;

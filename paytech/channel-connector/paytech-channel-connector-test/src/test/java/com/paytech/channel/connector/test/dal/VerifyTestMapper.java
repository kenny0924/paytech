package com.paytech.channel.connector.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 沙箱验证测试的只读检查与补偿任务回收；运行与步骤由 ChannelDataMocker 整表回收。 */
public interface VerifyTestMapper {

    /** 仍在执行或等待执行的验证运行补偿任务数。 */
    @Select("SELECT COUNT(*) FROM t_compensation_event WHERE event_type='INTEGRATION_VERIFY_RUN' AND execute_status IN ('INIT','PROCESSING')")
    int activeTasks();

    /** 仍为 RUNNING 的运行数。 */
    @Select("SELECT COUNT(*) FROM t_channel_verify_run WHERE run_status='RUNNING'")
    int runningRuns();

    /** 本例留下的验证运行补偿任务（终态失败等）。 */
    @Select("SELECT event_key eventKey, execute_status executeStatus, execute_count executeCount FROM t_compensation_event WHERE event_type='INTEGRATION_VERIFY_RUN'")
    List<Map<String, Object>> tasks();

    /** 回收验证运行补偿任务。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type='INTEGRATION_VERIFY_RUN'")
    int deleteTasks();

    /** 运行的步骤原始 JSON，用于核对落库的脱敏轨迹。 */
    @Select("SELECT trace_json FROM t_channel_verify_run_step WHERE verify_run_id=#{runId} ORDER BY step_no")
    List<String> traces(@Param("runId") long runId);
}

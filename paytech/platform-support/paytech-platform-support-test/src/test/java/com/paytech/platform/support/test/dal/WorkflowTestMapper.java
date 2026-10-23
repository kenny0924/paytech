package com.paytech.platform.support.test.dal;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 工作流十表与其补偿任务的只读断言、到期调整与清理；业务写入只来自被测链路。 */
public interface WorkflowTestMapper {

    /** 工作流补偿任务（邮件与回调发布）。 */
    @Select("""
        SELECT event_key, event_type, execute_status, execute_count, last_error_code FROM t_compensation_event
        WHERE event_type IN ('WORKFLOW_NOTIFICATION', 'WORKFLOW_CALLBACK_EVENT') ORDER BY id
        """)
    List<Map<String, Object>> events();

    /** 仍在执行中的工作流补偿任务数。 */
    @Select("""
        SELECT COUNT(*) FROM t_compensation_event
        WHERE event_type IN ('WORKFLOW_NOTIFICATION', 'WORKFLOW_CALLBACK_EVENT') AND execute_status IN ('INIT', 'PROCESSING')
        """)
    int activeEvents();

    /** 使等待重试的工作流任务立即到期；执行仍经过正式 Scanner。 */
    @Update("""
        UPDATE t_compensation_event SET next_execute_at = UTC_TIMESTAMP(6)
        WHERE event_type IN ('WORKFLOW_NOTIFICATION', 'WORKFLOW_CALLBACK_EVENT') AND execute_status = 'ERROR'
        """)
    int due();

    /** 删除工作流补偿任务。 */
    @Delete("DELETE FROM t_compensation_event WHERE event_type IN ('WORKFLOW_NOTIFICATION', 'WORKFLOW_CALLBACK_EVENT')")
    int deleteEvents();

    /** 实例的通知回调。 */
    @Select("""
        SELECT callback_id, node_run_id, trigger_code, callback_type, target, status, attempt_count, last_error, version
        FROM t_workflow_callback WHERE instance_id = #{instanceId} ORDER BY create_time, callback_id
        """)
    List<Map<String, Object>> callbacks(@Param("instanceId") long instanceId);

    /** 实例行。 */
    @Select("SELECT instance_id, status, version, end_time, terminated_by, termination_reason FROM t_workflow_instance WHERE instance_id = #{instanceId}")
    Map<String, Object> instance(@Param("instanceId") long instanceId);

    /** 实例的节点运行。 */
    @Select("""
        SELECT node_run_id, node_key, round_no, branch_key, previous_node_run_id, status, selected_edge_key
        FROM t_workflow_node_run WHERE instance_id = #{instanceId} ORDER BY activated_time, node_run_id
        """)
    List<Map<String, Object>> nodeRuns(@Param("instanceId") long instanceId);

    /** 实例的动作运行。 */
    @Select("""
        SELECT a.action_run_id, a.node_run_id, a.action_key, a.assignee_id, a.status, a.submitted_by, a.output_json,
            a.version, n.node_key, n.round_no
        FROM t_workflow_action_run a JOIN t_workflow_node_run n ON n.node_run_id = a.node_run_id
        WHERE a.instance_id = #{instanceId} ORDER BY n.activated_time, a.action_run_id
        """)
    List<Map<String, Object>> actionRuns(@Param("instanceId") long instanceId);

    /** 实例的审批行。 */
    @Select("""
        SELECT p.approval_id, p.approver_id, p.status, p.decision_comment, n.node_key, n.round_no
        FROM t_workflow_approval p JOIN t_workflow_node_run n ON n.node_run_id = p.node_run_id
        WHERE p.instance_id = #{instanceId} ORDER BY p.create_time, p.approval_id
        """)
    List<Map<String, Object>> approvals(@Param("instanceId") long instanceId);

    /** 实例总数。 */
    @Select("SELECT COUNT(*) FROM t_workflow_instance")
    int instanceCount();

    /**
     * 构造被测入口无法自然到达的数据：直接改写动作运行状态（如节点待审批时把必选动作改回 PENDING，
     * 验证同意时的兜底检查）。
     */
    @Update("UPDATE t_workflow_action_run SET status = #{status}, version = version + 1 WHERE action_run_id = #{id}")
    int forceActionStatus(@Param("id") long actionRunId, @Param("status") String status);

    /** 实例的历史验证记录数；2026-10-22 起完成动作不再写入。 */
    @Select("SELECT COUNT(*) FROM t_workflow_verification WHERE instance_id = #{instanceId}")
    int verificationCount(@Param("instanceId") long instanceId);

    /** 实例的转交记录数。 */
    @Select("SELECT COUNT(*) FROM t_workflow_transfer WHERE instance_id = #{instanceId}")
    int transferCount(@Param("instanceId") long instanceId);

    /** 定义行。 */
    @Select("SELECT definition_id, status, current_version_no, draft_version_no, version FROM t_workflow_definition WHERE definition_id = #{id}")
    Map<String, Object> definition(@Param("id") long id);

    /** 定义的版本行。 */
    @Select("""
        SELECT version_no, status, action_snapshot_json FROM t_workflow_definition_version
        WHERE definition_id = #{id} ORDER BY version_no
        """)
    List<Map<String, Object>> versions(@Param("id") long id);

    /** 模板行。 */
    @Select("SELECT action_template_id, template_code, status, version, config_json FROM t_workflow_action_template WHERE action_template_id = #{id}")
    Map<String, Object> template(@Param("id") long id);

    /** 工作流十表的总行数。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM t_workflow_definition) + (SELECT COUNT(*) FROM t_workflow_definition_version)
             + (SELECT COUNT(*) FROM t_workflow_action_template) + (SELECT COUNT(*) FROM t_workflow_instance)
             + (SELECT COUNT(*) FROM t_workflow_node_run) + (SELECT COUNT(*) FROM t_workflow_action_run)
             + (SELECT COUNT(*) FROM t_workflow_verification) + (SELECT COUNT(*) FROM t_workflow_approval)
             + (SELECT COUNT(*) FROM t_workflow_transfer) + (SELECT COUNT(*) FROM t_workflow_callback)
        """)
    long rowCount();
}

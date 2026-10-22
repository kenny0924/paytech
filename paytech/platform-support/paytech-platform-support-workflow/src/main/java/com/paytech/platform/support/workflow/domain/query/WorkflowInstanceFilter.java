package com.paytech.platform.support.workflow.domain.query;

import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 实例列表筛选条件；participantId 非空时只返回该账号参与的实例（发起、执行、审批或转交）。 */
@Getter
@AllArgsConstructor
public class WorkflowInstanceFilter {

    /** 流程定义；为空不限。 */
    private final Long definitionId;

    /** 实例状态；为空不限。 */
    private final WorkflowInstanceStatus status;

    /** 发起人；为空不限。 */
    private final Long startedBy;

    /** 业务关联键精确匹配；为空不限。 */
    private final String businessKey;

    /** 参与人；为空表示查看全部（调用方已确认 WORKFLOW_INSTANCE_READ）。 */
    private final Long participantId;

    /** 标题关键字（已转义 LIKE 通配符，转义符为 !）；为空不限。 */
    private final String titleKeyword;
}

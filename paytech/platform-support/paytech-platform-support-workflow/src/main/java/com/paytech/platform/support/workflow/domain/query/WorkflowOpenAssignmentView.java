package com.paytech.platform.support.workflow.domain.query;

import lombok.Getter;
import lombok.Setter;

/** 进行中节点的一位当前处理人：未完成动作的执行人（ACTIVE）或待决定的审批人（APPROVING）。 */
@Getter
@Setter
public class WorkflowOpenAssignmentView {

    /** 实例 ID。 */
    private Long instanceId;

    /** 节点 key。 */
    private String nodeKey;

    /** 节点运行状态。允许值：ACTIVE、APPROVING。 */
    private String nodeStatus;

    /** 处理人账号。 */
    private Long userId;
}

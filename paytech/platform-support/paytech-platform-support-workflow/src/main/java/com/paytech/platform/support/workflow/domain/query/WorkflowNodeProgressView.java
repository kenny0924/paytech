package com.paytech.platform.support.workflow.domain.query;

import lombok.Getter;
import lombok.Setter;

/** 实例列表进度计算用的 TASK 节点运行。 */
@Getter
@Setter
public class WorkflowNodeProgressView {

    /** 实例 ID。 */
    private Long instanceId;

    /** 节点 key。 */
    private String nodeKey;

    /** 轮次。 */
    private Integer roundNo;

    /** 节点运行状态。 */
    private String status;
}

package com.paytech.platform.support.api.workflow.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 实例当前进行中的一个节点及其处理人。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowCurrentNodeBody {

    /** 节点 key。 */
    private String nodeKey;

    /** 节点名称。 */
    private String nodeName;

    /** 节点运行状态：ACTIVE（执行中）或 APPROVING（待审批）。 */
    private String status;

    /** 当前处理人：未完成动作的执行人或待决定的审批人，去重。 */
    private List<WorkflowPersonBody> handlers;
}

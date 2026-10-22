package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 写接口的最小结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMutationResponseBody {

    /**
     * 对象类型
     * <li>WORKFLOW_ACTION_TEMPLATE、WORKFLOW_DEFINITION、WORKFLOW_INSTANCE、WORKFLOW_ACTION_RUN、WORKFLOW_APPROVAL、WORKFLOW_CALLBACK。</li>
     */
    private String objectType;

    /** 对象 ID（十进制字符串）。 */
    private String objectId;

    /** 保存后的状态 Code。 */
    private String status;

    /** 保存后的并发版本；没有版本概念时为空。 */
    private Integer version;
}

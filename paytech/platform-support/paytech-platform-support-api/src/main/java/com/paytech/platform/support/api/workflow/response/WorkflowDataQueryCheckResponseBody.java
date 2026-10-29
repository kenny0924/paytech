package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 立即检查的结果：检查后的动作与实例状态及最新检查块。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDataQueryCheckResponseBody {

    /** 动作运行 ID。 */
    private String actionRunId;

    /** 检查后的动作状态：PENDING、COMPLETED 或 CANCELLED。 */
    private String actionStatus;

    /** 实例状态：RUNNING、COMPLETED 或 TERMINATED。 */
    private String instanceStatus;

    /**
     * 已有检查在进行
     * <li>true 表示检查锁被占用，本次未执行查询，check 为当前已记录的状态。</li>
     */
    private boolean inProgress;

    /** 最新检查块。 */
    private WorkflowDataQueryCheckBody check;
}

package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 活动记录一条。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowActivityBody {

    /** 发生时间。 */
    private Instant time;

    /**
     * 类型
     * <li>NODE、SUBMISSION、APPROVAL、TRANSFER、NOTIFY（通知投递）、CALLBACK（业务回调）。</li>
     */
    private String activityType;

    /** 节点 key；可空。 */
    private String nodeKey;

    /** 节点轮次；可空。 */
    private Integer roundNo;

    /**
     * 该条记录的状态或结论
     * <li>SUBMISSION：动作运行当前状态（如 COMPLETED）；2026-10-22 前的历史验证记录为 PASSED / FAILED。</li>
     */
    private String status;

    /** 操作人；可空。 */
    private String actorId;

    /** 操作人显示名；可空。 */
    private String actorName;

    /** 补充说明：意见、原因、回调编码或投递错误；可空。 */
    private String detail;

    /** 对应记录 ID（如通知回调 ID，可用于手动重试）。 */
    private String referenceId;
}

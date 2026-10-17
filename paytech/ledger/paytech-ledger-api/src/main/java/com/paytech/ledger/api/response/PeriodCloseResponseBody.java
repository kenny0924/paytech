package com.paytech.ledger.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 期间关账流程摘要。 */
@Getter
@Setter
public class PeriodCloseResponseBody {
    /** 流程主键。 */
    private String periodCloseId;

    /** 会计期间主键。 */
    private String periodId;

    /** 期间内流程序号。 */
    private String closeNo;

    /**
     * 流程类型
     * <li>CLOSE 或 REOPEN。</li>
     */
    private String closeType;

    /**
     * 流程状态
     * <li>PENDING_CHECK、COMPLETED、REJECTED、FAILED 等。</li>
     */
    private String status;

    /** 编制人。 */
    private String makerId;

    /**
     * 复核人
     * <li>未复核时为空。</li>
     */
    private String checkerId;

    /**
     * 关账摘要
     * <li>PENDING_CHECK 起有值，FAILED 时为空。</li>
     */
    private String closeHash;

    /**
     * 首个失败步骤类型
     * <li>未失败时为空。</li>
     */
    private String failedStepType;

    /**
     * 重开所引用原关账流程的期间内序号
     * <li>仅 REOPEN 流程有值，CLOSE 流程为空。</li>
     */
    private String reopensCloseNo;

    /**
     * 重开原因代码
     * <li>仅 REOPEN 流程有值，CLOSE 流程为空。</li>
     */
    private String reopenReasonCode;

    /**
     * 重开申请编号
     * <li>仅 REOPEN 流程有值，CLOSE 流程为空。</li>
     */
    private String reopenRequestRef;

    /** 创建时间（UTC）。 */
    private Instant createTime;
}

package com.paytech.finance.api.accounting.response;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 期间关账流程详情：摘要、步骤、账簿证据与审批记录。 */
@Getter
@Setter
public class PeriodCloseDetailResponseBody {
    /** 流程主键。 */
    private String periodCloseId;

    /** 会计期间主键。 */
    private String periodId;

    /** 期间内流程序号。 */
    private String closeNo;

    /** 流程类型。 */
    private String closeType;

    /** 流程状态。 */
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
     * <li>PENDING_CHECK 起有值。</li>
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

    /** 按步骤号升序的步骤。 */
    private List<Step> steps;

    /** 按账簿主键升序的账簿证据。 */
    private List<Book> books;

    /** 审批记录。 */
    private List<Approval> approvals;

    /** 关账步骤。 */
    @Getter
    @Setter
    public static class Step {
        /** 步骤号，1—7。 */
        private Integer stepNo;

        /** 步骤类型。 */
        private String stepType;

        /** 步骤状态：PENDING、SUCCEEDED、FAILED 等。 */
        private String status;

        /**
         * 结果摘要
         * <li>未完成时为空。</li>
         */
        private String resultHash;

        /**
         * 完成时间（UTC）
         * <li>未完成时为空。</li>
         */
        private Instant completedTime;
    }

    /** 账簿证据。 */
    @Getter
    @Setter
    public static class Book {
        /** 账簿主键。 */
        private String ledgerBookId;

        /** 冻结的分录序号水位。 */
        private String asOfSequence;

        /**
         * 最终试算主键
         * <li>FINALIZED 前为空。</li>
         */
        private String finalTrialRunId;

        /**
         * 损益结转指令主键
         * <li>本期无收入费用时为空。</li>
         */
        private String postingInstructionId;

        /** 状态：LOCKED 或 FINALIZED。 */
        private String status;
    }

    /** 审批记录。 */
    @Getter
    @Setter
    public static class Approval {
        /** 角色：MAKER 或 CHECKER。 */
        private String approvalRole;

        /** 审批人。 */
        private String actorId;

        /** 决定：APPROVED 或 REJECTED。 */
        private String decision;

        /** 确认的关账摘要。 */
        private String approvedCloseHash;

        /** 决定时间（UTC）。 */
        private Instant decisionTime;
    }
}

package com.paytech.ledger.domain.view;

import java.time.Instant;
import java.util.List;

import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.CloseBookStatus;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.core.enums.CloseStepType;

import lombok.Getter;
import lombok.Setter;

/** 关账流程详情：摘要、步骤、账簿证据与审批记录的组合读取结果。 */
@Getter
@Setter
public class PeriodCloseDetailView {
    /** 流程摘要。 */
    private PeriodCloseView summary;

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
        /** 步骤号。 */
        private Integer stepNo;

        /** 步骤类型。 */
        private CloseStepType stepType;

        /** 步骤状态。 */
        private CloseStepStatus status;

        /**
         * 结果摘要
         * <li>未完成时为空。</li>
         */
        private String resultHash;

        /**
         * 完成时间
         * <li>未完成时为空。</li>
         */
        private Instant completedTime;
    }

    /** 账簿证据。 */
    @Getter
    @Setter
    public static class Book {
        /** 账簿主键。 */
        private Long ledgerBookId;

        /** 冻结的分录序号水位。 */
        private Long asOfSequence;

        /**
         * 最终试算主键
         * <li>FINALIZED 之前为空。</li>
         */
        private Long finalTrialRunId;

        /**
         * 损益结转指令主键
         * <li>本期无收入费用时为空。</li>
         */
        private Long postingInstructionId;

        /** 账簿证据状态。 */
        private CloseBookStatus status;
    }

    /** 审批记录。 */
    @Getter
    @Setter
    public static class Approval {
        /** 角色。 */
        private ApprovalRole approvalRole;

        /** 审批人。 */
        private Long actorId;

        /** 决定。 */
        private ApprovalDecision decision;

        /** 确认的关账摘要。 */
        private String approvedCloseHash;

        /** 决定时间。 */
        private Instant decisionTime;
    }
}

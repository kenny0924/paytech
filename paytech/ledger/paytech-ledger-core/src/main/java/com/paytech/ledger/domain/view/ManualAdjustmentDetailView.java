package com.paytech.ledger.domain.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.EntrySide;

import lombok.Getter;
import lombok.Setter;

/** 人工调整详情：调整头、明细、审批记录及过账结果的组合读取。 */
@Getter
@Setter
public class ManualAdjustmentDetailView {
    /** 调整主键。 */
    private Long manualAdjustmentId;

    /** 调整业务单号。 */
    private String adjustmentNo;

    /** 适用账簿主键。 */
    private Long ledgerBookId;

    /** 适用会计期间主键。 */
    private Long accountingPeriodId;

    /** 调整币种。 */
    private String currency;

    /** 调整原因代码。 */
    private String reasonCode;

    /**
     * Platform 证据文件主键
     * <li>可为空。</li>
     */
    private Long fileId;

    /**
     * 证据文件内容摘要
     * <li>与文件主键成对为空。</li>
     */
    private String fileContentHash;

    /** 借方合计。 */
    private BigDecimal totalDebit;

    /** 贷方合计。 */
    private BigDecimal totalCredit;

    /** 调整内容摘要。 */
    private String contentHash;

    /** 当前状态。 */
    private AdjustmentStatus status;

    /** 编制人。 */
    private Long makerId;

    /** 提交时间。 */
    private Instant submittedTime;

    /** 按行号升序的明细。 */
    private List<Line> lines;

    /** 按记录顺序的审批记录。 */
    private List<Approval> approvals;

    /**
     * 过账生成的分录主键
     * <li>未过账时为空。</li>
     */
    private Long journalEntryId;

    /**
     * 分录在账簿内的序号
     * <li>未过账时为空。</li>
     */
    private Long journalSequence;

    /**
     * 分录过账日期
     * <li>未过账时为空。</li>
     */
    private LocalDate postingDate;

    /** 调整明细。 */
    @Getter
    @Setter
    public static class Line {
        /** 行号。 */
        private Integer lineNo;

        /** 记账账户主键。 */
        private Long accountId;

        /** 借贷方向。 */
        private EntrySide entrySide;

        /** 金额。 */
        private BigDecimal amount;

        /** 业务说明。 */
        private String lineDescription;
    }

    /** 审批记录。 */
    @Getter
    @Setter
    public static class Approval {
        /** 审批角色。 */
        private ApprovalRole approvalRole;

        /** 审批人。 */
        private Long actorId;

        /** 审批决定。 */
        private ApprovalDecision decision;

        /** 审批确认的内容摘要。 */
        private String approvedContentHash;

        /** 决定时间。 */
        private Instant decisionTime;
    }
}

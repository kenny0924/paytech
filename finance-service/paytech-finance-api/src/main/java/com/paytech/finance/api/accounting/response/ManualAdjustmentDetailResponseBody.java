package com.paytech.finance.api.accounting.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 人工调整详情。 */
@Getter
@Setter
public class ManualAdjustmentDetailResponseBody {
    /**
     * 调整主键
     */
    private String manualAdjustmentId;

    /**
     * 调整业务单号
     */
    private String adjustmentNo;

    /**
     * 账簿主键
     */
    private String ledgerBookId;

    /**
     * 会计期间主键
     */
    private String accountingPeriodId;

    /**
     * 调整币种
     */
    private String currency;

    /**
     * 调整原因代码
     */
    private String reasonCode;

    /**
     * 证据文件主键
     * <li>可为空</li>
     */
    private String fileId;

    /**
     * 证据文件内容摘要
     * <li>可为空</li>
     */
    private String fileContentHash;

    /**
     * 借方合计
     * <li>按币种精度补齐小数位</li>
     */
    private String totalDebit;

    /**
     * 贷方合计
     * <li>按币种精度补齐小数位</li>
     */
    private String totalCredit;

    /**
     * 调整内容摘要
     */
    private String contentHash;

    /**
     * 调整状态
     * <li>SUBMITTED、REJECTED、POSTED 等</li>
     */
    private String status;

    /**
     * 编制人
     */
    private String makerId;

    /**
     * 提交时间（UTC）
     */
    private Instant submittedTime;

    /**
     * 按行号升序的明细
     */
    private List<Line> lines;

    /**
     * 审批记录
     */
    private List<Approval> approvals;

    /**
     * 过账分录主键
     * <li>未过账时为空</li>
     */
    private String journalEntryId;

    /**
     * 分录账簿序号
     * <li>未过账时为空</li>
     */
    private String journalSequence;

    /**
     * 过账日期
     * <li>未过账时为空</li>
     */
    private LocalDate postingDate;

    /** 调整明细。 */
    @Getter
    @Setter
    public static class Line {
        /** 行号。 */
        private Integer lineNo;

        /** 记账账户主键。 */
        private String accountId;

        /** 借贷方向：DEBIT 或 CREDIT。 */
        private String entrySide;

        /** 金额，按币种精度补齐小数位。 */
        private String amount;

        /** 业务说明。 */
        private String lineDescription;
    }

    /** 审批记录。 */
    @Getter
    @Setter
    public static class Approval {
        /** 审批角色：MAKER 或 CHECKER。 */
        private String approvalRole;

        /** 审批人。 */
        private String actorId;

        /** 决定：APPROVED 或 REJECTED。 */
        private String decision;

        /** 审批确认的内容摘要。 */
        private String approvedContentHash;

        /** 决定时间（UTC）。 */
        private Instant decisionTime;
    }
}

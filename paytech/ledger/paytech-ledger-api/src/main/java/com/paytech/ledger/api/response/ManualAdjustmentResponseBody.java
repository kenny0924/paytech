package com.paytech.ledger.api.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 人工调整摘要（列表元素）。 */
@Getter
@Setter
public class ManualAdjustmentResponseBody {
    /** 调整主键。 */
    private String manualAdjustmentId;

    /** 调整业务单号。 */
    private String adjustmentNo;

    /** 账簿主键。 */
    private String ledgerBookId;

    /** 会计期间主键。 */
    private String accountingPeriodId;

    /** 调整币种。 */
    private String currency;

    /** 调整原因代码。 */
    private String reasonCode;

    /** 调整状态：SUBMITTED、POSTED 或 REJECTED。 */
    private String status;

    /**
     * 借方合计
     * <li>按币种精度补齐小数位。</li>
     */
    private String totalDebit;

    /**
     * 贷方合计
     * <li>按币种精度补齐小数位。</li>
     */
    private String totalCredit;

    /** 编制人 Account ID。 */
    private String makerId;

    /** 提交时间（UTC）。 */
    private Instant submittedTime;

    /**
     * 过账分录主键
     * <li>未过账时为空。</li>
     */
    private String journalEntryId;
}

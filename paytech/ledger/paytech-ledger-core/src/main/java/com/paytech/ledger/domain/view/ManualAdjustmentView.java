package com.paytech.ledger.domain.view;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.ledger.core.enums.AdjustmentStatus;

import lombok.Getter;
import lombok.Setter;

/** 人工调整列表行：调整头与其过账分录主键（经人工指令关联）的组合读取。 */
@Getter
@Setter
public class ManualAdjustmentView {
    /** 调整主键。 */
    private Long manualAdjustmentId;

    /** 调整业务单号。 */
    private String adjustmentNo;

    /** 账簿主键。 */
    private Long ledgerBookId;

    /** 会计期间主键。 */
    private Long accountingPeriodId;

    /** 调整币种。 */
    private String currency;

    /** 调整原因代码。 */
    private String reasonCode;

    /** 调整状态。 */
    private AdjustmentStatus status;

    /** 借方合计；查询后按币种精度补齐。 */
    private BigDecimal totalDebit;

    /** 贷方合计；查询后按币种精度补齐。 */
    private BigDecimal totalCredit;

    /** 编制人。 */
    private Long makerId;

    /** 提交时间。 */
    private Instant submittedTime;

    /**
     * 过账分录主键
     * <li>未过账时为空。</li>
     */
    private Long journalEntryId;
}

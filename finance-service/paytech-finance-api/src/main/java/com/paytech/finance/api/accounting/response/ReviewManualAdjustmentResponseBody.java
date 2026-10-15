package com.paytech.finance.api.accounting.response;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/** 复核人工调整结果。 */
@Getter
@Setter
public class ReviewManualAdjustmentResponseBody {
    /**
     * 调整主键
     */
    private String manualAdjustmentId;

    /**
     * 调整状态
     * <li>批准后为 POSTED，驳回后为 REJECTED</li>
     */
    private String status;

    /**
     * 过账分录主键
     * <li>驳回时为空</li>
     */
    private String journalEntryId;

    /**
     * 分录在账簿内的序号
     * <li>驳回时为空</li>
     */
    private String journalSequence;

    /**
     * 分录过账日期
     * <li>驳回时为空</li>
     */
    private LocalDate postingDate;
}

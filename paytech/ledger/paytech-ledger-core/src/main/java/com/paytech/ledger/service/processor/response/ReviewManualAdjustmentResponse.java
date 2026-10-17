package com.paytech.ledger.service.processor.response;

import java.time.LocalDate;

import com.paytech.ledger.core.enums.AdjustmentStatus;

import lombok.Getter;
import lombok.Setter;

/** 复核人工调整的结果。 */
@Getter
@Setter
public class ReviewManualAdjustmentResponse {
    /** 调整主键 */
    private Long manualAdjustmentId;

    /** 当前状态 */
    private AdjustmentStatus status;

    /** 过账分录主键，驳回时为空 */
    private Long journalEntryId;

    /** 分录账簿序号，驳回时为空 */
    private Long journalSequence;

    /** 过账日期，驳回时为空 */
    private LocalDate postingDate;
}

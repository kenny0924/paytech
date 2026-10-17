package com.paytech.ledger.service.processor.response;

import com.paytech.ledger.core.enums.AdjustmentStatus;

import lombok.Getter;
import lombok.Setter;

/** 提交人工调整的结果。 */
@Getter
@Setter
public class SubmitManualAdjustmentResponse {
    /** 调整主键 */
    private Long manualAdjustmentId;

    /** 调整业务单号 */
    private String adjustmentNo;

    /** 内容摘要，复核时需原样确认 */
    private String contentHash;

    /** 当前状态 */
    private AdjustmentStatus status;
}

package com.paytech.finance.api.accounting.response;

import lombok.Getter;
import lombok.Setter;

/** 提交人工调整结果。 */
@Getter
@Setter
public class SubmitManualAdjustmentResponseBody {
    /**
     * 调整主键
     */
    private String manualAdjustmentId;

    /**
     * 调整业务单号
     */
    private String adjustmentNo;

    /**
     * 调整内容摘要
     * <li>复核时须原样确认</li>
     */
    private String contentHash;

    /**
     * 调整状态
     * <li>提交成功为 SUBMITTED</li>
     */
    private String status;
}

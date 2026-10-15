package com.paytech.finance.accounting.domain;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.AdjustmentApproval;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustment;
import com.paytech.finance.accounting.core.dal.dataobject.ManualAdjustmentLine;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交人工调整：调整头、冻结明细与 Maker 审批记录一起保存。 */
@Getter
@AllArgsConstructor
public class SubmitManualAdjustmentContext {
    /** 状态为 SUBMITTED 且已计算内容摘要的调整头。 */
    private final ManualAdjustment adjustment;

    /** 调整明细。 */
    private final List<ManualAdjustmentLine> lines;

    /** Maker 对同一内容摘要的提交记录。 */
    private final AdjustmentApproval makerApproval;
}

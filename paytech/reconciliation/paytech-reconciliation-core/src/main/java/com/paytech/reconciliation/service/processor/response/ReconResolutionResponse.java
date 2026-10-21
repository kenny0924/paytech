package com.paytech.reconciliation.service.processor.response;

import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 处置提交或复核结果：处置与所属异常（币种用于金额补齐）。 */
@Getter
@AllArgsConstructor
public class ReconResolutionResponse {
    /** 处置 */
    private final ReconResolution resolution;

    /** 所属异常 */
    private final ReconException exception;
}

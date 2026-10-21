package com.paytech.reconciliation.domain.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.paytech.reconciliation.api.enums.ReconClassification;
import com.paytech.reconciliation.api.enums.ResolutionType;
import com.paytech.reconciliation.core.dal.dataobject.ReconException;
import com.paytech.reconciliation.core.dal.dataobject.ReconResolution;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账异常详情（#25）：异常、来源对象摘要、当前可用处置与处置历史，组合异常、处置与来源对象各表。 */
@Getter
@AllArgsConstructor
public class ReconExceptionDetailView {
    /** 异常 */
    private final ReconException exception;

    /** 来源对象摘要 */
    private final SourceSummary source;

    /** 当前可提交的处置方式；异常不是 OPEN 时为空 */
    private final List<ResolutionType> availableResolutionTypes;

    /** 当前可选归类码；不允许 CLASSIFY 时为空 */
    private final List<ReconClassification> availableClassificationCodes;

    /** 全部处置，按序号升序 */
    private final List<ReconResolution> resolutions;

    /** 来源对象的关键字段。 */
    @Getter
    @AllArgsConstructor
    public static class SourceSummary {
        /** 可读引用 */
        private final String reference;

        /** 方向 Code；账单与对账单为空 */
        private final String direction;

        /** 对象金额；对账单为空 */
        private final BigDecimal amount;

        /** 对象当前状态 Code */
        private final String status;

        /** 业务日期，可为空 */
        private final LocalDate businessDate;
    }
}

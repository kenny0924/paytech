package com.paytech.reconciliation.api.exception.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 对账异常详情：异常、来源对象摘要、可用处置与处置历史。 */
@Getter
@Setter
public class ReconExceptionDetailResponseBody {
    /** 异常 */
    private ReconExceptionResponseBody exception;

    /** 来源对象摘要 */
    private SourceSummary source;

    /**
     * 当前可提交的处置方式
     * <li>异常不是 OPEN 时为空列表。</li>
     */
    private List<String> availableResolutionTypes;

    /**
     * 当前可选的归类码
     * <li>按来源对象限定；不允许 CLASSIFY 时为空列表。</li>
     */
    private List<String> availableClassificationCodes;

    /** 全部处置，按序号升序 */
    private List<ReconResolutionResponseBody> resolutions;

    /** 来源对象的关键字段，供页面识别。 */
    @Getter
    @Setter
    public static class SourceSummary {
        /** 来源对象类型 */
        private String sourceType;

        /** 来源对象主键 */
        private String sourceId;

        /**
         * 可读引用
         * <li>账单 / 账单行为渠道批次号与行号，平台流水为外部交易号，银行流水为银行交易号，预期银行流水为来源类型与来源 ID。</li>
         */
        private String reference;

        /** 方向（RECEIVABLE / PAYABLE 或 CREDIT / DEBIT）；账单与对账单为空 */
        private String direction;

        /** 对象金额，按币种精度补齐；对账单为空 */
        private String amount;

        /** 对象当前状态（账单状态、匹配状态等） */
        private String status;

        /** 业务日期：结算日、起息日、预期日或业务发生日；可为空 */
        private LocalDate businessDate;
    }
}

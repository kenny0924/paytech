package com.paytech.commercial.api.agreement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 开通项及其约束。 */
@Getter
@Setter
public class AgreementCapabilityDetail {
    /** 开通项 ID */
    private String agreementCapabilityId;

    /** 准确 OE */
    private String entityNo;

    /** 可开通项代码 */
    private String capabilityCode;

    /** 状态：ENABLED、SUSPENDED、DISABLED */
    private String status;

    /** 生效起点（含），UTC ISO 8601 */
    private String effectiveFrom;

    /** 生效终点（不含），可空 */
    private String effectiveTo;

    /** 滚动保证金比例，可空 */
    private String rollingReserveRate;

    /** 滚动保证金天数，可空 */
    private Integer rollingReserveDays;

    /** 约束行，按 limitCode、limitValue 排序 */
    private List<CapabilityLimitDetail> limits;
}

package com.paytech.commercial.api.agreement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 开通项及其接受条件。 */
@Getter
@Setter
public class AgreementCapabilityDetail {
    /** 开通项 ID */
    private String agreementCapabilityId;

    /** 准确 OE */
    private String entityNo;

    /** 可开通项代码 */
    private String capabilityCode;

    /** 合同成员资格：ACTIVE、DISABLED */
    private String status;

    /**
     * 执行状态：PENDING_PREREQUISITES、ENABLED、SUSPENDED_BY_PREREQUISITE、SUSPENDED_MANUALLY
     * <li>成员资格 DISABLED 时为空。</li>
     */
    private String executionStatus;

    /** 生效起点（含），UTC ISO 8601 */
    private String effectiveFrom;

    /** 生效终点（不含），可空 */
    private String effectiveTo;

    /** 滚动保证金比例，可空 */
    private String rollingReserveRate;

    /** 滚动保证金天数，可空 */
    private Integer rollingReserveDays;

    /** 接受条件，按维度代码排序 */
    private List<CapabilityConditionDetail> conditions;

    /** 是否要求 3DS 认证 */
    private Boolean require3ds;
}

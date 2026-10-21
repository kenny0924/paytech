package com.paytech.commercial.api.catalog.response;

import lombok.Getter;
import lombok.Setter;

/** 可开通项的一个可配维度。 */
@Getter
@Setter
public class CapabilityDimensionDetail {
    /** 维度代码 */
    private String dimensionCode;

    /** 合同开通时是否必填 */
    private Boolean required;
}

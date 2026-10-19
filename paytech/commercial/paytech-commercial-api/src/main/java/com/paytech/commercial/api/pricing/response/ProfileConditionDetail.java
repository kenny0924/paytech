package com.paytech.commercial.api.pricing.response;

import lombok.Getter;
import lombok.Setter;

/** 定价画像的一个等值条件。 */
@Getter
@Setter
public class ProfileConditionDetail {
    /** 定价维度 */
    private String dimensionCode;

    /** 规范化值 */
    private String conditionValue;
}

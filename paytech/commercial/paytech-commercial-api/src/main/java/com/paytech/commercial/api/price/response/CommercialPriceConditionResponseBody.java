package com.paytech.commercial.api.price.response;

import lombok.Getter;
import lombok.Setter;

/** 画像的一条离散等值条件；同维度多值按 OR，不同维度按 AND。 */
@Getter
@Setter
public class CommercialPriceConditionResponseBody {
    /** 维度代码（PricingDimension） */
    private String dimensionCode;

    /** 规范化条件值 */
    private String conditionValue;
}

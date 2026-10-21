package com.paytech.commercial.api.agreement.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 开通项的一个接受条件：同维度多值按 OR，异维度按 AND。 */
@Getter
@Setter
public class CapabilityConditionDetail {
    /** 维度代码，如 CURRENCY、CARD_BRAND */
    private String dimensionCode;

    /** 取值，按字典序 */
    private List<String> values;
}

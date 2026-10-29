package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 自动承保判定的一项检查。 */
@Getter
@Setter
public class UnderwritingCheckBody {

    /**
     * 检查编码
     * <li>RULE_CONFIGURED、CRR_LOW、VOLUME_PROVIDED、CURRENCY_CONFIGURED、VOLUME_WITHIN_THRESHOLD、
     * MCC_NOT_NETWORK_HIGH_RISK、MCC_NOT_EXCLUDED、CLASSIFICATION_CONFIRMED。</li>
     */
    private String code;

    /** 是否通过。 */
    private Boolean passed;
}

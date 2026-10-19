package com.paytech.commercial.api.price.response;

import lombok.Getter;
import lombok.Setter;

/** 阶梯档位：区间 [lowerBound, upperBound)。 */
@Getter
@Setter
public class CommercialPriceBandResponseBody {
    /** 档位 ID */
    private String bandId;

    /** 档位序号，从 1 连续 */
    private Integer bandNo;

    /** 包含下界 */
    private String lowerBound;

    /** 不包含上界；末档为空 */
    private String upperBound;

    /** 档位费率 */
    private String rate;

    /** 档位固定金额 */
    private String fixedAmount;

    /** 继承组件的币种 */
    private String currency;
}

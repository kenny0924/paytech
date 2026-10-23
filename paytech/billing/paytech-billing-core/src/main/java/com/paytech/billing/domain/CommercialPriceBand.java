package com.paytech.billing.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 阶梯档位：区间 [lowerBound, upperBound)，upperBound 为空表示无上限。 */
@Getter
@AllArgsConstructor
public class CommercialPriceBand {
    /** 档位 ID，证据 */
    private final long bandId;

    /** 档位序号 */
    private final int bandNo;

    /** 包含下界 */
    private final BigDecimal lowerBound;

    /**
     * 不包含上界
     * <li>末档为空。</li>
     */
    private final BigDecimal upperBound;

    /**
     * 档位费率
     * <li>可空。</li>
     */
    private final BigDecimal rate;

    /**
     * 档位固定金额
     * <li>可空；币种为所属组件币种。</li>
     */
    private final BigDecimal fixedAmount;

    /** 基数是否落在本档：下界含、上界不含。 */
    public boolean contains(BigDecimal basis) {
        return basis.compareTo(lowerBound) >= 0 && (upperBound == null || basis.compareTo(upperBound) < 0);
    }
}

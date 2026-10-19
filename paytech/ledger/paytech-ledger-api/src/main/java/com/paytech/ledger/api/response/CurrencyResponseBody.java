package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 币种列表项。 */
@Getter
@Setter
public class CurrencyResponseBody {
    /** ISO 4217 币种代码。 */
    private String currency;

    /**
     * 最小单位小数位数
     * <li>例如 USD 为 2，JPY 为 0，KWD 为 3。</li>
     */
    private Integer minorUnit;

    /**
     * 舍入方式代码
     * <li>当前只有 HALF_UP。</li>
     */
    private String roundingMode;

    /**
     * 币种状态
     * <li>DRAFT、ACTIVE 或 INACTIVE；只有 ACTIVE 币种可用于新建账簿。</li>
     */
    private String status;
}

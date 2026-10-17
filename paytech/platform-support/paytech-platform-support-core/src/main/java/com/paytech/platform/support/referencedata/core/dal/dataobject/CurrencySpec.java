package com.paytech.platform.support.referencedata.core.dal.dataobject;

import java.time.Instant;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import lombok.Getter;
import lombok.Setter;

/** t_currency_spec 映射对象；只在 referencedata 模块内使用。 */
@Getter
@Setter
public class CurrencySpec {

    /** 币种规则 Snowflake 主键。 */
    private Long id;

    /** ISO 4217 三位币种代码。 */
    private String currency;

    /**
     * 最小货币单位位数
     * <li>0..8。</li>
     */
    private Integer minorUnit;

    /** 舍入方式。 */
    private CurrencyRoundingMode roundingMode;

    /** 状态。 */
    private CurrencySpecStatus status;

    /** 创建时间（UTC）。 */
    private Instant createTime;

    /** 最后更新时间（UTC）。 */
    private Instant updateTime;
}

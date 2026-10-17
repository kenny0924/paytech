package com.paytech.platform.support.referencedata.service.processor.response;

import java.time.Instant;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 跨模块发布的币种规则结果；不暴露 DataObject。 */
@Getter
@AllArgsConstructor
public class CurrencySpecResult {

    /** ISO 4217 代码。 */
    private final String currency;

    /** 最小货币单位位数。 */
    private final int minorUnit;

    /** 舍入方式。 */
    private final CurrencyRoundingMode roundingMode;

    /** 状态。 */
    private final CurrencySpecStatus status;

    /** 最后更新时间（UTC）。 */
    private final Instant updateTime;

    /** 由本模块 DataObject 构造。 */
    public static CurrencySpecResult of(CurrencySpec spec) {
        return new CurrencySpecResult(spec.getCurrency(), spec.getMinorUnit(), spec.getRoundingMode(),
            spec.getStatus(), spec.getUpdateTime());
    }
}

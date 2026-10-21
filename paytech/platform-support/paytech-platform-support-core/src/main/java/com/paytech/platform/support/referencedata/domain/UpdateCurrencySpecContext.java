package com.paytech.platform.support.referencedata.domain;

import java.time.Instant;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 修改 DRAFT 币种规则的本地保存上下文。 */
@Getter
@AllArgsConstructor
public class UpdateCurrencySpecContext {

    /** 已锁定并核对版本的 DRAFT 规则。 */
    private final CurrencySpec spec;

    /** 目标小数位。 */
    private final int minorUnit;

    /** 目标舍入方式。 */
    private final CurrencyRoundingMode roundingMode;

    /** 本次 UTC 微秒时间。 */
    private final Instant updateTime;

    /** 小数位与舍入方式都未变化时不写库、不递增版本。 */
    public boolean unchanged() {
        return spec.getMinorUnit() == minorUnit && spec.getRoundingMode() == roundingMode;
    }
}

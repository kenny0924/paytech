package com.paytech.platform.support.referencedata.domain;

import java.time.Instant;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 切换币种状态的本地保存上下文。 */
@Getter
@AllArgsConstructor
public class ChangeCurrencySpecStatusContext {

    /** 已锁定并核对版本的币种。 */
    private final ReferenceCurrency currency;

    /** 目标状态。 */
    private final CurrencySpecStatus status;

    /** 本次 UTC 微秒时间。 */
    private final Instant updateTime;

    /**
     * 是否为允许的状态转换
     * <li>只允许 DRAFT→ACTIVE、ACTIVE→INACTIVE、INACTIVE→ACTIVE；DRAFT 从未生效，不能直接停用。</li>
     */
    public boolean transitionAllowed() {
        return switch (currency.getStatus()) {
            case DRAFT, INACTIVE -> status == CurrencySpecStatus.ACTIVE;
            case ACTIVE -> status == CurrencySpecStatus.INACTIVE;
        };
    }
}

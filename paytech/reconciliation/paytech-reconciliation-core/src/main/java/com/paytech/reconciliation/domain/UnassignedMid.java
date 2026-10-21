package com.paytech.reconciliation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个已激活但尚未归入清算账户的 MID + 币种。 */
@Getter
@AllArgsConstructor
public class UnassignedMid {
    /** MID 事实 */
    private final MidProfile mid;

    /** 能力组中尚未归属的币种 */
    private final String currency;
}

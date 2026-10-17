package com.paytech.ledger.test.mocker.local;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个 OE 币种账簿及其日历和当前开放期间的测试前置身份。 */
@Getter
@AllArgsConstructor
public class LedgerFixture {
    /** 账簿主键。 */
    private final long ledgerBookId;

    /** 主体财务日历主键。 */
    private final long fiscalCalendarId;

    /** 覆盖今天的 OPEN 期间主键。 */
    private final long openPeriodId;

    /** 账簿主体标识：OE 为 entityNo，Finance Entity 为主键十进制字符串。 */
    private final String entityNo;

    /** 账簿币种。 */
    private final String currency;
}

package com.paytech.finance.auto.ext.http.accounting.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.finance.accounting.core.dal.mapper.AccountingPeriodMapper;
import com.paytech.finance.accounting.core.enums.LedgerEntityType;
import com.paytech.finance.accounting.core.enums.PeriodStatus;
import com.paytech.finance.api.accounting.AccountingPeriodCommandApi;
import com.paytech.finance.test.base.MockTestBase;

/**
 * 开放会计期间：FUTURE→OPEN、已 OPEN 幂等返回、其他状态拒绝。
 *
 * @see AccountingPeriodCommandApi#openAccountingPeriod(String)
 */
class OpenAccountingPeriodTest extends MockTestBase {
    private static final LocalDate JAN_1 = LocalDate.of(2027, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2027, 1, 31);

    @Autowired
    private AccountingPeriodMapper accountingPeriodMapper;

    private long calendarId;

    @BeforeEach
    void setUpClassMocks() {
        calendarId = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE001");
    }

    @Test
    void test_futurePeriodOpened() {
        long periodId = ledgerMocker.mockPeriod(calendarId, 2027, 1, JAN_1, JAN_31, PeriodStatus.FUTURE);

        post(path(periodId), null).assertSuccess(200).assertText("status", PeriodStatus.OPEN.getCode());

        assertEquals(PeriodStatus.OPEN, accountingPeriodMapper.selectById(periodId).getStatus());
        assertEquals(1L, accountingPeriodMapper.selectById(periodId).getVersion());
    }

    @Test
    void test_alreadyOpenReturnsCurrentState() {
        long periodId = ledgerMocker.mockPeriod(calendarId, 2027, 1, JAN_1, JAN_31, PeriodStatus.OPEN);

        post(path(periodId), null).assertSuccess(200).assertText("status", PeriodStatus.OPEN.getCode());

        assertEquals(0L, accountingPeriodMapper.selectById(periodId).getVersion());
    }

    @Test
    void test_closedPeriodRejected() {
        long periodId = ledgerMocker.mockPeriod(calendarId, 2027, 1, JAN_1, JAN_31, PeriodStatus.CLOSED);

        post(path(periodId), null).assertBusinessRejected("只有 FUTURE 期间可以开放");
    }

    @Test
    void test_unknownPeriodRejected() {
        post(path(123456789L), null).assertBusinessRejected("会计期间不存在");
    }

    /** 开放期间路径。 */
    String path(long periodId) {
        return "/accounting-periods/v1/" + periodId + "/open";
    }
}

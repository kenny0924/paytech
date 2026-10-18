package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.api.FiscalCalendarCommandApi;
import com.paytech.ledger.api.request.CreateAccountingPeriodRequestBody;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.mapper.AccountingPeriodMapper;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;

/**
 * 创建会计期间：FUTURE 初始状态、日期不重叠、年度期号唯一和日历资源锁。
 *
 * @see FiscalCalendarCommandApi#createAccountingPeriod(String, CreateAccountingPeriodRequestBody)
 */
class CreateAccountingPeriodTest extends MockTestBase {
    private static final LocalDate JAN_1 = LocalDate.of(2027, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2027, 1, 31);

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private AccountingPeriodMapper accountingPeriodMapper;

    private long calendarId;

    @BeforeEach
    void setUpClassMocks() {
        calendarId = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE001");
    }

    @Test
    void test_normalFlowCreatesFuturePeriod() {
        var response = post(path(), AccountingSetupRequestBuilder.period(2027, 1, JAN_1, JAN_31)).assertSuccess(201);

        AccountingPeriod period = accountingPeriodMapper.selectById(response.id("accountingPeriodId"));
        assertAll(() -> assertEquals(calendarId, period.getCalendarId()),
            () -> assertEquals(PeriodStatus.FUTURE, period.getStatus()), () -> assertEquals(JAN_1, period.getStartDate()),
            () -> assertEquals(JAN_31, period.getEndDate()), () -> assertEquals(0, period.getReopenCount()));
    }

    @Test
    void test_overlappingDatesRejected() {
        ledgerMocker.mockPeriod(calendarId, 2027, 1, JAN_1, JAN_31, PeriodStatus.FUTURE);

        post(path(), AccountingSetupRequestBuilder.period(2027, 2, JAN_31, LocalDate.of(2027, 2, 28)))
            .assertBusinessRejected("会计期间日期与已有期间重叠");
    }

    @Test
    void test_duplicateYearAndPeriodNoRejected() {
        ledgerMocker.mockPeriod(calendarId, 2027, 1, JAN_1, JAN_31, PeriodStatus.FUTURE);

        post(path(), AccountingSetupRequestBuilder.period(2027, 1, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)))
            .assertBusinessRejected("会计期间已存在");
    }

    @Test
    void test_endBeforeStartRejected() {
        post(path(), AccountingSetupRequestBuilder.period(2027, 1, JAN_31, JAN_1)).assertInvalidRequest()
            .assertDetailContains("期间开始日期不能晚于结束日期");
    }

    @Test
    void test_unknownCalendarRejected() {
        post("/fiscal-calendars/v1/123456789/periods", AccountingSetupRequestBuilder.period(2027, 1, JAN_1, JAN_31))
            .assertBusinessRejected("财务日历不存在");
    }

    @Test
    void test_calendarLockHeldReturnsConflict() {
        RLock lock = redissonClient.getSpinLock("lock:fiscal-calendar:" + calendarId);
        lock.lock();
        try {
            post(path(), AccountingSetupRequestBuilder.period(2027, 1, JAN_1, JAN_31)).assertConcurrentRequest();
        } finally {
            lock.unlock();
        }
    }

    /** 本类日历的期间路径。 */
    String path() {
        return "/fiscal-calendars/v1/" + calendarId + "/periods";
    }
}

package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.test.base.MockTestBase;

import tools.jackson.databind.JsonNode;

/**
 * 日历下会计期间列表：财年过滤、财年与期号排序、日历归属、日历不存在与参数校验。
 *
 * @see LedgerSetupQueryApi#listAccountingPeriods(String, Integer, Integer, Integer)
 */
class ListAccountingPeriodsTest extends MockTestBase {
    private long calendarId;

    private long jan2027;

    @BeforeEach
    void setUpClassMocks() {
        calendarId = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE001");
        ledgerMocker.mockPeriod(calendarId, 2027, 2, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28),
            PeriodStatus.FUTURE);
        jan2027 = ledgerMocker.mockPeriod(calendarId, 2027, 1, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31),
            PeriodStatus.OPEN);
        ledgerMocker.mockPeriod(calendarId, 2026, 12, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31),
            PeriodStatus.CLOSED);
        long other = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "SA001");
        ledgerMocker.mockPeriod(other, 2027, 1, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31),
            PeriodStatus.FUTURE);
    }

    @Test
    void test_sortByFiscalYearAndPeriodNo() {
        List<JsonNode> periods = get(path(calendarId)).assertSuccess(200).assertPage(1, 10, 3);

        assertEquals(List.of("2026-12", "2027-1", "2027-2"),
            periods.stream().map(item -> item.path("fiscalYear").asInt() + "-" + item.path("periodNo").asInt()).toList());
        JsonNode jan = periods.get(1);
        assertAll(() -> assertEquals(String.valueOf(jan2027), jan.path("accountingPeriodId").asString()),
            () -> assertEquals(String.valueOf(calendarId), jan.path("fiscalCalendarId").asString()),
            () -> assertEquals("2027-01-01", jan.path("startDate").asString()),
            () -> assertEquals("2027-01-31", jan.path("endDate").asString()),
            () -> assertEquals(PeriodStatus.OPEN.getCode(), jan.path("status").asString()),
            () -> assertEquals(0, jan.path("reopenCount").asInt()));
    }

    @Test
    void test_filterByFiscalYearAndPaging() {
        get(path(calendarId) + "?fiscalYear=2027").assertSuccess(200).assertPage(1, 10, 2);
        get(path(calendarId) + "?fiscalYear=2030").assertSuccess(200).assertPage(1, 10, 0);
        List<JsonNode> second = get(path(calendarId) + "?page=2&size=10").assertSuccess(200).assertPage(2, 10, 3);
        assertEquals(0, second.size());
    }

    @Test
    void test_unknownCalendarRejected() {
        get(path(123456789L)).assertRejected(LedgerSetupErrorCode.FISCAL_CALENDAR_NOT_FOUND);
    }

    @Test
    void test_invalidParameter() {
        get(path(calendarId) + "?size=7").assertInvalidRequest();
        get("/fiscal-calendars/v1/abc/periods").assertStatus(400);
    }

    /** 日历下期间列表路径。 */
    String path(long fiscalCalendarId) {
        return "/fiscal-calendars/v1/" + fiscalCalendarId + "/periods";
    }
}

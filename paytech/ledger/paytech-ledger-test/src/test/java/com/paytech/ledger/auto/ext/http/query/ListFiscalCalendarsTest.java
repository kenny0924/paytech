package com.paytech.ledger.auto.ext.http.query;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.mocker.local.LedgerMocker;

import tools.jackson.databind.JsonNode;

/**
 * 财务日历列表：主体类型与主体过滤、字段投影、主键升序、参数校验。
 *
 * @see LedgerSetupQueryApi#listFiscalCalendars(String, String, Integer, Integer)
 */
class ListFiscalCalendarsTest extends MockTestBase {
    private static final String PATH = "/fiscal-calendars/v1";

    private long aeCalendar;

    private long saCalendar;

    private long feCalendar;

    private String financeEntityId;

    @BeforeEach
    void setUpClassMocks() {
        aeCalendar = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE001");
        saCalendar = ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "SA001");
        financeEntityId = String.valueOf(ledgerMocker.mockFinanceEntity("FE-PLATFORM", 880001L,
            FinanceEntityStatus.ACTIVE));
        feCalendar = ledgerMocker.mockCalendar(LedgerEntityType.FINANCE_ENTITY, financeEntityId);
    }

    @Test
    void test_filterByEntity() {
        List<JsonNode> all = get(PATH).assertSuccess(200).assertPage(1, 10, 3);
        assertEquals(List.of(aeCalendar, saCalendar, feCalendar),
            all.stream().map(item -> Long.parseLong(item.path("fiscalCalendarId").asString())).toList(), "按主键升序");

        get(PATH + "?entityType=OPERATING_ENTITY").assertSuccess(200).assertPage(1, 10, 2);
        JsonNode ae = get(PATH + "?entityType=OPERATING_ENTITY&entityId=AE001").assertSuccess(200)
            .assertPage(1, 10, 1).getFirst();
        assertAll(() -> assertEquals(String.valueOf(aeCalendar), ae.path("fiscalCalendarId").asString()),
            () -> assertEquals(LedgerEntityType.OPERATING_ENTITY.getCode(), ae.path("entityType").asString()),
            () -> assertEquals("AE001", ae.path("entityId").asString()),
            () -> assertEquals("CAL-AE001", ae.path("calendarCode").asString()),
            () -> assertEquals(LedgerMocker.TIMEZONE, ae.path("timezone").asString()),
            () -> assertEquals(1, ae.path("yearStartMonth").asInt()));

        JsonNode fe = get(PATH + "?entityType=FINANCE_ENTITY").assertSuccess(200).assertPage(1, 10, 1).getFirst();
        assertEquals(financeEntityId, fe.path("entityId").asString());
        get(PATH + "?entityId=ZZ001").assertSuccess(200).assertPage(1, 10, 0);
    }

    @Test
    void test_invalidParameter() {
        get(PATH + "?size=7").assertInvalidRequest();
        get(PATH + "?entityType=CLIENT").assertStatus(400);
    }
}

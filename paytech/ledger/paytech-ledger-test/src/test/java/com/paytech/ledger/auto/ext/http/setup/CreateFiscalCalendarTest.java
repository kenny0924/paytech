package com.paytech.ledger.auto.ext.http.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.api.FiscalCalendarCommandApi;
import com.paytech.ledger.api.request.CreateFiscalCalendarRequestBody;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.mapper.FiscalCalendarMapper;
import com.paytech.ledger.core.enums.FinanceEntityStatus;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.MockTestBase;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;

/**
 * 创建财务日历：成功、主体唯一与时区/月份校验。
 *
 * @see FiscalCalendarCommandApi#createFiscalCalendar(CreateFiscalCalendarRequestBody)
 */
class CreateFiscalCalendarTest extends MockTestBase {
    private static final String PATH = "/fiscal-calendars/v1";

    @Autowired
    private FiscalCalendarMapper fiscalCalendarMapper;

    @Test
    void test_normalFlow() {
        var response = post(PATH, AccountingSetupRequestBuilder.fiscalCalendar(
            LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001")).assertSuccess(201);

        FiscalCalendar calendar = fiscalCalendarMapper.selectById(response.id("fiscalCalendarId"));
        assertAll(() -> assertEquals(LedgerEntityType.OPERATING_ENTITY, calendar.getEntityType()),
            () -> assertEquals("AE001", calendar.getEntityId()), () -> assertEquals("Asia/Dubai", calendar.getTimezone()),
            () -> assertEquals(1, calendar.getYearStartMonth()));
    }

    @Test
    void test_financeEntityCalendarRequiresActiveEntity() {
        long active = ledgerMocker.mockFinanceEntity("FE-ACTIVE", 880001L, FinanceEntityStatus.ACTIVE);
        String type = LedgerEntityType.FINANCE_ENTITY.getCode();

        post(PATH, AccountingSetupRequestBuilder.fiscalCalendar(type, String.valueOf(active))).assertSuccess(201);
        post(PATH, AccountingSetupRequestBuilder.fiscalCalendar(type, "123456789"))
            .assertBusinessRejected("财务主体不存在或已关闭");
    }

    @Test
    void test_duplicateCalendarRejected() {
        ledgerMocker.mockCalendar(LedgerEntityType.OPERATING_ENTITY, "AE001");

        post(PATH, AccountingSetupRequestBuilder.fiscalCalendar(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001"))
            .assertBusinessRejected("财务日历已存在");
    }

    @Test
    void test_invalidTimezone() {
        var body = AccountingSetupRequestBuilder.fiscalCalendar(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        body.setTimezone("Mars/Base");

        post(PATH, body).assertInvalidRequest().assertDetailContains("时区不是合法的 IANA 代码");
    }

    @Test
    void test_invalidYearStartMonth() {
        var body = AccountingSetupRequestBuilder.fiscalCalendar(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        body.setYearStartMonth(13);

        post(PATH, body).assertProblem(422, "VALIDATION_ERROR").assertFieldError("yearStartMonth");
    }
}

package com.paytech.ledger.test.builder;

import java.time.LocalDate;

import com.paytech.ledger.api.request.CreateAccountRequestBody;
import com.paytech.ledger.api.request.CreateAccountingPeriodRequestBody;
import com.paytech.ledger.api.request.CreateFinanceEntityRequestBody;
import com.paytech.ledger.api.request.CreateFiscalCalendarRequestBody;
import com.paytech.ledger.api.request.CreateLedgerBookRequestBody;

/** 会计基础资料请求的合法模板；返回可修改的正式 RequestBody，用例按场景覆盖字段。 */
public final class AccountingSetupRequestBuilder {

    private AccountingSetupRequestBuilder() {
    }

    /** 财务主体模板。 */
    public static CreateFinanceEntityRequestBody financeEntity(String entityCode, String legalPartyId) {
        CreateFinanceEntityRequestBody body = new CreateFinanceEntityRequestBody();
        body.setEntityCode(entityCode);
        body.setLegalPartyId(legalPartyId);
        body.setDisplayName("Platform " + entityCode);
        return body;
    }

    /** USD、STANDARD 科目表的账簿模板。 */
    public static CreateLedgerBookRequestBody ledgerBook(String entityType, String entityId) {
        CreateLedgerBookRequestBody body = new CreateLedgerBookRequestBody();
        body.setEntityType(entityType);
        body.setEntityId(entityId);
        body.setCurrency("USD");
        body.setChartCode("STANDARD");
        return body;
    }

    /** 账户模板。 */
    public static CreateAccountRequestBody account(String accountCode, String ownerType, String ownerId,
        String balanceBucket) {
        CreateAccountRequestBody body = new CreateAccountRequestBody();
        body.setAccountCode(accountCode);
        body.setOwnerType(ownerType);
        body.setOwnerId(ownerId);
        body.setBalanceBucket(balanceBucket);
        return body;
    }

    /** Asia/Dubai、1 月起始的财务日历模板。 */
    public static CreateFiscalCalendarRequestBody fiscalCalendar(String entityType, String entityId) {
        CreateFiscalCalendarRequestBody body = new CreateFiscalCalendarRequestBody();
        body.setEntityType(entityType);
        body.setEntityId(entityId);
        body.setCalendarCode("CAL-" + entityId);
        body.setTimezone("Asia/Dubai");
        body.setYearStartMonth(1);
        return body;
    }

    /** 会计期间模板。 */
    public static CreateAccountingPeriodRequestBody period(int fiscalYear, int periodNo, LocalDate start,
        LocalDate end) {
        CreateAccountingPeriodRequestBody body = new CreateAccountingPeriodRequestBody();
        body.setFiscalYear(fiscalYear);
        body.setPeriodNo(periodNo);
        body.setStartDate(start);
        body.setEndDate(end);
        return body;
    }
}

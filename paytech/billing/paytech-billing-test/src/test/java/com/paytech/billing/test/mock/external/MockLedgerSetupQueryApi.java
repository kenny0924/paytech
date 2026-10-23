package com.paytech.billing.test.mock.external;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.billing.test.mocker.external.FinanceEntityMocker;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.response.AccountingPeriodResponseBody;
import com.paytech.ledger.api.response.ChartAccountChangeResponseBody;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.ChartOfAccountsListResponseBody;
import com.paytech.ledger.api.response.FinanceEntityResponseBody;
import com.paytech.ledger.api.response.FiscalCalendarResponseBody;

import lombok.AllArgsConstructor;

/** 在独立 Mock 服务继承 ledger 正式账务基础资料查询合同；billing 只调用按 Party 解析 Finance Entity，其余查询记为意外。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockLedgerSetupQueryApi implements LedgerSetupQueryApi {
    private final FinanceEntityMocker mocker;

    @Override
    public FinanceEntityResponseBody getFinanceEntityByParty(String legalPartyId) {
        MockCurrencySpecApi.markRequestId();
        return mocker.getByParty(legalPartyId);
    }

    @Override
    public PagingResponseBody<FinanceEntityResponseBody> listFinanceEntities(String status, Integer page, Integer size) {
        MockCurrencySpecApi.markRequestId();
        throw mocker.unexpectedCall("listFinanceEntities");
    }

    @Override
    public PagingResponseBody<FiscalCalendarResponseBody> listFiscalCalendars(String entityType, String entityId,
        Integer page, Integer size) {
        MockCurrencySpecApi.markRequestId();
        throw mocker.unexpectedCall("listFiscalCalendars");
    }

    @Override
    public PagingResponseBody<AccountingPeriodResponseBody> listAccountingPeriods(String fiscalCalendarId,
        Integer fiscalYear, Integer page, Integer size) {
        MockCurrencySpecApi.markRequestId();
        throw mocker.unexpectedCall("listAccountingPeriods");
    }

    @Override
    public ChartOfAccountsListResponseBody listChartsOfAccounts() {
        MockCurrencySpecApi.markRequestId();
        throw mocker.unexpectedCall("listChartsOfAccounts");
    }

    @Override
    public ChartAccountListResponseBody listChartAccounts(String chartCode, Boolean postingAllowed,
        String accountStatus) {
        MockCurrencySpecApi.markRequestId();
        throw mocker.unexpectedCall("listChartAccounts");
    }

    @Override
    public PagingResponseBody<ChartAccountChangeResponseBody> listChartAccountChanges(String chartCode,
        String chartAccountId, Integer page, Integer size) {
        MockCurrencySpecApi.markRequestId();
        throw mocker.unexpectedCall("listChartAccountChanges");
    }
}

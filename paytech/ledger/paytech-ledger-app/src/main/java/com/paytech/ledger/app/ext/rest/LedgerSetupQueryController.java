package com.paytech.ledger.app.ext.rest;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.response.AccountingPeriodResponseBody;
import com.paytech.ledger.api.response.ChartAccountChangeResponseBody;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.ChartOfAccountsListResponseBody;
import com.paytech.ledger.api.response.CurrencyListResponseBody;
import com.paytech.ledger.api.response.FinanceEntityResponseBody;
import com.paytech.ledger.api.response.FiscalCalendarResponseBody;
import com.paytech.ledger.app.ext.rest.converter.ChartOfAccountsConverter;
import com.paytech.ledger.app.ext.rest.converter.LedgerQueryConverter;
import com.paytech.ledger.app.ext.rest.converter.LedgerReadConverter;
import com.paytech.ledger.domainservice.service.LedgerQueryService;

import lombok.AllArgsConstructor;

/** 账务基础资料只读查询的 HTTP 协议适配；简单只读查询直接委托 LedgerQueryService，不加锁、不开事务。 */
@RestController
@AllArgsConstructor
public class LedgerSetupQueryController implements LedgerSetupQueryApi {
    private final LedgerQueryService ledgerQueryService;
    private final LedgerQueryConverter converter;
    private final LedgerReadConverter readConverter;
    private final ChartOfAccountsConverter chartConverter;

    /** 分页查询财务主体。 */
    @Override
    public PagingResponseBody<FinanceEntityResponseBody> listFinanceEntities(String status, Integer page,
        Integer size) {
        return ledgerQueryService.pageFinanceEntities(converter.toFinanceEntityQuery(status, page, size))
            .map(converter::toResponseBody);
    }

    /** 分页查询财务日历。 */
    @Override
    public PagingResponseBody<FiscalCalendarResponseBody> listFiscalCalendars(String entityType, String entityId,
        Integer page, Integer size) {
        return ledgerQueryService.pageFiscalCalendars(converter.toFiscalCalendarQuery(entityType, entityId, page, size))
            .map(converter::toResponseBody);
    }

    /** 分页查询日历下的会计期间。 */
    @Override
    public PagingResponseBody<AccountingPeriodResponseBody> listAccountingPeriods(String fiscalCalendarId,
        Integer fiscalYear, Integer page, Integer size) {
        return ledgerQueryService.pageAccountingPeriods(
            converter.toAccountingPeriodQuery(fiscalCalendarId, fiscalYear, page, size)).map(converter::toResponseBody);
    }

    /** 查询全部科目表。 */
    @Override
    public ChartOfAccountsListResponseBody listChartsOfAccounts() {
        return converter.toChartsResponseBody(ledgerQueryService.listChartsOfAccounts());
    }

    /** 查询科目表下的科目，复用科目批量读取的响应转换。 */
    @Override
    public ChartAccountListResponseBody listChartAccounts(String chartCode, Boolean postingAllowed,
        String accountStatus) {
        return readConverter.toResponseBody(ledgerQueryService.listChartAccountsByChart(chartCode, postingAllowed,
            converter.toChartAccountStatus(accountStatus)));
    }

    /** 分页查询科目变更记录。 */
    @Override
    public PagingResponseBody<ChartAccountChangeResponseBody> listChartAccountChanges(String chartCode,
        String chartAccountId, Integer page, Integer size) {
        return ledgerQueryService.pageChartAccountChanges(chartCode,
            chartAccountId == null ? null : Long.valueOf(chartAccountId), PagingRequestBody.of(page, size))
            .map(chartConverter::toResponseBody);
    }

    /** 查询币种规则。 */
    @Override
    public CurrencyListResponseBody listCurrencies(String status) {
        return converter.toCurrenciesResponseBody(ledgerQueryService.listCurrencies(converter.toCurrencyStatus(status)));
    }
}

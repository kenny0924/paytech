package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireIdentity;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.requireSame;
import static com.paytech.operations.console.bff.integration.impl.FinanceContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.AccountingPeriodCommandApi;
import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.FinanceEntityCommandApi;
import com.paytech.ledger.api.FiscalCalendarCommandApi;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.operations.console.bff.integration.FinanceSetupClient;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.AccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountChangeResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartOfAccountsListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateAccountingPeriodRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateAccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateChartAccountRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateChartAccountResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFinanceEntityRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFinanceEntityResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFiscalCalendarRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFiscalCalendarResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.FinanceEntityResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.FiscalCalendarResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.OpenAccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.UpdateChartAccountRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/**
 * ledger 账务基础资料与科目维护的 HTTP 转发：每个入口只调用一次 ledger，不重试
 * <li>命令把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；不转发浏览器 Authorization、Cookie 或 Idempotency-Key。</li>
 * <li>业务拒绝由 ledger 以 LedgerSetupErrorCode 返回 400，经 Foundation 客户端原样转换。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class FinanceSetupClientImpl implements FinanceSetupClient {
    private final LedgerSetupQueryApi queryApi;
    private final FinanceEntityCommandApi financeEntityApi;
    private final FiscalCalendarCommandApi fiscalCalendarApi;
    private final AccountingPeriodCommandApi accountingPeriodApi;
    private final ChartOfAccountsCommandApi chartOfAccountsApi;

    /** 财务主体分页，核对分页回执。 */
    @Override
    public PagingResponseBody<FinanceEntityResponseBody> listFinanceEntities(String status, int page, int size) {
        return page(queryApi.listFinanceEntities(status, page, size), page, size, FinanceEntityResponseBody.class);
    }

    /** 新建财务主体；回执须带新主键。 */
    @Override
    public CreateFinanceEntityResponseBody createFinanceEntity(OperatorContext context,
        CreateFinanceEntityRequestBody request) {
        var created = write(request, () -> financeEntityApi.createFinanceEntity(context.accountId(),
            context.sessionId(), convert(request, com.paytech.ledger.api.request.CreateFinanceEntityRequestBody.class))
            .getBody(), CreateFinanceEntityResponseBody.class);
        requireIdentity(created.getFinanceEntityId());
        return created;
    }

    /** 财务日历分页，核对分页回执。 */
    @Override
    public PagingResponseBody<FiscalCalendarResponseBody> listFiscalCalendars(String entityType, String entityId,
        int page, int size) {
        return page(queryApi.listFiscalCalendars(entityType, entityId, page, size), page, size,
            FiscalCalendarResponseBody.class);
    }

    /** 新建财务日历；回执须带新主键。 */
    @Override
    public CreateFiscalCalendarResponseBody createFiscalCalendar(OperatorContext context,
        CreateFiscalCalendarRequestBody request) {
        var created = write(request, () -> fiscalCalendarApi.createFiscalCalendar(context.accountId(),
            context.sessionId(), convert(request, com.paytech.ledger.api.request.CreateFiscalCalendarRequestBody.class))
            .getBody(), CreateFiscalCalendarResponseBody.class);
        requireIdentity(created.getFiscalCalendarId());
        return created;
    }

    /** 会计期间分页，核对分页回执与所属日历。 */
    @Override
    public PagingResponseBody<AccountingPeriodResponseBody> listAccountingPeriods(String fiscalCalendarId,
        Integer fiscalYear, int page, int size) {
        var periods = page(queryApi.listAccountingPeriods(fiscalCalendarId, fiscalYear, page, size), page, size,
            AccountingPeriodResponseBody.class);
        periods.items().forEach(period -> requireSame(fiscalCalendarId, period.getFiscalCalendarId()));
        return periods;
    }

    /** 新建会计期间；回执须带新主键。 */
    @Override
    public CreateAccountingPeriodResponseBody createAccountingPeriod(OperatorContext context, String fiscalCalendarId,
        CreateAccountingPeriodRequestBody request) {
        var created = write(request, () -> fiscalCalendarApi.createAccountingPeriod(context.accountId(),
            context.sessionId(), fiscalCalendarId,
            convert(request, com.paytech.ledger.api.request.CreateAccountingPeriodRequestBody.class)).getBody(),
            CreateAccountingPeriodResponseBody.class);
        requireIdentity(created.getAccountingPeriodId());
        return created;
    }

    /** 开放会计期间，无请求体；回执期间主键须与请求一致。 */
    @Override
    public OpenAccountingPeriodResponseBody openAccountingPeriod(OperatorContext context, String accountingPeriodId) {
        var opened = convert(accountingPeriodApi.openAccountingPeriod(context.accountId(), context.sessionId(),
            accountingPeriodId), OpenAccountingPeriodResponseBody.class);
        requireSame(accountingPeriodId, opened.getAccountingPeriodId());
        return opened;
    }

    /** 全部科目表。 */
    @Override
    public ChartOfAccountsListResponseBody listChartsOfAccounts() {
        var charts = convert(queryApi.listChartsOfAccounts(), ChartOfAccountsListResponseBody.class);
        FinanceContractConverter.require(charts.getItems() != null);
        return charts;
    }

    /** 科目表下的科目。 */
    @Override
    public ChartAccountListResponseBody listChartAccounts(String chartCode, Boolean postingAllowed,
        String accountStatus) {
        var accounts = convert(queryApi.listChartAccounts(chartCode, postingAllowed, accountStatus),
            ChartAccountListResponseBody.class);
        FinanceContractConverter.require(accounts.getItems() != null);
        return accounts;
    }

    /** 新增科目；回执须带新主键。 */
    @Override
    public CreateChartAccountResponseBody createChartAccount(OperatorContext context, String chartCode,
        CreateChartAccountRequestBody request) {
        var created = write(request, () -> chartOfAccountsApi.createChartAccount(context.accountId(),
            context.sessionId(), chartCode,
            convert(request, com.paytech.ledger.api.request.CreateChartAccountRequestBody.class)).getBody(),
            CreateChartAccountResponseBody.class);
        requireIdentity(created.getChartAccountId());
        return created;
    }

    /** 修改科目；回执科目主键须与请求一致。 */
    @Override
    public ChartAccountResponseBody updateChartAccount(OperatorContext context, String chartCode,
        String chartAccountId, UpdateChartAccountRequestBody request) {
        var updated = write(request, () -> chartOfAccountsApi.updateChartAccount(context.accountId(),
            context.sessionId(), chartCode, chartAccountId,
            convert(request, com.paytech.ledger.api.request.UpdateChartAccountRequestBody.class)),
            ChartAccountResponseBody.class);
        requireSame(chartAccountId, updated.getChartAccountId());
        return updated;
    }

    /** 停用科目，无请求体；回执科目主键须与请求一致。 */
    @Override
    public ChartAccountResponseBody disableChartAccount(OperatorContext context, String chartCode,
        String chartAccountId) {
        var disabled = convert(chartOfAccountsApi.disableChartAccount(context.accountId(), context.sessionId(),
            chartCode, chartAccountId), ChartAccountResponseBody.class);
        requireSame(chartAccountId, disabled.getChartAccountId());
        return disabled;
    }

    /** 启用科目，无请求体；回执科目主键须与请求一致。 */
    @Override
    public ChartAccountResponseBody enableChartAccount(OperatorContext context, String chartCode,
        String chartAccountId) {
        var enabled = convert(chartOfAccountsApi.enableChartAccount(context.accountId(), context.sessionId(),
            chartCode, chartAccountId), ChartAccountResponseBody.class);
        requireSame(chartAccountId, enabled.getChartAccountId());
        return enabled;
    }

    /** 科目变更记录分页，核对分页回执；指定科目时每条记录须属于该科目。 */
    @Override
    public PagingResponseBody<ChartAccountChangeResponseBody> listChartAccountChanges(String chartCode,
        String chartAccountId, int page, int size) {
        var changes = page(queryApi.listChartAccountChanges(chartCode, chartAccountId, page, size), page, size,
            ChartAccountChangeResponseBody.class);
        if (chartAccountId != null) {
            changes.items().forEach(change -> requireSame(chartAccountId, change.getChartAccountId()));
        }
        return changes;
    }
}

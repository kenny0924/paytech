package com.paytech.operations.console.bff.ext.rest;

import static com.paytech.operations.console.bff.ext.rest.RiskListController.requireQueryKeys;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.FinanceSetupClient;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.AccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartAccountListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.ChartOfAccountsListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateAccountingPeriodRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateAccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFinanceEntityRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFinanceEntityResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFiscalCalendarRequestBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CreateFiscalCalendarResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.CurrencyListResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.FinanceEntityResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.FiscalCalendarResponseBody;
import com.paytech.operations.console.bff.service.model.FinanceSetupModels.OpenAccountingPeriodResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 账务基础资料的浏览器入口：财务主体、财务日历、会计期间、科目表与币种，全部简单转发 ledger
 * <li>每个路由由 Gateway 绑定 FINANCE_ACCOUNTING_READ（GET）或 FINANCE_ACCOUNTING_MANAGE（POST），并经 PLT-I02 实时授权。</li>
 * <li>浏览器路径为 /finance + ledger 路径；Query 按路由白名单严格匹配，GET 与开放期间不接受 Body。</li>
 * <li>财务数据不含个人原值，不加入元数据日志前缀。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/finance")
public class FinanceSetupController {
    private static final String ID = "[1-9][0-9]{0,18}";
    private static final String CHART_CODE = "[A-Z][A-Z0-9_]{0,63}";
    private static final String ENTITY_TYPE = "OPERATING_ENTITY|FINANCE_ENTITY";

    private final FinanceSetupClient client;

    /** R01 财务主体分页，按主键升序。 */
    @GetMapping("/finance-entities/v1")
    public PagingResponseBody<FinanceEntityResponseBody> listFinanceEntities(HttpServletRequest servletRequest,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "ACTIVE|CLOSED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("status", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listFinanceEntities(status, paging.page(), paging.size());
    }

    /** R02 新建 ACTIVE 财务主体。 */
    @PostMapping("/finance-entities/v1")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateFinanceEntityResponseBody createFinanceEntity(HttpServletRequest servletRequest,
        OperatorContext context, @RequestBody @NotNull @Valid CreateFinanceEntityRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createFinanceEntity(context, body);
    }

    /** R03 财务日历分页，按主键升序。 */
    @GetMapping("/fiscal-calendars/v1")
    public PagingResponseBody<FiscalCalendarResponseBody> listFiscalCalendars(HttpServletRequest servletRequest,
        @RequestParam(value = "entityType", required = false) @Pattern(regexp = ENTITY_TYPE) String entityType,
        @RequestParam(value = "entityId", required = false) @Size(max = 64) String entityId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("entityType", "entityId", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listFiscalCalendars(entityType, entityId, paging.page(), paging.size());
    }

    /** R04 为主体新建唯一的财务日历。 */
    @PostMapping("/fiscal-calendars/v1")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateFiscalCalendarResponseBody createFiscalCalendar(HttpServletRequest servletRequest,
        OperatorContext context, @RequestBody @NotNull @Valid CreateFiscalCalendarRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createFiscalCalendar(context, body);
    }

    /** R05 日历下的会计期间分页，按财年、期号升序。 */
    @GetMapping("/fiscal-calendars/v1/{fiscalCalendarId}/periods")
    public PagingResponseBody<AccountingPeriodResponseBody> listAccountingPeriods(HttpServletRequest servletRequest,
        @PathVariable("fiscalCalendarId") @Pattern(regexp = ID) String fiscalCalendarId,
        @RequestParam(value = "fiscalYear", required = false) Integer fiscalYear,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        requireQueryKeys(servletRequest, Set.of("fiscalYear", "page", "size"));
        PagingRequestBody paging = PagingRequestBody.of(page, size);
        return client.listAccountingPeriods(fiscalCalendarId, fiscalYear, paging.page(), paging.size());
    }

    /** R06 在日历下新建 FUTURE 会计期间。 */
    @PostMapping("/fiscal-calendars/v1/{fiscalCalendarId}/periods")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateAccountingPeriodResponseBody createAccountingPeriod(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("fiscalCalendarId") @Pattern(regexp = ID) String fiscalCalendarId,
        @RequestBody @NotNull @Valid CreateAccountingPeriodRequestBody body) {
        requireQueryKeys(servletRequest, Set.of());
        return client.createAccountingPeriod(context, fiscalCalendarId, body);
    }

    /** R07 开放会计期间（FUTURE → OPEN，已 OPEN 直接返回）；无请求体，携带 Body 时被请求边界拒绝。 */
    @PostMapping("/accounting-periods/v1/{accountingPeriodId}/open")
    public OpenAccountingPeriodResponseBody openAccountingPeriod(HttpServletRequest servletRequest,
        OperatorContext context, @PathVariable("accountingPeriodId") @Pattern(regexp = ID) String accountingPeriodId) {
        requireQueryKeys(servletRequest, Set.of());
        return client.openAccountingPeriod(context, accountingPeriodId);
    }

    /** R14 全部科目表，按代码升序。 */
    @GetMapping("/chart-of-accounts/v1")
    public ChartOfAccountsListResponseBody listChartsOfAccounts(HttpServletRequest servletRequest) {
        requireQueryKeys(servletRequest, Set.of());
        return client.listChartsOfAccounts();
    }

    /** R15 科目表下的科目，按代码升序；postingAllowed 省略时返回全部。 */
    @GetMapping("/chart-of-accounts/v1/{chartCode}/accounts")
    public ChartAccountListResponseBody listChartAccounts(HttpServletRequest servletRequest,
        @PathVariable("chartCode") @Pattern(regexp = CHART_CODE) String chartCode,
        @RequestParam(value = "postingAllowed", required = false) Boolean postingAllowed) {
        requireQueryKeys(servletRequest, Set.of("postingAllowed"));
        return client.listChartAccounts(chartCode, postingAllowed);
    }

    /** R16 币种列表，按代码升序。 */
    @GetMapping("/currencies/v1")
    public CurrencyListResponseBody listCurrencies(HttpServletRequest servletRequest,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status) {
        requireQueryKeys(servletRequest, Set.of("status"));
        return client.listCurrencies(status);
    }
}

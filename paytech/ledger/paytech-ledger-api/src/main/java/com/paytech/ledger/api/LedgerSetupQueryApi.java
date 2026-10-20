package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.response.AccountingPeriodResponseBody;
import com.paytech.ledger.api.response.ChartAccountChangeResponseBody;
import com.paytech.ledger.api.response.ChartAccountListResponseBody;
import com.paytech.ledger.api.response.ChartOfAccountsListResponseBody;
import com.paytech.ledger.api.response.CurrencyListResponseBody;
import com.paytech.ledger.api.response.FinanceEntityResponseBody;
import com.paytech.ledger.api.response.FiscalCalendarResponseBody;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 账务基础资料只读查询（内部能力 Ledger & Account Balance）
 * <li>只读，不加锁、不开事务；供 Operations BFF 简单转发给账务基础资料页面。</li>
 * <li>分页复用公共 page/size：page 默认 1，size 默认 10 且只允许 10、20、50、100、200，非法值返回 400 INVALID_REQUEST。</li>
 * <li>需要区分处理的拒绝以 {@link com.paytech.ledger.api.enums.LedgerSetupErrorCode} 返回 400。</li>
 */
@HttpExchange(accept = "application/json")
public interface LedgerSetupQueryApi {

    /**
     * 分页查询财务主体，按主键升序
     *
     * @param status 可选状态：ACTIVE 或 CLOSED
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 财务主体分页
     */
    @GetExchange("/finance-entities/v1")
    PagingResponseBody<FinanceEntityResponseBody> listFinanceEntities(
        @RequestParam(value = "status", required = false) @Pattern(regexp = "ACTIVE|CLOSED") String status,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询财务日历，按主键升序
     *
     * @param entityType 可选主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY
     * @param entityId 可选主体标识，最长 64 字符
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 财务日历分页
     */
    @GetExchange("/fiscal-calendars/v1")
    PagingResponseBody<FiscalCalendarResponseBody> listFiscalCalendars(
        @RequestParam(value = "entityType", required = false) @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY") String entityType,
        @RequestParam(value = "entityId", required = false) @Size(max = 64) String entityId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询日历下的会计期间，按财年、期号升序
     *
     * @param fiscalCalendarId 财务日历主键
     * @param fiscalYear 可选财年
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 会计期间分页；日历不存在时 400 FISCAL_CALENDAR_NOT_FOUND
     */
    @GetExchange("/fiscal-calendars/v1/{fiscalCalendarId}/periods")
    PagingResponseBody<AccountingPeriodResponseBody> listAccountingPeriods(
        @PathVariable("fiscalCalendarId") @Pattern(regexp = "[1-9][0-9]{0,18}") String fiscalCalendarId,
        @RequestParam(value = "fiscalYear", required = false) Integer fiscalYear,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询全部科目表，按科目表代码升序
     *
     * @return 科目表列表，不分页
     */
    @GetExchange("/chart-of-accounts/v1")
    ChartOfAccountsListResponseBody listChartsOfAccounts();

    /**
     * 查询科目表下的科目，按科目编号升序（父科目在其下级之前）
     *
     * @param chartCode 科目表代码
     * @param postingAllowed 可选是否可过账；省略时返回全部科目
     * @param accountStatus 可选启停状态：ACTIVE 或 INACTIVE；省略时返回全部
     * @return 科目列表，不分页；科目表不存在时 400 CHART_NOT_ACTIVE
     */
    @GetExchange("/chart-of-accounts/v1/{chartCode}/accounts")
    ChartAccountListResponseBody listChartAccounts(
        @PathVariable("chartCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String chartCode,
        @RequestParam(value = "postingAllowed", required = false) Boolean postingAllowed,
        @RequestParam(value = "accountStatus", required = false) @Pattern(regexp = "ACTIVE|INACTIVE") String accountStatus);

    /**
     * 分页查询科目变更记录，按变更时间倒序
     *
     * @param chartCode 科目表代码；不存在时 400 CHART_NOT_ACTIVE
     * @param chartAccountId 可选科目主键；不属于该科目表时 400 CHART_ACCOUNT_NOT_FOUND
     * @param page 可选页码
     * @param size 可选每页条数
     * @return 变更记录分页：操作人、时间与变更前后的编号、上级、名称和状态
     */
    @GetExchange("/chart-of-accounts/v1/{chartCode}/changes")
    PagingResponseBody<ChartAccountChangeResponseBody> listChartAccountChanges(
        @PathVariable("chartCode") @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String chartCode,
        @RequestParam(value = "chartAccountId", required = false) @Pattern(regexp = "[1-9][0-9]{0,18}") String chartAccountId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询币种，按币种代码升序
     *
     * @param status 可选状态：DRAFT、ACTIVE 或 INACTIVE
     * @return 币种列表，不分页
     */
    @GetExchange("/currencies/v1")
    CurrencyListResponseBody listCurrencies(
        @RequestParam(value = "status", required = false) @Pattern(regexp = "DRAFT|ACTIVE|INACTIVE") String status);
}

package com.paytech.finance.api.accounting;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.finance.api.accounting.request.CreateAccountingPeriodRequestBody;
import com.paytech.finance.api.accounting.request.CreateFiscalCalendarRequestBody;
import com.paytech.finance.api.accounting.response.CreateAccountingPeriodResponseBody;
import com.paytech.finance.api.accounting.response.CreateFiscalCalendarResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 维护财务日历与会计期间（内部能力 Ledger & Account Balance）
 * <li>Finance 内部开发接口：未接 Gateway 与统一身份，不得对外发布。</li>
 */
@HttpExchange(url = "/fiscal-calendars/v1", accept = "application/json")
public interface FiscalCalendarCommandApi {

    /**
     * 为主体创建唯一的财务日历
     *
     * @param body 请求
     * @return 201 与新日历主键；主体已有日历或财务主体无效时 400
     */
    @PostExchange
    ResponseEntity<CreateFiscalCalendarResponseBody> createFiscalCalendar(
        @Valid @RequestBody CreateFiscalCalendarRequestBody body);

    /**
     * 在日历下创建 FUTURE 会计期间
     *
     * @param fiscalCalendarId 财务日历主键
     * @param body 请求
     * @return 201 与新期间主键；日期重叠或年度期号重复时 400，同日历并发创建时 409
     */
    @PostExchange("/{fiscalCalendarId}/periods")
    ResponseEntity<CreateAccountingPeriodResponseBody> createAccountingPeriod(
        @PathVariable("fiscalCalendarId") @Pattern(regexp = "[1-9][0-9]{0,18}") String fiscalCalendarId,
        @Valid @RequestBody CreateAccountingPeriodRequestBody body);
}

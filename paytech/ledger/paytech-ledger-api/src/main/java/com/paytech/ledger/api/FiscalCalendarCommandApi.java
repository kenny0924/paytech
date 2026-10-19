package com.paytech.ledger.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.request.CreateAccountingPeriodRequestBody;
import com.paytech.ledger.api.request.CreateFiscalCalendarRequestBody;
import com.paytech.ledger.api.response.CreateAccountingPeriodResponseBody;
import com.paytech.ledger.api.response.CreateFiscalCalendarResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * 维护财务日历与会计期间（内部能力 Ledger & Account Balance）
 * <li>Finance 内部接口：命令操作人身份来自 Operations BFF 填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 */
@HttpExchange(url = "/fiscal-calendars/v1", accept = "application/json")
public interface FiscalCalendarCommandApi {

    /**
     * 为主体创建唯一的财务日历
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param body 请求
     * @return 201 与新日历主键；主体已有日历或财务主体无效时 400
     */
    @PostExchange
    ResponseEntity<CreateFiscalCalendarResponseBody> createFiscalCalendar(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @Valid @RequestBody CreateFiscalCalendarRequestBody body);

    /**
     * 在日历下创建 FUTURE 会计期间
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param fiscalCalendarId 财务日历主键
     * @param body 请求
     * @return 201 与新期间主键；日期重叠或年度期号重复时 400，同日历并发创建时 409
     */
    @PostExchange("/{fiscalCalendarId}/periods")
    ResponseEntity<CreateAccountingPeriodResponseBody> createAccountingPeriod(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("fiscalCalendarId") @Pattern(regexp = "[1-9][0-9]{0,18}") String fiscalCalendarId,
        @Valid @RequestBody CreateAccountingPeriodRequestBody body);
}

package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.response.OpenAccountingPeriodResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 会计期间状态流转（内部能力 Ledger & Account Balance）
 * <li>本期只提供 FUTURE→OPEN；关账与重开由后续期间关账能力提供。</li>
 * <li>Finance 内部接口：命令操作人身份来自 Operations BFF 填入的 X-Gateway-Actor-Subject / X-Gateway-Session-Id Header，仅经 Operations BFF 转发，不得直接对外发布。</li>
 */
@HttpExchange(url = "/accounting-periods/v1", accept = "application/json")
public interface AccountingPeriodCommandApi {

    /**
     * 开放会计期间
     *
     * @param actorAccountId 操作人 Account ID，由 Operations BFF 从会话填入 X-Gateway-Actor-Subject；缺失时 400 INVALID_REQUEST
     * @param sessionId 操作人会话 ID，由 Operations BFF 填入 X-Gateway-Session-Id；缺失时 400 INVALID_REQUEST
     * @param accountingPeriodId 会计期间主键
     * @return 200 与当前状态；已 OPEN 时直接返回，其他非 FUTURE 状态 400
     */
    @PostExchange("/{accountingPeriodId}/open")
    OpenAccountingPeriodResponseBody openAccountingPeriod(
        @RequestHeader("X-Gateway-Actor-Subject") String actorAccountId,
        @RequestHeader("X-Gateway-Session-Id") String sessionId,
        @PathVariable("accountingPeriodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String accountingPeriodId);
}

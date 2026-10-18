package com.paytech.ledger.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.ledger.api.response.OpenAccountingPeriodResponseBody;

import jakarta.validation.constraints.Pattern;

/**
 * 会计期间状态流转（内部能力 Ledger & Account Balance）
 * <li>本期只提供 FUTURE→OPEN；关账与重开由后续期间关账能力提供。</li>
 * <li>Finance 内部开发接口：未接 Gateway 与统一身份，不得对外发布。</li>
 */
@HttpExchange(url = "/accounting-periods/v1", accept = "application/json")
public interface AccountingPeriodCommandApi {

    /**
     * 开放会计期间
     *
     * @param accountingPeriodId 会计期间主键
     * @return 200 与当前状态；已 OPEN 时直接返回，其他非 FUTURE 状态 400
     */
    @PostExchange("/{accountingPeriodId}/open")
    OpenAccountingPeriodResponseBody openAccountingPeriod(
        @PathVariable("accountingPeriodId") @Pattern(regexp = "[1-9][0-9]{0,18}") String accountingPeriodId);
}

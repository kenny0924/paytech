package com.paytech.billing.integration.reconciliation.impl;

import static com.paytech.billing.integration.UpstreamResponseFields.required;
import static com.paytech.billing.integration.UpstreamResponseFields.requiredId;

import org.springframework.stereotype.Component;

import com.paytech.billing.domain.ClearingAccount;
import com.paytech.billing.integration.reconciliation.ClearingAccountClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.reconciliation.api.clearing.ClearingAccountResolutionApi;
import com.paytech.reconciliation.api.clearing.request.ResolveClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;

import lombok.AllArgsConstructor;

/**
 * 只适配 reconciliation-api 清算账户解析 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>400 CLEARING_ACCOUNT_NOT_RESOLVED（MID 未归属或清算账户停用）转为 ErrorException：事实停在重试，清算账户建好后自动恢复
 * （对账决定第 9 条）。</li>
 */
@Component
@AllArgsConstructor
public class ClearingAccountClientImpl implements ClearingAccountClient {
    private static final String UPSTREAM = "reconciliation";

    private final ClearingAccountResolutionApi clearingAccountResolutionApi;

    /** 按 MID + 币种解析；上游明确拒绝转为可重试错误。 */
    @Override
    public ClearingAccount resolve(long midId, String currency) {
        ResolveClearingAccountRequestBody body = new ResolveClearingAccountRequestBody();
        body.setMidId(Long.toString(midId));
        body.setCurrency(currency);
        ClearingAccountResolutionResponseBody response;
        try {
            response = clearingAccountResolutionApi.resolveClearingAccount(body);
        } catch (FailException rejected) {
            throw new ErrorException("清算账户未解析: MID " + midId + ", 币种 " + currency, rejected);
        }
        required(UPSTREAM, "clearingAccount", response);
        return new ClearingAccount(requiredId(UPSTREAM, "clearingAccountId", response.getClearingAccountId()),
            required(UPSTREAM, "entityNo", response.getEntityNo()), required(UPSTREAM, "currency", response.getCurrency()));
    }
}

package com.paytech.billing.integration.ledger.impl;

import static com.paytech.billing.integration.UpstreamResponseFields.required;
import static com.paytech.billing.integration.UpstreamResponseFields.requiredId;

import org.springframework.stereotype.Component;

import com.paytech.billing.integration.ledger.FinanceEntityClient;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.LedgerSetupQueryApi;
import com.paytech.ledger.api.response.FinanceEntityResponseBody;

import lombok.AllArgsConstructor;

/**
 * 只适配 ledger-api 按法律 Party 解析 Finance Entity 的 DTO；HTTP 与错误解码交给 Foundation 公共客户端
 * <li>404 FINANCE_ENTITY_NOT_FOUND（不存在或已关闭）转为 ErrorException：事实停在重试，Finance Entity 建好后由运维重放恢复。</li>
 */
@Component
@AllArgsConstructor
public class FinanceEntityClientImpl implements FinanceEntityClient {
    private static final String UPSTREAM = "ledger";

    private final LedgerSetupQueryApi ledgerSetupQueryApi;

    /** 按 Party 解析；上游明确拒绝转为可重试错误。 */
    @Override
    public long resolveFinanceEntityId(long legalPartyId) {
        FinanceEntityResponseBody response;
        try {
            response = ledgerSetupQueryApi.getFinanceEntityByParty(Long.toString(legalPartyId));
        } catch (FailException rejected) {
            throw new ErrorException("法律 Party 没有 ACTIVE Finance Entity: " + legalPartyId, rejected);
        }
        required(UPSTREAM, "financeEntity", response);
        return requiredId(UPSTREAM, "financeEntityId", response.getFinanceEntityId());
    }
}

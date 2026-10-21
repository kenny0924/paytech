package com.paytech.reconciliation.api.clearing;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.reconciliation.api.clearing.request.ResolveClearingAccountRequestBody;
import com.paytech.reconciliation.api.clearing.response.ClearingAccountResolutionResponseBody;

import jakarta.validation.Valid;

/**
 * 清算账户解析（UC-R03）
 * <li>服务间内部接口：billing 登记支付类事实时按 MID + 币种取 CLEARING_ACCOUNT 引用；不经 Gateway，不带操作人头。</li>
 * <li>只读；只解析 ACTIVE 清算账户。</li>
 */
@HttpExchange(url = "/reconciliation/v1/clearing-accounts", accept = "application/json")
public interface ClearingAccountResolutionApi {

    /**
     * 按 MID + 币种解析清算账户
     *
     * @param body MID 主键与币种
     * @return 200 与清算账户主键、编码、渠道、经营主体与币种；未归属或清算账户已停用时 400
     *         {@link com.paytech.reconciliation.api.enums.ReconciliationErrorCode#CLEARING_ACCOUNT_NOT_RESOLVED}
     */
    @PostExchange("/resolutions")
    ClearingAccountResolutionResponseBody resolveClearingAccount(@Valid @RequestBody ResolveClearingAccountRequestBody body);
}

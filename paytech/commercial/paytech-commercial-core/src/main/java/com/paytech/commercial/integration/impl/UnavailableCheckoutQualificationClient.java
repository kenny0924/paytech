package com.paytech.commercial.integration.impl;

import com.paytech.commercial.integration.CheckoutQualificationClient;
import com.paytech.foundation.common.error.ErrorException;
import static com.paytech.commercial.api.enums.CheckoutErrorCode.OWNER_UNAVAILABLE;

/** Risk 新身份资格查询尚未接入。无可配置的默认通过分支。 */
public final class UnavailableCheckoutQualificationClient implements CheckoutQualificationClient {
    /** 缺少新身份资格合同，禁止把 CCT 入驻历史解释为当前 Client 资格。 */
    @Override
    public void requireClientEligible(long clientId) {
        throw new ErrorException(OWNER_UNAVAILABLE, "Risk Checkout 资格合同尚未接入");
    }
    /** 缺少新身份资格合同，不能将依赖不可用解释为普通候选落选。 */
    @Override
    public boolean isCandidateEligible(String entityNo, Long localPartnerId) {
        throw new ErrorException(OWNER_UNAVAILABLE, "Risk Checkout 资格合同尚未接入");
    }
}

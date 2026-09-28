package com.paytech.risk.decision.domainservice.service;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityCommand;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityResult;

/**
 * 生产规则与持久化尚未接入时的 Fail-Closed 实现。
 */
public final class UnavailableManualPayoutEligibilityService implements ManualPayoutEligibilityService {

    @Override
    public ManualPayoutEligibilityResult evaluate(ManualPayoutEligibilityCommand command) {
        throw new ServiceUnavailableException(new IllegalStateException("Manual Payout 风控评估尚未就绪"));
    }
}

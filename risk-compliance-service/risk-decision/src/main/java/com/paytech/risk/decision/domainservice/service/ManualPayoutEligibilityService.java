package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.domain.ManualPayoutEligibilityCommand;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityResult;

/**
 * Manual Payout 资格评估能力。
 */
public interface ManualPayoutEligibilityService {

    /**
     * 创建评估；同一命令键和相同输入必须重放原结果。
     *
     * @param command 评估命令
     * @return 不可变评估结果
     */
    ManualPayoutEligibilityResult evaluate(ManualPayoutEligibilityCommand command);
}

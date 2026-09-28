package com.paytech.risk.decision.domain;

import java.util.List;

/**
 * Manual Payout 资格评估的不可变领域结果。
 */
public record ManualPayoutEligibilityResult(long assessmentId, long evaluationId, String decision,
    List<String> reasonCodes, List<String> matchedControls, String requiredAmount, String requiredCurrency,
    String controlSetHash) {
}

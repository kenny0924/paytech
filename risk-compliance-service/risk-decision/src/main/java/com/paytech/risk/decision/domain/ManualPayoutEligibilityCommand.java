package com.paytech.risk.decision.domain;

import java.math.BigDecimal;

/**
 * Manual Payout 资格评估的领域命令。
 */
public record ManualPayoutEligibilityCommand(String commandKey, String checkpointType, String roleType,
    String roleId, String operatingEntityId, BigDecimal grossAmount, String currency, String bankCountry,
    String sourceFactType, String sourceFactId, int sourceFactVersion, String inputHash) {
}

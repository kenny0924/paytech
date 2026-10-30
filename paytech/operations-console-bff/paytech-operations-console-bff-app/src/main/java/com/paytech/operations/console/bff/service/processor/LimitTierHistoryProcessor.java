package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.RiskLimitTierClient;
import com.paytech.operations.console.bff.service.model.risklimittier.LimitTierHistoryPageBody;
import com.paytech.operations.console.bff.service.processor.request.LimitTierClientRequest;

import lombok.AllArgsConstructor;

/** 档位变更历史（L3）：转发 Risk 后为申请人、审批人补显示名。 */
@Component
@AllArgsConstructor
public class LimitTierHistoryProcessor extends AbstractProcessor<LimitTierClientRequest, LimitTierHistoryPageBody> {
    private final LimitTierLabeler labeler;
    private final RiskLimitTierClient tiers;

    @Override
    protected LimitTierHistoryPageBody processRequest(LimitTierClientRequest request) {
        return labeler.labelHistory(tiers.history(request.getOperator(), request.getClientId()));
    }
}

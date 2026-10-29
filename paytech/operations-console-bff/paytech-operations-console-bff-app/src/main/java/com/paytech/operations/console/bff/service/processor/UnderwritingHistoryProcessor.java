package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.riskunderwriting.UnderwritingHistoryPageBody;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingRecordRequest;

import lombok.AllArgsConstructor;

/** Client 承保版本历史（U4）：转发 Risk 历史并一次批量补显示名。 */
@Component
@AllArgsConstructor
public class UnderwritingHistoryProcessor extends AbstractProcessor<UnderwritingRecordRequest, UnderwritingHistoryPageBody> {
    private final UnderwritingLabeler labeler;
    private final RiskUnderwritingClient underwritings;

    @Override
    protected UnderwritingHistoryPageBody processRequest(UnderwritingRecordRequest request) {
        return new UnderwritingHistoryPageBody(labeler.labelAll(underwritings.history(request.getOperator(), request.getId())
            .getItems()));
    }
}

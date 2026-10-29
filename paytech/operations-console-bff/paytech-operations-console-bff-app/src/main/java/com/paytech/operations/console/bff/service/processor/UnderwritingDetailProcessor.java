package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingResponseBody;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingRecordRequest;

import lombok.AllArgsConstructor;

/** 承保版本详情（U2）：转发 Risk 详情并补创建人、提交人、决定人显示名。 */
@Component
@AllArgsConstructor
public class UnderwritingDetailProcessor extends AbstractProcessor<UnderwritingRecordRequest, LabeledUnderwritingResponseBody> {
    private final UnderwritingLabeler labeler;
    private final RiskUnderwritingClient underwritings;

    @Override
    protected LabeledUnderwritingResponseBody processRequest(UnderwritingRecordRequest request) {
        return labeler.label(underwritings.detail(request.getOperator(), request.getId()));
    }
}

package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingPreviewRequest;
import com.paytech.risk.decision.api.control.request.UnderwritingPreviewRequestBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;

import lombok.AllArgsConstructor;

/** 承保预览（U11）：按 Client 重新读取输入快照（任一来源不可用 503），与页面的结算周期建议一起调用 Risk 预览。 */
@Component
@AllArgsConstructor
public class UnderwritingPreviewProcessor extends AbstractProcessor<UnderwritingPreviewRequest, UnderwritingPreviewResponseBody> {
    private final RiskUnderwritingClient underwritings;
    private final UnderwritingInputAssembler assembler;

    @Override
    protected UnderwritingPreviewResponseBody processRequest(UnderwritingPreviewRequest request) {
        UnderwritingPreviewRequestBody body = new UnderwritingPreviewRequestBody();
        body.setInput(assembler.assemble(request.getOperator(), request.getClientId(), true).getInput());
        body.setSuggestedSettlementDays(request.getSuggestedSettlementDays());
        return underwritings.preview(request.getOperator(), body);
    }
}

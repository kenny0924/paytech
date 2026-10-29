package com.paytech.operations.console.bff.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.RiskUnderwritingClient;
import com.paytech.operations.console.bff.service.model.riskunderwriting.LabeledUnderwritingResponseBody;
import com.paytech.operations.console.bff.service.processor.request.UnderwritingCommandRequest;
import com.paytech.risk.decision.api.control.request.UnderwritingInputBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;

import lombok.AllArgsConstructor;

/**
 * 承保写命令转发（U5—U10）：按版本取得所属 Client，重新从 Owner 读取输入快照后单次写 Risk，回执补显示名
 * <li>页面只发结论字段，不能伪造输入；任一输入来源不可用返回 503，不调用 Risk。</li>
 * <li>Risk 的 404 / 409 / 422 原样返回，不重试；回执须属于该 Client。</li>
 */
@Component
@AllArgsConstructor
public class UnderwritingCommandProcessor extends AbstractProcessor<UnderwritingCommandRequest, LabeledUnderwritingResponseBody> {
    private final UnderwritingLabeler labeler;
    private final RiskUnderwritingClient underwritings;
    private final UnderwritingInputAssembler assembler;

    @Override
    protected LabeledUnderwritingResponseBody processRequest(UnderwritingCommandRequest request) {
        String clientId = request.getClientId() != null ? request.getClientId()
            : underwritings.detail(request.getOperator(), request.getUnderwritingId()).getClientId();
        UnderwritingInputBody input = request.isWithInput()
            ? assembler.assemble(request.getOperator(), clientId, true).getInput() : null;
        UnderwritingResponseBody response = request.getCommand().apply(input);
        if (response == null || !clientId.equals(response.getClientId())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return labeler.label(response);
    }
}

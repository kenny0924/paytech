package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.compliance.service.processor.request.ClientReviewCaseRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCaseResponse;

import lombok.AllArgsConstructor;

/** O02（Risk 部分）：主案件、子案件、主体、材料、证据、已接收分类及通过门禁；只读。 */
@Component
@AllArgsConstructor
public class GetClientReviewCaseProcessor extends AbstractProcessor<ClientReviewCaseRequest, ClientReviewCaseResponse> {
    private final ClientReviewFamilyLoader families;

    @Override
    protected ClientReviewCaseResponse processRequest(ClientReviewCaseRequest request) {
        return ClientReviewCaseConverter.detail(families.load(request.getCaseId()));
    }
}

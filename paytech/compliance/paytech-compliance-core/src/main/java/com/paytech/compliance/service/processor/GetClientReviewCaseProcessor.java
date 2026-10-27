package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.service.processor.request.ClientReviewCaseRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCaseResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/** O02 案件工作台详情；只读，不加锁。 */
@Component
@AllArgsConstructor
public class GetClientReviewCaseProcessor extends AbstractProcessor<ClientReviewCaseRequest, ClientReviewCaseResponse> {
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 读取全貌并按查看人计算门禁。 */
    @Override
    protected ClientReviewCaseResponse processRequest(ClientReviewCaseRequest request) {
        return ClientReviewCaseConverter.detail(families.load(request.getCaseId()), request.getActorId(), support.now());
    }
}

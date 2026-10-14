package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.client.contract.api.onboarding.ClientApplicationAdminApi;
import com.paytech.client.contract.api.onboarding.ClientClassificationAdminApi;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.ClientDraftResponseBody;
import com.paytech.client.contract.api.onboarding.request.ClassificationItemBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationOptionsResponseBody;
import com.paytech.client.contract.api.onboarding.response.ClassificationSetResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewCctClient;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ClassificationSetRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/** 把页面分类命令转换为 CCT 合同；响应必须对应请求的任务。 */
@Component
@AllArgsConstructor
public class ClientReviewCctClientImpl implements ClientReviewCctClient {
    private final ClientApplicationAdminApi applications;
    private final ClientClassificationAdminApi classifications;

    @Override
    public ClientDraftResponseBody application(OperatorContext o, String caseId) {
        var response = applications.application(o.accountId(), o.sessionId(), caseId);
        ClientReviewRiskClientImpl.require(response != null && response.applicationId() != null);
        return response;
    }

    @Override
    public ClassificationSetResponseBody classifications(OperatorContext o, String caseId) {
        return classification(caseId, classifications.get(o.accountId(), o.sessionId(), caseId));
    }

    @Override
    public ClassificationOptionsResponseBody options(OperatorContext o) {
        var response = classifications.options(o.accountId(), o.sessionId());
        ClientReviewRiskClientImpl.require(response != null && response.getIndustries() != null);
        return response;
    }

    @Override
    public ClassificationSetResponseBody replace(OperatorContext o, String caseId, ClassificationSetRequestBody request) {
        var body = new com.paytech.client.contract.api.onboarding.request.ClassificationSetRequestBody();
        body.setItems(request.items().stream().map(item -> {
            var row = new ClassificationItemBody();
            row.setIndustryCode(item.industryCode());
            row.setMccCode(item.mccCode());
            row.setPrimary(item.primary());
            return row;
        }).toList());
        return classification(caseId, classifications.replace(o.accountId(), o.sessionId(), caseId, body));
    }

    @Override
    public ClassificationSetResponseBody confirm(OperatorContext o, String caseId) {
        return classification(caseId, classifications.confirm(o.accountId(), o.sessionId(), caseId));
    }

    /** 分类结果必须属于本任务。 */
    static ClassificationSetResponseBody classification(String caseId, ClassificationSetResponseBody response) {
        ClientReviewRiskClientImpl.require(response != null && caseId.equals(response.getCaseId()) && response.getItems() != null);
        return response;
    }
}

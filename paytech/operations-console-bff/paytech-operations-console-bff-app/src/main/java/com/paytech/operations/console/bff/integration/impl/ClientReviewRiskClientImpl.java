package com.paytech.operations.console.bff.integration.impl;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.DecisionRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.EvidenceReviewRequestBody;
import com.paytech.operations.console.bff.service.model.ClientReviewModels.ReturnRequestBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.risk.api.compliance.ClientReviewAdminApi;
import com.paytech.risk.api.compliance.request.ClientReviewDecisionRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.risk.api.compliance.request.ClientReviewReturnRequestBody;
import com.paytech.risk.api.compliance.response.ClientReviewCaseResponseBody;
import com.paytech.risk.api.compliance.response.ClientReviewCommandResponseBody;

import lombok.AllArgsConstructor;

/** 把页面命令转换为 Risk 合同；响应必须对应请求的任务，否则视为上游响应无效。 */
@Component
@AllArgsConstructor
public class ClientReviewRiskClientImpl implements ClientReviewRiskClient {
    private final ClientReviewAdminApi api;

    @Override
    public PagingResponseBody<ClientReviewCaseResponseBody> list(OperatorContext o, Integer page, Integer size, String status,
        String qualificationType, String applicationId) {
        var response = api.listCases(o.accountId(), o.sessionId(), page, size, status, qualificationType, applicationId);
        require(response != null && response.items() != null);
        return response;
    }

    @Override
    public ClientReviewCaseResponseBody detail(OperatorContext o, String caseId) {
        var response = api.getCase(o.accountId(), o.sessionId(), caseId);
        require(response != null && caseId.equals(response.getCaseId()));
        return response;
    }

    /** 任意主体、任意材料要求下的证据文件；结构缺失视为没有证据。 */
    @Override
    public java.util.Set<String> evidenceFileIds(OperatorContext o, String caseId) {
        var review = detail(o, caseId);
        if (review.getSubjects() == null) {
            return java.util.Set.of();
        }
        return review.getSubjects().stream()
            .filter(subject -> subject.getRequirements() != null)
            .flatMap(subject -> subject.getRequirements().stream())
            .filter(requirement -> requirement.getEvidence() != null)
            .flatMap(requirement -> requirement.getEvidence().stream())
            .map(evidence -> evidence.getFileId())
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override
    public ClientReviewCommandResponseBody reviewEvidence(OperatorContext o, String caseId, String evidenceId,
        EvidenceReviewRequestBody request) {
        var body = new ClientReviewEvidenceReviewRequestBody();
        body.setOutcome(request.outcome());
        return command(caseId, api.reviewEvidence(o.accountId(), o.sessionId(), caseId, evidenceId, body));
    }

    @Override
    public ClientReviewCommandResponseBody returnCase(OperatorContext o, String caseId, ReturnRequestBody request) {
        var body = new ClientReviewReturnRequestBody();
        body.setReasonCode(request.reasonCode());
        body.setSection(request.section());
        body.setPublicNote(request.publicNote());
        return command(caseId, api.returnCase(o.accountId(), o.sessionId(), caseId, body));
    }

    @Override
    public ClientReviewCommandResponseBody approve(OperatorContext o, String caseId, DecisionRequestBody request) {
        return command(caseId, api.approveCase(o.accountId(), o.sessionId(), caseId, decision(request)));
    }

    @Override
    public ClientReviewCommandResponseBody reject(OperatorContext o, String caseId, DecisionRequestBody request) {
        return command(caseId, api.rejectCase(o.accountId(), o.sessionId(), caseId, decision(request)));
    }

    /** 结论请求。 */
    static ClientReviewDecisionRequestBody decision(DecisionRequestBody request) {
        var body = new ClientReviewDecisionRequestBody();
        body.setReasonCode(request.reasonCode());
        body.setReason(request.reason());
        body.setPublicNote(request.publicNote());
        return body;
    }

    /** 命令结果必须属于本任务。 */
    static ClientReviewCommandResponseBody command(String caseId, ClientReviewCommandResponseBody response) {
        require(response != null && caseId.equals(response.getCaseId()) && response.getStatus() != null);
        return response;
    }

    /** 上游成功响应不符合合同。 */
    static void require(boolean valid) {
        if (!valid) {
            throw new ErrorException(com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }
}

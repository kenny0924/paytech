package com.paytech.compliance.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.compliance.api.ClientReviewAdminApi;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.api.request.ClientReviewReturnRequestBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.service.processor.DecideClientReviewProcessor;
import com.paytech.compliance.service.processor.GetClientReviewCaseProcessor;
import com.paytech.compliance.service.processor.ListClientReviewCasesProcessor;
import com.paytech.compliance.service.processor.ReviewClientEvidenceProcessor;
import com.paytech.compliance.service.processor.request.ClientReviewCaseRequest;
import com.paytech.compliance.service.processor.request.DecideClientReviewRequest;
import com.paytech.compliance.service.processor.request.ListClientReviewCasesRequest;
import com.paytech.compliance.service.processor.request.ReviewClientEvidenceRequest;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/**
 * Operations Client 入驻审核入口：解析可信操作者与路径参数后委托 risk-compliance
 * <li>仅在 onboarding.review.enabled=true 时注册；路径前缀为 metadata-only 日志。</li>
 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewAdminController implements ClientReviewAdminApi {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final OperationRequestContextParser parser;
    private final DecideClientReviewProcessor decideProcessor;
    private final GetClientReviewCaseProcessor detailProcessor;
    private final ListClientReviewCasesProcessor listProcessor;
    private final ReviewClientEvidenceProcessor evidenceProcessor;

    @Override
    public PagingResponseBody<ClientReviewCaseResponseBody> listCases(String actorAccountId, String sessionId, Integer page,
        Integer size, String status, String qualificationType, String applicationId) {
        parser.parse(actorAccountId, sessionId);
        int pageNo = page == null ? 1 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNo < 1 || pageNo > 100000 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new InvalidParameterException("分页参数无效");
        }
        var request = new ListClientReviewCasesRequest(pageNo, pageSize, parser.parseOptionalUpperCode("status", status),
            parser.parseOptionalUpperCode("qualificationType", qualificationType),
            applicationId == null ? null : parser.parsePositiveId("applicationId", applicationId));
        var result = listProcessor.process(request);
        return new PagingResponseBody<>(result.getItems().stream().map(ClientReviewWireConverter::caseBody).toList(),
            result.getPage(), result.getSize(), result.getTotal());
    }

    @Override
    public ClientReviewCaseResponseBody getCase(String actorAccountId, String sessionId, String caseId) {
        parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.caseBody(
            detailProcessor.process(new ClientReviewCaseRequest(parser.parsePositiveId("caseId", caseId))));
    }

    @Override
    public ClientReviewCommandResponseBody reviewEvidence(String actorAccountId, String sessionId, String caseId, String evidenceId,
        ClientReviewEvidenceReviewRequestBody request) {
        parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(evidenceProcessor.process(new ReviewClientEvidenceRequest(
            parser.parsePositiveId("caseId", caseId), parser.parsePositiveId("evidenceId", evidenceId), request.getOutcome())));
    }

    @Override
    public ClientReviewCommandResponseBody returnCase(String actorAccountId, String sessionId, String caseId,
        ClientReviewReturnRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(decideProcessor.process(new DecideClientReviewRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), request.getReasonCode(), null, request.getPublicNote(),
            request.getSection(), ClientReviewAction.RETURN)));
    }

    @Override
    public ClientReviewCommandResponseBody approveCase(String actorAccountId, String sessionId, String caseId,
        ClientReviewDecisionRequestBody request) {
        return decide(actorAccountId, sessionId, caseId, request, ClientReviewAction.APPROVE);
    }

    @Override
    public ClientReviewCommandResponseBody rejectCase(String actorAccountId, String sessionId, String caseId,
        ClientReviewDecisionRequestBody request) {
        return decide(actorAccountId, sessionId, caseId, request, ClientReviewAction.REJECT);
    }

    /** 通过与拒绝共用的转换；结论由路径确定。 */
    ClientReviewCommandResponseBody decide(String actorAccountId, String sessionId, String caseId, ClientReviewDecisionRequestBody request,
        ClientReviewAction action) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(decideProcessor.process(new DecideClientReviewRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), request.getReasonCode(), request.getReason(),
            request.getPublicNote(), null, action)));
    }
}

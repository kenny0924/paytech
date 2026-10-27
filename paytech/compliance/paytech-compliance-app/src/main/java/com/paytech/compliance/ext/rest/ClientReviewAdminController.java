package com.paytech.compliance.ext.rest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.compliance.api.ClientReviewAdminApi;
import com.paytech.compliance.api.request.ClientReviewCheckResultRequestBody;
import com.paytech.compliance.api.request.ClientReviewDecisionRequestBody;
import com.paytech.compliance.api.request.ClientReviewEvidenceReviewRequestBody;
import com.paytech.compliance.api.request.ClientReviewHitResolutionRequestBody;
import com.paytech.compliance.api.request.ClientReviewRecommendationRequestBody;
import com.paytech.compliance.api.request.ClientReviewReturnRequestBody;
import com.paytech.compliance.api.request.ClientReviewRiskRatingRequestBody;
import com.paytech.compliance.api.request.ClientReviewScreeningRunRequestBody;
import com.paytech.compliance.api.request.ClientReviewWaiverRequestBody;
import com.paytech.compliance.api.response.ClientReviewCaseResponseBody;
import com.paytech.compliance.api.response.ClientReviewCommandResponseBody;
import com.paytech.compliance.api.response.ClientReviewProgramListResponseBody;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.service.processor.DecideClientReviewProcessor;
import com.paytech.compliance.service.processor.GetClientReviewCaseProcessor;
import com.paytech.compliance.service.processor.ListClientReviewCasesProcessor;
import com.paytech.compliance.service.processor.ListProgramsProcessor;
import com.paytech.compliance.service.processor.RateClientReviewRiskProcessor;
import com.paytech.compliance.service.processor.RecommendClientReviewProcessor;
import com.paytech.compliance.service.processor.RecordClientReviewCheckProcessor;
import com.paytech.compliance.service.processor.RerunClientReviewScreeningProcessor;
import com.paytech.compliance.service.processor.ResolveScreeningHitProcessor;
import com.paytech.compliance.service.processor.ReviewClientEvidenceProcessor;
import com.paytech.compliance.service.processor.WaiveClientReviewRequirementProcessor;
import com.paytech.compliance.service.processor.request.ClientReviewCaseRequest;
import com.paytech.compliance.service.processor.request.DecideClientReviewRequest;
import com.paytech.compliance.service.processor.request.ListClientReviewCasesRequest;
import com.paytech.compliance.service.processor.request.ListProgramsRequest;
import com.paytech.compliance.service.processor.request.RateClientReviewRiskRequest;
import com.paytech.compliance.service.processor.request.RecommendClientReviewRequest;
import com.paytech.compliance.service.processor.request.RecordClientReviewCheckRequest;
import com.paytech.compliance.service.processor.request.RerunClientReviewScreeningRequest;
import com.paytech.compliance.service.processor.request.ResolveScreeningHitRequest;
import com.paytech.compliance.service.processor.request.ReviewClientEvidenceRequest;
import com.paytech.compliance.service.processor.request.WaiveClientReviewRequirementRequest;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.foundation.web.request.OperationRequestContextParser;

import lombok.AllArgsConstructor;

/** 运营案件工作台的协议适配：解析 Gateway 可信头与路径，转换为 Owner 输入后委托 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "onboarding.review.enabled", havingValue = "true")
public class ClientReviewAdminController implements ClientReviewAdminApi {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final OperationRequestContextParser parser;
    private final DecideClientReviewProcessor decideProcessor;
    private final GetClientReviewCaseProcessor detailProcessor;
    private final RateClientReviewRiskProcessor riskProcessor;
    private final ListClientReviewCasesProcessor listProcessor;
    private final ListProgramsProcessor programProcessor;
    private final ResolveScreeningHitProcessor hitProcessor;
    private final ReviewClientEvidenceProcessor evidenceProcessor;
    private final RecordClientReviewCheckProcessor checkProcessor;
    private final RecommendClientReviewProcessor recommendProcessor;
    private final RerunClientReviewScreeningProcessor screeningProcessor;
    private final WaiveClientReviewRequirementProcessor waiverProcessor;

    /** O01 列表；responsiblePartnerId=0 表示只看平台自营。 */
    @Override
    public PagingResponseBody<ClientReviewCaseResponseBody> listCases(String actorAccountId, String sessionId, Integer page,
        Integer size, String status, String qualificationType, String applicationId, String responsiblePartnerId,
        String jurisdictionCode) {
        parser.parse(actorAccountId, sessionId);
        int pageNo = page == null ? 1 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNo < 1 || pageNo > 100000 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new InvalidParameterException("分页参数无效");
        }
        boolean platformOnly = "0".equals(responsiblePartnerId);
        if (jurisdictionCode != null && !jurisdictionCode.matches("[A-Z][A-Z0-9-]{1,15}")) {
            throw new InvalidParameterException("jurisdictionCode 无效");
        }
        var request = new ListClientReviewCasesRequest(pageNo, pageSize, parser.parseOptionalUpperCode("status", status),
            parser.parseOptionalUpperCode("qualificationType", qualificationType), optionalId("applicationId", applicationId),
            platformOnly ? null : optionalId("responsiblePartnerId", responsiblePartnerId), platformOnly, jurisdictionCode);
        var result = listProcessor.process(request);
        return new PagingResponseBody<>(result.getItems().stream().map(ClientReviewWireConverter::caseBody).toList(),
            result.getPage(), result.getSize(), result.getTotal());
    }

    /** O14 程序目录；只读。 */
    @Override
    public ClientReviewProgramListResponseBody listPrograms(String actorAccountId, String sessionId, String jurisdictionCode,
        String status) {
        parser.parse(actorAccountId, sessionId);
        if (jurisdictionCode != null && !jurisdictionCode.matches("[A-Z][A-Z0-9-]{1,15}")) {
            throw new InvalidParameterException("jurisdictionCode 无效");
        }
        var result = programProcessor.process(new ListProgramsRequest(jurisdictionCode, parser.parseOptionalUpperCode("status", status)));
        var body = new ClientReviewProgramListResponseBody();
        body.setItems(result.getItems().stream().map(ClientReviewWireConverter::catalogProgram).toList());
        return body;
    }

    /** O02 详情；门禁按当前查看人计算四眼。 */
    @Override
    public ClientReviewCaseResponseBody getCase(String actorAccountId, String sessionId, String caseId) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.caseBody(detailProcessor.process(
            new ClientReviewCaseRequest(parser.parsePositiveId("caseId", caseId), context.getActorAccountId())));
    }

    /** O05 核验材料。 */
    @Override
    public ClientReviewCommandResponseBody reviewEvidence(String actorAccountId, String sessionId, String caseId, String evidenceId,
        ClientReviewEvidenceReviewRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(evidenceProcessor.process(new ReviewClientEvidenceRequest(
            parser.parsePositiveId("caseId", caseId), parser.parsePositiveId("evidenceId", evidenceId), context.getActorAccountId(),
            request.getOutcome(), request.getRejectReasonCode(), request.getNote(), obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 豁免材料要求。 */
    @Override
    public ClientReviewCommandResponseBody waiveRequirement(String actorAccountId, String sessionId, String caseId, String requirementId,
        ClientReviewWaiverRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(waiverProcessor.process(new WaiveClientReviewRequirementRequest(
            parser.parsePositiveId("caseId", caseId), parser.parsePositiveId("requirementId", requirementId), context.getActorAccountId(),
            request.getReasonCode(), request.getNote(), obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 录入人工检查结论。 */
    @Override
    public ClientReviewCommandResponseBody recordCheckResult(String actorAccountId, String sessionId, String caseId, String checkId,
        ClientReviewCheckResultRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(checkProcessor.process(new RecordClientReviewCheckRequest(
            parser.parsePositiveId("caseId", caseId), parser.parsePositiveId("checkId", checkId), context.getActorAccountId(),
            request.getOutcome(), request.getReasonCode(), request.getEvidenceRef(), request.getNote(),
            obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 重跑筛查。 */
    @Override
    public ClientReviewCommandResponseBody rerunScreenings(String actorAccountId, String sessionId, String caseId,
        ClientReviewScreeningRunRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(screeningProcessor.process(new RerunClientReviewScreeningRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 处置命中。 */
    @Override
    public ClientReviewCommandResponseBody resolveScreeningHit(String actorAccountId, String sessionId, String caseId, String hitId,
        ClientReviewHitResolutionRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(hitProcessor.process(new ResolveScreeningHitRequest(
            parser.parsePositiveId("caseId", caseId), parser.parsePositiveId("hitId", hitId), context.getActorAccountId(),
            request.getResolution(), request.getReasonCode(), request.getNote(), obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 手工评级。 */
    @Override
    public ClientReviewCommandResponseBody rateRisk(String actorAccountId, String sessionId, String caseId,
        ClientReviewRiskRatingRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(riskProcessor.process(new RateClientReviewRiskRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), request.getRiskRating(),
            request.getFactors().stream().map(item -> new RateClientReviewRiskRequest.Factor(item.getFactorCode(), item.getFactorValue()))
                .toList(), request.getNote(), obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 推荐。 */
    @Override
    public ClientReviewCommandResponseBody recommend(String actorAccountId, String sessionId, String caseId,
        ClientReviewRecommendationRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(recommendProcessor.process(new RecommendClientReviewRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), request.getRecommendation(), request.getNote(),
            obo(request.getOnBehalfOfPartnerId()))));
    }

    /** O07 退回。 */
    @Override
    public ClientReviewCommandResponseBody returnCase(String actorAccountId, String sessionId, String caseId,
        ClientReviewReturnRequestBody request) {
        var context = parser.parse(actorAccountId, sessionId);
        var errors = request.getErrors().stream().map(item -> new DecideClientReviewRequest.ReturnErrorItem(item.getRequirementKey(),
            optionalId("subjectPartyId", item.getSubjectPartyId()), item.getCode(), item.getReason(), item.getRemediation())).toList();
        return ClientReviewWireConverter.command(decideProcessor.process(new DecideClientReviewRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), request.getReasonCode(), null, request.getPublicNote(),
            request.getSection(), errors, request.getDueDays(), ClientReviewAction.RETURN, obo(request.getOnBehalfOfPartnerId()))));
    }

    /** O08 批准。 */
    @Override
    public ClientReviewCommandResponseBody approveCase(String actorAccountId, String sessionId, String caseId,
        ClientReviewDecisionRequestBody request) {
        return decide(actorAccountId, sessionId, caseId, request, ClientReviewAction.APPROVE);
    }

    /** O09 拒绝。 */
    @Override
    public ClientReviewCommandResponseBody rejectCase(String actorAccountId, String sessionId, String caseId,
        ClientReviewDecisionRequestBody request) {
        return decide(actorAccountId, sessionId, caseId, request, ClientReviewAction.REJECT);
    }

    /** 批准与拒绝共用的转换；结论由路径确定。 */
    ClientReviewCommandResponseBody decide(String actorAccountId, String sessionId, String caseId, ClientReviewDecisionRequestBody request,
        ClientReviewAction action) {
        var context = parser.parse(actorAccountId, sessionId);
        return ClientReviewWireConverter.command(decideProcessor.process(new DecideClientReviewRequest(
            parser.parsePositiveId("caseId", caseId), context.getActorAccountId(), request.getReasonCode(), request.getReason(),
            request.getPublicNote(), null, java.util.List.of(), null, action, obo(request.getOnBehalfOfPartnerId()))));
    }

    /** 可空正十进制 ID。 */
    Long optionalId(String name, String value) {
        return value == null ? null : parser.parsePositiveId(name, value);
    }

    /** 可空代操作 PayFac ID；格式已由 Body 校验。 */
    Long obo(String value) {
        return optionalId("onBehalfOfPartnerId", value);
    }
}

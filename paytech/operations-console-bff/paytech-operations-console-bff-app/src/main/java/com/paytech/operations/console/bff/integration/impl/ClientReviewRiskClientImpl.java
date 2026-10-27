package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

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
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import lombok.AllArgsConstructor;

/** 页面命令以 compliance 合同原样转发；响应必须对应请求的案件，否则视为上游响应无效。 */
@Component
@AllArgsConstructor
public class ClientReviewRiskClientImpl implements ClientReviewRiskClient {
    /** 文件服务地址，证据只保存该地址。 */
    static final Pattern FILE_ADDRESS = Pattern.compile("^/files/v1/([1-9][0-9]{0,18})$");

    private final ClientReviewAdminApi api;

    @Override
    public PagingResponseBody<ClientReviewCaseResponseBody> list(OperatorContext o, Integer page, Integer size, String status,
        String qualificationType, String applicationId, String responsiblePartnerId, String jurisdictionCode) {
        var response = api.listCases(o.accountId(), o.sessionId(), page, size, status, qualificationType, applicationId, responsiblePartnerId,
            jurisdictionCode);
        require(response != null && response.items() != null);
        return response;
    }

    /** 只接受请求辖区或全局（jurisdictionCode 为 null）的程序；不按状态过滤，DRAFT / RETIRED 版本一并展示。 */
    @Override
    public ClientReviewProgramListResponseBody programs(OperatorContext o, String jurisdictionCode) {
        var response = api.listPrograms(o.accountId(), o.sessionId(), jurisdictionCode, null);
        require(response != null && response.getItems() != null && response.getItems().stream().allMatch(program -> program != null
            && program.getProgramVersionId() != null && (program.getJurisdictionCode() == null || jurisdictionCode.equals(program.getJurisdictionCode()))));
        return response;
    }

    @Override
    public ClientReviewCaseResponseBody detail(OperatorContext o, String caseId) {
        var response = api.getCase(o.accountId(), o.sessionId(), caseId);
        require(response != null && caseId.equals(response.getCaseId()));
        return response;
    }

    /** 任意主体、任意材料要求下的证据文件；地址不是文件服务地址视为上游响应无效。 */
    @Override
    public Set<String> evidenceFileIds(OperatorContext o, String caseId) {
        var review = detail(o, caseId);
        if (review.getSubjects() == null) {
            return Set.of();
        }
        return review.getSubjects().stream()
            .filter(subject -> subject.getRequirements() != null)
            .flatMap(subject -> subject.getRequirements().stream())
            .filter(requirement -> requirement.getEvidence() != null)
            .flatMap(requirement -> requirement.getEvidence().stream())
            .map(evidence -> evidence.getFileAddress())
            .filter(Objects::nonNull)
            .map(ClientReviewRiskClientImpl::fileIdOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public ClientReviewCommandResponseBody reviewEvidence(OperatorContext o, String caseId, String evidenceId,
        ClientReviewEvidenceReviewRequestBody request) {
        return command(caseId, api.reviewEvidence(o.accountId(), o.sessionId(), caseId, evidenceId, request));
    }

    @Override
    public ClientReviewCommandResponseBody waiveRequirement(OperatorContext o, String caseId, String requirementId,
        ClientReviewWaiverRequestBody request) {
        return command(caseId, api.waiveRequirement(o.accountId(), o.sessionId(), caseId, requirementId, request));
    }

    @Override
    public ClientReviewCommandResponseBody recordCheckResult(OperatorContext o, String caseId, String checkId,
        ClientReviewCheckResultRequestBody request) {
        return command(caseId, api.recordCheckResult(o.accountId(), o.sessionId(), caseId, checkId, request));
    }

    @Override
    public ClientReviewCommandResponseBody rerunScreenings(OperatorContext o, String caseId, ClientReviewScreeningRunRequestBody request) {
        return command(caseId, api.rerunScreenings(o.accountId(), o.sessionId(), caseId, request));
    }

    @Override
    public ClientReviewCommandResponseBody resolveScreeningHit(OperatorContext o, String caseId, String hitId,
        ClientReviewHitResolutionRequestBody request) {
        return command(caseId, api.resolveScreeningHit(o.accountId(), o.sessionId(), caseId, hitId, request));
    }

    @Override
    public ClientReviewCommandResponseBody rateRisk(OperatorContext o, String caseId, ClientReviewRiskRatingRequestBody request) {
        return command(caseId, api.rateRisk(o.accountId(), o.sessionId(), caseId, request));
    }

    @Override
    public ClientReviewCommandResponseBody recommend(OperatorContext o, String caseId, ClientReviewRecommendationRequestBody request) {
        return command(caseId, api.recommend(o.accountId(), o.sessionId(), caseId, request));
    }

    @Override
    public ClientReviewCommandResponseBody returnCase(OperatorContext o, String caseId, ClientReviewReturnRequestBody request) {
        return command(caseId, api.returnCase(o.accountId(), o.sessionId(), caseId, request));
    }

    @Override
    public ClientReviewCommandResponseBody approve(OperatorContext o, String caseId, ClientReviewDecisionRequestBody request) {
        return command(caseId, api.approveCase(o.accountId(), o.sessionId(), caseId, request));
    }

    @Override
    public ClientReviewCommandResponseBody reject(OperatorContext o, String caseId, ClientReviewDecisionRequestBody request) {
        return command(caseId, api.rejectCase(o.accountId(), o.sessionId(), caseId, request));
    }

    /** 文件服务地址中的文件 ID。 */
    static String fileIdOf(String fileAddress) {
        var matcher = FILE_ADDRESS.matcher(fileAddress);
        require(matcher.matches());
        return matcher.group(1);
    }

    /** 命令结果必须属于本案件。 */
    static ClientReviewCommandResponseBody command(String caseId, ClientReviewCommandResponseBody response) {
        require(response != null && caseId.equals(response.getCaseId()) && response.getStatus() != null);
        return response;
    }

    /** 上游成功响应不符合合同。 */
    static void require(boolean valid) {
        if (!valid) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
    }
}

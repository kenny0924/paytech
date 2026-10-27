package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLOSED;
import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_EVIDENCE_REVIEWED;
import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.EvidenceReviewContext;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.ReviewClientEvidenceRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * O05 核验一份材料：只改变证据与所属材料要求的状态，不改原文件
 * <li>证据必须属于该主案件或其子案件；已是同一结果直接返回，已核验为其他结果 409；所属要求已豁免时拒绝。</li>
 * <li>拒绝必须带原因码，接受不得带原因码；记录核验人、时间与备注。</li>
 * <li>要求状态由其全部证据推导：任一 ACCEPTED 为 ACCEPTED，否则仍有 RECEIVED 为 RECEIVED，其余为 REJECTED。</li>
 */
@Component
@AllArgsConstructor
public class ReviewClientEvidenceProcessor extends AbstractProcessor<ReviewClientEvidenceRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 拒绝必须带原因码，接受不得带原因码。 */
    @Override
    protected void validate(ReviewClientEvidenceRequest request) {
        boolean rejected = ReviewCodes.REJECTED.equals(request.getOutcome());
        if (rejected != (request.getRejectReasonCode() != null)) {
            throw new BizCheckFailException("拒绝材料必须填写拒绝原因码，接受材料不能填写: " + request.getEvidenceId());
        }
    }

    /** 归属与当前状态校验后保存核验结果。 */
    @Override
    protected ClientReviewCommandResponse processRequest(ReviewClientEvidenceRequest request) {
        // 归属与当前状态
        ClientReviewFamily family = families.load(request.getCaseId());
        EvidenceRef evidence = family.findEvidence(request.getEvidenceId())
            .orElseThrow(() -> new FailException(CLIENT_REVIEW_NOT_FOUND, "材料不属于该审核案件: " + request.getEvidenceId()));
        if (request.getOutcome().equals(evidence.getStatus())) {
            return response(family);
        }
        if (!family.isActive()) {
            throw new FailException(CLIENT_REVIEW_CLOSED, "审核案件已结束: " + request.getCaseId());
        }
        if (!ReviewCodes.RECEIVED.equals(evidence.getStatus())) {
            throw new FailException(CLIENT_REVIEW_EVIDENCE_REVIEWED, "材料已核验为: " + evidence.getStatus());
        }
        ReviewRequirementView requirement = family.findRequirement(evidence.getRequirementId()).orElseThrow();
        if (ReviewCodes.WAIVED.equals(requirement.getStatus())) {
            throw new BizCheckFailException("材料要求已豁免，不再核验: " + requirement.getRequirementCode());
        }

        // 保存核验结果
        ReviewCommandContext command = support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId());
        String requirementStatus = requirementStatus(family.getEvidence(), evidence, request.getOutcome());
        reviews.reviewEvidence(new EvidenceReviewContext(command, evidence, request.getOutcome(), request.getRejectReasonCode(),
            request.getNote(), requirement, requirementStatus.equals(requirement.getStatus()) ? null : requirementStatus));
        return response(family);
    }

    /** 本次核验后要求的状态。 */
    static String requirementStatus(List<EvidenceRef> all, EvidenceRef reviewed, String outcome) {
        List<String> statuses = all.stream().filter(row -> row.getRequirementId() == reviewed.getRequirementId())
            .map(row -> row.getEvidenceId() == reviewed.getEvidenceId() ? outcome : row.getStatus()).toList();
        if (statuses.contains(ReviewCodes.ACCEPTED)) {
            return ReviewCodes.ACCEPTED;
        }
        return statuses.contains(ReviewCodes.RECEIVED) ? ReviewCodes.RECEIVED : ReviewCodes.REJECTED;
    }

    /** 主案件当前坐标。 */
    static ClientReviewCommandResponse response(ClientReviewFamily family) {
        return new ClientReviewCommandResponse(family.getMainCase().getCaseId(), family.getMainCase().getStatus());
    }
}

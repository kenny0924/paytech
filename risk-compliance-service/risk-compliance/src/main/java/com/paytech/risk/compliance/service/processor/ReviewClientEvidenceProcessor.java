package com.paytech.risk.compliance.service.processor;

import static com.paytech.risk.compliance.core.enums.ClientReviewErrorCode.*;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.risk.compliance.core.dal.dataobject.EvidenceRef;
import com.paytech.risk.compliance.domain.ClientReviewFamily;
import com.paytech.risk.compliance.domain.ReviewRequirementView;
import com.paytech.risk.compliance.domainservice.service.ClientReviewService;
import com.paytech.risk.compliance.service.processor.request.ReviewClientEvidenceRequest;
import com.paytech.risk.compliance.service.processor.response.ClientReviewCommandResponse;

import lombok.AllArgsConstructor;

/**
 * O05 核验一份材料：只改变证据与所属材料要求的状态，不改原文件
 * <li>证据必须属于该主任务或其子任务；已是同一结果直接返回，已核验为其他结果 409。</li>
 * <li>要求状态由其全部证据推导：任一 ACCEPTED 为 ACCEPTED，否则仍有 RECEIVED 为 RECEIVED，其余为 REJECTED。</li>
 * <li>表中不保存核验人；操作人由 Gateway 可信头确认后只用于访问控制。</li>
 */
@Component
@AllArgsConstructor
public class ReviewClientEvidenceProcessor extends AbstractProcessor<ReviewClientEvidenceRequest, ClientReviewCommandResponse> {
    private final Clock clock;
    private final ClientReviewService reviews;
    private final ClientReviewFamilyLoader families;

    @Override
    protected ClientReviewCommandResponse processRequest(ReviewClientEvidenceRequest request) {
        // 归属与当前状态
        ClientReviewFamily family = families.load(request.getCaseId());
        EvidenceRef evidence = family.getEvidence().stream().filter(row -> row.getEvidenceId() == request.getEvidenceId())
            .findFirst().orElseThrow(() -> new FailException(CLIENT_REVIEW_NOT_FOUND));
        if (request.getOutcome().equals(evidence.getStatus())) {
            return response(family);
        }
        if (!family.isActive()) {
            throw new FailException(CLIENT_REVIEW_CLOSED);
        }
        if (!"RECEIVED".equals(evidence.getStatus())) {
            throw new FailException(CLIENT_REVIEW_EVIDENCE_REVIEWED);
        }

        // 保存核验结果
        ReviewRequirementView requirement = family.getRequirements().stream()
            .filter(row -> row.getRequirementId() == evidence.getRequirementId()).findFirst().orElseThrow();
        String requirementStatus = requirementStatus(family.getEvidence(), evidence, request.getOutcome());
        reviews.reviewEvidence(family.getMainCase(), evidence, request.getOutcome(), requirement,
            requirementStatus.equals(requirement.getStatus()) ? null : requirementStatus,
            clock.instant().truncatedTo(ChronoUnit.MICROS));
        return response(family);
    }

    /** 本次核验后要求的状态。 */
    static String requirementStatus(List<EvidenceRef> all, EvidenceRef reviewed, String outcome) {
        List<String> statuses = all.stream().filter(row -> row.getRequirementId() == reviewed.getRequirementId())
            .map(row -> row.getEvidenceId() == reviewed.getEvidenceId() ? outcome : row.getStatus()).toList();
        if (statuses.contains("ACCEPTED")) {
            return "ACCEPTED";
        }
        return statuses.contains("RECEIVED") ? "RECEIVED" : "REJECTED";
    }

    /** 主任务当前坐标。 */
    static ClientReviewCommandResponse response(ClientReviewFamily family) {
        return new ClientReviewCommandResponse(family.getMainCase().getCaseId(), family.getMainCase().getStatus());
    }
}

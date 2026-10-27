package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_HIT_CLOSED;
import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.ScreeningHit;
import com.paytech.compliance.core.dal.dataobject.ScreeningResolution;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.core.enums.ScreeningHitResolution;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.HitResolutionContext;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.ResolveScreeningHitRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 处置一条筛查命中（design SM-I）
 * <li>只处置 OPEN 命中；已关闭或已被新执行取代 409 CLIENT_REVIEW_HIT_CLOSED。</li>
 * <li>NO_MATCH、FALSE_POSITIVE、TRUE_POSITIVE_ACCEPT、TRUE_POSITIVE_REJECT 关闭命中；POTENTIAL_MATCH、INSUFFICIENT_EVIDENCE 只追加记录。</li>
 * <li>TRUE_POSITIVE_REJECT 之后案件只能拒绝（批准门禁 SCREENING_TRUE_MATCH）。</li>
 */
@Component
@AllArgsConstructor
public class ResolveScreeningHitProcessor extends AbstractProcessor<ResolveScreeningHitRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 处置结论必须在目录内。 */
    @Override
    protected void validate(ResolveScreeningHitRequest request) {
        if (ScreeningHitResolution.ofCode(request.getResolution()) == null) {
            throw new InvalidParameterException("命中处置结论无效");
        }
    }

    /** 归属与命中状态校验后追加处置。 */
    @Override
    protected ClientReviewCommandResponse processRequest(ResolveScreeningHitRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        ScreeningHit hit = family.findHit(request.getHitId())
            .orElseThrow(() -> new FailException(CLIENT_REVIEW_NOT_FOUND, "命中不属于该审核案件: " + request.getHitId()));
        if (!ReviewCodes.HIT_OPEN.equals(hit.getStatus())) {
            throw new FailException(CLIENT_REVIEW_HIT_CLOSED, "命中已关闭或已被取代: " + hit.getStatus());
        }
        ReviewCommandContext command = support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId());
        ScreeningHitResolution resolution = ScreeningHitResolution.ofCode(request.getResolution());
        reviews.resolveHit(new HitResolutionContext(command, hit, resolution(family, hit, request, command), resolution.closesHit()));
        return ReviewClientEvidenceProcessor.response(family);
    }

    /** 新处置记录，版本为已有最大版本加 1。 */
    ScreeningResolution resolution(ClientReviewFamily family, ScreeningHit hit, ResolveScreeningHitRequest request,
        ReviewCommandContext command) {
        ScreeningResolution row = new ScreeningResolution();
        row.setResolutionId(support.nextId());
        row.setHitId(hit.getHitId());
        row.setResolutionVersion(family.resolutionsOf(hit.getHitId()).size() + 1);
        row.setResolution(request.getResolution());
        row.setReasonCode(request.getReasonCode());
        row.setNote(request.getNote());
        row.setReviewerId(command.getActorId());
        row.setOperatedBy(ReviewCodes.OPERATED_BY_PLATFORM);
        row.setOnBehalfOfPartnerId(command.getOnBehalfOfPartnerId());
        row.setResolvedTime(command.getNow());
        row.setCreateTime(command.getNow());
        return row;
    }
}

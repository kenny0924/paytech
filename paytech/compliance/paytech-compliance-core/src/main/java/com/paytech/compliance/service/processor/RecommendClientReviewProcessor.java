package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.RecommendClientReviewRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 推荐人给出推荐结论（design SM-J 四眼）；可重复推荐，以最新一次为准
 * <li>程序要求四眼时，决定人必须与最新推荐人不同，且决定方向须与推荐一致。</li>
 */
@Component
@AllArgsConstructor
public class RecommendClientReviewProcessor extends AbstractProcessor<RecommendClientReviewRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 案件进行中时保存推荐。 */
    @Override
    protected ClientReviewCommandResponse processRequest(RecommendClientReviewRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        reviews.recommend(support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId()), request.getRecommendation(),
            request.getNote());
        return ReviewClientEvidenceProcessor.response(family);
    }
}

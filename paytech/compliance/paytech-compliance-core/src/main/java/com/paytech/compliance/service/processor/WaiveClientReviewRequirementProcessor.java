package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ReviewRequirementView;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.WaiveClientReviewRequirementRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 豁免一项材料要求（design SM-E：PENDING / RECEIVED / REJECTED → WAIVED）
 * <li>只有程序标记可豁免的要求能豁免；已豁免直接返回；已接受的要求不再豁免。记录豁免人、原因码与说明。</li>
 */
@Component
@AllArgsConstructor
public class WaiveClientReviewRequirementProcessor
    extends AbstractProcessor<WaiveClientReviewRequirementRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 归属、可豁免性与当前状态校验后保存豁免。 */
    @Override
    protected ClientReviewCommandResponse processRequest(WaiveClientReviewRequirementRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        ReviewRequirementView requirement = family.findRequirement(request.getRequirementId())
            .orElseThrow(() -> new FailException(CLIENT_REVIEW_NOT_FOUND, "材料要求不属于该审核案件: " + request.getRequirementId()));
        if (ReviewCodes.WAIVED.equals(requirement.getStatus())) {
            return ReviewClientEvidenceProcessor.response(family);
        }
        if (!requirement.isWaivable()) {
            throw new BizCheckFailException("程序不允许豁免该材料要求: " + requirement.getRequirementCode());
        }
        if (ReviewCodes.ACCEPTED.equals(requirement.getStatus())) {
            throw new BizCheckFailException("材料要求已接受，不能豁免: " + requirement.getRequirementCode());
        }
        reviews.waive(support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId()), requirement,
            request.getReasonCode(), request.getNote());
        return ReviewClientEvidenceProcessor.response(family);
    }
}

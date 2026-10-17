package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.service.processor.request.ClientReviewResultRequest;
import com.paytech.compliance.service.processor.response.ClientReviewResultResponse;

import lombok.AllArgsConstructor;

/**
 * C05 审核结果投影：申请最近一次主任务的客户可见结果；只读
 * <li>只返回结果、退回分区与公开说明；内部理由、核验记录与决定人不出 Risk。</li>
 */
@Component
@AllArgsConstructor
public class GetClientReviewResultProcessor extends AbstractProcessor<ClientReviewResultRequest, ClientReviewResultResponse> {
    /** 结束态即客户可见结果。 */
    static final Set<String> OUTCOMES = Set.of("RETURNED", "APPROVED", "REJECTED");

    private final DueDiligenceCaseRepository cases;

    @Override
    protected ClientReviewResultResponse processRequest(ClientReviewResultRequest request) {
        // 列表按提交时间倒序，第一条即最近一次提交的主任务
        List<DueDiligenceCase> latest = cases.pageClientCases(null, null, request.getApplicationId(), 0, 1);
        if (latest.isEmpty()) {
            throw new FailException(CLIENT_REVIEW_NOT_FOUND);
        }
        DueDiligenceCase main = latest.getFirst();
        if (!OUTCOMES.contains(main.getStatus())) {
            return new ClientReviewResultResponse(request.getApplicationId(), main.getCaseId(), main.getStatus(), null, null, null, null);
        }
        return new ClientReviewResultResponse(request.getApplicationId(), main.getCaseId(), main.getStatus(), main.getStatus(),
            main.getReturnSection(), main.getPublicNote(), main.getDecisionTime());
    }
}

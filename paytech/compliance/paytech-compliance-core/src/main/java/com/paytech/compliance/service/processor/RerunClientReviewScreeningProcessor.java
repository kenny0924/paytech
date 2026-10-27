package com.paytech.compliance.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.RerunClientReviewScreeningRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 重跑案件全部名单筛查：同事务登记以新重跑 ID 为子类型的 CASE_SCREENING 任务，提交后异步执行
 * <li>每次请求都是新的重跑；新执行成功后取代同一主体同一模板的旧命中。</li>
 */
@Component
@AllArgsConstructor
public class RerunClientReviewScreeningProcessor
    extends AbstractProcessor<RerunClientReviewScreeningRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 案件进行中时登记重跑任务。 */
    @Override
    protected ClientReviewCommandResponse processRequest(RerunClientReviewScreeningRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        ReviewCommandContext command = support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId());
        reviews.requestScreening(command, "R" + support.nextId());
        return new ClientReviewCommandResponse(family.getMainCase().getCaseId(), family.getMainCase().getStatus(), true);
    }
}

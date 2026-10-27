package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.repository.ReturnErrorRepository;
import com.paytech.compliance.service.processor.request.ClientReviewResultRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCaseResponse;
import com.paytech.compliance.service.processor.response.ClientReviewResultResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * C05 审核结果投影：申请最近一轮主案件的客户可见结果
 * <li>结束态（RETURNED、APPROVED、REJECTED、WITHDRAWN、EXPIRED）即客户可见结果；退回与过期附需求级错误与补件期限。</li>
 * <li>不返回内部理由、核验记录、风险评级、决定人或子案件。</li>
 */
@Component
@AllArgsConstructor
public class GetClientReviewResultProcessor extends AbstractProcessor<ClientReviewResultRequest, ClientReviewResultResponse> {
    /** 带需求级错误的结果。 */
    static final Set<String> WITH_ERRORS = Set.of(DueDiligenceCaseStatus.RETURNED.getCode(), DueDiligenceCaseStatus.EXPIRED.getCode());

    private final ReturnErrorRepository returnErrors;
    private final DueDiligenceCaseRepository cases;

    /** 读取最近一轮主案件与其退回错误。 */
    @Override
    protected ClientReviewResultResponse processRequest(ClientReviewResultRequest request) {
        DueDiligenceCase main = cases.findLatestClientCase(request.getApplicationId());
        if (main == null) {
            throw new FailException(CLIENT_REVIEW_NOT_FOUND, "申请没有审核案件: " + request.getApplicationId());
        }
        DueDiligenceCaseStatus status = DueDiligenceCaseStatus.ofCode(main.getStatus());
        boolean closed = status.terminal() || status == DueDiligenceCaseStatus.RETURNED;
        boolean withErrors = WITH_ERRORS.contains(main.getStatus());
        List<ClientReviewCaseResponse.ErrorItem> errors = withErrors
            ? ClientReviewCaseConverter.errors(returnErrors.findByCase(main.getCaseId()), main.getReviewDueTime()) : List.of();
        return new ClientReviewResultResponse(request.getApplicationId(), main.getCaseId(), main.getStatus(),
            closed ? main.getStatus() : null, main.getRoundNo(), main.getReturnSection(), closed ? main.getPublicNote() : null, errors,
            withErrors ? main.getReviewDueTime() : null, closed ? main.getDecisionTime() : null);
    }
}

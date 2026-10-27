package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_CHECK_RECORDED;
import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseCheck;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.dal.dataobject.ProgramCheck;
import com.paytech.compliance.core.enums.ReviewCodes;
import com.paytech.compliance.domain.CheckResultContext;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domain.ReviewCommandContext;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.RecordClientReviewCheckRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 录入一项人工检查结论（design SM-G：MANUAL REQUESTED → COMPLETED）
 * <li>检查必须属于该主案件或其子案件且为人工检查；同结论重放直接返回，已录入其他结论 409。</li>
 * <li>有效截止 = 录入时间加模板有效天数（未设有效期为空）；记录操作方 PLATFORM 与代操作 PayFac。</li>
 */
@Component
@AllArgsConstructor
public class RecordClientReviewCheckProcessor extends AbstractProcessor<RecordClientReviewCheckRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 归属、执行方式与当前状态校验后保存结论。 */
    @Override
    protected ClientReviewCommandResponse processRequest(RecordClientReviewCheckRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        CaseCheck check = family.findCheck(request.getCheckId())
            .orElseThrow(() -> new FailException(CLIENT_REVIEW_NOT_FOUND, "检查不属于该审核案件: " + request.getCheckId()));
        if (ReviewCodes.CHECK_COMPLETED.equals(check.getStatus())) {
            if (request.getOutcome().equals(check.getOutcome())) {
                return ReviewClientEvidenceProcessor.response(family);
            }
            throw new FailException(CLIENT_REVIEW_CHECK_RECORDED, "检查已录入结论: " + check.getOutcome());
        }
        if (!ReviewCodes.CHECK_MODE_MANUAL.equals(check.getCheckMode())) {
            throw new BizCheckFailException("只有人工检查可以录入结论: " + request.getCheckId());
        }
        ReviewCommandContext command = support.begin(family, request.getActorId(), request.getOnBehalfOfPartnerId());
        reviews.recordCheck(new CheckResultContext(command, check, request.getOutcome(), request.getReasonCode(),
            request.getEvidenceRef(), request.getNote(), validUntil(family, check, command.getNow())));
        return ReviewClientEvidenceProcessor.response(family);
    }

    /** 录入时间加模板有效天数；模板未设有效期为空。 */
    static Instant validUntil(ClientReviewFamily family, CaseCheck check, Instant now) {
        DueDiligenceCase owner = family.allCases().stream().filter(row -> row.getCaseId() == check.getCaseId()).findFirst().orElseThrow();
        ProgramCheck template = family.programOf(owner).getChecks().stream()
            .filter(row -> row.getProgramCheckId() == check.getProgramCheckId()).findFirst().orElseThrow();
        return template.getValidityDays() == null ? null : now.plus(template.getValidityDays(), ChronoUnit.DAYS);
    }
}

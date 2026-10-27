package com.paytech.compliance.service.processor;

import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_CLOSED;
import static com.paytech.compliance.core.enums.ClientReviewErrorCode.CLIENT_REVIEW_NOT_FOUND;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.domain.CaseTransitionContext;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.WithdrawClientReviewRequest;
import com.paytech.compliance.service.processor.response.ClientReviewCommandResponse;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * I03 撤回（S-14）：client-master 撤回申请后，把该申请当前的主案件与其子案件结束为 WITHDRAWN
 * <li>进行中或已退回（未被重提）的案件可撤回；已 WITHDRAWN 直接返回；已批准、拒绝、过期或已被重提 409 CLIENT_REVIEW_CLOSED。</li>
 * <li>申请与案件不符 404；不发布 ClientReviewDecided（撤回由 client-master 发起）。</li>
 */
@Component
@AllArgsConstructor
public class WithdrawClientReviewProcessor extends AbstractProcessor<WithdrawClientReviewRequest, ClientReviewCommandResponse> {
    private final ClientReviewService reviews;
    private final DueDiligenceCaseRepository cases;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 归属与当前状态校验后迁移主案件与子案件。 */
    @Override
    protected ClientReviewCommandResponse processRequest(WithdrawClientReviewRequest request) {
        ClientReviewFamily family = families.load(request.getCaseId());
        DueDiligenceCase main = family.getMainCase();
        if (!Objects.equals(main.getSourceApplicationId(), request.getApplicationId())) {
            throw new FailException(CLIENT_REVIEW_NOT_FOUND, "审核案件不属于该申请: " + request.getCaseId());
        }
        DueDiligenceCaseStatus status = family.mainStatus();
        if (status == DueDiligenceCaseStatus.WITHDRAWN) {
            return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
        }
        if (!withdrawable(status) || cases.hasSuccessor(main.getCaseId())) {
            throw new FailException(CLIENT_REVIEW_CLOSED, "审核案件已结束或已被重提: " + main.getCaseId() + ", 状态: " + main.getStatus());
        }
        reviews.transition(buildContext(family, request));
        return new ClientReviewCommandResponse(main.getCaseId(), main.getStatus());
    }

    /** 进行中或已退回可撤回。 */
    static boolean withdrawable(DueDiligenceCaseStatus status) {
        return status.inProgress() || status == DueDiligenceCaseStatus.RETURNED;
    }

    /** 可撤回的子案件在前、主案件在后。 */
    CaseTransitionContext buildContext(ClientReviewFamily family, WithdrawClientReviewRequest request) {
        Instant now = support.now();
        List<DueDiligenceCase> targets = new ArrayList<>(family.getChildCases().stream()
            .filter(child -> withdrawable(DueDiligenceCaseStatus.ofCode(child.getStatus()))).toList());
        targets.add(family.getMainCase());
        List<CaseStateChange> changes = new ArrayList<>();
        for (DueDiligenceCase row : targets) {
            CaseStateChange change = support.stateChange(row, DueDiligenceCaseStatus.WITHDRAWN.getCode(), "WITHDRAW", "CLIENT_WITHDRAWAL",
                null, null, now);
            change.setTriggerType("CLIENT_WITHDRAWAL");
            change.setTriggerId(request.getRequestedByAccountId() == null ? Long.toString(request.getApplicationId())
                : Long.toString(request.getRequestedByAccountId()));
            changes.add(change);
        }
        return new CaseTransitionContext(family.getMainCase(), targets, DueDiligenceCaseStatus.WITHDRAWN.getCode(), changes, false, now);
    }
}

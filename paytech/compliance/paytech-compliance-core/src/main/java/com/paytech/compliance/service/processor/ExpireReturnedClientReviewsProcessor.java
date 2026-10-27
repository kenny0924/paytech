package com.paytech.compliance.service.processor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.compliance.core.dal.dataobject.CaseStateChange;
import com.paytech.compliance.core.dal.dataobject.DueDiligenceCase;
import com.paytech.compliance.core.enums.DueDiligenceCaseStatus;
import com.paytech.compliance.domain.CaseTransitionContext;
import com.paytech.compliance.domain.ClientReviewFamily;
import com.paytech.compliance.domainservice.repository.DueDiligenceCaseRepository;
import com.paytech.compliance.domainservice.service.ClientReviewService;
import com.paytech.compliance.service.processor.request.ExpireReturnedClientReviewsRequest;
import com.paytech.compliance.service.processor.response.ExpireReturnedClientReviewsResponse;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 退回超期扫描（design SM-D RETURNED → EXPIRED，Q16）：补件期限已到且未重提的 RETURNED 主案件与其 RETURNED 子案件结束为 EXPIRED，
 * 同事务登记 ClientReviewDecided(EXPIRED) 发布；client-master 收到后把申请置为 WITHDRAWN
 * <li>逐案件一个短事务；单个案件失败（并发变化等）计入失败数，不影响其他案件，下一轮重新选中。</li>
 */
@Component
@AllArgsConstructor
public class ExpireReturnedClientReviewsProcessor
    extends AbstractProcessor<ExpireReturnedClientReviewsRequest, ExpireReturnedClientReviewsResponse> {
    private final ClientReviewService reviews;
    private final DueDiligenceCaseRepository cases;
    private final ClientReviewCommandSupport support;
    private final ClientReviewFamilyLoader families;

    /** 逐个过期本轮选中的案件。 */
    @Override
    protected ExpireReturnedClientReviewsResponse processRequest(ExpireReturnedClientReviewsRequest request) {
        List<DueDiligenceCase> due = cases.findDueReturned(request.getNow(), request.getLimit());
        int expired = 0;
        int failed = 0;
        for (DueDiligenceCase row : due) {
            try {
                reviews.transition(buildContext(families.load(row.getCaseId()), request));
                expired++;
            } catch (RuntimeException changed) {
                // 案件在扫描后被重提、撤回或并发修改：保持原状态，下一轮重新判断。
                failed++;
            }
        }
        return new ExpireReturnedClientReviewsResponse(due.size(), expired, failed);
    }

    /** 已退回的子案件在前、主案件在后，全部迁移为 EXPIRED 并登记结果发布。 */
    CaseTransitionContext buildContext(ClientReviewFamily family, ExpireReturnedClientReviewsRequest request) {
        List<DueDiligenceCase> targets = new ArrayList<>(family.getChildCases().stream()
            .filter(child -> DueDiligenceCaseStatus.RETURNED.getCode().equals(child.getStatus())).toList());
        targets.add(family.getMainCase());
        List<CaseStateChange> changes = new ArrayList<>();
        for (DueDiligenceCase row : targets) {
            CaseStateChange change = support.stateChange(row, DueDiligenceCaseStatus.EXPIRED.getCode(), "EXPIRE", "RETURN_DUE_PASSED",
                null, null, request.getNow());
            change.setTriggerType("SYSTEM_TIMER");
            change.setTriggerId("RETURN_EXPIRY_SCAN");
            changes.add(change);
        }
        return new CaseTransitionContext(family.getMainCase(), targets, DueDiligenceCaseStatus.EXPIRED.getCode(), changes, true,
            request.getNow());
    }
}

package com.paytech.ledger.domainservice.validator.adjustment;

import java.util.List;

import com.paytech.foundation.common.error.FailException;
import com.paytech.ledger.api.enums.LedgerErrorCode;
import com.paytech.ledger.core.dal.dataobject.AdjustmentApproval;
import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.AdjustmentStatus;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;

/** 人工调整批准与驳回共用的 Maker-Checker 校验；只读入参，不访问数据库。 */
public final class ManualAdjustmentReviewValidator {

    private ManualAdjustmentReviewValidator() {
    }

    /** 新复核只能针对 SUBMITTED 调整，复核人不同于编制人，且确认的摘要等于调整内容摘要。 */
    public static void checkReviewable(ManualAdjustment adjustment, long checkerId, String approvedContentHash) {
        if (adjustment.getStatus() != AdjustmentStatus.SUBMITTED) {
            throw new FailException(LedgerErrorCode.REVIEW_STATUS_INVALID);
        }
        if (adjustment.getMakerId() == checkerId) {
            throw new FailException(LedgerErrorCode.SELF_REVIEW_FORBIDDEN);
        }
        if (!adjustment.getContentHash().equals(approvedContentHash)) {
            throw new FailException(LedgerErrorCode.REVIEW_CONTENT_CHANGED);
        }
    }

    /**
     * 判断已终结的调整是否就是同一复核人以同一摘要作出的同一决定
     *
     * @return true 表示是同一复核命令的重放，可以返回原结果
     */
    public static boolean isSameReview(List<AdjustmentApproval> approvals, long checkerId, ApprovalDecision decision,
        String approvedContentHash) {
        return approvals.stream().anyMatch(approval -> approval.getApprovalRole() == ApprovalRole.CHECKER
            && approval.getActorId() == checkerId && approval.getDecision() == decision
            && approval.getApprovedContentHash().equals(approvedContentHash));
    }
}
